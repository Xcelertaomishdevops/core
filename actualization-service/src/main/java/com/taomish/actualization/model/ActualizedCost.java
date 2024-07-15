package com.taomish.actualization.model;


import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;


@Entity
@Table(name="xceler_actualizationservice_actualizedcost")
@Getter
@Setter
public class ActualizedCost extends ActualizationEntity implements Serializable {

    @Column
    private String actualizedCostId;

    @Column
    private String name;

    @Column
    private String type;

    @Column
    private String counterparty;

    @Column
    private String costGroup;

    @Column
    private String costType;

    @Column
    private double costValue;

    @Column
    private double claimedCostValue = 0;

    @Column
    private String costCurrency;

    @Column
    private String uom;

    @Column
    private String quantityOption;

    @Column
    private String percentageComponent;

    @Column
    private String description;

    @Column
    private LocalDateTime inputDate;

    @Column
    private LocalDateTime effectiveDate;

    @Column
    private LocalDateTime maturityDate;

    @Column
    private String paymentType;

    @Column
    private String paymentTerm;

    @Column
    private LocalDateTime paymentDueDate;

    @Column
    private double percentagePayable;

    @Column
    private boolean taxApplicable;

    @Column
    private String additionalNote;

    @Column
    private String estimatedCostId;

    @Column
    private String costFor;

    @Column
    private String linkTo;

    @Column
    private String costChargesType;

    @Column
    private Integer splitSequenceNumber = 0;
}
