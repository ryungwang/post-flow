package com.postflow.ai.content.dto;

import java.util.List;

/**
 * One generated content card (PRD → Generated Content Card).
 *
 * @param content  본문 (Hook→Body→Insight→Question→CTA 흐름, ≤500자)
 * @param hashtags 해시태그
 * @param cta      행동 유도 문구
 */
public record GeneratedCard(
        String content,
        List<String> hashtags,
        String cta
) {
}
