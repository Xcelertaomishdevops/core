package com.taomish.actualization.model;

import com.taomish.common.jpa.AbstractBaseEntity;
import com.taomish.enums.TradeTransactionType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;

@Setter
@Getter
@Entity
@Table(name = "vw_bl_with_plan_id")
@EqualsAndHashCode(callSuper = true)
public class BillOfLandingV2 extends AbstractBaseEntity {

    private LocalDateTime blDate;


    private String blNumber;


    private String stowageNo;


    private String selfToOrder;


    private String shipper;


    private String consignee;


    private String contractRef;


    private Double splitSequenceNumber;


    private Double actualQuantity;


    private String uom;


    private String tradeId;

    @JdbcTypeCode(Types.VARBINARY)
    private ArrayList<String> notifyParty;


    @JdbcTypeCode(Types.VARBINARY)
    private ArrayList<String> forwardingAgent;


    private String actualizationId;


    private String plannedObligationId;


    private Boolean surrendered;


    private String status;


    private LocalDateTime shipOnboard;


    private String remarks;

    private LocalDateTime norDate;


    private String assignmentSurveyor;


    private String importLicenceNo;


    private String consignmentNo;


    private String flag;


    private String master;


    private LocalDateTime charterDate;
    private String planId;
    private Boolean replacementBl;
    private Double claimedQuantity;
    private TradeTransactionType tradeTransactionType;
    private Double plannedQuantity;
    private String quantityUom;
}
