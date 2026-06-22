package com.postflow.ai.provider;

import com.postflow.ai.AiProperties;
import com.postflow.ai.LLMProvider;
import com.postflow.ai.ModelTier;
import com.postflow.ai.dto.GenerationRequest;
import com.postflow.ai.dto.GenerationResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Default {@link LLMProvider} implementation backed by Anthropic Claude.
 *
 * <p>Active when {@code ai.provider=claude} (also the matchIfMissing default). An
 * {@code OpenAIProvider} can be added behind the same interface and selected via
 * {@code ai.provider=openai}.
 *
 * <p>STUB: the actual Anthropic SDK call is wired in a follow-up. Tier→model resolution,
 * config, and the contract are in place so feature code can compile against the interface.
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

    public ClaudeProvider(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return "claude";
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        String model = resolveModel(request.tier());
        // TODO: call Anthropic Java SDK (com.anthropic) — messages.create with
        //  system prefix (cacheHint -> cache_control), output_config.format for schema,
        //  max_tokens, adaptive thinking. Map usage to GenerationResult.
        throw new UnsupportedOperationException(
                "ClaudeProvider.generate not yet wired to Anthropic SDK (model=" + model + ")");
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
