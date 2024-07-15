package com.taomish.actualization.dto;
import com.taomish.common.jpa.AbstractBaseDto;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ActualQualityDTO extends AbstractBaseDto {
    private String qualityParameter;
    private String contractedValue;
    private String actualizationId;
    private String basis;
    private String unit;
    private String plannedObligationId;
    private LocalDateTime actualizedOn = LocalDateTime.now();
    private String tradeId;
    private List<ActualQualityDetailsDTO> actualizedQualityId;
}
