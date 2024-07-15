package com.taomish.actualization.service;

import com.taomish.actualization.dto.ActualizeCostObj;
import com.taomish.actualization.dto.ActualizeObj;
import com.taomish.actualization.model.ActualizedCost;
import com.taomish.actualization.model.ActualizedQuantityObligations;
import com.taomish.actualization.repo.ActualizationQuantityRepo;
import com.taomish.common.domain.TaomishError;
import com.taomish.dtos.actualizationservice.ActualizeCostObjDTO;
import com.taomish.dtos.actualizationservice.ActulalizeCostPlannedObligationDTO;
import com.taomish.dtos.cashflowservice.CashflowDataDTO;
import com.taomish.dtos.ReturnStatus;
import com.taomish.dtos.costservice.ActualizedCostDTO;
import com.taomish.dtos.costservice.TradeCostDTO;
import com.taomish.enums.PlannedObligationState;
import com.taomish.enums.TradeTransactionType;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.common.searchcriteria.SearchCriteria;
import com.taomish.dtos.cashflowservice.UpdateCashflowDTO;
import com.taomish.dtos.costservice.TradeCostEODRawDataDTO;
import com.taomish.dtos.physicaltradeservice.PhysicalTradeDTO;
import com.taomish.dtos.physicaltradeservice.PlannedObligationDTO;
import com.taomish.messaging.PlatformQueueService;
import com.taomish.transaction_reference.service.TransactionIDGenerator;
import com.taomish.web.security.models.User;
import com.taomish.web.service.ModelMapperService;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.taomish.constants.CashflowConstants.*;
import static com.taomish.constants.PlannedObligationConstants.GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID;
import static com.taomish.constants.PlannedObligationConstants.PLANNED_OBLIGATION_ROOT;
import static com.taomish.constants.EODConstants.EXCHANGE_NAME;
import static com.taomish.constants.EODConstants.PHYSICAL_TRADE_COST_ACTUALIZATION_CREATE;
import static com.taomish.constants.PhysicalConstants.*;
import static com.taomish.RestEndPoints.PhysicalRestEndPoint.GET_PHYSICAL_TRADE_BY_TRADE_ID;
import static com.taomish.RestEndPoints.PhysicalRestEndPoint.PHYSICAL_TRADE_ROOT;
import static com.taomish.RestEndPoints.CashflowRestEndPoints.*;
import static com.taomish.constants.TradeCostConstants.*;
import static org.apache.coyote.http11.Constants.a;

@Service
public class ActualizeCostService extends ActualizationService {

    private static final Logger logger = LoggerFactory.getLogger(ActualizeCostService.class);

    private final TransactionIDGenerator transactionIDGenerator;
    @Autowired
    private ActualizationCashflowService actualizationCashflowService;

    @Autowired
    ActualizationCnDnService actualizationCnDnService;

    @Autowired
    ActualizationQuantityRepo actualizationQuantityRepo;


    private final ModelMapperService modelMapperService;

    @Value("${masterBaseURL}")
    private String masterBaseUrl;

    private final PlatformQueueService platformQueueService;

    public ActualizeCostService(TransactionIDGenerator transactionIDGenerator, ModelMapperService modelMapperService, PlatformQueueService platformQueueService) {
        this.transactionIDGenerator = transactionIDGenerator;
        this.modelMapperService = modelMapperService;
        this.platformQueueService = platformQueueService;
    }



    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ResponseEntity actualizeCost(ActualizeObj actualizeObj, boolean isClaimed, String token,String tenantId) throws Exception {
        ResponseEntity responseEntity = null;
        if(isClaimed) {
            for(ActualizeCostObj actualizeCostObj:actualizeObj.getCosts()) {
                checkCanClaimCost(actualizeCostObj.getActualizedCostId(),tenantId,token,actualizeCostObj.getCostType(),actualizeCostObj.getPlannedObligationId());
            }
        }
        for(ActualizeCostObj actualizeCostObj:actualizeObj.getCosts()) {
            actualizeSingleCost(actualizeCostObj,actualizeObj.getPlannedObligation(),isClaimed,token,tenantId);
        }
        responseEntity = new ResponseEntity("{\"ActualizationCostDone\":true}", HttpStatus.OK);
        return responseEntity;
    }

    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ResponseEntity<?> actualizeCostForTransportActualization(ActualizeObj actualizeObj, boolean isClaimed, String token,String tenantId) throws Exception {
        ResponseEntity<?> responseEntity = null;
        PlannedObligationDTO plannedObligationDTO = null;
        if(isClaimed) {
            for(ActualizeCostObj actualizeCostObj:actualizeObj.getCosts()) {
                checkCanClaimCost(actualizeCostObj.getActualizedCostId(),tenantId,token,actualizeCostObj.getCostType(),actualizeCostObj.getPlannedObligationId());
            }
        } else {
            for(ActualizeCostObj actualizeCostObj:actualizeObj.getCosts()) {
                checkCanActualizeCost(tenantId,actualizeCostObj.getCostType(),actualizeCostObj.getPlannedObligationId());
            }
        }
        for(ActualizeCostObj actualizeCostObj:actualizeObj.getCosts()) {
            if(actualizeCostObj.getActualizedStatus().equalsIgnoreCase(ESTIMATE) || (isClaimed && actualizeCostObj.getActualizedStatus().equalsIgnoreCase(ACTUALIZED))) {
                plannedObligationDTO = TransactionIdUtil.query(baseUrl+PLANNED_OBLIGATION_ROOT+GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID+"?tenantId="+tenantId+"&plannedObligationId="+actualizeCostObj.getPlannedObligationId(),HttpMethod.GET,token,null,PlannedObligationDTO.class);

                TradeCostDTO tradeCostDTO =   TransactionIdUtil.query(baseUrl+COST_ROOT+GET_ALL_COSTS_BY_COST_ID+"?costId="+actualizeCostObj.getCostId()+"&tenantId="+tenantId,HttpMethod.GET,token,null, TradeCostDTO.class);
                actualizeSingleCost_v1(actualizeCostObj,plannedObligationDTO,isClaimed,tradeCostDTO.getQuantity(),token,tenantId,false);
            }
        }
        responseEntity = new ResponseEntity<>(ReturnStatus.successInstance("Cost Actualized Successfully"), HttpStatus.OK);
        return responseEntity;
    }

    private void checkCanActualizeCost(String tenantId,String costType, String plannedObligationId) throws Exception {
        boolean canActualize = false;
        if(!costType.equalsIgnoreCase(LUMPSUM)) {
            List<ActualizedQuantityObligations> actualizedQuantityObligationsList =  actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationId(
                    tenantId,plannedObligationId);
            if(actualizedQuantityObligationsList != null && actualizedQuantityObligationsList.size() > 0){
               canActualize = true;
            }
        } else {
            canActualize = true;
        }
        if(!canActualize) {
            throw new Exception("{\"status\":\"Quantity Actualization is not found.\"}");
        }
    }

    private void checkCanClaimCost(String costId, String tenantId, String token,String costType,String planObligationId) throws Exception {
        boolean canClaim = false;
        if(!costType.equalsIgnoreCase(LUMPSUM)) {
           List<ActualizedQuantityObligations> actualizedQuantityObligationsList =  actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationId(
                   tenantId,planObligationId);
           if(actualizedQuantityObligationsList != null && actualizedQuantityObligationsList.size() > 0){
               List<ActualizedQuantityObligations> claimedQuantityObligations =
                       actualizedQuantityObligationsList.stream().filter(obj -> (obj.getClaimedQuantity() != null && obj.getClaimedQuantity() > 0)).collect(Collectors.toList());
               if(claimedQuantityObligations.size() == actualizedQuantityObligationsList.size()) {
                   canClaim = true;
               }
           }
        } else {
            canClaim = true;
        }
        if(!canClaim) {
            throw new Exception("Quantity claim is not found.");
        }
        if(costId != null && !StringUtils.isEmpty(costId)) {
            List<SearchCriteria> searchCriteriaList;
            searchCriteriaList= new ArrayList<>();
            searchCriteriaList.add(new SearchCriteria("tenantId",EQUALS,tenantId));
            searchCriteriaList.add(new SearchCriteria("costId",EQUALS,costId));
            searchCriteriaList.add(new SearchCriteria("quantityStatus",EQUALS,ACTUAL));
            searchCriteriaList.add(new SearchCriteria("stage",EQUALS, ACCRUED));
            searchCriteriaList.add(new SearchCriteria("cashflowStatus",EQUALS, ACTIVE));
            searchCriteriaList.add(new SearchCriteria("type",EQUALS,COST));
            CashflowDataDTO[] cashFlowBaseDTO = TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT+GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST,token,searchCriteriaList,CashflowDataDTO[].class);
            if (cashFlowBaseDTO.length > 1){
                throw new Exception("More than one OR No Cashflow found");
            }
            searchCriteriaList.remove(searchCriteriaList.size() -1);
            searchCriteriaList.add(new SearchCriteria("type",EQUALS, COST_REVERSAL));
            CashflowDataDTO[] cashFlowToCheckCnDn = TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT+GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST,token,searchCriteriaList,CashflowDataDTO[].class);
            if (cashFlowToCheckCnDn.length > 0){
                if (cashFlowToCheckCnDn[0].getTradeId() != null) {
                    throw new Exception("Selected cost(s) is already claimed");
                }
                /**
                 * Condition to check whether invoicing happened or not in claims flow as it is mandatory
                 */
                if (StringUtils.isEmpty(cashFlowBaseDTO[0].getInvoiceStatus())) {
                    throw new Exception("Cannot claim the cost(s) because invoicing is not done");
                }
            }
        } else {
            throw new Exception("Cannot claim the cost(s) because invoicing is not done");
        }
    }

    public ReturnStatus AcutalizeandCreatCost(ActulalizeCostPlannedObligationDTO actulalizeCostPlannedObligationDTO, User principal,String token) throws Exception {

        TradeCostDTO tradeCostDTO = actulalizeCostPlannedObligationDTO.getCosts();
        PlannedObligationDTO plannedObligationDTO = actulalizeCostPlannedObligationDTO.getPlannedObligation();
        ActualizeCostObjDTO actualizeCostObjDTO = new ActualizeCostObjDTO();
        actualizeCostObjDTO.setTradeId(tradeCostDTO.getTradeId());  // tradeId or planned obligationId
        actualizeCostObjDTO.setPlannedObligationId(tradeCostDTO.getLinkTo());
        actualizeCostObjDTO.setCostCurrency(tradeCostDTO.getCostCurrency());
        actualizeCostObjDTO.setCostType(tradeCostDTO.getCostType());
        actualizeCostObjDTO.setCostChargesType(tradeCostDTO.getCostChargesType());
        actualizeCostObjDTO.setCostGroup(tradeCostDTO.getCostGroup());
        actualizeCostObjDTO.setCostFor(tradeCostDTO.getCostFor());
        actualizeCostObjDTO.setCostValue(tradeCostDTO.getCostValue());
        actualizeCostObjDTO.setCostId(tradeCostDTO.getCostId());
        actualizeCostObjDTO.setCounterparty(tradeCostDTO.getCounterparty());
        actualizeCostObjDTO.setName(tradeCostDTO.getName());
        actualizeCostObjDTO.setInputDate(tradeCostDTO.getInputDate());
        actualizeCostObjDTO.setDescription(tradeCostDTO.getDescription());
        actualizeCostObjDTO.setPaymentDueDate(plannedObligationDTO.getDeliveryEndDate());
        actualizeCostObjDTO.setPaymentTerm(tradeCostDTO.getPaymentTerm());
        actualizeCostObjDTO.setPaymentType(tradeCostDTO.getPaymentType());
        actualizeCostObjDTO.setPercentageComponent(tradeCostDTO.getPercentageComponent());
        actualizeCostObjDTO.setPercentagePayable(tradeCostDTO.getPercentagePayable());
        actualizeCostObjDTO.setQuantityOption(tradeCostDTO.getQuantityOption());
        actualizeCostObjDTO.setTaxApplicable(tradeCostDTO.isTaxApplicable());
        actualizeCostObjDTO.setType(tradeCostDTO.getType());
        actualizeCostObjDTO.setUom(tradeCostDTO.getUom());
        actualizeCostObjDTO.setActualizedStatus("Estimate");
        actualizeCostObjDTO.setLinkTo(tradeCostDTO.getLinkTo());
        actualizeCostObjDTO.setCostId(tradeCostDTO.getCostId());
        actualizeCostObjDTO.setPercentagePayable(100);

        ActualizeCostObj actualizeCostObj  =   modelMapperService.map(actualizeCostObjDTO,ActualizeCostObj.class);
        actualizeCostObj.setTenantId(principal.getTenantId());
        try {
            this.actualizeSingleCost_v1(actualizeCostObj,plannedObligationDTO,false,tradeCostDTO.getQuantity(),token,principal.getTenantId(),true);
        } catch (Exception ex) {
            logger.error("Failed to Actualize the Cost , error is: ",ex);
            throw new TaomishError("Failed to Actualize the Cost");
        }
        return ReturnStatus.successInstance("Actulized Successfully");
    }

    public ReturnStatus actualizeSingleCost_v1(ActualizeCostObj cost,PlannedObligationDTO plannedObligationDTO, boolean isClaimed, double quantity, String token, String tenantId,boolean useManualQuantity) throws Exception {
        String actualizationId = TransactionIdUtil.generateRandomId();
        String actualizationCostId = "";
        ActualizedCost actualizedCost;
        List<ActualizedQuantityObligations> actualizedQuantityObligations = actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationIdAndSplitSequenceNumber(tenantId,plannedObligationDTO.getPlannedObligationId(),cost.getSplitSequenceNumber());
        if (cost.getCostType().equalsIgnoreCase(LUMPSUM) || (plannedObligationDTO.getObligationState().get(PlannedObligationState.ACTUALIZED) && !cost.getCostType().equalsIgnoreCase(LUMPSUM))) {
            logger.info("Making old cost cashflow to status Defunct and actualized cost");
                actualizedCost = new ActualizedCost();
                BeanUtils.copyProperties(cost, actualizedCost, "id");
                actualizedCost.setActualizationId(actualizationId);
                actualizedCost.setPlannedObligationId(plannedObligationDTO.getPlannedObligationId());
                actualizedCost.setTradeId(plannedObligationDTO.getTradeId());
                actualizedCost.setEstimatedCostId(cost.getCostId());
                actualizationCostId = transactionIDGenerator.generateId( "actualizationCostId", actualizedCost, tenantId,token,false,"",false);
                if (actualizationCostId == null) {
                    throw new TaomishError("Actualization Cost ID is not generated");
                }
                actualizedCost.setActualizedCostId(actualizationCostId);
            logger.info("Saving Actualized Cost Cashflow with actualizedCostId: {}",actualizedCost.getActualizedCostId());
            actualizedCostRepo.save(actualizedCost);
            try {
                var latestQuantity = quantity;
                if(!useManualQuantity && actualizedQuantityObligations != null && !actualizedQuantityObligations.isEmpty()) {
                    latestQuantity = actualizedQuantityObligations.getFirst().getUnloadQuantity();
                }
                logger.info("Actualized Cost saved successfully updating Actualized Cost-Cashflow");
                this.createCostCashFlow(plannedObligationDTO, actualizedCost, latestQuantity, isClaimed, token,tenantId);
            } catch (Exception ex) {
                logger.info("Failed to Actualize the Cost-cashflow",ex);
                throw new TaomishError("Failed to Actualize the Cost-cashflow");
            }
            logger.info("Publishing Actualized Cost data to EOD Service");
            this.publishActualizedCostData(actualizedCost);
            logger.info("Cost actualization is done for planned Obligation id : {}" , plannedObligationDTO.getPlannedObligationId());
        } else {
            throw new TaomishError("Quantity is not actualized");
        }
         return  ReturnStatus.successInstance("Actualized the cost Succesfully");
    }


    public void actualizeSingleCost(ActualizeCostObj cost,PlannedObligationDTO plannedObligationDTO, boolean isClaimed, String token, String tenantId) throws Exception {
        String actualizationId = TransactionIdUtil.generateRandomId();
        String actualizationCostId = "";
        ActualizedCost actualizedCost;
        double quantity;
        List<ActualizedQuantityObligations> actualizedQuantityObligations = actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationIdAndSplitSequenceNumber(tenantId,plannedObligationDTO.getPlannedObligationId(),cost.getSplitSequenceNumber());
        if (cost.getCostType().equalsIgnoreCase(LUMPSUM) || (!actualizedQuantityObligations.isEmpty() && !cost.getCostType().equalsIgnoreCase(LUMPSUM))) {
            if(cost.getCostType().equalsIgnoreCase(LUMPSUM)) {
                if(actualizedQuantityObligations != null && !actualizedQuantityObligations.isEmpty()) {
                    if (plannedObligationDTO.getTradeTransactionType().equals(TradeTransactionType.BUY)) {
                        quantity = actualizedQuantityObligations.get(0).getLoadQuantity();
                    } else {
                        quantity = actualizedQuantityObligations.get(0).getUnloadQuantity();
                    }
                } else {
                    quantity = plannedObligationDTO.getPlannedQuantity();
                }
            } else {
                if (plannedObligationDTO.getTradeTransactionType().equals(TradeTransactionType.BUY)) {
                    quantity = actualizedQuantityObligations.get(0).getLoadQuantity();
                } else {
                    quantity = actualizedQuantityObligations.get(0).getUnloadQuantity();
                }
            }
            logger.info("Making old cost cashflow to status Defunct and actualized cost");
            if (!isClaimed) {
                if (cost.getActualizedCostId() != null && !StringUtils.isEmpty(cost.getActualizedCostId())) {
                    logger.info("Actualization already happened for the selected cost");
                    return;
                }
            }

            if (cost.getActualizedStatus().equalsIgnoreCase(ACTUALIZED) && (cost.getActualizedCostId() != null && cost.getActualizedCostId().length() > 0)) {
                actualizedCost = actualizedCostRepo.findAllByTenantIdAndActualizedCostId(tenantId,cost.getActualizedCostId()).get(0);
                if(isClaimed) {
                    actualizedCost.setClaimedCostValue(cost.getCostValue());
                } else {
                    BeanUtils.copyProperties(cost, actualizedCost, "id", "actualizationId", "actualizedCostId", "plannedObligationId", "tradeId");
                }
            } else {
                actualizedCost = new ActualizedCost();
                BeanUtils.copyProperties(cost, actualizedCost, "id");
                actualizedCost.setActualizationId(actualizationId);
                actualizedCost.setPlannedObligationId(plannedObligationDTO.getPlannedObligationId());
                actualizedCost.setTradeId(plannedObligationDTO.getTradeId());
                actualizedCost.setEstimatedCostId(cost.getCostId());
                actualizationCostId = transactionIDGenerator.generateId( "actualizationCostId", actualizedCost, tenantId,token,false,"",false);
                if (actualizationCostId == null) {
                    throw new TaomishError("Actualization Cost ID is not generated");
                }
                actualizedCost.setActualizedCostId(actualizationCostId);
            }

            logger.info("Saving Actualized Cost Cashflow with actualizedCostId: {}",actualizedCost.getActualizedCostId());
            actualizedCostRepo.save(actualizedCost);

            logger.info("Actualized Cost saved successfully updating Actualized Cost Cashflow");
            this.createCostCashFlow(plannedObligationDTO, actualizedCost, quantity, isClaimed, token,tenantId);

            logger.info("Publishing Actualized Cost data to EOD Service");
            this.publishActualizedCostData(actualizedCost);
            logger.info("Cost actualization is done for planned Obligation id : {}" , plannedObligationDTO.getPlannedObligationId());
        } else {
            throw new TaomishError("Quantity is not actualized");
        }
    }


    /**
     * Method to create and publish ActualizedCost Data to EODService via Messaging Service
     * @param actualizedCost
     */
    private void publishActualizedCostData(ActualizedCost actualizedCost){
        logger.info("Entered ActializedCostService:publishActualizedCostData()");

        TradeCostEODRawDataDTO tradeCostEODRawDataDTO = new TradeCostEODRawDataDTO();
        try{
            tradeCostEODRawDataDTO.setTradeId(actualizedCost.getTradeId());
            tradeCostEODRawDataDTO.setTenantId(actualizedCost.getTenantId());
            tradeCostEODRawDataDTO.setCostId(actualizedCost.getActualizedCostId());
            tradeCostEODRawDataDTO.setEstimatedCostId(actualizedCost.getEstimatedCostId());
            tradeCostEODRawDataDTO.setPlannedObligationId(actualizedCost.getPlannedObligationId());

            tradeCostEODRawDataDTO.setCostGroup(actualizedCost.getCostGroup());
            tradeCostEODRawDataDTO.setCostType(actualizedCost.getCostType());
            tradeCostEODRawDataDTO.setCostCurrency(actualizedCost.getCostCurrency());
            tradeCostEODRawDataDTO.setCostValue(actualizedCost.getCostValue());
            tradeCostEODRawDataDTO.setActualValue(actualizedCost.getCostValue());
            tradeCostEODRawDataDTO.setPercentagePayable(actualizedCost.getPercentagePayable());
            tradeCostEODRawDataDTO.setQuantityOption(actualizedCost.getQuantityOption());
            tradeCostEODRawDataDTO.setCostFor(actualizedCost.getCostFor());
            tradeCostEODRawDataDTO.setLinkTo(actualizedCost.getLinkTo());
            tradeCostEODRawDataDTO.setSplitSequenceNumber(actualizedCost.getSplitSequenceNumber());
            platformQueueService.sendObject(EXCHANGE_NAME,PHYSICAL_TRADE_COST_ACTUALIZATION_CREATE,tradeCostEODRawDataDTO);
            logger.info("Actualized Cost Data Published Successfully");
        }
        catch(Exception e){
            logger.error("Failed to create and publish Actualized cost data with ID : {} ",actualizedCost.getActualizedCostId(),e);
        }
        logger.info("Exit ActualizedCostService:publishActualizedCostData()");
    }

    public ResponseEntity getActualizedCost(String tenantId, String tradeId, String costId) {
        ResponseEntity responseEntity = null;
        ActualizedCost actualizedCost = actualizedCostRepo.findByTenantIdAndTradeIdAndActualizedCostId(tenantId,tradeId,costId);
        if(actualizedCost != null) {
            responseEntity = new ResponseEntity(actualizedCost,HttpStatus.OK);
        } else {
            responseEntity = new ResponseEntity(new ReturnStatus("No Actualized cost found for trade id :"+tradeId+"" +
                    " and Actualized cost id :"+costId),HttpStatus.BAD_REQUEST);
        }
        return responseEntity;
    }

    private void createCostCashFlow(PlannedObligationDTO plannedObligationDTO, ActualizedCost cost,
                                    double quantity, boolean isClaimed, String token,
                                    String tenantId) throws Exception {
        CashflowDataDTO[] cashflowDTOForInvoiceCheck = null;
        PhysicalTradeDTO trade = TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_ROOT + GET_PHYSICAL_TRADE_BY_TRADE_ID + "?tenantId=" + tenantId + "&tradeId=" + cost.getTradeId(),
                            HttpMethod.GET, token,null, PhysicalTradeDTO.class);
        UpdateCashflowDTO updateCashflowDTO = new UpdateCashflowDTO();
        updateCashflowDTO.setTradeId(trade.getTradeId());
        updateCashflowDTO.setCounterparty(cost.getCounterparty());
        updateCashflowDTO.setCommodity(plannedObligationDTO.getCommodity());
        updateCashflowDTO.setSplitSequenceNumber(cost.getSplitSequenceNumber());
        updateCashflowDTO.setTradeTransactionType(plannedObligationDTO.getTradeTransactionType());
        updateCashflowDTO.setTradeSettlementCurrency(plannedObligationDTO.getTradeSettlementCurrency());
        updateCashflowDTO.setTotalContractQuantity(plannedObligationDTO.getTotalTradeQty());
        updateCashflowDTO.setObligationId(plannedObligationDTO.getTradeObligationId());
        updateCashflowDTO.setObligationQuantity(quantity);
        updateCashflowDTO.setPlanId(plannedObligationDTO.getPlanId());
        updateCashflowDTO.setPlannedObligationId(plannedObligationDTO.getPlannedObligationId());
        updateCashflowDTO.setDeliveryDate(cost.getPaymentDueDate());
        updateCashflowDTO.setCostValue(cost.getCostValue());
        updateCashflowDTO.setCostUom(cost.getUom());
        updateCashflowDTO.setQuantityOption(cost.getQuantityOption());
        updateCashflowDTO.setCostType(cost.getCostType());
        updateCashflowDTO.setCostId(cost.getActualizedCostId());
        updateCashflowDTO.setCostCurrency(cost.getCostCurrency());
        updateCashflowDTO.setQuantityUom(plannedObligationDTO.getQuantityUOM());
        updateCashflowDTO.setProfitcenter(plannedObligationDTO.getProfitcenter());
        updateCashflowDTO.setCompany(plannedObligationDTO.getCompany());
        updateCashflowDTO.setCostName(cost.getName());
        updateCashflowDTO.setCostGroup(cost.getCostGroup());
        updateCashflowDTO.setTradeDateTime(plannedObligationDTO.getTradeDateTime());
        updateCashflowDTO.setDescription(cost.getDescription());

        updateCashflowDTO.setStage(ACCRUED);
        updateCashflowDTO.setType(cost.getCostChargesType());
        if(Boolean.TRUE.equals(plannedObligationDTO.getProvisionalPricing()) && !plannedObligationDTO.getPriceType().equalsIgnoreCase(FIXEDPRICED)) {
            updateCashflowDTO.setPriceType(plannedObligationDTO.getPriceType());
            updateCashflowDTO.setTradePriceCurrency(plannedObligationDTO.getProvisionalPriceCurrency());
            updateCashflowDTO.setTradePriceUom(plannedObligationDTO.getProvisionalPriceUom());
            updateCashflowDTO.setPrice(plannedObligationDTO.getProvisionalPrice());
            updateCashflowDTO.setFxRate(plannedObligationDTO.getFxRate());
        } else {
            updateCashflowDTO.setPriceType(plannedObligationDTO.getPriceType());
            updateCashflowDTO.setTradePriceCurrency(plannedObligationDTO.getTradePriceCurrency());
            updateCashflowDTO.setTradePriceUom(plannedObligationDTO.getTradePriceUom());
            updateCashflowDTO.setPrice(plannedObligationDTO.getTradePrice());
            updateCashflowDTO.setFxRate(plannedObligationDTO.getFxRate());
        }
        updateCashflowDTO.setTradePrice(updateCashflowDTO.getPrice());
        /**
         * Actualization is happening for the first time, so create the new cashflows
         */
        if (!isClaimed) {
            actualizationCashflowService.runCreatCashflows(updateCashflowDTO,token,tenantId);
        }
        if (isClaimed) {
            /**
             * if isClaimed is true then trigger reversal cashflow flow and cn/dn services
             */
            List<SearchCriteria> searchCriteriaList;
            searchCriteriaList= new ArrayList<>();
            searchCriteriaList.add(new SearchCriteria("tenantId",EQUALS,tenantId));
            searchCriteriaList.add(new SearchCriteria("costId",EQUALS,cost.getActualizedCostId()));
            searchCriteriaList.add(new SearchCriteria("quantityStatus",EQUALS,ACTUAL));
            searchCriteriaList.add(new SearchCriteria("stage",IN, Arrays.asList(INVOICE_FINAL,INVOICE_FINAL_PROVISIONAL,ACCRUED,ACCRUED_PROVISIONAL)));
            searchCriteriaList.add(new SearchCriteria("cashflowStatus",EQUALS, ACTIVE));
            searchCriteriaList.add(new SearchCriteria("type",EQUALS,COST));
            cashflowDTOForInvoiceCheck = TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT+GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST,token,searchCriteriaList,CashflowDataDTO[].class);
            if(cashflowDTOForInvoiceCheck != null && cashflowDTOForInvoiceCheck.length > 0) {
                CashflowDataDTO invoiceCashflow = cashflowDTOForInvoiceCheck[0];
                if (!StringUtils.isEmpty(cashflowDTOForInvoiceCheck[0].getInvoiceNumber())) {
                    invoiceCashflow.setTradeTransactionType(trade.getTradeTransactionType());
                    invoiceCashflow.setClaimQuantity(updateCashflowDTO.getObligationQuantity());
                    invoiceCashflow.setFxRate(updateCashflowDTO.getFxRate());
                    invoiceCashflow.setType(updateCashflowDTO.getType());
                    invoiceCashflow.setCostType(cost.getCostType());
                    invoiceCashflow.setCostUom(cost.getUom());
                    invoiceCashflow.setCostValue(cost.getClaimedCostValue());
                    actualizationCnDnService.generateClaimCashflow(invoiceCashflow, token, tenantId);
                }
            }
        }
    }

    public ActualizedCostDTO checkForCostDelete(String costId, User principal) {
       var costObj =  actualizedCostRepo.findByEstimatedCostIdAndTenantId(costId,principal.getTenantId());
        return this.modelMapperService.map(costObj,ActualizedCostDTO.class);
    }

    public List<ActualizedCostDTO> getactulaizedfromChargeId(String chargeId, User principal, String authorization) throws TaomishError {
        List<ActualizedCostDTO> actualizedCostDTOList = null;
        try {
            List<ActualizedCost> actualizedCostList = this.actualizedCostRepo.findAllByTenantIdAndEstimatedCostId(principal.getTenantId(),chargeId);
            actualizedCostDTOList = TransactionIdUtil.convertList(actualizedCostList, ActualizedCostDTO.class);
        } catch (Exception e) {
            logger.error(" failed to get Actulaized cost object: " , e);
            throw new TaomishError("failed to get Actulaized cost object:",e);
        }
        return actualizedCostDTOList;
    }
}
