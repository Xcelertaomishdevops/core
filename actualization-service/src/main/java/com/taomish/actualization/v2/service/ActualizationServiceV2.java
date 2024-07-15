package com.taomish.actualization.v2.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taomish.actualization.dto.*;
import com.taomish.actualization.model.*;
import com.taomish.actualization.repo.*;
import com.taomish.actualization.service.ActualizationCashflowService;
import com.taomish.actualization.service.ActualizationCnDnService;
import com.taomish.actualization.v2.dtos.PreActualizationPayloadDto;
import com.taomish.actualization.v2.models.PlannedObligationsForActualization;
import com.taomish.actualization.v2.models.PlannedTrades;
import com.taomish.actualization.v2.repo.PlannedObligationsForActualizationRepo;
import com.taomish.actualization.v2.repo.PlannedTradesRepo;
import com.taomish.common.domain.TaomishError;
import com.taomish.common.searchcriteria.SearchCriteriaBuilder;
import com.taomish.common.searchcriteria.SpecBuilderUtil;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.constants.ActualizationConstants;
import com.taomish.dtos.ReturnStatus;
import com.taomish.dtos.actualizationservice.ActualizeObjV2;
import com.taomish.dtos.actualizationservice.ActualizedQuantityObligationsDTO;
import com.taomish.dtos.cashflowservice.CashflowDataDTO;
import com.taomish.dtos.cashflowservice.UpdateCashflowDTO;
import com.taomish.dtos.conversionservice.UomConversionOutputtDTO;
import com.taomish.dtos.costservice.TradeCostDTO;
import com.taomish.dtos.costservice.TradeCostEODRawDataDTO;
import com.taomish.dtos.inventoryservice.StockMovementDto;
import com.taomish.dtos.invoice.InvoiceDTO;
import com.taomish.dtos.oisservice.PurchaseOrderDto;
import com.taomish.dtos.physicaltradeplanning.PhysicalTradePlanningDTO;
import com.taomish.dtos.physicaltradeplanning.PlanningDTO;
import com.taomish.dtos.physicaltradeplanning.TradePlanningDetails;
import com.taomish.dtos.physicaltradeservice.InterCompanyTradeDTO;
import com.taomish.dtos.physicaltradeservice.InterCompanyTradeObjectDTO;
import com.taomish.dtos.physicaltradeservice.PhysicalTradeDTO;
import com.taomish.dtos.physicaltradeservice.PlannedObligationDTO;
import com.taomish.dtos.tradepricingservice.PriceAllocationCheckDTO;
import com.taomish.dtos.tradepricingservice.PriceAllocationDTO;
import com.taomish.dtos.transportAllocation.SeaFreightDetailsDTO;
import com.taomish.dtos.transportactualizationservice.BillOfLandingDTO;
import com.taomish.enums.PlannedObligationState;
import com.taomish.enums.TradeTransactionType;
import com.taomish.search.JpaSpecBuilder;
import com.taomish.search.SearchCriteria;
import com.taomish.services.CurrencyAndUOMConversionService.service.CurrencyConversionService;
import com.taomish.transaction_reference.service.TransactionIDGenerator;
import com.taomish.utils.JsonUtils;
import com.taomish.web.config.TaomishLocalDateTime;
import com.taomish.web.security.models.User;
import com.taomish.web.service.ModelMapperService;
import com.taomish.web.utils.StreamUtil;
import jakarta.persistence.EntityManager;
import lombok.SneakyThrows;
import org.primefaces.shaded.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.taomish.messaging.PlatformQueueService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.taomish.RestEndPoints.CashflowRestEndPoints.*;
import static com.taomish.RestEndPoints.OisRestEndpoint.*;
import static com.taomish.RestEndPoints.PhysicalRestEndPoint.*;
import static com.taomish.RestEndPoints.PlanningRestEndPoint.*;
import static com.taomish.RestEndPoints.PricingRestEndPoint.*;
import static com.taomish.RestEndPoints.PricingServiceRestEndPoints.ADVANCE_ALLOCATION_ROOT;
import static com.taomish.constants.ActualizationConstants.AND;
import static com.taomish.constants.ActualizationConstants.*;
import static com.taomish.constants.CashflowConstants.*;
import static com.taomish.constants.EODConstants.*;
import static com.taomish.constants.InvoiceConstants.*;
import static com.taomish.constants.PhysicalConstants.*;
import static com.taomish.constants.PlannedObligationConstants.*;
import static com.taomish.constants.PlanningConstants.BACK2BACK;
import static com.taomish.constants.PlanningConstants.WASHOUT;
import static com.taomish.constants.TradeCostConstants.*;
import static com.taomish.constants.TradeCostConstants.SCHEDULED_QUANTITY;
import static com.taomish.constants.TransportAllocationConstants.*;

@Service
public class ActualizationServiceV2 {
    private static final Logger logger = LoggerFactory.getLogger(ActualizationServiceV2.class);

    @Value("${baseUrlEC2DEV}")
    private String baseUrl;

    @Value("${masterBaseURL}")
    private String masterBaseURL;

    @Value("${inventoryBaseURL}")
    private String inventoryBaseURL;

    @Value("${oisBaseURL}")
    private String oisBaseURL;

    private final PlatformQueueService platformQueueService;

    @Autowired
    @Lazy
    ActualizationServiceV2 actualizationServiceV2;
    

    final
    ActualizationQuantityRepo actualizationQuantityRepo;

    final
    BillOfLandingRepo billOfLandingRepo;
    final
    ActualizationCostRepo actualizedCostRepo;
    final
    ActualQualityRepo actualQualityRepo;
    final
    ActualPackingDetailsRepo actualPackingDetailsRepo;

    final
    ActualizationCnDnService actualizationCnDnService;

    final
    GrnStockRepo grnStockRepo;
    private final GRNService grnService;
    private final PlannedObligationsForActualizationRepo plannedObligationsForActualizationRepo;
    private final ActualizationCashflowService actualizationCashflowService;
    private final CurrencyConversionService currencyConversionService;
    private final PlannedTradesRepo plannedTradesRepo;
    private final EntityManager entityManager;
    private final ModelMapperService modelMapperService;

    private final ActualizationCashflowServiceV2 actualizationCashflowServiceV2;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();
    private final StreamUtil streamUtil;

    private final TypeReference<Map<String, List<String>>>  criteriaType = new TypeReference<>() {};

    private final TransactionIDGenerator transactionIDGenerator;
    public ActualizationServiceV2(PlatformQueueService platformQueueService, PlannedObligationsForActualizationRepo plannedObligationsForActualizationRepo,
                                  PlannedTradesRepo plannedTradesRepo, EntityManager entityManager,
                                  ActualizationCashflowService actualizationCashflowService,
                                  StreamUtil streamUtil, ModelMapperService modelMapperService, ActualizationCashflowServiceV2 actualizationCashflowServiceV2, ActualizationQuantityRepo actualizationQuantityRepo, BillOfLandingRepo billOfLandingRepo, ActualizationCostRepo actualizedCostRepo, ActualQualityRepo actualQualityRepo, ActualPackingDetailsRepo actualPackingDetailsRepo, ActualizationCnDnService actualizationCnDnService, GrnStockRepo grnStockRepo, GRNService grnService, CurrencyConversionService currencyConversionService, TransactionIDGenerator transactionIDGenerator) {
        this.platformQueueService = platformQueueService;
        this.plannedObligationsForActualizationRepo = plannedObligationsForActualizationRepo;
        this.plannedTradesRepo = plannedTradesRepo;
        this.entityManager = entityManager;
        this.actualizationCashflowService = actualizationCashflowService;
        this.streamUtil = streamUtil;
        this.modelMapperService = modelMapperService;
        this.actualizationCashflowServiceV2 = actualizationCashflowServiceV2;
        
        
        this.actualizationQuantityRepo = actualizationQuantityRepo;
        this.billOfLandingRepo = billOfLandingRepo;
        this.actualizedCostRepo = actualizedCostRepo;
        this.actualQualityRepo = actualQualityRepo;
        this.actualPackingDetailsRepo = actualPackingDetailsRepo;
        this.actualizationCnDnService = actualizationCnDnService;
        this.grnStockRepo = grnStockRepo;
        this.grnService = grnService;
        this.currencyConversionService = currencyConversionService;
        this.transactionIDGenerator = transactionIDGenerator;
    }

    public Page<PlannedObligationsForActualization> findAllObligations(Set<SearchCriteria> searchCriteriaList, int page, int size, User principal){
        var jpaSpecBuilder = new JpaSpecBuilder<PlannedObligationsForActualization>();
        return  this.plannedObligationsForActualizationRepo.findAll(jpaSpecBuilder.buildSpec(searchCriteriaList,principal.getTenantId()), PageRequest.of(page, size));
    }
    public Page<PlannedTrades> findAllPlannedTrades(Set<SearchCriteria> searchCriteriaList, int page, int size, User principal){
        var jpaSpecBuilder = new JpaSpecBuilder<PlannedTrades>();
        return  this.plannedTradesRepo.findAll(jpaSpecBuilder.buildSpec(searchCriteriaList,principal.getTenantId()), PageRequest.of(page, size));
    }

    public Map<String,List<String>> findFilterCriteriaForActualization(String tenantId) throws JsonProcessingException {
        var query = entityManager.createNativeQuery("select criteria_map from vw_planned_obligations_for_actualization_criteria where tenant_id = :tenantId");
        query.setParameter(TEN_ANT_ID,tenantId);
        var json = (String) query.getSingleResult();
        return objectMapper.readValue(json,criteriaType);
    }
    public Map<String,List<String>> findFilterCriteriaForActualizationContainer(String tenantId) throws JsonProcessingException {
        var query = entityManager.createNativeQuery("select criteria_map from vw_planned_obligations_for_actualization_criteria_container where tenant_id = :tenantId");
        query.setParameter(TEN_ANT_ID,tenantId);
        var json = (String) query.getSingleResult();
        return objectMapper.readValue(json,criteriaType);
    }

    public Page<PlannedObligationsForActualization> findAllObligationsByCriteria(Set<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaList, int page, int size, User principal) {
        try{
            var finalCriteriaList = searchCriteriaList.stream().peek(item -> {
                if(item.getFieldName().equalsIgnoreCase("tradeTransactionType")) {
                    if(item.getCondition().equalsIgnoreCase("in")){
                        List<String> value= (List<String>) item.getValue();
                        item.setValue(value.stream().map(v->TradeTransactionType.valueOf(v.toString())).toList());
                    }else{
                        item.setValue(TradeTransactionType.valueOf(item.getValue().toString()));
                    }
                }
                if(item.getFieldName().equalsIgnoreCase("tradeDateTime")) {
                    item.setValue(TaomishLocalDateTime.parse((String) item.getValue()));
                }
                if(item.getFieldName().equalsIgnoreCase("deliveryEndDate")) {
                    item.setValue(TaomishLocalDateTime.parse((String) item.getValue()));
                }
                if(item.getFieldName().equalsIgnoreCase("actualized")) {
                    {
                        if(item.getCondition().equalsIgnoreCase("equals") || item.getCondition().equalsIgnoreCase("notequals")){
                            item.setValue(item.getValue().equals("true")?true:false);
                        }
                    }
                }
            }).toList();
            return new SpecBuilderUtil().with(principal.getTenantId())
                    .setupPageDesc(page,size,"createdTimestamp")
                    .setCriteriaList(finalCriteriaList)
                    .from(plannedObligationsForActualizationRepo).findPage(PlannedObligationsForActualization.class);
        }catch (Exception e){
            logger.error("failed",e);
        }
        return null;
    }

    private List<String> canActualize(List<PlannedObligationDTO> plannedObligationDTOList,List<ActualizeObjV2> actualizeObjList) {
        List<String> errors = new ArrayList<>();
        if(plannedObligationDTOList == null || plannedObligationDTOList.isEmpty()) {
            List<String> plannedObligationIds = actualizeObjList.stream().map(ActualizeObjV2::getPlannedObligationId).filter(Objects::nonNull).toList();
            errors.add("No Planned Obligation found for Planned Obligation Ids: " + String.join(",", plannedObligationIds));
            return errors;
        }
        for(PlannedObligationDTO plannedObligationDTO: plannedObligationDTOList) {
            if(plannedObligationDTO.getPriceType() == null) {
                errors.add("Price Type is not defined for Planned Obligation Id: " + plannedObligationDTO.getPlannedObligationId());
                continue;
            }
            if(plannedObligationDTO.getPriceType().equalsIgnoreCase(PTBF) && Boolean.FALSE.equals(plannedObligationDTO.getProvisionalPricing()) || plannedObligationDTO.getPriceType().equalsIgnoreCase(DIFFERENTIAL)) {
                if(Boolean.FALSE.equals(plannedObligationDTO.getObligationState().get(PlannedObligationState.PARTIALLY_PRICED)) && Boolean.FALSE.equals(plannedObligationDTO.getObligationState().get(PlannedObligationState.PRICED))) {
                    errors.add("Price is not allocated for Planned Obligation Id: " + plannedObligationDTO.getPlannedObligationId());
                } else if(Boolean.TRUE.equals(plannedObligationDTO.getObligationState().get(PlannedObligationState.PARTIALLY_PRICED))) {
                    errors.add("Price is not fully allocated for Planned Obligation Id: " + plannedObligationDTO.getPlannedObligationId());
                }
            }
        }
        return errors;
    }

    private PhysicalTradePlanningDTO createPlanningObjectForInterCompany(List<PlannedObligationDTO> plannedObligationDTOList, String parentPlanId, Double matchQuantity, List<String> plannedObligationIds, String tenantId, String token) {
        TradePlanningDetails planDetails = new TradePlanningDetails();
        planDetails.setMatchType(List.of(BACK2BACK));
        planDetails.setMatchQuantity(matchQuantity);
        plannedObligationDTOList.forEach(obligation -> {
            var planningDTO = new PlanningDTO();
            BeanUtils.copyProperties(obligation,planningDTO);
            planningDTO.setMatchQuantity(obligation.getPlannedQuantity());
            if(planningDTO.getTradeTransactionType() == TradeTransactionType.BUY) {
                planDetails.setTradeBuyObligations(List.of(planningDTO));
            } else {
                planDetails.setTradeSellObligations(List.of(planningDTO));
            }
        });
        PhysicalTradePlanningDTO physicalTradePlan  = TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_PLANNING_ROOT + SAVE_TRADE_PLANNING+"?vesselPlanning=false" +AND_TENANT_ID+tenantId,HttpMethod.POST, token,  planDetails, PhysicalTradePlanningDTO.class);

        SeaFreightDetailsDTO seaFreightDetailsDTO = TransactionIdUtil.query(baseUrl + TRANSPORT_ALLOCATION_V2_ROOT + GET_SEA_FRIGHT_DETAILS_BY_PLAN_ID + QUERY+TENANT_ID_EQ + tenantId+ "&planId=" +parentPlanId, HttpMethod.GET, token,null, SeaFreightDetailsDTO.class);
        if(seaFreightDetailsDTO != null && physicalTradePlan != null) {
            seaFreightDetailsDTO.setUuid(null);
            seaFreightDetailsDTO.setPlanId(physicalTradePlan.getPlanningId());
            seaFreightDetailsDTO.setPlannedObligationIds(new ArrayList<>());
            seaFreightDetailsDTO.getPlannedObligationIds().addAll(plannedObligationIds);
            TransactionIdUtil.query(baseUrl+TRANSPORT_ALLOCATION_V2_ROOT+SAVE_SEA_FREIGHT_DETAILS+QUERY+TENANT_ID_EQ+tenantId,HttpMethod.POST,token,seaFreightDetailsDTO,Object.class);
        }
        return physicalTradePlan;
    }

    private List<ActualizeObjV2> getActualizedObjectListForInterCompanyTrades(String tenantId, ActualizeObjV2 actualizeObjV2, String token, PreActualizationPayloadDto preActualizationPayloadDto,Boolean canCreatePlan) {
        InterCompanyTradeDTO interCompanyTradeDTO;
        interCompanyTradeDTO = TransactionIdUtil.query(baseUrl + "/api/interCompanyTrades/v1/getInterCompanyTradeByUuid" + QUE_TENANT_ID + tenantId + "&interCompanyUuid=" + actualizeObjV2.getTradeSettlementReference(), HttpMethod.GET, token, null, InterCompanyTradeDTO.class);
        if(interCompanyTradeDTO == null) {
            return new ArrayList<>();
        }
        var plannedObligationIdList = interCompanyTradeDTO.getSettlementData().getInterCompanyTradeDetails().stream().map(InterCompanyTradeObjectDTO::getObligationId).toList();
        var plannedObligationsSearchCriteria = new ArrayList<com.taomish.common.searchcriteria.SearchCriteria>();
        plannedObligationsSearchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(TEN_ANT_ID, EQUA_LS, tenantId));
        plannedObligationsSearchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(PLANNED_OBLI_ID, "in", plannedObligationIdList));
        List<PlannedObligationDTO> plannedObligationDTOList = TransactionIdUtil.queryList(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATION_BY_CRITERIA + QUE_TENANT_ID + tenantId, HttpMethod.POST, token, plannedObligationsSearchCriteria, PlannedObligationDTO.class);
        if(!plannedObligationIdList.isEmpty()) {
            var physicalTradePlan= new PhysicalTradePlanningDTO();
            if(canCreatePlan){
                physicalTradePlan = createPlanningObjectForInterCompany(plannedObligationDTOList,actualizeObjV2.getPlanId(),actualizeObjV2.getPlannedQuantity(),plannedObligationIdList,tenantId,token);
            } else {
                physicalTradePlan = new PhysicalTradePlanningDTO();
            }
            preActualizationPayloadDto.getInterCompanyPlanningList().add(physicalTradePlan);
            PhysicalTradePlanningDTO finalPhysicalTradePlan = physicalTradePlan;
            return plannedObligationDTOList.stream().map(obligation -> {
                var quantityRow = new ActualizeObjV2();
                BeanUtils.copyProperties(actualizeObjV2,quantityRow);
                quantityRow.setLoadQuantity(actualizeObjV2.getLoadQuantity());
                quantityRow.setUnLoadQuantity(actualizeObjV2.getUnLoadQuantity());
                quantityRow.setPlanId(obligation.getPlanId());
                if(finalPhysicalTradePlan != null) {
                    quantityRow.setPlanId(finalPhysicalTradePlan.getPlanningId());
                }
                quantityRow.setMatchType(BACK2BACK);
                quantityRow.setQualityDetails(new ArrayList<>());
                quantityRow.setPackingDetails(new ArrayList<>());
                quantityRow.setTradeId(obligation.getTradeId());
                quantityRow.setPlannedObligationId(obligation.getPlannedObligationId());
                quantityRow.setIsInterCompanyTrade(true);
                return quantityRow;
            }).toList();
        }
        return new ArrayList<>();
    }

    @SneakyThrows
    public ReturnStatus actualizeQuantity (List<ActualizeObjV2> actualizeObjList, User principal, String token){
        PreActualizationPayloadDto preActualizationPayloadDto = new PreActualizationPayloadDto();
        actualizationServiceV2.actualizeQuantityObjects(actualizeObjList,principal,token,preActualizationPayloadDto);
        preActualizationPayloadDto.getActualizedQuantityObligationsDTOListForEOD().forEach(actualizedQuantityObligationsDTO -> platformQueueService.sendObject(EXCHANGE_NAME, QUANTITY_ACTUALIZATION, actualizedQuantityObligationsDTO));
        return ReturnStatus.successInstance("Actualization Done Successfully");
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void actualizeQuantityObjects(List<ActualizeObjV2> actualizeObjList, User principal, String token, PreActualizationPayloadDto preActualizationPayloadDto) throws TaomishError {
        var interCompanyList = new ArrayList<ActualizeObjV2>();
        var intercompanyTradeList = actualizeObjList.stream().filter(item -> item.getTradeSettlementReference() != null).toList();
        for (ActualizeObjV2 plannedObligationDTO: intercompanyTradeList){
            interCompanyList.addAll(getActualizedObjectListForInterCompanyTrades(principal.getTenantId(),plannedObligationDTO,token,preActualizationPayloadDto,true));
        }
        actualizeObjList.addAll(interCompanyList);
        List<String> plannedObligationIds = actualizeObjList.stream()
                .map(ActualizeObjV2::getPlannedObligationId)
                .filter(Objects::nonNull)
                .toList();
        var plannedObligationsSearchCriteria = new ArrayList<com.taomish.common.searchcriteria.SearchCriteria>();
        plannedObligationsSearchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(TEN_ANT_ID, EQUA_LS, principal.getTenantId()));
        plannedObligationsSearchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(PLANNED_OBLI_ID, "in", plannedObligationIds));
        List<PlannedObligationDTO> plannedObligationDTOList = TransactionIdUtil.queryList(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATION_BY_CRITERIA + QUE_TENANT_ID + principal.getTenantId(), HttpMethod.POST, token, plannedObligationsSearchCriteria, PlannedObligationDTO.class);
        var errors = canActualize(plannedObligationDTOList,actualizeObjList);
        if (!errors.isEmpty()) {
            throw new TaomishError("Actualization failed with below errors : " + String.join(",", errors));
        }

        for (ActualizeObjV2 actualizeObjV2 : actualizeObjList) {
            PlannedObligationDTO plannedObligationDTO = plannedObligationDTOList.stream().filter(e->(e.getPlannedObligationId().equalsIgnoreCase(actualizeObjV2.getPlannedObligationId()))).toList().getFirst();
            grabPayloadForQuantityActualization(actualizeObjV2, preActualizationPayloadDto,plannedObligationDTO, token, principal);
        }

        try {
            actualizationQuantityRepo.saveAll(preActualizationPayloadDto.getActualizedQuantityObligationsList());
            billOfLandingRepo.saveAll(preActualizationPayloadDto.getBlRecords());
            actualPackingDetailsRepo.saveAll(preActualizationPayloadDto.getActualPackingDetails());
            actualQualityRepo.saveAll(preActualizationPayloadDto.getQualities());
            preActualizationPayloadDto.getObligationIds().forEach(item -> TransactionIdUtil.setPlannedObligationStatesToTrue(baseUrl,item,principal.getTenantId(),token,PlannedObligationState.ACTUALIZED));
            var saveCashflowResult = TransactionIdUtil.queryList(baseUrl+CASHFLOW_ROOT + SAVE_ALl_CASHFLOW + QUERY+ TENANT_ID_EQ + principal.getTenantId(),HttpMethod.POST,token,preActualizationPayloadDto.getCashflowDataDTOList(),CashflowDataDTO.class);
            if(saveCashflowResult == null) {
                throw new TaomishError("Failed to save cashflow");
            }
        } catch (Exception e) {
            if(!preActualizationPayloadDto.getInterCompanyPlanningList().isEmpty()) {
                preActualizationPayloadDto.getInterCompanyPlanningList().forEach(plan ->  TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + DELETE_TRADE_PLAN + QUERY+TENANT_ID_EQ + principal.getTenantId() + "&planUuid=" + plan.getUuid(), HttpMethod.GET, token, null, String.class));
            }
            preActualizationPayloadDto.getObligationIds().forEach(item -> TransactionIdUtil.setPlannedObligationStatesToFalse(baseUrl,item,principal.getTenantId(),token,PlannedObligationState.ACTUALIZED));
            throw new TaomishError("Failed during actualization",e);
        }
        try {
            addAutoCosts(actualizeObjList, token, principal.getTenantId());
        } catch (Exception e) {
            logger.error("Failed to add cost while actualization.",e);
        }
    }

    @SneakyThrows
    private void grabPayloadForQuantityActualization(ActualizeObjV2 actualizeObjV2,
                                                     PreActualizationPayloadDto preActualizationPayloadDto, PlannedObligationDTO plannedObligation,
                                                     String token, User principal) {
        ActualizedQuantityObligations actualizedQuantityObligationsOld = actualizationQuantityRepo.findByPlannedObligationIdAndSplitSequenceNumberAndTenantIdOrderBySplitSequenceNumberAsc(plannedObligation.getPlannedObligationId(), actualizeObjV2.getSplitSequenceNumber(), principal.getTenantId());
        if (actualizedQuantityObligationsOld != null && plannedObligation.getObligationState().get(PlannedObligationState.ACTUALIZED)) {
            return;
        }
        ActualizedQuantityObligations actualizedQuantityObligations = new ActualizedQuantityObligations();
        if(actualizedQuantityObligationsOld != null){
            BeanUtils.copyProperties(actualizedQuantityObligationsOld,actualizedQuantityObligations);
        }else{
            String actualizationQuantityId = transactionIDGenerator.generateId( "actualizationQuantityId", plannedObligation, principal.getTenantId(), token,false,"",false);
            if (actualizationQuantityId == null) {
                throw new TaomishError("Failed to generate Actualization ID");
            }
            actualizedQuantityObligations.setActualizedQuantityId(actualizationQuantityId);
            actualizedQuantityObligations.setActualizationId(actualizationQuantityId);
        }
        actualizedQuantityObligations.setSplitSequenceNumber(actualizeObjV2.getSplitSequenceNumber());
        actualizedQuantityObligations.setAdjusted(actualizeObjV2.getAdjusted());
        actualizedQuantityObligations.setLossGainQuantity(actualizeObjV2.getLossGainQuantity());
        actualizedQuantityObligations.setDischargeDate(actualizeObjV2.getDischargeDate());
        actualizedQuantityObligations.setPlannedObligationId(plannedObligation.getPlannedObligationId());
        actualizedQuantityObligations.setPlannedObligationType(plannedObligation.getTradeTransactionType());
        actualizedQuantityObligations.setTradeId(plannedObligation.getTradeId());
        if (actualizeObjV2.getSplitSequenceNumber() == 0) {
            actualizedQuantityObligations.setPlannedQuantity(plannedObligation.getPlannedQuantity());
        } else {
            actualizedQuantityObligations.setPlannedQuantity(actualizeObjV2.getLoadQuantity());
        }
        actualizedQuantityObligations.setBalanceAllocateQuantity(actualizeObjV2.getLoadQuantity());
        actualizedQuantityObligations.setLoadQuantity(actualizeObjV2.getLoadQuantity());
        actualizedQuantityObligations.setUnloadQuantity(actualizeObjV2.getUnLoadQuantity());
        if(actualizeObjV2.getPackageType() != null) {
            if (actualizeObjV2.getPackageType().equals(PACKAGE_TYPE_CONTAINER)) {
                actualizedQuantityObligations.setExternalPackage(actualizeObjV2.getExternalPackage());
                actualizedQuantityObligations.setExternalPackageUnit(actualizeObjV2.getActualExternalPackage());
                actualizedQuantityObligations.setPlannedExternalPackageUnit(plannedObligation.getNoOfUnits());
            } else if (actualizeObjV2.getPackageType().equals(PACKAGE_TYPE_UNIT)) {
                actualizedQuantityObligations.setInternalPackage(actualizeObjV2.getInternalPackage());
                actualizedQuantityObligations.setInternalPackageUnit(actualizeObjV2.getActualInternalPackage());
                actualizedQuantityObligations.setPlannedInternalPackageUnit(plannedObligation.getNoOfUnits());
            }
        }

        actualizedQuantityObligations.setBrand(plannedObligation.getBrand());
        actualizedQuantityObligations.setGrade(plannedObligation.getGrade());
        actualizedQuantityObligations.setOrigin(plannedObligation.getCropOrigin());
        actualizedQuantityObligations.setCommodity(plannedObligation.getCommodity());
        actualizedQuantityObligations.setQuantityUom(plannedObligation.getQuantityUOM());
        actualizedQuantityObligations.setTenantId(principal.getTenantId());

        logger.info("Saving Quantity Actualization for ObligationId: {}" , actualizedQuantityObligations.getPlannedObligationId());
        preActualizationPayloadDto.getActualizedQuantityObligationsList().add(actualizedQuantityObligations);

        manageSaveBl(actualizedQuantityObligations,preActualizationPayloadDto,plannedObligation,actualizeObjV2,principal);
        manageQuality(actualizedQuantityObligations,preActualizationPayloadDto,actualizeObjV2,principal);
        managePackingDetails(actualizedQuantityObligations,preActualizationPayloadDto,actualizeObjV2,principal);

        logger.info("Publishing Rabbit message to update Actulaization data in EOD service for obligationID: {}" , actualizedQuantityObligations.getPlannedObligationId());
        var actualizedQuantityObligationsDTO = new ActualizedQuantityObligationsDTO();
        actualizedQuantityObligationsDTO.setPlannedObligationId(actualizedQuantityObligations.getPlannedObligationId());
        actualizedQuantityObligationsDTO.setLoadQuantity(actualizedQuantityObligations.getLoadQuantity());
        actualizedQuantityObligationsDTO.setBrand(actualizedQuantityObligations.getBrand());
        actualizedQuantityObligationsDTO.setGrade(actualizedQuantityObligations.getGrade());
        actualizedQuantityObligationsDTO.setOrigin(actualizedQuantityObligations.getOrigin());
        actualizedQuantityObligationsDTO.setUnloadQuantity(actualizedQuantityObligations.getUnloadQuantity());
        actualizedQuantityObligationsDTO.setTenantId(principal.getTenantId());
        actualizedQuantityObligationsDTO.setBalanceAllocateQuantity(actualizedQuantityObligations.getLoadQuantity());
        preActualizationPayloadDto.getActualizedQuantityObligationsDTOListForEOD().add(actualizedQuantityObligationsDTO);

        try {
            preActualizationPayloadDto.getObligationIds().add(plannedObligation.getPlannedObligationId());
        } catch (Exception e) {
            throw new TaomishError("Updating planned obligation failed");
        }
        if (!plannedObligation.isExternalRecord()) {
            var cashflowDto = actualizationCashflowServiceV2.getActualizeCashflowDTO(actualizedQuantityObligations,plannedObligation,principal.getTenantId(),token);
            preActualizationPayloadDto.getCashflowDataDTOList().add(cashflowDto);
            preActualizationPayloadDto.getCashflowDataDTOList().addAll(getPricingCashflowToActualize(plannedObligation,principal,token));
        }
        logger.info("Quantity actualization is done for planned Obligation id : {}" , plannedObligation.getPlannedObligationId());
    }

    private Collection<? extends CashflowDataDTO> getPricingCashflowToActualize(PlannedObligationDTO plannedObligation, User principal, String token) {
        if(List.of(PTBF.toLowerCase(),DIFFERENTIAL.toLowerCase()).contains(plannedObligation.getPriceType().toLowerCase()) && Boolean.FALSE.equals(plannedObligation.getObligationState().get(PlannedObligationState.PRICED))) {
            var cashflowCriteria = new SpecBuilderUtil().with(principal)
                    .addCriteria(new com.taomish.common.searchcriteria.SearchCriteria("plannedObligationId","equals",plannedObligation.getPlannedObligationId()))
                    .addCriteria(new com.taomish.common.searchcriteria.SearchCriteria("cashflowStatus","equals",ACTIVE))
                    .addCriteria(new com.taomish.common.searchcriteria.SearchCriteria("priceStatus","equals",PRICED))
                    .addCriteria(new com.taomish.common.searchcriteria.SearchCriteria("stage","in",List.of(ESTIMATED,PLANNED)))
                    .getCriteriaList();
            var cashflows =TransactionIdUtil.queryCashflows(baseUrl,token,cashflowCriteria,false).stream().filter(item->!List.of(COST.toLowerCase(),CHARGES.toLowerCase()).contains(item.getType().toLowerCase())).toList();
            for (CashflowDataDTO cashflow : cashflows) {
                cashflow.setQuantityStatus(ACTUAL);
                cashflow.setStage(ACCRUED);
            }
            return cashflows;
        }
        return new ArrayList<>();
    }

    private void manageQuality(ActualizedQuantityObligations actualizedQuantityObligations,PreActualizationPayloadDto preActualizationPayloadDto, ActualizeObjV2 actualizeObjV2, User principal) {
        List<ActualQuality> actualQualities;
        actualQualities =  streamUtil.mapToList(actualizeObjV2.getQualityDetails()!= null?actualizeObjV2.getQualityDetails():new ArrayList<>(),ActualQuality.class);
        actualQualities.forEach(e -> {
            e.setActualizationId(actualizedQuantityObligations.getActualizationId());
            e.setTenantId(principal.getTenantId());
        });
        if(preActualizationPayloadDto == null) {
            actualQualityRepo.saveAll(actualQualities);
        } else {
            preActualizationPayloadDto.getQualities().addAll(actualQualities);
        }
    }
    private void managePackingDetails(ActualizedQuantityObligations actualizedQuantityObligations,PreActualizationPayloadDto preActualizationPayloadDto, ActualizeObjV2 actualizeObjV2, User principal) {
        List<ActualPackingDetails> actualPackingDetails;
        actualPackingDetails =  streamUtil.mapToList(actualizeObjV2.getPackingDetails()!= null?actualizeObjV2.getPackingDetails():new ArrayList<>(),ActualPackingDetails.class);
        actualPackingDetails.forEach(e -> {
            e.setActualizationId(actualizedQuantityObligations.getActualizationId());
            e.setTenantId(principal.getTenantId());
        });
        if(preActualizationPayloadDto == null) {
            actualPackingDetailsRepo.saveAll(actualPackingDetails);
        } else {
            preActualizationPayloadDto.getActualPackingDetails().addAll(actualPackingDetails);
        }
    }

    @SneakyThrows
    private void manageSaveBl(ActualizedQuantityObligations actualizedQuantityObligations,PreActualizationPayloadDto preActualizationPayloadDto, PlannedObligationDTO plannedObligation, ActualizeObjV2 actualizeObjV2, User principal) {
        var plannedObligationId = actualizedQuantityObligations.getPlannedObligationId();
        var splitSequenceNumber = actualizedQuantityObligations.getSplitSequenceNumber();

        BillOfLanding billOfLandingRow = billOfLandingRepo.findByPlannedObligationIdAndSplitSequenceNumberAndTenantId(plannedObligationId, splitSequenceNumber, principal.getTenantId());
        if(billOfLandingRow == null) {
            billOfLandingRow = new BillOfLanding();
        }
        billOfLandingRow.setPlannedObligationId(plannedObligationId);
        billOfLandingRow.setActualQuantity(actualizedQuantityObligations.getLoadQuantity());
        billOfLandingRow.setUom(plannedObligation.getQuantityUOM());
        billOfLandingRow.setActualizationId(actualizedQuantityObligations.getActualizationId());
        billOfLandingRow.setSurrendered(false);
        billOfLandingRow.setTenantId(principal.getTenantId());
        billOfLandingRow.setSplitSequenceNumber(splitSequenceNumber);
        billOfLandingRow.setBlDate(actualizeObjV2.getBlDate());
        billOfLandingRow.setBlNumber(actualizeObjV2.getBlNumber());
        billOfLandingRow.setConsignee(actualizeObjV2.getConsignee());
        billOfLandingRow.setShipper(actualizeObjV2.getShipper());
        billOfLandingRow.setStowageNo(actualizeObjV2.getStowageNo());
        billOfLandingRow.setNotifyParty(actualizeObjV2.getNotifyParty());
        billOfLandingRow.setForwardingAgent(actualizeObjV2.getForwardingAgent());
        billOfLandingRow.setShipOnboard(actualizeObjV2.getShipOnboard());
        billOfLandingRow.setSelfToOrder(actualizeObjV2.getSelfToOrder());
        billOfLandingRow.setCustomFields(actualizeObjV2.getCustomFields());
        billOfLandingRow.setNorDate(actualizeObjV2.getNorDate());
        billOfLandingRow.setAssignmentSurveyor(actualizeObjV2.getAssignmentSurveyor());
        billOfLandingRow.setImportLicenceNo(actualizeObjV2.getImportLicenceNo());
        billOfLandingRow.setConsignmentNo(actualizeObjV2.getConsignmentNo());
        billOfLandingRow.setFlag(actualizeObjV2.getFlag());
        billOfLandingRow.setMaster(actualizeObjV2.getMaster());
        billOfLandingRow.setCharterDate(actualizeObjV2.getCharterDate());
        preActualizationPayloadDto.getBlRecords().add(billOfLandingRow);
    }

    @SneakyThrows
    @Deprecated
    public PlannedObligationDTO updateActualizedStatus(PlannedObligationDTO plannedObligationDTO, String token, String tenantId) {
        plannedObligationDTO.getObligationState().put(PlannedObligationState.ACTUALIZED, true);
        return TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + UPDATE_TRADE_PLANOBLIGATION + QUE_TENANT_ID + tenantId, HttpMethod.POST, token, plannedObligationDTO, PlannedObligationDTO.class);
    }

    @SneakyThrows
    @Deprecated
    private void createQuantityCashFlow(double loadQuantity, double unloadQuantity, PlannedObligationDTO plannedObligation,
                                        boolean isEdit, String token,
                                        String tenantId, double splitSequenceNumber) throws Exception {
        double quantity;
        String type;
        PhysicalTradeDTO trade = TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_ROOT + GET_PHYSICAL_TRADE_BY_TRADE_ID +
                QUE_TENANT_ID + tenantId +AND_TRADEID+ plannedObligation.getTradeId() , HttpMethod.GET, token, null, PhysicalTradeDTO.class);
        PhysicalTradePlanningDTO physicalTradePlanningDTO = TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_PLANNING_ROOT + GET_PHYSICAL_TRADE_PLANNING_BY_PLAN_ID + QUE_TENANT_ID + tenantId + AND+PLAN_ID_EQ + plannedObligation.getPlanId(), HttpMethod.GET, token, null, PhysicalTradePlanningDTO.class);
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

        PriceAllocationCheckDTO priceAllocationCheckDTO = TransactionIdUtil.query(baseUrl + ADVANCE_ALLOCATION_ROOT + GET_PRICE_ALLOCATED_CHECK_BY_PLANNED_OBLIGATION_ID + QUE_OBLIGATION_ID + plannedObligation.getPlannedObligationId() + AND_TENANT_ID + tenantId, HttpMethod.GET, token, null, PriceAllocationCheckDTO.class);
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
        updateCashflowDTO.setObligationQuantity(quantity);
        CashflowDataDTO cashflowDataDTO = null;
        List<CashflowDataDTO> cashflowDataDTOList = new ArrayList<>();
        try {
            if(isEdit){
                updateCashflowDTO.setPlannedObligationId(plannedObligation.getPlannedObligationId());
                actualizationCashflowService.runUpdateCashflow(tenantId,updateCashflowDTO, token );
            }else{
                cashflowDataDTO = actualizationCashflowService.runCreatCashflows(updateCashflowDTO, token, tenantId);
            }
        } catch (Exception e) {
            throw new TaomishError("Cash-flow creation failed !",e);
        }
        try {
            updateCashflow(plannedObligation.getPlannedObligationId(), trade, quantity, plannedObligation.getPlannedObligationId(), plannedObligation.getPlanId(), plannedObligation.getPlannedQuantity(), null, token, tenantId, updateCashflowDTO.getTradePrice(), plannedObligation, splitSequenceNumber);
        } catch (Exception e) {
            cashflowDataDTOList.add(cashflowDataDTO);
            TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + CASHFLOW_DELETE_ALL + QUE_TENANT_ID + tenantId, HttpMethod.POST, token, cashflowDataDTOList, Object.class);
            throw new TaomishError("Cash-flow updation failed",e);
        }
    }
    private String getWashoutCashflowType(String tenantId, PlannedObligationDTO plannedObligation, String token) {
        String cashflowType = "";
        List<SearchCriteria> searchCriteriaList = new ArrayList<>();
        searchCriteriaList.add(new SearchCriteria(TEN_ANT_ID, EQUA_LS, tenantId));
        searchCriteriaList.add(new SearchCriteria(PLANNED_OBLI_ID, EQUA_LS, plannedObligation.getPlannedObligationId()));
        searchCriteriaList.add(new SearchCriteria("cashflowStatus", EQUA_LS, ACTIVE));
        searchCriteriaList.add(new SearchCriteria("type", "in", List.of(Trade)));
        searchCriteriaList.add(new SearchCriteria(STAGE, EQUA_LS, PLANNED));
        CashflowDataDTO[] cashflowDataDTOSForWashout = TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaList, CashflowDataDTO[].class);
        if (cashflowDataDTOSForWashout != null && cashflowDataDTOSForWashout.length > 0) {
            CashflowDataDTO cashflowDataDTO = cashflowDataDTOSForWashout[0];
            if (StringUtils.isEmpty(cashflowDataDTO.getFxRate())) {
                cashflowDataDTO.setFxRate(1);
            }
            cashflowType = Trade;
        }
        return cashflowType;
    }

    public List<BLActualizationDTO> getBL(String planID,String vesselId, User principal,String token) {
        List<PlannedObligationDTO> plannedObligationDTOList = null;
        if(vesselId.isEmpty()){
            plannedObligationDTOList = TransactionIdUtil.queryList(baseUrl+PLANNED_OBLIGATION_ROOT+GET_PLANNED_OBLIGATION_FOR_ACTUALIZATION+QUE_TENANT_ID+principal.getTenantId()+AND+PLAN_ID_EQ+planID,HttpMethod.GET,token,null, PlannedObligationDTO.class);
        }else{
            plannedObligationDTOList = TransactionIdUtil.queryList(baseUrl+TRANSPORT_ALLOCATION_V2_ROOT+GET_ALLOCATED_PLANNED_OBLIGATION_SEA_FREIGHT_FOR_TRANSPORT_ACTUALIZATION_V2+QUE_TENANT_ID+principal.getTenantId()+AND+PLAN_ID_EQ+planID+AND+"vesselId="+vesselId,HttpMethod.GET,token,null, PlannedObligationDTO.class);
        }
        List<BLActualizationDTO> blActualizationDTOS = new ArrayList<>();
        for(PlannedObligationDTO plannedObligationDTO:plannedObligationDTOList){
            var actualQuality = streamUtil.mapToList(actualQualityRepo.findAllByPlannedObligationIdAndTenantId(plannedObligationDTO.getPlannedObligationId(),principal.getTenantId()), ActualQualityDTO.class);
            var actualPackingDetails = streamUtil.mapToList(actualPackingDetailsRepo.findAllByPlannedObligationIdAndTenantId(plannedObligationDTO.getPlannedObligationId(),principal.getTenantId()), ActualPackingDetailsDTO.class);
            boolean finalInvoice = Arrays.asList(INVOICE_FINAL_PROVISIONAL, INVOICE_GENERATED).contains(TransactionIdUtil.getInvoiceStatus(baseUrl,plannedObligationDTO.getPlannedObligationId(),plannedObligationDTO.getTradeId(),principal.getTenantId(),token,plannedObligationDTO.getSplitSequenceNumber()));
            var initialInvoiced = plannedObligationDTO.getObligationState().get(PlannedObligationState.INITIAL_INVOICE) ||
                    (plannedObligationDTO.getObligationState().get(PlannedObligationState.DOCBYPASS_BOTH) && plannedObligationDTO.getObligationState().get(PlannedObligationState.DOCBYPASS_CNDN) && plannedObligationDTO.getObligationState().get(PlannedObligationState.DOCBYPASS_COMMERCIAL))||
                    (!plannedObligationDTO.getObligationState().get(PlannedObligationState.DOCBYPASS_BOTH) && plannedObligationDTO.getObligationState().get(PlannedObligationState.DOCBYPASS_COMMERCIAL)) ;
            var finalInvoiced = plannedObligationDTO.getObligationState().get(PlannedObligationState.FINAL_INVOICE) || plannedObligationDTO.getObligationState().get(PlannedObligationState.DOCBYPASS_FINAL);
            var canClaim = initialInvoiced && !finalInvoiced;
            if(plannedObligationDTO.getActualizeQuantityRows()==null || plannedObligationDTO.getActualizeQuantityRows().isEmpty() ){
                BLActualizationDTO blActualizationDTO = new BLActualizationDTO();
                blActualizationDTO.setPlanId(plannedObligationDTO.getPlanId());
                blActualizationDTO.setClaimedQuantity(0.0);
                blActualizationDTO.setPlannedObligationId(plannedObligationDTO.getPlannedObligationId());
                blActualizationDTO.setPlannedQuantity(plannedObligationDTO.getPlannedQuantity());
                blActualizationDTO.setTradeId(plannedObligationDTO.getTradeId());
                blActualizationDTO.setTradeTransactionType(plannedObligationDTO.getTradeTransactionType());
                blActualizationDTO.setUom(plannedObligationDTO.getQuantityUOM());
                blActualizationDTO.setTradeSettlementReference(plannedObligationDTO.getTradeSettlementReference());
                blActualizationDTO.setPackageType(plannedObligationDTO.getPackageType());
                blActualizationDTO.setBalanceQuantity(plannedObligationDTO.getBalanceQuantity());
                blActualizationDTO.setPackageCapacity(plannedObligationDTO.getPackageCapacity());
                blActualizationDTO.setPackageUom(plannedObligationDTO.getPackageUom());
                blActualizationDTO.setNoOfUnits(plannedObligationDTO.getNoOfUnits());
                blActualizationDTO.setContractTerm(plannedObligationDTO.getContractTerm());
                blActualizationDTO.setActualQuantity(plannedObligationDTO.getActualizedQuantity());
                blActualizationDTO.setSplitQuantity(plannedObligationDTO.getSplitQuantity());
                blActualizationDTO.setSplitSequenceNumber(plannedObligationDTO.getSplitSequenceNumber());
                blActualizationDTO.setExternalPackage(plannedObligationDTO.getExternalPackage());
                blActualizationDTO.setInternalPackage(plannedObligationDTO.getInternalPackage());
                blActualizationDTO.setLoadQuantity(plannedObligationDTO.getLoadQuantity());
                blActualizationDTO.setUnLoadQuantity(plannedObligationDTO.getUnLoadQuantity());
                blActualizationDTO.setMatchType(plannedObligationDTO.getMatchType());
                blActualizationDTO.setAdjusted(plannedObligationDTO.getAdjusted());
                blActualizationDTO.setLossGainQuantity(plannedObligationDTO.getLossGainQuantity());
                blActualizationDTO.setBlSplitRows(plannedObligationDTO.getBlSplitRows());
                blActualizationDTO.setCreatedBy(plannedObligationDTO.getCreatedBy());
                blActualizationDTO.setUpdatedBy(plannedObligationDTO.getUpdatedBy());
                blActualizationDTO.setUuid(plannedObligationDTO.getUuid());
                blActualizationDTO.setCounterparty(plannedObligationDTO.getCounterparty());
                blActualizationDTO.setTradeQuantity(plannedObligationDTO.getTradeQuantity());
                blActualizationDTO.setObligationState(plannedObligationDTO.getObligationState());
                blActualizationDTO.setDischargeDate(plannedObligationDTO.getDischargeDate());
                blActualizationDTO.setToleranceValue(plannedObligationDTO.getToleranceValue());
                blActualizationDTO.setCommodity(plannedObligationDTO.getCommodity());
                blActualizationDTO.setFinalInvoiced(finalInvoice);
                blActualizationDTO.setQualityDetails(actualQuality);
                blActualizationDTO.setTradeId(plannedObligationDTO.getTradeId());
                blActualizationDTO.setInvoiceClaim(canClaim);
                blActualizationDTO.setPackageType(plannedObligationDTO.getPackageType());
                blActualizationDTO.setExternalPackageUnit(plannedObligationDTO.getExternalPackageUnit());
                blActualizationDTO.setInternalPackageUnit(plannedObligationDTO.getInternalPackageUnit());
                blActualizationDTO.setSettlementToleranceValue(plannedObligationDTO.getSettlementToleranceValue());
                blActualizationDTO.setPackingDetails(actualPackingDetails);
                blActualizationDTO.setTradeDateTime(plannedObligationDTO.getTradeDateTime());
                blActualizationDTOS.add(blActualizationDTO);
            }else{
                for(ActualizedQuantityObligationsDTO actualizedQuantityObligationsDTO:plannedObligationDTO.getActualizeQuantityRows()){
                    BLActualizationDTO blActualizationDTO = new BLActualizationDTO();
                    blActualizationDTO.setPlanId(plannedObligationDTO.getPlanId());
                    blActualizationDTO.setClaimedQuantity(actualizedQuantityObligationsDTO.getClaimedQuantity());
                    blActualizationDTO.setPlannedObligationId(actualizedQuantityObligationsDTO.getPlannedObligationId());
                    blActualizationDTO.setPlannedQuantity(plannedObligationDTO.getPlannedQuantity());
                    blActualizationDTO.setTradeId(plannedObligationDTO.getTradeId());
                    blActualizationDTO.setTradeTransactionType(plannedObligationDTO.getTradeTransactionType());
                    blActualizationDTO.setBalanceQuantity(plannedObligationDTO.getBalanceQuantity());
                    blActualizationDTO.setUom(plannedObligationDTO.getQuantityUOM());
                    blActualizationDTO.setTradeSettlementReference(plannedObligationDTO.getTradeSettlementReference());
                    blActualizationDTO.setPackageType(actualizedQuantityObligationsDTO.getPackageType());
                    blActualizationDTO.setPackageCapacity(plannedObligationDTO.getPackageCapacity());
                    blActualizationDTO.setPackageUom(plannedObligationDTO.getPackageUom());
                    blActualizationDTO.setNoOfUnits(plannedObligationDTO.getNoOfUnits());
                    blActualizationDTO.setContractTerm(plannedObligationDTO.getContractTerm());
                    blActualizationDTO.setActualQuantity(actualizedQuantityObligationsDTO.getLoadQuantity());
                    blActualizationDTO.setSplitQuantity(actualizedQuantityObligationsDTO.getLoadQuantity());
                    blActualizationDTO.setSplitSequenceNumber(actualizedQuantityObligationsDTO.getSplitSequenceNumber());
                    blActualizationDTO.setExternalPackage(plannedObligationDTO.getExternalPackage());
                    blActualizationDTO.setInternalPackage(actualizedQuantityObligationsDTO.getInternalPackage());
                    blActualizationDTO.setLoadQuantity(actualizedQuantityObligationsDTO.getLoadQuantity());
                    blActualizationDTO.setUnLoadQuantity(actualizedQuantityObligationsDTO.getUnloadQuantity());
                    blActualizationDTO.setMatchType(plannedObligationDTO.getMatchType());
                    blActualizationDTO.setAdjusted(actualizedQuantityObligationsDTO.getAdjusted());
                    blActualizationDTO.setLossGainQuantity(actualizedQuantityObligationsDTO.getLossGainQuantity());
                    blActualizationDTO.setCreatedBy(plannedObligationDTO.getCreatedBy());
                    blActualizationDTO.setUpdatedBy(plannedObligationDTO.getUpdatedBy());
                    blActualizationDTO.setObligationState(plannedObligationDTO.getObligationState());
                    blActualizationDTO.setCounterparty(plannedObligationDTO.getCounterparty());
                    blActualizationDTO.setTradeQuantity(plannedObligationDTO.getTradeQuantity());
                    blActualizationDTO.setUuid(UUID.randomUUID());
                    blActualizationDTO.setBlSplitRows(plannedObligationDTO.getBlSplitRows().stream().filter(e->e.getSplitSequenceNumber().equals(actualizedQuantityObligationsDTO.getSplitSequenceNumber())).toList());
                    blActualizationDTO.setToleranceValue(plannedObligationDTO.getToleranceValue());
                    blActualizationDTO.setDischargeDate(actualizedQuantityObligationsDTO.getDischargeDate());
                    blActualizationDTO.setCommodity(plannedObligationDTO.getCommodity());
                    blActualizationDTO.setFinalInvoiced(finalInvoice);
                    blActualizationDTO.setQualityDetails(actualQuality);
                    blActualizationDTO.setTradeId(plannedObligationDTO.getTradeId());
                    blActualizationDTO.setInvoiceClaim(canClaim);
                    blActualizationDTO.setPackageType(plannedObligationDTO.getPackageType());
                    blActualizationDTO.setExternalPackageUnit(plannedObligationDTO.getExternalPackageUnit());
                    blActualizationDTO.setInternalPackageUnit(plannedObligationDTO.getInternalPackageUnit());
                    blActualizationDTO.setSettlementToleranceValue(plannedObligationDTO.getSettlementToleranceValue());
                    blActualizationDTO.setPackingDetails(actualPackingDetails);
                    blActualizationDTO.setTradeDateTime(plannedObligationDTO.getTradeDateTime());
                    blActualizationDTOS.add(blActualizationDTO);
                }
            }
        }
        return blActualizationDTOS;
    }

    @SneakyThrows
    protected void updateCashflow(String obligationId, PhysicalTradeDTO trade, double quantity, String plannedObligationId, String planId, double plannedQuantity, PriceAllocationDTO priceAllocationDTOS, String token, String tenantId, double averagePrice, PlannedObligationDTO plannedObligationDTO,double splitSequenceNumber) {
        double totalAmount;
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
        updateCashflowDTO.setSplitSequenceNumber(splitSequenceNumber);
        updateCashflowDTO.setType(Trade);
        actualizationCashflowService.runUpdateCashflow(tenantId, updateCashflowDTO, token);
    }

    public Page<PlannedTrades> findAllPlannedTradesByCriteria(Set<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaList, int page, int size, User principal) {

        var finalCriteriaList = searchCriteriaList.stream().peek(item -> {
            if(item.getFieldName().equalsIgnoreCase("tradeTransactionType")) {
                if(item.getCondition().equalsIgnoreCase("equals")){
                    item.setValue(item.getValue().toString().equalsIgnoreCase("BUY") ? 0 : 1);
                }
                else{
                    List<String> transactionTypeList = (List<String>) item.getValue();
                    String transactionType = transactionTypeList.get(0);
                    int value = transactionType.equalsIgnoreCase("BUY") ? 0 : 1;
                    item.setValue(List.of(value));
                }
            }
        }).toList();
        return new SpecBuilderUtil().with(principal.getTenantId())
                .setupPageDesc(page,size,"updatedTimestamp")
                .setCriteriaList(finalCriteriaList)
                .from(plannedTradesRepo).findPage(PlannedTrades.class);
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
        List<com.taomish.common.searchcriteria.SearchCriteria> invoiceCriteria = new ArrayList<>();
        invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(ActualizationConstants.TENANT_ID, EQ, tenantId));
        invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(PLANNED_OBLGN_ID, EQ, plannedObligationId));
        invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria("splitNumber", "in", List.of(0,splitNumber)));
        invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria("status", "notequals", VOID));
        return TransactionIdUtil.queryList(baseUrl+INVOICE_ROOT+GET_INVOICE_BY_CRITERIA,HttpMethod.POST,token,invoiceCriteria, InvoiceDTO.class);
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
            finalObject.setLoadQuantity(quantityObligationsList.getFirst().getLoadQuantity());
            finalObject.setUnloadQuantity(quantityObligationsList.getFirst().getUnloadQuantity());
            finalObject.setPlannedQuantity(quantityObligationsList.getFirst().getPlannedQuantity());
            finalObject.setQuantityUom(quantityObligationsList.getFirst().getQuantityUom());
            finalObject.setBrand(quantityObligationsList.getFirst().getBrand());
            finalObject.setGrade(quantityObligationsList.getFirst().getGrade());
            finalObject.setOrigin(quantityObligationsList.getFirst().getOrigin());
            finalObject.setCommodity(quantityObligationsList.getFirst().getCommodity());
            finalObject.setCreatedBy(quantityObligationsList.getFirst().getCreatedBy());
            finalObject.setPurpose(quantityObligationsList.getFirst().getPurpose());
            finalObject.setActualizationEventMapping(getEventList(quantityObligationsList.getFirst().getActualizationEventMapping()));
            finalObject.setClaimedQuantity(quantityObligationsList.getFirst().getClaimedQuantity());
            finalObject.setCreatedTimestamp(quantityObligationsList.getFirst().getCreatedTimestamp());
            finalObject.setUpdatedTimestamp(quantityObligationsList.getFirst().getUpdatedTimestamp());
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
        PhysicalTradeDTO tradeDTO = TransactionIdUtil.query(baseUrl + PHYSICAL_TRADE_ROOT + GET_PHYSICAL_TRADE_BY_TRADE_ID +
                        "?tradeId=" + plannedObligation.getTradeId() + "&tenantId=" + tenantId,
                HttpMethod.GET, token, null, PhysicalTradeDTO.class);
        finalObject.setTolerance(tradeDTO.getToleranceValue());
        finalObject.setPackageType(tradeDTO.getPackageType());
        if (!StringUtils.isEmpty(tradeDTO.getExternalPackage())) {
            finalObject.setExternalPackage(tradeDTO.getExternalPackage());
            finalObject.setExternalPackageUnit(tradeDTO.getExternalPackageUnit());
        }
        if (!StringUtils.isEmpty(tradeDTO.getInternalPackage())) {
            finalObject.setInternalPackage(tradeDTO.getInternalPackage());
            finalObject.setInternalPackageUnit(tradeDTO.getInternalPackageUnit());
        }
        return finalObject;
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

    private List<String> getInvoiceGeneratedForCost(String plannedObligationId, String costId, String tenantId, String token) throws Exception {
        List<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaList = new ArrayList<>();
        searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria(TEN_ANT_ID,"equals",tenantId));
        searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("plannedObligationId","equals",plannedObligationId));
        searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("cashflowStatus","equals",ACTIVE));
        searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("type","equals",CHARGES));
        searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("costId","equals",costId));
        var cashflows = TransactionIdUtil.queryCashflows(baseUrl,token,searchCriteriaList,false);
        return cashflows.stream().filter(item -> item.getInvoiceNumber() != null && !item.getInvoiceNumber().isEmpty()).map(CashflowDataDTO::getInvoiceNumber).toList();
    }

    @SneakyThrows
    public List<String> canDeactulizecost(String obligationId, String costId, User principal, String token){
            var errors = new ArrayList<String>();
        var invoiceList = getInvoiceGeneratedForCost(obligationId,costId,principal.getTenantId(),token);
        if(!invoiceList.isEmpty()) {
            errors.add("Invoices ("+String.join(",",invoiceList)+") is already generated. To De-Actualize you have to delete invoices first");
        }
        return errors;
    }


    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReturnStatus deActulizeCost(String obligationId,String costId,User principal,String token){
        var errors = canDeactulizecost(obligationId,costId,principal,token);
        if(!errors.isEmpty()) {
            return ReturnStatus.errorInstance("Failed to De-Actualize. Check below errors : \n\n" + String.join(",\n",errors));
        }

        List<ActualizedCost> actualizedCostList = new ArrayList<>();
        actualizedCostList  = actualizedCostRepo.findAllByTenantIdAndEstimatedCostIdAndPlannedObligationId(principal.getTenantId(), costId,obligationId);
        actualizedCostRepo.deleteAll(actualizedCostList);
        List<String> chargeIds  = actualizedCostList.stream().map(ActualizedCost::getActualizedCostId).toList();
        if(!chargeIds.isEmpty()){
            List<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaList = new ArrayList<>();
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("tenantId","equals",principal.getTenantId()));
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("plannedObligationId","equals",obligationId));
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("cashflowStatus","equals",ACTIVE));
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("costId","in",chargeIds));
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("type","equals",CHARGES));
            TransactionIdUtil.deleteCashflows(baseUrl,token,searchCriteriaList);
        }
      return ReturnStatus.successInstance("charges is successfully DeAcutlized");

    }


    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Deprecated
    public ReturnStatus deActualize(List<PlannedObligationDTO> obligationList, String token, String tenantId) throws TaomishError {
        List<ActualizedCost> actualizedCostList;
        List<ActualizedQuantityObligations> actualizedQuantityObligationsList;
        List<BillOfLanding> billOfLandingList;
        PlannedObligationDTO plannedObligationItem = null;
        List<String> tradeIds = new ArrayList<>();
        List<String> costIds = new ArrayList<>();
        List<String> plannedObligationIds = new ArrayList<>();
        List<PlannedObligationDTO> plannedObligationDTOList = new ArrayList<>();

        var errors = canDeactualize(obligationList,tenantId,token);
        if(!errors.isEmpty()) {
            return ReturnStatus.errorInstance("Failed to De-Actualize. Check below errors : \n\n" + String.join(",\n",errors));
        }

        boolean costDefunct = false;

        for (PlannedObligationDTO plannedObligation : obligationList) {
            actualizedCostList = actualizedCostRepo.findAllByTenantIdAndPlannedObligationId(tenantId, plannedObligation.getPlannedObligationId());
            List<ActualQuality> actualQualities = actualQualityRepo.findAllByPlannedObligationIdAndTenantId(plannedObligation.getPlannedObligationId(),tenantId);
            List<ActualPackingDetails> actualPackingDetails = actualPackingDetailsRepo.findAllByPlannedObligationIdAndTenantId(plannedObligation.getPlannedObligationId(),tenantId);
            actualizedQuantityObligationsList = actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationId(tenantId, plannedObligation.getPlannedObligationId());
            billOfLandingList = billOfLandingRepo.findAllByTenantIdAndPlannedObligationId(tenantId, plannedObligation.getPlannedObligationId());
            for (ActualizedCost actualizedCost : actualizedCostList) {
                costDefunct = true;
                actualizedCostRepo.delete(actualizedCost);
                tradeIds.add(plannedObligation.getTradeId());
                costIds.add(actualizedCost.getActualizedCostId());
                plannedObligationIds.add(plannedObligation.getPlannedObligationId());
            }
            actualQualityRepo.deleteAll(actualQualities);
            actualPackingDetailsRepo.deleteAll(actualPackingDetails);

            for (ActualizedQuantityObligations actualizedQuantityObligations : actualizedQuantityObligationsList) {
                tradeIds.add(plannedObligation.getTradeId());
                plannedObligationIds.add(plannedObligation.getPlannedObligationId());
            }
            actualizationQuantityRepo.deleteAll(actualizedQuantityObligationsList);
            billOfLandingRepo.deleteAll(billOfLandingList);

            plannedObligationItem = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT +
                            GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + QUE_OBLIGATION_ID + plannedObligation.getPlannedObligationId() + AND_TENANT_ID + tenantId,
                    HttpMethod.GET, token, null, PlannedObligationDTO.class);
            if(plannedObligationItem!=null){
                plannedObligationItem.getObligationState().put(PlannedObligationState.ACTUALIZED, false);
                plannedObligationDTOList.add(plannedObligationItem);
            }
        }
        List<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaListForDefunct;
        searchCriteriaListForDefunct = new ArrayList<>();
        if (costDefunct) {
            try {
                searchCriteriaListForDefunct.add(new com.taomish.common.searchcriteria.SearchCriteria(TEN_ANT_ID, EQUA_LS, tenantId));
                searchCriteriaListForDefunct.add(new com.taomish.common.searchcriteria.SearchCriteria("type", EQUA_LS, COST));
                searchCriteriaListForDefunct.add(new com.taomish.common.searchcriteria.SearchCriteria(STAGE, EQUA_LS, ACCRUED));
                searchCriteriaListForDefunct.add(new com.taomish.common.searchcriteria.SearchCriteria("tradeId", "in", tradeIds));
                if (!costIds.isEmpty()) {
                    searchCriteriaListForDefunct.add(new com.taomish.common.searchcriteria.SearchCriteria("costId", "in", costIds));
                }
                searchCriteriaListForDefunct.add(new com.taomish.common.searchcriteria.SearchCriteria(PLANNED_OBLI_ID, "in", plannedObligationIds));
                TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + DEFUNCT_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaListForDefunct, Object.class);
            } catch (Exception e) {
                logger.error("cashflow defunct failed reverting cashflow",e);
                throw new TaomishError("cashflow defunct failed reverting cashflow");
            }

        }

        var ids = plannedObligationDTOList.stream().map(PlannedObligationDTO::getPlannedObligationId).toList();
        List<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaListForCostDefunct;
        searchCriteriaListForCostDefunct = new ArrayList<>();

        try {
            TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + BULK_UPDATE_TRADE_PLANOBLIGATION + QUE_TENANT_ID + tenantId, HttpMethod.POST, token, plannedObligationDTOList, PlannedObligationDTO.class);
        } catch (Exception e) {
            logger.error("failed to deActualize",e);
            TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + ACTIVE_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaListForCostDefunct, Object.class);
            TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + ACTIVE_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaListForDefunct, Object.class);
        }
        /***
         * Need confirmation on why the cashflow defunct is happening on last planned obligation ID
         */
        List<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaListForDefunctValues;
        searchCriteriaListForDefunctValues = new ArrayList<>();
        searchCriteriaListForDefunctValues.add(new com.taomish.common.searchcriteria.SearchCriteria("cashflowStatus", EQUA_LS, ACTIVE));
        searchCriteriaListForDefunctValues.add(new com.taomish.common.searchcriteria.SearchCriteria(TEN_ANT_ID, EQUA_LS, tenantId));
        searchCriteriaListForDefunctValues.add(new com.taomish.common.searchcriteria.SearchCriteria(PLANNED_OBLI_ID, "in", ids));
        searchCriteriaListForDefunctValues.add(new com.taomish.common.searchcriteria.SearchCriteria("type", "in", Arrays.asList(Trade, CASHFLOW_TYPE_PROVISIONAL, CASHFLOW_TYPE_PREMIUM)));
        searchCriteriaListForDefunctValues.add(new com.taomish.common.searchcriteria.SearchCriteria(STAGE, "in", Arrays.asList(ACCRUED, ACCRUED_PROVISIONAL)));
        TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + DEFUNCT_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaListForDefunctValues, Object.class);

        try {
            TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + BULK_UPDATE_TRADE_PLANOBLIGATION + QUE_TENANT_ID + tenantId, HttpMethod.POST, token, plannedObligationDTOList, PlannedObligationDTO.class);
        } catch (Exception e) {
            logger.error(" failed to de Actualize",e);
            TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + ACTIVE_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaListForCostDefunct, Object.class);
            TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT + ACTIVE_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, plannedObligationDTOList, PlannedObligationDTO.class);
        }
        deactulizeIntercompanyObligations(obligationList,tenantId,token);
        logger.info("De-Actualization for Planned Obligation id {} : ", plannedObligationItem.getPlannedObligationId());
        return ReturnStatus.successInstance("De-Actualization Done");
    }
    @SneakyThrows
    @Deprecated
    private void deactulizeIntercompanyObligations(List<PlannedObligationDTO> obligationList, String tenantId, String token){
        for (PlannedObligationDTO plannedObligation : obligationList) {
            if(plannedObligation.getTradeSettlementReference()!=null && !plannedObligation.getTradeSettlementReference().isEmpty()){
                InterCompanyTradeDTO interCompanyTradeDTO = TransactionIdUtil.query(baseUrl + "/api/interCompanyTrades/v1/getInterCompanyTradeByUuid" + QUE_TENANT_ID + tenantId + "&interCompanyUuid=" + plannedObligation.getTradeSettlementReference(), HttpMethod.GET, token, null, InterCompanyTradeDTO.class);
                var obligations = new ArrayList<PlannedObligationDTO>();
                assert interCompanyTradeDTO != null;
                for(var obj :interCompanyTradeDTO.getSettlementData().getInterCompanyTradeDetails()) {
                    var obligation = TransactionIdUtil.query(baseUrl +PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + QUE_TENANT_ID + tenantId + PLANNED_OBLIGATION_ID+ obj.getObligationId(), HttpMethod.GET, token, null, PlannedObligationDTO.class);
                    obligations.add(obligation);
                }
                deActualize(obligations,token, tenantId);
            }
        }
    }

    @SneakyThrows
    private ArrayList<String> canClaim(List<ActualizeObjV2> actualizeObjList, String tenantId, String token) {
        var errors = new ArrayList<String>();
        actualizeObjList.forEach(actualizeObjV2 ->  {
            List<com.taomish.common.searchcriteria.SearchCriteria> invoiceCriteria = new ArrayList<>();
            invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(TEN_ANT_ID, EQ, tenantId));
            invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(PLANNED_OBLGN_ID, EQ, actualizeObjV2.getPlannedObligationId()));
            invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria("finalInvoiceType", "equals", FINAL_AGAINST_CLAIM));
            invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria("splitNumber", "in", List.of(0,actualizeObjV2.getSplitSequenceNumber())));
            invoiceCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria("status", "notequals", VOID));
            var invoices = TransactionIdUtil.queryList(baseUrl+INVOICE_ROOT+GET_INVOICE_BY_CRITERIA,HttpMethod.POST,token,invoiceCriteria, InvoiceDTO.class).stream().filter(item -> !item.getStatus().equalsIgnoreCase(INITIATED)).toList();;
            if(!invoices.isEmpty()) {
                var invoiceNumber = invoices.stream().map(InvoiceDTO::getInvoiceNumber).toList();
                errors.add("Invoice ("+ String.join(",",invoiceNumber) +") is already generated for claim for Obligation : "+actualizeObjV2.getPlannedObligationId());
            }
        });
        return errors;
    }

    @SneakyThrows
    public ReturnStatus claimActualizeQuantity(List<ActualizeObjV2> actualizeObjList, User principal, String token) {
        PreActualizationPayloadDto preActualizationPayloadDto = new PreActualizationPayloadDto();
        var interCompanyList = new ArrayList<ActualizeObjV2>();
        var intercompanyTradeList = actualizeObjList.stream().filter(item -> item.getTradeSettlementReference() != null).toList();
        for (ActualizeObjV2 plannedObligationDTO: intercompanyTradeList){
            interCompanyList.addAll(getActualizedObjectListForInterCompanyTrades(principal.getTenantId(),plannedObligationDTO,token,preActualizationPayloadDto,false));
        }
        actualizeObjList.addAll(interCompanyList);
        var errors = canClaim(actualizeObjList,principal.getTenantId(),token);
        if(!errors.isEmpty()) {
            throw new TaomishError( "Failed to claim quantity. Check below errors : \n\n" + String.join(",", errors));
        }

        List<String> plannedObligationIds = actualizeObjList.stream()
                .map(ActualizeObjV2::getPlannedObligationId)
                .filter(Objects::nonNull)
                .toList();
        var plannedObligationsSearchCriteria = new ArrayList<com.taomish.common.searchcriteria.SearchCriteria>();
        plannedObligationsSearchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(TEN_ANT_ID, EQUA_LS, principal.getTenantId()));
        plannedObligationsSearchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(PLANNED_OBLI_ID, "in", plannedObligationIds));
        List<PlannedObligationDTO> plannedObligationDTOList = TransactionIdUtil.queryList(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATION_BY_CRITERIA + QUE_TENANT_ID + principal.getTenantId(), HttpMethod.POST, token, plannedObligationsSearchCriteria, PlannedObligationDTO.class);

        for (ActualizeObjV2 actualizeObjV2 : actualizeObjList) {
            PlannedObligationDTO plannedObligationDTO = plannedObligationDTOList.stream().filter(e->(e.getPlannedObligationId().equalsIgnoreCase(actualizeObjV2.getPlannedObligationId()))).toList().getFirst();
            claimQuantity(actualizeObjV2,preActualizationPayloadDto, plannedObligationDTO, principal,token,actualizeObjList);
        }
        try {
            actualizationQuantityRepo.saveAll(preActualizationPayloadDto.getActualizedQuantityObligationsList());
            billOfLandingRepo.saveAll(preActualizationPayloadDto.getBlRecords());
            actualPackingDetailsRepo.saveAll(preActualizationPayloadDto.getActualPackingDetails());
            actualQualityRepo.saveAll(preActualizationPayloadDto.getQualities());
            preActualizationPayloadDto.getObligationIds().forEach(item -> TransactionIdUtil.setPlannedObligationStatesToTrue(baseUrl,item,principal.getTenantId(),token,PlannedObligationState.DISCHARGED));
            TransactionIdUtil.query(baseUrl+CASHFLOW_ROOT + SAVE_ALl_CASHFLOW + QUERY+ TENANT_ID_EQ + principal.getTenantId(),HttpMethod.POST,token,preActualizationPayloadDto.getCashflowDataDTOList(),CashflowDataDTO.class);
        } catch (Exception e) {
            preActualizationPayloadDto.getObligationIds().forEach(item -> TransactionIdUtil.setPlannedObligationStatesToFalse(baseUrl,item,principal.getTenantId(),token,PlannedObligationState.DISCHARGED));
            throw new TaomishError("Failed during claim : " + e.getMessage(),e);
        }
        preActualizationPayloadDto.getActualizedQuantityObligationsDTOListForEOD().forEach(actualizedQuantityObligationsDTO -> platformQueueService.sendObject(EXCHANGE_NAME, QUANTITY_ACTUALIZATION, actualizedQuantityObligationsDTO));

        return ReturnStatus.successInstance("Claim  Done Successfully");
    }

    @SneakyThrows
    private void claimQuantity(ActualizeObjV2 actualizeObjV2, PreActualizationPayloadDto preActualizationPayloadDto, PlannedObligationDTO plannedObligation, User principal, String token, List<ActualizeObjV2> actualizeObjList) {
        ActualizedQuantityObligations actualizedQuantityObligationsOld = actualizationQuantityRepo.findByPlannedObligationIdAndSplitSequenceNumberAndTenantIdOrderBySplitSequenceNumberAsc(plannedObligation.getPlannedObligationId(), actualizeObjV2.getSplitSequenceNumber(), principal.getTenantId());
        if (actualizedQuantityObligationsOld == null) {
           throw new TaomishError("Actualization not found for planned obligation id : "+plannedObligation.getPlannedObligationId());
        }

        actualizedQuantityObligationsOld.setClaimedQuantity(actualizeObjV2.getUnLoadQuantity());
        actualizedQuantityObligationsOld.setUnloadQuantity(actualizeObjV2.getUnLoadQuantity());
        actualizedQuantityObligationsOld.setAdjusted(actualizeObjV2.getAdjusted());
        actualizedQuantityObligationsOld.setLossGainQuantity(actualizeObjV2.getLossGainQuantity());
        actualizedQuantityObligationsOld.setDischargeDate(actualizeObjV2.getDischargeDate());
        logger.info("Saving Quantity Actualization for ObligationId: {} " ,actualizedQuantityObligationsOld.getPlannedObligationId());
        preActualizationPayloadDto.getActualizedQuantityObligationsList().add(actualizedQuantityObligationsOld);
        manageSaveBl(actualizedQuantityObligationsOld,preActualizationPayloadDto,plannedObligation,actualizeObjV2,principal);
        manageQuality(actualizedQuantityObligationsOld,preActualizationPayloadDto,actualizeObjV2,principal);
        managePackingDetails(actualizedQuantityObligationsOld,preActualizationPayloadDto,actualizeObjV2,principal);

        preActualizationPayloadDto.getObligationIds().add(plannedObligation.getPlannedObligationId());
        if (!plannedObligation.isExternalRecord()) {
            double claimQuantity = actualizeObjV2.getUnLoadQuantity();
            var exceededQuantity = 0.0;
            var exceedingPrice = 0.0;
            if(actualizeObjV2.getToleranceBreached() != null && Boolean.TRUE.equals(actualizeObjV2.getToleranceBreached()) && (actualizeObjV2.getSplitSequenceNumber() == 0 || actualizeObjV2.getSplitSequenceNumber() == 1)) {
                var maxTolerance = actualizeObjV2.getTolerance();
                var unloadQuantity = actualizeObjV2.getUnLoadQuantity();
                if(actualizeObjList != null) {
                    unloadQuantity = actualizeObjList.stream().map(ActualizeObjV2::getUnLoadQuantity).reduce(Double::sum).orElse(0.0);
                }
                if(unloadQuantity != 0) {
                    exceededQuantity = TransactionIdUtil.formatToDecimalPoint((unloadQuantity - maxTolerance),3);
                    if(actualizeObjV2.getSplitSequenceNumber() == 0) {
                        claimQuantity = unloadQuantity - exceededQuantity;
                    }
                    exceedingPrice = actualizeObjV2.getSettlementPrice();
                }
            }
            actualizedQuantityObligationsOld.setClaimedQuantity((actualizeObjV2.getToleranceBreached() != null &&  Boolean.TRUE.equals(actualizeObjV2.getToleranceBreached()) && actualizeObjV2.getSplitSequenceNumber() != 0)?actualizeObjV2.getTolerance()/actualizeObjList.size() : claimQuantity);
            preActualizationPayloadDto.getCashflowDataDTOList().add(actualizationCashflowServiceV2.getClaimCashflowDTO(actualizedQuantityObligationsOld,plannedObligation,principal.getTenantId(),token));
            if(exceededQuantity != 0 ) {
                preActualizationPayloadDto.getCashflowDataDTOList().add(actualizationCashflowServiceV2.getExceedingAmountCashflow(plannedObligation,actualizeObjV2.getSplitSequenceNumber(),exceededQuantity,exceedingPrice,principal.getTenantId(),token));
            }
        }
        logger.info("Quantity actualization is done for planned Obligation id : {}" , plannedObligation.getPlannedObligationId());
    }


    public List<ActualQuality> updateQualityForObligation(List<ActualQualityDTO> actualQualityDTOs,User user) {
        actualQualityRepo.deleteAllByTenantIdAndPlannedObligationId(user.getTenantId(),actualQualityDTOs.getFirst().getPlannedObligationId());
        return actualQualityRepo.saveAll(streamUtil.mapToList(actualQualityDTOs,ActualQuality.class));
    }
    public List<ActualPackingDetails> updatePackingDetailsForObligation(List<ActualPackingDetailsDTO> actualPackingDetailsDTOs) {
        return actualPackingDetailsRepo.saveAll(streamUtil.mapToList(actualPackingDetailsDTOs,ActualPackingDetails.class));
    }
    @SneakyThrows
    public List<ActualizedQuantityObligationsDTO> getActualizationQuantityByCriteria(String tenantId, String token, List<com.taomish.common.searchcriteria.SearchCriteria> searchBuilder, String operation){
        try {
            searchBuilder.add(new com.taomish.common.searchcriteria.SearchCriteria("tenantId","equals",tenantId));
            return streamUtil.mapToList(actualizationQuantityRepo.findAll(SearchCriteriaBuilder.getSpecificationWithOperation(searchBuilder,operation)),ActualizedQuantityObligationsDTO.class) ;
        }
        catch (Exception ex){
            logger.error("Criteria failed in getActualizationQuantityByCriteria : ", ex);
            throw new TaomishError("Failed to get the Criteria data for actualizationQuantity");
        }
    }

    @SneakyThrows
    public ActualizedQuantityObligationsDTO saveActualizationQuantity(String token, ActualizedQuantityObligationsDTO saveActualizedQuantityObj){
        try{
            ActualizedQuantityObligations actualizedQuantityObj = null;
            actualizedQuantityObj = modelMapperService.map(saveActualizedQuantityObj,ActualizedQuantityObligations.class);
            return modelMapperService.map(actualizationQuantityRepo.save(actualizedQuantityObj),ActualizedQuantityObligationsDTO.class);
        }
        catch(Exception ex){
            logger.error("Error while saving data in saveActualizationQuantity, error is: ", ex);
            throw new TaomishError("Failed to save the data in the  actualizationQuantity");
        }
    }

    public List<ActualPackingDetails> getPackagingDetailsForObligationId(String obligationId, User user) {
        return actualPackingDetailsRepo.findAllByPlannedObligationIdAndTenantId(obligationId,user.getTenantId());
    }

    public List<com.taomish.dtos.actualizationservice.ActualQualityDTO> getQualityForObligation(String obligationId, User user) {
        return streamUtil.mapToList(actualQualityRepo.findAllByPlannedObligationIdAndTenantId(obligationId,user.getTenantId()), com.taomish.dtos.actualizationservice.ActualQualityDTO.class);
    }
    @SneakyThrows
    public List<BLActualizationDTO> getGrnData(String planId, User principal,String token){
        List<PlannedObligationDTO> plannedObligationDTOListForPlanId = TransactionIdUtil.queryList(baseUrl+PLANNED_OBLIGATION_ROOT+GET_PLANNED_OBLIGATION_FOR_ACTUALIZATION+QUE_TENANT_ID+principal.getTenantId()+AND+PLAN_ID_EQ+planId,HttpMethod.GET,token,null, PlannedObligationDTO.class);
        List<StockMovementDto> stockMovementDtoForPlanId = TransactionIdUtil.queryList(inventoryBaseURL + "/api/stock-movement"+"/get-movement-by-vessel-plan-id?vesselPlanId="+ planId+"&checkPlanId="+true, HttpMethod.GET,token,null,StockMovementDto.class);
        List<String> stockInvList = Arrays.asList("Stock Transfer","Processing","Stock Transfer-Simple blending");
        List<String> statusList = Arrays.asList("Initiated","Completed");
        List<BLActualizationDTO> blActualizationDTOS = new ArrayList<>();
        if(stockMovementDtoForPlanId.isEmpty()) return blActualizationDTOS;
        List<StockMovementDto> optimizedList = stockMovementDtoForPlanId.stream()
                .sorted(Comparator.comparing(e -> e.getStatus().equalsIgnoreCase("Initiated") ? 0 : 1))
                .collect(Collectors.toList());
        stockMovementDtoForPlanId.clear();
        stockMovementDtoForPlanId.addAll(optimizedList);
        for(StockMovementDto stockData: stockMovementDtoForPlanId){
            if(!statusList.contains(stockData.getStatus()))continue;
            PlannedObligationDTO plannedObligationDTO = new PlannedObligationDTO();
            plannedObligationDTO= plannedObligationDTOListForPlanId.stream()
                    .filter(planObliga -> planObliga.getPlannedObligationId().equalsIgnoreCase(stockData.getTransferFrom()))
                    .findFirst()
                    .orElse(null);

            if(plannedObligationDTO !=null && (stockData.getType().equalsIgnoreCase("Build") || stockData.getType().equalsIgnoreCase("Build-Simple blending"))){
                ActualizedQuantityObligations actualizeRecords = actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationIdAndActualizationId(principal.getTenantId(),stockData.getTransferFrom(),stockData.getActualizeId());
                var actualQuality = streamUtil.mapToList(actualQualityRepo.findByTenantIdAndPlannedObligationIdAndActualizationId(principal.getTenantId(),stockData.getTransferFrom(),stockData.getActualizeId()), ActualQualityDTO.class);
                var actualPackingDetails = streamUtil.mapToList(actualPackingDetailsRepo.findAllByPlannedObligationIdAndTenantId(stockData.getTransferFrom(),principal.getTenantId()), ActualPackingDetailsDTO.class);
                BillOfLandingDTO billOfLandingForTrade;
                billOfLandingForTrade = modelMapperService.map(billOfLandingRepo.findByPlannedObligationIdAndSplitSequenceNumberAndTenantId(stockData.getTransferFrom(),stockData.getSplitSequenceNumber(),stockData.getTenantId()), BillOfLandingDTO.class );;
                BLActualizationDTO blActualizationDTO = new BLActualizationDTO();
                blActualizationDTO.setPlanId(plannedObligationDTO.getPlanId());
                blActualizationDTO.setClaimedQuantity(0.0);
                blActualizationDTO.setPlannedObligationId(plannedObligationDTO.getPlannedObligationId());
                blActualizationDTO.setPlannedQuantity(plannedObligationDTO.getPlannedQuantity());
                blActualizationDTO.setTradeId(plannedObligationDTO.getTradeId());
                blActualizationDTO.setTradeTransactionType(plannedObligationDTO.getTradeTransactionType());
                blActualizationDTO.setUom(plannedObligationDTO.getQuantityUOM());
                blActualizationDTO.setTradeSettlementReference(plannedObligationDTO.getTradeSettlementReference());
                blActualizationDTO.setPackageType(plannedObligationDTO.getPackageType());
                blActualizationDTO.setPackageCapacity(plannedObligationDTO.getPackageCapacity());
                blActualizationDTO.setPackageUom(plannedObligationDTO.getPackageUom());
                blActualizationDTO.setNoOfUnits(plannedObligationDTO.getNoOfUnits());
                blActualizationDTO.setContractTerm(plannedObligationDTO.getContractTerm());
                blActualizationDTO.setActualQuantity(plannedObligationDTO.getActualizedQuantity());
                blActualizationDTO.setSplitQuantity(plannedObligationDTO.getSplitQuantity());
                blActualizationDTO.setMatchType(plannedObligationDTO.getMatchType());
                blActualizationDTO.setAdjusted(plannedObligationDTO.getAdjusted());
                blActualizationDTO.setLossGainQuantity(plannedObligationDTO.getLossGainQuantity());
                blActualizationDTO.setCreatedBy(plannedObligationDTO.getCreatedBy());
                blActualizationDTO.setUpdatedBy(plannedObligationDTO.getUpdatedBy());
                blActualizationDTO.setUuid(UUID.randomUUID());
                blActualizationDTO.setCounterparty(plannedObligationDTO.getCounterparty());
                blActualizationDTO.setTradeQuantity(plannedObligationDTO.getTradeQuantity());
                blActualizationDTO.setObligationState(plannedObligationDTO.getObligationState());
                blActualizationDTO.setDischargeDate(plannedObligationDTO.getDischargeDate());
                blActualizationDTO.setToleranceValue(plannedObligationDTO.getToleranceValue());
                blActualizationDTO.setCommodity(plannedObligationDTO.getCommodity());
                blActualizationDTO.setFinalInvoiced(plannedObligationDTO.isFinalInvoiced());
                blActualizationDTO.setQualityDetails(actualQuality);
                blActualizationDTO.setTradeId(plannedObligationDTO.getTradeId());
                blActualizationDTO.setInvoiceClaim(plannedObligationDTO.getInvoiceClaim());
                blActualizationDTO.setPackageType(plannedObligationDTO.getPackageType());
                blActualizationDTO.setExternalPackageUnit(plannedObligationDTO.getExternalPackageUnit());
                blActualizationDTO.setInternalPackageUnit(plannedObligationDTO.getInternalPackageUnit());
                blActualizationDTO.setSettlementToleranceValue(plannedObligationDTO.getSettlementToleranceValue());
                blActualizationDTO.setPackingDetails(actualPackingDetails);
                blActualizationDTO.setTradeDateTime(plannedObligationDTO.getTradeDateTime());
                //  transferID
                blActualizationDTO.setStockTransferId(stockData.getTransferId());
                // bill of ladding
                var blRows = billOfLandingForTrade == null ? null : List.of(billOfLandingForTrade);
                blActualizationDTO.setBlSplitRows(blRows);
                blActualizationDTO.setExternalPackage(plannedObligationDTO.getExternalPackage());
                blActualizationDTO.setInternalPackage(plannedObligationDTO.getInternalPackage());
                //  load and unload qty
                blActualizationDTO.setUnLoadQuantity(stockData.getQuantity().getUnit() / stockData.getConversionFactor());
                if(stockData.getStatus().equalsIgnoreCase("Completed")){
                    blActualizationDTO.setLoadQuantity(stockData.getQuantity().getUnit() / stockData.getConversionFactor());
                    blActualizationDTO.setGrnQuantity(stockData.getActualReceivedQty().getUnit() / stockData.getConversionFactor());
                    GrnStock grnRecord = new GrnStock();
                    if(stockData.getGrnId() != null )grnRecord = grnStockRepo.findByTenantIdAndGrnId(principal.getTenantId(),stockData.getGrnId());
                    blActualizationDTO.setGrnId(grnRecord.getGrnId() == null ? null : grnRecord.getGrnId());
                    blActualizationDTO.setGrnDate(grnRecord.getGrnDate() == null ? null :grnRecord.getGrnDate());
                    blActualizationDTO.setLossGainQuantity(grnRecord.getLossGainQuantity() == null ? null :grnRecord.getLossGainQuantity());
                    blActualizationDTO.setSplitSequenceNumber(stockData.getSplitSequenceNumber());
                    blActualizationDTO.setIsRowEditable(false);
                }else if(stockData.getStatus().equalsIgnoreCase("Initiated")){
                    blActualizationDTO.setLoadQuantity(stockData.getQuantity().getUnit() / stockData.getConversionFactor());
                    blActualizationDTO.setGrnQuantity(stockData.getQuantity().getUnit() / stockData.getConversionFactor());
                    blActualizationDTO.setIsRowEditable(true);
                    //  split sequence Number for partial build
                    blActualizationDTO.setSplitSequenceNumber(stockData.getSplitSequenceNumber());
                };
                blActualizationDTO.setUpdateReceviedQty(stockData.getIsBlSplit());
                blActualizationDTO.setStockType("Stock-build");
                blActualizationDTO.setIsOis(false);
                blActualizationDTOS.add(blActualizationDTO);

            }
            else if (stockInvList.contains(stockData.getType())) {
                BLActualizationDTO blActualizationDTO = new BLActualizationDTO();
                String plandID = stockData.getVesselPlanId() != null ? stockData.getVesselPlanId() : stockData.getPlanId();
                blActualizationDTO.setPlanId(plandID);
                blActualizationDTO.setClaimedQuantity(0.0);
                blActualizationDTO.setPlannedObligationId(stockData.getTransferId());
                blActualizationDTO.setPlannedQuantity(stockData.getQuantity().getUnit() / stockData.getConversionFactor());
                blActualizationDTO.setTradeId(stockData.getTransferFrom());
                blActualizationDTO.setTradeQuantity(stockData.getQuantity().getUnit() / stockData.getConversionFactor());
                blActualizationDTO.setTradeTransactionType(null);
                blActualizationDTO.setUom(stockData.getTransferFromUom());
                blActualizationDTO.setTradeSettlementReference(null);
                blActualizationDTO.setPackageType(null);
                blActualizationDTO.setPackageCapacity(null);
                blActualizationDTO.setPackageUom(null);
                blActualizationDTO.setNoOfUnits(null);
                blActualizationDTO.setContractTerm(null);
                blActualizationDTO.setActualQuantity(stockData.getQuantity().getUnit());
                blActualizationDTO.setMatchType(stockData.getType());
                blActualizationDTO.setCreatedBy(stockData.getCreatedBy());
                blActualizationDTO.setUpdatedBy(stockData.getUpdatedBy());
                blActualizationDTO.setUuid(UUID.randomUUID());
                blActualizationDTO.setObligationState(TransactionIdUtil.getNewObligationState(null));
                blActualizationDTO.setCommodity(stockData.getCommodity());
                blActualizationDTO.setTradeDateTime(stockData.getCreatedTimestamp());
                //  transferID
                blActualizationDTO.setStockTransferId(stockData.getTransferId());
                //  load and unload qty
                blActualizationDTO.setLoadQuantity(stockData.getQuantity().getUnit() / stockData.getConversionFactor());
                if(stockData.getStatus().equalsIgnoreCase("Completed")){
                    blActualizationDTO.setUnLoadQuantity(stockData.getQuantity().getUnit() / stockData.getConversionFactor());
                    blActualizationDTO.setGrnQuantity(stockData.getActualReceivedQty().getUnit() / stockData.getConversionFactor());
                    if(stockData.getGrnId() != null){
                        GrnStock grnRecord = grnStockRepo.findByTenantIdAndGrnId(principal.getTenantId(),stockData.getGrnId());
                        blActualizationDTO.setGrnId(grnRecord.getGrnId() == null ? null : grnRecord.getGrnId());
                        blActualizationDTO.setGrnDate(grnRecord.getGrnDate() == null ? null :grnRecord.getGrnDate());
                        blActualizationDTO.setLossGainQuantity(grnRecord.getLossGainQuantity() == null ? null :grnRecord.getLossGainQuantity());
                        blActualizationDTO.setSplitSequenceNumber(stockData.getSplitSequenceNumber());
                    }
                    blActualizationDTO.setIsRowEditable(false);
                }else if(stockData.getStatus().equalsIgnoreCase("Initiated")) {
                    blActualizationDTO.setUnLoadQuantity(stockData.getQuantity().getUnit() / stockData.getConversionFactor());
                    blActualizationDTO.setGrnQuantity(stockData.getQuantity().getUnit() / stockData.getConversionFactor());
                    blActualizationDTO.setIsRowEditable(true);
                    blActualizationDTO.setSplitSequenceNumber(1);
                }
                blActualizationDTO.setStockType("Stock Transfer");
                blActualizationDTO.setIsOis(false);
                blActualizationDTOS.add(blActualizationDTO);
            }
        }
        return blActualizationDTOS;
    }
    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public List<GrnStock> saveGrnData(List<ActualizeObjV2> actualizeObjList, User principal, String token)throws TaomishError{
        logger.info("entered into actualizationServiceV2.getGrnData function");
        List<GrnStock> savedGrnRecord = new ArrayList<>();
        List<String> stockInvList = Arrays.asList("Stock Transfer","Processing","Stock Transfer-Simple blending");
        List<ActualizeObjV2> actualizeObjListFiltered = actualizeObjList.stream().filter(e-> !stockInvList.contains(e.getStockType()) && !e.getIsOis()).toList();
        List<String> plannedObligationIds = actualizeObjListFiltered.stream()
                .map(ActualizeObjV2::getPlannedObligationId)
                .filter(Objects::nonNull)
                .toList();
        List<PlannedObligationDTO> plannedObligationDTOList =  new ArrayList<PlannedObligationDTO>();
        var plannedObligationsSearchCriteria = new ArrayList<com.taomish.common.searchcriteria.SearchCriteria>();
        plannedObligationsSearchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(TEN_ANT_ID, EQUA_LS, principal.getTenantId()));
        plannedObligationsSearchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(PLANNED_OBLI_ID, "in", plannedObligationIds));
        if(!plannedObligationIds.isEmpty()) {
            plannedObligationDTOList = TransactionIdUtil.queryList(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATION_BY_CRITERIA + QUE_TENANT_ID + principal.getTenantId(), HttpMethod.POST, token, plannedObligationsSearchCriteria, PlannedObligationDTO.class);
            var errorList = canObligationActualizeInGrn(plannedObligationDTOList);
            if (!errorList.isEmpty()) {
                throw new TaomishError("obligations-not-priced-in-grn","Grn actualization failed with below errors : " + String.join(",", errorList),null);
            }
        }
        for (ActualizeObjV2 actualizeObjV2 : actualizeObjList) {
            if(stockInvList.contains(actualizeObjV2.getStockType())){
                savedGrnRecord.add(actulizeStockTransferGrnRecord(actualizeObjV2,token,principal));
            }
            else if (actualizeObjV2.getUpdateReceviedQty() != null && actualizeObjV2.getUpdateReceviedQty()){
        //  obligation which are actualized before doing build
                ActualizedQuantityObligations savedActualizationQuantity ;
                var quantityData =  actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationIdAndSplitSequenceNumber(principal.getTenantId(),actualizeObjV2.getPlannedObligationId(),actualizeObjV2.getSplitSequenceNumber());
                if(quantityData.isEmpty()) throw  new TaomishError("Unable to fetch Actualization Quantity data for obligation "+actualizeObjV2.getPlannedObligationId());
                savedActualizationQuantity = quantityData.getFirst();
                savedActualizationQuantity.setReceivedQuantity(savedActualizationQuantity.getReceivedQuantity() + actualizeObjV2.getGrnQuantity());
                actualizationQuantityRepo.save(savedActualizationQuantity);
                var grnrecord = createGrnObject(savedActualizationQuantity,actualizeObjV2,principal,token);
                grnStockRepo.save(grnrecord);
                savedGrnRecord.add(grnrecord);
                var invActualizeResp = grnService.actulizeInventory(actualizeObjV2.getStockTransferId(),actualizeObjV2.getGrnQuantity(),
                        "actualize",savedActualizationQuantity.getActualizationId(),savedActualizationQuantity.getSplitSequenceNumber(),grnrecord.getGrnId(),principal,token);
                if(invActualizeResp.getStatusType().equalsIgnoreCase("error")){
                    logger.error("error-in-actualizing-inventory: " + invActualizeResp.getStatus());
                    throw new TaomishError("error-in-actualizing-inventory");
                }
            }
            else{
                PurchaseOrderDto purchaseOrderDto = new PurchaseOrderDto();
                PlannedObligationDTO plannedObligation = new PlannedObligationDTO();
                if(!actualizeObjV2.getIsOis()){
                    plannedObligation = plannedObligationDTOList.stream().filter(e->(e.getPlannedObligationId().equalsIgnoreCase(actualizeObjV2.getPlannedObligationId()))).toList().getFirst();
                }
                else{
                    purchaseOrderDto = TransactionIdUtil.query( oisBaseURL+ PURCHASE_ORDER_ROOT+GET_PURCHASE_ORDER_DETAILS_BY_TENANT_ID_AND_PONUMBER+principal.getTenantId()+PO_NUMBER+actualizeObjV2.getPlannedObligationId(), HttpMethod.GET, token,null, PurchaseOrderDto.class);
                }
                List<BillOfLanding> billOfLanding = billOfLandingRepo.findAllByTenantIdAndPlannedObligationId(principal.getTenantId(),plannedObligation.getPlannedObligationId());
                ActualizedQuantityObligations actualizedQuantityObligations =  actualizationQuantityRepo.findByPlannedObligationIdAndSplitSequenceNumberAndTenantId(plannedObligation.getPlannedObligationId(),actualizeObjV2.getSplitSequenceNumber(),principal.getTenantId());
                if(actualizedQuantityObligations == null){
                    actualizedQuantityObligations = this.createActualizedQuantityObj(actualizeObjV2,plannedObligation,purchaseOrderDto,principal,token);
                } else if (!billOfLanding.isEmpty()) {
                    actualizedQuantityObligations.setReceivedQuantity(actualizedQuantityObligations.getReceivedQuantity() + actualizeObjV2.getGrnQuantity());
                }else{
                    actualizedQuantityObligations.setLoadQuantity(actualizedQuantityObligations.getLoadQuantity() + actualizeObjV2.getGrnQuantity());
                    actualizedQuantityObligations.setUnloadQuantity(actualizedQuantityObligations.getUnloadQuantity() + actualizeObjV2.getGrnQuantity());
                }


                logger.info("Saving Quantity Actualization for ObligationId: {}" , actualizedQuantityObligations.getPlannedObligationId());
                actualizationQuantityRepo.save(actualizedQuantityObligations);
            //  Manage quality in  Actualization
                manageQuality(actualizedQuantityObligations,null,actualizeObjV2,principal);
            //  Manage packing in  Actualization
                managePackingDetails(actualizedQuantityObligations,null,actualizeObjV2,principal);
            //  Save Grn Data
                var grnrecord = createGrnObject(actualizedQuantityObligations,actualizeObjV2,principal,token);
                grnStockRepo.save(grnrecord);
                savedGrnRecord.add(grnrecord);
                var invActualizeResp = grnService.actulizeInventory(actualizeObjV2.getStockTransferId(),actualizeObjV2.getGrnQuantity(),
                        "actualize",actualizedQuantityObligations.getActualizationId(),actualizedQuantityObligations.getSplitSequenceNumber(),grnrecord.getGrnId(),principal,token);
                if(invActualizeResp.getStatusType().equalsIgnoreCase("error")){
                    logger.error("error-in-actualizing-inventory: "+invActualizeResp.getStatus());
                    throw new TaomishError("error-in-actualizing-inventory");
                }
                if(billOfLanding.isEmpty() && !plannedObligation.getObligationState().isEmpty() && !plannedObligation.getObligationState().get(PlannedObligationState.ACTUALIZED)){
                    try{
                    actualizeObligationInGrn(plannedObligation, false,0,token,principal);
                    }catch (Exception ex){
                        logger.error("Update of planned Obligation failed due to :" + ex);
                        logger.info("Reverting the stock-movement record update, for transferId=" + grnrecord.getTransferId());
                        grnService.actulizeInventory(actualizeObjV2.getStockTransferId(),actualizeObjV2.getGrnQuantity(),
                                "deactualize",actualizedQuantityObligations.getActualizationId(),actualizedQuantityObligations.getSplitSequenceNumber(),grnrecord.getGrnId(),principal,token);
                        throw new TaomishError("Failed to update planned Obligation due to");
                    }
                }
                if(actualizeObjV2.getIsOis()){
                    actualizationCashflowServiceV2.createCashFlowForPurchaseOrder(purchaseOrderDto,actualizeObjV2.getGrnQuantity(),true,principal,token);
                }
            }
        }
        return savedGrnRecord;
    }

    public List<String> canObligationActualizeInGrn(List<PlannedObligationDTO> plannedObligationDTOList){
        List<String> errorInGrnActualize = new ArrayList<>();
        for(PlannedObligationDTO plannedObligationDTO : plannedObligationDTOList){
            if(plannedObligationDTO.getPriceType().equalsIgnoreCase(PTBF) && Boolean.FALSE.equals(plannedObligationDTO.getProvisionalPricing())){
                if(Boolean.FALSE.equals(plannedObligationDTO.getObligationState().get(PlannedObligationState.PARTIALLY_PRICED)) && Boolean.FALSE.equals(plannedObligationDTO.getObligationState().get(PlannedObligationState.PRICED))) {
                    errorInGrnActualize.add("Price is not allocated for Planned Obligation Id: " + plannedObligationDTO.getPlannedObligationId());
                } else if(Boolean.TRUE.equals(plannedObligationDTO.getObligationState().get(PlannedObligationState.PARTIALLY_PRICED))) {
                    errorInGrnActualize.add("Price is not fully allocated for Planned Obligation Id: " + plannedObligationDTO.getPlannedObligationId());
                }
            }
        }
        return errorInGrnActualize;
    }

    public ActualizedQuantityObligations createActualizedQuantityObj(ActualizeObjV2 actualizeObj, PlannedObligationDTO plannedObligation, PurchaseOrderDto purchaseOrderDto, User principal, String token) throws TaomishError{
        ActualizedQuantityObligations actualizedQuantityObj = new ActualizedQuantityObligations();
        String actualizationQuantityId = transactionIDGenerator.generateId( "actualizationQuantityId", plannedObligation, principal.getTenantId(), token,false,"",false);
        if (actualizationQuantityId == null) {
            throw new TaomishError("Actualization Quantity ID is not generated");
        }
        String plannedObligationId = plannedObligation.getPlannedObligationId() != null ? plannedObligation.getPlannedObligationId() : purchaseOrderDto.getPoNumber();
        double plannedQuantity = plannedObligation.getPlannedObligationId() != null ? plannedObligation.getPlannedQuantity() : purchaseOrderDto.getPurchaseOrderDescription().getFirst().getQuantity().getUnit();

        actualizedQuantityObj.setActualizedQuantityId(actualizationQuantityId);
        //  updating actualization_id
        actualizedQuantityObj.setActualizationId(actualizationQuantityId);
        actualizedQuantityObj.setSplitSequenceNumber(actualizeObj.getSplitSequenceNumber());
        actualizedQuantityObj.setAdjusted(actualizeObj.getAdjusted());
        actualizedQuantityObj.setLossGainQuantity(actualizeObj.getLossGainQuantity());
        actualizedQuantityObj.setDischargeDate(actualizeObj.getDischargeDate());
        actualizedQuantityObj.setPlannedObligationId(plannedObligationId);
        actualizedQuantityObj.setPlannedObligationType(plannedObligation.getTradeTransactionType());
        actualizedQuantityObj.setTradeId(plannedObligation.getTradeId());
        actualizedQuantityObj.setPlannedQuantity(plannedQuantity);
        actualizedQuantityObj.setLoadQuantity(actualizeObj.getGrnQuantity());
        actualizedQuantityObj.setUnloadQuantity(actualizeObj.getGrnQuantity());
        if(actualizeObj.getPackageType() != null) {
            if (actualizeObj.getPackageType().equals(PACKAGE_TYPE_CONTAINER)) {
                actualizedQuantityObj.setExternalPackage(actualizeObj.getExternalPackage());
                actualizedQuantityObj.setExternalPackageUnit(actualizeObj.getActualExternalPackage());
                actualizedQuantityObj.setPlannedExternalPackageUnit(plannedObligation.getNoOfUnits());
            } else if (actualizeObj.getPackageType().equals(PACKAGE_TYPE_UNIT)) {
                actualizedQuantityObj.setInternalPackage(actualizeObj.getInternalPackage());
                actualizedQuantityObj.setInternalPackageUnit(actualizeObj.getActualInternalPackage());
                actualizedQuantityObj.setPlannedInternalPackageUnit(plannedObligation.getNoOfUnits());
            }
        }
        actualizedQuantityObj.setBrand(plannedObligation.getBrand());
        actualizedQuantityObj.setGrade(plannedObligation.getGrade());
        actualizedQuantityObj.setOrigin(plannedObligation.getCropOrigin());
        actualizedQuantityObj.setCommodity(plannedObligation.getCommodity());
        actualizedQuantityObj.setQuantityUom(plannedObligation.getQuantityUOM());
        actualizedQuantityObj.setTenantId(principal.getTenantId());
        return actualizedQuantityObj;
    }

    @SneakyThrows
    public void actualizeObligationInGrn(PlannedObligationDTO plannedObligation,
                                         boolean isEdit, Integer splitSequenceNumber, String token, User principal)throws TaomishError{
        logger.info("entered in actualizeObligationForGrn function to create cash flow");
        JSONObject tolerance = new JSONObject(plannedObligation.getToleranceValue());
        double min = tolerance.getDouble("min");
        double minimumTolerance = (plannedObligation.getPlannedQuantity() - ((min / 100) * plannedObligation.getPlannedQuantity()));
        List<ActualizedQuantityObligations> actualizeRecords = actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationId(principal.getTenantId(),plannedObligation.getPlannedObligationId());
        double sumOfLoadQty = actualizeRecords.stream()
                .mapToDouble(ActualizedQuantityObligations::getLoadQuantity)
                .sum();
        double sumOfUnLoadQty = actualizeRecords.stream()
                .mapToDouble(ActualizedQuantityObligations::getUnloadQuantity)
                .sum();
        if(minimumTolerance <= sumOfLoadQty){
            logger.info("update the ACTUALIZED ObligationState of obligation to true");
            plannedObligation.getObligationState().put(PlannedObligationState.ACTUALIZED, true);
            var updatedPlannedObligation = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + UPDATE_TRADE_PLANOBLIGATION + QUE_TENANT_ID + principal.getTenantId(), HttpMethod.POST, token, plannedObligation, PlannedObligationDTO.class);
            logger.info("updated the plannedObligationDto for obligationId=" + updatedPlannedObligation.getPlannedObligationId());
            try{
        //  In cash flow table the quantity field will be set to grnQty instead planned qty
                CashflowDataDTO cashflowDataDTO = actualizationCashflowServiceV2.getActualizeCashflowDTO(actualizeRecords.getFirst(),plannedObligation,principal.getTenantId(),token);
                var updatedCashFlow = TransactionIdUtil.query(baseUrl+CASHFLOW_ROOT + SAVE_ALl_CASHFLOW + QUERY+ TENANT_ID_EQ + principal.getTenantId(),HttpMethod.POST,token,List.of(cashflowDataDTO),CashflowDataDTO.class);

            }catch (Exception e) {
                logger.error("Cash-flow creation failed due to :" + e);
                logger.info("Reverting the PlannedObligationDto changes for : "+plannedObligation.getPlannedObligationId());
                plannedObligation.getObligationState().put(PlannedObligationState.ACTUALIZED, false);
                TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + UPDATE_TRADE_PLANOBLIGATION + QUE_TENANT_ID + principal.getTenantId(), HttpMethod.POST, token, plannedObligation, PlannedObligationDTO.class);
                throw new TaomishError("Cash-flow creation failed");
            }
        }

    }
    @SneakyThrows
    public ReturnStatus deactualizeGrnData(List<BLActualizationDTO> grnBLRows, String token, User principal) {
        logger.info("entered in deactualizeGrnData function");
        String tenantId = principal.getTenantId();
        String errorFound = "";
        List<String> plannedObligationList = grnBLRows.stream().filter(e->e.getStockType().equalsIgnoreCase("Stock-build") && !e.getUpdateReceviedQty() && !e.getIsOis())
                .map(BLActualizationDTO::getPlannedObligationId).toList();
        var errors = grnService.canDeactualize(plannedObligationList, tenantId, token);
        if(!errors.isEmpty()) {
            return ReturnStatus.errorInstance("De-Actualization failed with errors: ",String.join(",",errors));
        }
        for(BLActualizationDTO grnBlRecord : grnBLRows){
            PlannedObligationDTO plannedObligation = new PlannedObligationDTO();
            if(!grnBlRecord.getIsOis()){
                plannedObligation= TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT +GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + QUE_OBLIGATION_ID + grnBlRecord.getPlannedObligationId() + AND_TENANT_ID + tenantId,
                        HttpMethod.GET, token, null, PlannedObligationDTO.class);
            }
            GrnStock grnSavedRecord = grnStockRepo.findByTenantIdAndGrnId(tenantId,grnBlRecord.getGrnId());
            try {
                var response = grnService.deactualizeRecordInGrn(plannedObligation,grnBlRecord,grnSavedRecord,principal,token);
                if(response.isErrorInstance()){
                    errorFound = (response.getStatus());
                    return ReturnStatus.errorInstance("Falied to deactualize",errorFound);
                }
            }catch(Exception ex){
                errorFound = (TransactionIdUtil.getErrorResponse(ex).getStatus());
                logger.error("failed to deactualize GRN record ",ex);
                return ReturnStatus.errorInstance("Falied to deactualize",errorFound);
            }
        }
        return ReturnStatus.successInstance("De-Actualization Done");
    }
    @SneakyThrows
    public GrnStock actulizeStockTransferGrnRecord(ActualizeObjV2 actualizeObjV2,String token, User principal)throws TaomishError{
        logger.info("entered in actulizeStockTransferGrnRecord function");
        PlannedObligationDTO plannedObligation = new PlannedObligationDTO();
        String actualizationQuantityId = transactionIDGenerator.generateId( "actualizationQuantityId", plannedObligation, principal.getTenantId(), token,false,"",false);
        if (actualizationQuantityId == null) {
            throw new TaomishError("Actualization Quantity ID is not generated");
        }
    //  save to actual quantity table
        ActualizedQuantityObligations actualizedQuantityObligations = new ActualizedQuantityObligations();
        actualizedQuantityObligations.setActualizedQuantityId(actualizationQuantityId);
        actualizedQuantityObligations.setActualizationId(actualizationQuantityId);
        actualizedQuantityObligations.setSplitSequenceNumber(actualizeObjV2.getSplitSequenceNumber());
        actualizedQuantityObligations.setAdjusted(actualizeObjV2.getAdjusted());
        actualizedQuantityObligations.setLossGainQuantity(actualizeObjV2.getLossGainQuantity());
        actualizedQuantityObligations.setDischargeDate(actualizeObjV2.getDischargeDate());
        actualizedQuantityObligations.setPlannedObligationId(actualizeObjV2.getStockTransferId());
        actualizedQuantityObligations.setPlannedObligationType(null);
        actualizedQuantityObligations.setTradeId(actualizeObjV2.getStockTransferId());
        actualizedQuantityObligations.setPlannedQuantity(actualizeObjV2.getLoadQuantity());
        actualizedQuantityObligations.setLoadQuantity(actualizeObjV2.getLoadQuantity());
        actualizedQuantityObligations.setUnloadQuantity(actualizeObjV2.getGrnQuantity());
        actualizedQuantityObligations.setTenantId(principal.getTenantId());
        actualizationQuantityRepo.save(actualizedQuantityObligations);
    //  save quality details to table
        this.manageQuality(actualizedQuantityObligations,null,actualizeObjV2,principal);
    //  save packing details to table
        this.managePackingDetails(actualizedQuantityObligations,null,actualizeObjV2,principal);

        var grnrecord = createGrnObject(actualizedQuantityObligations,actualizeObjV2,principal,token);
        grnStockRepo.save(grnrecord);
        var invActualizeResp = grnService.actulizeInventory(actualizeObjV2.getStockTransferId(),actualizeObjV2.getGrnQuantity(),
                "actualize",actualizedQuantityObligations.getActualizationId(),actualizedQuantityObligations.getSplitSequenceNumber(),grnrecord.getGrnId(),principal,token);
        if(invActualizeResp.getStatusType().equalsIgnoreCase("error")){
            logger.error("error-in-actualizing-inventory: " + invActualizeResp.getStatus());
            throw new TaomishError("error-in-actualizing-inventory");
        }
        return grnrecord;
    }

    @SneakyThrows
    public GrnStock createGrnObject(ActualizedQuantityObligations actualizedQuantityObligations, ActualizeObjV2 actualizeObjV2, User principal, String token) throws TaomishError{
        GrnStock grnStock = new GrnStock();
        String grnStockId = transactionIDGenerator.generateId( "grnStockId", grnStock, principal.getTenantId(), token,false,"",false);
        if (grnStockId == null) {
            throw new TaomishError("Grn ID is not generated");
        }
        grnStock.setGrnId(grnStockId);
        grnStock.setLossGainQuantity(actualizeObjV2.getLossGainQuantity());
        grnStock.setPlannedObligationId(actualizedQuantityObligations.getPlannedObligationId());
        grnStock.setSplitSequenceNumber(actualizedQuantityObligations.getSplitSequenceNumber());
        grnStock.setActualizeId(actualizedQuantityObligations.getActualizationId());
        grnStock.setGrnQuantity(actualizeObjV2.getGrnQuantity());
        grnStock.setTransferId(actualizeObjV2.getStockTransferId());
        grnStock.setTenantId(principal.getTenantId());
        grnStock.setGrnDate(actualizeObjV2.getGrnDate());
        return grnStock;
    }

    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReturnStatus deleteGrn(String plannedObligation, String actulizedId, String grnId, Boolean isBlSplit,User principal,String token)throws TaomishError {
        logger.info("entered in deleteGrn function");
        String tenantId = principal.getTenantId();
        ActualizedQuantityObligations actualizedQuantityObligations = actualizationQuantityRepo.findByTenantIdAndActualizationId(tenantId,actulizedId);
        List<ActualQuality> actualQualities = actualQualityRepo.findAllByTenantIdAndActualizationId(tenantId,actulizedId);
        List<ActualPackingDetails> actualPackingDetails = actualPackingDetailsRepo.findAllByTenantIdAndActualizationId(tenantId,actulizedId);
        GrnStock grnSavedRecord = grnStockRepo.findByTenantIdAndGrnId(tenantId,grnId);
        if(isBlSplit){
            double Qty = actualizedQuantityObligations.getReceivedQuantity() - grnSavedRecord.getGrnQuantity();
            actualizedQuantityObligations.setReceivedQuantity(Math.max(0,Qty));
            actualizationQuantityRepo.save(actualizedQuantityObligations);
        }else{
            if(actualizedQuantityObligations != null)actualizationQuantityRepo.delete(actualizedQuantityObligations);
        }
        if(actualQualities != null)actualQualityRepo.deleteAll(actualQualities);
        if(actualPackingDetails != null)actualPackingDetailsRepo.deleteAll(actualPackingDetails);
        if(grnSavedRecord != null)grnStockRepo.delete(grnSavedRecord);
        return ReturnStatus.successInstance("Deletion of GRN and was successfully");
    }

    public List<BLActualizationDTO> getPurchaseOrderForGrn(String purchaseOrder, User user, String token){
        logger.info("entered into actualizationServiceV2.getPurchaseOrderForGrn function");
        List<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaList = new ArrayList<>();
        searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("transferFrom","equals",purchaseOrder));
        searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("type","equals","Build"));
        searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("status","notequals","Deleted"));
        searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("tenantId","equals",user.getTenantId()));
        List<StockMovementDto> purchaseOrderStockData = TransactionIdUtil.queryList(inventoryBaseURL+"/api/stock-movement" +"/get-stock-movement-by-criteria?tenantId="+ user.getTenantId()+"&isSearchCriteria=true", HttpMethod.POST, token,searchCriteriaList, StockMovementDto.class);
        List<BLActualizationDTO> blActualizationDTOs = new ArrayList<>();
        PurchaseOrderDto purchaseOrderDto = TransactionIdUtil.query( oisBaseURL+ PURCHASE_ORDER_ROOT+GET_PURCHASE_ORDER_DETAILS_BY_TENANT_ID_AND_PONUMBER+user.getTenantId()+PO_NUMBER+purchaseOrder, HttpMethod.GET, token,null, PurchaseOrderDto.class);
        for(StockMovementDto stockData:purchaseOrderStockData){
            BLActualizationDTO blActualizationDTO = new BLActualizationDTO();
            blActualizationDTO.setCounterparty(purchaseOrderDto.getCounterpartyName());
            blActualizationDTO.setPlannedObligationId(stockData.getTransferFrom());
            blActualizationDTO.setClaimedQuantity(0.0);
            blActualizationDTO.setPlannedQuantity(stockData.getQuantity().getUnit());
            blActualizationDTO.setTradeId(stockData.getTransferFrom());
            blActualizationDTO.setTradeQuantity(stockData.getQuantity().getUnit());
            blActualizationDTO.setUom(stockData.getQuantity().getUom());
            blActualizationDTO.setActualQuantity(stockData.getQuantity().getUnit());
            blActualizationDTO.setMatchType(stockData.getType());
            blActualizationDTO.setCreatedBy(stockData.getCreatedBy());
            blActualizationDTO.setUpdatedBy(stockData.getUpdatedBy());
            blActualizationDTO.setUuid(UUID.randomUUID());
//            blActualizationDTO.setObligationState(TransactionIdUtil.getNewObligationState(null));
            blActualizationDTO.setCommodity(stockData.getCommodity());
            blActualizationDTO.setTradeDateTime(stockData.getCreatedTimestamp());
            //  transferID
            blActualizationDTO.setStockTransferId(stockData.getTransferId());
            //  load and unload qty
            blActualizationDTO.setLoadQuantity(stockData.getQuantity().getUnit());
            if(stockData.getStatus().equalsIgnoreCase("Completed")){
                blActualizationDTO.setUnLoadQuantity(stockData.getQuantity().getUnit());
                blActualizationDTO.setGrnQuantity(stockData.getActualReceivedQty().getUnit());
                if(stockData.getGrnId() != null){
                    GrnStock grnRecord = grnStockRepo.findByTenantIdAndGrnId(user.getTenantId(),stockData.getGrnId());
                    blActualizationDTO.setGrnId(grnRecord.getGrnId() == null ? null : grnRecord.getGrnId());
                    blActualizationDTO.setGrnDate(grnRecord.getGrnDate() == null ? null :grnRecord.getGrnDate());
                    blActualizationDTO.setLossGainQuantity(grnRecord.getLossGainQuantity() == null ? null :grnRecord.getLossGainQuantity());
                    blActualizationDTO.setSplitSequenceNumber(stockData.getSplitSequenceNumber());
                }
                blActualizationDTO.setIsRowEditable(false);
            }else if(stockData.getStatus().equalsIgnoreCase("Initiated")) {
                blActualizationDTO.setUnLoadQuantity(stockData.getQuantity().getUnit());
                blActualizationDTO.setGrnQuantity(stockData.getQuantity().getUnit());
                blActualizationDTO.setIsRowEditable(true);
                blActualizationDTO.setSplitSequenceNumber(stockData.getSplitSequenceNumber());
            }
            blActualizationDTO.setStockType("Stock-build");
            blActualizationDTO.setIsOis(true);
            blActualizationDTOs.add(blActualizationDTO);
        }
        return blActualizationDTOs;
    }
    public void addAutoCosts(List<ActualizeObjV2> actualizeObjV2s, String token, String tenantId) throws Exception {
        final String AUTO_ACTUALIZE_FLAG = "autoActualizeOnLoad";
        ArrayList<String> obligationIdList = new ArrayList<>();

        for (ActualizeObjV2 actualizeObj : actualizeObjV2s) {
            ArrayList<ActualizeCostObj> actualizeCostObjArrayList = new ArrayList<>();
            String obligationId = actualizeObj.getPlannedObligationId();
            if (!obligationIdList.contains(obligationId)) {
                obligationIdList.add(obligationId);
                List<ActualizeCostObj> costListForObligation = getCost(obligationId, token, tenantId);
                for (ActualizeCostObj obj : costListForObligation) {
                    if (obj.getCostMatrixWorkflow() != null
                            && obj.getCostMatrixWorkflow().get(AUTO_ACTUALIZE_FLAG) != null
                            && obj.getCostMatrixWorkflow().get(AUTO_ACTUALIZE_FLAG).equals("YES")) {
                        actualizeCostObjArrayList.add(obj);
                    }
                }
                String plannedObligationUrl = baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + QUE_TENANT_ID + tenantId + "&plannedObligationId=" + obligationId;
                PlannedObligationDTO plannedObligationDTO = TransactionIdUtil.query(plannedObligationUrl, HttpMethod.GET, token, null, PlannedObligationDTO.class);
                actualizeCost(actualizeCostObjArrayList,plannedObligationDTO, false, token, tenantId);
            }
        }


    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ResponseEntity actualizeCost(ArrayList<ActualizeCostObj> actualizeCostObjArrayList, PlannedObligationDTO plannedObligationDTO, boolean isClaimed, String token, String tenantId) throws Exception {
        ResponseEntity responseEntity = null;
        if(isClaimed) {
            for(ActualizeCostObj actualizeCostObj:actualizeCostObjArrayList) {
                checkCanClaimCost(actualizeCostObj.getActualizedCostId(),tenantId,token,actualizeCostObj.getCostType(),actualizeCostObj.getPlannedObligationId());
            }
        }
        for(ActualizeCostObj actualizeCostObj:actualizeCostObjArrayList) {
            actualizeSingleCost(actualizeCostObj,plannedObligationDTO,isClaimed,token,tenantId);
        }
        responseEntity = new ResponseEntity("{\"ActualizationCostDone\":true}", HttpStatus.OK);
        return responseEntity;
    }


    public List<ActualizeCostObj> getCost(String plannedObligationId, String token, String tenantId) {
        List<ActualizeCostObj> finalList = new ArrayList<>();
        ActualizedCost actualizedCostObject = null;
        PlannedObligationDTO plannedObligationDTO = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT +
                        GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + "?plannedObligationId=" + plannedObligationId + "&tenantId=" + tenantId,
                HttpMethod.GET, token, null, PlannedObligationDTO.class);
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
    private boolean getCostFinalInvoiceDone(PlannedObligationDTO plannedObligationDTO, String costId, String tenantId, String token) {
        List<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaList = new ArrayList<>();
        searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria(TENANT_ID, "equals", tenantId));
        searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("plannedObligationId", "equals", plannedObligationDTO.getPlannedObligationId()));
        searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("invoiceStatus", "in", Arrays.asList(APPROVED, SETTLED)));
        searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("costId", "equals", costId));
        searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("cashflowStatus", "equals", ACTIVE));
        List<CashflowDataDTO> cashflowDataResponse = TransactionIdUtil.queryList(baseUrl + CASHFLOW_ROOT + GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST, token, searchCriteriaList, CashflowDataDTO.class);
        return cashflowDataResponse != null && cashflowDataResponse.size() != 0;
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
            List<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaList;
            searchCriteriaList= new ArrayList<>();
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("tenantId",EQUALS,tenantId));
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("costId",EQUALS,costId));
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("quantityStatus",EQUALS,ACTUAL));
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("stage",EQUALS, ACCRUED));
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("cashflowStatus",EQUALS, ACTIVE));
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("type",EQUALS,COST));
            CashflowDataDTO[] cashFlowBaseDTO = TransactionIdUtil.query(baseUrl + CASHFLOW_ROOT+GET_CASHFLOW_BY_CRITERIA, HttpMethod.POST,token,searchCriteriaList,CashflowDataDTO[].class);
            if (cashFlowBaseDTO.length > 1){
                throw new Exception("More than one OR No Cashflow found");
            }
            searchCriteriaList.remove(searchCriteriaList.size() -1);
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("type",EQUALS, COST_REVERSAL));
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
            List<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaList;
            searchCriteriaList= new ArrayList<>();
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("tenantId",EQUALS,tenantId));
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("costId",EQUALS,cost.getActualizedCostId()));
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("quantityStatus",EQUALS,ACTUAL));
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("stage",IN, Arrays.asList(INVOICE_FINAL,INVOICE_FINAL_PROVISIONAL,ACCRUED,ACCRUED_PROVISIONAL)));
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("cashflowStatus",EQUALS, ACTIVE));
            searchCriteriaList.add(new com.taomish.common.searchcriteria.SearchCriteria("type",EQUALS,COST));
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
}
