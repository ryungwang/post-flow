package com.postflow.shoppingshorts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postflow.ai.LLMProvider;
import com.postflow.ai.dto.GenerationRequest;
import com.postflow.ai.dto.GenerationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShoppingShortsCostSafetyTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void blocksClaudePlanningBeforeLlmCallWhenLiveApiIsLocked() {
        LLMProvider throwingLlm = new LLMProvider() {
            @Override
            public String id() {
                return "test-llm";
            }

            @Override
            public GenerationResult generate(GenerationRequest request) {
                throw new AssertionError("LLM must not be called while shopping shorts live API is locked");
            }
        };
        ClaudeShoppingShortsPlanningProvider provider =
                new ClaudeShoppingShortsPlanningProvider(throwingLlm, objectMapper, new ShoppingShortsCostSafety(false));

        assertThatThrownBy(() -> provider.generate(product()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHOPPING_SHORTS_LIVE_API_ENABLED=true");
    }

    @Test
    void blocksKlingSubmitBeforeHttpCallWhenLiveApiIsLocked() {
        KlingShoppingShortsVideoGenerationProvider provider = new KlingShoppingShortsVideoGenerationProvider(
                objectMapper,
                new ShoppingShortsCostSafety(false),
                "https://api.klingai.com",
                "",
                "access-key",
                "secret-key-secret-key-secret-key-1234",
                "kling-v2.1",
                "std",
                "/v1/videos/text2video",
                "/v1/videos/image2video",
                "",
                "/v1/videos/text2video/{taskId}",
                "/v1/videos/image2video/{taskId}");

        assertThatThrownBy(() -> provider.generateScenes(tempDir, List.of(Map.of(
                        "sceneId", "scene-01",
                        "prompt", "A product shot",
                        "negativePrompt", "text",
                        "duration", 5))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHOPPING_SHORTS_LIVE_API_ENABLED=true");
    }

    @Test
    void acceptsSingleKlingApiKeyShapeButStillBlocksWhenLiveApiIsLocked() {
        KlingShoppingShortsVideoGenerationProvider provider = new KlingShoppingShortsVideoGenerationProvider(
                objectMapper,
                new ShoppingShortsCostSafety(false),
                "https://api.klingai.com",
                "api_key-kling-test",
                "",
                "",
                "kling-v2.1",
                "std",
                "/v1/videos/text2video",
                "/v1/videos/image2video",
                "",
                "/v1/videos/text2video/{taskId}",
                "/v1/videos/image2video/{taskId}");

        assertThatThrownBy(() -> provider.generateScenes(tempDir, List.of(Map.of(
                        "sceneId", "scene-01",
                        "prompt", "A product shot",
                        "negativePrompt", "text",
                        "duration", 5))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHOPPING_SHORTS_LIVE_API_ENABLED=true");
    }

    @Test
    void rejectsUnsafeSceneIdInMockVideoProvider() {
        MockShoppingShortsVideoGenerationProvider provider = new MockShoppingShortsVideoGenerationProvider(objectMapper);

        assertThatThrownBy(() -> provider.generateScenes(tempDir, List.of(Map.of(
                        "sceneId", "../escape",
                        "prompt", "A product shot",
                        "negativePrompt", "text",
                        "duration", 5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Scene ID");
    }

    @Test
    void rejectsNonHttpsKlingEndpointBeforeNetworkCall() {
        KlingShoppingShortsVideoGenerationProvider provider = new KlingShoppingShortsVideoGenerationProvider(
                objectMapper,
                new ShoppingShortsCostSafety(true),
                "http://127.0.0.1:9",
                "api_key-kling-test",
                "",
                "",
                "kling-v2.1",
                "std",
                "/v1/videos/text2video",
                "/v1/videos/image2video",
                "",
                "/v1/videos/text2video/{taskId}",
                "/v1/videos/image2video/{taskId}");

        ShoppingShortsVideoGenerationProvider.SceneGenerationResult result =
                provider.generateScenes(tempDir, List.of(Map.of(
                        "sceneId", "scene-01",
                        "prompt", "A product shot",
                        "negativePrompt", "text",
                        "duration", 5)));

        assertThat(result.jobs()).hasSize(1);
        assertThat(result.jobs().getFirst().status()).isEqualTo("FAILED");
        assertThat(result.jobs().getFirst().errorMessage()).contains("https");
    }

    private ShoppingShortsProductDocument product() {
        Instant now = Instant.now();
        return new ShoppingShortsProductDocument(
                "prod-test",
                "테스트 상품",
                "브랜드",
                "생활용품",
                29900L,
                null,
                null,
                List.of(),
                List.of("확인된 특징"),
                "상품 설명",
                "https://www.coupang.com/vp/products/test",
                "https://link.coupang.com/a/test",
                List.of("https://image.example.com/product.jpg"),
                now,
                now,
                now);
    }
}
