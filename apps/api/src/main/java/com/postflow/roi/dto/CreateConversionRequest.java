package com.postflow.roi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateConversionRequest(
        @NotNull Long postId,
        @NotNull @Positive BigDecimal amount,
        String currency,
        Long leadId,
        Instant occurredAt,
        String note
) {
}
