package com.taomish.actualization.controller;


import com.taomish.actualization.dto.ActualizeCostObj;
import com.taomish.actualization.dto.ActualizeObj;
import com.taomish.actualization.model.ActualizedDocuments;
import com.taomish.common.domain.TaomishError;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.dtos.actualizationservice.ActualizedDocumentsDTO;
import com.taomish.actualization.model.BillOfLanding;
import com.taomish.actualization.service.*;
import com.taomish.dtos.ReturnStatus;
    import com.taomish.common.searchcriteria.SearchCriteria;
import com.taomish.dtos.actualizationservice.ActulalizeCostPlannedObligationDTO;
import com.taomish.dtos.costservice.ActualizedCostDTO;
import com.taomish.dtos.physicaltradeservice.PlannedObligationDTO;
import com.taomish.dtos.transportactualizationservice.BillOfLandingDTO;
import com.taomish.dtos.transportactualizationservice.SurrenderBlRowsDTO;
import com.taomish.dtos.transportactualizationservice.TransportActualizationDataFetchObj;
import com.taomish.web.security.models.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.taomish.constants.ActualizationConstants.*;
import static com.taomish.constants.TradeCostConstants.GET_COST_BY_CRITERIA;
import static com.taomish.constants.TransportActualizationConstants.*;


@RestController
@RequestMapping(value = ACTUALIZATION_ROOT)
public class ActualizationController {

    @Autowired
    ActualizationService actualizationService;

    @Autowired
    ActualizeCostService actualizeCostService;

    @Autowired
    ActualizeQualityService actualizeQualityService;

    @Autowired
    ActualizeQuantityService actualizeQuantityService;

    @Autowired
    BillOfLandingService billOfLandingService;


    private static final Logger logger = LoggerFactory.getLogger(ActualizationController.class);

    /**
     * return stats of partially allocated,allocated and planned count
     * @return
     * @throws Exception
     */
    @RequestMapping(method = RequestMethod.GET, produces = "application/json", value = STATS)
    @ResponseBody
    public ResponseEntity<?> getStats(@RequestParam String tenantId,@RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to ActualizationController.getStats()");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(actualizationService.getStats(tenantId,token), HttpStatus.OK);
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to get stats"),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to get stats",e);
        }
        logger.info("Exited from  ActualizationController.getStats()");
        return response;
    }

    /**
     * return stats of partially allocated,allocated and planned count
     * @return
     * @throws Exception
     */
    @RequestMapping(method = RequestMethod.GET, produces = "application/json", value = GET_PLANS)
    @ResponseBody
    public ResponseEntity<?> getPlans(@RequestParam String tenantId,@RequestParam int page, @RequestParam int size,@RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to ActualizationController.getPlans()");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity<>(actualizationService.getPlanTradeList(tenantId,page,size,token), HttpStatus.OK);
        } catch (Exception e) {
            response = new ResponseEntity<>(new ReturnStatus("Failed to get plans"),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to get Plans",e);

        }
        logger.info("Exited from ActualizationController.getPlans()");
        return response;
    }

    @RequestMapping(method = RequestMethod.GET, produces = "application/json", value = GET_PLANS_BY_PLANID)
    @ResponseBody
    public ResponseEntity<?> getPlanByPlanId(@RequestParam String tenantId,@RequestParam String planId,@RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to get Plans list");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(actualizationService.getPlanTradeListByPlanId(tenantId,planId,token), HttpStatus.OK);
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to get plans"),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to get Plans",e);
        }
        return response;
    }

    /**
     * return stats of partially allocated,allocated and planned count
     * @return
     * @throws Exception
     */
    @RequestMapping(method = RequestMethod.GET, produces = "application/json", value = GET_PLANNED_OBLIGATIONS)
    @ResponseBody
    public ResponseEntity<?> getPlanObligations(@RequestParam String planId,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token){
        logger.info("Entered to ActualizationController.getPlanObligations() list for plan id {}:",planId);
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(actualizationService.getPlannedObligationList(planId,tenantId, token),HttpStatus.OK);
            logger.info("Succesfully fetched Planned Obligations List for plan id {}:",planId);
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to get planned Obligations"),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to get Planned Obligations",e);
        }
        logger.info("Exited from ActualizationController.getPlanObligations() list for plan id {}:",planId);
        return response;
    }

    @RequestMapping(method = RequestMethod.POST,consumes = "application/json", value = ACTUALIZE_COST)
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<?> actualizeCost(@RequestBody ActualizeObj actualizeObj,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to  ActualizationController.actualizeCost()");
        ResponseEntity response = null;
        try {
            boolean  isClaimed = false;
            response = actualizeCostService.actualizeCost(actualizeObj,isClaimed, token,tenantId);
            logger.info("Cost actualization done Successfully !");
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to get cost actualization "),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to get cost actualization ",e);
        }
        logger.info("Exited from  ActualizationController.actualizeCost()");
        return response;
    }

    @RequestMapping(method = RequestMethod.POST,consumes = "application/json", value = ACTUALIZE_COST_FOR_TRANSPORT_ACTUALIZATION)
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<?> actualizeCostForTransportActualization(@RequestBody ActualizeObj actualizeObj,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to  ActualizationController.actualizeCostForTransportActualization()");
        ResponseEntity response = null;
        try {
            boolean  isClaimed = false;
            response = actualizeCostService.actualizeCostForTransportActualization(actualizeObj,isClaimed, token,tenantId);
            logger.info("Cost  transport actualization done Successfully !");
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to Cost  transport actualization"),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to tCost  transport actualization." ,e);
        }
        logger.info("Exited from  ActualizationController.actualizeCostForTransportActualization()");
        return response;
    }

    @PostMapping("/actulaizetheCostFromCharges")
    public ReturnStatus ActulizetheCostFromCharges(@RequestBody ActulalizeCostPlannedObligationDTO actulalizeCostPlannedObligationDTO,
                                           @Parameter(hidden = true) @AuthenticationPrincipal User principal,
                                           @Parameter(hidden = true) @RequestHeader("Authorization") String authorization) throws Exception {
       return this.actualizeCostService.AcutalizeandCreatCost(actulalizeCostPlannedObligationDTO,principal,authorization);

    }
    @GetMapping("/getActulaizationfromChargeId")
    public List<ActualizedCostDTO> ActulizetheCostFromCharges(@RequestParam String chargeId,
                                                   @Parameter(hidden = true) @AuthenticationPrincipal User principal,
                                                   @Parameter(hidden = true) @RequestHeader("Authorization") String authorization) throws Exception {
        return this.actualizeCostService.getactulaizedfromChargeId(chargeId,principal,authorization);

    }

    @RequestMapping(method = RequestMethod.GET,produces = "application/json", value = GET_ACTUALIZATION_COST_BY_TRADEID_AND_COSTID)
    @ResponseBody
    public ResponseEntity<?> getActualizeCostByTradeIdAndCostID(@RequestParam String tenantId,
                                                                @RequestParam String tradeId,
                                                                @RequestParam String costId) {
        logger.info("Entered to ActualizationController.getActualizeCostByTradeIdAndCostID()");
        ResponseEntity response = null;
        try {
            response = actualizeCostService.getActualizedCost(tenantId,tradeId,costId);
            logger.info("fetch actualization cost  done Successfully ! ");
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to fetch actaulize Cost by trade Id"),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to fetch actaulize Cost by trade Id {} : ",tradeId,e);
        }
        logger.info("Exited from ActualizationController.getActualizeCostByTradeIdAndCostID()");
        return response;
    }

    @RequestMapping(method = RequestMethod.POST,consumes = "application/json", value = ACTUALIZE_QUALITY)
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<?> actualizeQuality(@RequestBody ActualizeObj actualizeObj,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered to ActualizationController.actualizeQuality()");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(actualizeQualityService.actualizeQualitySpec(actualizeObj, token,tenantId),HttpStatus.OK);
            logger.info("Quality Actualization done successfully !");
        } catch (Exception e) {
            logger.error("Failed to actualize quality :",e);
            response = new ResponseEntity(new ReturnStatus("Failed to actualize quality"),HttpStatus.INTERNAL_SERVER_ERROR);
        }
        logger.info("Exited from ActualizationController.actualizeQuality()");
        return response;
    }

    @RequestMapping(method = RequestMethod.POST,consumes = "application/json", value = ACTUALIZE_QUALITY_FOR_TRANSPORT_ACTUALIZATION)
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<?> actualizeQualityForTransportActualization(@RequestBody ActualizeObj actualizeObj,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered to ActualizationController.actualizeQualityForTransportActualization()");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(actualizeQualityService.actualizeQualitySpecForTransportActualization(actualizeObj,false, token,tenantId),HttpStatus.OK);
            logger.info("Quality Actualization done successfully !");
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to actualize quality for transport"),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to actualize quality for transport",e);
        }
        logger.info("Exited from  ActualizationController.actualizeQualityForTransportActualization()");
        return response;
    }

    @RequestMapping(method = RequestMethod.POST,produces = "application/json", consumes = "application/json", value = ACTUALIZE_QUANTITY)
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<?> actualizeQuantity(@RequestBody ActualizeObj actualizeObj,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to ActualizationController.actualizeQuantity()");
        ResponseEntity response = null;
        try {
            Boolean isClaimed = false;
            response =  actualizeQuantityService.actualizeQuantity(actualizeObj,isClaimed, token,tenantId);
            logger.info("Quantity actualization done successfully ! ");
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to actualize quantity "),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.info("Exited from ActualizationController.actualizeQuantity()",e);
        }
        return response;
    }

    @RequestMapping(method = RequestMethod.POST,produces = "application/json", consumes = "application/json", value = ACTUALIZE_QUANTITY_FOR_TRANSPORT_ACTUALIZATION)
    @ResponseBody
    public ResponseEntity<ReturnStatus> actualizeQuantityForTransportActualization(@RequestBody ActualizeObj actualizeObj,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to ActualizationController.actualizeQuantityForTransportActualization()");
        ResponseEntity<ReturnStatus> response = null;
        try {
            var status =  actualizeQuantityService.actualizeQuantityForTransportActulization(actualizeObj,false, token,tenantId);
            try {
                actualizeQuantityService.addAutoCosts(actualizeObj, token, tenantId);
            } catch (Exception e) {
                status = ReturnStatus.successInstance("Actualization Done Successfully. Cost auto actualization was not success");
            }
            response = new ResponseEntity<>(status,HttpStatus.OK);
            logger.info("Quantity actualization done successfully :!");
        } catch (Exception e) {
            response = new ResponseEntity<>(TransactionIdUtil.getErrorResponse(e),HttpStatus.BAD_REQUEST);
            logger.error("Failed to actualize quantity :",e);
        }
        logger.info("Exited from ActualizationController.actualizeQuantityForTransportActualization()");
        return response;
    }

    @RequestMapping(method = RequestMethod.GET,produces = "application/json",  value = GET_ACTUALIZE_QUANTITY_PLANNED_OBLIGATION_ID)
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<?> actualizeQuantitybyplannedobligationid(@RequestParam String plannedObligationId,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to ActualizationController.actualizeQuantitybyplannedobligationid()");
        ResponseEntity response = null;
        try {
            response =  actualizeQuantityService.getActualizedQuantityObligationByPlannedObligationId(tenantId,plannedObligationId);
            logger.info("Quantity actualization done successfully !");
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to actualize quantity by planned obligation Id:"),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to actualize quantity by planned obligation Id:",e);
        }
        logger.info("Exited from ActualizationController.actualizeQuantitybyplannedobligationid()");
        return response;
    }

    @RequestMapping(method = RequestMethod.POST,consumes = "application/json", value = DEACTUALIZE )
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<ReturnStatus> deactualize(@RequestBody List<PlannedObligationDTO> obligationList,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to ActualizationController.deactualize()");
        ResponseEntity<ReturnStatus> response = null;
        try {
            var status = actualizeQuantityService.deActualize(obligationList,token,tenantId);
            response =  new ResponseEntity<>(status,status.isSuccessInstance()?HttpStatus.OK:HttpStatus.BAD_REQUEST);
            logger.info("Deleted successfully");
        } catch (Exception e) {
            response = new ResponseEntity<>(TransactionIdUtil.getErrorResponse(e),HttpStatus.BAD_REQUEST);
            logger.error("Failed to delete actualization object",e);
        }
        logger.info("Exited from ActualizationController.deactualize()");
        return response;
    }

    @RequestMapping(method = RequestMethod.POST,consumes = "application/json", value = SAVE_DOCUMENT)
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<?> saveDocument(@RequestBody ActualizedDocumentsDTO actualizedDocuments, @RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to ActualizationController.saveDocument()");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(actualizationService.saveDocument(actualizedDocuments, token,tenantId),HttpStatus.OK);
            logger.info("Actualized document saved successfully !");
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to save actualized document:"),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to save actualized document:",e);
        }
        logger.info("Exited from ActualizationController.saveDocument()");
        return response;
    }

    @RequestMapping(method = RequestMethod.GET,produces = "application/json",value = GET_ACTUALIZATION_QTY_BY_PLANNED_OBLIGATION_ID)
    @ResponseBody
    public ResponseEntity<?> getActualizedQuantity(@RequestParam String tenantId, @RequestParam String plannedObligationId,@RequestParam(required = false) Double splitNumber, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to get Actualized Quantity for planned Obligation id {}: ",plannedObligationId);
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(actualizeQuantityService.getQuantity(tenantId, plannedObligationId,splitNumber, token),HttpStatus.OK);
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to get actualized Quantity for planned Obligation id : "+plannedObligationId),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to get actualized Quantity for planned Obligation id {} : ",plannedObligationId,e);
        }
        logger.info("Exited from ActualizationController.getActualizedQuantity()");
        return response;
    }

    @RequestMapping(method = RequestMethod.GET,produces = "application/json",value = GET_QUALITY)
    @ResponseBody
    public ResponseEntity<?> getActualizedQuality(@RequestParam String tenantId, @RequestParam String plannedObligationId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to get Actualized Quality for planned Obligation id {} : ",plannedObligationId);
        ResponseEntity response = null;
        try {
            response =  new ResponseEntity(actualizationService.getQuality(tenantId, plannedObligationId, token),HttpStatus.OK);
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to get Actualized Quality for planned obligation id : "+plannedObligationId),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to get Actualized Quality for planned obligation id {}: ",plannedObligationId,e);
        }
        logger.info("Exited from get Actualized Quality for planned Obligation id {} : ",plannedObligationId);
        return response;
    }

    @RequestMapping(method = RequestMethod.POST,produces = "application/json",consumes = "application/json",value = GET_QUALITY_FOR_TRANSPORT_ACTUALIZATION)
    @Operation(summary = "Get all Quality specs")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Get all Quality specs",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ),
            @ApiResponse(responseCode = "500", description = "Error while getting Quality specs.",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @ResponseBody
    public ResponseEntity<?> getActualizedQualityForTransportActualization(@RequestParam String tenantId, @RequestBody TransportActualizationDataFetchObj transportActualizationDataFetchObj, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to  ActualizationController.getActualizedQualityForTransportActualization()");
        ResponseEntity response = null;
        try {
            response =  new ResponseEntity(actualizationService.getQualityForTransportActualization(tenantId, transportActualizationDataFetchObj, token),HttpStatus.OK);
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to get Actualized Quality:"),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to get Actualized Quality:",e);
        }
        logger.info("Exited from  ActualizationController.getActualizedQualityForTransportActualization()");
        return response;
    }

    @RequestMapping(method = RequestMethod.POST,produces = "application/json",consumes = "application/json",value = GET_ACTUALIZED_QUALITY_BY_CRITERIA)
    @Operation(summary = "Get all Quality specs")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Get all Quality specs",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ),
            @ApiResponse(responseCode = "500", description = "Error while getting Quality specs.",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @ResponseBody
    public ResponseEntity<?> getActualizedQualityByCriteria(@RequestBody List<SearchCriteria> searchCriteria,@AuthenticationPrincipal User user) {
        logger.info("Entered to  ActualizationController.getActualizedQualityByCriteria()");
        ResponseEntity response = null;
        try {
            response =  new ResponseEntity(actualizationService.getActualizedQualityByCriteria(searchCriteria,user),HttpStatus.OK);
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to get Actualized Quality"),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to get Actualized Quality",e);
        }
        logger.info("Exited from ActualizationController.getActualizedQualityByCriteria()");
        return response;
    }

    @RequestMapping(method = RequestMethod.GET,produces = "application/json",value = GET_COST)
    @ResponseBody
    public ResponseEntity<?> getActualizedCost(@RequestParam String plannedObligationId,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to get actualize cost for planned Obligation id {} : ",plannedObligationId);
        ResponseEntity response = null;
        try {
            response =  new ResponseEntity(actualizationService.getCost(plannedObligationId, token,tenantId),HttpStatus.OK);
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to get actualized cost for planned obligation id : "+plannedObligationId),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to get actualized cost for planned obligation id {} : ",plannedObligationId,e);
        }
        logger.info("Exited from ActualizationController.getActualizedQualityByCriteria()");
        return response;
    }

    @RequestMapping(method = RequestMethod.POST,produces = "application/json",consumes = "application/json",value = GET_COST_BY_CRITERIA)
    @ResponseBody
    public ResponseEntity<?> getActualizedCostByCriteria(@RequestBody List<SearchCriteria> searchCriteria, @RequestHeader(required = false, value = "Authorization") String token,@AuthenticationPrincipal User user) {
        logger.info("Entered to ActualizationController.getActualizedCostByCriteria()");
        ResponseEntity<?> response = null;
        try {
            response =  new ResponseEntity<>(actualizationService.getCostByCriteria(searchCriteria,user),HttpStatus.OK);
        } catch (Exception e) {
            response = new ResponseEntity<>(new ReturnStatus("Entered to get actualize cost for by criteria "),HttpStatus.BAD_REQUEST);
            logger.error("Entered to get actualize cost for by criteria ",e);
        }
        logger.info("Exited from ActualizationController.getActualizedCostByCriteria()");
        return response;
    }

    @RequestMapping(method = RequestMethod.POST,produces = "application/json",consumes = "application/json",value = GET_COST_FOR_TRANSPORT_ACTUALIZATION)
    @ResponseBody
    public ResponseEntity<?> getActualizedCostForTransportActualization(@RequestBody TransportActualizationDataFetchObj transportActualizationDataFetchObj, @RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to ActualizationController.getActualizedCostForTransportActualization()");
        ResponseEntity response = null;
        try {
            response =  actualizationService.getCostForTransportActualization(transportActualizationDataFetchObj, token,tenantId);
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to get actualized cost for transport actualization "),HttpStatus.BAD_REQUEST);
            logger.error("Failed to get actualized cost for  transport actualization ",e);
        }
        logger.info("Exited from ActualizationController.getActualizedCostForTransportActualization()");
        return response;
    }

    @RequestMapping(method = RequestMethod.GET,produces = "application/json",value = GET_ALL_ACTUALIZED)
    @ResponseBody
    public ResponseEntity<?> getAllActualized(@RequestParam String plannedObligationId,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to  ActualizationController.getAllActualized()");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(actualizationService.getAllActualized(plannedObligationId, token,tenantId),HttpStatus.OK);
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to get actualization data for planned obligation id : "+plannedObligationId),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to get Plans for planned obligation id {} : ",plannedObligationId,e);
        }
        logger.info("Exited from ActualizationController.getAllActualized()");
        return response;
    }

    @RequestMapping(method = RequestMethod.POST,produces = "application/json",consumes = "application/json",value = DELETE_ACTUALIZATION_DOCUMENTS)
    @ResponseBody
    public ResponseEntity<?> deleteDocuments(@RequestBody List<String> uuids,@AuthenticationPrincipal User user) {
        logger.info("Entered to  ActualizationController.deleteDocuments()");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(actualizationService.deleteAllByDocId(uuids,user),HttpStatus.OK);
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to get document id : "+uuids),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to get Plans for planned obligation id {} : ",uuids,e);
        }
        logger.info("Exited from ActualizationController.deleteDocuments()");
        return response;
    }

    @RequestMapping(method = RequestMethod.GET,produces = "application/json",value = GET_ALL_ACTUALIZED_QUANTITY)
    @ResponseBody
    public ResponseEntity<?> getAllActualizedQuantityList(@RequestParam String plannedObligationId,@AuthenticationPrincipal User user) {
        logger.info("Entered to ActualizationController.deleteDocuments()");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(actualizationService.getAllActualizedQuantityList(plannedObligationId, user),HttpStatus.OK);
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to get actualization data for planned obligation id : "+plannedObligationId),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to get Plans for planned obligation id {}: ",plannedObligationId,e);
        }
        return response;
    }

    /**
     * API to support quantity claim module
     * @param actualizeObj
     * @return
     */
    @RequestMapping(method = RequestMethod.POST, produces = "application/json", consumes = "application/json", value = CLAIM_QUANTITY)
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<?> claimQuantity(@RequestBody ActualizeObj actualizeObj,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered to ActualizationController.claimQuantity() method");
        ResponseEntity response = null;
        try {
            Boolean isClaimed = true;
            response =  actualizeQuantityService.actualizeQuantity(actualizeObj,isClaimed, token,tenantId);
            logger.info("Quantity claim done successfully !");
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to claim quantity"),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to claim quantity",e);
        }
        logger.info("Exited from ActualizationController.claimQuantity() method");
        return response;
    }

    @RequestMapping(method = RequestMethod.POST, produces = "application/json", consumes = "application/json", value = CLAIM_QUANTITY_FOR_TRANSPORT_ACTUALIZATION)
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<?> claimQuantityForTransportActualization(@RequestBody ActualizeObj actualizeObj,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered to ActualizationController.claimQuantity() method");
        ResponseEntity<?> response = null;
        try {
            response =  new ResponseEntity<>(actualizeQuantityService.actualizeQuantityForTransportActulization(actualizeObj,true, token,tenantId),HttpStatus.OK);
            logger.info("Quantity claim done successfully !");
        } catch (Exception e) {
            logger.error("Failed to claim quantity",e);
            response = new ResponseEntity<>(TransactionIdUtil.getErrorResponse(e),HttpStatus.BAD_REQUEST);
        }
        logger.info("Exited from ActualizationController.claimQuantity() method");
        return response;
    }

    @GetMapping(UNCLAIM_QUANTITY)
    @ResponseBody
    public ResponseEntity<?> unClaimQuantity(@RequestParam String obligationId,@AuthenticationPrincipal User user, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to ActualizationController.claimQuantity() method");
        ResponseEntity<?> response = null;
        try {
            var status = actualizeQuantityService.unclaimQuantity(obligationId,token,user);
            response = new ResponseEntity<>(status,status.isSuccessInstance()?HttpStatus.OK:HttpStatus.BAD_REQUEST);
            logger.info("Quantity claim done successfully !");
        } catch (Exception e) {
            logger.error("Failed to claim quantity",e);
            response = new ResponseEntity<>(ReturnStatus.errorInstance(e.getMessage()),HttpStatus.BAD_REQUEST);
        }
        logger.info("Exited from ActualizationController.claimQuantity() method");
        return response;
    }

    /**
     * API to support cost claim module
     * @param actualizeObj
     * @return
     */
    @RequestMapping(method = RequestMethod.POST,consumes = "application/json", value = CLAIM_COST)
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<?> claimCost(@RequestBody ActualizeObj actualizeObj,@RequestParam String tenantId ,@RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to actualization.claimCost ");
        ResponseEntity response = null;
        try {
            response = actualizeCostService.actualizeCost(actualizeObj, true, token,tenantId);
            logger.info("Cost claim done Successfully !");
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus(e.getMessage()),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to claim Cost.",e);
        }
        logger.info("Exited from ActualizationController.claimCost() method");
        return response;
    }

    /**
     * API to support cost claim module
     * @param actualizeObj
     * @return
     */
    @RequestMapping(method = RequestMethod.POST,consumes = "application/json", value = CLAIM_COST_FOR_TRANSPORT_ACTUALIZATION)
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<?> claimCostForTransportActualization(@RequestBody ActualizeObj actualizeObj,@RequestParam String tenantId ,@RequestHeader(required = false, value = "Authorization") String token) {
         logger.info("Entered to ActualizationController.claimCostForTransportActualization() method");
        ResponseEntity<?> response = null;
        try {
            response = actualizeCostService.actualizeCostForTransportActualization(actualizeObj, true, token,tenantId);
            logger.info("Cost claim for transport actualization done Successfully !");
        } catch (Exception e) {
            response = new ResponseEntity<>(TransactionIdUtil.getErrorResponse(e),HttpStatus.BAD_REQUEST);
            logger.error("Failed to claim Cost for transport actualization.",e);
        }
        logger.info("Exited from ActualizationController.claimCostForTransportActualization() method");
        return response;
    }

    @RequestMapping(method = RequestMethod.GET, produces = "application/json", value = GET_ALL_DOCUMENT)
    @Operation(summary = "Get all attached documents")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Get all attached documents",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ),
            @ApiResponse(responseCode = "500", description = "Error while getting all attached documents.",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @ResponseBody
    public ResponseEntity<?> getallattacheddoc(@RequestParam String tenantId, @RequestParam String  plannedObligationId) {
        logger.info("Entered ActualizationController.getallattacheddoc() method");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = new ResponseEntity(actualizationService.getAllAttachedDOC(tenantId,plannedObligationId), HttpStatus.OK);
            logger.info("Successfully fetched all the documents attached");
        } catch (Exception e) {
            responseEntity = new ResponseEntity(new ReturnStatus("Failed to fetch all the documents attached: " ),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to fetch all the documents attached: ",e);
        }
        logger.info("Exited from ActualizationController.getallattacheddoc() method");
        return responseEntity;
    }

    @RequestMapping(method = RequestMethod.POST, produces = "application/json",consumes = "application/json", value = GET_ALL_DOCUMENT_FOR_TRANSPORT_ACTUALIZATION)
    @Operation(summary = "Get all attached documents")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Get all attached documents",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ),
            @ApiResponse(responseCode = "500", description = "Error while getting all attached documents.",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @ResponseBody
    public ResponseEntity<?> getallattacheddocfortransportactualization(@RequestBody TransportActualizationDataFetchObj transportActualizationDataFetchObj, @RequestParam String  tenantId) {
        logger.info("Entered ActualizationController.getallattacheddocfortransportactualization() method");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = new ResponseEntity(actualizationService.getAllAttachedDocForTransportActualization(tenantId,transportActualizationDataFetchObj), HttpStatus.OK);
            logger.info("Successfully fetched all the documents attached");
        } catch (Exception e) {
            responseEntity = new ResponseEntity(new ReturnStatus("Failed to fetch all the documents attached: " + e.getMessage()),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to fetch all the documents attached: ",e);
        }
        logger.info("Exited from ActualizationController.getallattacheddocfortransportactualization() method");
        return responseEntity;
    }

    @RequestMapping(method = RequestMethod.POST,consumes = "application/json",produces = "application/json", value = ACTUALIZE_SPLIT_PLANNED_OBLIGATION)
    @Operation(summary = "Actualize BL Split Rows for Planned Obligation")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Actualize BL Split Rows for Planned Obligation",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ),
            @ApiResponse(responseCode = "500", description = "Error while Actualize BL Split Rows for Planned Obligation.",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @ResponseBody
    public ResponseEntity actaulizeSplitRows(@RequestParam String tenantId, @RequestBody List<PlannedObligationDTO> splitRows, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered ActualizationController.actaulizeSplitRows()");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = actualizeQuantityService.actualizeQuantityForBLSplit(splitRows,token,tenantId);
        } catch (Exception e) {
            responseEntity = new ResponseEntity(ReturnStatus.errorInstance("Failed to Actualize BL Split Rows for Planned Obligation, Error: : " + e.getMessage()), HttpStatus.BAD_REQUEST);
            logger.error("Failed to Actualize BL Split Rows for Planned Obligation: ",e);
        }
        logger.info("Entered ActualizationController.actaulizeSplitRows()");
        return responseEntity;
    }

    @RequestMapping(method = RequestMethod.POST,consumes = "application/json",produces = "application/json", value = SAVE_BL_INFO)
    @Operation(summary = "Save BL Info for Planned Obligation Split Row")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Save BL Info for Planned Obligation Split Row",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ),
            @ApiResponse(responseCode = "500", description = "Error while Save BL Info for Planned Obligation Split Row.",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @ResponseBody
    public ResponseEntity saveBlInfo(@RequestParam String tenantId, @RequestBody BillOfLandingDTO billOfLandingDTO, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered to ActualizationController.saveBlInfo()");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = new ResponseEntity(billOfLandingService.saveBlInfo(billOfLandingDTO,token,tenantId),HttpStatus.OK);
        } catch (Exception e) {
            responseEntity = new ResponseEntity(new ReturnStatus("Failed to Save BL Info for Planned Obligation Split Row, Error: : " + e.getMessage()), HttpStatus.BAD_REQUEST);
            logger.error("Failed to Save BL Info for Planned Obligation Split Row: ",e);
        }
        logger.info("Exited from ActualizationController.saveBlInfo()");
        return responseEntity;
    }

    @RequestMapping(method = RequestMethod.GET,produces = "application/json", value = GET_BL_INFO)
    @Operation(summary = "GET BL Info for Planned Obligation Split Row")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "GET BL Info for Planned Obligation Split Row",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ),
            @ApiResponse(responseCode = "500", description = "Error while GET BL Info for Planned Obligation Split Row.",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @ResponseBody
    public ResponseEntity getBlInfo(@RequestParam String plannedObligationId,@RequestParam double splitSequence, @RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered  to ActualizationController.getBlInfo()");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = billOfLandingService.getBLInfo(plannedObligationId,splitSequence,token,tenantId);
        } catch (Exception e) {
            responseEntity = new ResponseEntity(new ReturnStatus("Failed to GET BL Info for Planned Obligation Split Row, Error: " + e.getMessage()), HttpStatus.BAD_REQUEST);
            logger.error("Failed to GET BL Info for Planned Obligation Split Row: ",e);
        }
        logger.info("Exited from ActualizationController.getBlInfo()");
        return responseEntity;
    }

    @RequestMapping(method = RequestMethod.GET,produces = "application/json", value = GET_ALL_BL_INFO)
    @Operation(summary = "GET BL Info for Planned Obligation Split Row")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "GET BL Info for Planned Obligation Split Row",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ),
            @ApiResponse(responseCode = "500", description = "Error while GET BL Info for Planned Obligation Split Row.",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @ResponseBody
    public ResponseEntity<?> getBlInfo(@RequestParam String plannedObligationId,@RequestParam(required = false) Double splitSequenceNumber, @RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered  to ActualizationController.getBlInfo()");
        ResponseEntity<?> responseEntity = null;
        try {
            responseEntity = new ResponseEntity<>(billOfLandingService.getAllBLInfo(plannedObligationId,splitSequenceNumber,tenantId),HttpStatus.OK);
        } catch (Exception e) {
            responseEntity = new ResponseEntity<>(ReturnStatus.errorInstance("Failed to GET BL Info for Planned Obligation Split Row, Error: : " + e.getMessage()), HttpStatus.BAD_REQUEST);
            logger.error("Failed to GET BL Info for Planned Obligation Split Row:" , e);
        }
        logger.info("Exited from  ActualizationController.getBlInfo()");
        return responseEntity;
    }

    @RequestMapping(method = RequestMethod.GET,produces = "application/json", value = CONFIRM_BL)
    @Operation(summary = "Confirm BL Info for Planned Obligation Split Row")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Confirm BL Info for Planned Obligation Split Row",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ),
            @ApiResponse(responseCode = "500", description = "Error while Confirm BL Info for Planned Obligation Split Row.",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @ResponseBody
    public ResponseEntity confirmBL(@RequestParam String plannedObligationId,@RequestParam String blNumber, @RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered ActualizationController.confirmBL()");
        ResponseEntity responseEntity = null;
        try {
            var billOfLanding =billOfLandingService.confirmBL(plannedObligationId,blNumber,token,tenantId);
            if(billOfLanding != null) {
                responseEntity = new ResponseEntity(billOfLanding,HttpStatus.OK);
            } else {
                responseEntity = new ResponseEntity(ReturnStatus.errorInstance("Failed to confirm BL for Bl Number : "+blNumber +" for Planned Obligation Id : "+plannedObligationId),HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            responseEntity = new ResponseEntity(new ReturnStatus("Failed to GET BL Info for Planned Obligation Split Row, Error: : " + e.getMessage()), HttpStatus.BAD_REQUEST);
            logger.error("Failed to GET BL Info for Planned Obligation Split Row: ",e);
        }
        logger.info("Exited from ActualizationController.confirmBL()");
        return responseEntity;
    }

    @RequestMapping(method = RequestMethod.POST,produces = "application/json",consumes = "application/json", value = SURRENDER_BL)
    @Operation(summary = "Surrender BL Info for Planned Obligation Split Row")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Surrender BL Info for Planned Obligation Split Row",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ),
            @ApiResponse(responseCode = "500", description = "Error while Surrender BL Info for Planned Obligation Split Row.",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @ResponseBody
    public ResponseEntity surrenderBlInfo(@RequestBody List<BillOfLanding> blrows, @RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered ActualizationController.surrenderBlInfo()");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = billOfLandingService.surrenderBLInfo(blrows,token,tenantId);
        } catch (Exception e) {
            responseEntity = new ResponseEntity(new ReturnStatus("Failed to Surrender BL Info for Planned Obligation Split Row, Error: : " + e.getMessage()), HttpStatus.BAD_REQUEST);
            logger.error("Failed to Surrender BL Info for Planned Obligation Split Row: " ,e);
        }
        logger.info("Entered ActualizationController.surrenderBlInfo()");
        return responseEntity;
    }

    @RequestMapping(method = RequestMethod.POST,produces = "application/json",consumes = "application/json", value = GET_BL_ROW_LIST_BY_PLANNED_OBLIGATION_ID)
    @Operation(summary = "Get BL Info for Planned Obligations Split Row")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Get BL Info for Planned Obligation Split Row",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ),
            @ApiResponse(responseCode = "500", description = "Error while Get BL Info for Planned Obligation Split Row.",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @ResponseBody
    public ResponseEntity getBlInfoRowsByPlannedObligationIds(@RequestBody List<String> plannedObligationList, @RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered to ActualizationController.getBlInfoRowsByPlannedObligationIds()");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = billOfLandingService.getBlInfoRowsByPlannedObligationIds(plannedObligationList,token,tenantId);
        } catch (Exception e) {
            responseEntity = new ResponseEntity(new ReturnStatus("Failed to Get BL Info for Planned Obligation Split Row, Error: : " ), HttpStatus.BAD_REQUEST);
            logger.error("Failed to Get BL Info for Planned Obligation Split Row: ",e);
        }
        logger.info("Exited from ActualizationController.getBlInfoRowsByPlannedObligationIds()");
        return responseEntity;
    }


    @GetMapping(GET_BL_ROW_LIST_BY_PLANNED_OBLIGATION_ID_V2)
    public SurrenderBlRowsDTO getBlInfoRowsByPlanId(@RequestParam String planId, @RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        try {
            return billOfLandingService.getBlInfoRowsByPlanId(planId,token,tenantId);
        } catch (Exception e) {
            logger.error("Failed to Get BL Info for Planned Obligation Split Row: ",e);
            throw new TaomishError(GET_BL_ROW_LIST_BY_PLANNED_OBLIGATION_ID_V2,e);
        }
    }


    @RequestMapping(method = RequestMethod.POST,produces = "application/json",consumes = "application/json", value = MERGE_BL)
    @Operation(summary = "save merge BL Info for Planned Obligations Split Row")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "save merge BL Info for Planned Obligation Split Row",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ),
            @ApiResponse(responseCode = "500", description = "Error while save merge BL Info for Planned Obligation Split Row.",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @ResponseBody
    public ResponseEntity saveMergeBlInfo(@RequestBody List<BillOfLandingDTO> billOfLandingDTOS,@RequestParam Integer mergeCount,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered to  ActualizationController.saveMergeBlInfo()");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = billOfLandingService.merge(billOfLandingDTOS,mergeCount,token,tenantId);
        } catch (Exception e) {
            responseEntity = new ResponseEntity(new ReturnStatus("Failed to save merge BL Info for Planned Obligation Split Row, Error: : "), HttpStatus.BAD_REQUEST);
            logger.error("Failed to save merge BL Info for Planned Obligation Split Row: ",e);
        }
        logger.info("Exited from  ActualizationController.saveMergeBlInfo()");
        return responseEntity;
    }

    @RequestMapping(method = RequestMethod.POST,produces = "application/json",consumes = "application/json", value = CHECK_QUALITY_ACTUALIZATION)
    @Operation(summary = "Check for quality actualization is done for given planned obligations")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Check for quality actualization is done for given planned obligations",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ),
            @ApiResponse(responseCode = "500", description = "Error while checking for quality actualization is done for given planned obligations.",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @ResponseBody
    public ResponseEntity checkQualityActualization(@RequestBody List<String> plannedObligationIds,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered to ActualizationController.checkQualityActualization()");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = actualizeQualityService.checkQualityActualization(plannedObligationIds,tenantId,token);
        } catch (Exception e) {
            responseEntity = new ResponseEntity(new ReturnStatus("Error while checking for quality actualization is done for given planned obligations, Error: : "), HttpStatus.BAD_REQUEST);
            logger.error("Error while checking for quality actualization is done for given planned obligations : ",e);
        }
        logger.info("Exited from ActualizationController.checkQualityActualization()");
        return responseEntity;
    }

    @RequestMapping(method = RequestMethod.POST, produces = "application/json", consumes = "application/json", value = CLAIM_QUALITY)
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<?> claimQuality(@RequestBody ActualizeObj actualizeObj,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered to ActualizationController.claimQuality() method");
        ResponseEntity<?> response = null;
        try {
            var status =  actualizeQualityService.actualizeQualitySpecForTransportActualization(actualizeObj,true, token,tenantId);
            response = new ResponseEntity<>(status,HttpStatus.OK);
            logger.info("Quality claim done successfully !");
        } catch (Exception e) {
            logger.error("Failed to claim Quality",e);
            throw new Exception("Failed to claim Quality");
        }
        logger.info("Exited from ActualizationController.claimQuality() method");
        return response;
    }

    /*
 Standard filter for Trade Actualization
 */
    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = "application/json", value =GET_ACTUALIZATION_BY_CRITERIA)
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "fetch payment term",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ResponseEntity.class))
                    }
            ),
            @ApiResponse(responseCode = "500", description = "Error while fetching payment term",
                    content = {@Content(schema = @Schema(implementation = ResponseEntity.class))})
    })
    @ResponseBody
    public ResponseEntity getBySearchCriteria(@RequestBody List<SearchCriteria> searchBuilder,@RequestParam String tenantId, @RequestParam(required = false) String operation, @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,@RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered into ActualizationController.getBySearchCriteria() method");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = actualizationService.getByCriteria(searchBuilder,tenantId,operation, page,size,token);
        } catch (Exception ex) {
            responseEntity = new ResponseEntity(new ReturnStatus(ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Couldn't fetch TradeActualization, error is ",ex);
        }
        logger.info("Exited from ActualizationController.getBySearchCriteria() method");
        return responseEntity;
    }


    @RequestMapping(method = RequestMethod.POST,consumes = "application/json", value = SAVE_ACTUALIZED_DOCUMENT)
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<?> saveactualizeddocument(@RequestBody ActualizedDocumentsDTO actualizedDocuments, @RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to ActualizationController.saveactualizeddocument()");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(actualizationService.saveActualizedDocument(actualizedDocuments,tenantId),HttpStatus.OK);
            logger.info("Actualized document saved successfully !");
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to save actualized document"),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to save actualized document",e);
        }
        logger.info("Exited from ActualizationController.saveactualizeddocument()");
        return response;
    }

    @GetMapping(CHECK_COST_ACTUALIZE_FOR_DELETE)
    public ActualizedCostDTO checkForCostDelete(@RequestParam String costId,
                                                @Parameter(hidden = true) @AuthenticationPrincipal User principal){
       return this.actualizeCostService.checkForCostDelete(costId,principal);
    }

    @RequestMapping(method = RequestMethod.POST,produces = "application/json", consumes = "application/json", value = ACTUALIZE_QUANTITY_FOR_INVENTORY)
    @ResponseBody
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<?> actualizeQuantityForInventory(@RequestBody ActualizeObj actualizeObj,@RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to ActualizationController.actualizeQuantityForInventory()");
        ResponseEntity response = null;
        try {
            Boolean isClaimed = false;
            response =  actualizeQuantityService.actualizeQuantityForInventory(actualizeObj,isClaimed, token,tenantId);
            logger.info("Quantity actualization done successfully !");
        } catch (Exception e) {
            response = new ResponseEntity(new ReturnStatus("Failed to actualize quantity"),HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to actualize quantity",e);
        }
        return response;
    }

    @GetMapping(GET_BL_INFO_FOR_INVENTORY)
    public BillOfLandingDTO getBlInfoForInventory(@RequestParam String tenantId,
                                                  @RequestParam String plannedObligationId,
                                                  @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to ActualizationController.getBlInfoForInventory");
        BillOfLandingDTO billOfLandingDTO=null;
        try {
            Boolean isClaimed = false;
            billOfLandingDTO =  billOfLandingService.getBLInfoForInventory(plannedObligationId,tenantId,token);
        } catch (Exception e) {
            logger.error("Failed to getBlInfoForInventory : ",e);
        }
        logger.info("Exited from ActualizationController.getBlInfoForInventory");
        return billOfLandingDTO;
    }

    @PostMapping(GET_ALL_DOCUMENT_FOR_TRANSPORT_ACTUALIZATION_BY_CRITERIA)
    public Page<ActualizedDocuments> getallattacheddocfortransportactualizationbycriteria(@RequestBody List<SearchCriteria> searchBuilder,
                                                                                                @RequestParam String tenantId,
                                                                                                @RequestParam(required = false) String operation,
                                                                                                @RequestParam(required = false) Integer page,
                                                                                                @RequestParam(required = false) Integer size,
                                                                                                @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        logger.info("Entered into ActualizationController.getBySearchCriteriagetallattacheddocfortransportactualizationbycriteria() method");
        try {
            return  actualizationService.getallattacheddocfortransportactualizationbycriteria(searchBuilder,tenantId,operation, page,size,token);
        } catch (Exception ex) {
            logger.error("Couldn't fetch getallattacheddocfortransportactualizationbycriteria, error is ",ex);
            throw new TaomishError("Actualization-Doc-001",ex);
        }
    }
}


