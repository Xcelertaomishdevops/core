package com.taomish.actualization.model;

import lombok.Data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Data
@Table(name="xceler_actualizationservice_actualizedqualityspec")
public class ActualizedQuality extends ActualizationEntity {

    @Column
    private String actualizedQualityId;

    @Column
    private String name;

    @Column
    private String description;

    @Column
    private Float min;

    @Column
    private Float max;

    @Column
    private String typical;

    @Column
    private String settlementType;

    @Column
    private double basis;

    @Column
    private String unit;

    @Column
    private String estimatedQualitySpecId;

    @Column
    private double premiumDiscount = 0;

    @Column
    private double claimedBasis = 0;

    @Column
    private double claimedPremiumDiscount = 0;

}
