package com.postflow.ai.provider;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.postflow.ai.AiProperties;
import com.postflow.ai.LLMProvider;
import com.postflow.ai.ModelTier;
import com.postflow.ai.dto.GenerationRequest;
import com.postflow.ai.dto.GenerationResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Default {@link LLMProvider} implementation backed by Anthropic Claude.
 *
 * <p>Active when {@code ai.provider=claude} (also the matchIfMissing default). An
 * {@code OpenAIProvider} can be added behind the same interface and selected via
 * {@code ai.provider=openai}.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "claude", matchIfMissing = true)
public class ClaudeProvider implements LLMProvider {

    private static final Map<ModelTier, String> DEFAULT_MODELS = Map.of(
            ModelTier.LIGHT, "claude-haiku-4-5",
            ModelTier.STANDARD, "claude-sonnet-4-6",
            ModelTier.PREMIUM, "claude-opus-4-8"
    );

    private final AiProperties properties;
    private final AnthropicClient client;

    public ClaudeProvider(AiProperties properties) {
        this.properties = properties;
        String apiKey = properties.claude() != null ? properties.claude().apiKey() : null;
        // Placeholder when unset so the client builds at startup (local dev without a key);
        // any real generate() call then fails with 401 rather than blocking boot.
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(StringUtils.hasText(apiKey) ? apiKey : "missing-anthropic-api-key")
                .build();
    }

    @Override
    public String id() {
        return "claude";
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        String model = resolveModel(request.tier());

        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maxTokens())
                .addUserMessage(request.prompt());

        applySystem(params, request);

        Message response = client.messages().create(params.build());

        String text = response.content().stream()
                .map(ContentBlock::text)
                .flatMap(java.util.Optional::stream)
                .map(textBlock -> textBlock.text())
                .reduce("", String::concat);

        return GenerationResult.builder()
                .text(text)
                .provider(id())
                .model(model)
                .inputTokens(response.usage().inputTokens())
                .outputTokens(response.usage().outputTokens())
                .build();
    }

    /**
     * Place the stable system prompt as a cacheable prefix when {@code cacheHint} is set
     * (brand voice / style guide reused across the session → ~90% cheaper on cache reads).
     */
    private void applySystem(MessageCreateParams.Builder params, GenerationRequest request) {
        if (!StringUtils.hasText(request.systemPrompt())) {
            return;
        }
        if (request.cacheHint()) {
            params.systemOfTextBlockParams(List.of(
                    TextBlockParam.builder()
                            .text(request.systemPrompt())
                            .cacheControl(CacheControlEphemeral.builder().build())
                            .build()));
        } else {
            params.system(request.systemPrompt());
        }
    }

    private String resolveModel(ModelTier tier) {
        Map<String, String> configured = properties != null && properties.claude() != null
                ? properties.claude().models()
                : null;
        if (configured != null && configured.containsKey(tier.name())) {
            return configured.get(tier.name());
        }
        return DEFAULT_MODELS.get(tier);
    }
}
