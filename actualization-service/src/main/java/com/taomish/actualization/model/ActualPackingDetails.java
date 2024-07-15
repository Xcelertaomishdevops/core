package com.taomish.actualization.model;

import com.taomish.common.jpa.AbstractBaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import lombok.Data;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.util.HashMap;
import java.util.Map;

@Entity
@Data
@Table(name="xceler_actualizationservice_actual_packing_details")
public class ActualPackingDetails extends AbstractBaseEntity {
    private String packagingId;
    private String internalPackageNo;
    private String actualizationId;
    private String plannedObligationId;
    private String sealId;
    private String marksAndNumber;
    private String shippingMark;
    private Double grossWeight;
    private Double netWeight;
    private Double splitSequenceNumber;
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String,String> customAddedFields= new HashMap<>();
    private String transportNo;
    private String internalPackageName;
}
