package com.taomish.actualization.v2.service;

import com.taomish.actualization.model.ActualizedQuantityObligations;
import com.taomish.actualization.model.BillOfLanding;
import com.taomish.actualization.repo.ActualizationQuantityRepo;
import com.taomish.actualization.repo.BillOfLandingRepo;
import com.taomish.actualization.v2.dtos.ActualizationGiDto;
import com.taomish.common.domain.TaomishError;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.dtos.ReturnStatus;
import com.taomish.dtos.actualizationservice.ActualizeObjV2;
import com.taomish.dtos.oisservice.PurchaseOrderDto;
import com.taomish.dtos.physicaltradeservice.PlannedObligationDTO;
import com.taomish.enums.PlannedObligationState;
import com.taomish.web.security.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.taomish.RestEndPoints.PhysicalRestEndPoint.*;
import static com.taomish.constants.ActualizationConstants.PLANNED_OBLI_ID;
import static com.taomish.constants.ActualizationConstants.QUE_OBLIGATION_ID;
import static com.taomish.constants.PlannedObligationConstants.*;

@Service
public class GIService {

    private static final Logger logger = LoggerFactory.getLogger(GIService.class);
    private final ActualizationServiceV2 actualizationServiceV2;
    private final ActualizationQuantityRepo actualizationQuantityRepo;
    private final GRNService grnService;

    private final BillOfLandingRepo billOfLandingRepo;

    @Value("${baseUrlEC2DEV}")
    private String baseUrl;

    public GIService(ActualizationServiceV2 actualizationServiceV2, ActualizationQuantityRepo actualizationQuantityRepo, GRNService grnService, BillOfLandingRepo billOfLandingRepo) {
        this.actualizationServiceV2 = actualizationServiceV2;
        this.actualizationQuantityRepo = actualizationQuantityRepo;
        this.grnService = grnService;
        this.billOfLandingRepo = billOfLandingRepo;
    }


    @Transactional
    public ReturnStatus actualizationGI(List<ActualizationGiDto> actualizationGiDtos, User principal, String token)throws TaomishError {
        logger.info("entered into GIService.actualizationGI function");

        List<String> plannedObligationIds = actualizationGiDtos.stream()
                .map(ActualizationGiDto::getPlannedObligationId)
                .filter(Objects::nonNull)
                .toList();
        List<PlannedObligationDTO> plannedObligationDTOList =  new ArrayList<PlannedObligationDTO>();
        var plannedObligationsSearchCriteria = new ArrayList<com.taomish.common.searchcriteria.SearchCriteria>();
        plannedObligationsSearchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(TEN_ANT_ID, EQUA_LS, principal.getTenantId()));
        plannedObligationsSearchCriteria.add(new com.taomish.common.searchcriteria.SearchCriteria(PLANNED_OBLI_ID, "in", plannedObligationIds));
        if(!plannedObligationIds.isEmpty()) {
            plannedObligationDTOList = TransactionIdUtil.queryList(baseUrl + PLANNED_OBLIGATION_ROOT + GET_PLANNED_OBLIGATION_BY_CRITERIA + QUE_TENANT_ID + principal.getTenantId(), HttpMethod.POST, token, plannedObligationsSearchCriteria, PlannedObligationDTO.class);
            var errorList = actualizationServiceV2.canObligationActualizeInGrn(plannedObligationDTOList);
            if (!errorList.isEmpty()) {
                throw new TaomishError("GI actualization failed with below errors : " + String.join(",", errorList));
            }
        }

        for (ActualizationGiDto actualizationGiDto : actualizationGiDtos) {
            PurchaseOrderDto purchaseOrderDto = new PurchaseOrderDto();
            PlannedObligationDTO plannedObligation = new PlannedObligationDTO();
            plannedObligation = plannedObligationDTOList.stream().filter(e -> (e.getPlannedObligationId().equalsIgnoreCase(actualizationGiDto.getPlannedObligationId()))).toList().getFirst();
            List<BillOfLanding> billOfLanding = billOfLandingRepo.findAllByTenantIdAndPlannedObligationId(principal.getTenantId(),plannedObligation.getPlannedObligationId());
            Integer sequenceNumber = actualizationGiDto.getSplitSequenceNumber() == null ? 0 : actualizationGiDto.getSplitSequenceNumber();
            double actualizedQuantity = actualizationGiDto.getActualizeQuantity();
            ActualizedQuantityObligations actualizedQuantityObligations = actualizationQuantityRepo.findByPlannedObligationIdAndSplitSequenceNumberAndTenantId(plannedObligation.getPlannedObligationId(), sequenceNumber, principal.getTenantId());
            if (actualizedQuantityObligations == null) {
                ActualizeObjV2 actualizeObjV2 =new ActualizeObjV2();
                actualizeObjV2.setPlannedObligationId(actualizationGiDto.getPlannedObligationId());
                actualizeObjV2.setAdjusted(0.0);
                actualizeObjV2.setLossGainQuantity(0.0);
                actualizeObjV2.setDischargeDate(actualizationGiDto.getActualizeDate());
                actualizeObjV2.setGrnQuantity(actualizedQuantity);
                actualizeObjV2.setSplitSequenceNumber(sequenceNumber);
                actualizedQuantityObligations = actualizationServiceV2.createActualizedQuantityObj(actualizeObjV2, plannedObligation, purchaseOrderDto, principal, token);
            }else if (!billOfLanding.isEmpty()){
                actualizedQuantityObligations.setReceivedQuantity(actualizedQuantityObligations.getReceivedQuantity() + actualizedQuantity);
            } else {
                actualizedQuantityObligations.setLoadQuantity(actualizedQuantityObligations.getLoadQuantity() + actualizedQuantity);
                actualizedQuantityObligations.setUnloadQuantity(actualizedQuantityObligations.getUnloadQuantity() + actualizedQuantity);
            }


            logger.info("Saving Quantity Actualization for ObligationId: {}", actualizedQuantityObligations.getPlannedObligationId());
            actualizedQuantityObligations=actualizationQuantityRepo.save(actualizedQuantityObligations);

            var invActualizeResp = grnService.actulizeInventory(actualizationGiDto.getTransferId(), actualizationGiDto.getActualizeQuantity(),
                    "actualize", actualizedQuantityObligations.getActualizationId(), actualizedQuantityObligations.getSplitSequenceNumber(), null, principal, token);
            if (invActualizeResp.getStatusType().equalsIgnoreCase("error")) {
                logger.error("error-in-actualizing-inventory: " + invActualizeResp.getStatus());
                throw new TaomishError("error-in-actualizing-inventory");
            }
            if (!plannedObligation.getObligationState().isEmpty() && !plannedObligation.getObligationState().get(PlannedObligationState.ACTUALIZED) && billOfLanding.isEmpty()) {
                try {
                    actualizationServiceV2.actualizeObligationInGrn(plannedObligation, false, 0, token, principal);
                } catch (Exception ex) {
                    logger.error("Update of planned Obligation failed due to :" + ex);
                    logger.info("Reverting the stock-movement record update, for transferId=" + actualizationGiDto.getTransferId());
                    grnService.actulizeInventory(actualizationGiDto.getTransferId(), actualizationGiDto.getActualizeQuantity(),
                            "deactualize", actualizedQuantityObligations.getActualizationId(), actualizedQuantityObligations.getSplitSequenceNumber(), null, principal, token);
                    throw new TaomishError("Failed to update planned Obligation due to");
                }
            }
        }
        return  ReturnStatus.successInstance("Actualization GI Completed !!");
    }


    @Transactional
    public ReturnStatus deactualizationGI(List<ActualizationGiDto> actualizationGiDtos, User principal, String token)throws TaomishError {

        logger.info("entered in deactualizationGI function");
        String tenantId = principal.getTenantId();
        String errorFound = "";
        List<String> plannedObligationList = actualizationGiDtos.stream().map(ActualizationGiDto::getPlannedObligationId).toList();
        var errors = grnService.canDeactualize(plannedObligationList, tenantId, token);
        if(!errors.isEmpty()) {
            throw new TaomishError("De-Actualization failed with errors: "+String.join(",",errors));
        }
        for(ActualizationGiDto actualizationGiDto : actualizationGiDtos){
            PlannedObligationDTO plannedObligation = new PlannedObligationDTO();
            plannedObligation= TransactionIdUtil.query(baseUrl + PLANNED_OBLIGATION_ROOT +GET_PLANNED_OBLIGATIONS_BY_PLANNED_OBLIGATION_ID + QUE_OBLIGATION_ID + actualizationGiDto.getPlannedObligationId() + AND_TENANT_ID + tenantId,
                        HttpMethod.GET, token, null, PlannedObligationDTO.class);
            try {
                Integer splitSequenceNumber = actualizationGiDto.getSplitSequenceNumber() == null ? 0 : actualizationGiDto.getSplitSequenceNumber();;
                String actualizationId = actualizationGiDto.getActualizeId();
                List<BillOfLanding> billOfLanding = billOfLandingRepo.findAllByTenantIdAndPlannedObligationId(principal.getTenantId(),plannedObligation.getPlannedObligationId());
                if(actualizationId!=null){
                    ActualizedQuantityObligations actualizedQuantityObligations = actualizationQuantityRepo.findByPlannedObligationIdAndSplitSequenceNumberAndTenantId(plannedObligation.getPlannedObligationId(),splitSequenceNumber,tenantId);
                    if(actualizedQuantityObligations != null){
                        double quantity = actualizedQuantityObligations.getUnloadQuantity() - actualizationGiDto.getActualizeQuantity(); // TODO How to get actualize Quantity
                        if(!billOfLanding.isEmpty()){
                            actualizedQuantityObligations.setReceivedQuantity(actualizedQuantityObligations.getReceivedQuantity() - quantity);
                        }
                        else if(quantity<=0){
                            actualizationQuantityRepo.delete(actualizedQuantityObligations);
                        }else{
                            actualizedQuantityObligations.setLoadQuantity(quantity);
                            actualizedQuantityObligations.setUnloadQuantity(quantity);
                            actualizationQuantityRepo.save(actualizedQuantityObligations);
                        }
                    }
                }

                var invActualizeResp = grnService.actulizeInventory(actualizationGiDto.getTransferId(),actualizationGiDto.getActualizeQuantity(),"deactualize",
                            actualizationId,splitSequenceNumber,null,principal,token);
                if(invActualizeResp.getStatusType().equalsIgnoreCase("error")){
                    logger.error("error while deactualize the stock record "+invActualizeResp.getStatus());
                    throw new TaomishError(actualizationGiDto.getTransferId() + " deactualize failed with errors: " + invActualizeResp.getStatus());
                }
                    //   Deactualize the plannedObligation
                if(plannedObligation.getObligationState().get(PlannedObligationState.ACTUALIZED) && billOfLanding.isEmpty()){
                    try{
                        grnService.deActualizeObligationInGrn(Collections.singletonList(plannedObligation),token,principal);
                    }catch (Exception ex){
                            logger.error("Failed to update the planned obligation in deActualizeObligationInGrn() due to :" + ex);
                            logger.info("Reverting update of the stock-movement record for transferId=" + actualizationGiDto.getTransferId());
                        grnService.actulizeInventory(actualizationGiDto.getTransferId(),actualizationGiDto.getActualizeQuantity(),"actualize",
                                    actualizationId,splitSequenceNumber,null,principal,token);
                            throw new TaomishError("Failed to update the obligation " + ex.getMessage());
                        }
                }
            }catch(Exception ex){
                errorFound = (TransactionIdUtil.getErrorResponse(ex).getStatus());
                logger.error("failed to deactualize GI record ",ex);
                throw new TaomishError("Falied to deactualize");
            }
        }
        return ReturnStatus.successInstance("De-Actualization Done");
    }

}


