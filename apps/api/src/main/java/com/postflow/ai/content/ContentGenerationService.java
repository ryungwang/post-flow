package com.postflow.ai.content;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postflow.ai.LLMProvider;
import com.postflow.ai.ModelTier;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestrates content generation: build prompts → call the active {@link LLMProvider}
 * → parse JSON cards → persist an audit record. Vendor-agnostic (depends only on the
 * LLMProvider abstraction + ModelTier).
 */
@Service
public class ContentGenerationService {

    private static final int THREADS_MAX_CHARS = 500;
    private static final int MAX_OUTPUT_TOKENS = 16000;

    private final LLMProvider llmProvider;
    private final ContentPromptBuilder promptBuilder;
    private final AiGenerationRepository aiGenerationRepository;
    private final ObjectMapper objectMapper;
    private final UsageService usageService;
    private final com.postflow.brand.BrandRepository brandRepository;

    public ContentGenerationService(LLMProvider llmProvider,
                                    ContentPromptBuilder promptBuilder,
                                    AiGenerationRepository aiGenerationRepository,
                                    ObjectMapper objectMapper,
                                    UsageService usageService,
                                    com.postflow.brand.BrandRepository brandRepository) {
        this.llmProvider = llmProvider;
        this.promptBuilder = promptBuilder;
        this.aiGenerationRepository = aiGenerationRepository;
        this.objectMapper = objectMapper;
        this.usageService = usageService;
        this.brandRepository = brandRepository;
    }

    @Transactional
    public GenerateContentResponse generate(Long userId, GenerateContentRequest request) {
        usageService.assertCanGenerate(userId);
        String systemPrompt = promptBuilder.systemPrompt();
        String userPrompt = promptBuilder.userPrompt(request, brandContext(userId, request.brandId()));

        GenerationRequest llmRequest = GenerationRequest.builder()
                .systemPrompt(systemPrompt)
                .prompt(userPrompt)
                .maxTokens(estimateMaxTokens(request.quantity()))
                .tier(ModelTier.STANDARD)
                .cacheHint(true)
                .build();

        GenerationResult result = llmProvider.generate(llmRequest);

        List<GeneratedCard> cards = parseCards(result.text());

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

    @Transactional
    public GenerateSeriesResponse generateSeries(Long userId, String topic, int days, String goal, Long brandId) {
        usageService.assertCanSeries(userId);
        usageService.assertCanGenerate(userId);
        String systemPrompt = promptBuilder.seriesSystemPrompt();
        String userPrompt = promptBuilder.seriesUserPrompt(topic, days, goal, brandContext(userId, brandId));

        GenerationRequest llmRequest = GenerationRequest.builder()
                .systemPrompt(systemPrompt)
                .prompt(userPrompt)
                .maxTokens(estimateSeriesTokens(days))
                .tier(ModelTier.PREMIUM) // series planning → Opus (PRD)
                .cacheHint(true)
                .build();

        GenerationResult result = llmProvider.generate(llmRequest);

        List<SeriesItem> items = parseSeries(result.text());

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

    private List<SeriesItem> parseSeries(String raw) {
        String json = extractJsonArray(raw);
        try {
            List<SeriesItem> items = objectMapper.readValue(json, new TypeReference<>() {});
            return items.stream()
                    .map(it -> it.content() != null && it.content().length() > THREADS_MAX_CHARS
                            ? new SeriesItem(it.day(), it.title(),
                                    it.content().substring(0, THREADS_MAX_CHARS), it.hashtags(), it.cta())
                            : it)
                    .map(it -> it.withScore(ContentScorer.score(it.content(), it.hashtags(), it.cta())))
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
        return Math.min(MAX_OUTPUT_TOKENS, 500 + quantity * 300);
    }

    /** Series items are larger (title + rich multi-line post per day) → wider budget to avoid truncation. */
    private int estimateSeriesTokens(int days) {
        return Math.min(MAX_OUTPUT_TOKENS, 1000 + days * 700);
    }

    private List<GeneratedCard> parseCards(String raw) {
        String json = extractJsonArray(raw);
        try {
            List<GeneratedCard> cards = objectMapper.readValue(json, new TypeReference<>() {});
            return cards.stream()
                    .map(this::clampContent)
                    .map(c -> c.withScore(ContentScorer.score(c.content(), c.hashtags(), c.cta())))
                    .sorted(java.util.Comparator.comparingInt(GeneratedCard::score).reversed())
                    .toList();
        } catch (JsonProcessingException e) {
            throw new ContentGenerationException("Failed to parse generated cards as JSON", e);
        }
    }

    /** Defensive ≤500-char guard in case the model overruns the instruction. */
    private GeneratedCard clampContent(GeneratedCard card) {
        if (card.content() != null && card.content().length() > THREADS_MAX_CHARS) {
            return new GeneratedCard(
                    card.content().substring(0, THREADS_MAX_CHARS),
                    card.hashtags(),
                    card.cta());
        }
        return card;
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
