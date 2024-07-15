package com.taomish.actualization.service;

import com.taomish.actualization.common.CnDnUtil;
import com.taomish.dtos.cashflowservice.CashflowDataDTO;
import com.taomish.enums.TradeTransactionType;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.common.searchcriteria.SearchCriteria;
import com.taomish.dtos.conversionservice.UomConversionInputDTO;
import com.taomish.dtos.conversionservice.UomConversionOutputtDTO;
import com.taomish.dtos.cashflowservice.UpdateCashflowDTO;
import com.taomish.common.domain.TaomishError;
import com.taomish.services.CurrencyAndUOMConversionService.service.CurrencyConversionService;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static com.taomish.RestEndPoints.ConfigService.UnitOfMeasurementRestEndPoints.CONVERT_UOM;
import static com.taomish.RestEndPoints.ConfigService.UnitOfMeasurementRestEndPoints.UNIT_OF_MEASUREMENT_ROOT;
import static com.taomish.constants.CashflowConstants.*;
import static com.taomish.constants.TradeCostConstants.*;
import static com.taomish.constants.TradeCostConstants.LUMPSUM;
import static com.taomish.constants.PhysicalConstants.*;
import static com.taomish.RestEndPoints.CashflowRestEndPoints.*;
import static com.taomish.constants.TradeCostConstants.PERCENTAGE_SIMPLE;

@Service
public class ActualizationCnDnService {


    private static final Logger logger = LoggerFactory.getLogger(ActualizationCnDnService.class);

    @Value("${baseUrlEC2DEV}")
    private String baseUrl;

    @Value("${masterBaseURL}")
    private String masterBaseURL;

    @Autowired
    private CnDnUtil cnDnUtil;

    @Value("${jwt.secret}")
    private String jwtSecretKey;

    @Autowired
    private  ActualizationCashflowService actualizationCashflowService;

    @Autowired
    private CurrencyConversionService currencyConversionService;

    @SneakyThrows
    private double getTradeValue(double amount,String commodity, String cashflowType, double obliQuantity, double costValue, String costUom,
                                 String costType, String quantityUom,String tenantId, String token) {
        logger.info("Entered into ActualizationCnDnService.getTradeValue");
        double tradeValue = 0.0;
        double obligationQuantity = 0.0;
        if (List.of(TRADE_REVERSAL,Trade,CASHFLOW_STAGE_PROVISIONAL).contains(cashflowType)) {
            tradeValue = amount;
        } else if (cashflowType.equalsIgnoreCase(COST)) {
            obligationQuantity = obliQuantity;
            if (costType.equalsIgnoreCase(PER_UNIT)) {
                if (!quantityUom.equalsIgnoreCase(costUom)) {
                    obligationQuantity = getConvertedQuantity(obliQuantity, quantityUom, costUom,commodity,tenantId,token);
                }
                tradeValue = obligationQuantity * costValue;
            } else if (costType.equalsIgnoreCase(PERCENTAGE)) {
                tradeValue = ((amount * costValue) / 100);
            } else if (costType.equalsIgnoreCase(LUMPSUM)) {
                tradeValue = costValue;
            }
        }
        logger.info("Exited from ActualizationCnDnService.getTradeValue");
        return tradeValue;
    }

    @SneakyThrows
    private double getConvertedQuantity(double quantity, String fromUom, String toUom,String commodity, String tenantId, String token) {
        logger.info("Entered into ActualizationCnDnService.getConvertedQuantity");
        var uomConversionOutputtDTO=currencyConversionService.convertUom(fromUom,toUom,quantity,commodity,tenantId,token);
        return uomConversionOutputtDTO.getValue();
    }

    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void generateClaimCashflow(CashflowDataDTO invoiceCashflow,String token,String tenantId) throws Exception {
        /**
         * Setting the reversalcashflow financial amount type if it's receivable make it payable and vice-versa
         */
        if(invoiceCashflow.getType().equalsIgnoreCase(COST)) {
            if(!invoiceCashflow.getCostType().equalsIgnoreCase(PERCENTAGE) && !invoiceCashflow.getCostType().equalsIgnoreCase(PERCENTAGE_SIMPLE) && !invoiceCashflow.getCostType().equals(PER_UNIT)) {
                return;
            }
        }
        CashflowDataDTO originalCashflow = new CashflowDataDTO();
        BeanUtils.copyProperties(invoiceCashflow,originalCashflow);
        originalCashflow.setInvoiceNumber("");
        originalCashflow.setInvoiceAmount(0);
        originalCashflow.setInvoiceDate(null);
        originalCashflow.setInvoiceStatus("");
        originalCashflow.setInvoiceDueDate(null);
        originalCashflow.setInvoiceRemitTo("");
        originalCashflow.setPaidInvoiceList(new ArrayList<>());
        originalCashflow.setInvoicedSettlementValue(0.0);
        originalCashflow.setStage(ACCRUED);
        List<CashflowDataDTO> cashflowDataDTOList = new ArrayList<>();
        String cashflowReversalUrl = baseUrl + CASHFLOW_ROOT + SAVE_CASHFLOW;
        invoiceCashflow.setCashflowId("");
        invoiceCashflow.setUuid(null);
        invoiceCashflow.setFinancialAmountType((invoiceCashflow.getFinancialAmountType().equalsIgnoreCase(RECEIVABLE)) ? PAYABLE : RECEIVABLE);
        String originalInvoice = invoiceCashflow.getInvoiceNumber();
        invoiceCashflow.setInvoiceNumber("");
        invoiceCashflow.setInvoiceAmount(0);
        invoiceCashflow.setInvoiceDate(null);
        invoiceCashflow.setInvoiceStatus("");
        invoiceCashflow.setInvoiceDueDate(null);
        invoiceCashflow.setInvoiceRemitTo("");
        invoiceCashflow.setPaidInvoiceList(new ArrayList<>());
        invoiceCashflow.setInvoicedSettlementValue(0.0);
        invoiceCashflow.setStage(ACCRUED);
        invoiceCashflow.setType(getReversalType(invoiceCashflow.getType()));
        invoiceCashflow.setOriginalInvoice(originalInvoice);
        double amount = invoiceCashflow.getQuantity() * invoiceCashflow.getTradePrice();
        double tradeValue = getTradeValue(amount,invoiceCashflow.getCommodity(), invoiceCashflow.getType(), invoiceCashflow.getClaimQuantity(), invoiceCashflow.getCostValue(),
                invoiceCashflow.getCostUom(), invoiceCashflow.getCostType(), invoiceCashflow.getQuantityUom(), tenantId, token);
        if (StringUtils.isEmpty(invoiceCashflow.getFxRate())) {
            invoiceCashflow.setFxRate(1);
        }
        invoiceCashflow.setTradePriceValue(tradeValue);
        double settlementValue = (tradeValue * invoiceCashflow.getFxRate());
        invoiceCashflow.setSettlementValue(settlementValue);
        CashflowDataDTO cashflowDataDTOForReversal = null;
        try {
            cashflowDataDTOForReversal = TransactionIdUtil.query(cashflowReversalUrl, HttpMethod.POST, token, invoiceCashflow, CashflowDataDTO.class);
            cashflowDataDTOList.add(cashflowDataDTOForReversal);
        } catch (Exception e) {
            logger.error("Cashflow reversal failed",e);
            throw new TaomishError("Cashflow reversal failed");
        }
        originalCashflow.setCashflowId("");
        createCnDnCashFlows(originalCashflow,token,tenantId,null);
    }

    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateClaimCashflow(List<CashflowDataDTO> cashflows, String token, String tenantId, double claimQuantity, UpdateCashflowDTO updateCashflowDTO) {
        var nonReversalCashflows =  cashflows.stream().filter(e -> !List.of(TRADE_REVERSAL,COST_REVERSAL).contains(e.getType())).toList();
        for(CashflowDataDTO cashflow:nonReversalCashflows) {
            cashflow.setClaimQuantity(claimQuantity);
            cashflow.setFxRate(updateCashflowDTO.getFxRate());
            cashflow.setTradePrice(updateCashflowDTO.getTradePrice());
            cashflow.setPriceStatus(updateCashflowDTO.getPriceStatus());
            createCnDnCashFlows(cashflow,token,tenantId,cashflow.getUuid());
        }

    }

    @SneakyThrows
    public ResponseEntity createCnDnCashFlows(CashflowDataDTO cashFlowObject, String token, String tenantId, UUID uuid) {

        logger.info("Entered to ActualizationCnDnService.createCnDnCashFlows() method");
        if (cashFlowObject == null) {
            return ResponseEntity.badRequest().body("Given cashflow DTO is null");
        }
        if (Arrays.asList(CASHFLOW_TYPE_PREMIUM, CASHFLOW_TYPE_DISCOUNT).contains(cashFlowObject.getType())) {
            if (cashFlowObject.getTradeTransactionType() == TradeTransactionType.BUY) {
                if (cashFlowObject.getClaimpremiumDiscount() > 0) {
                    cashFlowObject.setType(CASHFLOW_TYPE_PREMIUM_CREDIT_NOTE);
                } else {
                    cashFlowObject.setType(CASHFLOW_TYPE_DISCOUNT_DEBIT_NOTE);
                }
            } else {
                if (cashFlowObject.getClaimpremiumDiscount() > 0) {
                    cashFlowObject.setType(CASHFLOW_TYPE_PREMIUM_DEBIT_NOTE);
                } else {
                    cashFlowObject.setType(CASHFLOW_TYPE_DISCOUNT_CREDIT_NOTE);
                }
            }
            cashFlowObject.setTradePrice(cashFlowObject.getClaimpremiumDiscount());
            cashFlowObject.setTradePriceValue(Math.abs(cashFlowObject.getTradePrice() * cashFlowObject.getClaimQuantity()));
            if (StringUtils.isEmpty(cashFlowObject.getFxRate())) {
                cashFlowObject.setFxRate(1);
            }
            double settlementValue = (cashFlowObject.getTradePriceValue() * cashFlowObject.getFxRate());
            cashFlowObject.setQuantity(cashFlowObject.getClaimQuantity());
            cashFlowObject.setSettlementValue(settlementValue);
        } else {
            double amount = cashFlowObject.getClaimQuantity() * cashFlowObject.getTradePrice();
            double tradeValue = getTradeValue(amount,cashFlowObject.getCommodity(), cashFlowObject.getType(), cashFlowObject.getClaimQuantity(), cashFlowObject.getCostValue(),
                    cashFlowObject.getCostUom(), cashFlowObject.getCostType(), cashFlowObject.getQuantityUom(), tenantId, token);
            if (StringUtils.isEmpty(cashFlowObject.getFxRate())) {
                cashFlowObject.setFxRate(1);
            }
            double settlementValue = (tradeValue * cashFlowObject.getFxRate());
            cashFlowObject.setSettlementValue(settlementValue);
            cashFlowObject.setTradePriceValue(tradeValue);
            cashFlowObject.setQuantity(cashFlowObject.getClaimQuantity());
        }
        cashFlowObject.setUuid(uuid);
        try {
            cashFlowObject = TransactionIdUtil.query(baseUrl+CASHFLOW_ROOT+SAVE_CASHFLOW, HttpMethod.POST,token,cashFlowObject, CashflowDataDTO.class);
        } catch (HttpStatusCodeException e) {
            logger.error("Something went wrong during cashflow creation: {}",e.getResponseBodyAsString());
        }
        try {
            TransactionIdUtil.auditLog(baseUrl,"Cashflows Create during Actualization",cashFlowObject,null,token,jwtSecretKey);
        } catch (Exception ex) {
            logger.error("Couldn't capture  audit log while creating creating cashflows :",ex);
        }
        logger.info("Exited from ActualizationCnDnService.createCnDnCashFlows() method");
        return ResponseEntity.ok().body(cashFlowObject);
    }

    private String getReversalType(String type) {
        if(type.equalsIgnoreCase(COST)) {
            return COST_REVERSAL;
        } else if(type.equalsIgnoreCase(CASHFLOW_TYPE_PREMIUM)) {
            return CASHFLOW_TYPE_PREMIUM_REVERSAL;
        } else if(type.equalsIgnoreCase(CASHFLOW_TYPE_DISCOUNT)) {
            return CASHFLOW_TYPE_DISCOUNT_REVERSAL;
        } else {
            return TRADE_REVERSAL;
        }
    }


    /**
     * Service class to generate reversal cost cashflows and generate new cn/dn cashflows
     *
     * @param actualizationCnDnCashFlowCostDTO
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ResponseEntity createCnDnCashFlowsForCostActualizationService(UpdateCashflowDTO actualizationCnDnCashFlowCostDTO, String tenantId, String token) throws Exception {
        logger.info("Entered to ActualizationCnDnService.createCnDnCashFlowsForCostActualizationService() method");
        CashflowDataDTO[] cashflowDataResponse = null;
        if (actualizationCnDnCashFlowCostDTO == null) {
            logger.error("Given cnDnCashFlowCostDTO is null");
            return ResponseEntity.badRequest().body("Given cnDnCashFlowCostDTO is null");
        }

        Set<String> nullFieldsInCnDnCashFlowCostDTO = cnDnUtil.getNullPropertyNames(actualizationCnDnCashFlowCostDTO);

        if (!nullFieldsInCnDnCashFlowCostDTO.isEmpty()) {
            logger.error("All the fields in cnDnCashFlowCostDTO are mandatory, the following are missing {} :",nullFieldsInCnDnCashFlowCostDTO);
            return ResponseEntity.badRequest().body("All the fields in cnDnCashFlowCostDTO are mandatory, the following are missing " + nullFieldsInCnDnCashFlowCostDTO);
        }

//        String getCashflowByCashflowId = baseUrl + CASHFLOW_ROOT + GET_CASHFLOW_BY_CASHFLOWID + "?cashflowId=" + actualizationCnDnCashFlowCostDTO.getCashflowId();


        List<SearchCriteria> searchCriteriaList;
        searchCriteriaList= new ArrayList<>();
        searchCriteriaList.add(new SearchCriteria("tenantId","equals",tenantId));
        searchCriteriaList.add(new SearchCriteria("cashflowId","equals",actualizationCnDnCashFlowCostDTO.getCashflowId()));
        cashflowDataResponse = TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT+GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST,token,searchCriteriaList,CashflowDataDTO[].class);

        if (cashflowDataResponse.length != 1){
            return ResponseEntity.badRequest().body("More than one Or no cashflow found");
        }
        CashflowDataDTO cashFlowObject = cashflowDataResponse[0];

        if (cashFlowObject == null) {
            logger.error("cashflow not found for the given cashflowId {} : ", actualizationCnDnCashFlowCostDTO.getCashflowId());
            return ResponseEntity.badRequest().body("cashflow not found for the given cashflowId : " + actualizationCnDnCashFlowCostDTO.getCashflowId());
        }

        double settlementValue = actualizationCnDnCashFlowCostDTO.getObligationQuantity() * actualizationCnDnCashFlowCostDTO.getPrice();
        actualizationCnDnCashFlowCostDTO.setType(COST);
        /**
         * set the cash flow type for the new cashflow as credit note and debit note depending on buy or sell
         */
//        restTemplate.postForObject(baseUrl + CASHFLOW_ROOT + CREATE_CASHFLOW, actualizationCnDnCashFlowCostDTO, Object.class);

        actualizationCashflowService.runCreatCashflows(actualizationCnDnCashFlowCostDTO, token, tenantId);

        logger.info("Exited from ActualizationCnDnService.createCnDnCashFlowsForCostActualizationService() method");
        return ResponseEntity.ok().body("Reversal cashflow and new cashflow(s) generated");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ResponseEntity createCnDnCashFlowsForQualityActualizationService(UpdateCashflowDTO updateCashflowDTO, String tenantId, String token) throws Exception {
        logger.info("Entered to ActualizationCnDnService.createCnDnCashFlowsForQualityActualizationService() method");
        CashflowDataDTO[] cashflowDataResponse = null;
        if (updateCashflowDTO == null) {
            logger.error("Given cashflow DTO is null");
            return ResponseEntity.badRequest().body("Given cashflow DTO is null");
        }
        List<SearchCriteria> searchCriteriaList;
        searchCriteriaList= new ArrayList<>();
        searchCriteriaList.add(new SearchCriteria("tenantId","equals",tenantId));
        searchCriteriaList.add(new SearchCriteria("cashflowId","equals",updateCashflowDTO.getCashflowId()));
        cashflowDataResponse = TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT+GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST,token,searchCriteriaList,CashflowDataDTO[].class);
        if (cashflowDataResponse.length != 1){
            return ResponseEntity.badRequest().body("More than one Or no cashflow found");
        }
        CashflowDataDTO cashFlowObject = cashflowDataResponse[0];
        if (cashFlowObject == null) {
            logger.error("cashflow not found for the given cashflowId {} : " , updateCashflowDTO.getCashflowId());
            return ResponseEntity.badRequest().body("cashflow not found for the given cashflowId: " + updateCashflowDTO.getCashflowId());
        }


        /**
         * set the cash flow type for the new cashflow as Trade credit note and Trade debit note depending
         * on (buy trade and decrease in reversal) or (sell and increase in reversal) then Trade debit note
         * on (buy trade and increase in reversal) or (sell and decrease in reversal) then Trade credit note
         */
        if(updateCashflowDTO.getTradeTransactionType() == TradeTransactionType.BUY) {
            if(updateCashflowDTO.getPremiumDiscount() > 0) {
                updateCashflowDTO.setType(CASHFLOW_TYPE_PREMIUM_CREDIT_NOTE);
            } else {
                updateCashflowDTO.setType(CASHFLOW_TYPE_DISCOUNT_DEBIT_NOTE);
            }
        } else {
            if(updateCashflowDTO.getPremiumDiscount() > 0) {
                updateCashflowDTO.setType(CASHFLOW_TYPE_PREMIUM_DEBIT_NOTE);
            } else {
                updateCashflowDTO.setType(CASHFLOW_TYPE_DISCOUNT_CREDIT_NOTE);
            }
        }
        CashflowDataDTO cashflowDataDTO = actualizationCashflowService.runCreatCashflows(updateCashflowDTO, token, tenantId);
        logger.info("Exited from ActualizationCnDnService.createCnDnCashFlowsForQualityActualizationService() method");
        return ResponseEntity.ok().body(cashflowDataDTO);
    }
}
