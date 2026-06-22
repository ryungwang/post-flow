package com.postflow.ai.content.dto;

import java.util.List;

public record SeriesItem(
        int day,
        String title,
        String content,
        List<String> hashtags,
        String cta,
        int score
) {
    /** Items from the model omit score; default 0 until scored. */
    public SeriesItem(int day, String title, String content, List<String> hashtags, String cta) {
        this(day, title, content, hashtags, cta, 0);
    }

    public SeriesItem withScore(int score) {
        return new SeriesItem(day, title, content, hashtags, cta, score);
    }
}
