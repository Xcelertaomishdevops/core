package com.taomish.actualization.model;

import com.taomish.common.jpa.AbstractBaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@Table(name="xceler_actualizationservice_billoflanding")
public class BillOfLanding extends AbstractBaseEntity {
    @Column
    private LocalDateTime blDate;

    @Column
    private String blNumber;

    @Column
    private String stowageNo;

    @Column
    private String selfToOrder;

    @Column
    private String shipper;

    @Column
    private String consignee;

    @Column
    private String contractRef;

    @Column
    private double splitSequenceNumber;

    @Column
    private double actualQuantity;

    @Column
    private String uom;

    @Column
    private String tradeId;

    @Column
    @JdbcTypeCode(Types.VARBINARY)
    private ArrayList<String> notifyParty;

    @Column
    @JdbcTypeCode(Types.VARBINARY)
    private ArrayList<String> forwardingAgent;

    @Column
    private String actualizationId;

    @Column
    private String plannedObligationId;

    @Column
    private Boolean surrendered;

    @Column
    private String status;

    @Column
    private LocalDateTime shipOnboard;

    @Column
    private String remarks;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, String>> customFields;

    @Column
    private LocalDateTime norDate;

    @Column
    private String assignmentSurveyor;

    @Column
    private String importLicenceNo;

    @Column
    private String consignmentNo;

    @Column
    private String flag;

    @Column
    private String master;

    @Column
    private LocalDateTime charterDate;
}
