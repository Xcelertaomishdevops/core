package com.taomish.actualization.dto;


import com.taomish.dtos.actualizationservice.ActualizationQualityObj;

import java.util.List;

public class AllActualizeObj {

    private List<ActualizeCostObj> costs;

    private List<ActualizationQualityObj> qualitySpecs;

    private ActualizationQuantityObj quantity;

    public List<ActualizeCostObj> getCosts() {
        return costs;
    }

    public void setCosts(List<ActualizeCostObj> costs) {
        this.costs = costs;
    }

    public List<ActualizationQualityObj> getQualitySpecs() {
        return qualitySpecs;
    }

    public void setQualitySpecs(List<ActualizationQualityObj> qualitySpecs) {
        this.qualitySpecs = qualitySpecs;
    }

    public ActualizationQuantityObj getQuantity() {
        return quantity;
    }

    public void setQuantity(ActualizationQuantityObj quantity) {
        this.quantity = quantity;
    }
}
