package com.postflow.ai.content;

import com.postflow.ai.content.dto.GenerateAffiliateRequest;
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

    /** Stable, cacheable prefix — tailored to the target platform's algorithm & limits. */
    public String systemPrompt(PlatformContentProfile p) {
        String imageNote = p.imageCentric()
                ? "\n- 이 플랫폼은 이미지가 핵심이라, \"content\"는 이미지에 붙는 캡션이다. 첫 줄에 가장 강한 훅을 둬라."
                : "";
        return """
                You are a top-tier social media copywriter for %s. Write substantial,
                scroll-stopping posts that people actually save and share — never a single
                thin sentence.

                PLATFORM — %s:
                %s

                Structure each post's "content" with line breaks (\\n):
                1. Hook — a bold, curiosity-driving first line.
                2. Body — 2 to 4 short lines delivering concrete, specific value
                   (numbered points, examples, or numbers). No vague filler.
                3. Insight — one punchy takeaway line.
                4. Close — a closing line that fits the GOAL (질문/팔로우 유도/구매·클릭 등).
                   목표에 맞는 마무리를 쓰고, 모든 글을 질문으로 끝내지 말 것.

                Hard rules:
                - "content" MUST be a rich multi-line post, ideally %d-%d characters,
                  and MUST be %d characters or fewer (%s limit). NEVER exceed the limit.
                - Write everything (content, cta, hashtags) in natural KOREAN (한국어) by default.
                  Only use another language if the topic is itself clearly written in that language.
                  Use natural, human Korean — not translated-sounding.
                - Tasteful emoji allowed (0-3), never spammy.
                - The CTA goes in the separate "cta" field, NOT inside content.
                - Hashtags: %d-%d, relevant to this platform, no '#', no spaces.%s

                Output format:
                - Return ONLY a JSON array, no prose, no markdown fences.
                - Each element: {"content": string, "hashtags": string[], "cta": string}.
                - Each "content" must differ meaningfully in angle and hook.
                """.formatted(
                p.displayName(), p.displayName(), p.algorithmGuidance(),
                p.idealMin(), p.idealMax(), p.maxChars(), p.displayName(),
                p.hashtagMin(), p.hashtagMax(), imageNote);
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

    /**
     * 제휴(쿠팡파트너스) 리뷰형 소프트셀 프롬프트. 링크·대가성 고지문·subId는 서버가 코드로 덧붙이므로
     * 모델에겐 본문/CTA에 URL·고지문을 넣지 말라고 지시한다. 스펙·수치·후기·"내돈내산" 날조를 금지해
     * 공정위 추천·보증 지침 위반과 신뢰 훼손을 막는다. {@code bodyBudget}는 링크·고지문 자리를 남긴 본문 상한.
     */
    public String affiliateUserPrompt(GenerateAffiliateRequest req, int bodyBudget, boolean linkInComment) {
        String features = req.featuresOrNull();
        // 특장점은 스토리의 재료다 — 스펙으로 나열·설명하지 말고, 그 강점이 '느껴지는' 장면·감정으로 녹인다.
        String featuresLine = features == null
                ? "특장점 정보 없음 → 상품 카테고리에서 자연스러운 상황·감정만 상상해 쓰고, 없는 스펙·효과는 지어내지 마라."
                : "제품의 특장점(이 강점이 자연스럽게 드러나는 장면·감정으로 녹여라. 스펙처럼 나열·설명하진 마라): " + features;
        // 궁금증의 답(=링크)이 어디 있는지 → 댓글 모드=고정 댓글 / 본문 모드=링크.
        String answerAt = linkInComment
                ? "고정(첫) 댓글에 있어 — 궁금하면 댓글 보게 자연스럽게 흘려."
                : "링크에 있어 — 궁금하면 링크 보게 자연스럽게 흘려.";
        String ctaRule = linkInComment
                ? "- cta 는 반말로 댓글 보게 유도(예: \"링크는 댓글에 박아둠\", \"궁금하면 댓글 ㄱㄱ\", \"댓글에 정리해둠\")."
                : "- cta 는 반말로 링크 보게 유도(예: \"궁금하면 링크 ㄱㄱ\", 이미지 중심 플랫폼이면 \"프로필 링크 확인\").";
        return """
                제품(쿠팡파트너스 제휴 — 아래는 '참고용 맥락'일 뿐, 본문에서 설명하지 마라): %s
                %s

                이건 SNS 제휴 게시물이다. 제품을 소개·설명하면 광고 티가 나서 바로 스킵당한다.
                본문(content)은 '제품 설명'이 아니라, 그 제품의 특장점이 빛나는 순간을 담은 '반말 스토리텔링'이다:
                - 무조건 반말. 친구한테 툭 던지듯 편하게. 존댓말 절대 금지.
                - 특장점을 스펙으로 설명하지 말고, 그 강점이 '느껴지는' 상황·장면·감정으로 보여줘라(설명 말고 보여주기).
                  예) 미스트 분사가 강점이면 → 더운 마당이 순식간에 시원해지는 장면. 스틱형이 강점이면 → 바쁜 아침 툭 챙기는 순간.
                - 제품은 '이런 거 / 이거' 정도로만 흘려라. 제품명·기능·스펙·"왜 좋은지"를 본문에서 대놓고 설명·나열하지 마라.
                - 본문에서 답을 다 주지 마라. 궁금해서 더 알고 싶게만 만들어라 — 답(링크)은 %s

                자극적이되 정직하게:
                - '자극적'은 훅·앵글·감정으로만 낸다. 허위·과장·낚시성 거짓은 절대 금지.
                - 없는 스펙·가격·효과·후기·"내돈내산" 날조 금지. "1위·최저가 보장" 같은 근거 없는 단정 금지.

                형식 규칙:
                - 본문(content)·cta 에 URL·고지문·이미지태그를 넣지 마라. 링크·고지문은 시스템이 붙인다.
                - 각 content 는 %d자 이내. 반말로 여운 있게 끝나게.
                %s

                %d개의 서로 다른 장면·감정의 게시물을 JSON 배열로.
                """.formatted(
                req.productName(), featuresLine,
                answerAt, bodyBudget, ctaRule, req.quantity());
    }

    /**
     * 제휴 <b>블로그</b> 리뷰 글 프롬프트. SNS 카드가 아니라 검색 유입용 긴 리뷰. 링크·쿠팡 HTML 배너·
     * 대가성 고지문은 서버가 코드로 붙이므로 본문엔 URL·이미지태그·고지문을 넣지 말라고 지시한다.
     * 스펙·수치·후기·'내돈내산' 날조 금지(공정위 지침).
     */
    public String affiliateBlogUserPrompt(GenerateAffiliateRequest req, int bodyBudget) {
        String features = req.featuresOrNull();
        String featuresLine = features == null
                ? "실제 확인된 특징 정보가 제공되지 않았다 → 일반적이고 검증 가능한 장점만 담고, 구체 수치·효과는 지어내지 마라."
                : "실제 특징·장점(이 범위 안에서만 근거로 쓰라, 여기 없는 수치·효과는 만들지 마라): " + features;
        return """
                제품(쿠팡파트너스 제휴 블로그 글): %s
                %s
                톤: %s

                검색해서 들어온 사람에게 도움이 되는 정직한 제품 리뷰 블로그 글을 써라:
                1) 검색 의도·고민에 공감하는 도입 → 2) 제품 소개(무엇을·왜) → 3) 실제 장점(구체적으로) + 정직한 한계 한두 줄
                → 4) 추천 대상·사용 팁 → 5) 마무리. 소제목/줄바꿈으로 문단을 나눠 읽기 쉽게.

                엄격 규칙:
                - 확인 안 된 스펙·가격·할인·수치·효과·후기·"내가 써봤다/내돈내산" 날조 금지. "광고 아님" 사칭 금지.
                - 본문(content)·cta 에 URL(링크)·이미지태그(<img>)·고지 문구를 넣지 마라 — 링크·쿠팡 배너·고지문은 시스템이 붙인다.
                - 각 content 는 %d자 이내. 자연스럽게 마무리.
                - cta 는 부담 없는 추천 한 줄.

                %d개의 서로 다른 각도의 글을 JSON 배열로.
                """.formatted(
                req.productName(), featuresLine, req.toneOrDefault(),
                bodyBudget, req.quantity());
    }

    /** 지금 뜨는 실제 게시물 샘플을 프롬프트에 주입 — 알고리즘 타는 훅·포맷·주제를 반영하게. */
    public String trendBlock(String keyword, List<String> trendTexts) {
        // 샘플은 Threads 검색에서 오지만, 훅·리듬·화제성은 플랫폼 무관하게 참고 가치가 있다.
        if (trendTexts == null || trendTexts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\nTREND CONTEXT — '").append(keyword)
                .append("' 키워드로 지금 반응 좋은 실제 게시물 샘플:\n");
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

    /** Stable, cacheable prefix for a multi-day content series — tailored to the platform. */
    public String seriesSystemPrompt(PlatformContentProfile p) {
        return """
                You are an expert content strategist for %s.
                Design a coherent multi-day content series that builds on itself day by day,
                progressing from hook/awareness to depth to action.

                PLATFORM — %s:
                %s

                Hard rules:
                - Write everything (title, content, cta, hashtags) in natural KOREAN (한국어) by default,
                  unless the topic is itself clearly in another language. Natural, human Korean.
                - Each day's "content" is a rich multi-line post (hook → 2-4 value lines →
                  insight → close), ideally %d-%d chars, and ≤%d chars (%s limit). NEVER exceed the limit.
                - Use line breaks (\\n); be specific, human, not a single thin sentence.
                - Each day has a short punchy title and the full post body.
                - Hashtags: %d-%d per day, relevant to this platform, no '#', no spaces.

                Output format:
                - Return ONLY a JSON array, no prose, no markdown fences.
                - Exactly one element per day, in order.
                - Each element: {"day": number, "title": string, "content": string, "hashtags": string[], "cta": string}.
                """.formatted(
                p.displayName(), p.displayName(), p.algorithmGuidance(),
                p.idealMin(), p.idealMax(), p.maxChars(), p.displayName(),
                p.hashtagMin(), p.hashtagMax());
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
