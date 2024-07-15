package com.taomish.actualization.v2.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.taomish.actualization.dto.ActualizeCostObj;
import com.taomish.actualization.model.ActualizedQuantityObligations;
import com.taomish.common.searchcriteria.SearchCriteria;
import com.taomish.common.searchcriteria.SpecBuilderUtil;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.dtos.cashflowservice.CashflowDataDTO;
import com.taomish.dtos.conversionservice.UomConversionOutputtDTO;
import com.taomish.dtos.oisservice.PurchaseOrderDto;
import com.taomish.dtos.physicaltradeservice.PlannedObligationDTO;
import com.taomish.enums.PlannedObligationState;
import com.taomish.enums.TradeTransactionType;
import com.taomish.services.CurrencyAndUOMConversionService.service.CurrencyConversionService;
import com.taomish.web.security.models.User;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static com.taomish.RestEndPoints.CashflowRestEndPoints.CASHFLOW_ROOT;
import static com.taomish.RestEndPoints.CashflowRestEndPoints.GET_CASHFLOW_BY_CRITERIA;
import static com.taomish.RestEndPoints.PhysicalRestEndPoint.EQUA_LS;
import static com.taomish.RestEndPoints.PhysicalRestEndPoint.TEN_ANT_ID;
import static com.taomish.RestEndPoints.PricingRestEndPoint.CASHFLOW_TYPE_PROVISIONAL;
import static com.taomish.constants.ActualizationConstants.*;
import static com.taomish.constants.CashflowConstants.*;
import static com.taomish.constants.CashflowConstants.DEFUNCT;
import static com.taomish.constants.PhysicalConstants.*;

@Service
public class ActualizationCashflowServiceV2 {

    @Value("${baseUrlEC2DEV}")
    private String baseUrl;

    @Value("${masterBaseURL}")
    private String masterBaseURL;

    public ActualizationCashflowServiceV2(CurrencyConversionService currencyConversionService) {
        this.currencyConversionService = currencyConversionService;
    }

    private final CurrencyConversionService currencyConversionService ;
    private static final Logger logger = LoggerFactory.getLogger(ActualizationCashflowServiceV2.class);

    private double getConversionFactor(PlannedObligationDTO plannedObligationDTO, String token, String tenantId) {
        UomConversionOutputtDTO uomConversionOutputtDTO = currencyConversionService.convertUom(plannedObligationDTO.getQuantityUOM(), plannedObligationDTO.getTradePriceUom(), 1.0, plannedObligationDTO.getCommodity(), tenantId, token);
        double conversionFactor = 1;
        if (uomConversionOutputtDTO.getStatus().equalsIgnoreCase(STATUS_OK)) {
            conversionFactor = uomConversionOutputtDTO.getConversionFactor();
        }
        return conversionFactor;
    }

    @NotNull
    private CashflowDataDTO getCommonPayloadFromPlannedObligation(PlannedObligationDTO plannedObligation, Integer splitSequenceNumber, String tenantId) {
        CashflowDataDTO cashflowDataDTO = new CashflowDataDTO();
        cashflowDataDTO.setTenantId(tenantId);
        cashflowDataDTO.setFinancialAmountType(plannedObligation.getTradeTransactionType() == TradeTransactionType.BUY?PAYABLE:RECEIVABLE);
        cashflowDataDTO.setCashflowStatus(ACTIVE);
        cashflowDataDTO.setProvisionalTradePrice(plannedObligation.getProvisionalPrice() != null?plannedObligation.getProvisionalPrice():0);
        cashflowDataDTO.setTradeTransactionType(plannedObligation.getTradeTransactionType());
        cashflowDataDTO.setObligationId(plannedObligation.getPlannedObligationId());
        cashflowDataDTO.setPaymentDate(null);
        cashflowDataDTO.setPaymentDueDate(plannedObligation.getDeliveryEndDate());
        cashflowDataDTO.setPlanId(plannedObligation.getPlanId());
        cashflowDataDTO.setPlannedObligationId(plannedObligation.getPlannedObligationId());
        cashflowDataDTO.setSettlementCurrency(plannedObligation.getTradeSettlementCurrency());
        cashflowDataDTO.setTradeId(plannedObligation.getTradeId());
        cashflowDataDTO.setTradePriceCurrency(plannedObligation.getTradePriceCurrency());
        cashflowDataDTO.setTradePriceUom(plannedObligation.getTradePriceUom());
        cashflowDataDTO.setTradeDateTime(plannedObligation.getTradeDateTime());
        cashflowDataDTO.setSplitSequenceNumber(splitSequenceNumber == null? 0 : splitSequenceNumber);
        cashflowDataDTO.setCommodity(plannedObligation.getCommodity());
        cashflowDataDTO.setCounterparty(plannedObligation.getCounterparty());
        cashflowDataDTO.setProfitcenter(plannedObligation.getProfitcenter());
        cashflowDataDTO.setQuantityUom(plannedObligation.getQuantityUOM());
        cashflowDataDTO.setCompany(plannedObligation.getCompany());
        cashflowDataDTO.setDocByPassId(plannedObligation.getDocByPassId());
        cashflowDataDTO.setFxRate(plannedObligation.getFxRate() == null? 1.0 : plannedObligation.getFxRate());
        cashflowDataDTO.setPriceType(plannedObligation.getPriceType());
        return cashflowDataDTO;
    }

    @NotNull
    private CashflowDataDTO patchPricingInfo(PlannedObligationDTO plannedObligation, CashflowDataDTO cashflowDataDTO,double manualPrice,String tenantId,String token) {
        var conversionFactor = getConversionFactor(plannedObligation,token,tenantId);
        var isPriced = true;
        JsonNode output = TransactionIdUtil.getFromTenantConfig(masterBaseURL,tenantId,token,List.of("roundingFormat","priceRounding"));
        var priceRoundingFormat = 2;
        if(output != null && (output.get("0") != null)) {
            priceRoundingFormat = output.get("0").asInt();
        }
        if (manualPrice == 0.0) {
            var tradePrice = plannedObligation.getPriceType().equalsIgnoreCase(DIFFERENTIAL) && Boolean.FALSE.equals(plannedObligation.getObligationState().get(PlannedObligationState.PRICED))?plannedObligation.getProvisionalPrice():plannedObligation.getTradePrice();
            if(Boolean.TRUE.equals(plannedObligation.getProvisionalPricing()) && Boolean.FALSE.equals(plannedObligation.getObligationState().get(PlannedObligationState.PRICED))) {
                tradePrice = plannedObligation.getProvisionalPrice();
            }
            cashflowDataDTO.setTradePrice(tradePrice);
        } else {
            cashflowDataDTO.setTradePrice(manualPrice);
        }
        cashflowDataDTO.setTradePrice(TransactionIdUtil.formatToDecimalPoint(cashflowDataDTO.getTradePrice(),priceRoundingFormat));
        if(List.of(PTBF,DIFFERENTIAL).contains(plannedObligation.getPriceType())) {
            isPriced = plannedObligation.getObligationState().get(PlannedObligationState.PRICED);
        }
        if(isPriced) {
            cashflowDataDTO.setPriceStatus(PRICED);
            cashflowDataDTO.setType(Trade);
            cashflowDataDTO.setStage(ACCRUED);
        } else {
            cashflowDataDTO.setPriceStatus(UNPRICED);
            cashflowDataDTO.setType(CASHFLOW_STAGE_PROVISIONAL);
            cashflowDataDTO.setStage(ACCRUED_PROVISIONAL);
        }
        var tradePriceValue = (Math.abs(cashflowDataDTO.getQuantity()) * conversionFactor) * cashflowDataDTO.getTradePrice();
        cashflowDataDTO.setFxRate(plannedObligation.getFxRate() == null? 1.0 : plannedObligation.getFxRate());
        cashflowDataDTO.setTradePriceValue(Math.abs(tradePriceValue));
        cashflowDataDTO.setSettlementValue(Math.abs(cashflowDataDTO.getTradePriceValue() * cashflowDataDTO.getFxRate()));
        return cashflowDataDTO;
    }

    public CashflowDataDTO getActualizeCashflowDTO(ActualizedQuantityObligations actualizedQuantityObligations, PlannedObligationDTO plannedObligation, String tenantId,String token) {
        CashflowDataDTO cashflowDataDTO = getCommonPayloadFromPlannedObligation(plannedObligation,actualizedQuantityObligations.getSplitSequenceNumber(),tenantId);
        cashflowDataDTO.setActualizationObjectId(actualizedQuantityObligations.getActualizationId());
        cashflowDataDTO.setQuantityStatus(ACTUAL);
        cashflowDataDTO.setQuantity(actualizedQuantityObligations.getLoadQuantity());
        return patchPricingInfo(plannedObligation, cashflowDataDTO,0.0,tenantId,token);
    }

    public CashflowDataDTO getClaimCashflowDTO(ActualizedQuantityObligations actualizedQuantityObligations, PlannedObligationDTO plannedObligation, String tenantId,String token) throws Exception {
        var cashflowFetchCriteria = new SpecBuilderUtil().with(tenantId)
                .addCriteria(new SearchCriteria(PLANNED_OBLGN_ID, EQ, plannedObligation.getPlannedObligationId()))
                .addCriteria(new SearchCriteria(STAGE, "in", List.of(ACCRUED,ACCRUED_PROVISIONAL)))
                .addCriteria(new SearchCriteria("type", "in", List.of(Trade,CASHFLOW_TYPE_PROVISIONAL)))
                .addCriteria(new SearchCriteria("splitSequenceNumber", "in", (actualizedQuantityObligations.getSplitSequenceNumber() > 1)?List.of(actualizedQuantityObligations.getSplitSequenceNumber()):List.of(actualizedQuantityObligations.getSplitSequenceNumber(),0)))
                .addCriteria(new SearchCriteria(CASH_FLOW_STATUS, EQ, ACTIVE))
                .getCriteriaList();
        var cashflows = TransactionIdUtil.queryCashflows(baseUrl,token,cashflowFetchCriteria,false);
        var notInvoicedCashflows = cashflows.stream().filter(item -> item.getInvoiceNumber() == null || item.getInvoiceNumber().isEmpty()).filter(item -> item.getAllocationId() == null || item.getAllocationId().isEmpty()).toList();
        CashflowDataDTO cashflowDataDTO;
        if(notInvoicedCashflows.isEmpty()) {
            cashflowDataDTO = getCommonPayloadFromPlannedObligation(plannedObligation,actualizedQuantityObligations.getSplitSequenceNumber(),tenantId);
        }  else {
            cashflowDataDTO = notInvoicedCashflows.getFirst();
        }
        cashflowDataDTO.setQuantityStatus(ACTUAL);
        cashflowDataDTO.setQuantity(actualizedQuantityObligations.getClaimedQuantity());
        return patchPricingInfo(plannedObligation, cashflowDataDTO,0.0,tenantId,token);
    }

    @SneakyThrows
    public CashflowDataDTO getExceedingAmountCashflow(PlannedObligationDTO plannedObligation, Integer splitSequenceNumber, double exceedingQuantity, double exceedingPrice, String tenantId, String token  ) {
        var cashflowFetchCriteria = new SpecBuilderUtil().with(tenantId)
                .addCriteria(new SearchCriteria(PLANNED_OBLGN_ID, EQ, plannedObligation.getPlannedObligationId()))
                .addCriteria(new SearchCriteria(STAGE, "in", List.of(ACCRUED,ACCRUED_PROVISIONAL)))
                .addCriteria(new SearchCriteria("type", "in", List.of(Trade,CASHFLOW_TYPE_PROVISIONAL)))
                .addCriteria(new SearchCriteria("splitSequenceNumber", "in", (splitSequenceNumber > 1)?List.of(splitSequenceNumber):List.of(splitSequenceNumber,0)))
                .addCriteria(new SearchCriteria(CASH_FLOW_STATUS, EQ, ACTIVE))
                .getCriteriaList();
        var cashflows = TransactionIdUtil.queryCashflows(baseUrl,token,cashflowFetchCriteria,false);
        var exceedingCashflow = cashflows.stream().filter(item -> item.getDescription() != null && item.getDescription().toLowerCase().contains("exceeding")).toList();
        CashflowDataDTO cashflowDataDTO = null;
        if(!exceedingCashflow.isEmpty()) {
            cashflowDataDTO = exceedingCashflow.getFirst();
        } else {
            cashflowDataDTO = getCommonPayloadFromPlannedObligation(plannedObligation, splitSequenceNumber,tenantId);
        }
        cashflowDataDTO.setDescription("Exceeding Amount "+ ((exceedingQuantity < 0)?"Min":"Max"));
        cashflowDataDTO.setQuantity(Math.abs(exceedingQuantity));
        cashflowDataDTO.setType(Trade);
        cashflowDataDTO.setStage(ACCRUED);
        cashflowDataDTO.setQuantityStatus(ACTUAL);
        cashflowDataDTO.setPriceStatus(PRICED);
        return patchPricingInfo(plannedObligation,cashflowDataDTO,exceedingPrice,tenantId,token);
    }

    public CashflowDataDTO getActualizedCostCashflowDTO(PlannedObligationDTO plannedObligation, ActualizeCostObj actualizeCostObj,String tenantId) {
        CashflowDataDTO cashflowDataDTO = getCommonPayloadFromPlannedObligation(plannedObligation,actualizeCostObj.getSplitSequenceNumber(),tenantId);
        cashflowDataDTO.setActualizationObjectId(actualizeCostObj.getActualizedCostId());
        cashflowDataDTO.setCostGroup(actualizeCostObj.getCostGroup());
        cashflowDataDTO.setCostId(actualizeCostObj.getCostId());
        cashflowDataDTO.setCostName(actualizeCostObj.getName());
        cashflowDataDTO.setCostType(actualizeCostObj.getCostType());
        cashflowDataDTO.setCostUom(actualizeCostObj.getUom());
        cashflowDataDTO.setCostValue(actualizeCostObj.getCostValue());
        return cashflowDataDTO;
    }

    public void createCashFlowForPurchaseOrder(PurchaseOrderDto purchaseOrderDto, double grnQuantity, boolean actualize, User user, String token){
        List<com.taomish.common.searchcriteria.SearchCriteria> searchCriteria = new ArrayList<>();
        searchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria("cashflowStatus", EQUA_LS, ACTIVE));
        searchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(TEN_ANT_ID, EQUA_LS, user.getTenantId()));
        searchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria("tradeId", EQUA_LS, purchaseOrderDto.getPoNumber()));
        searchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria("type", EQUA_LS, "Purchase Order"));
        searchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(STAGE, EQUA_LS, ACCRUED));
        List<CashflowDataDTO> cashflowData =  TransactionIdUtil.queryList(baseUrl + CASHFLOW_ROOT + GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteria, CashflowDataDTO.class);
        CashflowDataDTO cashflowDTO = new CashflowDataDTO();
        if(actualize){
            if(cashflowData.isEmpty()){
                var description = purchaseOrderDto.getPurchaseOrderDescription().getFirst();
                double tradePriceValue = description.getPricePerUnit().getUnit() * description.getQuantity().getUnit();

                cashflowDTO.setTradeId(purchaseOrderDto.getPoNumber());
                cashflowDTO.setTenantId(user.getTenantId());
                cashflowDTO.setCashflowStatus(ACTIVE);
                cashflowDTO.setType(CASHFLOW_TYPE_PURCHASE_ORDER);
                cashflowDTO.setTradePriceCurrency(description.getPricePerUnit().getUom());
                cashflowDTO.setCommodity(description.getCommodity());
                cashflowDTO.setCompany(purchaseOrderDto.getCompanyName());
                cashflowDTO.setCounterparty(purchaseOrderDto.getCounterpartyName());
                cashflowDTO.setFinancialAmountType(PAYABLE);
                cashflowDTO.setTradePriceValue(Math.abs(tradePriceValue));
                cashflowDTO.setSettlementValue(cashflowDTO.getTradePriceValue());
                cashflowDTO.setProfitcenter(purchaseOrderDto.getProfitCenter());
                cashflowDTO.setPaymentDueDate(LocalDateTime.now());
                cashflowDTO.setStage(ACCRUED);
                cashflowDTO.setTradeDateTime(purchaseOrderDto.getPoDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                cashflowDTO.setQuantityUom(description.getQuantity().getUom());
                cashflowDTO.setSettlementCurrency(description.getPricePerUnit().getUom());
                cashflowDTO.setQuantityStatus(ESTIMATED);
                cashflowDTO.setPriceStatus(PRICED);
                cashflowDTO.setTradePrice(description.getPricePerUnit().getUnit());
                cashflowDTO.setTradePriceUom(description.getPricePerUnit().getUom());
                cashflowDTO.setTradePriceCurrency(description.getPricePerUnit().getUom());
                cashflowDTO.setQuantity(grnQuantity);
            }else{
                cashflowDTO = cashflowData.getFirst();
                cashflowData.getFirst().setQuantity(cashflowDTO.getQuantity() + grnQuantity);
            }
        }
        else if(!actualize){
            cashflowDTO = cashflowData.getFirst();
            cashflowData.getFirst().setQuantity(cashflowDTO.getQuantity() - grnQuantity);
            if(cashflowData.getFirst().getQuantity() <= 0)cashflowData.getFirst().setStage(DEFUNCT);
        }
        try{
            TransactionIdUtil.query(baseUrl+"/api/cashflow/v1/savecashflow", HttpMethod.POST,token,cashflowDTO, CashflowDataDTO.class);
        }
        catch (Exception ex){
            logger.error("failed to save the cashflow for purchase order " + purchaseOrderDto.getPoNumber(), ex);
        }
    }
}
