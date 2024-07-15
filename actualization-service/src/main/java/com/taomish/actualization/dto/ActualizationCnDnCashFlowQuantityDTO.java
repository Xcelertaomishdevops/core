package com.taomish.actualization.dto;

import java.sql.Date;

public class ActualizationCnDnCashFlowQuantityDTO {
    private String tradeId;
    private double price = 0.0;
    private String priceType;
    private Boolean buySell;
    private String obligationId;
    private String stage;
    private String tradePriceCurrency;
    private String tradeSettlementCurrency;
    private String type;
    private String planId;
    private String plannedObligationId;
    private Date deliveryDate;
    private String cashflowId;
    private double obligationQuantity = 0.0;
    private double totalContractQuantity = 0.0;
    private String counterparty;
    private String commodity;
    private double fxRate = 0.0;
    private String originalInvoice;

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }


    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getPriceType() {
        return priceType;
    }

    public void setPriceType(String priceType) {
        this.priceType = priceType;
    }

    public Boolean getBuySell() {
        return buySell;
    }

    public void setBuySell(Boolean buySell) {
        this.buySell = buySell;
    }

    public String getObligationId() {
        return obligationId;
    }

    public void setObligationId(String obligationId) {
        this.obligationId = obligationId;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getTradePriceCurrency() {
        return tradePriceCurrency;
    }

    public void setTradePriceCurrency(String tradePriceCurrency) {
        this.tradePriceCurrency = tradePriceCurrency;
    }

    public String getTradeSettlementCurrency() {
        return tradeSettlementCurrency;
    }

    public void setTradeSettlementCurrency(String tradeSettlementCurrency) {
        this.tradeSettlementCurrency = tradeSettlementCurrency;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getPlannedObligationId() {
        return plannedObligationId;
    }

    public void setPlannedObligationId(String plannedObligationId) {
        this.plannedObligationId = plannedObligationId;
    }

    public Date getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(Date deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public String getCashflowId() {
        return cashflowId;
    }

    public void setCashflowId(String cashflowId) {
        this.cashflowId = cashflowId;
    }

    public double getObligationQuantity() {
        return obligationQuantity;
    }

    public void setObligationQuantity(double obligationQuantity) {
        this.obligationQuantity = obligationQuantity;
    }

    public double getTotalContractQuantity() {
        return totalContractQuantity;
    }

    public void setTotalContractQuantity(double totalContractQuantity) {
        this.totalContractQuantity = totalContractQuantity;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(String counterparty) {
        this.counterparty = counterparty;
    }

    public String getCommodity() {
        return commodity;
    }

    public void setCommodity(String commodity) {
        this.commodity = commodity;
    }

    public double getFxRate() {
        return fxRate;
    }

    public void setFxRate(double fxRate) {
        this.fxRate = fxRate;
    }

    public String getOriginalInvoice() {
        return originalInvoice;
    }

    public void setOriginalInvoice(String originalInvoice) {
        this.originalInvoice = originalInvoice;
    }
}
