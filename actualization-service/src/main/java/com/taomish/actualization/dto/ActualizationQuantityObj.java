package com.taomish.actualization.dto;
import com.taomish.common.jpa.AbstractBaseDto;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ActualizationQuantityObj extends AbstractBaseDto {
    private double loadQuantity;

    private double unloadQuantity;

    private double plannedQuantity;

    private String quantityUom;

    private LocalDateTime deliveryStartDate;

    private LocalDateTime deliveryEndDate;

    private String brand;

    private String grade;

    private String origin;

    private String commodity;

    private String tradeId;

    private String plannedObligationId;

    private String tolerance;

    private String packageType;
    private String internalPackage;
    private double internalPackageUnit;
    private String externalPackage;
    private double externalPackageUnit;
    private String purpose;
    private String actualizationId;

    private Double claimedQuantity = 0.0;

    private List<ActualizedPaymentEventDTO> actualizationEventMapping;
}
