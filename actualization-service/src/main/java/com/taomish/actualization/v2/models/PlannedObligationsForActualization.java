package com.taomish.actualization.v2.models;



import com.taomish.actualization.v2.converter.MapToStringConverter;
import com.taomish.dtos.physicaltradeservice.PlannedObligationDTO;
import com.taomish.enums.PlannedObligationState;
import com.taomish.enums.TradeTransactionType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Immutable;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.*;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Immutable
@Entity
@Table(name = "vw_planned_obligations_for_actualization")
public class PlannedObligationsForActualization extends PlannedObligationDTO {
    @Id
    private UUID uuid;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdTimestamp;
    private LocalDateTime updatedTimestamp;
    private String tenantId;
    private Integer circleStringSeq;
    private String broker;
    private String company;
    private String plannedObligationId;
    private String tradeObligationId;
    private String planId;
    private String planUuid;
    private String tradeId;
    private String tradeUuid;
    private TradeTransactionType tradeTransactionType;
    private String tradeSettlementCurrency;
    private String trader;
    private LocalDateTime tradeDateTime;
    private String commodity;
    private String counterparty;
    private double tradeQuantity;
    private String profitCenter;
    private String incoterm;
    private String location;
    private String cropOrigin;
    private String quantityuom;
    private String grade;
    private String brand;
    private double plannedQuantity;
    private LocalDateTime deliveryStartDate;
    private LocalDateTime deliveryEndDate;
    private String priceType;
    private double tradePrice;
    private String tradePriceCurrency;
    private String tradePriceUom;
    private double noOfUnits;
    private String loadLocation;
    private String unloadLocation;
    private String pricingStatus;
    private String provisionalPriceType;
    private String priceAllocationstatus;
    private Double provisionalPrice;
    private String paymentTerm;
    private String provisionalPriceCurrency;
    private String provisionalPriceUom;
    private Double fxRate;
    private Double provisionalFxRate;
    private double discountCost;
    private double totalTradeQty;
    private Double quantity;
    private LocalDateTime planMaxTime;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<PlannedObligationState,Boolean> obligationState= new EnumMap<>(PlannedObligationState.class);

    private LocalDateTime deliveryDate;
    private LocalDateTime obligationDate;
    private String referenceNumber;
    private String tradeObligationUuid;
    private String tradeType;
    private String remarks;
    private String season;
    private Double provisionalPricePercentage;
    private double premiunCost;
    private String brokerReferenceNumber;
    private String plannedObligationStatus;
    private String parentPlannedObligation;
    private String shipmentMonth;
    private Boolean externalRecord = false;
    private String matchType;
    private String allocationType;
    private String vesselId;
    private String vesselName;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        PlannedObligationsForActualization that = (PlannedObligationsForActualization) o;
        return uuid != null && Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
