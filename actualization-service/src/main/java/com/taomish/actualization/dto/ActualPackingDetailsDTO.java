package com.taomish.actualization.dto;
import com.taomish.common.jpa.AbstractBaseDto;
import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class ActualPackingDetailsDTO extends AbstractBaseDto {
    private String packagingId;
    private String internalPackageNo;
    private String actualizationId;
    private String plannedObligationId;
    private String sealId;
    private String marksAndNumber;
    private String shippingMark;
    private Double grossWeight;
    private Double netWeight;
    private Double splitSequenceNumber;
    private Map<String,String> customAddedFields= new HashMap<>();
    private String transportNo;
    private String internalPackageName;
}
