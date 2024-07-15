package com.taomish.actualization.v2.models;


import com.taomish.enums.PlannedObligationState;
import com.taomish.enums.TradeTransactionType;
import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Table(name = "vw_trade_planning_trade_plan")
public class PlannedTrades {

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
    @ElementCollection
    @MapKeyColumn(name="ObligationState")
    @MapKeyClass(PlannedObligationState.class)
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name="status")
    @CollectionTable(name="xceler_tradeplanningservice_plannedtradeobligationstate",joinColumns = @JoinColumn(name = "uuid"))
    private Map<PlannedObligationState,Boolean> obligationState= new HashMap<>();


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
    private Double balanceQuantity;

}
