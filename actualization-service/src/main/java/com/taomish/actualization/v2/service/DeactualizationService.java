package com.taomish.actualization.v2.service;

import com.taomish.actualization.model.*;
import com.taomish.actualization.repo.*;
import com.taomish.common.domain.TaomishError;
import com.taomish.common.searchcriteria.SearchCriteria;
import com.taomish.common.searchcriteria.SpecBuilderUtil;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.constants.ActualizationConstants;
import com.taomish.dtos.ReturnStatus;
import com.taomish.dtos.invoice.InvoiceDTO;
import com.taomish.dtos.physicaltradeplanning.PhysicalTradePlanningDTO;
import com.taomish.dtos.physicaltradeservice.InterCompanyTradeDTO;
import com.taomish.dtos.physicaltradeservice.InterCompanyTradeObjectDTO;
import com.taomish.dtos.physicaltradeservice.PlannedObligationDTO;
import com.taomish.enums.PlannedObligationState;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import com.taomish.messaging.PlatformQueueService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.taomish.RestEndPoints.CashflowRestEndPoints.CASHFLOW_ROOT;
import static com.taomish.RestEndPoints.CashflowRestEndPoints.DEFUNCT_CASHFLOW_BY_CRITERIA;
import static com.taomish.RestEndPoints.PhysicalRestEndPoint.*;
import static com.taomish.RestEndPoints.PhysicalRestEndPoint.QUE_TENANT_ID;
import static com.taomish.RestEndPoints.PlanningRestEndPoint.*;
import static com.taomish.RestEndPoints.PricingRestEndPoint.CASHFLOW_TYPE_PROVISIONAL;
import static com.taomish.constants.ActualizationConstants.*;
import static com.taomish.constants.ActualizationConstants.PLANNED_OBLI_ID;
import static com.taomish.constants.CashflowConstants.CASHFLOW_TYPE_PREMIUM;
import static com.taomish.constants.InvoiceConstants.*;
import static com.taomish.constants.InvoiceConstants.GET_INVOICE_BY_CRITERIA;
import static com.taomish.constants.PhysicalConstants.*;
import static com.taomish.constants.PlannedObligationConstants.*;
import static com.taomish.constants.PlanningConstants.CIRCLE;
import static com.taomish.constants.PlanningConstants.WASHOUT;
import static com.taomish.constants.TradeCostConstants.CHARGES;
import static com.taomish.constants.TradeCostConstants.COST;

@Service
public class DeactualizationService {

    private static final Logger logger = LoggerFactory.getLogger(DeactualizationService.class);

    @Value("${baseUrlEC2DEV}")
    private String baseUrl;
    
    
    final ActualizationQuantityRepo actualizationQuantityRepo;
    final BillOfLandingRepo billOfLandingRepo;
    final ActualizationCostRepo actualizedCostRepo;
    final ActualQualityRepo actualQualityRepo;
    final ActualPackingDetailsRepo actualPackingDetailsRepo;


    public DeactualizationService( ActualizationQuantityRepo actualizationQuantityRepo, BillOfLandingRepo billOfLandingRepo, ActualizationCostRepo actualizedCostRepo, ActualQualityRepo actualQualityRepo, ActualPackingDetailsRepo actualPackingDetailsRepo) {
        
        
        this.actualizationQuantityRepo = actualizationQuantityRepo;
        this.billOfLandingRepo = billOfLandingRepo;
        this.actualizedCostRepo = actualizedCostRepo;
        this.actualQualityRepo = actualQualityRepo;
        this.actualPackingDetailsRepo = actualPackingDetailsRepo;
    }

    private List<InvoiceDTO> getInvoiceList(String plannedObligationId, double splitNumber, String tenantId, String token) {
        List<com.taomish.common.searchcriteria.SearchCriteria> invoiceCriteria = new ArrayList<>();
        invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(ActualizationConstants.TENANT_ID, EQ, tenantId));
        invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(PLANNED_OBLGN_ID, EQ, plannedObligationId));
        invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria("splitNumber", "in", List.of(0,splitNumber)));
        invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria("status", "notequals", VOID));
        return TransactionIdUtil.queryList(baseUrl+INVOICE_ROOT+GET_INVOICE_BY_CRITERIA, HttpMethod.POST,token,invoiceCriteria, InvoiceDTO.class);
    }

    private List<String> canDeactualize(List<PlannedObligationDTO> obligationList, String tenantId, String token) {
        var errors = new ArrayList<String>();
        obligationList.forEach(obligationDTO -> {
            if(!obligationDTO.isExternalRecord()){
                if(Boolean.TRUE.equals(obligationDTO.getObligationState().get(PlannedObligationState.DISCHARGED))) {
                    errors.add("Obligation "+obligationDTO.getPlannedObligationId()+" "+(obligationDTO.getSplitSequenceNumber() != 0?"("+obligationDTO.getSplitSequenceNumber()+")":"")+" is claimed. To De-Actualize you have to undo claim first.");
                }
                var invoiceList = getInvoiceList(obligationDTO.getPlannedObligationId(),obligationDTO.getSplitSequenceNumber(),tenantId,token).stream().filter(item -> !List.of(BUY_ADVANCE,SELL_ADVANCE).contains(item.getInvoiceType()) && !item.getStatus().equalsIgnoreCase(INITIATED) && !List.of("Charges Invoice","Cost Invoice").contains(item.getFullInvoiceType()) ).toList();
                if(!invoiceList.isEmpty()) {
                    var invoiceNumber = invoiceList.stream().map(InvoiceDTO::getInvoiceNumber).toList();
                    errors.add("Invoices ("+String.join(",",invoiceNumber)+") is already generated. To De-Actualize you have to delete invoices first");
                }
            }
            if(obligationDTO.getMatchType() != null && !List.of(WASHOUT,CIRCLE).contains(obligationDTO.getMatchType())){
                List<BillOfLanding> billOfLandings = billOfLandingRepo.findAllByTenantIdAndPlannedObligationId(tenantId, obligationDTO.getPlannedObligationId());
                if(billOfLandings.isEmpty()){
                    errors.add("Obligation " + obligationDTO.getPlannedObligationId() + " is actualized without BL, De-Actualize is not possible");
                }
            }
        });
        return errors;
    }

    private List<PlannedObligationDTO> getInterCompanyRecords(List<PlannedObligationDTO> obligationList, String tenantId, String token) {
        var obligationIds = new ArrayList<String>();
        var intercompanyEnabledObligationList = obligationList.stream().filter(item -> item.getTradeSettlementReference()!= null &&!item.getTradeSettlementReference().isEmpty()).toList();
        for (PlannedObligationDTO plannedObligation : intercompanyEnabledObligationList) {
            InterCompanyTradeDTO interCompanyTradeDTO = TransactionIdUtil.query(baseUrl + "/api/interCompanyTrades/v1/getInterCompanyTradeByUuid" + QUE_TENANT_ID + tenantId + "&interCompanyUuid=" + plannedObligation.getTradeSettlementReference(), HttpMethod.GET, token, null, InterCompanyTradeDTO.class);
            if(interCompanyTradeDTO != null) {
                obligationIds.addAll(interCompanyTradeDTO.getSettlementData().getInterCompanyTradeDetails().stream().map(InterCompanyTradeObjectDTO::getObligationId).toList());
            }
        }
        if(obligationIds.isEmpty()) {
           return new ArrayList<>();
        }
        var obligationFetchCriteria = new SpecBuilderUtil().with(tenantId)
                .addCriteria(new SearchCriteria("plannedObligationId","in",obligationIds)).getCriteriaList();
        var list= TransactionIdUtil.queryList(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATION_BY_CRITERIA + QUE_TENANT_ID + tenantId, HttpMethod.POST, token, obligationFetchCriteria, PlannedObligationDTO.class);
        List<PlannedObligationDTO> intercompanyObligationList = new ArrayList<>();
        for (PlannedObligationDTO plannedObligationDTO : list) {
            var actualizationList= actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationId(tenantId, plannedObligationDTO.getPlannedObligationId());
            for(ActualizedQuantityObligations actualizationObj:actualizationList) {
                var plannedObligationObj = new PlannedObligationDTO();
                BeanUtils.copyProperties(plannedObligationDTO,plannedObligationObj);
                plannedObligationObj.setSplitSequenceNumber(actualizationObj.getSplitSequenceNumber());
                intercompanyObligationList.add(plannedObligationObj);
            }
          }
       return intercompanyObligationList;
    }

    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReturnStatus deactualizeQuantity(List<PlannedObligationDTO> plannedObligationList, String tenantId, String token) {
        logger.debug("Enter to de-actualize quantity");
        var interCompanyObligations=getInterCompanyRecords(plannedObligationList, tenantId, token);
        plannedObligationList.addAll(interCompanyObligations);
        var errors = canDeactualize(plannedObligationList, tenantId, token);
        if(!errors.isEmpty()) {
            throw new TaomishError("De-Actualization failed with errors: "+String.join(",",errors));
        }
        List<ActualizedQuantityObligations> quantityObligationsList = new ArrayList<>();
        List<ActualizedCost> costList = new ArrayList<>();
        List<ActualQuality> qualityList = new ArrayList<>();
        List<ActualPackingDetails> packingDetailsList = new ArrayList<>();
        List<BillOfLanding> blRecords = new ArrayList<>();
        List<String> obligationIds = plannedObligationList.stream().map(PlannedObligationDTO::getPlannedObligationId).toList();
        for(PlannedObligationDTO plannedObligation : plannedObligationList) {
            quantityObligationsList.addAll(actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationIdAndSplitSequenceNumber(tenantId, plannedObligation.getPlannedObligationId(),plannedObligation.getSplitSequenceNumber()));
            costList.addAll(actualizedCostRepo.findAllByTenantIdAndPlannedObligationId(tenantId, plannedObligation.getPlannedObligationId()));
            qualityList.addAll(actualQualityRepo.findAllByPlannedObligationIdAndTenantId(plannedObligation.getPlannedObligationId(),tenantId));
            packingDetailsList.addAll(actualPackingDetailsRepo.findAllByPlannedObligationIdAndTenantId(plannedObligation.getPlannedObligationId(),tenantId));
            blRecords.addAll(billOfLandingRepo.findAllByTenantIdAndPlannedObligationIdAndSplitSequenceNumber(tenantId, plannedObligation.getPlannedObligationId(),plannedObligation.getSplitSequenceNumber()));
        }
        actualizationQuantityRepo.deleteAll(quantityObligationsList);
        actualizedCostRepo.deleteAll(costList);
        actualQualityRepo.deleteAll(qualityList);
        actualPackingDetailsRepo.deleteAll(packingDetailsList);
        billOfLandingRepo.deleteAll(blRecords);
        if(!costList.isEmpty()) {
            var costIds = costList.stream().map(ActualizedCost::getActualizedCostId).toList();
            logger.debug("De-actualizing Cost : {}",costIds);
            var costCashflowDefunctCriteria = new SpecBuilderUtil().with(tenantId)
                    .addCriteria(new SearchCriteria("type","in",List.of(COST,CHARGES)))
                    .addCriteria(new SearchCriteria("cashflowStatus",EQUA_LS, ACTIVE))
                    .addCriteria(new SearchCriteria(STAGE,EQUA_LS,ACCRUED))
                    .addCriteria(new SearchCriteria(PLANNED_OBLI_ID,"in",obligationIds))
                    .addCriteria(new SearchCriteria("costId", "in", costIds)).getCriteriaList();
            TransactionIdUtil.defunctCashflows(baseUrl, token, costCashflowDefunctCriteria);
        }
        var defunctCriteriaListForObligationCashflow = new SpecBuilderUtil().with(tenantId)
                .addCriteria(new SearchCriteria("type","in",Arrays.asList(Trade, CASHFLOW_TYPE_PROVISIONAL, CASHFLOW_TYPE_PREMIUM)))
                .addCriteria(new SearchCriteria(STAGE,"in",Arrays.asList(ACCRUED, ACCRUED_PROVISIONAL)))
                .addCriteria(new SearchCriteria("cashflowStatus",EQUA_LS, ACTIVE))
                .addCriteria(new SearchCriteria(PLANNED_OBLI_ID,"in",obligationIds)).getCriteriaList();
        TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + DEFUNCT_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, defunctCriteriaListForObligationCashflow, Object.class);
        obligationIds.forEach(obligationId -> TransactionIdUtil.setPlannedObligationStatesToFalse(baseUrl,obligationId, tenantId, token,PlannedObligationState.ACTUALIZED));
        logger.debug("Exiting after de-actualize quantity");

        var tradePlanningIds = new ArrayList<String>();

        for(var obj :interCompanyObligations) {
            var obligation = TransactionIdUtil.query(baseUrl +PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + "?tenantId=" + tenantId + "&plannedObligationId=" + obj.getPlannedObligationId(), HttpMethod.GET, token, null, PlannedObligationDTO.class);
            PhysicalTradePlanningDTO tradePlanning = TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_PLANNING_ROOT + GET_PHYSICAL_TRADE_PLANNING_BY_PLAN_ID +
                    "?planId=" + obligation.getPlanId() + "&tenantId=" + tenantId, HttpMethod.GET, token, null, PhysicalTradePlanningDTO.class);
            tradePlanningIds.add(tradePlanning.getUuid().toString());
        }
        TransactionIdUtil.query(baseUrl+PHYSICAL_TRADE_PLANNING_ROOT+DELETE_TRADE_PLANNING+QUERY+TENANT_ID_EQ+tenantId,HttpMethod.POST,token,tradePlanningIds, Object.class);

        return ReturnStatus.successInstance("De-Actualization Done");
    }
}
