package com.postflow.shoppingshorts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShoppingShortsWorkspaceServiceFlowTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    private String originalUserDir;
    private ShoppingShortsWorkspaceService service;

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        service = new ShoppingShortsWorkspaceService(
                objectMapper,
                new MockShoppingShortsPlanningProvider(),
                new MockShoppingShortsStoryboardProvider(),
                new MockShoppingShortsVideoGenerationProvider(objectMapper),
                new MockShoppingShortsTtsProvider(objectMapper),
                new MockShoppingShortsRenderProvider(objectMapper),
                new MockShoppingShortsQualityProvider(objectMapper),
                new ShoppingShortsCostSafety(false));
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.dir", originalUserDir);
    }

    @Test
    void completesIsolatedShoppingShortsWorkspaceFlow() throws Exception {
        Long userId = 77L;
        ShoppingShortsDtos.ProductCaptureResponse captured = service.captureProduct(
                userId,
                new ShoppingShortsProductCaptureRequest(
                        "무선 핸디 청소기 XYZ",
                        "클린브랜드",
                        "생활용품",
                        29900L,
                        39900L,
                        25,
                        List.of("화이트", "블랙"),
                        List.of("600ml 대용량", "물세척 가능 필터", "구성품 3종"),
                        "차량과 책상 주변 청소에 쓰기 좋은 무선 핸디 청소기",
                        "https://www.coupang.com/vp/products/test",
                        "https://link.coupang.com/a/test",
                        List.of("https://image.example.com/product-main.jpg"),
                        null));

        ShoppingShortsDtos.ValidationResponse validation = service.validateProduct(userId, captured.productId());
        assertThat(validation.valid()).isTrue();

        ShoppingShortsDtos.CampaignGenerationResponse campaigns = service.generateCampaigns(userId, captured.productId());
        assertThat(campaigns.campaignCandidates()).hasSize(3);
        String campaignId = campaigns.campaignCandidates().getFirst().campaignId();

        ShoppingShortsDtos.StoryboardResponse storyboard = service.selectCampaign(userId, captured.productId(), campaignId);
        assertThat(storyboard.scenes()).hasSizeGreaterThanOrEqualTo(4);

        ShoppingShortsDtos.ProductionAssetResponse assets =
                service.prepareProductionAssets(userId, captured.productId(), campaignId);
        assertThat(assets.createdFiles()).contains("prompts.json", "subtitles/subtitle.srt", "audio/narration.txt");

        ShoppingShortsDtos.SceneGenerationResponse scenes =
                service.generateAiScenes(userId, captured.productId(), campaignId);
        assertThat(scenes.jobs()).allMatch(job -> "COMPLETED".equals(job.status()));

        ShoppingShortsDtos.SceneGenerationResponse polled =
                service.pollAiScenes(userId, captured.productId(), campaignId);
        assertThat(polled.jobs()).allMatch(job -> "COMPLETED".equals(job.status()));

        ShoppingShortsDtos.TtsGenerationResponse tts = service.generateTts(userId, captured.productId(), campaignId);
        assertThat(tts.status()).isEqualTo("COMPLETED");

        ShoppingShortsDtos.RenderResponse render = service.renderCampaign(userId, captured.productId(), campaignId);
        assertThat(render.status()).isEqualTo("COMPLETED");
        assertThat(render.width()).isEqualTo(1080);
        assertThat(render.height()).isEqualTo(1920);

        ShoppingShortsDtos.QualityCheckResponse quality = service.checkQuality(userId, captured.productId(), campaignId);
        assertThat(quality.status()).isEqualTo("PASS");
        assertThat(service.listProducts(userId)).extracting(ShoppingShortsDtos.ProductSummary::productId)
                .contains(captured.productId());

        Path productDir = Path.of(captured.productPath());
        assertThat(Files.isRegularFile(productDir.resolve("metadata/checkpoints.json"))).isTrue();
        assertThat(Files.readString(productDir.resolve("metadata/checkpoints.json"))).contains("COMPLETED");
    }

    @Test
    void deletesCapturedProductWorkspace() {
        Long userId = 77L;
        ShoppingShortsDtos.ProductCaptureResponse captured = service.captureProduct(
                userId,
                new ShoppingShortsProductCaptureRequest(
                        "삭제 테스트 상품",
                        "브랜드",
                        "생활용품",
                        10000L,
                        null,
                        null,
                        List.of(),
                        List.of("테스트 특징"),
                        "삭제 테스트 설명",
                        "https://www.coupang.com/vp/products/delete-test",
                        "https://link.coupang.com/a/delete-test",
                        List.of("https://image.example.com/delete-test.jpg"),
                        null));

        Path productDir = Path.of(captured.productPath());
        assertThat(Files.isDirectory(productDir)).isTrue();

        service.deleteProduct(userId, captured.productId());

        assertThat(Files.exists(productDir)).isFalse();
        assertThat(service.listProducts(userId)).extracting(ShoppingShortsDtos.ProductSummary::productId)
                .doesNotContain(captured.productId());
    }

    @Test
    void rejectsAnonymousWorkspaceAccess() {
        assertThatThrownBy(() -> service.listProducts(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("로그인");
    }
}
