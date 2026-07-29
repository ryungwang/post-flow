package com.postflow.shoppingshorts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postflow.ai.LLMProvider;
import com.postflow.ai.ModelTier;
import com.postflow.ai.dto.GenerationRequest;
import com.postflow.ai.dto.GenerationResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "shopping-shorts.storyboard.provider", havingValue = "claude")
public class ClaudeShoppingShortsStoryboardProvider implements ShoppingShortsStoryboardProvider {
    private static final int MAX_OUTPUT_TOKENS = 7000;
    private static final String SYSTEM_PROMPT_PATH = "shopping-shorts/prompts/storyboard-system.md";
    private static final String USER_PROMPT_PATH = "shopping-shorts/prompts/storyboard-generation.md";
    private static final String SCHEMA_PATH = "shopping-shorts/prompts/storyboard-generation-schema.json";

    private final LLMProvider llmProvider;
    private final ObjectMapper objectMapper;
    private final ShoppingShortsCostSafety costSafety;
    private final String systemPrompt;
    private final String userPromptTemplate;
    private final String outputSchema;

    public ClaudeShoppingShortsStoryboardProvider(
            LLMProvider llmProvider,
            ObjectMapper objectMapper,
            ShoppingShortsCostSafety costSafety) {
        this.llmProvider = llmProvider;
        this.objectMapper = objectMapper;
        this.costSafety = costSafety;
        this.systemPrompt = readResource(SYSTEM_PROMPT_PATH);
        this.userPromptTemplate = readResource(USER_PROMPT_PATH);
        this.outputSchema = readResource(SCHEMA_PATH);
    }

    @Override
    public String id() {
        return "claude";
    }

    @Override
    public StoryboardResult generate(ShoppingShortsProductDocument product, ShoppingShortsDtos.CampaignCandidate campaign) {
        costSafety.requireLiveApiEnabled(id(), "storyboard-generation");
        GenerationRequest request = GenerationRequest.builder()
                .systemPrompt(systemPrompt)
                .prompt(userPrompt(product, campaign))
                .outputSchema(outputSchema)
                .maxTokens(MAX_OUTPUT_TOKENS)
                .tier(ModelTier.STANDARD)
                .cacheHint(true)
                .build();
        GenerationResult result = llmProvider.generate(request);
        ShoppingShortsDtos.StoryboardResponse response = parse(product.productId(), campaign.campaignId(), result.text());
        return new StoryboardResult(response, result.provider(), result.model(), result.inputTokens(), result.outputTokens());
    }

    private ShoppingShortsDtos.StoryboardResponse parse(String productId, String campaignId, String rawText) {
        String json = extractJson(rawText);
        try {
            ShoppingShortsDtos.StoryboardResponse parsed =
                    objectMapper.readValue(json, ShoppingShortsDtos.StoryboardResponse.class);
            if (!productId.equals(parsed.productId())) {
                throw new IllegalArgumentException("Claude 스토리보드 응답의 productId가 요청 상품과 다릅니다.");
            }
            if (!campaignId.equals(parsed.campaignId())) {
                throw new IllegalArgumentException("Claude 스토리보드 응답의 campaignId가 요청 기획안과 다릅니다.");
            }
            parsed = ShoppingShortsTextSanitizer.sanitize(parsed);
            if (parsed.scenes() == null || parsed.scenes().size() < 4 || parsed.scenes().size() > 7) {
                throw new IllegalArgumentException("Claude 스토리보드는 4~7개 Scene이어야 합니다.");
            }
            long aiScenes = parsed.scenes().stream().filter(ShoppingShortsDtos.StoryboardScene::requiresAiGeneration).count();
            if (aiScenes > 2) {
                throw new IllegalArgumentException("AI 영상 Scene은 최대 2개까지만 허용됩니다.");
            }
            long reusableScenes = parsed.scenes().stream()
                    .filter(scene -> !scene.requiresAiGeneration())
                    .filter(scene -> "ORIGINAL_IMAGE".equals(scene.sourceType()) || "COMPOSITE".equals(scene.sourceType()))
                    .count();
            if (parsed.scenes().size() >= 5 && reusableScenes < 3) {
                throw new IllegalArgumentException("원본 이미지 또는 합성 Scene이 최소 3개 필요합니다.");
            }
            for (ShoppingShortsDtos.StoryboardScene scene : parsed.scenes()) {
                if (scene.requiresAiGeneration()) {
                    String prompt = scene.klingPrompt() == null ? "" : scene.klingPrompt().toLowerCase();
                    if (prompt.contains("no product visible") || prompt.contains("product not visible")) {
                        throw new IllegalArgumentException("AI 영상 Scene은 상품이 보이지 않는 배경 영상으로 만들 수 없습니다.");
                    }
                    if (!prompt.contains("product") && !prompt.contains("fan")) {
                        throw new IllegalArgumentException("AI 영상 Scene 프롬프트에는 상품 중심 묘사가 필요합니다.");
                    }
                }
            }
            return parsed;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Claude 쇼핑쇼츠 스토리보드 JSON 응답을 해석하지 못했어요.", e);
        }
    }

    private String userPrompt(ShoppingShortsProductDocument product, ShoppingShortsDtos.CampaignCandidate campaign) {
        try {
            String productJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(product);
            String campaignJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(campaign);
            return userPromptTemplate
                    .replace("{{PRODUCT_JSON}}", productJson)
                    .replace("{{CAMPAIGN_JSON}}", campaignJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("스토리보드 프롬프트 데이터를 JSON으로 변환하지 못했어요.", e);
        }
    }

    private String extractJson(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String readResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 Claude 스토리보드 프롬프트 파일을 읽지 못했어요: " + path, e);
        }
    }
}
