package com.postflow.ai.content;

import com.postflow.ai.content.dto.GenerateContentRequest;
import org.springframework.stereotype.Component;

/**
 * Builds the system/user prompts for content generation.
 *
 * <p>The system prompt is stable (brand voice + format rules) so it can be cached as a
 * prefix across a generation session. The user prompt carries the per-request variables
 * (topic / goal / tone / quantity).
 */
@Component
public class ContentPromptBuilder {

    /** Stable, cacheable prefix. */
    public String systemPrompt() {
        return """
                You are a top-tier social media copywriter for Threads. Write substantial,
                scroll-stopping posts that people actually save and share — never a single
                thin sentence.

                Structure each post's "content" with line breaks (\\n):
                1. Hook — a bold, curiosity-driving first line.
                2. Body — 2 to 4 short lines delivering concrete, specific value
                   (numbered points, examples, or numbers). No vague filler.
                3. Insight — one punchy takeaway line.
                4. Question — an engaging question to drive replies.

                Hard rules:
                - "content" MUST be a rich multi-line post, ideally 250-480 characters,
                  and MUST be 500 characters or fewer (Threads limit).
                - Write everything (content, cta, hashtags) in natural KOREAN (한국어) by default.
                  Only use another language if the topic is itself clearly written in that language.
                  Use natural, human Korean — not translated-sounding.
                - Tasteful emoji allowed (0-3), never spammy.
                - The CTA goes in the separate "cta" field, NOT inside content.
                - Hashtags: 3-5, relevant, no '#', no spaces.

                Output format:
                - Return ONLY a JSON array, no prose, no markdown fences.
                - Each element: {"content": string, "hashtags": string[], "cta": string}.
                - Each "content" must differ meaningfully in angle and hook.
                """;
    }

    /** Per-request user prompt. {@code brandContext} may be empty (no product selected). */
    public String userPrompt(GenerateContentRequest request, String brandContext) {
        return """
                Topic: %s
                Goal: %s
                Tone: %s
                %s
                Generate %d distinct posts as a JSON array.
                """.formatted(
                request.topic(),
                request.goalOrDefault(),
                request.toneOrDefault(),
                brandContext == null ? "" : brandContext,
                request.quantity());
    }

    /** Build a promotion-context block so posts naturally promote the user's product. */
    public String brandBlock(String name, String description, String audience,
                             String keyPoints, String ctaText, String url) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nPROMOTION CONTEXT — every post must naturally promote this product (소프트한 홍보, 광고티 X):\n");
        sb.append("- 제품/서비스: ").append(name);
        if (description != null && !description.isBlank()) sb.append(" — ").append(description);
        sb.append("\n");
        if (audience != null && !audience.isBlank()) sb.append("- 타깃 고객: ").append(audience).append("\n");
        if (keyPoints != null && !keyPoints.isBlank()) sb.append("- 핵심 강점: ").append(keyPoints).append("\n");
        if (ctaText != null && !ctaText.isBlank()) sb.append("- 선호 CTA: ").append(ctaText).append(" (cta 필드에 자연스럽게 반영)\n");
        if (url != null && !url.isBlank()) sb.append("- 링크: ").append(url).append(" (cta에 녹이되 본문엔 raw URL 넣지 말 것)\n");
        sb.append("주제는 이 제품의 가치를 보여주는 각도로 풀고, 가치를 먼저 준 뒤 자연스럽게 제품으로 연결하세요.\n");
        return sb.toString();
    }

    /** Stable, cacheable prefix for a multi-day content series. */
    public String seriesSystemPrompt() {
        return """
                You are an expert content strategist for Threads.
                Design a coherent multi-day content series that builds on itself day by day,
                progressing from hook/awareness to depth to action.

                Hard rules:
                - Write everything (title, content, cta, hashtags) in natural KOREAN (한국어) by default,
                  unless the topic is itself clearly in another language. Natural, human Korean.
                - Each day's "content" is a rich multi-line post (hook → 2-4 value lines →
                  insight → question), ideally 250-480 chars, and ≤500 chars (Threads limit).
                - Use line breaks (\\n); be specific, human, not a single thin sentence.
                - Each day has a short punchy title and the full post body.
                - Hashtags: 3-5 per day, relevant, no '#', no spaces.

                Output format:
                - Return ONLY a JSON array, no prose, no markdown fences.
                - Exactly one element per day, in order.
                - Each element: {"day": number, "title": string, "content": string, "hashtags": string[], "cta": string}.
                """;
    }

    public String seriesUserPrompt(String topic, int days) {
        return """
                Topic: %s
                Build a %d-day content series as a JSON array (day 1..%d).
                """.formatted(topic, days, days);
    }
}
