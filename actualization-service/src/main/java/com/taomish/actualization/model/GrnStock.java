package com.taomish.actualization.model;

import com.taomish.common.jpa.AbstractBaseEntity;
import jakarta.persistence.Entity;
import lombok.Data;
import java.time.LocalDateTime;


@Data
@Entity(name = "xceler_grn_stock")
public class GrnStock extends AbstractBaseEntity {
    private String grnId;
    private String plannedObligationId;
    private Integer splitSequenceNumber;
    private String actualizeId;
    private Double grnQuantity;
    private Double lossGainQuantity;
    private String transferId;
    private LocalDateTime grnDate;
}
