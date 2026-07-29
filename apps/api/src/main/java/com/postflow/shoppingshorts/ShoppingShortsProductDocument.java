package com.postflow.shoppingshorts;

import java.time.Instant;
import java.util.List;

public record ShoppingShortsProductDocument(
        String productId,
        String productName,
        String brand,
        String category,
        Long price,
        Long originalPrice,
        Integer discountRate,
        List<String> options,
        List<String> features,
        String description,
        String productUrl,
        String affiliateUrl,
        List<String> sourceImages,
        Instant extractedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
