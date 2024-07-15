package com.taomish.actualization.service;

import com.taomish.actualization.model.ActualizedQuantityObligations;
import com.taomish.actualization.model.BillOfLanding;
import com.taomish.actualization.model.BillOfLandingV2;
import com.taomish.actualization.repo.ActualizationQuantityRepo;
import com.taomish.actualization.repo.BillOfLandingRepo;
import com.taomish.actualization.repo.BillOfLandingV2Repo;
import com.taomish.dtos.ReturnStatus;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.dtos.actualizationservice.ActualizedQuantityObligationsDTO;
import com.taomish.dtos.physicaltradeservice.PlannedObligationDTO;
import com.taomish.dtos.transportactualizationservice.BillOfLandingDTO;
import com.taomish.dtos.transportactualizationservice.SurrenderBlRowsDTO;
import com.taomish.enums.TradeTransactionType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.taomish.constants.ActualizationConstants.ACTUALIZATION_ROOT;
import static com.taomish.constants.ActualizationConstants.GET_ACTUALIZE_QUANTITY_PLANNED_OBLIGATION_ID;
import static com.taomish.constants.PlannedObligationConstants.*;

@Service
public class BillOfLandingService {


    private static final Logger logger = LoggerFactory.getLogger(BillOfLandingService.class);

    @Autowired
    BillOfLandingRepo billOfLandingRepo;

    @Autowired
    BillOfLandingV2Repo billOfLandingV2Repo;

    @Autowired
    ActualizationQuantityRepo actualizationQuantityRepo;

    @Value("${baseUrlEC2DEV}")
    private String baseUrl;


    public BillOfLandingDTO saveBlInfo(BillOfLandingDTO billOfLandingDTO, String token, String tenantId) throws Exception {
        logger.info("Entered to save BL Info for Planned Obligation {} : ", billOfLandingDTO.getPlannedObligationId());
        var billOfLandingList = billOfLandingRepo.findAllByPlannedObligationIdAndSplitSequenceNumberAndTenantIdAndSurrenderedOrderByCreatedTimestampDesc(billOfLandingDTO.getPlannedObligationId(), billOfLandingDTO.getSplitSequenceNumber(), tenantId, false);
        BillOfLanding billOfLanding = null;
        if(!billOfLandingList.isEmpty()) {
            logger.debug("Found Multiple BL for Same Sequence Clearing up Records.count : {}",billOfLandingList.size());
            billOfLanding = billOfLandingList.get(0);
            if(billOfLandingList.size() > 1) {
                billOfLandingList.remove(0);
                billOfLandingRepo.deleteAll(billOfLandingList);
            }
        }
        if (billOfLanding == null) {
            billOfLanding = new BillOfLanding();
        }
        BeanUtils.copyProperties(billOfLandingDTO, billOfLanding, "uuid","actualizationId");
        billOfLanding.setSplitSequenceNumber(billOfLandingDTO.getSplitSequenceNumber());
        billOfLanding.setSurrendered(false);
        billOfLanding.setStatus("Confirmed");
        billOfLanding.setTenantId(tenantId);
        billOfLanding = billOfLandingRepo.save(billOfLanding);
        var output = TransactionIdUtil.convertObject(billOfLanding, BillOfLandingDTO.class);
        output.setTradeTransactionType(billOfLandingDTO.getTradeTransactionType());
        logger.info("Exiting after save BL Info for Planned Obligation {}: ", billOfLandingDTO.getPlannedObligationId());
        return output;
    }

    public ResponseEntity getBLInfo(String plannedObligationId, double splitSequence, String token, String tenantId) {
        logger.info("Entered to get BL Info for Planned Obligation {}: ", plannedObligationId);
        BillOfLanding billOfLanding = billOfLandingRepo.findByPlannedObligationIdAndSplitSequenceNumberAndTenantId(plannedObligationId, splitSequence, tenantId);
        logger.info("Exiting after get BL Info for Planned Obligation {}: ", plannedObligationId);
        return new ResponseEntity(billOfLanding, HttpStatus.OK);
    }

    public List<BillOfLanding> getAllBLInfo(String plannedObligationId, Double splitSequenceNumber, String tenantId) {
        logger.info("Entered to get All  BL Info for Planned Obligation : {}", plannedObligationId);
        List<BillOfLanding> billOfLandingList = null;
        if (splitSequenceNumber != null) {
            billOfLandingList = billOfLandingRepo.findAllByTenantIdAndPlannedObligationIdAndSplitSequenceNumber(tenantId, plannedObligationId, splitSequenceNumber);
        } else {
            billOfLandingList = billOfLandingRepo.findAllByTenantIdAndPlannedObligationId(tenantId, plannedObligationId);
        }
        logger.info("Exiting after get All BL Info for Planned Obligation : {}", plannedObligationId);
        return billOfLandingList.stream().filter(obj -> obj.getBlNumber() != null && !obj.getBlNumber().isEmpty()).toList();
    }

    public ResponseEntity surrenderBLInfo(List<BillOfLanding> blrows, String token, String tenantId) {
        logger.info("Entered to surrender BL Info");
        BillOfLanding billOfLanding;
        BillOfLanding billOfLandingRow = null;
        for (BillOfLanding row : blrows) {
            billOfLanding = billOfLandingRepo.findByPlannedObligationIdAndBlNumberAndTenantId(row.getPlannedObligationId(), row.getBlNumber(), tenantId);
            if (billOfLanding != null) {
                billOfLanding.setSurrendered(true);
                billOfLandingRepo.save(billOfLanding);
                billOfLandingRow = new BillOfLanding();
                billOfLandingRow.setTenantId(tenantId);
                billOfLandingRow.setSurrendered(false);
                billOfLandingRow.setActualizationId(billOfLanding.getActualizationId());
                billOfLandingRow.setPlannedObligationId(billOfLanding.getPlannedObligationId());
                billOfLandingRow.setActualQuantity(billOfLanding.getActualQuantity());
                billOfLandingRow.setUom(billOfLanding.getUom());
                billOfLandingRow.setSplitSequenceNumber(billOfLanding.getSplitSequenceNumber());
                billOfLandingRepo.save(billOfLandingRow);
            }
        }
        logger.info("Exiting after surrender BL Info");
        return new ResponseEntity(new ReturnStatus("Bl Surrendered Succssfully"), HttpStatus.OK);
    }

    public ResponseEntity getBlInfoRowsByPlannedObligationIds(List<String> plannedObligationList, String token, String tenantId) {
        logger.info("Entered to get BL Info Rows by planned obligation ids");
        SurrenderBlRowsDTO surrenderBlRowsDTO = new SurrenderBlRowsDTO();
        List<String> plannedObligationIdListForPlan = null;
        List<PlannedObligationDTO> plannedObligationDTOS = null;
        PlannedObligationDTO plannedObligationDTO = null;
        BillOfLanding billOfLandingRow = null;
        BillOfLandingDTO billOfLandingDTO = null;
        ActualizedQuantityObligations actualizedQuantity = null;
        BillOfLanding billOfLanding;
        List<BillOfLanding> billOfLandingList = null;
        List<ActualizedQuantityObligationsDTO> actualizedQuantityObligationsDTOList = null;
        for (String plannedObligationId : plannedObligationList) {
            plannedObligationDTO = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + "?tenantId=" + tenantId + "&plannedObligationId=" + plannedObligationId, HttpMethod.GET, token, null, PlannedObligationDTO.class);
            plannedObligationIdListForPlan = new ArrayList<>();
            plannedObligationDTOS = TransactionIdUtil.queryList(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLAN_ID + "?tenantId=" + tenantId + "&planId=" + plannedObligationDTO.getPlanId(), HttpMethod.GET, token, null, PlannedObligationDTO.class);
            for (PlannedObligationDTO plannedObligation : plannedObligationDTOS) {
                plannedObligationIdListForPlan.add(plannedObligation.getPlannedObligationId());
            }
            actualizedQuantityObligationsDTOList = TransactionIdUtil.queryList(baseUrl + ACTUALIZATION_ROOT + GET_ACTUALIZE_QUANTITY_PLANNED_OBLIGATION_ID + "?tenantId=" + tenantId + "&plannedObligationId=" + plannedObligationId, HttpMethod.GET, token, null, ActualizedQuantityObligationsDTO.class);
            if (!actualizedQuantityObligationsDTOList.isEmpty()) {
                for (ActualizedQuantityObligationsDTO actualizedQuantityObligationsDTO : actualizedQuantityObligationsDTOList) {
                    billOfLandingRow = billOfLandingRepo.findByPlannedObligationIdAndSplitSequenceNumberAndTenantId(plannedObligationId,actualizedQuantityObligationsDTO.getSplitSequenceNumber(),tenantId);
                    if(billOfLandingRow != null) {
                        if (billOfLandingRow.getSurrendered()) {
                            surrenderBlRowsDTO.getSurrenderRows().add(TransactionIdUtil.convertObject(billOfLandingRow, BillOfLandingDTO.class));
                        } else {
                            billOfLandingDTO = TransactionIdUtil.convertObject(billOfLandingRow, BillOfLandingDTO.class);
                            billOfLandingList = billOfLandingRepo.findAllByActualizationIdAndSplitSequenceNumberAndTenantIdAndSurrendered(billOfLandingDTO.getActualizationId(), billOfLandingDTO.getSplitSequenceNumber(), tenantId, true);
                            if (billOfLandingList != null && billOfLandingList.size() > 0) {
                                billOfLandingDTO.setReplacementBl(true);
                            }
                            billOfLandingDTO.setPlannedObligationList(plannedObligationIdListForPlan);
                            actualizedQuantity = actualizationQuantityRepo.findAllByTenantIdAndPlannedObligationIdAndActualizationId(tenantId, plannedObligationId, billOfLandingDTO.getActualizationId());
                            billOfLandingDTO.setClaimedQuantity(actualizedQuantity != null ? actualizedQuantity.getClaimedQuantity() : 0);
                            billOfLandingDTO.setTradeTransactionType(plannedObligationDTO.getTradeTransactionType());
                            billOfLandingDTO.setTradeId(plannedObligationDTO.getTradeId());
                            surrenderBlRowsDTO.getBlRows().add(billOfLandingDTO);
                        }
                    } else {
                        billOfLanding = new BillOfLanding();
                        billOfLanding.setTenantId(tenantId);
                        billOfLanding.setPlannedObligationId(plannedObligationId);
                        billOfLanding.setActualQuantity(actualizedQuantityObligationsDTO.getLoadQuantity());
                        billOfLanding.setTradeId(plannedObligationDTO.getTradeId());
                        billOfLanding.setUom(actualizedQuantityObligationsDTO.getQuantityUom());
                        billOfLanding.setActualizationId(actualizedQuantityObligationsDTO.getActualizedQuantityId());
                        billOfLanding.setSplitSequenceNumber(actualizedQuantityObligationsDTO.getSplitSequenceNumber());
                        billOfLanding.setSurrendered(false);
                        billOfLandingDTO = TransactionIdUtil.convertObject(billOfLanding, BillOfLandingDTO.class);
                        billOfLandingDTO.setClaimedQuantity(actualizedQuantityObligationsDTO.getClaimedQuantity());
                        billOfLandingDTO.setTradeTransactionType(plannedObligationDTO.getTradeTransactionType());
                        surrenderBlRowsDTO.getBlRows().add(billOfLandingDTO);
                    }
                }
            } else {
                billOfLandingDTO = new BillOfLandingDTO();
                billOfLandingDTO.setTenantId(tenantId);
                billOfLandingDTO.setActualQuantity(plannedObligationDTO.getPlannedQuantity());
                billOfLandingDTO.setUom(plannedObligationDTO.getQuantityUOM());
                billOfLandingDTO.setTradeId(plannedObligationDTO.getTradeId());
                billOfLandingDTO.setPlannedObligationId(plannedObligationId);
                billOfLandingDTO.setSurrendered(false);
                billOfLandingDTO.setReplacementBl(false);
                billOfLandingDTO.setStatus("Draft");
                billOfLandingDTO.setSplitSequenceNumber(0);
                billOfLandingDTO.setTradeTransactionType(plannedObligationDTO.getTradeTransactionType());
                surrenderBlRowsDTO.getBlRows().add(billOfLandingDTO);
            }
        }
        logger.info("Exiting after get BL Info Rows by planned obligation ids");
        return new ResponseEntity(surrenderBlRowsDTO, HttpStatus.OK);
    }

    public SurrenderBlRowsDTO getBlInfoRowsByPlanId(String planId, String token, String tenantId) {
        logger.info("Entered to get BL Info Rows by planned obligation ids");
        SurrenderBlRowsDTO surrenderBlRowsDTO = new SurrenderBlRowsDTO();
        var blList = billOfLandingV2Repo.findByPlanIdAndTenantId(planId, tenantId);
        for (var bl : blList) {
            if (bl.getActualizationId() != null) {
                if (bl.getSurrendered()) {
                    surrenderBlRowsDTO.getSurrenderRows().add(TransactionIdUtil.convertObject(bl, BillOfLandingDTO.class));
                } else {
                    var billOfLandingDTO = TransactionIdUtil.convertObject(bl, BillOfLandingDTO.class);
//                    billOfLandingDTO.setPlannedObligationList(plannedObligationIdListForPlan);
                    billOfLandingDTO.setClaimedQuantity(bl.getClaimedQuantity());
                    billOfLandingDTO.setTradeTransactionType(bl.getTradeTransactionType());
                    billOfLandingDTO.setTradeId(bl.getTradeId());
                    surrenderBlRowsDTO.getBlRows().add(billOfLandingDTO);
                }
            } else {
                var actualizedQuantityObligationsDTOList = TransactionIdUtil.queryList(baseUrl + ACTUALIZATION_ROOT + GET_ACTUALIZE_QUANTITY_PLANNED_OBLIGATION_ID + "?tenantId=" + tenantId + "&plannedObligationId=" + bl.getPlannedObligationId(), HttpMethod.GET, token, null, ActualizedQuantityObligationsDTO.class);
                if (!actualizedQuantityObligationsDTOList.isEmpty()) {
                    for (ActualizedQuantityObligationsDTO actualizedQuantityObligationsDTO : actualizedQuantityObligationsDTOList) {
                        var billOfLanding = getBillOfLanding(tenantId, bl, actualizedQuantityObligationsDTO);
//                        billOfLandingRepo.save(billOfLanding);
                        var billOfLandingDTO = TransactionIdUtil.convertObject(billOfLanding, BillOfLandingDTO.class);
                        billOfLandingDTO.setClaimedQuantity(actualizedQuantityObligationsDTO.getClaimedQuantity());
                        billOfLandingDTO.setTradeTransactionType(bl.getTradeTransactionType());
                        surrenderBlRowsDTO.getBlRows().add(billOfLandingDTO);
                    }
                } else {
                    var billOfLandingDTO = getBillOfLandingDTO(tenantId, bl);
                    surrenderBlRowsDTO.getBlRows().add(billOfLandingDTO);
                }
            }
        }
        logger.info("Exiting after get BL Info Rows by planned obligation ids");
        return surrenderBlRowsDTO;
    }

    @NotNull
    private static BillOfLandingDTO getBillOfLandingDTO(String tenantId, BillOfLandingV2 bl) {
        var billOfLandingDTO = new BillOfLandingDTO();
        billOfLandingDTO.setTenantId(tenantId);
        billOfLandingDTO.setActualQuantity(bl.getPlannedQuantity());
        billOfLandingDTO.setUom(bl.getQuantityUom());
        billOfLandingDTO.setTradeId(bl.getTradeId());
        billOfLandingDTO.setPlannedObligationId(bl.getPlannedObligationId());
        billOfLandingDTO.setSurrendered(false);
        billOfLandingDTO.setReplacementBl(false);
        billOfLandingDTO.setStatus("Draft");
        billOfLandingDTO.setSplitSequenceNumber(0);
        billOfLandingDTO.setTradeTransactionType(bl.getTradeTransactionType());
        return billOfLandingDTO;
    }

    @NotNull
    private static BillOfLanding getBillOfLanding(String tenantId, BillOfLandingV2 bl, ActualizedQuantityObligationsDTO actualizedQuantityObligationsDTO) {
        var billOfLanding = new BillOfLanding();
        billOfLanding.setTenantId(tenantId);
        billOfLanding.setPlannedObligationId(bl.getPlannedObligationId());
        billOfLanding.setActualQuantity(actualizedQuantityObligationsDTO.getLoadQuantity());
        billOfLanding.setTradeId(bl.getTradeId());
        billOfLanding.setUom(actualizedQuantityObligationsDTO.getQuantityUom());
        billOfLanding.setActualizationId(actualizedQuantityObligationsDTO.getActualizedQuantityId());
        billOfLanding.setSplitSequenceNumber(actualizedQuantityObligationsDTO.getSplitSequenceNumber());
        billOfLanding.setSurrendered(false);
        return billOfLanding;
    }

    public ResponseEntity merge(List<BillOfLandingDTO> billOfLandingDTOS, Integer mergeCount, String token, String tenantId) {
        logger.info("Entered after merge BL Info Rows by planned obligation ids");
        double sumQuantity = 0.0;
        BillOfLanding billOfLanding;
        if (mergeCount < billOfLandingDTOS.size()) {
            if (canMerge(billOfLandingDTOS, token, tenantId)) {
                for (BillOfLandingDTO billOfLandingDTO : billOfLandingDTOS) {
                    sumQuantity += billOfLandingDTO.getActualQuantity();
                    billOfLanding = billOfLandingRepo.findByPlannedObligationIdAndBlNumberAndTenantIdAndSurrendered(billOfLandingDTO.getPlannedObligationId(), billOfLandingDTO.getBlNumber(), tenantId, false);
                    if (billOfLanding != null) {
                        billOfLanding.setSurrendered(true);
                        billOfLandingRepo.save(billOfLanding);
                    }
                }
                BillOfLanding billOfLandingRow = null;
                for (int i = 0; i < mergeCount; i++) {
                    billOfLandingRow = new BillOfLanding();
                    billOfLandingRow.setTenantId(tenantId);
                    billOfLandingRow.setSurrendered(false);
                    billOfLandingRow.setPlannedObligationId(billOfLandingDTOS.get(0).getPlannedObligationId());
                    billOfLandingRow.setActualQuantity(sumQuantity / mergeCount);
                    billOfLandingRow.setUom(billOfLandingDTOS.get(0).getUom());
                    billOfLandingRow.setSplitSequenceNumber(i++);
                    billOfLandingRepo.save(billOfLandingRow);
                }
            } else {
                return new ResponseEntity(new ReturnStatus("Unable to merge BL from Different Plans"), HttpStatus.BAD_REQUEST);
            }
        }
        logger.info("Exiting after merge BL Info Rows by planned obligation ids");
        return new ResponseEntity(new ReturnStatus("Merge Successfull"), HttpStatus.OK);
    }

    private boolean canMerge(List<BillOfLandingDTO> billOfLandingDTOS, String token, String tenantId) {
        boolean canMerge = true;
        PlannedObligationDTO plannedObligationDTO = null;
        String planId = "";
        for (BillOfLandingDTO billOfLandingDTO : billOfLandingDTOS) {
            plannedObligationDTO = TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + "?tenantId=" + tenantId + "&plannedObligationId=" + billOfLandingDTO.getPlannedObligationId(), HttpMethod.GET, token, null, PlannedObligationDTO.class);
            if (planId == null || planId.length() == 0) {
                planId = plannedObligationDTO.getPlanId();
            } else {
                if (!planId.equals(plannedObligationDTO.getPlanId())) {
                    canMerge = false;
                    break;
                }
            }
        }
        return canMerge;
    }

    public BillOfLandingDTO confirmBL(String plannedObligationId, String blNumber, String token, String tenantId) {
        logger.info("Entered to confirm BL for Planned Obligation Id {}: ", plannedObligationId);
        var billOfLanding = billOfLandingRepo.findByPlannedObligationIdAndBlNumberAndTenantId(plannedObligationId, blNumber, tenantId);
        if (billOfLanding != null) {
            billOfLanding.setStatus("Final");
            billOfLanding = billOfLandingRepo.save(billOfLanding);
        }
        logger.info("Exiting after confirm BL for Planned Obligation Id {} : ", plannedObligationId);
        var output = TransactionIdUtil.convertObject(billOfLanding, BillOfLandingDTO.class);
        output.setTradeTransactionType(TradeTransactionType.SELL);
        return output;
    }

    public BillOfLandingDTO getBLInfoForInventory(String plannedObligationId, String tenantId, String token) {
        return TransactionIdUtil.convertObject(billOfLandingRepo.findByPlannedObligationIdAndTenantId(plannedObligationId, tenantId), BillOfLandingDTO.class);
    }
}
