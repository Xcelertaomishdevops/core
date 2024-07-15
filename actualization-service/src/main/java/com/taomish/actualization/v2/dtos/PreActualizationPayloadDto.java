package com.taomish.actualization.v2.dtos;

import com.taomish.actualization.model.ActualPackingDetails;
import com.taomish.actualization.model.ActualQuality;
import com.taomish.actualization.model.ActualizedQuantityObligations;
import com.taomish.actualization.model.BillOfLanding;
import com.taomish.dtos.actualizationservice.ActualizedQuantityObligationsDTO;
import com.taomish.dtos.cashflowservice.CashflowDataDTO;
import com.taomish.dtos.physicaltradeplanning.PhysicalTradePlanningDTO;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PreActualizationPayloadDto {
    List<ActualizedQuantityObligations> actualizedQuantityObligationsList = new ArrayList<>();
    List<ActualizedQuantityObligationsDTO> actualizedQuantityObligationsDTOListForEOD = new ArrayList<>();
    List<CashflowDataDTO> cashflowDataDTOList = new ArrayList<>();
    List<BillOfLanding> blRecords = new ArrayList<>();
    List<ActualQuality> qualities = new ArrayList<>();
    List<ActualPackingDetails> actualPackingDetails = new ArrayList<>();

    List<PhysicalTradePlanningDTO> interCompanyPlanningList = new ArrayList<>();
    List<String> obligationIds = new ArrayList<>();
}
