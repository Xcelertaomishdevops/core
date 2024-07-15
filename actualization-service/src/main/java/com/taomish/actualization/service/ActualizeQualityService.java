package com.taomish.actualization.service;

import com.taomish.actualization.dto.ActualizeObj;
import com.taomish.actualization.model.ActualizedQuality;
import com.taomish.actualization.model.ActualizedQuantityObligations;
import com.taomish.common.domain.TaomishError;
import com.taomish.common.searchcriteria.SearchCriteria;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.dtos.ReturnStatus;
import com.taomish.dtos.actualizationservice.ActualizationQualityObj;
import com.taomish.dtos.cashflowservice.CashflowDataDTO;
import com.taomish.dtos.cashflowservice.UpdateCashflowDTO;
import com.taomish.dtos.physicaltradeservice.PhysicalTradeDTO;
import com.taomish.dtos.physicaltradeservice.PlannedObligationDTO;
import com.taomish.dtos.qualityspec.QualitySpecDTO;
import com.taomish.dtos.tradepricingservice.PriceAllocationCheckDTO;
import com.taomish.enums.TradeTransactionType;
import com.taomish.transaction_reference.service.TransactionIDGenerator;
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
import java.util.Objects;
import java.util.stream.Collectors;

import static com.taomish.RestEndPoints.CashflowRestEndPoints.CASHFLOW_ROOT;
import static com.taomish.RestEndPoints.CashflowRestEndPoints.GET_CASHFLOW_BY_CRITERIA;
import static com.taomish.RestEndPoints.PhysicalRestEndPoint.GET_PHYSICAL_TRADE_BY_TRADE_ID;
import static com.taomish.RestEndPoints.PhysicalRestEndPoint.PHYSICAL_TRADE_ROOT;
import static com.taomish.RestEndPoints.PricingRestEndPoint.GET_PRICE_ALLOCATED_CHECK_BY_PLANNED_OBLIGATION_ID;
import static com.taomish.RestEndPoints.PricingServiceRestEndPoints.ADVANCE_ALLOCATION_ROOT;
import static com.taomish.constants.CashflowConstants.CASHFLOW_TYPE_DISCOUNT;
import static com.taomish.constants.CashflowConstants.CASHFLOW_TYPE_PREMIUM;
import static com.taomish.constants.PhysicalConstants.*;
import static com.taomish.constants.PlannedObligationConstants.GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID;
import static com.taomish.constants.PlannedObligationConstants.PLANNED_OBLIGATION_ROOT;
import static com.taomish.constants.QualitySpecConstants.GET_QUALITY_SPEC_BY_TRADE_ID;
import static com.taomish.constants.QualitySpecConstants.QUALITY_SPECIFICATION_ROOT;

@Service
public class ActualizeQualityService extends ActualizationService {

    private static final Logger logger = LoggerFactory.getLogger(ActualizeQualityService.class);
    private final TransactionIDGenerator transactionIDGenerator;
    @Autowired
    private ActualizationCashflowService actualizationCashflowService;

    @Autowired
    private ActualizationCnDnService actualizationCnDnService;

    @Value("${masterBaseURL}")
    private String masterBaseUrl;

    public ActualizeQualityService(TransactionIDGenerator transactionIDGenerator) {
        this.transactionIDGenerator = transactionIDGenerator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReturnStatus actualizeQualitySpec(ActualizeObj actualizeObj, String token, String tenantId) throws Exception {
        try {
            String actualizationId = getActualizationId(actualizeObj);
            String actualizationQualityId = "";
            ActualizedQuality actualizedQuality;
            for (ActualizationQualityObj qualitySpec : actualizeObj.getQualitySpecs()) {
                if (qualitySpec.getActualizedStatus().equalsIgnoreCase(ACTUALIZED)) {
                    actualizedQuality = actualizationQualityRepo.findAllByActualizedQualityIdAndTenantId(qualitySpec.getActualizedQualityId(),tenantId).get(0);
                    BeanUtils.copyProperties(qualitySpec, actualizedQuality, "id", "actualizationId", "actualizedQualityId", "plannedObligationId", "tradeId");
                } else {
                    actualizedQuality = new ActualizedQuality();
                    actualizationQualityId = transactionIDGenerator.generateId( "actualizationQualityId", actualizeObj.getPlannedObligation(), tenantId, token,false,"",false);
                    if (actualizationQualityId == null) {
                        throw new Exception("Actualization Quality ID is not generated");
                    }
                    actualizedQuality = new ActualizedQuality();
                    BeanUtils.copyProperties(qualitySpec, actualizedQuality, "id");
                    actualizedQuality.setActualizedQualityId(actualizationQualityId);
                    actualizedQuality.setActualizationId(actualizationId);
                    actualizedQuality.setPlannedObligationId(actualizeObj.getPlannedObligation().getPlannedObligationId());
                    actualizedQuality.setTradeId(actualizeObj.getPlannedObligation().getTradeId());
                    actualizedQuality.setEstimatedQualitySpecId(qualitySpec.getQualitySpecId());
                }
                actualizationQualityRepo.save(actualizedQuality);
            }
            logger.info("Quality Spec actualization is done for planned Obligation id {} : ",actualizeObj.getPlannedObligation().getPlannedObligationId());
            return new ReturnStatus("Actualization Quality Done");
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }


    @SneakyThrows
    public void actualizeSingleQualitySpec(ActualizationQualityObj qualitySpec, PlannedObligationDTO plannedObligationDTO, boolean isClaimed, String token, String tenantId) throws Exception {
        try {
            ActualizedQuality actualizedQuality;
            if(!isClaimed) {
                String actualizationId = TransactionIdUtil.generateRandomId();
                String actualizationQualityId = "";
                if (qualitySpec.getActualizedStatus().equalsIgnoreCase(ACTUALIZED) && qualitySpec.getActualizedQualityId() != null && qualitySpec.getActualizedQualityId().length() > 0) {
                    actualizedQuality = actualizationQualityRepo.findAllByActualizedQualityIdAndTenantId(qualitySpec.getActualizedQualityId(),tenantId).get(0);
                    BeanUtils.copyProperties(qualitySpec, actualizedQuality, "id", "actualizationId", "actualizedQualityId", "plannedObligationId", "tradeId");
                } else {
                    actualizationQualityId = transactionIDGenerator.generateId( "actualizationQualityId", plannedObligationDTO, tenantId, token,false,"",false);
                    if (actualizationQualityId == null) {
                        throw new Exception("Actualization Quality ID is not generated");
                    }
                    actualizedQuality = new ActualizedQuality();
                    BeanUtils.copyProperties(qualitySpec, actualizedQuality, "id");
                    actualizedQuality.setActualizedQualityId(actualizationQualityId);
                    actualizedQuality.setActualizationId(actualizationId);
                    actualizedQuality.setPlannedObligationId(plannedObligationDTO.getPlannedObligationId());
                    actualizedQuality.setTradeId(plannedObligationDTO.getTradeId());
                    actualizedQuality.setEstimatedQualitySpecId(qualitySpec.getQualitySpecId());
                }
                actualizationQualityRepo.save(actualizedQuality);
            } else {
                actualizedQuality = actualizationQualityRepo.findAllByActualizedQualityIdAndTenantId(qualitySpec.getActualizedQualityId(),tenantId).get(0);
                actualizedQuality.setClaimedBasis(qualitySpec.getClaimedBasis());
                actualizedQuality.setSettlementType(qualitySpec.getSettlementType());
                actualizedQuality.setClaimedPremiumDiscount(qualitySpec.getPremiumDiscount());
                actualizationQualityRepo.save(actualizedQuality);
            }
            logger.info("Quality Spec actualization is done for planned Obligation id : {}" , plannedObligationDTO.getPlannedObligationId());
        } catch (Exception e) {
            throw new TaomishError(e.getMessage());
        }
    }



    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReturnStatus actualizeQualitySpecForTransportActualization(ActualizeObj actualizeObj,boolean isClaimed, String token, String tenantId) throws Exception {
        PlannedObligationDTO plannedObligationDTO = null;
        for(ActualizationQualityObj actualizationQualityObj:actualizeObj.getQualitySpecs()) {
            if(actualizationQualityObj.getActualizedStatus().equalsIgnoreCase(CONTRACTED) || (isClaimed && actualizationQualityObj.getClaimedBasis() != 0)) {
                plannedObligationDTO = TransactionIdUtil.query(baseUrl+PLANNED_OBLIGATION_ROOT+GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID+"?tenantId="+tenantId+"&plannedObligationId="+actualizationQualityObj.getPlannedObligationId(), HttpMethod.GET,token,null,PlannedObligationDTO.class);
                actualizeSingleQualitySpec(actualizationQualityObj,plannedObligationDTO,isClaimed,token,tenantId);
            }
        }
        return ReturnStatus.successInstance("Quality Spec Claimed Successfully.");
    }

    public ResponseEntity checkQualityActualization(List<String> plannedObligationIds, String tenantId, String token) {
        logger.info("Entered to check quality actualization is done for given planned obligation in ActualizationQualityService.checkQualityActualization()");
        List<String> notActualizedFor = new ArrayList<>();
        List<String> plannedObligationList = plannedObligationIds.stream().distinct().toList();
        List<ActualizedQuality> actualizedQualityList = null;
        List<QualitySpecDTO> tradeQualitySpecs = null;
        PlannedObligationDTO plannedObligationDTO = null;
        List<String> qualityIds = null;
        for(String plannedOblogationsId:plannedObligationList) {
            plannedObligationDTO = TransactionIdUtil.query(baseUrl+PLANNED_OBLIGATION_ROOT+GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID+"?tenantId="+tenantId+"&plannedObligationId="+plannedOblogationsId,HttpMethod.GET,token,null,PlannedObligationDTO.class);
            if(plannedObligationDTO == null) {
                continue;
            }
            actualizedQualityList = actualizationQualityRepo.findAllByPlannedObligationIdAndTenantId(plannedOblogationsId,tenantId);
            qualityIds = actualizedQualityList.stream().map(ActualizedQuality::getName).collect(Collectors.toList());
            tradeQualitySpecs =  TransactionIdUtil.queryList(baseUrl + QUALITY_SPECIFICATION_ROOT +
                                GET_QUALITY_SPEC_BY_TRADE_ID +"?tenantId="+ tenantId
                            +"&tradeId="+ plannedObligationDTO.getTradeId(),
                        HttpMethod.GET, token,null, QualitySpecDTO.class);
            for(QualitySpecDTO qualitySpecDTO:tradeQualitySpecs) {
                if(!qualityIds.contains(qualitySpecDTO.getName())) {
                    notActualizedFor.add(plannedOblogationsId);
                    break;
                }
            }
        }
        if(notActualizedFor.size() > 0) {
            logger.info("Exiting after check quality actualization is done for given planned obligation in ActualizationQualityService.checkQualityActualization()");
            return new ResponseEntity(new ReturnStatus("Qualtity is not actualized for : "+String.join(",",notActualizedFor),"success"),HttpStatus.OK);
        }
        logger.info("Exiting after check quality actualization is done for given planned obligation in ActualizationQualityService.checkQualityActualization()");
        return new ResponseEntity(new ReturnStatus("Quality is Actaulized","success"),HttpStatus.OK);
    }

    public void claimQuality(ActualizationQualityObj qualitySpec,PlannedObligationDTO plannedObligation,String token, String tenantId) throws Exception {
        logger.info("Enterd to claim qualtity in ActualizationQualityService.claimQuality()");
        double quantity = 0.0;
        PhysicalTradeDTO trade = TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_ROOT + GET_PHYSICAL_TRADE_BY_TRADE_ID+
                "?tradeId=" + plannedObligation.getTradeId()+"&tenantId="+tenantId, HttpMethod.GET, token, null, PhysicalTradeDTO.class);

        UpdateCashflowDTO updateCashflowDTO = new UpdateCashflowDTO();
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
        updateCashflowDTO.setQuantityUom(plannedObligation.getQuantityUOM());

        PriceAllocationCheckDTO priceAllocationCheckDTO = TransactionIdUtil.query(baseUrl+ADVANCE_ALLOCATION_ROOT+GET_PRICE_ALLOCATED_CHECK_BY_PLANNED_OBLIGATION_ID+"?plannedObligationId="+plannedObligation.getPlannedObligationId()+"&tenantId="+tenantId,HttpMethod.GET,token,null,PriceAllocationCheckDTO.class);
        if(Boolean.TRUE.equals(trade.getIsProvisionalPricing()) && !trade.getPriceType().equalsIgnoreCase(FIXEDPRICED)) {
            updateCashflowDTO.setTradePriceCurrency(plannedObligation.getProvisionalPriceCurrency());
            updateCashflowDTO.setPriceType(plannedObligation.getPriceType());
            updateCashflowDTO.setFxRate(plannedObligation.getFxRate());
            if(!Objects.requireNonNull(priceAllocationCheckDTO).isFullyPriced()) {
                updateCashflowDTO.setStage(ACCRUED_PROVISIONAL);
                updateCashflowDTO.setPrice(plannedObligation.getProvisionalPrice());
            } else {
                updateCashflowDTO.setPrice(priceAllocationCheckDTO.getPrice());
            }
        } else {
            updateCashflowDTO.setTradePriceCurrency(plannedObligation.getTradePriceCurrency());
            updateCashflowDTO.setPrice(plannedObligation.getTradePrice());
            updateCashflowDTO.setPriceType(plannedObligation.getPriceType());
            updateCashflowDTO.setFxRate(plannedObligation.getFxRate());
            updateCashflowDTO.setStage(ACCRUED);
            if(Objects.requireNonNull(priceAllocationCheckDTO).isFullyPriced()) {
                updateCashflowDTO.setPrice(priceAllocationCheckDTO.getPrice());
            }
        }
        if(qualitySpec.getPremiumDiscount() > 0) {
            updateCashflowDTO.setType(CASHFLOW_TYPE_PREMIUM);
        } else {
            updateCashflowDTO.setType(CASHFLOW_TYPE_DISCOUNT);
        }
        ActualizedQuantityObligations actualizedQuantityObligations = actualizationQuantityRepo.findByPlannedObligationIdAndSplitSequenceNumberAndTenantIdOrderBySplitSequenceNumberAsc(plannedObligation.getPlannedObligationId(),plannedObligation.getSplitSequenceNumber(),tenantId);
        if(actualizedQuantityObligations != null) {
            if (plannedObligation.getTradeTransactionType().equals(TradeTransactionType.BUY)) {
                quantity = actualizedQuantityObligations.getLoadQuantity();
            } else {
                quantity = actualizedQuantityObligations.getUnloadQuantity();
            }
        }
        updateCashflowDTO.setObligationQuantity(quantity);
        updateCashflowDTO.setPremiumDiscount(qualitySpec.getPremiumDiscount());
        updateCashflowDTO.setTradeSettlementCurrency(plannedObligation.getTradeSettlementCurrency());
        updateCashflowDTO.setDescription("Quality claims for shipment "+plannedObligation.getPlannedObligationId()+" of "+plannedObligation.getTradeId());
        /**
         * if isClaimed is false normal actualization flow
         */
        List<SearchCriteria> searchCriteriaList;
        searchCriteriaList= new ArrayList<>();
        searchCriteriaList.add(new SearchCriteria("tenantId","equals",tenantId));
        searchCriteriaList.add(new SearchCriteria("plannedObligationId","equals", plannedObligation.getPlannedObligationId()));
        searchCriteriaList.add(new SearchCriteria("actualizationObjectId","equals", qualitySpec.getActualizedQualityId()));
        searchCriteriaList.add(new SearchCriteria("type","in", Arrays.asList(CASHFLOW_TYPE_PREMIUM,CASHFLOW_TYPE_DISCOUNT)));
        searchCriteriaList.add(new SearchCriteria("cashflowStatus","equals", ACTIVE));
        CashflowDataDTO[] cashflowDTOForInvoiceCheck = TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT+GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST,token,searchCriteriaList,CashflowDataDTO[].class);
        if(cashflowDTOForInvoiceCheck != null && cashflowDTOForInvoiceCheck.length > 0) {
            CashflowDataDTO invoiceCashflow = cashflowDTOForInvoiceCheck[0];
            if (!StringUtils.isEmpty(cashflowDTOForInvoiceCheck[0].getInvoiceNumber())) {
                invoiceCashflow.setTradeTransactionType(trade.getTradeTransactionType());
                invoiceCashflow.setClaimQuantity(updateCashflowDTO.getObligationQuantity());
                invoiceCashflow.setFxRate(updateCashflowDTO.getFxRate());
                invoiceCashflow.setType(updateCashflowDTO.getType());
                invoiceCashflow.setClaimpremiumDiscount(updateCashflowDTO.getPremiumDiscount());
                actualizationCnDnService.generateClaimCashflow(invoiceCashflow, token, tenantId);
            }
        } else {
        //    actualizationCnDnService.generateClaimCashflow(updateCashflowDTO, token, tenantId);
        }
        logger.info("Exited to claim qualtity in ActualizationQualityService.claimQuality()");

    }
}
