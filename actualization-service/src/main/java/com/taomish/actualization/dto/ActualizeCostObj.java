package com.taomish.actualization.dto;

import com.taomish.common.jpa.AbstractBaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class ActualizeCostObj extends AbstractBaseDto {
    private String tradeId;
    private String plannedObligationId;
    private String name;
    private String type;
    private String counterparty;
    private String costGroup;
    private String costType;
    private Double costValue;
    private double claimedCostValue;
    private String costCurrency;
    private String uom;
    private String quantityOption;
    private String percentageComponent;
    private String description;
    private LocalDateTime inputDate;
    private LocalDateTime effectiveDate;
    private LocalDateTime maturityDate;
    private String paymentType;
    private String paymentTerm;
    private LocalDateTime paymentDueDate;
    private double percentagePayable;
    private boolean taxApplicable;
    private String additionalNote;
    private String actualizedStatus;
    private boolean estimated;
    private String actualizedCostId;
    private String costId;
    private boolean quantityActualized = false;
    private boolean finalInvoiced = false;
    private String costFor;
    private String linkTo;
    private String costChargesType;
    private Integer splitSequenceNumber = 0;
    private String costMatrixId;
    private Map<String, String> costMatrixWorkflow;
}
