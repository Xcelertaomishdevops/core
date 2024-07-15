package com.taomish.actualization.service;

import com.taomish.common.domain.TaomishError;
import com.taomish.common.searchcriteria.SearchCriteria;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.dtos.ReturnStatus;
import com.taomish.dtos.cashflowservice.CashflowDataDTO;
import com.taomish.dtos.cashflowservice.UpdateCashflowDTO;
import com.taomish.dtos.conversionservice.UomConversionOutputtDTO;
import com.taomish.dtos.tradepricingservice.PriceAllocationCheckDTO;
import com.taomish.enums.TradeTransactionType;
import com.taomish.services.CurrencyAndUOMConversionService.service.CurrencyConversionService;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.taomish.RestEndPoints.CashflowRestEndPoints.*;
import static com.taomish.RestEndPoints.PricingRestEndPoint.GET_PRICE_ALLOCATED_CHECK_BY_PLANNED_OBLIGATION_ID;
import static com.taomish.RestEndPoints.PricingServiceRestEndPoints.ADVANCE_ALLOCATION_ROOT;
import static com.taomish.constants.CashflowConstants.*;
import static com.taomish.constants.PhysicalConstants.*;
import static com.taomish.constants.TradeCostConstants.PERCENTAGE_SIMPLE;
import static com.taomish.constants.TradeCostConstants.*;

@Service
public class ActualizationCashflowService {

    private static final Logger logger = LoggerFactory.getLogger(ActualizationCashflowService.class);

    @Value("${baseUrlEC2DEV}")
    protected String baseUrl;

    @Value("${masterBaseURL}")
    private String masterBaseURL;

    private final CurrencyConversionService currencyConversionService;
    @Value("${jwt.secret}")
    private String jwtSecretKey;

    public ActualizationCashflowService(CurrencyConversionService currencyConversionService) {
        this.currencyConversionService = currencyConversionService;
    }

    /**
     * @param updateCashflowDTO
     * @param token
     * @param tenantId
     * @return
     */
    public CashflowDataDTO runCreatCashflows(UpdateCashflowDTO updateCashflowDTO, String token, String tenantId) throws Exception {
        try {
            return this.createCashflow(updateCashflowDTO.getTradeId(), updateCashflowDTO.getCounterparty(), updateCashflowDTO.getCommodity(),
                    updateCashflowDTO.getTradeTransactionType(), updateCashflowDTO.getTradeSettlementCurrency(), updateCashflowDTO.getTradePriceCurrency(),
                    updateCashflowDTO.getPrice(), updateCashflowDTO.getPriceType(), updateCashflowDTO.getFxRate(), updateCashflowDTO.getTotalContractQuantity(),
                    updateCashflowDTO.getCostId(), updateCashflowDTO.getCostType(), updateCashflowDTO.getCostValue(), updateCashflowDTO.getQuantityOption(),
                    updateCashflowDTO.getObligationId(), updateCashflowDTO.getObligationQuantity(), updateCashflowDTO.getPlanId(), updateCashflowDTO.getPlannedObligationId(),
                    updateCashflowDTO.getDeliveryDate(), updateCashflowDTO.getStage(), updateCashflowDTO.getType(), updateCashflowDTO.getOriginalInvoice(),
                    token, tenantId, updateCashflowDTO.getSplitSequenceNumber(), updateCashflowDTO.getDescription(), updateCashflowDTO.getPremiumDiscount(),
                    updateCashflowDTO.getActualizationObjectId(), updateCashflowDTO.getQuantityUom(), updateCashflowDTO.getProfitcenter(),
                    updateCashflowDTO.getTradeDateTime(), updateCashflowDTO.getTradePriceUom(), updateCashflowDTO.getCompany(), updateCashflowDTO.getCostName(), updateCashflowDTO.getCostGroup(),updateCashflowDTO.getDocByPassId());
        } catch (Exception e) {
            throw new TaomishError(e.getMessage());
        }
    }

    public CashflowDataDTO createCashflow(String tradeId, String counterparty, String commodity, TradeTransactionType tradeTransactionType,
                                          String settlementCurrency, String tradePriceCurrency, double price, String priceType, double fxRate,
                                          double totalContractQuantity, String costId, String costType, double costValue, String quantityOption,
                                          String obligationId, Double obligationQuantity, String planId, String plannedObligationId, LocalDateTime
                                                  deliveryDate, String stage, String cashflowType, String originalInvoice, String token, String tenantId, double splitSequenceNumber,
                                          String description, double premiumDiscount, String actualizationObjectId, String quantityUom,
                                          String profitcenter, LocalDateTime tradeDateTime, String tradePriceUom, String company, String costName, String costGroup,String docBypassId) throws Exception {
        logger.info("starting saving cashflow for trade id : {} cost Id : {}  type : {} Plan Id : {} Obligation Id : {}  Planned Obligation Id : {} And Stage : {}" , tradeId , costId ,cashflowType , planId , obligationId , plannedObligationId , stage);
        UomConversionOutputtDTO uomConversionOutputtDTO = currencyConversionService.convertUom(quantityUom, tradePriceUom, obligationQuantity, commodity, tenantId, token);
        double conversionfactor = 1;
        if (uomConversionOutputtDTO.getStatus().equalsIgnoreCase(STATUS_OK)) {
            conversionfactor = uomConversionOutputtDTO.getConversionFactor();
        }
        var cashflowPrice=  price;
        if(cashflowType.equalsIgnoreCase(COST) || cashflowType.equalsIgnoreCase(CHARGES)) {
            cashflowPrice = costValue;
            if(costType.equalsIgnoreCase(PERCENTAGE)) {
                cashflowPrice =((price) * (costValue/ 100));
            }
        }
        double financeAmount = getFinanceAmount(costId, quantityOption, costType, costValue, (obligationQuantity * conversionfactor), cashflowType, priceType, settlementCurrency, tradePriceCurrency, price, fxRate, totalContractQuantity);
        CashflowDataDTO cashFlow = new CashflowDataDTO();
        cashFlow.setTradeId(tradeId);
        cashFlow.setTenantId(tenantId);
        cashFlow.setPaymentDueDate(deliveryDate);
        cashFlow.setCashflowStatus(ACTIVE);
        cashFlow.setCostName(costName);
        cashFlow.setCostGroup(costGroup);
        cashFlow.setType(cashflowType);
        cashFlow.setDocByPassId(docBypassId);
        cashFlow.setStage(stage);
        cashFlow.setTradePriceCurrency(tradePriceCurrency);
        cashFlow.setSettlementCurrency(settlementCurrency);
        cashFlow.setInvoiceAmount(0);
        cashFlow.setInvoiceDate(null);
        cashFlow.setInvoiceDueDate(null);
        cashFlow.setInvoiceNumber("");
        cashFlow.setInvoiceStatus("");
        cashFlow.setInvoiceRemitTo("");
        cashFlow.setDescription(description);
        cashFlow.setCompany(company);
        cashFlow.setOriginalInvoice(originalInvoice);
        cashFlow.setObligationId(obligationId);
        cashFlow.setActualizationObjectId(actualizationObjectId);
        cashFlow.setPlannedObligationId(plannedObligationId);
        cashFlow.setSplitSequenceNumber(splitSequenceNumber);
        cashFlow.setQuantityUom(quantityUom);
        cashFlow.setCounterparty(counterparty);
        cashFlow.setCommodity(commodity);
        cashFlow.setTradeDateTime(tradeDateTime);
        cashFlow.setPriceType(priceType);
        cashFlow.setProfitcenter(profitcenter);
        cashFlow.setTradePrice(cashflowPrice);
        cashFlow.setTradePriceUom(tradePriceUom);
        cashFlow.setPlanId(planId);
        cashFlow.setCostId(costId);
        cashFlow.setQuantityStatus(ACTUAL);
        cashFlow.setQuantity(obligationQuantity);
        PriceAllocationCheckDTO priceAllocationCheckDTO = TransactionIdUtil.query(baseUrl + ADVANCE_ALLOCATION_ROOT + GET_PRICE_ALLOCATED_CHECK_BY_PLANNED_OBLIGATION_ID + "?plannedObligationId=" + plannedObligationId + "&tenantId=" + tenantId, HttpMethod.GET, token, null, PriceAllocationCheckDTO.class);
        cashFlow.setPriceStatus(getPriceStatus(priceType, costId, costType, stage, cashflowType, Objects.requireNonNull(priceAllocationCheckDTO).isFullyPriced()));
        cashFlow.setFinancialAmountType(getFinanceAmountType(financeAmount, tradeTransactionType, cashflowType, premiumDiscount));
        cashFlow.setTradePriceValue(Math.abs(financeAmount));
        if (StringUtils.isEmpty(fxRate)) {
            fxRate = 1;
        }
        cashFlow.setSettlementValue(cashFlow.getTradePriceValue() * fxRate);
        CashflowDataDTO cashflowDataDTO = null;
        try {
            cashflowDataDTO = TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + SAVE_CASHFLOW, HttpMethod.POST, token, cashFlow, CashflowDataDTO.class);
        } catch (HttpStatusCodeException e) {
            logger.error("Something went wrong during cashflow creation: {}", e.getResponseBodyAsString());
        }
        try {
            TransactionIdUtil.auditLog(baseUrl, "Cashflows Create during Actualization", cashFlow, null, token, jwtSecretKey);
        } catch (Exception ex) {
            logger.error("Couldn't capture  audit log while creating creating cashflows {}" , ex.getMessage());
        }
        return cashflowDataDTO;
    }

    /**
     * @param priceType
     * @param costId
     * @param costType
     * @param stage
     * @param type
     * @return
     */

    //SEND Price Allocation Status
    public String getPriceStatus(String priceType, String costId, String costType, String stage, String type, Boolean isFullyPriced) {
        logger.info("Calculation Price status for cost id : " + costId + " Stage : " + stage + " and Type : " + type);
        if (type.equalsIgnoreCase(Trade) || type.equalsIgnoreCase(CASHFLOW_TYPE_PREMIUM) || type.equalsIgnoreCase(CASHFLOW_TYPE_DISCOUNT) || type.equalsIgnoreCase(CASHFLOW_STAGE_PROVISIONAL)) {
            if (priceType.equalsIgnoreCase(FIXEDPRICED)) {
                return PRICED;
            } else {
                if (isFullyPriced) {
                    return PRICED;
                }
                return UNPRICED;
            }
        } else if (type.equalsIgnoreCase(COST) || type.equalsIgnoreCase(CHARGES)) {
            if (costType.equalsIgnoreCase(PERCENTAGE_SIMPLE) || costType.equalsIgnoreCase(PERCENTAGE_MULTIPLE)) {
                if (priceType.equalsIgnoreCase(FIXEDPRICED)) {
                    return PRICED;
                } else {
                    if (isFullyPriced) {
                        return PRICED;
                    }
                    return UNPRICED;
                }
            } else {
                return PRICED;
            }
        }
        return "";
    }

    /**
     * @param costId
     * @param quantityOption
     * @param costType
     * @param costValue
     * @param obliquantity
     * @param type
     * @param priceType
     * @param settlementCurrency
     * @param tradePriceCurrency
     * @param price
     * @param fxRate
     * @param totalContractQuantity
     * @return
     * @throws Exception
     */
    public double getFinanceAmount(String costId, String quantityOption, String costType, double costValue, Double obliquantity, String type, String priceType, String settlementCurrency, String tradePriceCurrency, double price, double fxRate, double totalContractQuantity) throws Exception {
        double financialAmount = 0.0;
        logger.info("cashFlowType, PricType, Currency {} {} {}/{}",type,priceType,tradePriceCurrency, settlementCurrency);
        if (type.equalsIgnoreCase(Trade) || type.equalsIgnoreCase(CASHFLOW_TYPE_PREMIUM) || type.equalsIgnoreCase(CASHFLOW_TYPE_DISCOUNT)  || type.equalsIgnoreCase(CASHFLOW_STAGE_PROVISIONAL)) {
            financialAmount = price * obliquantity;
        } else if (type.equalsIgnoreCase(COST) || type.equalsIgnoreCase(CHARGES)) {
            logger.info("calculating financial amount for cost for the costId {}" , costId);
            financialAmount = switch (costType) {
                case PER_UNIT -> obliquantity * costValue;
                case LUMPSUM -> costValue;
                case PERCENTAGE -> (obliquantity * ((price) * (costValue/ 100)));
                default -> financialAmount;
            };
        }

        return financialAmount;
    }

    /**
     * @param financeAmount
     * @param tradeTransactionType
     * @param type
     * @param premiumDiscount
     * @return
     */
    public String getFinanceAmountType(double financeAmount, TradeTransactionType tradeTransactionType, String type, double premiumDiscount) {
        logger.info("Calculation Finance Amount Type for finance Amount : " + financeAmount + " Trade Type value : " + tradeTransactionType + " and Type : " + type);
        if (type.equalsIgnoreCase(COST)) {
            return PAYABLE;
        } else if (type.equalsIgnoreCase(CHARGES)) {
            return RECEIVABLE;
        } else if (type.equalsIgnoreCase(CASHFLOW_TYPE_PREMIUM) || type.equalsIgnoreCase(CASHFLOW_TYPE_DISCOUNT)) {
            if (tradeTransactionType == TradeTransactionType.BUY) {
                if (type.equalsIgnoreCase(CASHFLOW_TYPE_PREMIUM)) {
                    return PAYABLE;
                } else if (type.equalsIgnoreCase(CASHFLOW_TYPE_DISCOUNT)) {
                    return RECEIVABLE;
                }
            } else {
                if (type.equalsIgnoreCase(CASHFLOW_TYPE_PREMIUM)) {
                    return RECEIVABLE;
                } else if (type.equalsIgnoreCase(CASHFLOW_TYPE_DISCOUNT)) {
                    return PAYABLE;
                }
            }
        } else {
            if (((type.equalsIgnoreCase(Trade) || type.equalsIgnoreCase(CASHFLOW_STAGE_PROVISIONAL)) && tradeTransactionType == TradeTransactionType.SELL) || (type.equalsIgnoreCase(COST) && financeAmount > 0)) {
                return RECEIVABLE;
            }
            if (((type.equalsIgnoreCase(Trade) || type.equalsIgnoreCase(CASHFLOW_STAGE_PROVISIONAL)) && tradeTransactionType == TradeTransactionType.BUY) ||
                    (type.equalsIgnoreCase(COST)  && financeAmount < 0)) {
                return PAYABLE;
            }
        }
        return "";
    }

    /**
     * @param tenantId
     * @param updateCashflowDTO
     * @param token
     * @return
     */
    @SneakyThrows
    public ResponseEntity runUpdateCashflow(String tenantId, UpdateCashflowDTO updateCashflowDTO, String token) {
        logger.info("Entered into ActualizationCashflowService.runUpdateCashflow");
        ResponseEntity responseEntity = null;
        try {
            updateCashflow(tenantId,updateCashflowDTO.getCommodity(), updateCashflowDTO.getObligationId(), updateCashflowDTO.getStage(), updateCashflowDTO.getObligationQuantity(), updateCashflowDTO.getAmount(), updateCashflowDTO.getTradePriceCurrency(), updateCashflowDTO.getTradeSettlementCurrency(), updateCashflowDTO.getQuantityUom(), updateCashflowDTO.getCostId(), updateCashflowDTO.getCostValue(), updateCashflowDTO.getCostCurrency(), updateCashflowDTO.getCostUom(), updateCashflowDTO.getCostType(), updateCashflowDTO.getFxRate(), updateCashflowDTO.getFxId(), updateCashflowDTO.getType(), updateCashflowDTO.getPriceStatus(), token,updateCashflowDTO.getSplitSequenceNumber(),updateCashflowDTO);
            responseEntity = new ResponseEntity(new ReturnStatus("Cashflow updated successfully."), HttpStatus.OK);
        } catch (Exception e) {
            responseEntity = new ResponseEntity(new ReturnStatus("Failed to update cashflows, error is: + " + e.getMessage()), HttpStatus.BAD_REQUEST);
            logger.error("failed to update cashflow :",e);
        }
        logger.info("Exited from ActualizationCashflowService.runUpdateCashflow");
        return responseEntity;
    }

    /**
     * @param tenantId
     * @param obliId
     * @param stage
     * @param obliQuantity
     * @param amount
     * @param tradePriceCurrency
     * @param tradeSettlementCurrency
     * @param quantityUom
     * @param costId
     * @param costValue
     * @param costCurrency
     * @param costUom
     * @param costType
     * @param fxRate
     * @param fxId
     * @param type
     * @param priceStatus
     * @param token
     * @param updateCashflowDTO
     * @throws Exception
     */
    @SneakyThrows
    public void updateCashflow(String tenantId,String commodity, String obliId, String stage, Double obliQuantity, double amount, String tradePriceCurrency, String tradeSettlementCurrency, String quantityUom, String costId, double costValue, String costCurrency, String costUom, String costType, double fxRate, String fxId, String type, String priceStatus, String token, double splitSequenceNumber, UpdateCashflowDTO updateCashflowDTO) throws Exception {
        logger.info("Entered into ActualizationCashflowService.updateCashflow");
        double tradeValue = 0.0, settlementValue = 0.0;
        CashflowDataDTO[] cashflowDataResponse = null;
        CashflowDataDTO cashflowData = new CashflowDataDTO();
        CashflowDataDTO cashflowDataOldObject = new CashflowDataDTO();
        List<SearchCriteria> searchCriteriaList;
        if (type.equalsIgnoreCase(COST)) {
            searchCriteriaList = new ArrayList<>();
            searchCriteriaList.add(new SearchCriteria("tenantId", "equals", tenantId));
            searchCriteriaList.add(new SearchCriteria("plannedObligationId", "equals", obliId));
            searchCriteriaList.add(new SearchCriteria("costId", "equals", costId));
            searchCriteriaList.add(new SearchCriteria("cashflowStatus", "equals", ACTIVE));
            searchCriteriaList.add(new SearchCriteria("type", "equals", type));
            searchCriteriaList.add(new SearchCriteria("splitSequenceNumber", "equals", splitSequenceNumber));
            searchCriteriaList.add(new SearchCriteria("stage", "equals", stage));
            cashflowDataResponse = TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaList, CashflowDataDTO[].class);
            cashflowData = Arrays.stream(Objects.requireNonNull(cashflowDataResponse)).toList().get(0);
            BeanUtils.copyProperties(cashflowData, cashflowDataOldObject);
        } else if (type.equalsIgnoreCase(Trade)) {
            searchCriteriaList = new ArrayList<>();
            searchCriteriaList.add(new SearchCriteria("tenantId", "equals", tenantId));
            searchCriteriaList.add(new SearchCriteria("plannedObligationId", "equals", obliId));
            searchCriteriaList.add(new SearchCriteria("cashflowStatus", "equals", ACTIVE));
            searchCriteriaList.add(new SearchCriteria("type", "equals", type));
            searchCriteriaList.add(new SearchCriteria("stage", "equals", stage));
            searchCriteriaList.add(new SearchCriteria("splitSequenceNumber", "equals", splitSequenceNumber));
            cashflowDataResponse = TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaList, CashflowDataDTO[].class);
            cashflowData = Arrays.stream(Objects.requireNonNull(cashflowDataResponse)).toList().get(0);
            BeanUtils.copyProperties(cashflowData, cashflowDataOldObject);
        } else if (type.equalsIgnoreCase(FX)) {
            //TODO FX
        }
        if (cashflowData.getType().equalsIgnoreCase(Trade)) {
            cashflowData.setSettlementCurrency(tradeSettlementCurrency);
        } else if (cashflowData.getType().equalsIgnoreCase(COST)) {
            cashflowData.setSettlementCurrency(costCurrency);
        }
        tradeValue = getTradeValue(amount, commodity,type, obliQuantity, costValue, costUom, costType, quantityUom, tenantId, token);
        if (StringUtils.isEmpty(fxRate)) {
            fxRate = 1;
        }
        settlementValue = getSettlementValue(tradeValue, fxRate);
        cashflowData.setTradePriceValue(tradeValue);
        cashflowData.setSettlementValue(settlementValue);
        cashflowData.setTradePriceCurrency(tradePriceCurrency);
        cashflowData.setQuantity(obliQuantity);
        cashflowData.setPriceStatus(priceStatus == null ? cashflowData.getPriceStatus() : priceStatus);
        if(List.of(CASHFLOW_STAGE_PROVISIONAL,ACCRUED_PROVISIONAL).contains(cashflowData.getType()) && updateCashflowDTO.isFullyPriced()) {
            cashflowData.setType(Trade);
        }
        logger.info("Exited from ActualizationCashflowService.updateCashflow");
        try {
            TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + CASHFLOW_UPDATE + "?tenantId=" + tenantId, HttpMethod.POST, token, cashflowData, Object.class);
        } catch (Exception e) {
            logger.error("Something went wrong during cashflow updation: " ,e);
        }
        try {
            TransactionIdUtil.auditLog(baseUrl, "Cashflows Update during Actualization", cashflowData, null, token, jwtSecretKey);
        } catch (Exception ex) {
            logger.error("Couldn't capture  audit log while Cashflows Update during Actualization " ,ex);
        }
    }

    /**
     * @param tradeValue
     * @param fxRate
     * @return
     */
    public double getSettlementValue(double tradeValue, double fxRate) {
        if (StringUtils.isEmpty(fxRate)) {
            fxRate = 1;
        }
        return (tradeValue * fxRate);
    }

    /**
     * @param amount
     * @param cashflowType
     * @param obliQuantity
     * @param costValue
     * @param costUom
     * @param costType
     * @param quantityUom
     * @param tenantId
     * @param token
     * @return
     */
    @SneakyThrows
    public double getTradeValue(double amount,String commodity, String cashflowType, double obliQuantity, double costValue, String costUom, String costType, String quantityUom, String tenantId, String token) {
        logger.info("Entered into ActualizationCashflowService.getTradeValue");
        double tradeValue = 0.0;
        double obligationQuantity = 0.0;
        if (cashflowType.equalsIgnoreCase(Trade)) {
            tradeValue = amount;
        } else if (cashflowType.equalsIgnoreCase(COST)) {
            obligationQuantity = obliQuantity;
            if (costType.equalsIgnoreCase(PER_UNIT)) {
                if (!quantityUom.equalsIgnoreCase(costUom)) {
                    obligationQuantity = getConvertedQuantity(obliQuantity, quantityUom, costUom,commodity, tenantId, token);
                }
                tradeValue = obligationQuantity * costValue;
            } else if (costType.equalsIgnoreCase(PERCENTAGE)) {
                tradeValue = ((amount * costValue) / 100);
            } else if (costType.equalsIgnoreCase(LUMPSUM)) {
                tradeValue = costValue;
            }
        }
        logger.info("Exited from ActualizationCashflowService.getTradeValue");
        return tradeValue;
    }

    /**
     * @param quantity
     * @param fromUom
     * @param toUom
     * @param tenantId
     * @param token
     * @return
     */
    @SneakyThrows
    private double getConvertedQuantity(double quantity, String fromUom, String toUom,String commodity, String tenantId, String token) {
        logger.info("Entered into ActualizationCashflowService.getConvertedQuantity");
        var uomConversionOutputtDTO=currencyConversionService.convertUom(fromUom,toUom,quantity,commodity,tenantId,token);
        return uomConversionOutputtDTO.getValue();
    }
}
