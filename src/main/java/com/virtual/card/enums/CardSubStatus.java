package com.virtual.card.enums;

public enum CardSubStatus {
    // PENDING sub-states
    PENDING_REVIEW,      // application submitted, under review
    PENDING_APPROVAL,    // reviewed, waiting for approval
    PENDING_ACTIVATION,  // approved, waiting for customer to activate

    // ACTIVE sub-states
    IN_USE,              // normal active usage
    FROZEN,              // temporarily frozen by customer

    // BLOCKED sub-states
    FRAUD_REVIEW,        // under fraud investigation
    LOST,                // reported lost
    STOLEN,              // reported stolen

    // CLOSED sub-states
    CLOSED_BY_USER,      // customer requested closure
    CLOSED_BY_SYSTEM,    // system closed
    CLOSED_LOST,         // closed due to lost report
    CLOSED_STOLEN,       // closed due to stolen report
    REJECTED,            // application rejected

    // EXPIRED sub-states
    EXPIRED_NATURAL,     // expired by scheduler naturally
    EXPIRED_REPLACED     // expired because replaced by new card
}