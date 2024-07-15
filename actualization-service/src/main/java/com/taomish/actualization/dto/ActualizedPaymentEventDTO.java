package com.taomish.actualization.dto;

import java.time.LocalDateTime;

public class ActualizedPaymentEventDTO {
    private String eventType;
    private LocalDateTime date;

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }
}
