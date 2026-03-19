package com.virtual.card.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.virtual.card.enums.CardStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardResponse(
        UUID id,
        String cardHolderName,
        BigDecimal balance,
        LocalDate expiry,
        CardStatus cardStatus,
        Instant createdAt,
        Instant updateAt
) {
}