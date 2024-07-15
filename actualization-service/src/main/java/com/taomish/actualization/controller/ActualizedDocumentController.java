package com.taomish.actualization.controller;

import com.taomish.actualization.service.ActualizationService;
import com.taomish.dtos.ReturnStatus;
import com.taomish.dtos.actualizationservice.ActualizedDocumentsDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static com.taomish.constants.ActualizationConstants.*;


@RestController
@RequestMapping(value = ACTUALIZATION_DOCUMENT_ROOT)
public class ActualizedDocumentController {

    @Autowired
    ActualizationService actualizationService;

    private static final Logger logger = LoggerFactory.getLogger(ActualizedDocumentController.class);

    @Operation(summary = "Download the Document")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Download the Document",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ResponseEntity.class))
                    }
            ),
            @ApiResponse(responseCode = "500", description = "Error while Downloading the Document",
                    content = {@Content(schema = @Schema(implementation = String.class))})
    })
    @GetMapping(value = GET_DOCUMENT_BY_ATTACHMENTNAME)
    public ResponseEntity getdocumentbyattachmentname(HttpServletResponse response, @RequestParam String tenantId, @RequestParam String attachmentName) throws IOException, SQLException {
        logger.info("Entered into ActualizedDocumentController.getdocumentbyattachmentname");
        ResponseEntity responseEntity = null;
        try {
            responseEntity = actualizationService.getDocumentByAttachmentName(response, tenantId, attachmentName);
        } catch (Exception ex) {
            responseEntity = new ResponseEntity("Failed to get Document by AttachemntName", HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to get Document by AttachemntName: " + attachmentName + " because of following error ",ex);

        }
        logger.info("Exited from ActualizedDocumentController.getdocumentbyattachmentname");
        return responseEntity;
    }


    @PostMapping(UPDATE_DOCUMENT_STATUS)
    public String updateDocumentsStatus(@RequestBody List<String> documentUuidList, @RequestParam String tenantId, @RequestHeader(required = false, value = "Authorization") String token) {
        logger.info("Entered to ActualizedDocumentController.updateDocumentsStatus()");
        ResponseEntity response = null;
        try {
            logger.info("Actualized document Status Updated successfully !");
            return actualizationService.updateDocumentStatus(documentUuidList,tenantId);
        } catch (Exception e) {
            logger.error("Failed to update document Status :",e);
        }
        logger.info("Exited from ActualizedDocumentController.updateDocumentsStatus()");
        return "Not Updated !!";
    }
}



