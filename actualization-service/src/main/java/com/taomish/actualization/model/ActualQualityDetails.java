package com.taomish.actualization.model;

import com.taomish.common.jpa.AbstractBaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.util.HashMap;
import java.util.Map;

@Entity
@Data
@Table(name="xceler_actualizationservice_actual_quality_details")
public class ActualQualityDetails extends AbstractBaseEntity {
    private String stage;
    private String buyer;
    private String supplier;
    private String umpire;
    private String activeQuality;
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String,String> customAddedFields= new HashMap<>();


}
