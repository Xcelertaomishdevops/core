package com.taomish.auditloggingservice.service;

import com.taomish.dtos.auditloggingservice.AuditLoggingRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditLoggingService {

    private final Logger logger = LoggerFactory.getLogger(AuditLoggingService.class);

    private final String METHOD = "Method";
    private final String NEW_DATA = "NewData";
    private final String EXISTING_DATA = "ExistingData";
    private final String USER_ID = "USER_ID";
    private final String AUDITLOG = "AUDITLOG";

    /**
     * Write the audit data to the audit log file.
     *
     * @param auditLog
     */
    public void auditLog(AuditLoggingRequestDTO auditLog) {
        StringBuilder logBuilder = new StringBuilder(AUDITLOG).append(" ::")
                .append(" ").append(METHOD).append(": ").append(auditLog.getMethod())
                .append(" ").append(USER_ID).append(": ").append(auditLog.getUserId())
                .append(" ").append(NEW_DATA).append(": ").append(auditLog.getNewData().toString());
        if (null != auditLog.getExistingData()) {
            logBuilder.append(" ").append(EXISTING_DATA).append(": ").append(auditLog.getExistingData().toString());
        }
        logger.info(logBuilder.toString());
    }

}
