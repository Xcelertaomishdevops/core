package com.taomish.actualization.dto;

import com.taomish.common.jpa.AbstractBaseDto;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GrnStockDTO extends AbstractBaseDto {

    private String grnId;
    private String plannedObligationId;
    private Integer splitSequenceNumber;
    private String actualizeId;
    private Double grnQuantity;
    private String transferId;
    private LocalDateTime grnDate;
    private Double lossGainQuantity;
}
