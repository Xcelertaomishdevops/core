package com.taomish.blmanagement.dto;

import com.taomish.common.jpa.AbstractBaseDto;
import com.taomish.enums.BlRecordType;

import java.util.ArrayList;

public class BlRecordDTO extends AbstractBaseDto {

    private String blNumber;

    private String shipper;

    private String consignee;

    private ArrayList<String> notifyParty;

    private String quantity;

    private String exportReference;

    private String forwardingAgent;

    private String shipTo;

    private String assignContract;

    private BlRecordType blRecordType;

    private String parentBlId;

    private String planId;

    private String plannedObligationId;

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

    public ArrayList<String> getNotifyParty() {
        return notifyParty;
    }

    public void setNotifyParty(ArrayList<String> notifyParty) {
        this.notifyParty = notifyParty;
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

    public String getParentBlId() {
        return parentBlId;
    }

    public void setParentBlId(String parentBlId) {
        this.parentBlId = parentBlId;
    }

    public String getQuantityUom() {
        return quantityUom;
    }

    public void setQuantityUom(String quantityUom) {
        this.quantityUom = quantityUom;
    }
}
