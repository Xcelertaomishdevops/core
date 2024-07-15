package com.taomish.auditloggingservice.controller;

import com.taomish.dtos.auditloggingservice.AuditLoggingRequestDTO;
import com.taomish.auditloggingservice.dto.AuditLoggingResponseDTO;
import com.taomish.auditloggingservice.service.AuditLoggingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import static com.taomish.RestEndPoints.RestEndpointRegister.AUDIT_LOG;
import static com.taomish.RestEndPoints.RestEndpointRegister.AUDIT_LOG_ROOT;

@RestController
@RequestMapping(value = AUDIT_LOG_ROOT)
public class AuditLoggingController {

    private final Logger logger = LoggerFactory.getLogger(AuditLoggingController.class);

    @Autowired
    AuditLoggingService auditLoggingService;

    @PostMapping(AUDIT_LOG)
    @Operation(summary = "Log the audit log data", description = "Method to log the audit log data")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Audit data successfully logged",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = AuditLoggingResponseDTO.class))
                    }
            ),
            @ApiResponse(responseCode = "400",
                    description = "The server could not understand the request due to invalid syntax.",
                    content = {@Content(schema = @Schema(implementation = String.class))}),
            @ApiResponse(responseCode = "500", description = "Error while logging audit data",
                    content = {@Content(schema = @Schema(implementation = AuditLoggingResponseDTO.class))})
    })
    public ResponseEntity<AuditLoggingResponseDTO> auditLog(
            @Valid @RequestBody AuditLoggingRequestDTO auditLoggingRequest) {
        try {
            auditLoggingService.auditLog(auditLoggingRequest);
            return new ResponseEntity(
                    new AuditLoggingResponseDTO(true, "Audit data successfully logged"), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error while logging audit data",e);
            return new ResponseEntity(
                    new AuditLoggingResponseDTO(false, e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
