package com.postflow.ai.content.dto;

import java.util.List;

public record GenerateContentResponse(
        List<GeneratedCard> cards,
        String provider,
        String model
) {
}
