package com.postflow.ai.content;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postflow.ai.LLMProvider;
import com.postflow.ai.ModelTier;
import com.postflow.ai.content.dto.GenerateAffiliateRequest;
import com.postflow.ai.content.dto.GenerateAffiliateResponse;
import com.postflow.ai.content.dto.GenerateContentRequest;
import com.postflow.ai.content.dto.GenerateContentResponse;
import com.postflow.ai.content.dto.GenerateSeriesResponse;
import com.postflow.ai.content.dto.GeneratedCard;
import com.postflow.ai.content.dto.SeriesItem;
import com.postflow.ai.dto.GenerationRequest;
import com.postflow.ai.dto.GenerationResult;
import com.postflow.aigeneration.AiGeneration;
import com.postflow.aigeneration.AiGenerationRepository;
import com.postflow.user.UsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Orchestrates content generation: build prompts → call the active {@link LLMProvider}
 * → parse JSON cards → persist an audit record. Vendor-agnostic (depends only on the
 * LLMProvider abstraction + ModelTier).
 */
@Service
public class ContentGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ContentGenerationService.class);
    private static final int MAX_OUTPUT_TOKENS = 16000;

    private final LLMProvider llmProvider;
    private final ContentPromptBuilder promptBuilder;
    private final AiGenerationRepository aiGenerationRepository;
    private final ObjectMapper objectMapper;
    private final com.fasterxml.jackson.databind.ObjectReader cardsReader;
    private final UsageService usageService;
    private final com.postflow.brand.BrandRepository brandRepository;
    private final com.postflow.threads.SocialAccountService socialAccountService;

    public ContentGenerationService(LLMProvider llmProvider,
                                    ContentPromptBuilder promptBuilder,
                                    AiGenerationRepository aiGenerationRepository,
                                    ObjectMapper objectMapper,
                                    UsageService usageService,
                                    com.postflow.brand.BrandRepository brandRepository,
                                    com.postflow.threads.SocialAccountService socialAccountService) {
        this.llmProvider = llmProvider;
        this.promptBuilder = promptBuilder;
        this.aiGenerationRepository = aiGenerationRepository;
        this.objectMapper = objectMapper;
        // 모델이 멀티라인 본문을 낼 때 \n 대신 '진짜 줄바꿈'을 JSON 문자열에 넣어 파싱이 깨지는 일이 잦다.
        // 문자열 내 제어문자·후행 콤마를 허용하는 관대한 리더로 그 흔한 실패를 흡수한다.
        this.cardsReader = objectMapper.copy()
                .configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
                .configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true)
                .readerFor(new TypeReference<List<GeneratedCard>>() {});
        this.usageService = usageService;
        this.brandRepository = brandRepository;
        this.socialAccountService = socialAccountService;
    }

    @Transactional
    public GenerateContentResponse generate(Long userId, GenerateContentRequest request) {
        usageService.assertCanGenerate(userId);
        PlatformContentProfile profile = PlatformContentProfile.fromRequest(request.platform());
        String systemPrompt = promptBuilder.systemPrompt(profile);
        // 트렌드 반영: 키워드가 있으면 지금 뜨는 실제 게시물을 검색해 프롬프트에 주입(권한 없으면 조용히 스킵).
        String trendBlock = null;
        String kw = request.trendKeywordOrNull();
        if (kw != null) {
            List<String> trends = socialAccountService.trendTexts(userId, kw, 8);
            trendBlock = promptBuilder.trendBlock(kw, trends);
        }
        String userPrompt = promptBuilder.userPrompt(request, brandContext(userId, request.brandId()), trendBlock);

        GenerationRequest llmRequest = GenerationRequest.builder()
                .systemPrompt(systemPrompt)
                .prompt(userPrompt)
                .maxTokens(estimateMaxTokens(request.quantity()))
                .tier(ModelTier.STANDARD)
                .cacheHint(true)
                .build();

        GenerationResult result = llmProvider.generate(llmRequest);

        List<GeneratedCard> cards = parseCards(result.text(), profile);

        aiGenerationRepository.save(AiGeneration.record(
                userId,
                result.provider(),
                result.model(),
                userPrompt,
                result.text(),
                result.inputTokens(),
                result.outputTokens()));

        return new GenerateContentResponse(cards, result.provider(), result.model());
    }

    /** 대가성 고지문(쿠팡파트너스 필수 표기 — 공정위 추천·보증 지침·쿠팡 규정). 절대 잘리면 안 된다. */
    private static final String COUPANG_DISCLOSURE =
            "이 포스팅은 쿠팡 파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.";

    /**
     * 제휴(쿠팡파트너스) 콘텐츠 생성. 본문은 모델이 쓰되, <b>링크·subId·대가성 고지문은 서버가</b>
     * 각 카드에 결정적으로 덧붙인다(고지 누락 = 계정 정지·과징금 리스크라 모델에 맡기지 않는다).
     * Instagram처럼 본문 링크가 클릭되지 않는 플랫폼은 링크를 본문에 넣지 않고 subId 링크를 응답으로
     * 돌려줘 사용자가 프로필(bio) 링크로 쓰게 한다.
     */
    @Transactional
    public GenerateAffiliateResponse generateAffiliate(Long userId, GenerateAffiliateRequest req) {
        usageService.assertCanGenerate(userId);
        if (req.isBlog()) {
            return generateAffiliateBlog(userId, req);
        }
        PlatformContentProfile profile = PlatformContentProfile.fromRequest(req.platform());
        String subId = buildSubId(req.subIdPrefix(), profile.provider().name());
        String linkWithSub = appendSubId(req.affiliateLink(), subId);
        // 댓글 모드(고지=첫댓글 + 지원 플랫폼): 링크·고지문 둘 다 본문에서 빼고 첫 댓글로 → 본문은 글만 깨끗하게.
        boolean commentMode = req.disclosureAsComment() && supportsFirstComment(profile.provider());
        boolean linkInBody = !profile.imageCentric() && !commentMode; // IG=프로필 링크 / 댓글 모드=링크도 댓글로
        boolean disclosureInBody = !commentMode;
        String firstComment = commentMode ? ("👉 " + linkWithSub + "\n\n" + COUPANG_DISCLOSURE) : null;

        String suffix = affiliateSuffix(linkInBody, linkWithSub, disclosureInBody);
        int bodyBudget = Math.max(80, profile.maxChars() - codePoints(suffix));

        String systemPrompt = promptBuilder.systemPrompt(profile);
        String userPrompt = promptBuilder.affiliateUserPrompt(req, bodyBudget, commentMode);

        GenerationRequest llmRequest = GenerationRequest.builder()
                .systemPrompt(systemPrompt)
                .prompt(userPrompt)
                .maxTokens(estimateMaxTokens(req.quantity()))
                .tier(ModelTier.PREMIUM) // 제휴=전환 직결 → 카피 품질 위해 Opus(bulk 워크호스보다 상향)
                .cacheHint(true)
                .build();

        GenerationResult result = llmProvider.generate(llmRequest);

        // 모델이 본문에 넣은 고지/제휴 안내 줄은 표현 불문 항상 제거 — 고지문은 우리가 붙이는 정본만 쓴다
        // (본문 모드=하단에 정식 고지문 append, 댓글 모드=본문엔 없고 첫 댓글로).
        List<GeneratedCard> cards = parseCards(result.text(), profile).stream()
                .map(ContentGenerationService::stripDisclosureLines)
                .map(c -> decorateAffiliate(c, profile, suffix))
                .toList();

        aiGenerationRepository.save(AiGeneration.record(
                userId, result.provider(), result.model(),
                userPrompt, result.text(), result.inputTokens(), result.outputTokens()));

        return new GenerateAffiliateResponse(
                cards, subId, linkWithSub, linkInBody, COUPANG_DISCLOSURE, disclosureInBody, firstComment,
                result.provider(), result.model());
    }

    /** 모델이 본문에 넣은 고지/제휴 안내 줄 제거 — 쿠팡 파트너스·수수료·제휴 링크 안내 등 표현 불문. */
    private static GeneratedCard stripDisclosureLines(GeneratedCard c) {
        String content = c.content();
        if (content == null || content.isBlank()) {
            return c;
        }
        String cleaned = java.util.Arrays.stream(content.split("\n"))
                .filter(line -> !isAffiliateNoticeLine(line))
                .reduce((a, b) -> a + "\n" + b).orElse("")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
        return new GeneratedCard(cleaned, c.hashtags(), c.cta(), c.score());
    }

    /** 모델이 본문에 넣은 대가성 고지·제휴 안내 줄인지 — 표현이 달라도 잡도록 여러 패턴 매칭. */
    private static boolean isAffiliateNoticeLine(String line) {
        return line.contains("쿠팡 파트너스")
                || line.contains("일정액")
                || (line.contains("수수료") && line.contains("제공"))
                || (line.contains("제휴") && line.contains("링크") && (line.contains("포함") || line.contains("참고")))
                || (line.contains("광고") && line.contains("포함") && line.contains("게시물"));
    }

    /** 첫 댓글(자기 게시물 댓글)을 지원하는 플랫폼 — 이 플랫폼만 고지문을 댓글로 뺄 수 있다. */
    private static boolean supportsFirstComment(com.postflow.social.SocialProvider p) {
        return p == com.postflow.social.SocialProvider.THREADS
                || p == com.postflow.social.SocialProvider.FACEBOOK
                || p == com.postflow.social.SocialProvider.INSTAGRAM
                || p == com.postflow.social.SocialProvider.MASTODON
                || p == com.postflow.social.SocialProvider.BLUESKY;
    }

    /**
     * 제휴 <b>블로그</b> 생성 — 긴 리뷰 글 + (쿠팡 HTML 배너 상단 삽입 | 링크) + 대가성 고지문(하단).
     * 블로그는 검색 유입용이라 별도 프로필(길고 SEO). 쿠팡 HTML이 있으면 그 배너(상품·카테고리·프로모션
     * 어느 것이든)를 글 맨 위에 넣고, 없으면 subId 링크를 하단에 넣는다. 고지문은 항상 하단에 강제.
     */
    private GenerateAffiliateResponse generateAffiliateBlog(Long userId, GenerateAffiliateRequest req) {
        PlatformContentProfile profile = PlatformContentProfile.blog();
        String subId = buildSubId(req.subIdPrefix(), "blog");
        String blogHtml = req.blogHtmlOrNull();
        String linkWithSub = blogHtml == null ? appendSubId(req.affiliateLink(), subId) : null;

        String prefix = blogHtml != null ? blogHtml + "\n\n" : "";
        String suffix = "\n\n"
                + (blogHtml == null && linkWithSub != null ? "👉 " + linkWithSub + "\n\n" : "")
                + COUPANG_DISCLOSURE;
        int reserved = codePoints(prefix) + codePoints(suffix);
        int bodyBudget = Math.max(200, profile.maxChars() - reserved);

        String systemPrompt = promptBuilder.systemPrompt(profile);
        String userPrompt = promptBuilder.affiliateBlogUserPrompt(req, bodyBudget);

        GenerationRequest llmRequest = GenerationRequest.builder()
                .systemPrompt(systemPrompt)
                .prompt(userPrompt)
                .maxTokens(estimateMaxTokens(req.quantity()))
                .tier(ModelTier.STANDARD)
                .cacheHint(true)
                .build();

        GenerationResult result = llmProvider.generate(llmRequest);

        List<GeneratedCard> cards = parseCards(result.text(), profile).stream()
                .map(ContentGenerationService::stripDisclosureLines)
                .map(c -> {
                    String body = clampCodePoints(c.content(), bodyBudget);
                    String content = prefix + body + suffix;
                    int score = ContentScorer.score(content, c.hashtags(), c.cta(), profile);
                    return new GeneratedCard(content, c.hashtags(), c.cta(), score);
                })
                .toList();

        aiGenerationRepository.save(AiGeneration.record(
                userId, result.provider(), result.model(),
                userPrompt, result.text(), result.inputTokens(), result.outputTokens()));

        // 블로그는 첫 댓글 개념이 없어 고지문은 항상 본문(하단)에 둔다.
        return new GenerateAffiliateResponse(
                cards, subId, linkWithSub, true, COUPANG_DISCLOSURE, true, null, result.provider(), result.model());
    }

    /** 본문 끝에 붙일 링크(선택) + (선택) 대가성 고지문 블록. 댓글 모드면 고지문 제외. */
    private static String affiliateSuffix(boolean linkInBody, String linkWithSub, boolean includeDisclosure) {
        StringBuilder s = new StringBuilder();
        if (linkInBody && linkWithSub != null) {
            s.append("\n\n👉 ").append(linkWithSub);
        }
        if (includeDisclosure) {
            s.append("\n\n").append(COUPANG_DISCLOSURE);
        }
        return s.toString();
    }

    /** 모델 본문을 (링크·고지문 자리 확보 후) 잘라 붙이고 재채점. 고지문은 절대 잘리지 않는다. */
    private GeneratedCard decorateAffiliate(GeneratedCard card, PlatformContentProfile profile, String suffix) {
        int budget = Math.max(0, profile.maxChars() - codePoints(suffix));
        String body = clampCodePoints(card.content(), budget);
        String content = body + suffix;
        int score = ContentScorer.score(content, card.hashtags(), card.cta(), profile);
        return new GeneratedCard(content, card.hashtags(), card.cta(), score);
    }

    /** subId 값 만들기: prefix_platform(비면 platform). 쿠팡 subId 안전 문자만, 40자 제한. */
    private static String buildSubId(String prefix, String providerName) {
        String platform = providerName.toLowerCase();
        String base = (prefix == null || prefix.isBlank()) ? platform : prefix.trim() + "_" + platform;
        String safe = base.replaceAll("[^a-zA-Z0-9_]", "");
        return safe.length() > 40 ? safe.substring(0, 40) : safe;
    }

    /** 제휴 링크에 subId 쿼리 파라미터를 붙인다(기존 쿼리 유무에 따라 ?/&). */
    private static String appendSubId(String link, String subId) {
        String l = link.trim();
        String sep = l.contains("?") ? "&" : "?";
        return l + sep + "subId=" + URLEncoder.encode(subId, StandardCharsets.UTF_8);
    }

    private static int codePoints(String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    /** 코드포인트 기준으로 앞에서 maxCp 만큼만 남긴다. */
    private static String clampCodePoints(String s, int maxCp) {
        if (s == null) {
            return "";
        }
        int cp = s.codePointCount(0, s.length());
        if (cp <= maxCp) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, maxCp));
    }

    @Transactional
    public GenerateSeriesResponse generateSeries(Long userId, String topic, int days, String goal,
                                                 Long brandId, String platform) {
        usageService.assertCanSeries(userId);
        usageService.assertCanGenerate(userId);
        PlatformContentProfile profile = PlatformContentProfile.fromRequest(platform);
        String systemPrompt = promptBuilder.seriesSystemPrompt(profile);
        String userPrompt = promptBuilder.seriesUserPrompt(topic, days, goal, brandContext(userId, brandId));

        GenerationRequest llmRequest = GenerationRequest.builder()
                .systemPrompt(systemPrompt)
                .prompt(userPrompt)
                .maxTokens(estimateSeriesTokens(days))
                .tier(ModelTier.PREMIUM) // series planning → Opus (PRD)
                .cacheHint(true)
                .build();

        GenerationResult result = llmProvider.generate(llmRequest);

        List<SeriesItem> items = parseSeries(result.text(), profile);

        aiGenerationRepository.save(AiGeneration.record(
                userId,
                result.provider(),
                result.model(),
                userPrompt,
                result.text(),
                result.inputTokens(),
                result.outputTokens()));

        return new GenerateSeriesResponse(items, result.provider(), result.model());
    }

    private List<SeriesItem> parseSeries(String raw, PlatformContentProfile profile) {
        String json = extractJsonArray(raw);
        try {
            List<SeriesItem> items = objectMapper.readValue(json, new TypeReference<>() {});
            return items.stream()
                    .map(it -> {
                        String clamped = profile.clamp(it.content());
                        return clamped == it.content()
                                ? it
                                : new SeriesItem(it.day(), it.title(), clamped, it.hashtags(), it.cta());
                    })
                    .map(it -> it.withScore(ContentScorer.score(it.content(), it.hashtags(), it.cta(), profile)))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new ContentGenerationException("Failed to parse series as JSON", e);
        }
    }

    /** Brand promotion context for a chosen product (owned by user), or empty string. */
    private String brandContext(Long userId, Long brandId) {
        if (brandId == null) {
            return "";
        }
        return brandRepository.findByIdAndUserId(brandId, userId)
                .map(b -> promptBuilder.brandBlock(b.getName(), b.getDescription(), b.getAudience(),
                        b.getKeyPoints(), b.getCtaText(), b.getUrl()))
                .orElse("");
    }

    private int estimateMaxTokens(int quantity) {
        // 한국어 리치 멀티라인 글은 카드당 700~1000토큰까지 나온다. 예산이 빠듯하면 JSON이 잘려
        // parseCards가 실패하므로(글생성 오류) 카드당 넉넉히 잡는다.
        return Math.min(MAX_OUTPUT_TOKENS, 800 + quantity * 900);
    }

    /** Series items are larger (title + rich multi-line post per day) → wider budget to avoid truncation. */
    private int estimateSeriesTokens(int days) {
        return Math.min(MAX_OUTPUT_TOKENS, 1000 + days * 700);
    }

    private List<GeneratedCard> parseCards(String raw, PlatformContentProfile profile) {
        String json = extractJsonArray(raw);
        try {
            List<GeneratedCard> cards = cardsReader.readValue(json);
            return cards.stream()
                    .map(c -> clampContent(c, profile))
                    .map(c -> c.withScore(ContentScorer.score(c.content(), c.hashtags(), c.cta(), profile)))
                    .sorted(java.util.Comparator.comparingInt(GeneratedCard::score).reversed())
                    .toList();
        } catch (JsonProcessingException e) {
            // 원본을 남겨 진단 가능하게. 대개 maxTokens 초과로 배열이 잘려 파싱 실패(끝에 ']' 없음).
            boolean looksTruncated = !json.stripTrailing().endsWith("]");
            log.warn("카드 JSON 파싱 실패(truncated={}): {} | rawTail={}", looksTruncated, e.getOriginalMessage(),
                    raw.length() > 240 ? "…" + raw.substring(raw.length() - 240) : raw);
            throw new ContentGenerationException("Failed to parse generated cards as JSON", e);
        }
    }

    /** Defensive per-platform char guard (code-point aware) in case the model overruns. */
    private GeneratedCard clampContent(GeneratedCard card, PlatformContentProfile profile) {
        String clamped = profile.clamp(card.content());
        return clamped == card.content()
                ? card
                : new GeneratedCard(clamped, card.hashtags(), card.cta());
    }

    /** Strip markdown fences / prose and isolate the JSON array. */
    private String extractJsonArray(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ContentGenerationException("Empty generation result", null);
        }
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new ContentGenerationException("No JSON array found in generation result", null);
        }
        return raw.substring(start, end + 1);
    }
}
