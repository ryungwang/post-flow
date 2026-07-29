package com.postflow.shoppingshorts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record ShoppingShortsProductCaptureRequest(
        @NotBlank @Size(max = 180) String productName,
        @Size(max = 120) String brand,
        @Size(max = 180) String category,
        @PositiveOrZero Long price,
        @PositiveOrZero Long originalPrice,
        @Min(0) @Max(100)
        Integer discountRate,
        @Size(max = 30)
        List<@Size(max = 160) String> options,
        @Size(max = 30)
        List<@Size(max = 240) String> features,
        @Size(max = 5000) String description,
        @Size(max = 1000) String productUrl,
        @NotBlank @Size(max = 1000) String affiliateUrl,
        @Size(max = 50)
        List<@Size(max = 1000) String> sourceImages,
        Instant extractedAt
) {
}
