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
                You are an expert social media copywriter for Threads.
                Write posts that follow this structure: Hook → Body → Insight → Question → CTA.

                Hard rules:
                - Each post body MUST be 500 characters or fewer (Threads limit).
                - Write in the same language as the topic.
                - Make the hook scroll-stopping; end with an engaging question and a clear CTA.
                - Hashtags: 2-5, relevant, no spaces.

                Output format:
                - Return ONLY a JSON array, no prose, no markdown fences.
                - Each element: {"content": string, "hashtags": string[], "cta": string}.
                - "content" is the full post body (already ≤500 chars).
                """;
    }

    /** Per-request user prompt. */
    public String userPrompt(GenerateContentRequest request) {
        return """
                Topic: %s
                Goal: %s
                Tone: %s
                Generate %d distinct posts as a JSON array.
                """.formatted(
                request.topic(),
                request.goalOrDefault(),
                request.toneOrDefault(),
                request.quantity());
    }

    /** Stable, cacheable prefix for a multi-day content series. */
    public String seriesSystemPrompt() {
        return """
                You are an expert content strategist for Threads.
                Design a coherent multi-day content series that builds on itself day by day,
                progressing from hook/awareness to depth to action.

                Hard rules:
                - Each day's post body MUST be 500 characters or fewer (Threads limit).
                - Write in the same language as the topic.
                - Each day has a short punchy title and a full post body.
                - Hashtags: 2-5 per day, relevant, no spaces.

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
