package com.taomish.actualization.v2.dtos;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Immutable
public class PlannedObligationsForActualizationDto {
    private UUID uuid;
    private String createdBy;
    private String updatedBy;
    private OffsetDateTime createdTimestamp;
    private OffsetDateTime updatedTimestamp;
    private String tenantId;
    private Long circleStringSeq;
    private String company;
    private String plannedObligationId;
    private String tradeObligationId;
    private String planId;
    private String planUuid;
    private String tradeId;
    private String tradeUuid;
    private Long tradeTransactionType;
    private String tradeSettlementCurrency;
    private String trader;
    private OffsetDateTime tradeDateTime;
    private String commodity;
    private String counterparty;
    private Long tradeQuantity;
    private String profitCenter;
    private String incoterm;
    private String location;
    private String cropOrigin;
    private String quantityUom;
    private String grade;
    private String brand;
    private Double plannedQuantity;
    private OffsetDateTime deliveryStartDate;
    private OffsetDateTime deliveryEndDate;
    private String priceType;
    private Long tradePrice;
    private String tradePriceCurrency;
    private String tradePriceUom;
    private Long noOfUnits;
    private String loadLocation;
    private String unloadLocation;
    private String pricingStatus;
    private String provisionalPriceType;
    private String provisionalPrice;
    private String paymentTerm;
    private String provisionalPriceCurrency;
    private String provisionalPriceUom;
    private Long fxRate;
    private Long provisionalFxRate;
    private Long discountCost;
    private Long premiumCost;
    private Long totalTradeQty;
    private Double quantity;
    private String obligationState;
    private Boolean externalRecord = false;
    private String tradeSettlementReference;
    private Boolean transportAllocatedStatus;

    private Boolean settled;
    private Boolean stocked;
    private Boolean cndninvoiced;
    private Boolean deliverystarted;
    private Boolean provinvoiced;
    private Boolean actualized;
    private Boolean priced;
    private Boolean partiallyfxallocated;
    private Boolean discharged;
    private Boolean docbypassfinal;
    private Boolean fxallocated;
    private Boolean docbypassboth;
    private Boolean provpriced;
    private Boolean finalinvoiced;
    private Boolean initialinvoiced;
    private Boolean planned;
    private Boolean partiallysettled;
    private Boolean docbypasscommercial;
    private Boolean docbypasscndn;
    private Boolean docbypass;
    private Boolean partiallypriced;

    private double balanceQuantity = 0.0;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        PlannedObligationsForActualizationDto that = (PlannedObligationsForActualizationDto) o;
        return uuid != null && Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
