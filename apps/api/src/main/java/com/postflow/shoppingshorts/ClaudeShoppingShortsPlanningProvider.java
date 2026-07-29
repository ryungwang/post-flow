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
@ConditionalOnProperty(name = "shopping-shorts.planning.provider", havingValue = "claude")
public class ClaudeShoppingShortsPlanningProvider implements ShoppingShortsPlanningProvider {
    private static final int MAX_OUTPUT_TOKENS = 6000;
    private static final String SYSTEM_PROMPT_PATH = "shopping-shorts/prompts/planning-system.md";
    private static final String USER_PROMPT_PATH = "shopping-shorts/prompts/campaign-generation.md";
    private static final String SCHEMA_PATH = "shopping-shorts/prompts/campaign-generation-schema.json";

    private final LLMProvider llmProvider;
    private final ObjectMapper objectMapper;
    private final ShoppingShortsCostSafety costSafety;
    private final String systemPrompt;
    private final String userPromptTemplate;
    private final String outputSchema;

    public ClaudeShoppingShortsPlanningProvider(
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
    public PlanningResult generate(ShoppingShortsProductDocument product) {
        costSafety.requireLiveApiEnabled(id(), "campaign-generation");
        GenerationRequest request = GenerationRequest.builder()
                .systemPrompt(systemPrompt)
                .prompt(userPrompt(product))
                .outputSchema(outputSchema)
                .maxTokens(MAX_OUTPUT_TOKENS)
                .tier(ModelTier.STANDARD)
                .cacheHint(true)
                .build();
        GenerationResult result = llmProvider.generate(request);
        ShoppingShortsDtos.CampaignGenerationResponse response = parse(product.productId(), result.text());
        return new PlanningResult(response, result.provider(), result.model(), result.inputTokens(), result.outputTokens());
    }

    private ShoppingShortsDtos.CampaignGenerationResponse parse(String productId, String rawText) {
        String json = extractJson(rawText);
        try {
            ShoppingShortsDtos.CampaignGenerationResponse parsed =
                    objectMapper.readValue(json, ShoppingShortsDtos.CampaignGenerationResponse.class);
            if (!productId.equals(parsed.productId())) {
                throw new IllegalArgumentException("Claude 응답의 productId가 요청 상품과 다릅니다.");
            }
            if (parsed.campaignCandidates() == null || parsed.campaignCandidates().size() < 3) {
                throw new IllegalArgumentException("Claude 응답에 광고 기획안이 3개 이상 필요합니다.");
            }
            return ShoppingShortsTextSanitizer.sanitize(parsed);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Claude 쇼핑쇼츠 JSON 응답을 해석하지 못했어요.", e);
        }
    }

    private String userPrompt(ShoppingShortsProductDocument product) {
        String productJson;
        try {
            productJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(product);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("상품 데이터를 Claude 프롬프트로 변환하지 못했어요.", e);
        }
        return userPromptTemplate.replace("{{PRODUCT_JSON}}", productJson);
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
            throw new IllegalStateException("쇼핑쇼츠 Claude 프롬프트 파일을 읽지 못했어요: " + path, e);
        }
    }
}
