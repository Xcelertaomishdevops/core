package com.taomish.actualization.v2.service;

import com.taomish.actualization.dto.BLActualizationDTO;
import com.taomish.actualization.model.*;
import com.taomish.actualization.repo.*;
import com.taomish.common.domain.TaomishError;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.constants.ActualizationConstants;
import com.taomish.dtos.ReturnStatus;
import com.taomish.dtos.invoice.InvoiceDTO;
import com.taomish.dtos.oisservice.PurchaseOrderDto;
import com.taomish.dtos.physicaltradeservice.PlannedObligationDTO;
import com.taomish.enums.PlannedObligationState;
import com.taomish.web.security.models.User;
import lombok.SneakyThrows;
import org.primefaces.shaded.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.taomish.RestEndPoints.CashflowRestEndPoints.*;
import static com.taomish.RestEndPoints.OisRestEndpoint.*;
import static com.taomish.RestEndPoints.PhysicalRestEndPoint.*;
import static com.taomish.RestEndPoints.PhysicalRestEndPoint.EQUA_LS;
import static com.taomish.RestEndPoints.PricingRestEndPoint.CASHFLOW_TYPE_PROVISIONAL;
import static com.taomish.constants.ActualizationConstants.*;
import static com.taomish.constants.CashflowConstants.CASHFLOW_TYPE_PREMIUM;
import static com.taomish.constants.InvoiceConstants.*;
import static com.taomish.constants.InvoiceConstants.GET_INVOICE_BY_CRITERIA;
import static com.taomish.constants.PhysicalConstants.*;
import static com.taomish.constants.PhysicalConstants.ACCRUED_PROVISIONAL;
import static com.taomish.constants.PlannedObligationConstants.*;

@Service
public class GRNService {

    private static final Logger logger = LoggerFactory.getLogger(GRNService.class);

    @Value("${baseUrlEC2DEV}")
    private String baseUrl;

    @Value("${oisBaseURL}")
    private String oisBaseURL;

    @Value("${inventoryBaseURL}")
    private String inventoryBaseURL;
    final
    ActualizationQuantityRepo actualizationQuantityRepo;
    final
    ActualQualityRepo actualQualityRepo;
    final
    ActualPackingDetailsRepo actualPackingDetailsRepo;

    final
    GrnStockRepo grnStockRepo;

    final
    BillOfLandingRepo billOfLandingRepo;

    private final ActualizationCashflowServiceV2 actualizationCashflowServiceV2;

    public GRNService(ActualizationQuantityRepo actualizationQuantityRepo, ActualQualityRepo actualQualityRepo, ActualPackingDetailsRepo actualPackingDetailsRepo, GrnStockRepo grnStockRepo, ActualizationCashflowServiceV2 actualizationCashflowServiceV2, BillOfLandingRepo billOfLandingRepo) {
        this.actualizationQuantityRepo = actualizationQuantityRepo;
        this.actualQualityRepo = actualQualityRepo;
        this.actualPackingDetailsRepo = actualPackingDetailsRepo;
        this.grnStockRepo = grnStockRepo;
        this.actualizationCashflowServiceV2 = actualizationCashflowServiceV2;
        this.billOfLandingRepo = billOfLandingRepo;
    }

    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public ReturnStatus     deactualizeRecordInGrn(PlannedObligationDTO plannedObligation, BLActualizationDTO grnBlRecord, GrnStock grnSavedRecord, User principal, String token)throws TaomishError {
        logger.info("entered in deactualizeRecordFromGrn function");
        String actualizationId = "";
        String tenantId = principal.getTenantId();
        Integer splitSequenceNumber = grnBlRecord.getSplitSequenceNumber();
        String GrnId = grnBlRecord.getGrnId();
        List<String> buildtype = Arrays.asList("Stock-build","Build-Simple blending");
        List<PlannedObligationDTO> deactualizeObligations = new ArrayList<>();
        boolean obligationDeactualize = false;
        String obligation = plannedObligation == null ? grnBlRecord.getPlannedObligationId() : plannedObligation.getPlannedObligationId();
        List<BillOfLanding> billOfLanding = billOfLandingRepo.findAllByTenantIdAndPlannedObligationId(principal.getTenantId(),obligation);
        if(!billOfLanding.isEmpty())
            grnBlRecord.setUpdateReceviedQty(true);
        if(grnSavedRecord !=  null){
            actualizationId = grnSavedRecord.getActualizeId();

            List<ActualQuality> actualQualities = actualQualityRepo.findAllByTenantIdAndActualizationId(tenantId,actualizationId);
            List<ActualPackingDetails> actualPackingDetails = actualPackingDetailsRepo.findAllByTenantIdAndActualizationId(tenantId,actualizationId);
            ActualizedQuantityObligations actualizedQuantityObligations = actualizationQuantityRepo.findByTenantIdAndActualizationId(tenantId,actualizationId);
            if(buildtype.contains(grnBlRecord.getStockType()) && grnBlRecord.getUpdateReceviedQty()){
                //   update the recevied Qty in quantity table and delete grn
                double Qty = actualizedQuantityObligations.getReceivedQuantity() - grnBlRecord.getGrnQuantity();
                actualizedQuantityObligations.setReceivedQuantity(Math.max(0,Qty));
                actualizationQuantityRepo.save(actualizedQuantityObligations);
                if(actualQualities != null)actualQualityRepo.deleteAll(actualQualities);
                if(actualPackingDetails != null)actualPackingDetailsRepo.deleteAll(actualPackingDetails);
                grnStockRepo.delete(grnSavedRecord);
                if(actualQualities != null)actualQualityRepo.deleteAll(actualQualities);
                if(actualPackingDetails != null)actualPackingDetailsRepo.deleteAll(actualPackingDetails);
            }
            else {
                obligationDeactualize = true;
                List<GrnStock> grnStockList = grnStockRepo.findAllByTenantIdAndActualizeIdAndGrnIdNot(tenantId,actualizationId,GrnId);
                if(actualizedQuantityObligations != null && grnStockList.isEmpty()){
                    actualizationQuantityRepo.delete(actualizedQuantityObligations);
                }
                else if(actualizedQuantityObligations != null){
                    double quantity = actualizedQuantityObligations.getUnloadQuantity() - grnBlRecord.getGrnQuantity();
                    actualizedQuantityObligations.setLoadQuantity(Math.max(0,quantity));
                    actualizedQuantityObligations.setUnloadQuantity(Math.max(0, quantity));
                    actualizationQuantityRepo.save(actualizedQuantityObligations);
                }
                if(actualQualities != null)actualQualityRepo.deleteAll(actualQualities);
                if(actualPackingDetails != null)actualPackingDetailsRepo.deleteAll(actualPackingDetails);
                grnStockRepo.delete(grnSavedRecord);
            }
        }

        var invActualizeResp = this.actulizeInventory(grnBlRecord.getStockTransferId(),grnBlRecord.getGrnQuantity(),"deactualize",
                actualizationId,splitSequenceNumber,GrnId,principal,token);
        if(invActualizeResp.getStatusType().equalsIgnoreCase("error")){
            logger.error("error while deactualize the stock record "+invActualizeResp.getStatus());
            throw new TaomishError(grnBlRecord.getStockTransferId() + " deactualize failed with errors: " + invActualizeResp.getStatus());
        }
        //   Deactualize the plannedObligation
        boolean updateObligationState = obligationDeactualize && grnBlRecord.getGrnId() !=  null && buildtype.contains(grnBlRecord.getStockType()) && !plannedObligation.getObligationState().isEmpty() && plannedObligation.getObligationState().get(PlannedObligationState.ACTUALIZED);
        if(updateObligationState){
            try{
            deActualizeObligationInGrn(Collections.singletonList(plannedObligation),token,principal);
            }catch (Exception ex){
                logger.error("Failed to update the planned obligation in deActualizeObligationInGrn() due to :" + ex);
                logger.info("Reverting update of the stock-movement record for transferId=" + grnBlRecord.getStockTransferId());
                this.actulizeInventory(grnBlRecord.getStockTransferId(),grnBlRecord.getGrnQuantity(),"actualize",
                        actualizationId,splitSequenceNumber,GrnId,principal,token);
                throw new TaomishError("Failed to update the obligation ");
            }
          }
        if(grnBlRecord.getIsOis()){
            PurchaseOrderDto purchaseOrderDto = TransactionIdUtil.query( oisBaseURL+ PURCHASE_ORDER_ROOT+GET_PURCHASE_ORDER_DETAILS_BY_TENANT_ID_AND_PONUMBER+principal.getTenantId()+PO_NUMBER+grnBlRecord.getPlannedObligationId(), HttpMethod.GET, token,null, PurchaseOrderDto.class);
            actualizationCashflowServiceV2.createCashFlowForPurchaseOrder(purchaseOrderDto,grnBlRecord.getGrnQuantity(),false,principal,token);
        }
        return ReturnStatus.successInstance(grnBlRecord.getStockTransferId() + " Deactualized successfully");
    }

    private List<InvoiceDTO> getInvoiceList(String plannedObligationId, double splitNumber, String tenantId, String token) {
        List<com.taomish.common.searchcriteria.SearchCriteria> invoiceCriteria = new ArrayList<>();
        invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(ActualizationConstants.TENANT_ID, EQ, tenantId));
        invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(PLANNED_OBLGN_ID, EQ, plannedObligationId));
        invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria("splitNumber", "in", List.of(0,splitNumber)));
        invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria("status", "notequals", VOID));
        return TransactionIdUtil.queryList(baseUrl+INVOICE_ROOT+GET_INVOICE_BY_CRITERIA, HttpMethod.POST,token,invoiceCriteria, InvoiceDTO.class);
    }
    public List<String> canDeactualize(List<String> obligationList, String tenantId, String token) {
        var errors = new ArrayList<String>();

        obligationList.forEach(obligation -> {
            var invoiceList = getInvoiceList(obligation,0,tenantId,token);
            invoiceList = invoiceList.stream().filter(item -> !List.of(BUY_ADVANCE,SELL_ADVANCE).contains(item.getInvoiceType())  && !List.of("Charges Invoice","Cost Invoice").contains(item.getFullInvoiceType()) ).toList();
            if(!invoiceList.isEmpty()) {
                var invoiceNumber = invoiceList.stream().map(InvoiceDTO::getInvoiceNumber).toList();
                errors.add("Invoices is generated for " + obligation + " ("+String.join(",",invoiceNumber)+"). To De-Actualize you have to delete invoices first");
            }
        });
        return errors;
    }

    public ReturnStatus actulizeInventory(String transferId, Double actulizedQty, String type, String actulizeId, Integer splitSequenceNumber, String grnId, User principal, String token){
        //TODO Create dto instaed of object and name-change of the api endpoint
        HashMap<String,String> payload = new HashMap<>();
        payload.put("transferId",transferId);
        payload.put("actulizedQty", String.valueOf(actulizedQty));
        payload.put("type",type);
        payload.put("actulizeId",actulizeId);
        payload.put("splitSequenceNumber", String.valueOf(splitSequenceNumber));
        payload.put("grnId",grnId);
        return TransactionIdUtil.query(inventoryBaseURL + "/api/stock-movement/actualize-inventory-with-transferId", HttpMethod.POST,token,payload, ReturnStatus.class);
    }

    @SneakyThrows
    public void deActualizeObligationInGrn(List<PlannedObligationDTO>plannedObligationDTOList,String token, User principal){
        PlannedObligationDTO plannedObligation = plannedObligationDTOList.getFirst();
        JSONObject tolerance = new JSONObject(plannedObligation.getToleranceValue());
        double min = tolerance.getDouble("min");
        double max = tolerance.getDouble("max");
        double minimumTolerance = (plannedObligation.getPlannedQuantity() - ((min / 100) * plannedObligation.getPlannedQuantity()));
        List<ActualizedQuantityObligations> actualizeRecords = actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationId(principal.getTenantId(),plannedObligation.getPlannedObligationId());
        Double sumOfLoadQty = actualizeRecords.stream()
                .mapToDouble(ActualizedQuantityObligations::getLoadQuantity)
                .sum();
        if(minimumTolerance >= sumOfLoadQty){
            plannedObligation.getObligationState().put(PlannedObligationState.ACTUALIZED, false);
            var ids = plannedObligationDTOList.stream().map(PlannedObligationDTO::getPlannedObligationId).toList();
            String tenantId = principal.getTenantId();
            List<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaListForCostDefunct;
            searchCriteriaListForCostDefunct = new ArrayList<>();
            List<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaListForDefunct;
            searchCriteriaListForDefunct = new ArrayList<>();

            try {
                plannedObligation.getObligationState().put(PlannedObligationState.ACTUALIZED, false);
                var updatedPlannedObligation = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + UPDATE_TRADE_PLANOBLIGATION + QUE_TENANT_ID + principal.getTenantId(), HttpMethod.POST, token, plannedObligation, PlannedObligationDTO.class);
                logger.info("updated the plannedObligationDto for obligationId=" + updatedPlannedObligation.getPlannedObligationId());
            } catch (Exception e) {
                logger.error("failed to update the plannedObligationDto: "+plannedObligation,e);
                throw new TaomishError("Failed to update the plannedObligationDto, for obligation = "+plannedObligation.getPlannedObligationId());
            }
            List<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaListForDefunctValues;
            searchCriteriaListForDefunctValues = new ArrayList<>();
            searchCriteriaListForDefunctValues.add(new com.taomish.common.searchcriteria.SearchCriteria("cashflowStatus", EQUA_LS, ACTIVE));
            searchCriteriaListForDefunctValues.add(new com.taomish.common.searchcriteria.SearchCriteria(TEN_ANT_ID, EQUA_LS, tenantId));
            searchCriteriaListForDefunctValues.add(new com.taomish.common.searchcriteria.SearchCriteria(PLANNED_OBLI_ID, "in", ids));
            searchCriteriaListForDefunctValues.add(new com.taomish.common.searchcriteria.SearchCriteria("type", "in", Arrays.asList(Trade, CASHFLOW_TYPE_PROVISIONAL, CASHFLOW_TYPE_PREMIUM)));
            searchCriteriaListForDefunctValues.add(new com.taomish.common.searchcriteria.SearchCriteria(STAGE, "in", Arrays.asList(ACCRUED, ACCRUED_PROVISIONAL)));
            try{
            TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + DEFUNCT_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaListForDefunctValues, Object.class);
            }catch (Exception ex){
                logger.error("Cash-flow updation failed",ex);
                logger.info("Reverting the PlannedObligationDto changes for: "+plannedObligation.getPlannedObligationId());
                plannedObligation.getObligationState().put(PlannedObligationState.ACTUALIZED, true);
                TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + UPDATE_TRADE_PLANOBLIGATION + QUE_TENANT_ID + principal.getTenantId(), HttpMethod.POST, token, plannedObligation, PlannedObligationDTO.class);
                throw new TaomishError("Failed to update the cashflow dto");
            }
        }
    }
}
