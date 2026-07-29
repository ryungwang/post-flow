package com.postflow.shoppingshorts;

import java.nio.file.Path;

public interface ShoppingShortsQualityProvider {
    String id();

    QualityResult check(Path campaignDir);

    record QualityResult(
            String provider,
            String status,
            java.util.List<ShoppingShortsDtos.QualityCheckItem> checks,
            String reportPath
    ) {
    }
}
