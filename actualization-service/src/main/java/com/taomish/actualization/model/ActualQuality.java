package com.taomish.actualization.model;

import com.taomish.common.jpa.AbstractBaseEntity;
import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Table(name="xceler_actualizationservice_actual_quality")
public class ActualQuality extends AbstractBaseEntity {
    private String qualityParameter;
    private String contractedValue;
    private String actualizationId;
    private String basis;
    private String unit;
    private String plannedObligationId;
    private LocalDateTime actualizedOn = LocalDateTime.now();
    private String tradeId;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "actualized_quality_id", nullable = false)
    private List<ActualQualityDetails> actualizedQualityId;
}
