package com.virtual.card.enums;

public enum CardStatus {
    PENDING,   // application submitted, not yet approved
    ACTIVE,    // card is operational
    BLOCKED,   // temporarily or permanently restricted
    CLOSED,    // permanently closed
    EXPIRED    // expired by scheduler
}