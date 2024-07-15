package com.taomish.actualization.v2.controller;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.taomish.actualization.dto.ActualPackingDetailsDTO;
import com.taomish.actualization.dto.ActualQualityDTO;
import com.taomish.actualization.dto.BLActualizationDTO;
import com.taomish.actualization.model.ActualPackingDetails;
import com.taomish.actualization.model.ActualQuality;
import com.taomish.actualization.model.GrnStock;
import com.taomish.actualization.v2.dtos.ActualizationGiDto;
import com.taomish.actualization.v2.service.ActualizationServiceV2;
import com.taomish.actualization.v2.models.PlannedObligationsForActualization;
import com.taomish.actualization.v2.models.PlannedTrades;
import com.taomish.actualization.v2.service.DeactualizationService;
import com.taomish.actualization.v2.service.GIService;
import com.taomish.common.domain.TaomishError;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.dtos.ReturnStatus;
import com.taomish.dtos.actualizationservice.ActualizeObjV2;
import com.taomish.dtos.actualizationservice.ActualizedQuantityObligationsDTO;
import com.taomish.dtos.physicaltradeservice.PlannedObligationDTO;
import com.taomish.search.SearchCriteria;
import com.taomish.web.security.models.User;
import io.swagger.v3.oas.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.taomish.constants.ActualizationConstants.ACTUALIZATION_ROOT_V2;

@RestController
@RequestMapping(value = ACTUALIZATION_ROOT_V2)
public class ActualizationControllerV2 {
    private final ActualizationServiceV2 actualizationServiceV2;
    private final DeactualizationService deactualizationService;
    private final GIService giService;
    private static final Logger logger = LoggerFactory.getLogger(ActualizationControllerV2.class);

    public ActualizationControllerV2(ActualizationServiceV2 actualizationServiceV2, DeactualizationService deactualizationService,GIService giService) {
        this.actualizationServiceV2 = actualizationServiceV2;
        this.deactualizationService = deactualizationService;
        this.giService = giService;
    }

    @PostMapping("/find-obligations-for-actualization")
    public Page<PlannedObligationsForActualization> findAllObligations(@RequestBody Set<SearchCriteria> searchCriteriaList, @RequestParam int page,
                                                                       @RequestParam int size, @Parameter(hidden = true) @AuthenticationPrincipal User principal) {
        searchCriteriaList.add(new SearchCriteria("tenantId",SearchCriteria.EQUALS,principal.getTenantId()));
        return actualizationServiceV2.findAllObligations(searchCriteriaList,page,size,principal);
    }

    @PostMapping("/find-trade-planning-planned-trades")
    public Page<PlannedTrades> findAllPlannedTrades(@RequestBody Set<SearchCriteria> searchCriteriaList, @RequestParam int page,
                                                    @RequestParam int size, @Parameter(hidden = true) @AuthenticationPrincipal User principal){
        searchCriteriaList.add(new SearchCriteria("tenantId",SearchCriteria.EQUALS,principal.getTenantId()));
        return actualizationServiceV2.findAllPlannedTrades(searchCriteriaList,page,size,principal);
    }

    @PostMapping("/find-obligations-for-actualization-by-criteria")
    public Page<PlannedObligationsForActualization> findAllObligationsByCriteria(@RequestBody Set<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaList, @RequestParam int page,
                                                                                 @RequestParam int size, @Parameter(hidden = true) @AuthenticationPrincipal User principal) {
        return actualizationServiceV2.findAllObligationsByCriteria(searchCriteriaList,page,size,principal);
    }

    @PostMapping("/find-trade-planning-planned-trades-by-criteria")
    public Page<PlannedTrades> findAllPlannedTradesByCriteria(@RequestBody Set<com.taomish.common.searchcriteria.SearchCriteria> searchCriteriaList, @RequestParam int page,
                                                              @RequestParam int size, @Parameter(hidden = true) @AuthenticationPrincipal User principal) {
        return actualizationServiceV2.findAllPlannedTradesByCriteria(searchCriteriaList,page,size,principal);
    }

    @GetMapping("/find-filter-criteria-for-actualization")
    public Map<String, List<String>> findFilterCriteriaForActualization(@Parameter(hidden = true) @AuthenticationPrincipal User principal) throws JsonProcessingException {
        return actualizationServiceV2.findFilterCriteriaForActualization(principal.getTenantId());
    }
    @GetMapping("/find-filter-criteria-for-actualization-container")
    public Map<String, List<String>> findFilterCriteriaForActualizationContainer(@Parameter(hidden = true) @AuthenticationPrincipal User principal) throws JsonProcessingException {
        return actualizationServiceV2.findFilterCriteriaForActualizationContainer(principal.getTenantId());
    }
    @PostMapping("/actualize-quantity")
    public ReturnStatus actualizeQuantity(@RequestBody List<ActualizeObjV2> actualizeObj,
                                               @Parameter(hidden = true) @AuthenticationPrincipal User principal,
                                               @RequestHeader(required = false, value = "Authorization") String token) throws TaomishError {
        try {
            return actualizationServiceV2.actualizeQuantity(actualizeObj,principal,token);
        } catch (Exception e) {
            logger.error("failed to actualize quantity",e);
            return TransactionIdUtil.getErrorResponse(e);
        }
    }

    @GetMapping("/deActulaizeCost")
    public ReturnStatus deActulaizeCost(@RequestParam String obligationId,
                                        @RequestParam String costId,
                                        @Parameter(hidden = true) @AuthenticationPrincipal User principal,
                                        @RequestHeader(required = false, value = "Authorization") String token){
        return this.actualizationServiceV2.deActulizeCost(obligationId,costId,principal,token);

    }
    @PostMapping("/deactualize")
    public ResponseEntity<ReturnStatus> deActualize(@RequestBody List<PlannedObligationDTO> obligationList,
                                                    @Parameter(hidden = true) @AuthenticationPrincipal User principal,
                                                    @RequestHeader(required = false, value = "Authorization") String token) throws TaomishError {
        ResponseEntity<ReturnStatus> responseEntity = null;
        try {
            var status = deactualizationService.deactualizeQuantity(obligationList,principal.getTenantId(),token);
            responseEntity = new ResponseEntity<>(status,status.isErrorInstance()?HttpStatus.BAD_REQUEST:HttpStatus.OK);
        } catch (Exception e) {
            logger.error("failed to de actualize ",e);
            responseEntity = new ResponseEntity<>(TransactionIdUtil.getErrorResponse(e),HttpStatus.BAD_REQUEST);
        }
        return responseEntity;
    }
    @PostMapping("/claim-actualize-quantity")
    public ResponseEntity<ReturnStatus> claimActualizeQuantity(@RequestBody List<ActualizeObjV2> actualizeObj,
                                                               @Parameter(hidden = true) @AuthenticationPrincipal User principal,
                                                               @RequestHeader(required = false, value = "Authorization") String token) throws TaomishError {
        try {
            return new ResponseEntity<>(actualizationServiceV2.claimActualizeQuantity(actualizeObj,principal,token),HttpStatus.OK);
        } catch (Exception e) {
            logger.error("failed to claim Actualize Quantity",e);
            return new ResponseEntity<>(TransactionIdUtil.getErrorResponse(e), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/getBL")
    public List<BLActualizationDTO> getBL(@RequestParam String planId,@RequestParam String vesselId,@Parameter(hidden = true) @AuthenticationPrincipal User principal,
                                    @RequestHeader(required = false, value = "Authorization") String token) throws JsonProcessingException {
        return actualizationServiceV2.getBL(planId,vesselId,principal,token);
    }

    @PostMapping("/update-quality-for-obligation")
    public List<ActualQuality> updateQualityForObligation(@RequestBody List<ActualQualityDTO> actualQualityDTOs,@AuthenticationPrincipal User user){
        return actualizationServiceV2.updateQualityForObligation(actualQualityDTOs,user);
    }

    @GetMapping("/get-quality-for-obligation")
    public List<com.taomish.dtos.actualizationservice.ActualQualityDTO> getQualityForObligation(@RequestParam String obligationId, @AuthenticationPrincipal User user){
        return actualizationServiceV2.getQualityForObligation(obligationId,user);
    }
    @PostMapping("/update-packing-details-for-obligation")
    public List<ActualPackingDetails> updatePackingDetailsForObligation(@RequestBody List<ActualPackingDetailsDTO> actualPackingDetailsDTOs){
        return actualizationServiceV2.updatePackingDetailsForObligation(actualPackingDetailsDTOs);
    }

    @GetMapping("/get-packing-details-for-obligation")
    public List<ActualPackingDetails> getPackagingDetailsForObligationId(@RequestParam String obligationId,@AuthenticationPrincipal User user){
        return actualizationServiceV2.getPackagingDetailsForObligationId(obligationId,user);
    }

    @PostMapping("/get-actualization-quantity-by-criteria")
    public List<ActualizedQuantityObligationsDTO> getActualizationQuantityByCriteria(@RequestBody List<com.taomish.common.searchcriteria.SearchCriteria> searchCriteria,
                                                                                                 @RequestParam String tenantId,
                                                                                                 @RequestParam(required = false) String operation,
                                                                                                 @RequestHeader(required = false, value = "Authorization") String token) throws Exception {
        return actualizationServiceV2.getActualizationQuantityByCriteria(tenantId,token,searchCriteria,operation);
    }

    @PostMapping("/save-actualization-quantity")
    public ActualizedQuantityObligationsDTO saveActualizationQuantity(@RequestBody  ActualizedQuantityObligationsDTO saveActualizedQuantityObj,
                                                                      @RequestHeader(required = false, value = "Authorization") String token)throws Exception {
        return actualizationServiceV2.saveActualizationQuantity(token,saveActualizedQuantityObj);
    }

    @GetMapping("/getGrn")
    public List<BLActualizationDTO> getGrnData(@RequestParam String planId,@Parameter(hidden = true) @AuthenticationPrincipal User principal,
                                               @RequestHeader(required = false, value = "Authorization") String token)throws TaomishError {
        return actualizationServiceV2.getGrnData(planId,principal,token);
    }

    @PostMapping("/save-grn-data")
    public List<GrnStock> saveGrnData(@RequestBody List<ActualizeObjV2> actualizeObj,
                                     @Parameter(hidden = true) @AuthenticationPrincipal User principal,
                                     @RequestHeader(required = false, value = "Authorization") String token)throws TaomishError {
        return actualizationServiceV2.saveGrnData(actualizeObj,principal,token);
    }

    @PostMapping("/deactualize-grn")
    public ReturnStatus deactualizeGrnData(@RequestBody List<BLActualizationDTO> obligationList,
                                                       @Parameter(hidden = true) @AuthenticationPrincipal User principal,
                                                       @RequestHeader(required = false, value = "Authorization") String token)throws TaomishError {
        return actualizationServiceV2.deactualizeGrnData(obligationList, token,principal);
    }
    @PostMapping("/delete-grn")
    public ReturnStatus deleteGrn(@RequestParam String plannedObligation,
                                  @RequestParam String actulizedId,
                                  @RequestParam String grnId,
                                  @RequestParam Boolean isBlSplit,
                                  @Parameter(hidden = true) @AuthenticationPrincipal User principal,
                                  @RequestHeader(required = false, value = "Authorization") String token)throws TaomishError {
        return actualizationServiceV2.deleteGrn(plannedObligation,actulizedId,grnId,isBlSplit,principal,token);
    }
    @GetMapping("get-purchase-order-for-grn")
    public List<BLActualizationDTO>getPurchaseOrderForGrn(@RequestParam String purchaseOrder,
                                                                        @Parameter(hidden = true) @AuthenticationPrincipal User user,
                                                                        @RequestHeader(required = false, value = "Authorization") String token)throws TaomishError{
        return actualizationServiceV2.getPurchaseOrderForGrn(purchaseOrder,user,token);
    }

    @PostMapping("/actualization-gi")
    public ReturnStatus actualizationGI(@RequestBody List<ActualizationGiDto> actualizationGiDtos,
                                      @Parameter(hidden = true) @AuthenticationPrincipal User principal,
                                      @RequestHeader(required = false, value = "Authorization") String token)throws TaomishError {
        logger.info("Entered into actualizationGI {}");
        return giService.actualizationGI(actualizationGiDtos,principal,token);
    }

    @PostMapping("/deactualization-gi")
    public ReturnStatus deactualizationGI(@RequestBody List<ActualizationGiDto> actualizationGiDtos,
                                      @Parameter(hidden = true) @AuthenticationPrincipal User principal,
                                      @RequestHeader(required = false, value = "Authorization") String token)throws TaomishError {
        logger.info("Entered into deactulizeGI {}");
        return giService.deactualizationGI(actualizationGiDtos,principal,token);
    }

}
