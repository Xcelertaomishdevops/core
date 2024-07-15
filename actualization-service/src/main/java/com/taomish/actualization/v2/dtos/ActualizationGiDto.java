package com.taomish.actualization.v2.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActualizationGiDto {
    private String transferId;
    private String purchaseId;
    private String plannedObligationId;
    private Double actualizeQuantity=0.0;
    private Integer splitSequenceNumber;
    private String actualizeId;
    private String planId;
    private LocalDateTime actualizeDate;
    private Double conversionFactor;
}
