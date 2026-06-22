package com.postflow.post.dto;

import java.util.List;

/** A low-attention-score post with its top improvement tips. */
public record ImprovementDto(
        Long id,
        String content,
        int score,
        String status,
        List<String> tips
) {
}
