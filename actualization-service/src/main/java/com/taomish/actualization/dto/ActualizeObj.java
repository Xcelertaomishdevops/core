package com.taomish.actualization.dto;

import com.taomish.dtos.actualizationservice.ActualizationQualityObj;
import com.taomish.dtos.physicaltradeservice.PlannedObligationDTO;
import com.taomish.dtos.transportactualizationservice.TransportActualizationQuantityRows;
import lombok.Data;

import java.util.List;

@Data
public class ActualizeObj {
    private PlannedObligationDTO plannedObligation;
    private List<ActualizeCostObj> costs;
    private List<TransportActualizationQuantityRows> quantityRows;
    private List<ActualizationQualityObj> qualitySpecs;
    private ActualizationQuantityObj quantity;
    private String matchType;
}
