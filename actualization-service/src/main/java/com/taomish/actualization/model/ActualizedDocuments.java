package com.taomish.actualization.model;

import com.taomish.common.jpa.AbstractBaseEntity;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name="xceler_actualizationservice_actualizeddocuments")
public class ActualizedDocuments extends AbstractBaseEntity {

    @Column
    private String actualizationId = "";

    @Column
    private String plannedObligationId;

    @Column
    private LocalDateTime actualizedOn = LocalDateTime.now();

    @Column
    private String tradeId;

    @Column
    private String attachmentFileName;

    @Column
    private String fileSize;

    @Column
    private String attachmentFileType;

    @Column(nullable = false)
    private String attachmentUrl;

    @Column
    private String uploadedBy;

    @CreationTimestamp
    @Column
    private LocalDateTime uploadDate;

    @Column
    private LocalDateTime messageDate;

    @Column
    private Integer msgSessionId;

    @Column
    private String shipmentDocument;

    @Column
    private String documentType;

    @Column
    private String status;

    @Column
    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getShipmentDocument() {
        return shipmentDocument;
    }

    public void setShipmentDocument(String shipmentDocument) {
        this.shipmentDocument = shipmentDocument;
    }

    public String getActualizationId() {
        return actualizationId;
    }

    public void setActualizationId(String actualizationId) {
        this.actualizationId = actualizationId;
    }

    public String getPlannedObligationId() {
        return plannedObligationId;
    }

    public void setPlannedObligationId(String plannedObligationId) {
        this.plannedObligationId = plannedObligationId;
    }

    public LocalDateTime getActualizedOn() {
        return actualizedOn;
    }

    public void setActualizedOn(LocalDateTime actualizedOn) {
        this.actualizedOn = actualizedOn;
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public String getAttachmentFileName() {
        return attachmentFileName;
    }

    public void setAttachmentFileName(String attachmentFileName) {
        this.attachmentFileName = attachmentFileName;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    public String getAttachmentFileType() {
        return attachmentFileType;
    }

    public void setAttachmentFileType(String attachmentFileType) {
        this.attachmentFileType = attachmentFileType;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public void setAttachmentUrl(String attachmentUrl) {
        this.attachmentUrl = attachmentUrl;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public LocalDateTime getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(LocalDateTime uploadDate) {
        this.uploadDate = uploadDate;
    }

    public LocalDateTime getMessageDate() {
        return messageDate;
    }

    public void setMessageDate(LocalDateTime messageDate) {
        this.messageDate = messageDate;
    }

    public Integer getMsgSessionId() {
        return msgSessionId;
    }

    public void setMsgSessionId(Integer msgSessionId) {
        this.msgSessionId = msgSessionId;
    }
}
