package com.postflow.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * AI engine config. {@code provider} selects the active {@link LLMProvider}.
 * Per-vendor blocks hold credentials and the tier→model mapping.
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        String provider,
        Claude claude
) {
    public record Claude(
            String apiKey,
            /** tier name (LIGHT/STANDARD/PREMIUM) → concrete model id */
            Map<String, String> models
    ) {
    }
}
