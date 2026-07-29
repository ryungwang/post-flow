package com.postflow.shoppingshorts;

import java.nio.file.Path;

public interface ShoppingShortsTtsProvider {
    String id();

    TtsResult generate(Path campaignDir);

    record TtsResult(
            String provider,
            String model,
            String status,
            String narrationPath,
            String audioPath,
            String timingPath,
            int segmentCount,
            double duration
    ) {
    }
}
