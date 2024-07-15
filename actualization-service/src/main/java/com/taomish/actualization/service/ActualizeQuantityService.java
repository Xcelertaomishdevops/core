package com.taomish.actualization.service;

import com.taomish.actualization.dto.ActualizeCostObj;
import com.taomish.actualization.dto.ActualizeObj;
import com.taomish.actualization.model.ActualizedQuality;
import com.taomish.actualization.model.ActualizedQuantityObligations;
import com.taomish.actualization.model.BillOfLanding;
import com.taomish.actualization.repo.BillOfLandingRepo;
import com.taomish.common.domain.TaomishError;
import com.taomish.common.searchcriteria.SearchCriteria;
import com.taomish.common.searchcriteria.SpecBuilderUtil;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.dtos.ReturnStatus;
import com.taomish.dtos.actualizationservice.ActualizedQuantityObligationsDTO;
import com.taomish.dtos.cashflowservice.CashflowDataDTO;
import com.taomish.dtos.cashflowservice.UpdateCashflowDTO;
import com.taomish.dtos.invoice.InvoiceDTO;
import com.taomish.dtos.physicaltradeplanning.PhysicalTradePlanningDTO;
import com.taomish.dtos.physicaltradeplanning.PlanningDTO;
import com.taomish.dtos.physicaltradeplanning.TradePlanningDetails;
import com.taomish.dtos.physicaltradeservice.InterCompanyTradeDTO;
import com.taomish.dtos.physicaltradeservice.PhysicalTradeDTO;
import com.taomish.dtos.physicaltradeservice.PlannedObligationDTO;
import com.taomish.dtos.qualityspec.QualitySpecDTO;
import com.taomish.dtos.tradepricingservice.PriceAllocationCheckDTO;
import com.taomish.dtos.tradepricingservice.PriceAllocationDTO;
import com.taomish.dtos.tradepricingservice.PricingPlanDTO;
import com.taomish.dtos.transportAllocation.SeaFreightDetailsDTO;
import com.taomish.dtos.transportactualizationservice.TransportActualizationQuantityRows;
import com.taomish.enums.PlannedObligationState;
import com.taomish.enums.TradeTransactionType;
import com.taomish.messaging.PlatformQueueService;
import com.taomish.transaction_reference.service.TransactionIDGenerator;
import com.taomish.web.security.models.User;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ObjectUtils;
import org.primefaces.shaded.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.taomish.RestEndPoints.CashflowRestEndPoints.*;
import static com.taomish.RestEndPoints.PhysicalRestEndPoint.*;
import static com.taomish.RestEndPoints.PlanningRestEndPoint.*;
import static com.taomish.RestEndPoints.PricingRestEndPoint.*;
import static com.taomish.RestEndPoints.PricingServiceRestEndPoints.ADVANCE_ALLOCATION_ROOT;
import static com.taomish.constants.ActualizationConstants.AND;
import static com.taomish.constants.ActualizationConstants.*;
import static com.taomish.constants.CashflowConstants.*;
import static com.taomish.constants.EODConstants.EXCHANGE_NAME;
import static com.taomish.constants.EODConstants.QUANTITY_ACTUALIZATION;
import static com.taomish.constants.InvoiceConstants.*;
import static com.taomish.constants.PhysicalConstants.*;
import static com.taomish.constants.PlannedObligationConstants.*;
import static com.taomish.constants.PlanningConstants.BACK2BACK;
import static com.taomish.constants.PlanningConstants.WASHOUT;
import static com.taomish.constants.QualitySpecConstants.GET_QUALITY_SPEC_BY_TRADE_ID;
import static com.taomish.constants.QualitySpecConstants.QUALITY_SPECIFICATION_ROOT;
import static com.taomish.constants.TransportActualizationConstants.PENDING;
import static com.taomish.constants.TransportAllocationConstants.*;

@Service
public class ActualizeQuantityService extends ActualizationService {

    private static final Logger logger = LoggerFactory.getLogger(ActualizeQuantityService.class);

    private final TransactionIDGenerator transactionIDGenerator;
    private final BillOfLandingRepo billOfLadingRepo;
    private final ActualizationCnDnService actualizationCnDnService;
    private final  ActualizationCashflowService actualizationCashflowService;
    private final  ActualizationService actualizationService;
    private final  ActualizeCostService actualizeCostService;
    private final PlatformQueueService platformQueueService;

    @Value("${masterBaseURL}")
    private String masterBaseUrl;


    public ActualizeQuantityService(TransactionIDGenerator transactionIDGenerator, BillOfLandingRepo billOfLadingRepo,
                                    ActualizationCnDnService actualizationCnDnService,
                                    ActualizationCashflowService actualizationCashflowService,
                                    ActualizationService actualizationService,
                                    ActualizeCostService actualizeCostService, PlatformQueueService platformQueueService) {
        this.transactionIDGenerator = transactionIDGenerator;
        this.billOfLadingRepo = billOfLadingRepo;
        this.actualizationCnDnService = actualizationCnDnService;
        this.actualizationCashflowService = actualizationCashflowService;
        this.actualizationService = actualizationService;
        this.actualizeCostService = actualizeCostService;
        this.platformQueueService = platformQueueService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ResponseEntity actualizeQuantity(ActualizeObj actualizeObj, Boolean isClaimed, String token, String tenantId) throws Exception {
        ResponseEntity responseEntity = null;
        if (actualizeObj.getPlannedObligation().getPriceType() != null && (actualizeObj.getPlannedObligation().getPriceType().equalsIgnoreCase(PTBF) || actualizeObj.getPlannedObligation().getPriceType().equalsIgnoreCase(DIFFERENTIAL))) {
            PriceAllocationDTO priceAllocationDTO = TransactionIdUtil.query(baseUrl + MANUAL_PRICING_ROOT + GET_PRICE_ALLOCATION_LIST_BY_OBLIGATION_ID + QUERY+TENANT_ID_EQ + tenantId + OBLIGATION_ID + actualizeObj.getPlannedObligation().getPlannedObligationId(), HttpMethod.GET, token, null, PriceAllocationDTO.class);
            /**
             * Privisonal -> done
             * priced -> done
             * !provisonal && !priced
             */
            PhysicalTradeDTO trade = TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_ROOT + GET_PHYSICAL_TRADE_BY_TRADE_ID +
                    TRADE_ID + actualizeObj.getPlannedObligation().getTradeId() + AND+TENANT_ID_EQ + tenantId, HttpMethod.GET, token, null, PhysicalTradeDTO.class);
           if (!Objects.requireNonNull(priceAllocationDTO).getPriceAllocationLists().isEmpty() || (Objects.requireNonNull(trade).getIsProvisionalPricing() && !trade.getPriceType().equals(FIXEDPRICED))) {
                createActualizeQuantity(actualizeObj, priceAllocationDTO, isClaimed, token, tenantId);
                responseEntity = new ResponseEntity(new ReturnStatus("ActualizationQuantityDone."), HttpStatus.OK);
            } else {
                responseEntity = new ResponseEntity(new ReturnStatus("You cannot actualize.( Price is not allocated )"), HttpStatus.NOT_FOUND);
            }
        } else {
            createActualizeQuantity(actualizeObj, null, isClaimed, token, tenantId);
            responseEntity = new ResponseEntity(new ReturnStatus("ActualizationQuantityDone "), HttpStatus.OK);
        }
        return responseEntity;
    }


    private void createActualizeQuantity(ActualizeObj actualizeObj, PriceAllocationDTO priceAllocationDTOS, Boolean isClaimed, String token, String tenantId) throws Exception {
        String actualizationQuantityId = "";
        boolean isEdit = false;
        ActualizedQuantityObligations actualizedQuantityObligations;
        List<ActualizedQuantityObligations> list = actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationId(tenantId, actualizeObj.getPlannedObligation().getPlannedObligationId());
        if (!list.isEmpty()) {
            actualizedQuantityObligations = list.get(0);
            isEdit = true;
        } else {
            try {
                actualizationQuantityId = transactionIDGenerator.generateId( ACTUALIZATION_QUANTITY_ID, actualizeObj.getPlannedObligation(), tenantId, token,false,"",false);
                if (actualizationQuantityId == null) {
                    throw new TaomishError("Actualization Quantity ID is not generated :");
                }
                actualizedQuantityObligations = new ActualizedQuantityObligations();
                actualizedQuantityObligations.setActualizedQuantityId(actualizationQuantityId);
//          changing actualization_id  to actualizationQuantityId as actualization Id will create duplicate records
                actualizedQuantityObligations.setActualizationId(actualizationQuantityId);
            } catch (TaomishError e) {
                throw new TaomishError("Actualization Quantity ID is not generated ",e);
            }
        }
        actualizedQuantityObligations.setPlannedObligationId(actualizeObj.getPlannedObligation().getPlannedObligationId());
        actualizedQuantityObligations.setPlannedObligationType(actualizeObj.getPlannedObligation().getTradeTransactionType());
        actualizedQuantityObligations.setTradeId(actualizeObj.getPlannedObligation().getTradeId());
        actualizedQuantityObligations.setPlannedQuantity(actualizeObj.getQuantity().getPlannedQuantity());
        actualizedQuantityObligations.setLoadQuantity(actualizeObj.getQuantity().getLoadQuantity());
        actualizedQuantityObligations.setUnloadQuantity(actualizeObj.getQuantity().getUnloadQuantity());
        actualizedQuantityObligations.setBrand(actualizeObj.getQuantity().getBrand());
        actualizedQuantityObligations.setGrade(actualizeObj.getQuantity().getGrade());
        actualizedQuantityObligations.setOrigin(actualizeObj.getQuantity().getOrigin());
        actualizedQuantityObligations.setCommodity(actualizeObj.getQuantity().getCommodity());
        actualizedQuantityObligations.setQuantityUom(actualizeObj.getQuantity().getQuantityUom());
        actualizedQuantityObligations.setPurpose(actualizeObj.getQuantity().getPurpose());
        actualizedQuantityObligations.setTenantId(tenantId);
        if (actualizeObj.getQuantity().getActualizationEventMapping() != null && !actualizeObj.getQuantity().getActualizationEventMapping().isEmpty()) {
            actualizedQuantityObligations.setActualizationEventMapping(getEventMapping(actualizeObj.getQuantity().getActualizationEventMapping()));
        }
        logger.info("Saving Quantity Actualization for ObligationId  : {}" , actualizedQuantityObligations.getPlannedObligationId());
        actualizationQuantityRepo.save(actualizedQuantityObligations);

//        publishMessage(EXCHANGE_NAME, PHYSICAL_TRADE_ACTUALIZATION_TOPIC, UPDATE, actualizedQuantityObligations);
        PlannedObligationDTO plannedObligationDTO;
        try {
            plannedObligationDTO = updateActualizedStatus(actualizeObj.getPlannedObligation().getPlannedObligationId(), token, tenantId);
        } catch (Exception e) {
            throw new TaomishError("Updating planned obligation failed ",e);
        }
        if(!plannedObligationDTO.isExternalRecord()) {
            try {
                createQuantityCashFlow(actualizedQuantityObligations.getLoadQuantity(), actualizedQuantityObligations.getUnloadQuantity(), actualizeObj.getPlannedObligation(),
                        isEdit, priceAllocationDTOS, isClaimed, token, tenantId, actualizedQuantityObligations.getSplitSequenceNumber(), actualizedQuantityObligations.getClaimedQuantity(), null, null);
            } catch (Exception e) {
                logger.error("Cash-flow creation failed ! : {}",plannedObligationDTO.getPlannedObligationId(),e);
                plannedObligationDTO.getObligationState().put(PlannedObligationState.ACTUALIZED, false);
                TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + UPDATE_TRADE_PLANOBLIGATION + QUERY+TENANT_ID_EQ + tenantId, HttpMethod.POST, token, plannedObligationDTO, PlannedObligationDTO.class);
                throw new TaomishError("Cash-flow creation failed ",e);
            }
        }

        // updateFXAlloactionCashflow(actualizeObj.getPlannedObligation().getPlannedObligationId());

        // Create and Publish Rabbit message to update Actualized Quantity data to EOD service
        logger.info("Publishing Rabbit message to update Actualization data in EOD service for obligationID:{}",actualizedQuantityObligations.getPlannedObligationId());
        ActualizedQuantityObligationsDTO actualizedQuantityObligationsDTO = new ActualizedQuantityObligationsDTO();
        actualizedQuantityObligationsDTO.setPlannedObligationId(actualizedQuantityObligations.getPlannedObligationId());
        actualizedQuantityObligationsDTO.setLoadQuantity(actualizedQuantityObligations.getLoadQuantity());
        actualizedQuantityObligationsDTO.setBrand(actualizedQuantityObligations.getBrand());
        actualizedQuantityObligationsDTO.setGrade(actualizedQuantityObligations.getGrade());
        actualizedQuantityObligationsDTO.setOrigin(actualizedQuantityObligations.getOrigin());
        actualizedQuantityObligationsDTO.setUnloadQuantity(actualizedQuantityObligations.getUnloadQuantity());
        actualizedQuantityObligationsDTO.setTenantId(tenantId);
        platformQueueService.sendObject(EXCHANGE_NAME, QUANTITY_ACTUALIZATION, actualizedQuantityObligationsDTO);

        logger.info("Quantity actualization is done for planned Obligation id : {}",actualizeObj.getPlannedObligation().getPlannedObligationId());
    }

    @SneakyThrows
    private void createQuantityCashFlow(double loadQuantity, double unloadQuantity, PlannedObligationDTO plannedObligation,
                                        boolean isEdit, PriceAllocationDTO priceAllocationDTOS, Boolean isClaimed, String token,
                                        String tenantId, double splitSequenceNumber, double claimQuantity, TransportActualizationQuantityRows quantityRow, List<TransportActualizationQuantityRows> quantityRows) throws Exception {
        double quantity;
        String type;
        PhysicalTradeDTO trade = TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_ROOT + GET_PHYSICAL_TRADE_BY_TRADE_ID +
                TRADE_ID + plannedObligation.getTradeId() + AND+TENANT_ID_EQ + tenantId, HttpMethod.GET, token, null, PhysicalTradeDTO.class);
        PhysicalTradePlanningDTO physicalTradePlanningDTO = TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_PLANNING_ROOT + GET_PHYSICAL_TRADE_PLANNING_BY_PLAN_ID + QUERY+TENANT_ID_EQ + tenantId + "&planId=" + plannedObligation.getPlanId(), HttpMethod.GET, token, null, PhysicalTradePlanningDTO.class);
        String matchType = "";
        if(physicalTradePlanningDTO != null){
            matchType = physicalTradePlanningDTO.getMatchType();
        }
        UpdateCashflowDTO updateCashflowDTO = new UpdateCashflowDTO();
        updateCashflowDTO.setDocByPassId(plannedObligation.getDocByPassId());
        updateCashflowDTO.setTradeId(plannedObligation.getTradeId());
        updateCashflowDTO.setCounterparty(plannedObligation.getCounterparty());
        updateCashflowDTO.setCommodity(plannedObligation.getCommodity());
        updateCashflowDTO.setTradeTransactionType(plannedObligation.getTradeTransactionType());
        updateCashflowDTO.setTradeSettlementCurrency(plannedObligation.getTradeSettlementCurrency());
        updateCashflowDTO.setTotalContractQuantity(plannedObligation.getTotalTradeQty());
        updateCashflowDTO.setObligationId(plannedObligation.getTradeObligationId());
        updateCashflowDTO.setPlanId(plannedObligation.getPlanId());
        updateCashflowDTO.setPlannedObligationId(plannedObligation.getPlannedObligationId());
        updateCashflowDTO.setDeliveryDate(plannedObligation.getDeliveryEndDate());
        updateCashflowDTO.setSplitSequenceNumber(splitSequenceNumber);
        updateCashflowDTO.setQuantityUom(plannedObligation.getQuantityUOM());
        updateCashflowDTO.setPriceType(plannedObligation.getPriceType());
        updateCashflowDTO.setProfitcenter(plannedObligation.getProfitcenter());
        updateCashflowDTO.setCompany(plannedObligation.getCompany());
        updateCashflowDTO.setTradeDateTime(plannedObligation.getTradeDateTime());

        PriceAllocationCheckDTO priceAllocationCheckDTO = TransactionIdUtil.query(baseUrl + ADVANCE_ALLOCATION_ROOT + GET_PRICE_ALLOCATED_CHECK_BY_PLANNED_OBLIGATION_ID + QUE_OBLIGATION_ID + plannedObligation.getPlannedObligationId() + AND+TENANT_ID_EQ + tenantId, HttpMethod.GET, token, null, PriceAllocationCheckDTO.class);
        assert trade != null;
        updateCashflowDTO.setPriceStatus(PRICED);
        if (Boolean.TRUE.equals(trade.getIsProvisionalPricing()) && !trade.getPriceType().equalsIgnoreCase(FIXEDPRICED) || (trade.getPriceType().equalsIgnoreCase(PTBF) && plannedObligation.getProvisionalPriceType() != null && !plannedObligation.getProvisionalPriceType().isEmpty())) {
            updateCashflowDTO.setTradePriceCurrency(plannedObligation.getProvisionalPriceCurrency());
            updateCashflowDTO.setPriceType(plannedObligation.getPriceType());
            updateCashflowDTO.setFxRate(trade.getFxrate());
            updateCashflowDTO.setPriceStatus(UNPRICED);
            if (matchType.equalsIgnoreCase(WASHOUT)) {
                type = getWashoutCashflowType(tenantId, plannedObligation, token);
                updateCashflowDTO.setType(type);
            } else {
                updateCashflowDTO.setType(CASHFLOW_STAGE_PROVISIONAL);
            }
            if (priceAllocationCheckDTO != null && !priceAllocationCheckDTO.isFullyPriced()) {
                if(plannedObligation.getObligationState().get(PlannedObligationState.PRICED)){
                    updateCashflowDTO.setPriceStatus(PRICED);
                    updateCashflowDTO.setStage(ACCRUED);
                    updateCashflowDTO.setType(Trade);
                    updateCashflowDTO.setPrice(Objects.requireNonNull(priceAllocationCheckDTO).getPrice());
                }
                else {
                    updateCashflowDTO.setStage(ACCRUED_PROVISIONAL);
                    updateCashflowDTO.setPrice(plannedObligation.getProvisionalPrice());
                }

            } else {
                updateCashflowDTO.setPriceStatus(PRICED);
                updateCashflowDTO.setStage(ACCRUED);
                updateCashflowDTO.setType(Trade);
                updateCashflowDTO.setPrice(Objects.requireNonNull(priceAllocationCheckDTO).getPrice());
            }
            updateCashflowDTO.setFullyPriced(priceAllocationCheckDTO.isFullyPriced());
        } else {
            if (matchType.equalsIgnoreCase(WASHOUT)) {
                type = getWashoutCashflowType(tenantId, plannedObligation, token);
                updateCashflowDTO.setType(type);
            } else {
                updateCashflowDTO.setType(Trade);
            }
            updateCashflowDTO.setTradePriceCurrency(plannedObligation.getTradePriceCurrency());
            updateCashflowDTO.setPrice(plannedObligation.getTradePrice());
            updateCashflowDTO.setPriceType(plannedObligation.getPriceType());
            updateCashflowDTO.setFxRate(trade.getFxrate());
            updateCashflowDTO.setStage(ACCRUED);
            if (!plannedObligation.getPriceType().equalsIgnoreCase(FIXEDPRICED) && Objects.requireNonNull(priceAllocationCheckDTO).isFullyPriced()) {
                updateCashflowDTO.setPrice(priceAllocationCheckDTO.getPrice());
            }
        }
        updateCashflowDTO.setTradePrice(updateCashflowDTO.getPrice());
        updateCashflowDTO.setTradePriceUom(plannedObligation.getTradePriceUom());
        if (plannedObligation.getTradeTransactionType().equals(TradeTransactionType.BUY)) {
            quantity = loadQuantity;
        } else {
            quantity = unloadQuantity;
        }
        if(claimQuantity != 0) {
            quantity = claimQuantity;
        }
        updateCashflowDTO.setObligationQuantity(quantity);
        List<CashflowDataDTO> cashflowDataDTOList = new ArrayList<>();
        if (Boolean.FALSE.equals(isClaimed)) {
            CashflowDataDTO cashflowDataDTO = null;
            try {
                if(isEdit){
                    updateCashflowDTO.setPlannedObligationId(plannedObligation.getPlannedObligationId());
                    actualizationCashflowService.runUpdateCashflow(tenantId,updateCashflowDTO, token );
                }else{
                    cashflowDataDTO = actualizationCashflowService.runCreatCashflows(updateCashflowDTO, token, tenantId);
                }
            } catch (Exception e) {
                logger.error("Cash-flow creation failed ",e);
                throw new TaomishError("Cash-flow creation failed !",e);
            }
            try {
                updateCashflow(plannedObligation.getPlannedObligationId(), trade, quantity, plannedObligation.getPlannedObligationId(), plannedObligation.getPlanId(), plannedObligation.getPlannedQuantity(), priceAllocationDTOS, token, tenantId, updateCashflowDTO.getTradePrice(), plannedObligation, quantityRow);
            } catch (Exception e) {
                cashflowDataDTOList.add(cashflowDataDTO);
                TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + CASHFLOW_DELETE_ALL + QUERY+TENANT_ID_EQ + tenantId, HttpMethod.POST, token, cashflowDataDTOList, Object.class);
                throw new TaomishError("Cash-flow updation failed",e);
            }
        } else {
            var exceededQuantity = 0.0;
            var exceedingPrice = 0.0;
            if(quantityRow != null && quantityRow.getToleranceBreached() != null && Boolean.TRUE.equals(quantityRow.getToleranceBreached()) && (quantityRow.getSplitSequenceNumber() == 0 || quantityRow.getSplitSequenceNumber() == 1)) {
                var maxTolerance = quantityRow.getTolerance();
                var actualQuantity = quantityRow.getActualQuantity();
                if(quantityRows != null) {
                    actualQuantity = quantityRows.stream().map(TransportActualizationQuantityRows::getActualQuantity).reduce(Double::sum).orElse(0.0);
                }
                if(actualQuantity != 0) {
                    exceededQuantity = TransactionIdUtil.formatToDecimalPoint((actualQuantity - maxTolerance),3);
                    if(quantityRow.getSplitSequenceNumber() == 0) {
                        var intoleranceQuantity = actualQuantity - exceededQuantity;
                        claimQuantity = intoleranceQuantity;
                        quantity = intoleranceQuantity;
                    }
                    exceedingPrice = quantityRow.getSettlementPrice();
                }
            }
            List<SearchCriteria> claimdCashflowCriteria = new ArrayList<>();
            claimdCashflowCriteria.add(new SearchCriteria(TEN_ANT_ID, EQ, tenantId));
            claimdCashflowCriteria.add(new SearchCriteria(PLANNED_OBLGN_ID, EQ, plannedObligation.getPlannedObligationId()));
            claimdCashflowCriteria.add(new SearchCriteria(STAGE, "in", List.of(ACCRUED,ACCRUED_PROVISIONAL)));
            claimdCashflowCriteria.add(new SearchCriteria("type", "in", List.of(Trade,TRADE_REVERSAL,CASHFLOW_TYPE_PROVISIONAL)));
            claimdCashflowCriteria.add(new SearchCriteria(SPLIT_SEQUENCE_NO, "in", (splitSequenceNumber > 1)?List.of(splitSequenceNumber):List.of(splitSequenceNumber,0)));
            claimdCashflowCriteria.add(new SearchCriteria(CASH_FLOW_STATUS, EQ, ACTIVE));
            TransactionIdUtil.defunctCashflows(baseUrl,token,claimdCashflowCriteria);
            List<SearchCriteria> searchCriteriaList;
            searchCriteriaList = new ArrayList<>();
            searchCriteriaList.add(new SearchCriteria(TEN_ANT_ID, EQ, tenantId));
            searchCriteriaList.add(new SearchCriteria(PLANNED_OBLGN_ID, EQ, plannedObligation.getPlannedObligationId()));
            searchCriteriaList.add(new SearchCriteria(STAGE, "in", Arrays.asList(INVOICE_FINAL, INVOICE_FINAL_PROVISIONAL,INVOICE_PROVISIONAL)));
            searchCriteriaList.add(new SearchCriteria("type", "in", Arrays.asList(Trade, CASHFLOW_STAGE_PROVISIONAL)));
            searchCriteriaList.add(new SearchCriteria(SPLIT_SEQUENCE_NO, EQ, splitSequenceNumber));
            searchCriteriaList.add(new SearchCriteria(CASH_FLOW_STATUS, EQ, ACTIVE));
            CashflowDataDTO[] cashflowDTOForInvoiceCheck = TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaList, CashflowDataDTO[].class);
            if (cashflowDTOForInvoiceCheck != null && cashflowDTOForInvoiceCheck.length > 0) {
                CashflowDataDTO invoiceCashflow = cashflowDTOForInvoiceCheck[0];
                if (!invoiceCashflow.getInvoiceNumber().isEmpty()) {
                    invoiceCashflow.setTradeTransactionType(plannedObligation.getTradeTransactionType());
                    invoiceCashflow.setClaimQuantity(claimQuantity);
                    invoiceCashflow.setPriceStatus(updateCashflowDTO.getPriceStatus());
                    invoiceCashflow.setTradePrice(updateCashflowDTO.getTradePrice());
                    invoiceCashflow.setFxRate(updateCashflowDTO.getFxRate());
                    if(List.of(CASHFLOW_STAGE_PROVISIONAL,ACCRUED_PROVISIONAL).contains(invoiceCashflow.getType()) && updateCashflowDTO.isFullyPriced()) {
                        invoiceCashflow.setType(Trade);
                    }
                    actualizationCnDnService.generateClaimCashflow(invoiceCashflow, token, tenantId);
                }
                try {
                    updateCashflow(plannedObligation.getPlannedObligationId(), trade, quantity, plannedObligation.getPlannedObligationId(), plannedObligation.getPlanId(), plannedObligation.getPlannedQuantity(), priceAllocationDTOS, token, tenantId, invoiceCashflow.getTradePrice(), plannedObligation,quantityRow);
                    if(exceededQuantity != 0) {
                        cashflowDataDTOList.add(createExceedingAmountCashflow(plannedObligation.getPlannedObligationId(),exceededQuantity,token,tenantId,plannedObligation,exceedingPrice));
                    }
                } catch (Exception e) {
                    logger.error("Cash-flow updation failed",e);
                    TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + CASHFLOW_DELETE_ALL + "?tenantId=" + tenantId, HttpMethod.POST, token, cashflowDataDTOList, Object.class);
                }
            }

        }
    }

    @SneakyThrows
    @Deprecated
    private boolean canActaulize(String plannedObligationId,Integer splitNumber, String tenantId, String token, boolean isClaimed) {
        if(isClaimed) {
            List<SearchCriteria> invoiceCriteria = new ArrayList<>();
            invoiceCriteria.add(new SearchCriteria(TEN_ANT_ID, EQ, tenantId));
            invoiceCriteria.add(new SearchCriteria(PLANNED_OBLGN_ID, EQ, plannedObligationId));
            invoiceCriteria.add(new SearchCriteria("finalInvoiceType", "equals", FINAL_AGAINST_CLAIM));
            invoiceCriteria.add(new SearchCriteria("splitNumber", "in", List.of(0,splitNumber)));
            invoiceCriteria.add(new SearchCriteria("status", "notequals", VOID));
            var invoices = TransactionIdUtil.queryList(baseUrl+INVOICE_ROOT+GET_INVOICE_BY_CRITERIA,HttpMethod.POST,token,invoiceCriteria, InvoiceDTO.class).stream().filter(item -> !item.getStatus().equalsIgnoreCase(INITIATED)).toList();;
            if(!invoices.isEmpty()) {
                var invoiceNumber = invoices.stream().map(InvoiceDTO::getInvoiceNumber).toList();
                throw new TaomishError("Failed to claim quantity.Check below error : \n\nInvoice ("+ String.join(",",invoiceNumber) +") is already generated for claim for Obligation : "+plannedObligationId);
            }
        } else {
            var quantityObj = actualizationQuantityRepo.findByPlannedObligationIdAndSplitSequenceNumberAndTenantIdOrderBySplitSequenceNumberAsc(plannedObligationId,splitNumber,tenantId);
            if(quantityObj != null) {
                throw new TaomishError("Failed to Actualize quantity.Check below error : \n\n Quantity is already actualized for obligation id : " + plannedObligationId +(splitNumber != 0?"("+splitNumber+")":""));
            }
        }
        PlannedObligationDTO plannedObligationDTO = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + QUERY+TENANT_ID_EQ + tenantId + PLANNED_OBLIGATION_ID + plannedObligationId, HttpMethod.GET, token, null, PlannedObligationDTO.class);
        if (Objects.requireNonNull(plannedObligationDTO).getPriceType() != null && (plannedObligationDTO.getPriceType().equalsIgnoreCase(PTBF) || plannedObligationDTO.getPriceType().equalsIgnoreCase(DIFFERENTIAL))) {
            PriceAllocationCheckDTO priceAllocationCheckDTO = TransactionIdUtil.query(baseUrl + ADVANCE_ALLOCATION_ROOT + GET_PRICE_ALLOCATED_CHECK_BY_PLANNED_OBLIGATION_ID + QUE_OBLIGATION_ID + plannedObligationId + "&tenantId=" + tenantId, HttpMethod.GET, token, null, PriceAllocationCheckDTO.class);
            if(plannedObligationDTO.getProvisionalPricing() || (plannedObligationDTO.getProvisionalPriceType() != null && !plannedObligationDTO.getProvisionalPriceType().isEmpty() && plannedObligationDTO.getProvisionalPrice() != null && plannedObligationDTO.getProvisionalPrice() > 0)) {
                return true;
            } else {
                return Objects.requireNonNull(priceAllocationCheckDTO).isFullyPriced();
            }
        }
        return true;
    }

    private boolean canActaulizeV2(String plannedObligationId, String tenantId, String token) {
        PlannedObligationDTO plannedObligationDTO = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + QUERY+TENANT_ID_EQ + tenantId + PLANNED_OBLIGATION_ID + plannedObligationId, HttpMethod.GET, token, null, PlannedObligationDTO.class);
        if (Objects.requireNonNull(plannedObligationDTO).getPriceType() != null && (plannedObligationDTO.getPriceType().equalsIgnoreCase(PTBF) || plannedObligationDTO.getPriceType().equalsIgnoreCase(DIFFERENTIAL))) {
            List<PricingPlanDTO> pricingPlanDTO = null;
            try {
                pricingPlanDTO = TransactionIdUtil.queryList(baseUrl + MANUAL_PRICING_ROOT + GET_TRADE_BY_TRADEID + QUERY+TENANT_ID_EQ + tenantId + "&tradeId=" + plannedObligationId, HttpMethod.GET, token, null, PricingPlanDTO.class);
            } catch (Exception e) {
                logger.error("Failed to get Pricing DTO",e);
            }
            if(Boolean.TRUE.equals(plannedObligationDTO.getProvisionalPricing())) {
                return pricingPlanDTO == null || pricingPlanDTO.isEmpty() || pricingPlanDTO.get(0).getPriceStatus().equalsIgnoreCase(FULLY_PRICED);
            } else {
                return pricingPlanDTO != null && !pricingPlanDTO.isEmpty() && pricingPlanDTO.get(0).getPriceStatus().equalsIgnoreCase(FULLY_PRICED);
            }
        }
        return true;
    }

    private String getWashoutCashflowType(String tenantId, PlannedObligationDTO plannedObligation, String token) {
        String cashflowType = "";
        List<SearchCriteria> searchCriteriaList = new ArrayList<>();
        searchCriteriaList.add(new SearchCriteria(TEN_ANT_ID, EQ, tenantId));
        searchCriteriaList.add(new SearchCriteria(PLANNED_OBLGN_ID, EQ, plannedObligation.getPlannedObligationId()));
        searchCriteriaList.add(new SearchCriteria(CASH_FLOW_STATUS, EQ, ACTIVE));
        searchCriteriaList.add(new SearchCriteria("type", "in", List.of(Trade)));
        searchCriteriaList.add(new SearchCriteria(STAGE,EQ, PLANNED));
        CashflowDataDTO[] cashflowDataDTOSForWashout = TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaList, CashflowDataDTO[].class);
        if (cashflowDataDTOSForWashout != null && cashflowDataDTOSForWashout.length > 0) {
            CashflowDataDTO cashflowDataDTO = cashflowDataDTOSForWashout[0];
            if (ObjectUtils.isEmpty(cashflowDataDTO.getFxRate())) {
                cashflowDataDTO.setFxRate(1);
            }
            cashflowType = Trade;
        }
        return cashflowType;
    }


    @SneakyThrows
    public ReturnStatus actualizeQuantityForTransportActulization(ActualizeObj actualizeObj, boolean isClaimed, String token, String tenantId) {
        PlannedObligationDTO plannedObligationDTO = null;
        PlannedObligationDTO plannedObligationTempDTO = null;
        List<TransportActualizationQuantityRows> quantityRows = actualizeObj.getQuantityRows();
        for(TransportActualizationQuantityRows transportActualizationQuantityRows : quantityRows){
            plannedObligationTempDTO = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + QUERY+TENANT_ID_EQ + tenantId + PLANNED_OBLIGATION_ID + transportActualizationQuantityRows.getPlannedObligationId(), HttpMethod.GET, token, null, PlannedObligationDTO.class);
            if(plannedObligationTempDTO.getTradeSettlementReference()!=null){
                actualizeQuantityForTransportActulizationForInterCompanyTrades(tenantId,transportActualizationQuantityRows,plannedObligationTempDTO,token);
            }
        }
        List<String> failedPlannedObligationsIds = new ArrayList<>();
        for (TransportActualizationQuantityRows transportActualizationQuantityRows : quantityRows) {
            if (!canActaulize(transportActualizationQuantityRows.getPlannedObligationId(),transportActualizationQuantityRows.getSplitSequenceNumber(), tenantId, token,isClaimed) && (!failedPlannedObligationsIds.contains(transportActualizationQuantityRows.getPlannedObligationId()))) {
                    failedPlannedObligationsIds.add(transportActualizationQuantityRows.getPlannedObligationId());
            }
        }
        if (!failedPlannedObligationsIds.isEmpty()) {
            throw new TaomishError( "Failed to actualize quantity for Planned Obligation Ids : " + String.join(",", failedPlannedObligationsIds) + ".\n( Price is not allocated or Partially Allocated )");
        }
        PriceAllocationDTO priceAllocationDTO = null;
        for (TransportActualizationQuantityRows transportActualizationQuantityRows : quantityRows) {
            priceAllocationDTO = null;
            if (transportActualizationQuantityRows.getStatus().equalsIgnoreCase(PENDING) || (isClaimed && transportActualizationQuantityRows.getStatus().equalsIgnoreCase(ACTUALIZED))) {
                plannedObligationDTO = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + QUERY+TENANT_ID_EQ + tenantId + PLANNED_OBLIGATION_ID + transportActualizationQuantityRows.getPlannedObligationId(), HttpMethod.GET, token, null, PlannedObligationDTO.class);
                if (Objects.requireNonNull(plannedObligationDTO).getPriceType() != null && (plannedObligationDTO.getPriceType().equalsIgnoreCase(PTBF) || plannedObligationDTO.getPriceType().equalsIgnoreCase(DIFFERENTIAL))) {
                    priceAllocationDTO = TransactionIdUtil.query(baseUrl + MANUAL_PRICING_ROOT + GET_PRICE_ALLOCATION_LIST_BY_OBLIGATION_ID + QUERY+TENANT_ID_EQ + tenantId + OBLIGATION_ID + plannedObligationDTO.getPlannedObligationId(), HttpMethod.GET, token, null, PriceAllocationDTO.class);
                }
                createActualizeQuantityForTransportActualization(transportActualizationQuantityRows, plannedObligationDTO, priceAllocationDTO, isClaimed, token, tenantId, quantityRows);
            }
        }

        return ReturnStatus.successInstance("Actualization Done Successfully");
    }

    public void addAutoCosts(ActualizeObj actualizeObj, String token, String tenantId) throws Exception {
        final String AUTO_ACTUALIZE_FLAG = "autoActualizeOnLoad";
        List<TransportActualizationQuantityRows> quantityRows = actualizeObj.getQuantityRows();
        ArrayList<String> obligationIdList = new ArrayList<>();

        for (TransportActualizationQuantityRows transportActualizationQuantityRows : quantityRows) {
            ArrayList<ActualizeCostObj> actualizeCostObjArrayList = new ArrayList<>();
            String obligationId = transportActualizationQuantityRows.getPlannedObligationId();
            if (!obligationIdList.contains(obligationId)) {
                obligationIdList.add(obligationId);
                List<ActualizeCostObj> costListForObligation = actualizationService.getCost(obligationId, token, tenantId);
                for (ActualizeCostObj obj : costListForObligation) {
                    if (obj.getCostMatrixWorkflow() != null
                            && obj.getCostMatrixWorkflow().get(AUTO_ACTUALIZE_FLAG) != null
                            && obj.getCostMatrixWorkflow().get(AUTO_ACTUALIZE_FLAG).equals("YES")) {
                        actualizeCostObjArrayList.add(obj);
                    }
                }
                String plannedObligationUrl = baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + QUE_TENANT_ID + tenantId + "&plannedObligationId=" + obligationId;
                PlannedObligationDTO plannedObligationDTO = TransactionIdUtil.query(plannedObligationUrl, HttpMethod.GET, token, null, PlannedObligationDTO.class);
                actualizeObj.setCosts(actualizeCostObjArrayList);
                actualizeObj.setPlannedObligation(plannedObligationDTO);
                actualizeCostService.actualizeCost(actualizeObj, false, token, tenantId);
            }
        }


    }

    private void actualizeQuantityForTransportActulizationForInterCompanyTrades(String tenantId,TransportActualizationQuantityRows transportActualizationQuantityRows,PlannedObligationDTO plannedObligationTempDTO,String token) throws Exception {
       InterCompanyTradeDTO interCompanyTradeDTO;
       TransportActualizationQuantityRows quantityRowsBuy = null;
        TransportActualizationQuantityRows quantityRowSell= null;
       interCompanyTradeDTO = TransactionIdUtil.query(baseUrl + "/api/interCompanyTrades/v1/getInterCompanyTradeByUuid" + QUERY+TENANT_ID_EQ + tenantId + "&interCompanyUuid=" + plannedObligationTempDTO.getTradeSettlementReference(), HttpMethod.GET, token, null, InterCompanyTradeDTO.class);
        PlannedObligationDTO plannedObligationBuy = TransactionIdUtil.query(baseUrl +PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + QUERY+TENANT_ID_EQ + tenantId + PLANNED_OBLIGATION_ID+ interCompanyTradeDTO.getSettlementData().getInterCompanyTradeDetails().get(0).getObligationId(), HttpMethod.GET, token, null, PlannedObligationDTO.class);
        PlannedObligationDTO plannedObligationSell = TransactionIdUtil.query(baseUrl +PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + QUERY+TENANT_ID_EQ + tenantId + PLANNED_OBLIGATION_ID + interCompanyTradeDTO.getSettlementData().getInterCompanyTradeDetails().get(1).getObligationId(), HttpMethod.GET, token, null, PlannedObligationDTO.class);
        PlanningDTO planningBuyDTO = new PlanningDTO();
        PlanningDTO planningSellDTO = new PlanningDTO();
        BeanUtils.copyProperties(plannedObligationBuy,planningBuyDTO);
        BeanUtils.copyProperties(plannedObligationSell,planningSellDTO);
        planningBuyDTO.setMatchQuantity(plannedObligationBuy.getPlannedQuantity());
        planningSellDTO.setMatchQuantity(plannedObligationSell.getPlannedQuantity());
        TradePlanningDetails planDetails = new TradePlanningDetails();
        planDetails.setTradeBuyObligations(List.of(planningBuyDTO));
        planDetails.setTradeSellObligations(List.of(planningSellDTO));
        planDetails.setMatchType(List.of(BACK2BACK));
        planDetails.setMatchQuantity(transportActualizationQuantityRows.getPlannedQuantity());
        if(plannedObligationBuy.getPlanId() == null){
            PhysicalTradePlanningDTO physicalTradePlan = TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_PLANNING_ROOT + SAVE_TRADE_PLANNING+"?vesselPlanning=false" +AND+TENANT_ID_EQ+tenantId,HttpMethod.POST, token,  planDetails, PhysicalTradePlanningDTO.class);
            SeaFreightDetailsDTO seaFreightDetailsDTO = TransactionIdUtil.query(baseUrl + TRANSPORT_ALLOCATION_V2_ROOT + GET_SEA_FRIGHT_DETAILS_BY_PLAN_ID + QUERY+TENANT_ID_EQ + tenantId+ "&planId=" +plannedObligationTempDTO.getPlanId(), HttpMethod.GET, token,null, SeaFreightDetailsDTO.class);
            seaFreightDetailsDTO.setUuid(null);
            seaFreightDetailsDTO.setPlanId(physicalTradePlan.getPlanningId());
            seaFreightDetailsDTO.setPlannedObligationIds(new ArrayList<>());
            seaFreightDetailsDTO.getPlannedObligationIds().addAll(List.of(plannedObligationSell.getPlannedObligationId(),plannedObligationBuy.getPlannedObligationId()));
            TransactionIdUtil.query(baseUrl+TRANSPORT_ALLOCATION_V2_ROOT+SAVE_SEA_FREIGHT_DETAILS+QUERY+TENANT_ID_EQ+tenantId,HttpMethod.POST,token,seaFreightDetailsDTO,Object.class);
        }

        ActualizeObj actualizeObj = new ActualizeObj();

        quantityRowsBuy = new TransportActualizationQuantityRows();
        BeanUtils.copyProperties(transportActualizationQuantityRows,quantityRowsBuy);
        quantityRowsBuy.setPlanId(plannedObligationBuy.getPlanId());
        quantityRowsBuy.setTradeId(plannedObligationBuy.getTradeId());
        quantityRowsBuy.setPlannedObligationId(plannedObligationBuy.getPlannedObligationId());
        quantityRowsBuy.setPlannedQuantity(transportActualizationQuantityRows.getPlannedQuantity());
        quantityRowsBuy.setActualQuantity(transportActualizationQuantityRows.getActualQuantity());
        quantityRowsBuy.setStatus(transportActualizationQuantityRows.getStatus());
        quantityRowsBuy.setPackageType(transportActualizationQuantityRows.getPackageType());
        quantityRowsBuy.setUom(transportActualizationQuantityRows.getUom());
        quantityRowsBuy.setActualInternalPackage(transportActualizationQuantityRows.getActualInternalPackage());
        quantityRowsBuy.setActualExternalPackage(transportActualizationQuantityRows.getActualExternalPackage());
        quantityRowsBuy.setPlannedInternalPackage(transportActualizationQuantityRows.getPlannedInternalPackage());
        quantityRowsBuy.setPlannedExternalPackage(transportActualizationQuantityRows.getPlannedExternalPackage());
        quantityRowsBuy.setSplitSequenceNumber(transportActualizationQuantityRows.getSplitSequenceNumber());
        quantityRowsBuy.setInternalPackage(transportActualizationQuantityRows.getInternalPackage());
        quantityRowsBuy.setExternalPackage(transportActualizationQuantityRows.getExternalPackage());

        quantityRowSell = new TransportActualizationQuantityRows();
        BeanUtils.copyProperties(transportActualizationQuantityRows,quantityRowSell);
        quantityRowSell.setPlanId(plannedObligationSell.getPlanId());
        quantityRowSell.setTradeId(plannedObligationSell.getTradeId());
        quantityRowSell.setPlannedObligationId(plannedObligationSell.getPlannedObligationId());
        quantityRowSell.setPlannedQuantity(transportActualizationQuantityRows.getPlannedQuantity());
        quantityRowSell.setActualQuantity(transportActualizationQuantityRows.getActualQuantity());
        quantityRowSell.setStatus(transportActualizationQuantityRows.getStatus());
        quantityRowSell.setPackageType(transportActualizationQuantityRows.getPackageType());
        quantityRowSell.setUom(transportActualizationQuantityRows.getUom());
        quantityRowSell.setActualInternalPackage(transportActualizationQuantityRows.getActualInternalPackage());
        quantityRowSell.setActualExternalPackage(transportActualizationQuantityRows.getActualExternalPackage());
        quantityRowSell.setPlannedInternalPackage(transportActualizationQuantityRows.getPlannedInternalPackage());
        quantityRowSell.setPlannedExternalPackage(transportActualizationQuantityRows.getPlannedExternalPackage());
        quantityRowSell.setSplitSequenceNumber(transportActualizationQuantityRows.getSplitSequenceNumber());
        quantityRowSell.setInternalPackage(transportActualizationQuantityRows.getInternalPackage());
        quantityRowSell.setExternalPackage(transportActualizationQuantityRows.getExternalPackage());


        actualizeObj.setQuantityRows(List.of(quantityRowsBuy,quantityRowSell));
        actualizeObj.setMatchType(BACK2BACK);
        this.actualizeQuantityForTransportActulization(actualizeObj,false,token,tenantId);

    }

    public ResponseEntity getActualizedQuantityObligationByPlannedObligationId(String tenantId, String plannedObligationId) {
        logger.info("Entered to fetch Actualized Quantity Obligation by planned obligation id");
        List<ActualizedQuantityObligations> actualizedQuantityObligationsList = actualizationQuantityRepo.findAllByPlannedObligationIdAndTenantIdOrderBySplitSequenceNumberAsc(plannedObligationId, tenantId);
        logger.info("Exiting to fetch Actualized Quantity Obligation by planned obligation id");
        var finalList = actualizedQuantityObligationsList.stream().map(item -> {
            var dto = TransactionIdUtil.convertObject(item,ActualizedQuantityObligationsDTO.class);
            dto.setActualizedQuantityLoad(item.getActualizedQuantity(TradeTransactionType.BUY));
            dto.setActualizedQuantityUnload(item.getActualizedQuantity(TradeTransactionType.SELL));
            return dto;
        }).toList();
        return new ResponseEntity(finalList, HttpStatus.OK);
    }


    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createActualizeQuantityForTransportActualization(TransportActualizationQuantityRows transportActualizationQuantityRows,
                                                                  PlannedObligationDTO plannedObligation, PriceAllocationDTO priceAllocationDTOS,
                                                                  Boolean isClaimed, String token, String tenantId, List<TransportActualizationQuantityRows> quantityRows) {
        String actualizationQuantityId = "";
        boolean isEdit = false;
        ActualizedQuantityObligations actualizedQuantityObligations = null;
        ActualizedQuantityObligations actualizedQuantityObligationsOld = actualizationQuantityRepo.findByPlannedObligationIdAndSplitSequenceNumberAndTenantIdOrderBySplitSequenceNumberAsc(plannedObligation.getPlannedObligationId(), transportActualizationQuantityRows.getSplitSequenceNumber(), tenantId);
        if (Boolean.TRUE.equals(isClaimed)) {
            if (actualizedQuantityObligationsOld != null) {
                actualizedQuantityObligationsOld.setClaimedQuantity(transportActualizationQuantityRows.getActualQuantity());
                actualizedQuantityObligationsOld.setDischargeDate(transportActualizationQuantityRows.getDischargeDate());
                logger.info("Saving Quantity Actualization for ObligationId: {} " ,actualizedQuantityObligationsOld.getPlannedObligationId());
                actualizedQuantityObligations = actualizationQuantityRepo.save(actualizedQuantityObligationsOld);
            }
        }
        else {
            if (actualizedQuantityObligationsOld != null) {
                return;
            }
            actualizationQuantityId = transactionIDGenerator.generateId( ACTUALIZATION_QUANTITY_ID, plannedObligation, tenantId, token,false,"",false);
            if (actualizationQuantityId == null) {
                throw new TaomishError("Actualization Quantity ID is not generated ");
            }
            actualizedQuantityObligations = new ActualizedQuantityObligations();
            actualizedQuantityObligations.setActualizedQuantityId(actualizationQuantityId);
//          changing actualization_id  to actualizationQuantityId as actualization Id will create duplicate records
            actualizedQuantityObligations.setActualizationId(actualizationQuantityId);
            actualizedQuantityObligations.setSplitSequenceNumber(transportActualizationQuantityRows.getSplitSequenceNumber());
            actualizedQuantityObligations.setPlannedObligationId(plannedObligation.getPlannedObligationId());
            actualizedQuantityObligations.setPlannedObligationType(plannedObligation.getTradeTransactionType());
            actualizedQuantityObligations.setTradeId(plannedObligation.getTradeId());
            if (transportActualizationQuantityRows.getSplitSequenceNumber() == 0) {
                actualizedQuantityObligations.setPlannedQuantity(plannedObligation.getPlannedQuantity());
            } else {
                actualizedQuantityObligations.setPlannedQuantity(transportActualizationQuantityRows.getActualQuantity());
            }
            actualizedQuantityObligations.setLoadQuantity(transportActualizationQuantityRows.getActualQuantity());
            actualizedQuantityObligations.setUnloadQuantity(transportActualizationQuantityRows.getActualQuantity());
            actualizedQuantityObligations.setBalanceAllocateQuantity(transportActualizationQuantityRows.getActualQuantity());
            if(transportActualizationQuantityRows.getPackageType() != null) {
                if (transportActualizationQuantityRows.getPackageType().equals(PACKAGE_TYPE_CONTAINER)) {
                    actualizedQuantityObligations.setExternalPackage(transportActualizationQuantityRows.getExternalPackage());
                    actualizedQuantityObligations.setExternalPackageUnit(transportActualizationQuantityRows.getActualExternalPackage());
                    actualizedQuantityObligations.setPlannedExternalPackageUnit(plannedObligation.getNoOfUnits());
                } else if (transportActualizationQuantityRows.getPackageType().equals(PACKAGE_TYPE_UNIT)) {
                    actualizedQuantityObligations.setInternalPackage(transportActualizationQuantityRows.getInternalPackage());
                    actualizedQuantityObligations.setInternalPackageUnit(transportActualizationQuantityRows.getActualInternalPackage());
                    actualizedQuantityObligations.setPlannedInternalPackageUnit(plannedObligation.getNoOfUnits());
                }
            }

            actualizedQuantityObligations.setBrand(plannedObligation.getBrand());
            actualizedQuantityObligations.setGrade(plannedObligation.getGrade());
            actualizedQuantityObligations.setOrigin(plannedObligation.getCropOrigin());
            actualizedQuantityObligations.setCommodity(plannedObligation.getCommodity());
            actualizedQuantityObligations.setQuantityUom(plannedObligation.getQuantityUOM());
            actualizedQuantityObligations.setTenantId(tenantId);

            logger.info("Saving Quantity Actualization for ObligationId: {}" , actualizedQuantityObligations.getPlannedObligationId());
            actualizationQuantityRepo.save(actualizedQuantityObligations);

            // Create Empty BL Row While Actualizing Quantity
            var blrow = billOfLadingRepo.findByPlannedObligationIdAndSplitSequenceNumberAndTenantId(plannedObligation.getPlannedObligationId(),actualizedQuantityObligations.getSplitSequenceNumber(),tenantId);
            if(blrow == null ){
                BillOfLanding billOfLandingRow = new BillOfLanding();
                billOfLandingRow.setPlannedObligationId(actualizedQuantityObligations.getPlannedObligationId());
                billOfLandingRow.setActualQuantity(actualizedQuantityObligations.getLoadQuantity());
                billOfLandingRow.setUom(plannedObligation.getQuantityUOM());
                billOfLandingRow.setActualizationId(actualizedQuantityObligations.getActualizationId());
                billOfLandingRow.setSurrendered(false);
                billOfLandingRow.setTenantId(tenantId);
                billOfLandingRow.setSplitSequenceNumber(actualizedQuantityObligations.getSplitSequenceNumber());
                //billOfLadingRepo.save(billOfLandingRow);
            }


            // Create and Publish Rabbit message to update Actualized Quantity data to EOD service
            logger.info("Publishing Rabbit message to update Actualization data in EOD service for obligationID: {}" , actualizedQuantityObligations.getPlannedObligationId());
            ActualizedQuantityObligationsDTO actualizedQuantityObligationsDTO = new ActualizedQuantityObligationsDTO();
            actualizedQuantityObligationsDTO.setPlannedObligationId(actualizedQuantityObligations.getPlannedObligationId());
            actualizedQuantityObligationsDTO.setLoadQuantity(actualizedQuantityObligations.getLoadQuantity());
            actualizedQuantityObligationsDTO.setBrand(actualizedQuantityObligations.getBrand());
            actualizedQuantityObligationsDTO.setGrade(actualizedQuantityObligations.getGrade());
            actualizedQuantityObligationsDTO.setOrigin(actualizedQuantityObligations.getOrigin());
            actualizedQuantityObligationsDTO.setUnloadQuantity(actualizedQuantityObligations.getUnloadQuantity());
            actualizedQuantityObligationsDTO.setTenantId(tenantId);
            actualizedQuantityObligationsDTO.setBalanceAllocateQuantity(actualizedQuantityObligations.getLoadQuantity());
            platformQueueService.sendObject(EXCHANGE_NAME, QUANTITY_ACTUALIZATION, actualizedQuantityObligationsDTO);

            actualizeDefaultQualtityorGenerateCashflow(plannedObligation, actualizationQuantityId, actualizedQuantityObligations.getLoadQuantity(), tenantId, token);
        }

        PlannedObligationDTO plannedObligationDTO;
        try {
            plannedObligationDTO = updateActualizedStatus(plannedObligation.getPlannedObligationId(), token, tenantId);
            if(Boolean.TRUE.equals(isClaimed)) {
                TransactionIdUtil.setPlannedObligationStatesToTrue(baseUrl,plannedObligation.getPlannedObligationId(),tenantId,token,PlannedObligationState.DISCHARGED);
            }
        } catch (Exception e) {
            throw new TaomishError("Updating planned obligation failed ",e);
        }
        /**
         * Actualize Quality spec if not actualized with default setting and if actualized then check premiumdiscount > 0 if yes then generate cash-flows for that
         */
        if (!plannedObligation.isExternalRecord()) {
            try {
                assert actualizedQuantityObligations != null;
                createQuantityCashFlow(actualizedQuantityObligations.getLoadQuantity(), actualizedQuantityObligations.getUnloadQuantity(),
                        plannedObligation, isEdit, priceAllocationDTOS, isClaimed, token, tenantId, actualizedQuantityObligations.getSplitSequenceNumber(),
                        actualizedQuantityObligations.getClaimedQuantity(),transportActualizationQuantityRows,quantityRows);
            } catch (Exception e) {
                logger.error("Cash-flow creation failed.",e);
                if (Boolean.FALSE.equals(isClaimed)) {
                    plannedObligationDTO.getObligationState().put(PlannedObligationState.ACTUALIZED, false);
                    TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + UPDATE_TRADE_PLANOBLIGATION + QUERY+TENANT_ID_EQ + tenantId, HttpMethod.POST, token, plannedObligationDTO, PlannedObligationDTO.class);
                } else {
                    actualizedQuantityObligations.setClaimedQuantity(0.0);
                    actualizationQuantityRepo.save(actualizedQuantityObligations);
                }
                throw new TaomishError("Cash-flow creation failed.",e);
            }
        }

        logger.info("Quantity actualization is done for planned Obligation id : {}" , plannedObligation.getPlannedObligationId());
    }

    private void actualizeDefaultQualtityorGenerateCashflow(PlannedObligationDTO plannedObligation, String actualizationId, double quantity, String tenantId, String token) throws TaomishError {
        List<ActualizedQuality> actualizedQualityList = actualizationQualityRepo.findAllByPlannedObligationIdAndTenantId(plannedObligation.getPlannedObligationId(), tenantId);
        List<String> qualityIds = actualizedQualityList.stream().map(ActualizedQuality::getName).toList();
        List<QualitySpecDTO> tradeQualitySpecs = TransactionIdUtil.queryList(baseUrl + QUALITY_SPECIFICATION_ROOT +
                        GET_QUALITY_SPEC_BY_TRADE_ID + QUERY+TENANT_ID_EQ + tenantId
                        + "&tradeId=" + plannedObligation.getTradeId(),
                HttpMethod.GET, token, null, QualitySpecDTO.class);
        String actualizationQualityId = "";
        ActualizedQuality actualizedQuality = null;
        for (QualitySpecDTO qualitySpec : tradeQualitySpecs) {
            if (!qualityIds.contains(qualitySpec.getName())) {
                actualizationQualityId = transactionIDGenerator.generateId( "actualizationQualityId", plannedObligation, tenantId, token,false,"",false);
                if (actualizationQualityId == null) {
                    throw new TaomishError("Actualization Quality ID is not generated");
                }
                actualizedQuality = new ActualizedQuality();
                BeanUtils.copyProperties(qualitySpec, actualizedQuality);
                actualizedQuality.setActualizedQualityId(actualizationQualityId);
                actualizedQuality.setActualizationId(actualizationId);
                actualizedQuality.setPremiumDiscount(0);
                actualizedQuality.setPlannedObligationId(plannedObligation.getPlannedObligationId());
                actualizedQuality.setTradeId(plannedObligation.getTradeId());
                actualizedQuality.setEstimatedQualitySpecId(qualitySpec.getId());
                actualizedQuality.setBasis((qualitySpec.getBasis() != null && !qualitySpec.getBasis().isEmpty())?Double.parseDouble(qualitySpec.getBasis()):0);
                actualizedQuality.setTenantId(tenantId);
                actualizationQualityRepo.save(actualizedQuality);
            }
        }
    }

    private CashflowDataDTO generatePreimumDiscountCashflow(ActualizedQuality actualizedQuality, PlannedObligationDTO plannedObligation, double quantity, String tenantId, String token) throws Exception {
        PhysicalTradeDTO trade = TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_ROOT + GET_PHYSICAL_TRADE_BY_TRADE_ID + TRADE_ID + plannedObligation.getTradeId() + AND+TENANT_ID_EQ + tenantId, HttpMethod.GET, token, null, PhysicalTradeDTO.class);
        UpdateCashflowDTO updateCashflowDTO = new UpdateCashflowDTO();
        updateCashflowDTO.setTradeId(trade.getTradeId());
        updateCashflowDTO.setCounterparty(trade.getCounterparty());
        updateCashflowDTO.setCommodity(trade.getCommodity());
        updateCashflowDTO.setTradeTransactionType(trade.getTradeTransactionType());
        updateCashflowDTO.setTradeSettlementCurrency(trade.getTradeSettlementCurrency());
        updateCashflowDTO.setTotalContractQuantity(trade.getTotalTradeQty());
        updateCashflowDTO.setObligationId(plannedObligation.getTradeObligationId());
        updateCashflowDTO.setPlanId(plannedObligation.getPlanId());
        updateCashflowDTO.setPlannedObligationId(plannedObligation.getPlannedObligationId());
        updateCashflowDTO.setDeliveryDate(plannedObligation.getDeliveryEndDate());
        updateCashflowDTO.setProfitcenter(plannedObligation.getProfitcenter());
        updateCashflowDTO.setCompany(plannedObligation.getCompany());
        updateCashflowDTO.setTradePriceUom(plannedObligation.getTradePriceUom());
        updateCashflowDTO.setTradeDateTime(plannedObligation.getTradeDateTime());
        updateCashflowDTO.setQuantityUom(plannedObligation.getQuantityUOM());
        PriceAllocationCheckDTO priceAllocationCheckDTO = TransactionIdUtil.query(baseUrl + ADVANCE_ALLOCATION_ROOT + GET_PRICE_ALLOCATED_CHECK_BY_PLANNED_OBLIGATION_ID + QUE_OBLIGATION_ID + plannedObligation.getPlannedObligationId() + AND+TENANT_ID_EQ + tenantId, HttpMethod.GET, token, null, PriceAllocationCheckDTO.class);
        if (Boolean.TRUE.equals(trade.getIsProvisionalPricing()) && !trade.getPriceType().equalsIgnoreCase(FIXEDPRICED) && Objects.nonNull(priceAllocationCheckDTO)) {
            updateCashflowDTO.setTradePriceCurrency(trade.getProvisionalPriceCurrency());
            updateCashflowDTO.setPriceType(trade.getPriceType());
            updateCashflowDTO.setFxRate(trade.getFxrate());
            if (!priceAllocationCheckDTO.isFullyPriced()) {
                updateCashflowDTO.setStage(ACCRUED_PROVISIONAL);
                updateCashflowDTO.setPrice(trade.getProvisionalPrice());
            } else {
                updateCashflowDTO.setPrice(priceAllocationCheckDTO.getPrice());
            }
        } else {
            updateCashflowDTO.setTradePriceCurrency(trade.getTradePriceCurrency());
            updateCashflowDTO.setPrice(trade.getTradePrice());
            updateCashflowDTO.setPriceType(trade.getPriceType());
            updateCashflowDTO.setFxRate(trade.getFxrate());
            updateCashflowDTO.setStage(ACCRUED);
            if (Objects.nonNull(priceAllocationCheckDTO) && priceAllocationCheckDTO.isFullyPriced()) {
                updateCashflowDTO.setPrice(priceAllocationCheckDTO.getPrice());
            }
        }
        updateCashflowDTO.setPremiumDiscount(actualizedQuality.getPremiumDiscount());
        if (actualizedQuality.getPremiumDiscount() > 0) {
            updateCashflowDTO.setType(CASHFLOW_TYPE_PREMIUM);
        } else {
            updateCashflowDTO.setType(CASHFLOW_TYPE_DISCOUNT);
        }
        updateCashflowDTO.setObligationQuantity(quantity);
        updateCashflowDTO.setPrice(actualizedQuality.getPremiumDiscount());
        updateCashflowDTO.setActualizationObjectId(actualizedQuality.getActualizedQualityId());
        updateCashflowDTO.setTradeSettlementCurrency(trade.getTradeSettlementCurrency());
        updateCashflowDTO.setDescription(updateCashflowDTO.getType() + " for shipment " + plannedObligation.getPlannedObligationId() + " of " + trade.getTradeId());
        CashflowDataDTO cashflowDataDTO = null;
        try {
            cashflowDataDTO = actualizationCashflowService.runCreatCashflows(updateCashflowDTO, token, tenantId);
        } catch (Exception e) {
            logger.error("Cash-flow creation failed .",e);
            throw new TaomishError("Cash-flow creation failed  .",e);
        }
        return cashflowDataDTO;
    }

    public ResponseEntity actualizeQuantityForBLSplit(List<PlannedObligationDTO> splitRows, String token, String tenantId) throws Exception {
        List<String> failedPlannedObligationsIds = new ArrayList<>();
        for (PlannedObligationDTO plannedObligationDTO : splitRows) {
            if (!canActaulize(plannedObligationDTO.getPlannedObligationId(),plannedObligationDTO.getSplitSequenceNumber(), tenantId, token, false) && !failedPlannedObligationsIds.contains(plannedObligationDTO.getPlannedObligationId())) {
                failedPlannedObligationsIds.add(plannedObligationDTO.getPlannedObligationId());
            }
        }

        if (!failedPlannedObligationsIds.isEmpty()) {
            return new ResponseEntity(ReturnStatus.errorInstance("Failed to actualize quantity for Planned Obligation Ids : " + String.join(",", failedPlannedObligationsIds) + ".\n( Price is not allocated )"), HttpStatus.BAD_REQUEST);
        }
        for (PlannedObligationDTO plannedObligationDTO : splitRows) {
            if (plannedObligationDTO.getPriceType() != null && (plannedObligationDTO.getPriceType().equalsIgnoreCase(PTBF) || plannedObligationDTO.getPriceType().equalsIgnoreCase(DIFFERENTIAL))) {
                PriceAllocationDTO priceAllocationDTO = TransactionIdUtil.query(baseUrl + MANUAL_PRICING_ROOT + GET_PRICE_ALLOCATION_LIST_BY_OBLIGATION_ID + QUERY+TENANT_ID_EQ + tenantId + OBLIGATION_ID + plannedObligationDTO.getPlannedObligationId(), HttpMethod.GET, token, null, PriceAllocationDTO.class);
                createActualizeQuantityForSplitTransportActualization(plannedObligationDTO, priceAllocationDTO, token, tenantId);
            } else {
                createActualizeQuantityForSplitTransportActualization(plannedObligationDTO, null, token, tenantId);
            }
        }
        return new ResponseEntity(ReturnStatus.successInstance("Actualization Done Successfully"), HttpStatus.OK);
    }

    private void createActualizeQuantityForSplitTransportActualization(PlannedObligationDTO plannedObligation, PriceAllocationDTO priceAllocationDTOS, String token, String tenantId) throws Exception {
        String actualizationQuantityId = "";
        boolean isEdit = false;
        ActualizedQuantityObligations actualizedQuantityObligations = null;
        ActualizedQuantityObligations actualizedQuantityObligationsOld = actualizationQuantityRepo.findByPlannedObligationIdAndSplitSequenceNumberAndTenantIdOrderBySplitSequenceNumberAsc(plannedObligation.getPlannedObligationId(), plannedObligation.getSplitSequenceNumber(), tenantId);
        if (actualizedQuantityObligationsOld != null) {
            return;
        }
        actualizationQuantityId = transactionIDGenerator.generateId( ACTUALIZATION_QUANTITY_ID, plannedObligation, tenantId, token,false,"",false);
        if (actualizationQuantityId == null) {
            throw new TaomishError("Actualization Quantity ID is not generated");
        }
        actualizedQuantityObligations = new ActualizedQuantityObligations();
        actualizedQuantityObligations.setActualizedQuantityId(actualizationQuantityId);
//          changing actualization_id  to actualizationQuantityId as actualization Id will create duplicate records
        actualizedQuantityObligations.setActualizationId(actualizationQuantityId);
        actualizedQuantityObligations.setPlannedObligationId(plannedObligation.getPlannedObligationId());
        actualizedQuantityObligations.setPlannedObligationType(plannedObligation.getTradeTransactionType());
        actualizedQuantityObligations.setTradeId(plannedObligation.getTradeId());
        actualizedQuantityObligations.setSplitSequenceNumber(plannedObligation.getSplitSequenceNumber());
        actualizedQuantityObligations.setPlannedQuantity(plannedObligation.getPlannedQuantity());
        actualizedQuantityObligations.setLoadQuantity(plannedObligation.getPlannedQuantity());
        actualizedQuantityObligations.setUnloadQuantity(plannedObligation.getPlannedQuantity());
        if (plannedObligation.getPackageType().equals(PACKAGE_TYPE_CONTAINER)) {
            actualizedQuantityObligations.setExternalPackage(plannedObligation.getPackageName());
            // actualizedQuantityObligations.setExternalPackageUnit(plannedObligation.getPackageUnit());
            actualizedQuantityObligations.setPlannedExternalPackageUnit(plannedObligation.getNoOfUnits());
        } else if (plannedObligation.getPackageType().equals(PACKAGE_TYPE_UNIT)) {
            actualizedQuantityObligations.setInternalPackage(plannedObligation.getPackageName());
//            actualizedQuantityObligations.setInternalPackageUnit(transportActualizationQuantityRows.getActualInternalPackage());
            actualizedQuantityObligations.setPlannedInternalPackageUnit(plannedObligation.getNoOfUnits());
        }
        actualizedQuantityObligations.setBrand(plannedObligation.getBrand());
        actualizedQuantityObligations.setGrade(plannedObligation.getGrade());
        actualizedQuantityObligations.setOrigin(plannedObligation.getCropOrigin());
        actualizedQuantityObligations.setCommodity(plannedObligation.getCommodity());
        actualizedQuantityObligations.setQuantityUom(plannedObligation.getQuantityUOM());
        actualizedQuantityObligations.setTenantId(tenantId);
//        actualizedQuantityObligations.setPurpose(actualizeObj.getQuantity().getPurpose());
//        if (actualizeObj.getQuantity().getActualizationEventMapping() != null || !StringUtils.isEmpty(actualizeObj.getQuantity().getActualizationEventMapping())) {
//            actualizedQuantityObligations.setActualizationEventMapping(getEventMapping(actualizeObj.getQuantity().getActualizationEventMapping()));
//        }
        logger.info("Saving Quantity Actualization for ObligationId : {}", actualizedQuantityObligations.getPlannedObligationId());
        actualizationQuantityRepo.save(actualizedQuantityObligations);

//        publishMessage(EXCHANGE_NAME, PHYSICAL_TRADE_ACTUALIZATION_TOPIC, UPDATE, actualizedQuantityObligations);
        PlannedObligationDTO plannedObligationDTO;
        try {
            plannedObligationDTO = updateActualizedStatus(plannedObligation.getPlannedObligationId(), token, tenantId);
        } catch (Exception e) {
            throw new TaomishError("Updating planned obligation failed",e);
        }
        if (!plannedObligation.isExternalRecord()) {
            try {
                createQuantityCashFlow(actualizedQuantityObligations.getLoadQuantity(), actualizedQuantityObligations.getUnloadQuantity(), plannedObligation,
                        isEdit, priceAllocationDTOS, false, token, tenantId, actualizedQuantityObligations.getSplitSequenceNumber(),
                        actualizedQuantityObligations.getClaimedQuantity(), null, null);
            } catch (Exception e) {
                logger.error("Cash-flow creation failed !!",e);
                plannedObligationDTO.getObligationState().put(PlannedObligationState.ACTUALIZED, false);
                TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + UPDATE_TRADE_PLANOBLIGATION + QUERY+TENANT_ID_EQ + tenantId, HttpMethod.POST, token, plannedObligationDTO, PlannedObligationDTO.class);
                throw new TaomishError("Cash-flow creation failed",e);
            }
        }

        // Create and Publish Rabbit message to update Actualized Quantity data to EOD service
        logger.info("Publishing Rabbit message to update Actualization data in EOD service for obligationID  : {}" , actualizedQuantityObligations.getPlannedObligationId());
        ActualizedQuantityObligationsDTO actualizedQuantityObligationsDTO = new ActualizedQuantityObligationsDTO();
        actualizedQuantityObligationsDTO.setPlannedObligationId(actualizedQuantityObligations.getPlannedObligationId());
        actualizedQuantityObligationsDTO.setLoadQuantity(actualizedQuantityObligations.getLoadQuantity());
        actualizedQuantityObligationsDTO.setBrand(actualizedQuantityObligations.getBrand());
        actualizedQuantityObligationsDTO.setGrade(actualizedQuantityObligations.getGrade());
        actualizedQuantityObligationsDTO.setOrigin(actualizedQuantityObligations.getOrigin());
        actualizedQuantityObligationsDTO.setUnloadQuantity(actualizedQuantityObligations.getUnloadQuantity());
        actualizedQuantityObligationsDTO.setTenantId(tenantId);
        platformQueueService.sendObject(EXCHANGE_NAME, QUANTITY_ACTUALIZATION, actualizedQuantityObligationsDTO);

        logger.info("Quantity actualization is done for planned Obligation id  :{} " ,plannedObligation.getPlannedObligationId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ResponseEntity actualizeQuantityForInventory(ActualizeObj actualizeObj, Boolean isClaimed, String token, String tenantId) throws Exception {
        ResponseEntity responseEntity = null;
        if (actualizeObj.getPlannedObligation().getPriceType() != null && (actualizeObj.getPlannedObligation().getPriceType().equalsIgnoreCase(PTBF) || actualizeObj.getPlannedObligation().getPriceType().equalsIgnoreCase(DIFFERENTIAL))) {
            PriceAllocationDTO priceAllocationDTO = TransactionIdUtil.query(baseUrl + MANUAL_PRICING_ROOT + GET_PRICE_ALLOCATION_LIST_BY_OBLIGATION_ID + QUERY+TENANT_ID_EQ + tenantId + OBLIGATION_ID + actualizeObj.getPlannedObligation().getPlannedObligationId(), HttpMethod.GET, token, null, PriceAllocationDTO.class);

            PhysicalTradeDTO trade = TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_ROOT + GET_PHYSICAL_TRADE_BY_TRADE_ID +
                    TRADE_ID + actualizeObj.getPlannedObligation().getTradeId() + AND+TENANT_ID_EQ + tenantId, HttpMethod.GET, token, null, PhysicalTradeDTO.class);

            if (!Objects.requireNonNull(priceAllocationDTO).getPriceAllocationLists().isEmpty() || (Objects.requireNonNull(trade).getIsProvisionalPricing() && !trade.getPriceType().equals(FIXEDPRICED))) {
            createActualizeQuantityForInventory(actualizeObj, priceAllocationDTO, isClaimed, token, tenantId);
                responseEntity = new ResponseEntity(new ReturnStatus("ActualizationQuantityDone"), HttpStatus.OK);
            } else {
                responseEntity = new ResponseEntity(new ReturnStatus("You cannot actualize.( Price is not allocated )"), HttpStatus.NOT_FOUND);
            }
        } else {
            createActualizeQuantityForInventory(actualizeObj, null, isClaimed, token, tenantId);
            responseEntity = new ResponseEntity(new ReturnStatus("ActualizationQuantityDone"), HttpStatus.OK);
        }
        return responseEntity;
    }

    private void createActualizeQuantityForInventory(ActualizeObj actualizeObj, PriceAllocationDTO priceAllocationDTOS, Boolean isClaimed, String token, String tenantId) throws Exception {
        String actualizationQuantityId = "";
        boolean isEdit = false;
        ActualizedQuantityObligations actualizedQuantityObligations;
        List<ActualizedQuantityObligations> list = actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationId(tenantId, actualizeObj.getPlannedObligation().getPlannedObligationId());
        if (!list.isEmpty()) {
            actualizedQuantityObligations = list.get(0);
        } else {
            actualizationQuantityId = transactionIDGenerator.generateId( ACTUALIZATION_QUANTITY_ID, actualizeObj.getPlannedObligation(), tenantId, token,false,"",false);
            if (actualizationQuantityId == null) {
                throw new TaomishError("Actualization Quantity ID is not generated");
            }
            actualizedQuantityObligations = new ActualizedQuantityObligations();
            actualizedQuantityObligations.setActualizedQuantityId(actualizationQuantityId);
//          changing actualization_id  to actualizationQuantityId as actualization Id will create duplicate records
            actualizedQuantityObligations.setActualizationId(actualizationQuantityId);
        }
        double actualizeLoadQuantity = (actualizedQuantityObligations.getLoadQuantity() == null) ? actualizeObj.getQuantity().getLoadQuantity() : actualizedQuantityObligations.getLoadQuantity() + actualizeObj.getQuantity().getLoadQuantity() ;
        double actualizeUnLoadQuantity = (actualizedQuantityObligations.getUnloadQuantity() == null) ? actualizeObj.getQuantity().getUnloadQuantity() : actualizedQuantityObligations.getUnloadQuantity() + actualizeObj.getQuantity().getUnloadQuantity();
        actualizedQuantityObligations.setPlannedObligationId(actualizeObj.getPlannedObligation().getPlannedObligationId());
        actualizedQuantityObligations.setPlannedObligationType(actualizeObj.getPlannedObligation().getTradeTransactionType());
        actualizedQuantityObligations.setTradeId(actualizeObj.getPlannedObligation().getTradeId());
        actualizedQuantityObligations.setPlannedQuantity(actualizeObj.getQuantity().getPlannedQuantity());
        actualizedQuantityObligations.setLoadQuantity(actualizeLoadQuantity);
        actualizedQuantityObligations.setUnloadQuantity(actualizeUnLoadQuantity);
        actualizedQuantityObligations.setBrand(actualizeObj.getQuantity().getBrand());
        actualizedQuantityObligations.setGrade(actualizeObj.getQuantity().getGrade());
        actualizedQuantityObligations.setOrigin(actualizeObj.getQuantity().getOrigin());
        actualizedQuantityObligations.setCommodity(actualizeObj.getQuantity().getCommodity());
        actualizedQuantityObligations.setQuantityUom(actualizeObj.getQuantity().getQuantityUom());
        actualizedQuantityObligations.setPurpose(actualizeObj.getQuantity().getPurpose());
        actualizedQuantityObligations.setTenantId(tenantId);
        if (actualizeObj.getQuantity().getActualizationEventMapping() != null || !actualizeObj.getQuantity().getActualizationEventMapping().isEmpty()) {
            actualizedQuantityObligations.setActualizationEventMapping(getEventMapping(actualizeObj.getQuantity().getActualizationEventMapping()));
        }
        logger.info("Saving Quantity Actualization for ObligationId  : {}" , actualizedQuantityObligations.getPlannedObligationId());
        actualizationQuantityRepo.save(actualizedQuantityObligations);

//        publishMessage(EXCHANGE_NAME, PHYSICAL_TRADE_ACTUALIZATION_TOPIC, UPDATE, actualizedQuantityObligations);
        List<CashflowDataDTO> cashFlowBaseMasterList = null;
        List<SearchCriteria> searchCriteriaList;
        searchCriteriaList = new ArrayList<>();
        searchCriteriaList.add(new SearchCriteria(TEN_ANT_ID, EQ, tenantId));
        searchCriteriaList.add(new SearchCriteria(PLANNED_OBLGN_ID, EQ, actualizeObj.getPlannedObligation().getPlannedObligationId()));
        searchCriteriaList.add(new SearchCriteria(CASH_FLOW_STATUS, EQ, ACTIVE));
        searchCriteriaList.add(new SearchCriteria("quantityStatus", EQ, ACTUAL));
        cashFlowBaseMasterList = TransactionIdUtil.queryList(baseUrl + CASHFLOW_ROOT + GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaList, CashflowDataDTO.class);
        if (!cashFlowBaseMasterList.isEmpty()) {
            isEdit = true;
        }else{
            isEdit = false;
        }
        PlannedObligationDTO plannedObligationDTO;

        JSONObject tolerance = new JSONObject(actualizeObj.getPlannedObligation().getToleranceValue());
        double min = tolerance.getDouble("min");
        double max = tolerance.getDouble("max");

        double minimumTolerance = (actualizeObj.getPlannedObligation().getPlannedQuantity() - ((min / 100) * actualizeObj.getPlannedObligation().getPlannedQuantity()));
        double maximumTolerance = (actualizeObj.getPlannedObligation().getPlannedQuantity() + ((max / 100) * actualizeObj.getPlannedObligation().getPlannedQuantity()));

        if (actualizedQuantityObligations.getLoadQuantity() >= actualizedQuantityObligations.getUnloadQuantity() && actualizedQuantityObligations.getLoadQuantity() >= minimumTolerance && actualizedQuantityObligations.getLoadQuantity() <= maximumTolerance ) {
            try {
                plannedObligationDTO = updateActualizedStatus(actualizeObj.getPlannedObligation().getPlannedObligationId(), token, tenantId);
            } catch (Exception e) {
                throw new TaomishError("Updating planned obligation failed",e);
            }
            if (!plannedObligationDTO.isExternalRecord()) {
                try {
                    if (plannedObligationDTO.getTradeTransactionType().name().equalsIgnoreCase(BUY)) {
                        createQuantityCashFlow(actualizedQuantityObligations.getLoadQuantity(), actualizedQuantityObligations.getUnloadQuantity(), actualizeObj.getPlannedObligation(),
                                isEdit, priceAllocationDTOS, isClaimed, token, tenantId, actualizedQuantityObligations.getSplitSequenceNumber(), actualizedQuantityObligations.getClaimedQuantity(), null, null);
                    }

                    if (plannedObligationDTO.getTradeTransactionType().name().equalsIgnoreCase(SELL) && Boolean.TRUE.equals((plannedObligationDTO.getObligationState().get(PlannedObligationState.PLANNED)))) {
                        createQuantityCashFlow(actualizedQuantityObligations.getLoadQuantity(), actualizedQuantityObligations.getUnloadQuantity(), actualizeObj.getPlannedObligation(),
                                isEdit, priceAllocationDTOS, isClaimed, token, tenantId, actualizedQuantityObligations.getSplitSequenceNumber(), actualizedQuantityObligations.getClaimedQuantity(), null, null);
                    }
                } catch (Exception e) {
                    logger.error(" cash flow creation failed ",e);
                    plannedObligationDTO.getObligationState().put(PlannedObligationState.ACTUALIZED, false);
                    TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + UPDATE_TRADE_PLANOBLIGATION + QUERY+TENANT_ID_EQ + tenantId, HttpMethod.POST, token, plannedObligationDTO, PlannedObligationDTO.class);
                    throw new TaomishError("Cash-flow creation failed",e);
                }
            }
        }

        // updateFXAlloactionCashflow(actualizeObj.getPlannedObligation().getPlannedObligationId());

        // Create and Publish Rabbit message to update Actualized Quantity data to EOD service
        logger.info("Publishing Rabbit message to update Actualization data in EOD service for obligationID  :{}", actualizedQuantityObligations.getPlannedObligationId());
        ActualizedQuantityObligationsDTO actualizedQuantityObligationsDTO = new ActualizedQuantityObligationsDTO();
        actualizedQuantityObligationsDTO.setPlannedObligationId(actualizedQuantityObligations.getPlannedObligationId());
        actualizedQuantityObligationsDTO.setLoadQuantity(actualizedQuantityObligations.getLoadQuantity());
        actualizedQuantityObligationsDTO.setBrand(actualizedQuantityObligations.getBrand());
        actualizedQuantityObligationsDTO.setGrade(actualizedQuantityObligations.getGrade());
        actualizedQuantityObligationsDTO.setOrigin(actualizedQuantityObligations.getOrigin());
        actualizedQuantityObligationsDTO.setUnloadQuantity(actualizedQuantityObligations.getUnloadQuantity());
        actualizedQuantityObligationsDTO.setTenantId(tenantId);
        platformQueueService.sendObject(EXCHANGE_NAME, QUANTITY_ACTUALIZATION, actualizedQuantityObligationsDTO);

        logger.info("Quantity actualization is done for planned Obligation id  :{} ", actualizeObj.getPlannedObligation().getPlannedObligationId());
    }

    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReturnStatus unclaimQuantity(String plannedObligationId, String token, User user) {
        var  plannedObligationTempDTO = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + QUERY+TENANT_ID_EQ + user.getTenantId() + PLANNED_OBLIGATION_ID + plannedObligationId, HttpMethod.GET, token, null, PlannedObligationDTO.class);
        if(plannedObligationTempDTO.getTradeSettlementReference()!=null){
            var interCompanyTradeDTO = TransactionIdUtil.query(baseUrl + "/api/interCompanyTrades/v1/getInterCompanyTradeByUuid" + QUERY+TENANT_ID_EQ + user.getTenantId() + "&interCompanyUuid=" + plannedObligationTempDTO.getTradeSettlementReference(), HttpMethod.GET, token, null, InterCompanyTradeDTO.class);
            var ictBuyObligationId=interCompanyTradeDTO.getSettlementData().getInterCompanyTradeDetails().get(0).getObligationId();
            var ictSellObligationId=interCompanyTradeDTO.getSettlementData().getInterCompanyTradeDetails().get(1).getObligationId();
            unclaimQuantity(ictBuyObligationId,token,user);
            unclaimQuantity(ictSellObligationId,token,user);
        }
        List<ActualizedQuantityObligations> actualizedQuantityObligationsList = actualizationQuantityRepo.findAllByPlannedObligationIdAndTenantIdOrderBySplitSequenceNumberAsc(plannedObligationId,user.getTenantId());
        if (actualizedQuantityObligationsList != null && !actualizedQuantityObligationsList.isEmpty()) {
            var errors = new ArrayList<String>();
            actualizedQuantityObligationsList.forEach(item -> errors.addAll(canUnclaimQuantity(item.getPlannedObligationId(),item.getSplitSequenceNumber(),user.getTenantId(),token)));
            if(!errors.isEmpty()) {
                return ReturnStatus.errorInstance("Failed to revert claim with following error :  \n" + String.join(",\n",errors));
            }
            var splitList = new ArrayList<Integer>();
            actualizedQuantityObligationsList.forEach(item -> {
                item.setClaimedQuantity(0.0);
                splitList.add(item.getSplitSequenceNumber());
            });

            List<SearchCriteria> claimdCashflowCriteria = new ArrayList<>();
            claimdCashflowCriteria.add(new SearchCriteria(TEN_ANT_ID, EQ, user.getTenantId()));
            claimdCashflowCriteria.add(new SearchCriteria(PLANNED_OBLGN_ID, EQ, plannedObligationId));
            claimdCashflowCriteria.add(new SearchCriteria(STAGE, "in", List.of(ACCRUED,ACCRUED_PROVISIONAL)));
            claimdCashflowCriteria.add(new SearchCriteria("type", "in", List.of(Trade,TRADE_REVERSAL,CASHFLOW_TYPE_PROVISIONAL)));
            claimdCashflowCriteria.add(new SearchCriteria(SPLIT_SEQUENCE_NO, "in", splitList));
            claimdCashflowCriteria.add(new SearchCriteria(CASH_FLOW_STATUS, EQ, ACTIVE));
            var claimedCashflows = TransactionIdUtil.queryCashflows(baseUrl,token,claimdCashflowCriteria,false);
            var otherThanPriceAllocationCashflows = claimedCashflows.stream()
                    .filter(item -> item.getAllocationId() == null || item.getAllocationId().isEmpty())
                    .map(CashflowDataDTO::getCashflowId).toList();
            var defunctCashflowCriteria = new SpecBuilderUtil().with(user)
                            .addCriteria(new SearchCriteria("cashflowId","in",otherThanPriceAllocationCashflows))
                            .getCriteriaList();
            TransactionIdUtil.defunctCashflows(baseUrl,token,defunctCashflowCriteria);
            actualizationQuantityRepo.saveAll(actualizedQuantityObligationsList);
            TransactionIdUtil.setPlannedObligationStatesToFalse(baseUrl,plannedObligationId,user.getTenantId(),token,PlannedObligationState.DISCHARGED);
        } else {
            return ReturnStatus.errorInstance("Failed to revert claim with following error :  \n Claim Record not found.");
        }
        return ReturnStatus.successInstance("Unclaimed success for Obligation ID : "+ plannedObligationId);
    }

    @SneakyThrows
    private List<String> canUnclaimQuantity(String plannedObligationId, Integer splitNumber, String tenantId, String token) {
        var errors = new ArrayList<String>();
        List<SearchCriteria> invoiceCriteria = new ArrayList<>();
        invoiceCriteria.add(new SearchCriteria(TEN_ANT_ID, EQ, tenantId));
        invoiceCriteria.add(new SearchCriteria(PLANNED_OBLGN_ID, EQ, plannedObligationId));
        invoiceCriteria.add(new SearchCriteria("finalInvoiceType", "equals", "Final Against Claim"));
        invoiceCriteria.add(new SearchCriteria("splitNumber", "in", List.of(0,splitNumber)));
        invoiceCriteria.add(new SearchCriteria("status", "notequals", VOID));
        var invoices = TransactionIdUtil.queryList(baseUrl+INVOICE_ROOT+GET_INVOICE_BY_CRITERIA,HttpMethod.POST,token,invoiceCriteria, InvoiceDTO.class).stream().filter(item -> !item.getStatus().equalsIgnoreCase(INITIATED)).toList();;
        if(!invoices.isEmpty()) {
            var invoiceNumber = invoices.stream().map(InvoiceDTO::getInvoiceNumber).toList();
            errors.add("Invoice ("+ String.join(",",invoiceNumber) +") is already generated for claim");
        }
        return errors;
    }
}
