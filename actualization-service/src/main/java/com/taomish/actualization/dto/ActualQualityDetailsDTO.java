package com.taomish.actualization.dto;
import com.taomish.common.jpa.AbstractBaseDto;
import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class ActualQualityDetailsDTO extends AbstractBaseDto {
    private String stage;
    private String buyer;
    private String supplier;
    private String umpire;
    private String activeQuality;
    private Map<String,String> customAddedFields= new HashMap<>();
}
