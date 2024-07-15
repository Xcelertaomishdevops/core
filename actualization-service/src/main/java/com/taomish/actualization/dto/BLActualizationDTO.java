package com.taomish.actualization.dto;

import com.taomish.common.jpa.AbstractBaseDto;
import com.taomish.dtos.transportactualizationservice.BillOfLandingDTO;
import com.taomish.enums.PlannedObligationState;
import com.taomish.enums.TradeTransactionType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class BLActualizationDTO extends AbstractBaseDto {
    private String planId;
    private String plannedObligationId;
    private String tradeId;
    private String uom;
    private String tradeSettlementReference;
    private Double plannedQuantity;
    private String packageType;
    private Double packageCapacity;
    private double balanceQuantity=0.0;
    private String packageUom;
    private Double noOfUnits;
    private Boolean finalInvoiced;
    private String contractTerm;
    private String status;
    private Double actualQuantity;
    private Double splitQuantity;
    private Integer splitSequenceNumber;
    private String href;
    private String externalPackage;
    private String internalPackage;
    private Double actualExternalPackage;
    private Double actualInternalPackage;
    private Double loadQuantity;
    private Double unLoadQuantity;
    private String matchType;
    private TradeTransactionType tradeTransactionType;
    private Double adjusted;
    private Double lossGainQuantity;
    private String counterparty;
    private Double tradeQuantity;
    private LocalDateTime dischargeDate;
    private String toleranceValue;
    private String settlementToleranceValue;
    private String commodity;
    private Map<PlannedObligationState,Boolean> obligationState= new HashMap<>();
    private List<BillOfLandingDTO> blSplitRows;
    private List<ActualQualityDTO> qualityDetails;
    private Boolean invoiceClaim;
    private Double externalPackageUnit=0.0;
    private Double internalPackageUnit=0.0;
    private List<ActualPackingDetailsDTO> packingDetails;
    private Double claimedQuantity;
    private LocalDateTime tradeDateTime;
    private Double grnQuantity=0.0;
    private String stockType;
    private String stockTransferId;
    private Boolean isRowEditable=true;
    private Boolean updateReceviedQty=false;
    private String grnId;
    private LocalDateTime grnDate;
    private Boolean isOis=false;
}
