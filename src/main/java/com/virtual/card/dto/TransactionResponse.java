package com.virtual.card.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.virtual.card.enums.TransactionStatus;
import com.virtual.card.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(
        UUID id,
        UUID cardId,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant createdAt
) {
}