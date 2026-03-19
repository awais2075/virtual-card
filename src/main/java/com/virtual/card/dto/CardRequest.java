package com.virtual.card.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CardRequest(
        @NotBlank(message = "Cardholder name is required")
        String name,

        @NotNull(message = "Initial balance is required")
        @DecimalMin(value = "0.00", message = "Initial balance cannot be negative")
        BigDecimal initBalance
) {
}
