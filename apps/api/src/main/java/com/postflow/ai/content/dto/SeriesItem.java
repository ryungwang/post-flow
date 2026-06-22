package com.postflow.ai.content.dto;

import java.util.List;

public record SeriesItem(
        int day,
        String title,
        String content,
        List<String> hashtags,
        String cta
) {
}
