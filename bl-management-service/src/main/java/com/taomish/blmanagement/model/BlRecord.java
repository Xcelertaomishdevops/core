package com.taomish.blmanagement.model;

import com.taomish.common.jpa.AbstractBaseEntity;
import com.taomish.enums.BlRecordType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.util.ArrayList;

@Entity
@Table(name = "xceler_blmanagementservice_blrecord")
public class BlRecord extends AbstractBaseEntity {

    @Column
    private String blNumber;

    @Column
    private String shipper;

    @Column
    private String consignee;

    @Column
    @JdbcTypeCode(Types.VARBINARY)
    private ArrayList<String> notifyParty;

    @Column
    private String quantity;

    @Column
    private String exportReference;

    @Column
    private String forwardingAgent;

    @Column
    private String shipTo;

    @Column
    private String assignContract;

    @Column
    private BlRecordType blRecordType;

    @Column
    private String planId;

    @Column
    private String plannedObligationId;

    @Column
    private String parentBlId;

    @Column
    private String quantityUom;

    public String getBlNumber() {
        return blNumber;
    }

    public void setBlNumber(String blNumber) {
        this.blNumber = blNumber;
    }

    public String getShipper() {
        return shipper;
    }

    public void setShipper(String shipper) {
        this.shipper = shipper;
    }

    public String getConsignee() {
        return consignee;
    }

    public void setConsignee(String consignee) {
        this.consignee = consignee;
    }



    public String getExportReference() {
        return exportReference;
    }

    public void setExportReference(String exportReference) {
        this.exportReference = exportReference;
    }

    public String getForwardingAgent() {
        return forwardingAgent;
    }

    public void setForwardingAgent(String forwardingAgent) {
        this.forwardingAgent = forwardingAgent;
    }

    public String getShipTo() {
        return shipTo;
    }

    public void setShipTo(String shipTo) {
        this.shipTo = shipTo;
    }

    public String getAssignContract() {
        return assignContract;
    }

    public void setAssignContract(String assignContract) {
        this.assignContract = assignContract;
    }

    public BlRecordType getBlRecordType() {
        return blRecordType;
    }

    public void setBlRecordType(BlRecordType blRecordType) {
        this.blRecordType = blRecordType;
    }

    public String getParentBlId() {
        return parentBlId;
    }

    public void setParentBlId(String parentBlId) {
        this.parentBlId = parentBlId;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getPlannedObligationId() {
        return plannedObligationId;
    }

    public void setPlannedObligationId(String plannedObligationId) {
        this.plannedObligationId = plannedObligationId;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public ArrayList<String> getNotifyParty() {
        return notifyParty;
    }

    public void setNotifyParty(ArrayList<String> notifyParty) {
        this.notifyParty = notifyParty;
    }

    public String getQuantityUom() {
        return quantityUom;
    }

    public void setQuantityUom(String quantityUom) {
        this.quantityUom = quantityUom;
    }
}
