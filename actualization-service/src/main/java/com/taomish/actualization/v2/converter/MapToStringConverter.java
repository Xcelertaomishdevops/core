package com.taomish.actualization.v2.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.taomish.common.utils.TransactionIdUtil;
import com.taomish.enums.PlannedObligationState;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.SneakyThrows;

import java.util.Map;

@Converter
public class MapToStringConverter implements AttributeConverter<Map<PlannedObligationState, Boolean>,String> {

    @SneakyThrows
    @Override
    public String convertToDatabaseColumn(Map<PlannedObligationState, Boolean> attribute) {
        return TransactionIdUtil.getObjectMapper().writeValueAsString(attribute);
    }

    @SneakyThrows
    @Override
    public Map<PlannedObligationState, Boolean> convertToEntityAttribute(String dbData) {
        return TransactionIdUtil.getObjectMapper().readValue(dbData, new TypeReference<>() {});
    }
}
