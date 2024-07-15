package com.taomish.blmanagement.controller;

import com.taomish.blmanagement.dto.BlRecordDTO;
import com.taomish.blmanagement.service.BlRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.taomish.RestEndPoints.BlManagementRestEndPoints.*;


@RestController
@RequestMapping(value = BL_RECORD_ROOT)
public class BlRecordController {

    private static final Logger logger = LoggerFactory.getLogger(BlRecordController.class);

    @Autowired
    BlRecordService blRecordService;

    /**
     * Save BL Record
     *
     * @param blRecordDTO
     * @return
     * @throws Exception
     */
    @Operation(summary = "Save BL Record")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Save BL Record",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ), @ApiResponse(
            responseCode = "422",
            description = "Saving BL Record failed",
            content = {@Content(schema = @Schema(implementation = String.class))}
    ),
            @ApiResponse(responseCode = "500", description = "Error while Saving BL Record",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.POST, produces = "application/json", consumes = "application/json", value = CREATE_BL_RECORD)
    @ResponseBody
    public ResponseEntity<?> createblrecord(@RequestBody BlRecordDTO blRecordDTO) throws Exception {
        logger.info("Entered BlRecordController.createblrecord() method");
        ResponseEntity responseEntity = null;
        try {
            return blRecordService.createblrecord(blRecordDTO);
        } catch (Exception e) {
            responseEntity = new ResponseEntity("Failed to save BL Record(s)", HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to save BL Record: " ,e);
        }
        logger.info("Exited from BlRecordController.createblrecord() method");
        return responseEntity;
    }

    /**
     * Update Bl Record
     *
     * @param blRecordDTO
     * @param tenantId
     * @return
     * @throws Exception
     */
    @Operation(summary = "Update BL Record")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Update BL Record",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ), @ApiResponse(
            responseCode = "422",
            description = "Update BL Record failed",
            content = {@Content(schema = @Schema(implementation = String.class))}
    ),
            @ApiResponse(responseCode = "500", description = "Error while Update BL Record",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.POST, produces = "application/json", consumes = "application/json", value = UPDATE_BL_RECORD)
    @ResponseBody
    public ResponseEntity updateblrecord(@RequestBody BlRecordDTO blRecordDTO, @RequestParam String tenantId) throws Exception {
        logger.info("Entered BlRecordController.updateblrecord() method");
        ResponseEntity responseEntity = null;
        try {
            return blRecordService.updateblrecord(blRecordDTO, tenantId);
        } catch (Exception e) {
            responseEntity = new ResponseEntity("Failed to Update BL Record(s)", HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to update BL Record : ",e);
        }
        logger.info("Exited from BlRecordController.updateblrecord() method");
        return responseEntity;
    }

    /**
     * Get By UUID and TenantId
     *
     * @param tenantId
     * @param uuid
     * @return
     * @throws Exception
     */
    @Operation(summary = "Get BL Record by Id")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Get BL Record by Id",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ), @ApiResponse(
            responseCode = "422",
            description = "Get BL Record by Id failed",
            content = {@Content(schema = @Schema(implementation = String.class))}
    ),
            @ApiResponse(responseCode = "500", description = "Error while getting BL Record by Id",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.GET, produces = "application/json", value = GET_BL_RECORD_BY_UUID)
    @ResponseBody
    public ResponseEntity<?> getblrecordbyuuid(@RequestParam String tenantId, @RequestParam String uuid) throws Exception {
        logger.info("Entered BlRecordController.getblRecordbyuuid() method");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = new ResponseEntity(blRecordService.getblrecordbyuuid(tenantId, uuid), HttpStatus.OK);
            logger.info("Successfully fetched BL Record list by uuid and tenantid {}: " ,uuid);
        } catch (Exception e) {
            responseEntity = new ResponseEntity("Failed to fetch BL Record(s) data by Id", HttpStatus.NOT_FOUND);
            logger.error("Failed to fetch BL Record by uuid {} :" ,uuid,e);
        }
        logger.info("Exited from BlRecordController.getblrecordbyuuid() method");
        return responseEntity;
    }

    /**
     * Delete
     *
     * @param tenantId
     * @param uuid
     * @return
     * @throws Exception
     */
    @Operation(summary = "Delete BL Record objects")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Delete BL Record objects",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ResponseEntity.class))
                    }
            ),
            @ApiResponse(responseCode = "500", description = "Error while deleting getting Bl Record",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.GET, produces = "application/json", value = DELETE_BL_RECORD)
    @ResponseBody
    public ResponseEntity<?> deleteblrecord(@RequestParam String tenantId, @RequestParam String uuid) throws Exception {
        logger.info("Entered BlRecordController.deleteblrecord() method");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(blRecordService.deleteblrecord(tenantId, uuid), HttpStatus.OK);
            logger.info("Successfully deleted BL Record list");
        } catch (Exception ex) {
            response = new ResponseEntity("Failed to delete BL Record, because of the following reason: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to BL Record details ",ex);
        }
        logger.info("Exited from BlRecordController.deleteblrecord() method");
        return response;
    }

    /**
     *
     * @param tenantId
     * @param plannedObligationId
     * @param splitNumber
     * @return
     * @throws Exception
     */
    @Operation(summary = "Split BL Record by Id")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Split BL Record by Id",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ), @ApiResponse(
            responseCode = "422",
            description = "Split BL Record by Id failed",
            content = {@Content(schema = @Schema(implementation = String.class))}
    ),
            @ApiResponse(responseCode = "500", description = "Error while Spliting BL Record by Id",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.GET, produces = "application/json", value = SPLIT_BL_RECORD)
    @ResponseBody
    public ResponseEntity<?> splitblrecord(@RequestParam String tenantId, @RequestParam String plannedObligationId, @RequestParam Integer splitNumber) throws Exception {
        logger.info("Entered BlRecordController.splitblrecord() method");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = new ResponseEntity(blRecordService.splitblrecord(tenantId, plannedObligationId, splitNumber), HttpStatus.OK);
            logger.info("Successfully Split BL Record list");
        } catch (Exception e) {
            responseEntity = new ResponseEntity("Failed to Split BL Record(s) data by Id", HttpStatus.NOT_FOUND);
            logger.error("Failed to Split BL Record",e);
        }
        logger.info("Exited from BlRecordController.splitblrecord() method");
        return responseEntity;
    }
}
