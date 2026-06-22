package com.postflow.ai.content.dto;

import java.util.List;

public record GenerateSeriesResponse(
        List<SeriesItem> items,
        String provider,
        String model
) {
}
