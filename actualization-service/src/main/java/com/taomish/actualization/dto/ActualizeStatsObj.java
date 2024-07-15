package com.taomish.actualization.dto;

import org.springframework.stereotype.Component;

@Component
public class ActualizeStatsObj {

    private Integer partiallyActualized;

    private Integer actualized;

    private Integer deliveryStarted;

    public Integer getPartiallyActualized() {
        return partiallyActualized;
    }

    public void setPartiallyActualized(Integer partiallyActualized) {
        this.partiallyActualized = partiallyActualized;
    }

    public Integer getActualized() {
        return actualized;
    }

    public void setActualized(Integer actualized) {
        this.actualized = actualized;
    }

    public Integer getDeliveryStarted() {
        return deliveryStarted;
    }

    public void setDeliveryStarted(Integer deliveryStarted) {
        this.deliveryStarted = deliveryStarted;
    }
}
