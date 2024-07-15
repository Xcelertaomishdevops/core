package chartofaccount.controller;

import chartofaccount.dto.GLAccountDTO;
import chartofaccount.dto.GLStructureDTO;
import chartofaccount.service.GeneralLedgerStructureService;
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
import java.util.List;
import static com.taomish.RestEndPoints.ConfigService.GeneralLedgerRestEndPoints.*;

@RestController
@RequestMapping(value = GL_STRUCTURE_ACCOUNT_ROOT)
public class GeneralLedgerStructureController {

    @Autowired
    GeneralLedgerStructureService generalLedgerStructureService;

    private static final Logger logger = LoggerFactory.getLogger(GeneralLedgerStructureController.class);

    /**
     * Get All GL Structure
     * @param tenantId
     * @return
     * @throws Exception
     */
    @Operation(summary = "Get All GL Structure")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Get All GL Structure",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ), @ApiResponse(
            responseCode = "422",
            description = "Get All GL Structure failed",
            content = {@Content(schema = @Schema(implementation = String.class))}
    ),
            @ApiResponse(responseCode = "500", description = "Error while getting all GL Structure",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.GET, produces = "application/json", value = GET_ALL_GL_STRUCTURE)
    @ResponseBody
    public ResponseEntity<?> getallglstructure(@RequestParam String tenantId) throws Exception {
        logger.info("Entered GeneralLedgerStructureController.getallglstructure() method");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = new ResponseEntity(generalLedgerStructureService.getallglstructure(tenantId), HttpStatus.OK);
            logger.info("Successfully fetched Gl Structure list by tenantId :{} ",tenantId);
        } catch (Exception e) {
            responseEntity = new ResponseEntity("Failed to fetch all GL Structure(s) data", HttpStatus.BAD_REQUEST);
            logger.error("Failed to fetched Gl Structure list by tenantId : {}", tenantId,e);
        }
        logger.info("Exited from GeneralLedgerStructureController.getallglstructure() method");
        return responseEntity;
    }

    /**
     * Save GL Structure
     * @param glStructureDTO
     * @return
     * @throws Exception
     */
    @Operation(summary = "Save GL Structure")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Save GL Structure",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ), @ApiResponse(
            responseCode = "422",
            description = "Saving GL Structure failed",
            content = {@Content(schema = @Schema(implementation = String.class))}
    ),
            @ApiResponse(responseCode = "500", description = "Error while Saving GL Structure",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.POST, produces = "application/json", consumes = "application/json", value = SAVE_GL_STRUCTURE)
    @ResponseBody
    public ResponseEntity<?> saveglstructure(@RequestBody GLStructureDTO glStructureDTO) throws Exception {
        logger.info("Entered GeneralLedgerStructureController.saveglstructure() method");
        ResponseEntity responseEntity = null;
        try {
            return generalLedgerStructureService.saveglstructure(glStructureDTO);
        } catch (Exception e) {
            responseEntity = new ResponseEntity("Failed to save GL Structure(s)", HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to save GL Structure: " ,e);
        }
        logger.info("Exited from GeneralLedgerStructureController.saveglstructure() method");
        return responseEntity;
    }

    /**
     * Update GL Structure
     * @param glStructureDTO
     * @param tenantId
     * @return
     * @throws Exception
     */
    @Operation(summary = "Update GL Structure")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Update GL Structure",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ), @ApiResponse(
            responseCode = "422",
            description = "Update GL Structure failed",
            content = {@Content(schema = @Schema(implementation = String.class))}
    ),
            @ApiResponse(responseCode = "500", description = "Error while Update GL Structure",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.POST, produces = "application/json", consumes = "application/json", value = UPDATE_GL_STRUCTURE)
    @ResponseBody
    public ResponseEntity updateglstructure(@RequestBody GLStructureDTO glStructureDTO, @RequestParam String tenantId) throws Exception {
        logger.info("Entered GeneralLedgerStructureController.updateglstructure() method");
        ResponseEntity responseEntity = null;
        try {
            return generalLedgerStructureService.updateglstructure(glStructureDTO, tenantId);
        } catch (Exception e) {
            responseEntity = new ResponseEntity("Failed to Update GL Structure(s)", HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to update GL Structure : ",e);
        }
        logger.info("Exited from GeneralLedgerStructureController.updateglstructure() method");
        return responseEntity;
    }

    /**
     * Get By UUID and TenantId
     * @param tenantId
     * @param uuid
     * @return
     * @throws Exception
     */
    @Operation(summary = "Get GL Structure by Id")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Get GL Structure by Id",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ), @ApiResponse(
            responseCode = "422",
            description = "Get GL Structure by Id failed",
            content = {@Content(schema = @Schema(implementation = String.class))}
    ),
            @ApiResponse(responseCode = "500", description = "Error while getting GL Structure by Id",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.GET, produces = "application/json", value = GET_GL_STRUCTURE_BY_UUID)
    @ResponseBody
    public ResponseEntity<?> getglstructurebyuuid(@RequestParam String tenantId, @RequestParam String uuid) throws Exception {
        logger.info("Entered GeneralLedgerStructureController.getglstructurebyuuid() method");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = new ResponseEntity(generalLedgerStructureService.getglstructurebyuuid(tenantId, uuid), HttpStatus.OK);
            logger.info("Successfully fetched GL Structure list by uuid and tenantid : " + uuid);
        } catch (Exception e) {
            responseEntity = new ResponseEntity("Failed to fetch GL Structure(s) data by Id", HttpStatus.NOT_FOUND);
            logger.error("Failed to fetch GL Structure by uuid {}:", uuid,e);
        }
        logger.info("Exited from GeneralLedgerStructureController.getglstructurebyuuid() method");
        return responseEntity;
    }

    /**
     * Import Gl Structure
     * @param glStructureDTOList
     * @return
     */
    @Operation(summary = "Import GL Structure Data")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Import GL Structure Data",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ResponseEntity.class))
                    }
            ),
            @ApiResponse(responseCode = "500", description = "Error while import GL Structure Data",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.POST, produces = "application/json", consumes = "application/json", value = IMPORT_GL_STRUCTURE)
    @ResponseBody
    public ResponseEntity importglstructure(@RequestBody List<GLStructureDTO> glStructureDTOList) {
        logger.info("Entered GeneralLedgerStructureController.importglstructure() method");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(generalLedgerStructureService.importglstructure(glStructureDTOList), HttpStatus.OK);
        } catch (Exception ex) {
            response = new ResponseEntity("Failed to import bulk data ", HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to import bulk data ",ex);
        }
        logger.info("Exited from GeneralLedgerStructureController.importglstructure() method");
        return response;
    }

    /**
     * Get Parent List
     * @param tenantId
     * @param glStructure
     * @return
     */
    @Operation(summary = "Parent API")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Parent API",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ResponseEntity.class))
                    }
            ),
            @ApiResponse(responseCode = "500", description = "Error while Parent GL Structure API",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.GET, value = GET_PARENT_LIST)
    @ResponseBody
    public ResponseEntity getParentList(@RequestParam String tenantId, @RequestParam String glStructure) {
        logger.info("Entered into GeneralLedgerStructureController.getParentList()");
        ResponseEntity response = null;
        try {
            return generalLedgerStructureService.getParentList(tenantId, glStructure);
        } catch (Exception ex) {
            response = new ResponseEntity("Failed to get parent list for the given name, please contact admin", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        logger.info("Exited from GeneralLedgerStructureController.getParentList()");
        return response;
    }

    /**
     * Get All GL Account
     * @param tenantId
     * @return
     * @throws Exception
     */
    @Operation(summary = "Get All GL Account")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Get All GL Account",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ), @ApiResponse(
            responseCode = "422",
            description = "Get All GL Account failed",
            content = {@Content(schema = @Schema(implementation = String.class))}
    ),
            @ApiResponse(responseCode = "500", description = "Error while getting all GL Account",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.GET, produces = "application/json", value = GET_ALL_GL_ACCOUNT)
    @ResponseBody
    public ResponseEntity<?> getallglaccount(@RequestParam String tenantId) throws Exception {
        logger.info("Entered GeneralLedgerStructureController.getallglaccount() method");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = new ResponseEntity(generalLedgerStructureService.getallglaccount(tenantId), HttpStatus.OK);
            logger.info("Successfully fetched Gl Structure list by tenantId {} : ",tenantId);
        } catch (Exception e) {
            responseEntity = new ResponseEntity("Failed to fetch all GL Account(s) data", HttpStatus.BAD_REQUEST);
            logger.error("Failed to fetched Gl Account list by tenantId {} : ", tenantId,e);
        }
        logger.info("Exited from GeneralLedgerStructureController.getallglaccount() method");
        return responseEntity;
    }

    /**
     * Save GL Account
     * @param glAccountDTO
     * @return
     * @throws Exception
     */
    @Operation(summary = "Save GL Account")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Save GL Account",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ), @ApiResponse(
            responseCode = "422",
            description = "Saving GL Account failed",
            content = {@Content(schema = @Schema(implementation = String.class))}
    ),
            @ApiResponse(responseCode = "500", description = "Error while Saving GL Account",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.POST, produces = "application/json", consumes = "application/json", value = SAVE_GL_ACCOUNT)
    @ResponseBody
    public ResponseEntity<?> saveglaccount(@RequestBody GLAccountDTO glAccountDTO) throws Exception {
        logger.info("Entered GeneralLedgerStructureController.saveglaccount() method");
        ResponseEntity responseEntity = null;
        try {
            return generalLedgerStructureService.saveglaccount(glAccountDTO);
        } catch (Exception e) {
            responseEntity = new ResponseEntity("Failed to save GL Account(s)", HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to save GL Structure: ",e);
        }
        logger.info("Exited from GeneralLedgerStructureController.saveglaccount() method");
        return responseEntity;
    }

    /**
     * Update GL Account
     * @param glAccountDTO
     * @param tenantId
     * @return
     * @throws Exception
     */
    @Operation(summary = "Update GL Account")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Update GL Account",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ), @ApiResponse(
            responseCode = "422",
            description = "Update GL Account failed",
            content = {@Content(schema = @Schema(implementation = String.class))}
    ),
            @ApiResponse(responseCode = "500", description = "Error while Update GL Account",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.POST, produces = "application/json", consumes = "application/json", value = UPDATE_GL_ACCOUNT)
    @ResponseBody
    public ResponseEntity updateglaccount(@RequestBody GLAccountDTO glAccountDTO, @RequestParam String tenantId) throws Exception {
        logger.info("Entered GeneralLedgerStructureController.updateglaccount() method");
        ResponseEntity responseEntity = null;
        try {
            return generalLedgerStructureService.updateglaccount(glAccountDTO, tenantId);
        } catch (Exception e) {
            responseEntity = new ResponseEntity("Failed to Update GL Account(s)", HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to update GL Structure : " ,e);
        }
        logger.info("Exited from GeneralLedgerStructureController.updateglaccount() method");
        return responseEntity;
    }

    /**
     * Get By UUID and TenantId
     * @param tenantId
     * @param uuid
     * @return
     * @throws Exception
     */
    @Operation(summary = "Get GL Account by Id")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Get GL Account by Id",
                    content = {@Content(schema = @Schema(implementation = String.class))}
            ), @ApiResponse(
            responseCode = "422",
            description = "Get GL Account by Id failed",
            content = {@Content(schema = @Schema(implementation = String.class))}
    ),
            @ApiResponse(responseCode = "500", description = "Error while getting GL Account by Id",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.GET, produces = "application/json", value = GET_GL_ACCOUNT_BY_UUID)
    @ResponseBody
    public ResponseEntity<?> getglaccountbyuuid(@RequestParam String tenantId, @RequestParam String uuid) throws Exception {
        logger.info("Entered GeneralLedgerStructureController.getglaccountbyuuid() method");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = new ResponseEntity(generalLedgerStructureService.getglaccountbyuuid(tenantId, uuid), HttpStatus.OK);
            logger.info("Successfully fetched GL Account list by uuid and tenantid {}: ",uuid);
        } catch (Exception e) {
            responseEntity = new ResponseEntity("Failed to fetch GL Account(s) data by Id", HttpStatus.NOT_FOUND);
            logger.error("Failed to fetch GL Account by uuid {}:",uuid,e);
        }
        logger.info("Exited from GeneralLedgerStructureController.getglaccountbyuuid() method");
        return responseEntity;
    }

    /**
     * Import Gl Structure
     * @param glAccountDTOList
     * @return
     */
    @Operation(summary = "Import GL Account Data")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Import GL Account Data",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ResponseEntity.class))
                    }
            ),
            @ApiResponse(responseCode = "500", description = "Error while import GL Account Data",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @RequestMapping(method = RequestMethod.POST, produces = "application/json", consumes = "application/json", value = IMPORT_GL_ACCOUNT)
    @ResponseBody
    public ResponseEntity importglaccount(@RequestBody List<GLAccountDTO> glAccountDTOList) {
        logger.info("Entered GeneralLedgerStructureController.importglaccount() method");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(generalLedgerStructureService.importglaccount(glAccountDTOList), HttpStatus.OK);
        } catch (Exception ex) {
            response = new ResponseEntity("Failed to import bulk data", HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to import bulk data",ex);
        }
        logger.info("Exited from GeneralLedgerStructureController.importglaccount() method");
        return response;
    }
}
