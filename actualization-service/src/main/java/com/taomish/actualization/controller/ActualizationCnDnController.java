package com.taomish.actualization.controller;

import com.taomish.actualization.dto.ActualizationCnDnCashFlowQuantityDTO;
import com.taomish.actualization.service.ActualizationCnDnService;
import com.taomish.dtos.cashflowservice.UpdateCashflowDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import static com.taomish.constants.ActualizationCNDNConstants.*;


@RestController
@RequestMapping(value = CNDN_ROOT)
@Tag(name = "Credit and Debit note APIs", description = "All method related credit and debit note")
public class ActualizationCnDnController {

    @Autowired
    ActualizationCnDnService actualizationCnDnService;

    private static final Logger logger = LoggerFactory.getLogger(ActualizationCnDnController.class);

    /**
     * Create reversal cost cashflows and generate new cashflow of type cn/dn for already invoiced cost cashflows
     *
     * @param actualizationCnDnCashFlowCostDTO
     * @return
     * @throws Exception
     */
    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = "application/json",
            value = CREATE_CN_DN_FOR_COST_ACTUALIZATION)
    @ResponseBody
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity createCnDnCashFlowsForCostActualization(@RequestBody UpdateCashflowDTO actualizationCnDnCashFlowCostDTO, @RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token)
            throws Exception {
        logger.info("Entered to ActualizationCnDnController.createCnDnCashFlowsForCostActualization() method");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(
                    actualizationCnDnService.createCnDnCashFlowsForCostActualizationService(actualizationCnDnCashFlowCostDTO, tenantId, token), HttpStatus.OK);
        } catch (Exception e) {
            response = new ResponseEntity(
                    "Failed to create credit/debit note cashflow(s) for the costId "
                            + actualizationCnDnCashFlowCostDTO.getCashflowId(), HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to create credit/debit note cashflow (s) for the costId : ",e);
        }
        logger.info("Exited from ActualizationCnDnController.createCnDnCashFlowsForCostActualization() method");
        return response;
    }

    @RequestMapping(method = RequestMethod.POST,
            consumes = "application/json",
            produces = "application/json",
            value = CREATE_CN_DN_FOR_QUALITY_ACTUALIZATION)
    @Operation(summary = "Create a Credit note / debit note entry for quality actualization")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Credit Note / Debit Note created successfully",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ActualizationCnDnCashFlowQuantityDTO.class))
                    }
            ),
            @ApiResponse(responseCode = "500", description = "Error while creating CN DN",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity createCnDnCashFlowsForQualityActualization(@RequestBody UpdateCashflowDTO actualizationCnDnCashFlowDTO, @RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to ActualizationCnDnController.createCnDnCashFlowsForQuantityActualization() method");
        ResponseEntity response;
        try {
            response = new ResponseEntity(
                    actualizationCnDnService.createCnDnCashFlowsForQualityActualizationService(actualizationCnDnCashFlowDTO, tenantId, token), HttpStatus.OK);
        } catch (Exception e) {
            response = new ResponseEntity(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to create cn dn cash flows",e);
        }
        logger.info("Exited from ActualizationCnDnController.createCnDnCashFlowsForQuantityActualization() method");
        return response;
    }

}
