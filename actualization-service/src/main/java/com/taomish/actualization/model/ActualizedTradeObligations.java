package com.taomish.actualization.model;


import com.taomish.common.jpa.AbstractBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.util.ArrayList;

@Entity
@Table(name="xceler_actualizationservice_actualizedtradeobligation")
public class ActualizedTradeObligations extends AbstractBaseEntity {

    @Column
    private String actualizationId;

    @Column
    private String plannedObligationId;

    @Column
    private String plannedObligationType;

    @Column
    private String tradeId;

    @Column
    private String actualizedQuantityId;

    @Column
    @JdbcTypeCode(Types.VARBINARY)
    private ArrayList<String> actualizedCosts;

    @Column
    @JdbcTypeCode(Types.VARBINARY)
    private ArrayList<String> actualizedQuality;

    public String getActualizationId() {
        return actualizationId;
    }

    public void setActualizationId(String actualizationId) {
        this.actualizationId = actualizationId;
    }

    public String getPlannedObligationId() {
        return plannedObligationId;
    }

    public void setPlannedObligationId(String plannedObligationId) {
        this.plannedObligationId = plannedObligationId;
    }

    public String getPlannedObligationType() {
        return plannedObligationType;
    }

    public void setPlannedObligationType(String plannedObligationType) {
        this.plannedObligationType = plannedObligationType;
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public String getActualizedQuantityId() {
        return actualizedQuantityId;
    }

    public void setActualizedQuantityId(String actualizedQuantityId) {
        this.actualizedQuantityId = actualizedQuantityId;
    }

    public ArrayList<String> getActualizedCosts() {
        return actualizedCosts;
    }

    public void setActualizedCosts(ArrayList<String> actualizedCosts) {
        this.actualizedCosts = actualizedCosts;
    }

    public ArrayList<String> getActualizedQuality() {
        return actualizedQuality;
    }

    public void setActualizedQuality(ArrayList<String> actualizedQuality) {
        this.actualizedQuality = actualizedQuality;
    }
}
