package com.taomish.actualization.service;

import com.taomish.actualization.dto.*;
import com.taomish.actualization.model.*;
import com.taomish.actualization.repo.*;
import com.taomish.common.domain.TaomishError;
import com.taomish.common.searchcriteria.SearchCriteria;
import com.taomish.common.searchcriteria.SpecBuilderUtil;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.dtos.ReturnStatus;
import com.taomish.dtos.actualizationservice.ActualizationQualityObj;
import com.taomish.dtos.actualizationservice.ActualizedDocumentsDTO;
import com.taomish.dtos.actualizationservice.PlanTradeActualizationObj;
import com.taomish.dtos.cashflowservice.CashflowDataDTO;
import com.taomish.dtos.cashflowservice.UpdateCashflowDTO;
import com.taomish.dtos.conversionservice.UomConversionOutputtDTO;
import com.taomish.dtos.costservice.TradeCostDTO;
import com.taomish.dtos.doctemplateservice.DocumentRepositoryDTO;
import com.taomish.dtos.doctemplateservice.DocumentUploadResponseDTO;
import com.taomish.dtos.invoice.InvoiceDTO;
import com.taomish.dtos.physicaltradeplanning.PhysicalTradePlanningDTO;
import com.taomish.dtos.physicaltradeservice.InterCompanyTradeDTO;
import com.taomish.dtos.physicaltradeservice.PhysicalTradeDTO;
import com.taomish.dtos.physicaltradeservice.PlannedObligationDTO;
import com.taomish.dtos.qualityspec.QualitySpecDTO;
import com.taomish.dtos.stockplanningservice.StockDTO;
import com.taomish.dtos.tradepricingservice.PriceAllocationDTO;
import com.taomish.dtos.transportactualizationservice.TransportActualizationDataFetchObj;
import com.taomish.dtos.transportactualizationservice.TransportActualizationQuantityRows;
import com.taomish.enums.PlannedObligationState;
import com.taomish.enums.TradeTransactionType;
import com.taomish.services.CurrencyAndUOMConversionService.service.CurrencyConversionService;
import com.taomish.services.RevertManager.RevertManager;
import com.taomish.transaction_reference.service.TransactionIDGenerator;
import com.taomish.web.security.models.User;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import com.taomish.messaging.PlatformQueueService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.*;

import static com.taomish.RestEndPoints.CashflowRestEndPoints.*;
import static com.taomish.RestEndPoints.DocTemplateRestEndPoints.DOC_GEN_ROOT;
import static com.taomish.RestEndPoints.DocTemplateRestEndPoints.UPLOAD_DOCUMENT;
import static com.taomish.RestEndPoints.PhysicalRestEndPoint.GET_PHYSICAL_TRADE_BY_TRADE_ID;
import static com.taomish.RestEndPoints.PhysicalRestEndPoint.PHYSICAL_TRADE_ROOT;
import static com.taomish.RestEndPoints.PlanningRestEndPoint.*;
import static com.taomish.RestEndPoints.PricingRestEndPoint.*;
import static com.taomish.RestEndPoints.PricingServiceRestEndPoints.ADVANCE_PRICING_ROOT;
import static com.taomish.RestEndPoints.stockrestendpoint.*;
import static com.taomish.constants.ActualizationConstants.*;
import static com.taomish.constants.CashflowConstants.*;
import static com.taomish.constants.InvoiceConstants.*;
import static com.taomish.constants.InvoiceConstants.SELL_ADVANCE;
import static com.taomish.constants.PhysicalConstants.*;
import static com.taomish.constants.PlannedObligationConstants.*;
import static com.taomish.constants.PlanningConstants.CIRCLE;
import static com.taomish.constants.PlanningConstants.STRING;
import static com.taomish.constants.QualitySpecConstants.GET_QUALITY_SPEC_BY_TRADE_ID;
import static com.taomish.constants.QualitySpecConstants.QUALITY_SPECIFICATION_ROOT;
import static com.taomish.constants.TradeCostConstants.*;


@Service
public class ActualizationService {


    private static final Logger logger = LoggerFactory.getLogger(ActualizationService.class);
    @Autowired
    protected AmqpAdmin amqpAdmin;
    @Value("${baseUrlEC2DEV}")
    protected String baseUrl;
    @Autowired
    ActualizationDocumentsRepo actualizationDocumentsRepo;
    @Autowired
    ActualizationCostRepo actualizedCostRepo;
    @Autowired
    ActualizationQualityRepo actualizationQualityRepo;
    @Autowired
    ActualizationQuantityRepo actualizationQuantityRepo;
    @Autowired
    BillOfLandingRepo billOfLandingRepo;

    @Autowired
    CurrencyConversionService currencyConversionService;
    @Autowired
    private ActualizationCashflowService actualizationCashflowService;

    @Autowired
    ActualizePlanViewRepo actualizePlanViewRepo;

    @Value("${masterBaseURL}")
    private String masterBaseURL;

    @Autowired
    private  TransactionIDGenerator transactionIDGenerator;



    @Deprecated
    public ActualizeStatsObj getStats(String tenantId, String token) throws Exception {
        ActualizeStatsObj statsObj = new ActualizeStatsObj();
        Integer partiallyActualized = 0;
        Integer actualized = 0;
        Integer deliveryStarted = 0;
        List<PhysicalTradePlanningDTO> listPlanning = (List<PhysicalTradePlanningDTO>) TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_PLANNING_ROOT + GET_ALL_PHYSICAL_TRADE_PLAN,
                HttpMethod.GET, token, null, PlannedObligationDTO.class);
        Integer totalPlannedObligations = 0;
        Integer totalActualized = 0;
        Integer totalDeliveryStarted = 0;
        for (PhysicalTradePlanningDTO planning : listPlanning) {
            List<PlannedObligationDTO> plannedObligationList = (List<PlannedObligationDTO>) TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_ROOT + GET_PHYSICAL_TRADE_BY_TRADE_ID + planning.getPlanningId(),
                    HttpMethod.GET, token, null, PlannedObligationDTO.class);
            totalPlannedObligations = plannedObligationList.size();
            if (totalPlannedObligations > 0) {
                for (PlannedObligationDTO plannedObligation : plannedObligationList) {
                    if (plannedObligation.getObligationState().get(PlannedObligationState.ACTUALIZED)) {
                        totalActualized++;
                    }
                    if (plannedObligation.getObligationState().get(PlannedObligationState.DELIVERY_STARTED)) {
                        totalDeliveryStarted++;
                    }
                }
                if (totalDeliveryStarted == totalPlannedObligations) {
                    deliveryStarted++;
                } else if (totalPlannedObligations == totalActualized) {
                    actualized++;
                } else {
                    partiallyActualized++;
                }
            }
        }
        statsObj.setActualized(actualized);
        statsObj.setPartiallyActualized(partiallyActualized);
        statsObj.setDeliveryStarted(deliveryStarted);
        return statsObj;
    }

    public List<PlannedObligationDTO> getPlannedObligationList(String planId, String tenantId, String token) {
        List<PlannedObligationDTO> plannedObligationDTOList = new ArrayList<>();
        PlannedObligationDTO plannedObligationDTO;
        List<PlannedObligationDTO> plannedObligationList = TransactionIdUtil.queryList(baseUrl +
                        PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLAN_ID + "?planId=" + planId + "&tenantId=" + tenantId,
                HttpMethod.GET, token, null, PlannedObligationDTO.class);

        for (PlannedObligationDTO plannedObligation : plannedObligationList) {
            plannedObligationDTO = new PlannedObligationDTO();
            BeanUtils.copyProperties(plannedObligation, plannedObligationDTO);
            plannedObligationDTO.setQuantityUOM(plannedObligation.getQuantityUOM());
            plannedObligationDTO.setTradePriceUom(plannedObligation.getTradePriceUom());
            String tradePricingStatus = TransactionIdUtil.query(baseUrl + ADVANCE_PRICING_ROOT + GET_PRICINGSTATUS_BY_TRADEID + "?tenantId=" + tenantId + "&tradeId=" + plannedObligation.getTradeId(), HttpMethod.GET, token, null, String.class);
            if (!Objects.requireNonNull(tradePricingStatus).equalsIgnoreCase(PRICE_LINE_NOT_FOUND)) {
                plannedObligationDTO.setPricingStatus(tradePricingStatus);
            }
            String priceAllocationStatus = TransactionIdUtil.query(baseUrl + MANUAL_PRICING_ROOT + GET_PRICE_ALLOCAION_STATUS_BY_OBLIGATION_ID + "?tenantId=" + tenantId + "&obligationId=" + plannedObligation.getPlannedObligationId() + "&tradeId=" + plannedObligation.getTradeId(), HttpMethod.GET, token, null, String.class);
            plannedObligationDTO.setPriceAllocationstatus(priceAllocationStatus);
            plannedObligationDTOList.add(plannedObligationDTO);
        }
        return plannedObligationDTOList;
    }

    public PageImpl<PlanTradeActualizationObj> getPlanTradeList(String tenantId, int page, int size, String token) {
        Pageable p = PageRequest.of(page, size, Sort.by("updatedTimestamp").descending());
        Page<PlanViewActualization> planList = actualizePlanViewRepo.findAllByTenantId(tenantId, p);
        List<PlanViewActualization> list = planList.toList();
        List<PlanTradeActualizationObj> planTradeActualizationObjList = new ArrayList<>();
        list.forEach(item -> {
            planTradeActualizationObjList.add(getRowItem(item, token));
        });
        return TransactionIdUtil.createPage(planTradeActualizationObjList, page, size, planList.getTotalElements());
    }

    private PlanTradeActualizationObj getRowItem(PlanViewActualization item, String token) {
        int actualizedCount = 0;
        int settledCount = 0;
        double buyPlannedQuantity = 0.0;
        double sellPlannedQuantity = 0.0;
        PlanTradeActualizationObj planTradeActualizationObj = null;
        try {
            planTradeActualizationObj = new PlanTradeActualizationObj();
            planTradeActualizationObj.setPlanId(item.getPlanId());
            planTradeActualizationObj.setMatchType(item.getMatchType());

            List<PlannedObligationDTO> obligations = TransactionIdUtil.queryList(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLAN_ID + "?planId=" + item.getPlanId() + "&tenantId=" + item.getTenantId(), HttpMethod.GET, token, null, PlannedObligationDTO.class);
            for (PlannedObligationDTO plannedObligation : obligations) {
                Boolean actualizedState = plannedObligation.getObligationState().get(PlannedObligationState.ACTUALIZED);
                Boolean settleState = plannedObligation.getObligationState().get(PlannedObligationState.SETTLED);
                if (actualizedState) {
                    actualizedCount++;
                }
                if (settleState) {
                    settledCount++;
                }
                if (Arrays.asList(STRING, CIRCLE).contains(item.getMatchType())) {
                    if (plannedObligation.getTradeTransactionType().equals(TradeTransactionType.BUY)) {
                        buyPlannedQuantity = plannedObligation.getPlannedQuantity();
                    } else {
                        sellPlannedQuantity = plannedObligation.getPlannedQuantity();
                    }
                } else {
                    if (plannedObligation.getTradeTransactionType().equals(TradeTransactionType.BUY)) {
                        buyPlannedQuantity += plannedObligation.getPlannedQuantity();
                    } else {
                        sellPlannedQuantity += plannedObligation.getPlannedQuantity();
                    }
                }
            }

            planTradeActualizationObj.setBuyPlannedQuantity(buyPlannedQuantity);
            planTradeActualizationObj.setSellPlannedQuantity(sellPlannedQuantity);
            if (settledCount == 0 && actualizedCount == 0) {
                planTradeActualizationObj.setStatus("PENDING");
            } else {
                if (actualizedCount == obligations.size()) {
                    planTradeActualizationObj.setStatus(PlannedObligationState.ACTUALIZED.toString());
                } else if (settledCount == obligations.size()) {
                    planTradeActualizationObj.setStatus(PlannedObligationState.SETTLED.toString());
                } else {
                    planTradeActualizationObj.setStatus("PARTIALLY ACTUALIZED");
                }
            }
        } catch (Exception e) {
            logger.error("failed to get Row Item",e);
        }
        return planTradeActualizationObj;
    }


    @SneakyThrows
    public PlannedObligationDTO updateActualizedStatus(String plannedObligationId, String token, String tenantId) {
        PlannedObligationDTO plannedObligationDTO = null;
        plannedObligationDTO = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT +
                        GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + "?plannedObligationId=" + plannedObligationId + "&tenantId=" + tenantId,
                HttpMethod.GET, token, null, PlannedObligationDTO.class);
        if (plannedObligationDTO == null) {
            throw new Exception("No planned obligation found with planned obligation id : " + plannedObligationId);
        }
        PlannedObligationDTO plannedObligationDTOOld = new PlannedObligationDTO();
        BeanUtils.copyProperties(plannedObligationDTO, plannedObligationDTOOld);
        plannedObligationDTO.getObligationState().put(PlannedObligationState.ACTUALIZED, true);

        plannedObligationDTOOld = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + UPDATE_TRADE_PLANOBLIGATION + "?tenantId=" + tenantId, HttpMethod.POST, token, plannedObligationDTO, PlannedObligationDTO.class);
        return plannedObligationDTOOld;
    }

    protected void updateCashflow(String obligationId, PhysicalTradeDTO trade, double quantity, String plannedObligationId, String planId, double plannedQuantity, PriceAllocationDTO priceAllocationDTOS, String token, String tenantId, double averagePrice, PlannedObligationDTO plannedObligationDTO, TransportActualizationQuantityRows quantityRow) {
        double totalAmount = 0.0;
        UomConversionOutputtDTO uomConversionOutputtDTO = currencyConversionService.convertUom(plannedObligationDTO.getQuantityUOM(), plannedObligationDTO.getTradePriceUom(), 1.0, plannedObligationDTO.getCommodity(), tenantId, token);
        double conversionFactor = 1;
        if (uomConversionOutputtDTO.getStatus().equalsIgnoreCase(STATUS_OK)) {
            conversionFactor = uomConversionOutputtDTO.getConversionFactor();
        }
        totalAmount = (quantity * conversionFactor) * averagePrice;
        UpdateCashflowDTO updateCashflowDTO = new UpdateCashflowDTO();
        updateCashflowDTO.setObligationId(obligationId);
        updateCashflowDTO.setStage(ACCRUED);
        updateCashflowDTO.setObligationQuantity(quantity);
        updateCashflowDTO.setAmount(totalAmount);
        updateCashflowDTO.setQuantityUom(plannedObligationDTO.getQuantityUOM());
        updateCashflowDTO.setTradePriceCurrency(plannedObligationDTO.getTradePriceCurrency());
        updateCashflowDTO.setTradeSettlementCurrency(plannedObligationDTO.getTradeSettlementCurrency());
        updateCashflowDTO.setFxRate(plannedObligationDTO.getFxRate());
        updateCashflowDTO.setPlannedObligationId(plannedObligationId);
        updateCashflowDTO.setPlanId(planId);
        updateCashflowDTO.setPlannedQuantity(plannedQuantity);
        updateCashflowDTO.setSplitSequenceNumber(quantityRow.getSplitSequenceNumber());
        updateCashflowDTO.setType(Trade);
        actualizationCashflowService.runUpdateCashflow(tenantId, updateCashflowDTO, token);
    }

    protected CashflowDataDTO createExceedingAmountCashflow(String obligationId, double quantity, String token, String tenantId, PlannedObligationDTO plannedObligationDTO, double exceedingPrice) {
        double totalAmount;
        UomConversionOutputtDTO uomConversionOutputtDTO = currencyConversionService.convertUom(plannedObligationDTO.getQuantityUOM(), plannedObligationDTO.getTradePriceUom(), 1.0, plannedObligationDTO.getCommodity(), tenantId, token);
        double conversionFactor = 1;
        double fxRate = 1;
        if(plannedObligationDTO.getFxRate() != null && plannedObligationDTO.getFxRate() != 0) {
            fxRate = plannedObligationDTO.getFxRate();
        }
        if (uomConversionOutputtDTO.getStatus().equalsIgnoreCase(STATUS_OK)) {
            conversionFactor = uomConversionOutputtDTO.getConversionFactor();
        }
        totalAmount = (Math.abs(quantity) * conversionFactor) * exceedingPrice;
        CashflowDataDTO cashflowDataDTO = new CashflowDataDTO();
        cashflowDataDTO.setType(Trade);
        cashflowDataDTO.setStage(ACCRUED);
        cashflowDataDTO.setDocByPassId(plannedObligationDTO.getDocByPassId());
        cashflowDataDTO.setDescription("Exceeding Amount "+ ((quantity < 0)?"Min":"Max"));
        cashflowDataDTO.setQuantity(Math.abs(quantity));
        cashflowDataDTO.setTradePrice(exceedingPrice);
        cashflowDataDTO.setObligationId(obligationId);
        cashflowDataDTO.setPlannedObligationId(obligationId);
        cashflowDataDTO.setCommodity(plannedObligationDTO.getCommodity());
        cashflowDataDTO.setQuantityUom(plannedObligationDTO.getQuantityUOM());
        cashflowDataDTO.setTradePriceCurrency(plannedObligationDTO.getTradePriceCurrency());
        cashflowDataDTO.setTradePriceUom(plannedObligationDTO.getTradePriceUom());
        cashflowDataDTO.setSettlementCurrency(plannedObligationDTO.getTradeSettlementCurrency());
        cashflowDataDTO.setUom(plannedObligationDTO.getQuantityUOM());
        cashflowDataDTO.setPaymentDueDate(plannedObligationDTO.getDeliveryEndDate());
        cashflowDataDTO.setCompany(plannedObligationDTO.getCompany());
        cashflowDataDTO.setCounterparty(plannedObligationDTO.getCounterparty());
        cashflowDataDTO.setFxRate(fxRate);
        cashflowDataDTO.setCashflowStatus(ACTIVE);
        cashflowDataDTO.setPaymentDate(plannedObligationDTO.getDeliveryEndDate());
        cashflowDataDTO.setPlanId(plannedObligationDTO.getPlanId());

        cashflowDataDTO.setPriceType(plannedObligationDTO.getPriceType());
        cashflowDataDTO.setProfitcenter(plannedObligationDTO.getProfitcenter());
        cashflowDataDTO.setProvisionalTradePrice(plannedObligationDTO.getProvisionalPrice() != null?plannedObligationDTO.getProvisionalPrice():0);
        cashflowDataDTO.setSplitSequenceNumber(plannedObligationDTO.getSplitSequenceNumber());
        cashflowDataDTO.setTradeId(plannedObligationDTO.getTradeId());
        cashflowDataDTO.setTradePriceValue(totalAmount);
        cashflowDataDTO.setTradeDateTime(plannedObligationDTO.getTradeDateTime());
        cashflowDataDTO.setTradeTransactionType(plannedObligationDTO.getTradeTransactionType());
        cashflowDataDTO.setSettlementValue(cashflowDataDTO.getTradePriceValue() * cashflowDataDTO.getFxRate());
        cashflowDataDTO.setQuantityStatus(ACTUAL);
        cashflowDataDTO.setTenantId(tenantId);
        cashflowDataDTO.setPriceStatus(PRICED);
        cashflowDataDTO.setFinancialAmountType((plannedObligationDTO.getTradeTransactionType() == TradeTransactionType.SELL)?RECEIVABLE:PAYABLE);
        return TransactionIdUtil.query(baseUrl+CASHFLOW_ROOT+SAVE_CASHFLOW, HttpMethod.POST,token,cashflowDataDTO, CashflowDataDTO.class);
    }

    protected String getActualizationId(ActualizeObj actualizeObj) throws Exception {
        String actualizationId = "";
        actualizationId = String.valueOf(new Random().nextInt(1000));
        return actualizationId;
    }

    public Map<String, LocalDateTime> getEventMapping(List<ActualizedPaymentEventDTO> paymentEventObjList) {
        Map<String, LocalDateTime> map = new HashMap<>();
        for (ActualizedPaymentEventDTO paymentEventObj : paymentEventObjList) {
            map.put(paymentEventObj.getEventType(), paymentEventObj.getDate());
        }
        return map;
    }


    public List<ActualizedPaymentEventDTO> getEventList(Map<String, LocalDateTime> paymentEventMap) {
        List<ActualizedPaymentEventDTO> list = new ArrayList<>();
        for (String event : paymentEventMap.keySet()) {
            ActualizedPaymentEventDTO eventObj = new ActualizedPaymentEventDTO();
            eventObj.setEventType(event);
            eventObj.setDate(paymentEventMap.get(event));
            list.add(eventObj);
        }
        return list;
    }

    private List<String> canDeactualize(List<PlannedObligationDTO> obligationList, String tenantId, String token) {
        var errors = new ArrayList<String>();
        obligationList.forEach(obligationDTO -> {
            if(!obligationDTO.isExternalRecord()){
                var invoiceList = getInvoiceList(obligationDTO.getPlannedObligationId(),obligationDTO.getSplitSequenceNumber(),tenantId,token).stream().filter(item -> !List.of(BUY_ADVANCE,SELL_ADVANCE).contains(item.getInvoiceType()) && !item.getStatus().equalsIgnoreCase(INITIATED)).toList();
                if(invoiceList != null && !invoiceList.isEmpty()) {
                    var invoiceNumber = invoiceList.stream().map(InvoiceDTO::getInvoiceNumber).toList();
                    errors.add("Invoices ("+String.join(",",invoiceNumber)+") is already generated. To De-Actualize you have to delete invoices first");
                }
                var quantityDTO = getQuantity(tenantId,obligationDTO.getPlannedObligationId(), Double.valueOf(obligationDTO.getSplitSequenceNumber()),token);
                if(quantityDTO != null && quantityDTO.getClaimedQuantity() != null && quantityDTO.getClaimedQuantity()  != 0) {
                    errors.add("Obligation "+quantityDTO.getPlannedObligationId()+" "+(obligationDTO.getSplitSequenceNumber() != 0?"("+obligationDTO.getSplitSequenceNumber()+")":"")+" is claimed. To De-Actualize you have to undo claim first.");
                }
            }
        });
        return errors;
    }

    private List<InvoiceDTO> getInvoiceList(String plannedObligationId, double splitNumber, String tenantId, String token) {
        List<SearchCriteria> invoiceCriteria = new ArrayList<>();
        invoiceCriteria.add(new SearchCriteria(TENANT_ID, EQ, tenantId));
        invoiceCriteria.add(new SearchCriteria(PLANNED_OBLGN_ID, EQ, plannedObligationId));
        invoiceCriteria.add(new SearchCriteria("splitNumber", "in", List.of(0,splitNumber)));
        invoiceCriteria.add(new SearchCriteria("status", "notequals", VOID));
        return TransactionIdUtil.queryList(baseUrl+INVOICE_ROOT+GET_INVOICE_BY_CRITERIA,HttpMethod.POST,token,invoiceCriteria, InvoiceDTO.class);
    }

    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReturnStatus deActualize(List<PlannedObligationDTO> obligationList, String token, String tenantId) {
        RevertManager revertManager = new RevertManager();
        PlannedObligationDTO plannedObligationItem = null;
        try {
            List<ActualizedCost> actualizedCostList;
            List<ActualizedQuality> actualizedQualityList;
            List<ActualizedQuantityObligations> actualizedQuantityObligationsList;
            List<BillOfLanding> billOfLandingList;
            List<String> tradeIds = new ArrayList<>();
            List<String> costIds = new ArrayList<>();
            List<String> plannedObligationIds = new ArrayList<>();
            List<PlannedObligationDTO> plannedObligationDTOList = new ArrayList<>();
            List<PlannedObligationDTO> revertObligationDTOList = new ArrayList<>();

            var errors = canDeactualize(obligationList,tenantId,token);
            if(!errors.isEmpty()) {
                return ReturnStatus.errorInstance("Failed to De-Actualize. Check below errors : \n\n" + String.join(",\n",errors));
            }
            boolean costDefunct = false;

            for (PlannedObligationDTO plannedObligation : obligationList.stream().filter(item ->!item.isExternalRecord()).toList()) {
                actualizedCostList = actualizedCostRepo.findAllByTenantIdAndPlannedObligationId(tenantId, plannedObligation.getPlannedObligationId());
                actualizedQualityList = actualizationQualityRepo.findAllByPlannedObligationIdAndTenantId(plannedObligation.getPlannedObligationId(), tenantId);
                actualizedQuantityObligationsList = actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationId(tenantId, plannedObligation.getPlannedObligationId());
                billOfLandingList = billOfLandingRepo.findAllByTenantIdAndPlannedObligationId(tenantId, plannedObligation.getPlannedObligationId());
                for (ActualizedCost actualizedCost : actualizedCostList) {
                    costDefunct = true;
                    actualizedCostRepo.delete(actualizedCost);
                    tradeIds.add(plannedObligation.getTradeId());
                    costIds.add(actualizedCost.getActualizedCostId());
                    plannedObligationIds.add(plannedObligation.getPlannedObligationId());
                }
                actualizationQualityRepo.deleteAll(actualizedQualityList);
                tradeIds.add(plannedObligation.getTradeId());
                plannedObligationIds.add(plannedObligation.getPlannedObligationId());
                actualizationQuantityRepo.deleteAll(actualizedQuantityObligationsList);
                billOfLandingRepo.deleteAll(billOfLandingList);
                plannedObligationItem = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT +
                                GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + "?plannedObligationId=" + plannedObligation.getPlannedObligationId() + "&tenantId=" + tenantId,
                        HttpMethod.GET, token, null, PlannedObligationDTO.class);
                revertObligationDTOList.add(TransactionIdUtil.convertObject(plannedObligationItem,PlannedObligationDTO.class));
                Objects.requireNonNull(plannedObligationItem).getObligationState().put(PlannedObligationState.ACTUALIZED, false);
                plannedObligationDTOList.add(plannedObligationItem);

            }
            List<SearchCriteria> searchCriteriaListForDefunct;
            searchCriteriaListForDefunct = new ArrayList<>();
            if (costDefunct) {
                try {
                    searchCriteriaListForDefunct.add(new SearchCriteria(TENANT_ID, "equals", tenantId));
                    searchCriteriaListForDefunct.add(new SearchCriteria("type", "in", List.of(COST,CHARGES)));
                    searchCriteriaListForDefunct.add(new SearchCriteria("stage", "equals", ACCRUED));
                    searchCriteriaListForDefunct.add(new SearchCriteria("cashflowStatus","equals",ACTIVE));
                    searchCriteriaListForDefunct.add(new SearchCriteria("tradeId", "in", tradeIds));
                    if (costIds.size() > 0) {
                        searchCriteriaListForDefunct.add(new SearchCriteria("costId", "in", costIds));
                    }
                    searchCriteriaListForDefunct.add(new SearchCriteria("plannedObligationId", "in", plannedObligationIds));
                    TransactionIdUtil.defunctCashflows(baseUrl,token,searchCriteriaListForDefunct);
                } catch (Exception e) {
                    logger.error("cashflow defunct failed reverting cashflow",e);
                    throw new TaomishError("Failed while defuncting cashflows for cost.");
                }
                revertManager.registerActiveCashflowAPI(baseUrl ,searchCriteriaListForDefunct);
            }

            var ids = plannedObligationDTOList.stream().map(PlannedObligationDTO::getPlannedObligationId).toList();
            try {
                TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + BULK_UPDATE_TRADE_PLANOBLIGATION + "?tenantId=" + tenantId, HttpMethod.POST, token, plannedObligationDTOList, PlannedObligationDTO.class);
            } catch (Exception e) {
                logger.error("failed to deActualize",e);
                throw new TaomishError("Failed to updated obligation state");
            }
            revertManager.registerPostAPI(baseUrl + PLANNED_OBLIGATION_ROOT + BULK_UPDATE_TRADE_PLANOBLIGATION + "?tenantId=" + tenantId,revertObligationDTOList);

            List<SearchCriteria> searchCriteriaListForDefunctValues;
            searchCriteriaListForDefunctValues = new ArrayList<>();
            searchCriteriaListForDefunctValues.add(new SearchCriteria("cashflowStatus", "equals", ACTIVE));
            searchCriteriaListForDefunctValues.add(new SearchCriteria(TENANT_ID, "equals", tenantId));
            searchCriteriaListForDefunctValues.add(new SearchCriteria("plannedObligationId", "in", ids));
            searchCriteriaListForDefunctValues.add(new SearchCriteria("type", "in", Arrays.asList(Trade, CASHFLOW_TYPE_PROVISIONAL, CASHFLOW_TYPE_PREMIUM)));
            searchCriteriaListForDefunctValues.add(new SearchCriteria("stage", "in", Arrays.asList(ACCRUED, ACCRUED_PROVISIONAL)));
            try {
                TransactionIdUtil.defunctCashflows(baseUrl,token,searchCriteriaListForDefunctValues);
            } catch (Exception e) {
                logger.error("failed to deActualize",e);
                throw new TaomishError("Failed while defuncting cashflows for planned obligation.");
            }
            revertManager.registerActiveCashflowAPI(baseUrl,searchCriteriaListForDefunctValues);
            deactulizeIntercompanyObligations(obligationList,tenantId,token);
        } catch (Exception e) {
            revertManager.revertAll(token);
            throw e;
        }
        logger.info("De-Actualization for Planned Obligation");
        return ReturnStatus.successInstance("De-Actualization Done");
    }

    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void deactulizeIntercompanyObligations(List<PlannedObligationDTO> obligationList, String tenantId, String token){
        for (PlannedObligationDTO plannedObligation : obligationList) {
            if(plannedObligation.getTradeSettlementReference()!=null && !plannedObligation.getTradeSettlementReference().isEmpty()){
                InterCompanyTradeDTO interCompanyTradeDTO = TransactionIdUtil.query(baseUrl + "/api/interCompanyTrades/v1/getInterCompanyTradeByUuid" + "?tenantId=" + tenantId + "&interCompanyUuid=" + plannedObligation.getTradeSettlementReference(), HttpMethod.GET, token, null, InterCompanyTradeDTO.class);
                var obligations = new ArrayList<PlannedObligationDTO>();
                var tradePlanningIds = new ArrayList<String>();
                assert interCompanyTradeDTO != null;
                for(var obj :interCompanyTradeDTO.getSettlementData().getInterCompanyTradeDetails()) {
                    var obligation = TransactionIdUtil.query(baseUrl +PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + "?tenantId=" + tenantId + "&plannedObligationId=" + obj.getObligationId(), HttpMethod.GET, token, null, PlannedObligationDTO.class);
                    obligations.add(obligation);
                    PhysicalTradePlanningDTO tradePlanning = TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_PLANNING_ROOT + GET_PHYSICAL_TRADE_PLANNING_BY_PLAN_ID +
                            "?planId=" + obligation.getPlanId() + "&tenantId=" + tenantId, HttpMethod.GET, token, null, PhysicalTradePlanningDTO.class);
                    tradePlanningIds.add(tradePlanning.getUuid().toString());
                }
                deActualize(obligations,token, tenantId);
                TransactionIdUtil.query(baseUrl+PHYSICAL_TRADE_PLANNING_ROOT+DELETE_TRADE_PLANNING+QUERY+TENANT_ID_EQ+tenantId,HttpMethod.POST,token,tradePlanningIds, Object.class);
            }
        }
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ActualizedDocuments saveDocument(ActualizedDocumentsDTO actualizedDocumentsDTO, String token, String tenantId) throws Exception {
        try {
            String actualizationId = "";
            ActualizedDocuments actualizedDocuments = new ActualizedDocuments();
            BeanUtils.copyProperties(actualizedDocumentsDTO, actualizedDocuments);
            if (actualizedDocuments.getActualizationId().length() == 0) {
                PlannedObligationDTO plannedObligation = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT +
                                GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + "?plannedObligationId=" + actualizedDocuments.getPlannedObligationId() + "&tenantId=" + tenantId,
                        HttpMethod.GET, token, null, PlannedObligationDTO.class);
                actualizationId = transactionIDGenerator.generateId( "actualizationId", plannedObligation, tenantId, token, false, "", false);
                if (actualizationId == null) {
                    throw new Exception("Actualization ID is not generated");
                }
                actualizedDocuments.setActualizationId(actualizationId);
            }
            DocumentRepositoryDTO documentRepositoryDTO = new DocumentRepositoryDTO();
            documentRepositoryDTO.setDocument(actualizedDocumentsDTO.getAttachment());
            documentRepositoryDTO.setTradeType(PHYSICAL);
            documentRepositoryDTO.setBusinessApplicationName(BUSSINESS_APPLICATION_TRADE_ACTUALIZATION);
            documentRepositoryDTO.setBusinessApplicationId(actualizedDocuments.getActualizationId());
            actualizedDocuments.setDescription(actualizedDocumentsDTO.getDescription());
            if(actualizedDocuments.getDescription()!=null){
                documentRepositoryDTO.setDescription(actualizedDocuments.getDescription());
            }else {
                documentRepositoryDTO.setDescription(actualizedDocuments.getActualizationId() +" for "+BUSSINESS_APPLICATION_TRADE_ACTUALIZATION);
            }
            DocumentUploadResponseDTO uploadResponseDTO = TransactionIdUtil.query(masterBaseURL + DOC_GEN_ROOT + UPLOAD_DOCUMENT + "?tenantId=" + tenantId, HttpMethod.POST, token, documentRepositoryDTO, DocumentUploadResponseDTO.class);
            if (uploadResponseDTO.getStatus().equalsIgnoreCase(STATUS_OK)) {
                actualizedDocuments.setAttachmentUrl(uploadResponseDTO.getDownloadUrl());
            } else {
                logger.error("Failed to save document");
                throw new Exception("Failed to save document");
            }
            actualizedDocuments.setDocumentType(actualizedDocumentsDTO.getDocumentType());
            actualizedDocuments.setStatus(actualizedDocumentsDTO.getStatus());
            actualizationDocumentsRepo.save(actualizedDocuments);
            return actualizedDocuments;
        } catch (Exception e) {
            logger.error("Failed to save document",e);
            throw new Exception("Failed to save document");
        }
    }

    public ReturnStatus deleteAllByDocId(List<String> uuids, User user) {
        logger.info("Entered to DocUploadService.deleteByDocId() method");
        var uuidList = uuids.stream().map(UUID::fromString).toList();
        var documentLists = actualizationDocumentsRepo.findAllByTenantIdAndUuidIn(user.getTenantId(), uuidList);
        actualizationDocumentsRepo.deleteAll(documentLists);
        return ReturnStatus.successInstance("Document Deleted Successfully !");
    }

    public AllActualizeObj getAllActualized(String plannedObligationId, String token, String tenantId) {
        AllActualizeObj obj = new AllActualizeObj();
        obj.setCosts(getCost(plannedObligationId, token, tenantId));
        obj.setQualitySpecs(getQuality(tenantId, plannedObligationId, token));
        obj.setQuantity(getQuantity(tenantId, plannedObligationId, null, token));
        return obj;
    }

    public List<ActualizeCostObj> getCost(String plannedObligationId, String token, String tenantId) {
        List<ActualizeCostObj> finalList = new ArrayList<>();
        ActualizedCost actualizedCostObject = null;
        PlannedObligationDTO plannedObligationDTO = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT +
                        GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + "?plannedObligationId=" + plannedObligationId + "&tenantId=" + tenantId,
                HttpMethod.GET, token, null, PlannedObligationDTO.class);
        List<ActualizedCost> actualizedCost = actualizedCostRepo.findAllByTenantIdAndPlannedObligationId(tenantId, plannedObligationId);
        List<TradeCostDTO> tradeCost = TransactionIdUtil.queryList(baseUrl + COST_ROOT +
                        GET_ALL_COSTS_BY_COST_FOR_LINKTO_AND_QUANTITY_OPTION + "?costFor=" + COST_FOR_TRADE_ID + "&linkTo=" + plannedObligationDTO.getTradeId() +
                        "&quantityOption=" + SCHEDULED_QUANTITY + "&tenantId=" + tenantId,
                HttpMethod.GET, token, null, TradeCostDTO.class);
        List<TradeCostDTO> obligationCost = TransactionIdUtil.queryList(baseUrl + COST_ROOT +
                        GET_ALL_COSTS_BY_COST_FOR_LINKTO_AND_QUANTITY_OPTION + "?costFor=" + COST_FOR_OBLIGATION_ID + "&linkTo=" + plannedObligationDTO.getPlannedObligationId() +
                        "&quantityOption=" + SCHEDULED_QUANTITY + "&tenantId=" + tenantId,
                HttpMethod.GET, token, null, TradeCostDTO.class);
        List<TradeCostDTO> linkedToList = new ArrayList<>();
        linkedToList.addAll(tradeCost);
        linkedToList.addAll(obligationCost);

        var splits = actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationId(tenantId, plannedObligationId);
        var splitNumbers = new ArrayList<>(splits.stream().map(ActualizedQuantityObligations::getSplitSequenceNumber).toList());
        boolean quantityActualized = !splits.isEmpty();
        if (splitNumbers.isEmpty()) {
            splitNumbers.add(0);
        }
        for (TradeCostDTO cost : linkedToList) {
            for (var splitNumber : splitNumbers) {
                ActualizeCostObj obj = new ActualizeCostObj();
                actualizedCostObject = actualizedCostRepo.findByEstimatedCostIdAndTenantIdAndSplitSequenceNumberAndPlannedObligationId(cost.getCostId(), tenantId, splitNumber,plannedObligationId);
                if (actualizedCostObject != null) {
                    BeanUtils.copyProperties(actualizedCostObject, obj);
                    obj.setActualizedStatus(ACTUALIZED);
                    obj.setCostChargesType(cost.getCostChargesType());
                    obj.setFinalInvoiced(getCostFinalInvoiceDone(plannedObligationDTO, actualizedCostObject.getActualizedCostId(), tenantId, token));
                } else {
                    BeanUtils.copyProperties(cost, obj);
                    obj.setFinalInvoiced(false);
                    obj.setCostChargesType(cost.getCostChargesType());
                    obj.setActualizedStatus(ESTIMATE);
                    obj.setCostFor(cost.getCostFor());
                    obj.setLinkTo(cost.getLinkTo());
                }
                obj.setCostId(cost.getCostId());
                obj.setPlannedObligationId(plannedObligationId);
                obj.setEstimated(true);
                obj.setSplitSequenceNumber(splitNumber);
                obj.setQuantityActualized(quantityActualized);
                obj.setCostMatrixWorkflow(cost.getCostMatrixWorkflow());
                obj.setCostMatrixId(cost.getCostMatrixId());
                finalList.add(obj);
            }
        }
        finalList.sort((first, second) -> (first.getCreatedTimestamp().isAfter(second.getCreatedTimestamp()) || first.getCreatedTimestamp().isEqual(second.getCreatedTimestamp())) ? -1 : 1);
        return finalList;
    }

    public List<ActualizationQualityObj> getQuality(String tenantId, String plannedObligationId, String token) {
        List<ActualizationQualityObj> finalList = new ArrayList<>();
        List<ActualizedQuality> qualityObligationsList = actualizationQualityRepo.findAllByPlannedObligationIdAndTenantId(plannedObligationId, tenantId);
        List<String> qualityIds = new ArrayList<>();
        for (ActualizedQuality quality : qualityObligationsList) {
            qualityIds.add(quality.getName());
        }
        ActualizedQuality actualizedObject;
        PlannedObligationDTO plannedObligationDTO = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT +
                        GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + "?plannedObligationId=" + plannedObligationId + "&tenantId=" + tenantId,
                HttpMethod.GET, token, null, PlannedObligationDTO.class);
        List<QualitySpecDTO> tradeQualitySpecs = TransactionIdUtil.queryList(baseUrl + QUALITY_SPECIFICATION_ROOT +
                        GET_QUALITY_SPEC_BY_TRADE_ID + "?tenantId=" + tenantId
                        + "&tradeId=" + plannedObligationDTO.getTradeId(),
                HttpMethod.GET, token, null, QualitySpecDTO.class);
        var status = TransactionIdUtil.getInvoiceStatus(baseUrl, plannedObligationId, plannedObligationDTO.getTradeId(), tenantId, token, plannedObligationDTO.getSplitSequenceNumber());
        Boolean quantityFinalInvoiceDone = Arrays.asList(INVOICE_GENERATED, INVOICE_FINAL_PROVISIONAL,INVOICE_PENDING_FINAL,INVOICE_PROVISIONAL).contains(status);
        Boolean quantityActualized = isQuantityActualized(tenantId, plannedObligationId);
        for (QualitySpecDTO qualitySpec : tradeQualitySpecs) {
            ActualizationQualityObj obj = new ActualizationQualityObj();
            if (qualityIds.contains(qualitySpec.getName())) {
                int index = qualityIds.indexOf(qualitySpec.getName());
                actualizedObject = qualityObligationsList.get(index);
                BeanUtils.copyProperties(actualizedObject, obj);
                obj.setPremiumDiscount(actualizedObject.getPremiumDiscount());
                obj.setClaimedBasis(actualizedObject.getClaimedBasis());
                obj.setClaimedPremiumDiscount(actualizedObject.getClaimedPremiumDiscount());
                obj.setActualizedStatus(ACTUALIZED);
                obj.setBasis(actualizedObject.getBasis());
                obj.setFinalInvoicedQuality(getQualityFinalInvoiceDone(plannedObligationDTO, obj, tenantId, token));
                qualityObligationsList.remove(index);
                qualityIds.remove(index);
            } else {
                BeanUtils.copyProperties(qualitySpec, obj);
                obj.setPremiumDiscount(0);
                obj.setBasis(0.0);
                if(qualitySpec.getBasis() != null && !qualitySpec.getBasis().isEmpty()) {
                    obj.setBasis(Double.parseDouble(qualitySpec.getBasis()));

                }
                obj.setActualizedStatus(CONTRACTED);
            }
            obj.setQualitySpecId(qualitySpec.getId());
            obj.setTradeTransactionType(plannedObligationDTO.getTradeTransactionType());
            obj.setQuantityActualized(quantityActualized);
            obj.setFinalInvoicedQuantity(quantityFinalInvoiceDone);
            obj.setPlannedObligationId(plannedObligationDTO.getPlannedObligationId());
            obj.setTradeId(plannedObligationDTO.getTradeId());
            finalList.add(obj);
        }
        if (qualityObligationsList.size() > 0) {
            for (ActualizedQuality quality : qualityObligationsList) {
                ActualizationQualityObj obj = new ActualizationQualityObj();
                BeanUtils.copyProperties(quality, obj);
                obj.setTradeTransactionType(plannedObligationDTO.getTradeTransactionType());
                obj.setPlannedObligationId(plannedObligationDTO.getPlannedObligationId());
                obj.setTradeId(plannedObligationDTO.getTradeId());
                obj.setActualizedStatus(ACTUALIZED);
                obj.setQuantityActualized(quantityActualized);
                obj.setFinalInvoicedQuantity(quantityFinalInvoiceDone);
                obj.setQualitySpecId(quality.getEstimatedQualitySpecId());
                obj.setFinalInvoicedQuality(getQualityFinalInvoiceDone(plannedObligationDTO, obj, tenantId, token));
                finalList.add(0, obj);
            }
        }
        Collections.sort(finalList, new Comparator<ActualizationQualityObj>() {
            @Override
            public int compare(ActualizationQualityObj first, ActualizationQualityObj second) {
                return (first.getCreatedTimestamp().isAfter(second.getCreatedTimestamp()) || first.getCreatedTimestamp().isEqual(second.getCreatedTimestamp())) ? -1 : 1;
            }
        });
        return finalList;
    }

    private boolean getQualityFinalInvoiceDone(PlannedObligationDTO plannedObligationDTO, ActualizationQualityObj actualizedObject, String tenantId, String token) {
        List<SearchCriteria> searchCriteriaList = new ArrayList<>();
        searchCriteriaList.add(new SearchCriteria(TENANT_ID, "equals", tenantId));
        searchCriteriaList.add(new SearchCriteria("plannedObligationId", "equals", plannedObligationDTO.getPlannedObligationId()));
        searchCriteriaList.add(new SearchCriteria("invoiceStatus", "in", Arrays.asList(APPROVED, SETTLED)));
        searchCriteriaList.add(new SearchCriteria("actualizationObjectId", "equals", actualizedObject.getActualizedQualityId()));
        searchCriteriaList.add(new SearchCriteria("type", "in", Arrays.asList(CASHFLOW_TYPE_PREMIUM, CASHFLOW_TYPE_DISCOUNT)));
        searchCriteriaList.add(new SearchCriteria("cashflowStatus", "equals", ACTIVE));
        List<CashflowDataDTO> cashflowDataResponse = TransactionIdUtil.queryList(baseUrl + CASHFLOW_ROOT + GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaList, CashflowDataDTO.class);
        return cashflowDataResponse != null && cashflowDataResponse.size() != 0;
    }

    private boolean getCostFinalInvoiceDone(PlannedObligationDTO plannedObligationDTO, String costId, String tenantId, String token) {
        List<SearchCriteria> searchCriteriaList = new ArrayList<>();
        searchCriteriaList.add(new SearchCriteria(TENANT_ID, "equals", tenantId));
        searchCriteriaList.add(new SearchCriteria("plannedObligationId", "equals", plannedObligationDTO.getPlannedObligationId()));
        searchCriteriaList.add(new SearchCriteria("invoiceStatus", "in", Arrays.asList(APPROVED, SETTLED)));
        searchCriteriaList.add(new SearchCriteria("costId", "equals", costId));
        searchCriteriaList.add(new SearchCriteria("cashflowStatus", "equals", ACTIVE));
        List<CashflowDataDTO> cashflowDataResponse = TransactionIdUtil.queryList(baseUrl + CASHFLOW_ROOT + GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaList, CashflowDataDTO.class);
        return cashflowDataResponse != null && cashflowDataResponse.size() != 0;
    }

    public ActualizationQuantityObj getQuantity(String tenantId, String plannedObligationId, Double splitNumber, String token) {
        ActualizationQuantityObj finalObject = new ActualizationQuantityObj();
        List<ActualizedQuantityObligations> quantityObligationsList;
        if (splitNumber != null) {
            quantityObligationsList = actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationIdAndSplitSequenceNumber(tenantId, plannedObligationId, splitNumber.intValue());
        } else {
            quantityObligationsList = actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationId(tenantId, plannedObligationId);
        }
        PlannedObligationDTO plannedObligation = TransactionIdUtil.query(baseUrl +
                        PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + "?plannedObligationId=" + plannedObligationId + "&tenantId=" + tenantId,
                HttpMethod.GET, token, null, PlannedObligationDTO.class);
        if (!quantityObligationsList.isEmpty()) {
            finalObject.setLoadQuantity(quantityObligationsList.get(0).getLoadQuantity());
            finalObject.setUnloadQuantity(quantityObligationsList.get(0).getUnloadQuantity());
            finalObject.setPlannedQuantity(quantityObligationsList.get(0).getPlannedQuantity());
            finalObject.setQuantityUom(quantityObligationsList.get(0).getQuantityUom());
            finalObject.setBrand(quantityObligationsList.get(0).getBrand());
            finalObject.setGrade(quantityObligationsList.get(0).getGrade());
            finalObject.setOrigin(quantityObligationsList.get(0).getOrigin());
            finalObject.setCommodity(quantityObligationsList.get(0).getCommodity());
            finalObject.setCreatedBy(quantityObligationsList.get(0).getCreatedBy());
            finalObject.setPurpose(quantityObligationsList.get(0).getPurpose());
            finalObject.setActualizationEventMapping(getEventList(quantityObligationsList.get(0).getActualizationEventMapping()));
            finalObject.setClaimedQuantity(quantityObligationsList.get(0).getClaimedQuantity());
            finalObject.setCreatedTimestamp(quantityObligationsList.get(0).getCreatedTimestamp());
            finalObject.setUpdatedTimestamp(quantityObligationsList.get(0).getUpdatedTimestamp());
            finalObject.setActualizationId(quantityObligationsList.get(0).getActualizationId());
        } else {
            finalObject.setLoadQuantity(plannedObligation.getPlannedQuantity());
            finalObject.setUnloadQuantity(plannedObligation.getPlannedQuantity());
            finalObject.setPlannedQuantity(plannedObligation.getPlannedQuantity());
            finalObject.setQuantityUom(plannedObligation.getQuantityUOM());
            finalObject.setBrand(plannedObligation.getBrand());
            finalObject.setGrade(plannedObligation.getGrade());
            finalObject.setOrigin(plannedObligation.getCropOrigin());
            finalObject.setCommodity(plannedObligation.getCommodity());
            finalObject.setActualizationEventMapping(new ArrayList<>());
        }
        finalObject.setDeliveryEndDate(plannedObligation.getDeliveryEndDate());
        finalObject.setDeliveryStartDate(plannedObligation.getDeliveryStartDate());
        finalObject.setTradeId(plannedObligation.getTradeId());
        finalObject.setPlannedObligationId(plannedObligation.getPlannedObligationId());
        finalObject.setTolerance(plannedObligation.getToleranceValue());
        finalObject.setPackageType(plannedObligation.getPackageType());
        if (plannedObligation.getExternalPackage() != null && !plannedObligation.getExternalPackage().isEmpty()) {
            finalObject.setExternalPackage(plannedObligation.getExternalPackage());
            finalObject.setExternalPackageUnit(plannedObligation.getExternalPackageUnit());
        }
        if (plannedObligation.getInternalPackage() != null && !plannedObligation.getInternalPackage().isEmpty()) {
            finalObject.setInternalPackage(plannedObligation.getInternalPackage());
            finalObject.setInternalPackageUnit(plannedObligation.getInternalPackageUnit());
        }
        return finalObject;
    }

    /**
     * @param tenantId
     * @param plannedObligationId
     * @return
     */
    public Object getAllAttachedDOC(String tenantId, String plannedObligationId) {
        logger.info("Entered ActualizationService.getAllAttachedDOC()");
        List<ActualizedDocuments> actualizedDocumentsList = null;
        try {
            actualizedDocumentsList = null;
            if (plannedObligationId == null || plannedObligationId.isEmpty()) {
                logger.info("PlannedObligation Id is empty");
                return actualizedDocumentsList;
            }
            actualizedDocumentsList = actualizationDocumentsRepo.findAllByTenantIdAndPlannedObligationId(tenantId, plannedObligationId);
        } catch (Exception e) {
            logger.error("Failed to fetch all the documents attached: ",e);
        }
        logger.info("Entered ActualizationService.getAllAttachedDOC()");
        return actualizedDocumentsList;
    }

    @Deprecated
    private PlanTradeActualizationObj getPlanTradeActualizeObj(String planId, String planType, String tenantId, String token) {
        int notActualizedCount = 0;
        int actualizedCount = 0;
        int settledCount = 0;
        double buyPlannedQuantity = 0.0;
        double sellPlannedQuantity = 0.0;
        PlanTradeActualizationObj planTradeActualizationObj = null;
        try {
            planTradeActualizationObj = new PlanTradeActualizationObj();
            planTradeActualizationObj.setPlanId(planId);
            List<PlannedObligationDTO> buySellList = TransactionIdUtil.queryList(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLAN_ID
                    + "?planId=" + planId + "&tenantId=" + tenantId, HttpMethod.GET, token, null, PlannedObligationDTO.class);
            int totalCount = buySellList.size();
            for (PlannedObligationDTO plannedObligation : buySellList) {
                Boolean actualizedState = plannedObligation.getObligationState().get(PlannedObligationState.ACTUALIZED);
                Boolean settleState = plannedObligation.getObligationState().get(PlannedObligationState.SETTLED);
                if (actualizedState) {
                    actualizedCount++;
                }
                if (settleState) {
                    settledCount++;
                }
                if (!actualizedState && !settleState) {
                    notActualizedCount++;
                }
                if (Arrays.asList(STRING, CIRCLE).contains(planType)) {
                    if (plannedObligation.getTradeTransactionType().equals(TradeTransactionType.BUY)) {
                        buyPlannedQuantity = plannedObligation.getPlannedQuantity();
                    } else {
                        sellPlannedQuantity = plannedObligation.getPlannedQuantity();
                    }
                } else {
                    if (plannedObligation.getTradeTransactionType().equals(TradeTransactionType.BUY)) {
                        buyPlannedQuantity += plannedObligation.getPlannedQuantity();
                    } else {
                        sellPlannedQuantity += plannedObligation.getPlannedQuantity();
                    }
                }
            }
            planTradeActualizationObj.setObligations(new ArrayList());
            planTradeActualizationObj.setBuyPlannedQuantity(buyPlannedQuantity);
            planTradeActualizationObj.setSellPlannedQuantity(sellPlannedQuantity);
            if (settledCount == 0 && actualizedCount == 0) {
                planTradeActualizationObj.setStatus("PENDING");
            } else {
                if (actualizedCount == totalCount) {
                    planTradeActualizationObj.setStatus(PlannedObligationState.ACTUALIZED.toString());
                } else if (settledCount == totalCount) {
                    planTradeActualizationObj.setStatus(PlannedObligationState.SETTLED.toString());
                } else {
                    planTradeActualizationObj.setStatus("PARTIALLY ACTUALIZED");
                }
            }
            planTradeActualizationObj.setMatchType(planType);
        } catch (Exception e) {
            logger.error("failed to get Plan Trade Actualize Obj",e);
        }
        return planTradeActualizationObj;
    }

    private boolean isQuantityActualized(String tenantId, String plannedObligationId) {
        List<ActualizedQuantityObligations> actualizedQuantityObligations = actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationId(tenantId, plannedObligationId);
        return (actualizedQuantityObligations.size() > 0);
    }

    public Object getPlanTradeListByPlanId(String tenantId, String planId, String token) {
        List<PlanTradeActualizationObj> planTradeActualizationObjList = new ArrayList<>();
        PlanViewActualization planViewActualization = actualizePlanViewRepo.findByTenantIdAndPlanId(tenantId, planId);
        planTradeActualizationObjList.add(getRowItem(planViewActualization, token));
        return planTradeActualizationObjList;
    }

    /**
     * @param response
     * @param tenantId
     * @param attachmentName
     * @return
     */
    public ResponseEntity getDocumentByAttachmentName(HttpServletResponse response, String tenantId, String attachmentName) {
        logger.info("Entered into ActualizationService.getDocumentByAttachmentName");
        ResponseEntity responseEntity = null;
        try {
            ActualizedDocuments document = actualizationDocumentsRepo.findByTenantIdAndAttachmentFileName(tenantId, attachmentName).get(0);
            response.setContentType(document.getAttachmentFileType());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + document.getAttachmentFileName());
            //response.getOutputStream().write(document.getAttachment());
            responseEntity = new ResponseEntity("Document downloaded successfully with attachmentName: " + attachmentName, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Document download failed for the attachmentName: " + attachmentName + " because of following exception: ",e);
        }
        logger.info("Exited from ActualizationService.getDocumentByAttachmentName");
        return responseEntity;
    }

    public ResponseEntity getCostForTransportActualization(TransportActualizationDataFetchObj transportActualizationDataFetchObj, String token, String tenantId) {
        logger.info("Entered into ActualizationService.getCostForTransportActualization");
        ResponseEntity responseEntity = null;
        List<ActualizeCostObj> finalList = new ArrayList<>();
        for (String plannedObligationId : transportActualizationDataFetchObj.getPlannedObligationIds()) {
            finalList.addAll(getCost(plannedObligationId, token, tenantId));
        }
        logger.info("Exited from ActualizationService.getCostForTransportActualization");
        return new ResponseEntity(finalList, HttpStatus.OK);
    }

    public List<ActualizedDocuments> getAllAttachedDocForTransportActualization(String tenantId, TransportActualizationDataFetchObj transportActualizationDataFetchObj) {
        logger.info("Entered ActualizationService.getAllAttachedDocForTransportActualization()");
        List<ActualizedDocuments> actualizedDocumentsList = null;
        actualizedDocumentsList = actualizationDocumentsRepo.findAllByTenantIdAndPlannedObligationIdInOrderByUpdatedTimestampDesc(tenantId, transportActualizationDataFetchObj.getPlannedObligationIds());
        logger.info("Entered ActualizationService.getAllAttachedDocForTransportActualization()");
        return actualizedDocumentsList;
    }

    public List<ActualizationQualityObj> getQualityForTransportActualization(String tenantId, TransportActualizationDataFetchObj transportActualizationDataFetchObj, String token) {
        logger.info("Entered ActualizationService.getQualityForTransportActualization()");
        List<ActualizationQualityObj> actualizationQualityObjs = new ArrayList<>();
        for (String plannedObligationId : transportActualizationDataFetchObj.getPlannedObligationIds()) {
            actualizationQualityObjs.addAll(getQuality(tenantId, plannedObligationId, token));
        }
        logger.info("Entered ActualizationService.getQualityForTransportActualization()");
        return actualizationQualityObjs;
    }

    public ResponseEntity getByCriteria(List<SearchCriteria> searchBuilder, String tenantId, String operation, int page, int size, String token) {
        logger.info("Entered into ActualizetionService.getByCriteria");
        ResponseEntity responseEntity;
        List<SearchCriteria> PlanTableSearchCriteria = new ArrayList<>();
        List<SearchCriteria> stockPlanTableSearchCriteria = new ArrayList<>();
        List<SearchCriteria> finalSearchCriteria = new ArrayList<>();
        SearchCriteria tenantIdSearchCriteria = null;
        List<PlanTradeActualizationObj> planTradeActualizationObjList = new ArrayList<>();
        PlanTradeActualizationObj tempObj = null;
        Boolean isTenantId = false;
        try {
            for (SearchCriteria searchCriteria : searchBuilder) {
                if (searchCriteria.getFieldName().equals(TENANT_ID)) {
                    isTenantId = true;
                    tenantIdSearchCriteria = searchCriteria;
                    break;
                }
            }
            if (!isTenantId) {
                return ResponseEntity.badRequest().body("No TenantId Provided");
            }
            for (SearchCriteria searchCriteria : searchBuilder) {
                if (!searchCriteria.getFieldName().equals(TENANT_ID)) {
                    if (searchCriteria.getFieldName().equalsIgnoreCase("planId")) {
                        searchCriteria.setFieldName("planningId");
                        PlanTableSearchCriteria.add(searchCriteria);
                        stockPlanTableSearchCriteria.add(searchCriteria);
                    } else if (searchCriteria.getFieldName().equalsIgnoreCase("matchType")) {
                        PlanTableSearchCriteria.add(searchCriteria);
                        stockPlanTableSearchCriteria.add(new SearchCriteria("stockPlanType", searchCriteria.getCondition(), searchCriteria.getValue()));
                    } else {
                        finalSearchCriteria.add(searchCriteria);
                    }
                }
            }
            finalSearchCriteria.add(tenantIdSearchCriteria);
            if (PlanTableSearchCriteria.size() == 0 && stockPlanTableSearchCriteria.size() == 0) {
                List<PhysicalTradePlanningDTO> tradePlanningList = TransactionIdUtil.queryList(baseUrl + PHYSICAL_TRADE_PLANNING_ROOT + GET_ALL_PHYSICAL_TRADE_PLANNING + "?tenantId=" + tenantId,
                        HttpMethod.GET, token, null, PhysicalTradePlanningDTO.class);
                for (PhysicalTradePlanningDTO tradePlanning : tradePlanningList) {
                    tempObj = getPlanTradeActualizeObj(tradePlanning.getPlanningId(), tradePlanning.getMatchType(), tenantId, token);
                    if (finalSearchCriteria != null && finalSearchCriteria.size() > 1) {
                        if (canPassFilterCheck(tempObj, finalSearchCriteria)) {
                            planTradeActualizationObjList.add(tempObj);
                        }
                    } else {
                        planTradeActualizationObjList.add(tempObj);
                    }
                }
                List<StockDTO> stockDTOList = TransactionIdUtil.queryList(baseUrl + STOCK_ROOT
                        + GET_ALL_STOCK_PLAN + "?tenantId=" + tenantId, HttpMethod.GET, token, null, StockDTO.class);
                for (StockDTO stockDTO : stockDTOList) {
                    tempObj = getPlanTradeActualizeObj(stockDTO.getPlanningId(), stockDTO.getStockPlanType().toString(), tenantId, token);
                    if (finalSearchCriteria != null && finalSearchCriteria.size() > 1) {
                        if (canPassFilterCheck(tempObj, finalSearchCriteria)) {
                            planTradeActualizationObjList.add(tempObj);
                        }
                    } else {
                        planTradeActualizationObjList.add(tempObj);
                    }
                }

            } else {
                if (PlanTableSearchCriteria.size() > 0) {
                    PlanTableSearchCriteria.add(tenantIdSearchCriteria);
                    List<PhysicalTradePlanningDTO> tradePlanningList = TransactionIdUtil.queryList(baseUrl + PHYSICAL_TRADE_PLANNING_ROOT + GET_PLAN_INFO__BY_CRITERIA + "?tenantId=" + tenantId, HttpMethod.POST, token, PlanTableSearchCriteria, PhysicalTradePlanningDTO.class);
                    for (PhysicalTradePlanningDTO tradePlanning : tradePlanningList) {
                        tempObj = getPlanTradeActualizeObj(tradePlanning.getPlanningId(), tradePlanning.getMatchType(), tenantId, token);
                        if (finalSearchCriteria != null && finalSearchCriteria.size() > 1) {
                            if (canPassFilterCheck(tempObj, finalSearchCriteria)) {
                                planTradeActualizationObjList.add(tempObj);
                            }
                        } else {
                            planTradeActualizationObjList.add(tempObj);
                        }
                    }
                }
                if (stockPlanTableSearchCriteria.size() > 0) {
                    stockPlanTableSearchCriteria.add(tenantIdSearchCriteria);
                    List<StockDTO> stockPlanningList = TransactionIdUtil.queryList(baseUrl + STOCK_ROOT + GET_ALL_STOCK_PLAN_BY_CRITERIA + "?tenantId=" + tenantId, HttpMethod.POST, token, stockPlanTableSearchCriteria, StockDTO.class);
                    for (StockDTO stockDTO : stockPlanningList) {
                        tempObj = getPlanTradeActualizeObj(stockDTO.getPlanningId(), stockDTO.getStockPlanType().toString(), tenantId, token);
                        if (finalSearchCriteria != null && finalSearchCriteria.size() > 1) {
                            if (canPassFilterCheck(tempObj, finalSearchCriteria)) {
                                planTradeActualizationObjList.add(tempObj);
                            }
                        } else {
                            planTradeActualizationObjList.add(tempObj);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Something went wrong during Actualization fetching, error is: ",ex);
            responseEntity = new ResponseEntity("Something went wrong during Actualization fetch, error is", HttpStatus.BAD_REQUEST);
        }
        Pageable paging = PageRequest.of(page, size);
        Page<PlanTradeActualizationObj> planTradeActualizationObjPage = new PageImpl<>(planTradeActualizationObjList, paging, planTradeActualizationObjList.size());
        responseEntity = new ResponseEntity<>(planTradeActualizationObjPage, HttpStatus.OK);
        return responseEntity;
    }

    private boolean canPassFilterCheck(PlanTradeActualizationObj tempObj, List<SearchCriteria> finalSearchCriteria) {
        boolean canPass = true;
        for (SearchCriteria searchCriteria : finalSearchCriteria) {
            if (searchCriteria.getFieldName().equalsIgnoreCase("buyPlannedQuantity")) {
                if (!TransactionIdUtil.checkSearchCriteriaMatchForObject(searchCriteria.getFieldName(), searchCriteria.getCondition(),
                        searchCriteria.getValue(), tempObj.getBuyPlannedQuantity())) {
                    canPass = false;
                    break;
                }
            } else if (searchCriteria.getFieldName().equalsIgnoreCase("sellPlannedQuantity")) {
                if (!TransactionIdUtil.checkSearchCriteriaMatchForObject(searchCriteria.getFieldName(), searchCriteria.getCondition(),
                        searchCriteria.getValue(), tempObj.getSellPlannedQuantity())) {
                    canPass = false;
                    break;
                }

            } else if (searchCriteria.getFieldName().equalsIgnoreCase("status")) {
                if (!TransactionIdUtil.checkSearchCriteriaMatchForObject(searchCriteria.getFieldName(), searchCriteria.getCondition(),
                        searchCriteria.getValue(), tempObj.getStatus())) {
                    canPass = false;
                    break;
                }

            }
        }
        return canPass;
    }

    public ActualizedDocuments saveActualizedDocument(ActualizedDocumentsDTO actualizedDocumentsDTO, String tenantId) throws TaomishError{
        try {
            ActualizedDocuments actualizedDocuments = new ActualizedDocuments();
            BeanUtils.copyProperties(actualizedDocumentsDTO, actualizedDocuments);
            actualizedDocuments.setTenantId(tenantId);
            actualizationDocumentsRepo.save(actualizedDocuments);
            return actualizedDocuments;
        } catch (Exception e) {
            logger.error("Failed to saveActualizedDocument",e);
            throw new TaomishError("Failed to saveActualizedDocument");
        }
    }

    @SneakyThrows
    public List<ActualizedCost> getCostByCriteria(List<SearchCriteria> searchCriteria, User user) {
        try {
            var specBuilder = new SpecBuilderUtil().with(user).from(actualizedCostRepo).setCriteriaList(searchCriteria);
            return specBuilder.sortDesc("createdTimestamp").findAll(ActualizedCost.class);
        } catch (Exception ex) {
            logger.error("Something went wrong during Cost fetching, error is:", ex);
            throw new TaomishError("Failed to fetch Cost with Criteria");
        }
    }

    public List<ActualizedQuantityObligations> getAllActualizedQuantityList(String plannedObligationId, User user) {
        return new SpecBuilderUtil().with(user.getTenantId())
                .addCriteria(new SearchCriteria("plannedObligationId", "equals", plannedObligationId))
                .from(actualizationQuantityRepo).findAll(ActualizedQuantityObligations.class);
    }

    @SneakyThrows
    public List<ActualizedQuality> getActualizedQualityByCriteria(List<SearchCriteria> searchCriteria, User user) {

        try {
            var specBuilder = new SpecBuilderUtil().with(user.getTenantId()).from(actualizationQualityRepo).setCriteriaList(searchCriteria);
            return specBuilder.sortDesc("createdTimestamp").findAll(ActualizedQuality.class);
        } catch (Exception ex) {
            logger.error("Something went wrong during Quality fetching, error is:", ex);
            throw new TaomishError("Failed to fetch Quality with Criteria");
        }
    }

    public Page<ActualizedDocuments> getallattacheddocfortransportactualizationbycriteria(List<SearchCriteria> searchBuilder, String tenantId, String operation, int page, int size, String token) throws TaomishError {
        try{
            boolean isTradeListEmpty = searchBuilder.stream()
                    .anyMatch(e -> e.getFieldName().equalsIgnoreCase("plannedObligationId") && ObjectUtils.isEmpty(e.getValue()));
            if(isTradeListEmpty)return Page.empty();
            var specBuilder = new SpecBuilderUtil().with(tenantId).from(actualizationDocumentsRepo).setCriteriaList(searchBuilder);
            specBuilder.setupPageDesc(page,size,"createdTimestamp");
            return specBuilder.findPage(ActualizedDocuments.class);
        }catch (Exception ex){
            logger.error("Failed to get Actualized Documents By Criteria and Error is :-",ex);
            throw  new TaomishError("ActualizedDocuments-001",ex);
        }
    }

    public String updateDocumentStatus(List<String> documentUuidList, String tenantId) throws TaomishError {
        List<UUID> tempList=documentUuidList.stream().map(e->UUID.fromString(e)).toList();
        List<ActualizedDocuments> actualizedDocumentsList=actualizationDocumentsRepo.findAllByTenantIdAndUuidIn(tenantId,tempList);
        try {
            for (ActualizedDocuments actualizedDocuments : actualizedDocumentsList){
                actualizedDocuments.setStatus(CONFIRMED);
            }
            actualizationDocumentsRepo.saveAll(actualizedDocumentsList);
            return "Document Status Updated Successfully !!";
        }catch (Exception ex){
            throw  new TaomishError("Failed to update document status , Error : ",ex);
        }
    }
}
