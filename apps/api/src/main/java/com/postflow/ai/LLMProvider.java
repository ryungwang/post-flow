package com.postflow.ai;

import com.postflow.ai.dto.GenerationRequest;
import com.postflow.ai.dto.GenerationResult;

import java.util.List;

/**
 * Vendor-neutral LLM abstraction. Feature code depends only on this interface;
 * concrete vendors (Claude default, OpenAI, ...) are swappable via the {@code ai.provider}
 * setting. See PRD → AI Engine → Provider Abstraction.
 */
public interface LLMProvider {

    /** Stable provider id, e.g. "claude" / "openai". Recorded on every generation. */
    String id();

    /** Single generation. */
    GenerationResult generate(GenerationRequest request);

    /**
     * Bulk generation. Providers may optimize (e.g. Batch API at 50% cost); the default
     * is a sequential fan-out so a provider need only implement {@link #generate}.
     */
    default List<GenerationResult> generateBatch(List<GenerationRequest> requests) {
        return requests.stream().map(this::generate).toList();
    }
}
