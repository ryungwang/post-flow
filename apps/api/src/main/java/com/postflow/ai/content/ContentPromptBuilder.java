package com.postflow.ai.content;

import com.postflow.ai.content.dto.GenerateContentRequest;
import org.springframework.stereotype.Component;

import java.util.List;

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
                4. Close — a closing line that fits the GOAL (질문/팔로우 유도/구매·클릭 등).
                   목표에 맞는 마무리를 쓰고, 모든 글을 질문으로 끝내지 말 것.

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
        return userPrompt(request, brandContext, null);
    }

    public String userPrompt(GenerateContentRequest request, String brandContext, String trendBlock) {
        return """
                Topic: %s
                Goal: %s
                %s
                Tone: %s
                %s
                %s
                Generate %d distinct posts as a JSON array.
                """.formatted(
                request.topic(),
                request.goalOrDefault(),
                goalGuidance(request.goalOrDefault()),
                request.toneOrDefault(),
                brandContext == null ? "" : brandContext,
                trendBlock == null ? "" : trendBlock,
                request.quantity());
    }

    /** 지금 뜨는 실제 게시물 샘플을 프롬프트에 주입 — 알고리즘 타는 훅·포맷·주제를 반영하게. */
    public String trendBlock(String keyword, List<String> trendTexts) {
        if (trendTexts == null || trendTexts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\nTREND CONTEXT — '").append(keyword)
                .append("' 키워드로 지금 Threads에서 반응 좋은 실제 게시물 샘플:\n");
        int i = 1;
        for (String t : trendTexts) {
            String one = t.replace("\n", " ").trim();
            if (one.length() > 200) {
                one = one.substring(0, 200) + "…";
            }
            sb.append(i++).append(". ").append(one).append('\n');
        }
        sb.append("위 샘플이 지금 이 주제에서 먹히는 훅·포맷·어조·길이감이다. 그대로 베끼지 말고 "
                + "이 트렌드 감각(도입 훅, 리듬, 화제성)을 반영해 더 나은 오리지널 게시물을 만들어라.\n");
        return sb.toString();
    }

    /** Concrete writing instructions per goal so the goal actually shapes the post. */
    private String goalGuidance(String goal) {
        return switch (goal) {
            case "Sales" -> "GOAL GUIDANCE(판매·전환): 구체적 이득/결과를 앞세우고, 가능한 사회적 증거(숫자·사례)와 가벼운 긴급성을 넣어 행동을 끌어내세요. cta는 구매/신청/클릭 같은 명확한 전환 행동으로(애매한 질문 X). 제품 컨텍스트가 있으면 그 제품으로 연결.";
            case "Leads" -> "GOAL GUIDANCE(리드 확보): 무료 자료·체크리스트·템플릿 같은 가치를 미끼로 제시하고, cta는 \"댓글에 키워드\" 또는 \"링크에서 받기\"로 연락처/리드를 남기게 유도.";
            case "Followers" -> "GOAL GUIDANCE(팔로워 증가): 계속 보고 싶은 시리즈성 가치를 암시하고, cta는 \"이런 내용 더 보려면 팔로우\"처럼 팔로우를 직접 유도.";
            case "Awareness" -> "GOAL GUIDANCE(인지도): 한 문장으로 각인되는 메시지와 공유하고 싶은 관점에 집중. cta는 공유/리포스트 유도.";
            case "Personal Branding" -> "GOAL GUIDANCE(퍼스널 브랜딩): 본인 경험·관점·전문성이 드러나는 1인칭 서사로 신뢰를 쌓고, cta는 팔로우/대화 유도.";
            case "Fun" -> "GOAL GUIDANCE(재미·바이럴): 위트·반전·공감 밈 요소로 가볍고 재밌게. cta는 태그/공유 유도.";
            default -> "GOAL GUIDANCE(참여 유도): 공감 또는 가벼운 논쟁 포인트로 댓글·저장을 유도하고, cta는 답글을 부르는 질문.";
        };
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

    public String seriesUserPrompt(String topic, int days, String goal, String brandContext) {
        return """
                Topic: %s
                Goal: %s
                %s
                %s
                Build a %d-day content series as a JSON array (day 1..%d).
                """.formatted(
                topic,
                goal,
                goalGuidance(goal),
                brandContext == null ? "" : brandContext,
                days, days);
    }
}
