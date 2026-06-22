package com.postflow.ai.content.dto;

import java.util.List;

/**
 * One generated content card (PRD → Generated Content Card).
 *
 * @param content  본문 (Hook→Body→Insight→Question→CTA 흐름, ≤500자)
 * @param hashtags 해시태그
 * @param cta      행동 유도 문구
 * @param score    관심도 예측 점수 0-100 (서버 계산, 모델 응답엔 없음)
 */
public record GeneratedCard(
        String content,
        List<String> hashtags,
        String cta,
        int score
) {
    /** Cards parsed from the model omit score; default 0 until scored. */
    public GeneratedCard(String content, List<String> hashtags, String cta) {
        this(content, hashtags, cta, 0);
    }

    public GeneratedCard withScore(int score) {
        return new GeneratedCard(content, hashtags, cta, score);
    }
}
