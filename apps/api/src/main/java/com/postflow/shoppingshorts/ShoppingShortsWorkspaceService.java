package com.postflow.shoppingshorts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ShoppingShortsWorkspaceService {
    private static final String DISCLOSURE = "이 포스팅은 쿠팡파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.";
    private static final List<String> CHECKPOINTS = List.of(
            "PRODUCT_CAPTURED",
            "PRODUCT_VALIDATED",
            "PRODUCT_ANALYZED",
            "CAMPAIGNS_CREATED",
            "CAMPAIGN_SELECTED",
            "STORYBOARD_CREATED",
            "PROMPTS_CREATED",
            "AI_SCENES_CREATED",
            "TTS_CREATED",
            "SUBTITLES_CREATED",
            "DRAFT_RENDERED",
            "FINAL_RENDERED",
            "THUMBNAIL_CREATED",
            "COMPLETED");

    private final ObjectMapper objectMapper;
    private final Path workspaceRoot;
    private final ShoppingShortsPlanningProvider planningProvider;
    private final ShoppingShortsStoryboardProvider storyboardProvider;
    private final ShoppingShortsVideoGenerationProvider videoGenerationProvider;
    private final ShoppingShortsTtsProvider ttsProvider;
    private final ShoppingShortsRenderProvider renderProvider;
    private final ShoppingShortsQualityProvider qualityProvider;
    private final ShoppingShortsCostSafety costSafety;

    public ShoppingShortsWorkspaceService(ObjectMapper objectMapper,
                                          ShoppingShortsPlanningProvider planningProvider,
                                          ShoppingShortsStoryboardProvider storyboardProvider,
                                          ShoppingShortsVideoGenerationProvider videoGenerationProvider,
                                          ShoppingShortsTtsProvider ttsProvider,
                                          ShoppingShortsRenderProvider renderProvider,
                                          ShoppingShortsQualityProvider qualityProvider,
                                          ShoppingShortsCostSafety costSafety) {
        this.objectMapper = objectMapper;
        this.planningProvider = planningProvider;
        this.storyboardProvider = storyboardProvider;
        this.videoGenerationProvider = videoGenerationProvider;
        this.ttsProvider = ttsProvider;
        this.renderProvider = renderProvider;
        this.qualityProvider = qualityProvider;
        this.costSafety = costSafety;
        this.workspaceRoot = Path.of(System.getProperty("user.dir"), "var", "shopping-shorts").normalize();
    }

    public ShoppingShortsDtos.StatusResponse status() {
        return new ShoppingShortsDtos.StatusResponse(
                workspaceRoot.toAbsolutePath().toString(),
                DISCLOSURE,
                "BASIC",
                planningProvider.id(),
                storyboardProvider.id(),
                videoGenerationProvider.id(),
                ttsProvider.id(),
                renderProvider.id(),
                qualityProvider.id(),
                costSafety.liveApiEnabled(),
                costSafety.mode(),
                List.of(
                        "CAMPAIGNS_CREATED: Claude 호출 가능",
                        "STORYBOARD_CREATED: Claude 호출 가능",
                        "AI_SCENES_CREATED: Kling submit 호출 가능",
                        "AI_SCENE_POLL: Kling status 호출 가능",
                        "AI_SCENE_DOWNLOAD: 외부 결과 URL 다운로드 가능"),
                2,
                List.of(15, 20, 30),
                CHECKPOINTS);
    }

    public List<ShoppingShortsDtos.ProductSummary> listProducts(Long userId) {
        Path productsDir = productsDir(userId);
        if (!Files.isDirectory(productsDir)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(productsDir)) {
            return children
                    .filter(Files::isDirectory)
                    .map(path -> path.resolve("product.json"))
                    .filter(Files::isRegularFile)
                    .map(this::readProduct)
                    .sorted(Comparator.comparing(ShoppingShortsProductDocument::updatedAt).reversed())
                    .map(ShoppingShortsDtos.ProductSummary::from)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 상품 목록을 읽지 못했어요.", e);
        }
    }

    public ShoppingShortsDtos.ProductCaptureResponse captureProduct(Long userId, ShoppingShortsProductCaptureRequest req) {
        Instant now = Instant.now();
        String productId = createProductId(req.productName());
        Path productDir = productsDir(userId).resolve(productId).normalize();
        if (!productDir.startsWith(productsDir(userId))) {
            throw new IllegalArgumentException("잘못된 상품 경로입니다.");
        }

        ShoppingShortsProductDocument product = new ShoppingShortsProductDocument(
                productId,
                clean(req.productName()),
                clean(req.brand()),
                clean(req.category()),
                req.price(),
                req.originalPrice(),
                req.discountRate(),
                cleanList(req.options()),
                cleanList(req.features()),
                clean(req.description()),
                clean(req.productUrl()),
                clean(req.affiliateUrl()),
                cleanList(req.sourceImages()),
                req.extractedAt() == null ? now : req.extractedAt(),
                now,
                now);

        try {
            Files.createDirectories(productDir.resolve("source/images"));
            Files.createDirectories(productDir.resolve("source/screenshots"));
            Files.createDirectories(productDir.resolve("analysis"));
            Files.createDirectories(productDir.resolve("campaigns"));
            Files.createDirectories(productDir.resolve("shared-assets"));
            Files.createDirectories(productDir.resolve("metadata"));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(productDir.resolve("product.json").toFile(), product);
            writeCheckpoint(productDir, "PRODUCT_CAPTURED", "product.json");
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 상품 원본 데이터를 저장하지 못했어요.", e);
        }

        return new ShoppingShortsDtos.ProductCaptureResponse(
                productId,
                productDir.toAbsolutePath().toString(),
                ShoppingShortsDtos.ProductSummary.from(product));
    }

    public void deleteProduct(Long userId, String productId) {
        Path productDir = productDir(userId, productId);
        try (Stream<Path> paths = Files.walk(productDir)) {
            List<Path> targets = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path target : targets) {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 상품을 삭제하지 못했어요.", e);
        }
    }

    public ShoppingShortsDtos.ProductWorkspaceStateResponse productState(Long userId, String productId) {
        Path productDir = productDir(userId, productId);
        ShoppingShortsProductDocument product = readProduct(productDir.resolve("product.json"));
        Path campaignDir = latestCampaignDir(productDir);
        ShoppingShortsDtos.CampaignGenerationResponse campaigns = Files.isRegularFile(productDir.resolve("campaigns/candidates.json"))
                ? readCampaigns(productDir)
                : null;
        if (campaignDir == null) {
            return new ShoppingShortsDtos.ProductWorkspaceStateResponse(
                    ShoppingShortsDtos.ProductSummary.from(product),
                    campaigns,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        String campaignId = campaignDir.getFileName().toString();
        ShoppingShortsDtos.StoryboardResponse storyboard = readIfExists(campaignDir.resolve("storyboard.json"), ShoppingShortsDtos.StoryboardResponse.class);
        ShoppingShortsDtos.ProductionAssetResponse productionAssets = restoreProductionAssets(productId, campaignId, campaignDir);
        ShoppingShortsDtos.SceneGenerationResponse sceneGeneration = restoreSceneGeneration(productId, campaignId, campaignDir);
        ShoppingShortsDtos.TtsGenerationResponse ttsGeneration = restoreTtsGeneration(productId, campaignId, campaignDir);
        ShoppingShortsDtos.RenderResponse renderResult = restoreRenderResult(productId, campaignId, campaignDir);
        ShoppingShortsDtos.QualityCheckResponse qualityResult = restoreQualityResult(productId, campaignId, campaignDir);

        return new ShoppingShortsDtos.ProductWorkspaceStateResponse(
                ShoppingShortsDtos.ProductSummary.from(product),
                campaigns,
                storyboard,
                productionAssets,
                sceneGeneration,
                ttsGeneration,
                renderResult,
                qualityResult);
    }

    public ShoppingShortsDtos.ValidationResponse validateProduct(Long userId, String productId) {
        Path productDir = productDir(userId, productId);
        ShoppingShortsProductDocument product = readProduct(productDir.resolve("product.json"));
        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!StringUtils.hasText(product.productName())) {
            missing.add("상품명");
        }
        if (!StringUtils.hasText(product.affiliateUrl())) {
            missing.add("쿠팡파트너스 URL");
        }
        if (product.sourceImages() == null || product.sourceImages().isEmpty()) {
            warnings.add("대표 이미지가 없으면 Extension 추출 또는 직접 이미지 추가가 필요합니다.");
        }
        boolean hasProductContext = StringUtils.hasText(product.description())
                || (product.features() != null && !product.features().isEmpty());
        if (!hasProductContext) {
            warnings.add("상품 특징이나 상세 설명이 부족하면 광고 기획 정확도가 낮아질 수 있습니다.");
        }
        boolean valid = missing.isEmpty();
        writeCheckpoint(productDir, valid ? "PRODUCT_VALIDATED" : "PRODUCT_CAPTURED", "product.json");
        return new ShoppingShortsDtos.ValidationResponse(
                product.productId(),
                valid,
                missing,
                warnings,
                valid ? "광고 기획안을 생성할 수 있습니다." : "필수값을 보완해야 합니다.");
    }

    public ShoppingShortsDtos.CampaignGenerationResponse generateCampaigns(Long userId, String productId) {
        Path productDir = productDir(userId, productId);
        ShoppingShortsDtos.ValidationResponse validation = validateProduct(userId, productId);
        if (!validation.valid()) {
            throw new IllegalArgumentException("상품 필수값이 부족합니다: " + String.join(", ", validation.missingFields()));
        }
        Path candidatesPath = productDir.resolve("campaigns/candidates.json");
        if (Files.isRegularFile(candidatesPath)) {
            writeCheckpoint(productDir, "CAMPAIGNS_CREATED", "campaigns/candidates.json");
            return readCampaigns(productDir);
        }
        ShoppingShortsProductDocument product = readProduct(productDir.resolve("product.json"));
        ShoppingShortsPlanningProvider.PlanningResult planning = planningProvider.generate(product);
        ShoppingShortsDtos.CampaignGenerationResponse response = planning.response();
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(productDir.resolve("analysis/product-analysis.json").toFile(), response.productAnalysis());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(productDir.resolve("campaigns/candidates.json").toFile(), response);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(productDir.resolve("metadata/planning-provider.json").toFile(), Map.of(
                    "provider", planning.provider(),
                    "model", planning.model(),
                    "inputTokens", planning.inputTokens(),
                    "outputTokens", planning.outputTokens(),
                    "createdAt", Instant.now().toString()));
            writeCheckpoint(productDir, "PRODUCT_ANALYZED", "analysis/product-analysis.json");
            writeCheckpoint(productDir, "CAMPAIGNS_CREATED", "campaigns/candidates.json");
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 광고 기획안을 저장하지 못했어요.", e);
        }
        return response;
    }

    public ShoppingShortsDtos.StoryboardResponse selectCampaign(Long userId, String productId, String campaignId) {
        Path productDir = productDir(userId, productId);
        ShoppingShortsDtos.CampaignGenerationResponse generated = readCampaigns(productDir);
        ShoppingShortsDtos.CampaignCandidate selected = generated.campaignCandidates().stream()
                .filter(c -> c.campaignId().equals(campaignId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("선택한 기획안을 찾지 못했어요."));
        ShoppingShortsProductDocument product = readProduct(productDir.resolve("product.json"));
        Path campaignDir = productDir.resolve("campaigns").resolve(campaignId).normalize();
        if (!campaignDir.startsWith(productDir.resolve("campaigns"))) {
            throw new IllegalArgumentException("잘못된 캠페인 경로입니다.");
        }
        Path storyboardPath = campaignDir.resolve("storyboard.json");
        if (Files.isRegularFile(storyboardPath)) {
            try {
                return objectMapper.readValue(storyboardPath.toFile(), ShoppingShortsDtos.StoryboardResponse.class);
            } catch (IOException e) {
                throw new IllegalStateException("쇼핑쇼츠 스토리보드를 읽지 못했어요.", e);
            }
        }
        ShoppingShortsStoryboardProvider.StoryboardResult generatedStoryboard = storyboardProvider.generate(product, selected);
        ShoppingShortsDtos.StoryboardResponse storyboard = generatedStoryboard.response();
        try {
            Files.createDirectories(campaignDir.resolve("subtitles"));
            Files.createDirectories(campaignDir.resolve("audio"));
            Files.createDirectories(campaignDir.resolve("scenes"));
            Files.createDirectories(campaignDir.resolve("renders"));
            Files.createDirectories(campaignDir.resolve("metadata"));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(campaignDir.resolve("campaign.json").toFile(), selected);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(campaignDir.resolve("storyboard.json").toFile(), storyboard);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(campaignDir.resolve("metadata/youtube.json").toFile(), storyboard.youtube());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(campaignDir.resolve("metadata/storyboard-provider.json").toFile(), Map.of(
                    "provider", generatedStoryboard.provider(),
                    "model", generatedStoryboard.model(),
                    "inputTokens", generatedStoryboard.inputTokens(),
                    "outputTokens", generatedStoryboard.outputTokens(),
                    "createdAt", Instant.now().toString()));
            writeCheckpoint(productDir, "CAMPAIGN_SELECTED", "campaigns/" + campaignId + "/campaign.json");
            writeCheckpoint(productDir, "STORYBOARD_CREATED", "campaigns/" + campaignId + "/storyboard.json");
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 스토리보드를 저장하지 못했어요.", e);
        }
        return storyboard;
    }

    public ShoppingShortsDtos.ProductionAssetResponse prepareProductionAssets(Long userId, String productId, String campaignId) {
        Path productDir = productDir(userId, productId);
        Path campaignDir = productDir.resolve("campaigns").resolve(campaignId).normalize();
        if (!campaignDir.startsWith(productDir.resolve("campaigns"))) {
            throw new IllegalArgumentException("잘못된 캠페인 경로입니다.");
        }
        Path storyboardPath = campaignDir.resolve("storyboard.json");
        if (!Files.isRegularFile(storyboardPath)) {
            throw new IllegalStateException("먼저 기획안을 선택해 스토리보드를 생성해야 합니다.");
        }
        ShoppingShortsDtos.StoryboardResponse storyboard;
        try {
            storyboard = objectMapper.readValue(storyboardPath.toFile(), ShoppingShortsDtos.StoryboardResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 스토리보드를 읽지 못했어요.", e);
        }
        ShoppingShortsProductDocument product = readProduct(productDir.resolve("product.json"));

        List<Map<String, Object>> prompts = storyboard.scenes().stream()
                .filter(ShoppingShortsDtos.StoryboardScene::requiresAiGeneration)
                .map(scene -> buildScenePrompt(scene, product))
                .toList();
        List<Map<String, Object>> subtitles = buildSubtitles(storyboard);
        String narration = storyboard.scenes().stream()
                .map(ShoppingShortsDtos.StoryboardScene::narration)
                .filter(StringUtils::hasText)
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
        Map<String, Object> cost = Map.of(
                "mode", "PREPARED_ONLY",
                "aiSceneCount", prompts.size(),
                "estimatedCostLevel", prompts.size() == 0 ? "LOW" : prompts.size() <= 2 ? "MEDIUM" : "HIGH",
                "claudeCalls", 0,
                "klingCalls", 0,
                "ttsCalls", 0,
                "note", "제작 패키지 준비 단계입니다. Kling/TTS 버튼을 실행하기 전까지 추가 비용 호출은 없습니다.");
        Map<String, Object> jobLog = Map.of(
                "jobType", "PREPARE_PRODUCTION_ASSETS",
                "status", "COMPLETED",
                "completedAt", Instant.now().toString(),
                "provider", "LOCAL_PREP");

        List<String> createdFiles = List.of(
                "prompts.json",
                "subtitles/subtitle.json",
                "subtitles/subtitle.srt",
                "audio/narration.txt",
                "metadata/generation-cost.json",
                "metadata/job-log.json");
        try {
            Files.createDirectories(campaignDir.resolve("subtitles"));
            Files.createDirectories(campaignDir.resolve("audio"));
            Files.createDirectories(campaignDir.resolve("metadata"));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(campaignDir.resolve("prompts.json").toFile(), prompts);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(campaignDir.resolve("subtitles/subtitle.json").toFile(), subtitles);
            Files.writeString(campaignDir.resolve("subtitles/subtitle.srt"), buildSrt(subtitles));
            Files.writeString(campaignDir.resolve("audio/narration.txt"), narration);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(campaignDir.resolve("metadata/generation-cost.json").toFile(), cost);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(campaignDir.resolve("metadata/job-log.json").toFile(), jobLog);
            writeCheckpoint(productDir, "PROMPTS_CREATED", "campaigns/" + campaignId + "/prompts.json");
            writeCheckpoint(productDir, "TTS_CREATED", "campaigns/" + campaignId + "/audio/narration.txt");
            writeCheckpoint(productDir, "SUBTITLES_CREATED", "campaigns/" + campaignId + "/subtitles/subtitle.json");
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 제작 패키지를 저장하지 못했어요.", e);
        }

        return new ShoppingShortsDtos.ProductionAssetResponse(
                productId,
                campaignId,
                createdFiles,
                prompts.size(),
                subtitles.size(),
                prompts.isEmpty() ? "LOW" : "MEDIUM",
                "다음 단계는 Kling/TTS Provider 연결 또는 Remotion 렌더링입니다.");
    }

    private Map<String, Object> buildScenePrompt(ShoppingShortsDtos.StoryboardScene scene, ShoppingShortsProductDocument product) {
        Map<String, Object> prompt = new java.util.LinkedHashMap<>();
        prompt.put("sceneId", scene.sceneId());
        prompt.put("provider", "KLING");
        prompt.put("mode", "IMAGE_TO_VIDEO");
        prompt.put("duration", scene.duration());
        prompt.put("prompt", scene.klingPrompt());
        prompt.put("negativePrompt", scene.negativePrompt());
        String imageUrl = firstText(scene.sourceAssetIds());
        if (!StringUtils.hasText(imageUrl)) {
            imageUrl = firstText(product.sourceImages());
        }
        if (StringUtils.hasText(imageUrl)) {
            prompt.put("imageUrl", imageUrl);
        }
        return prompt;
    }

    public ShoppingShortsDtos.SceneGenerationResponse generateAiScenes(Long userId, String productId, String campaignId) {
        Path productDir = productDir(userId, productId);
        Path campaignDir = productDir.resolve("campaigns").resolve(campaignId).normalize();
        if (!campaignDir.startsWith(productDir.resolve("campaigns"))) {
            throw new IllegalArgumentException("잘못된 캠페인 경로입니다.");
        }
        Path promptsPath = campaignDir.resolve("prompts.json");
        if (!Files.isRegularFile(promptsPath)) {
            throw new IllegalStateException("먼저 제작 패키지를 준비해야 합니다.");
        }
        List<Map<String, Object>> prompts = readPromptList(promptsPath);
        ShoppingShortsVideoGenerationProvider.SceneGenerationResult result =
                videoGenerationProvider.generateScenes(campaignDir, prompts);
        try {
            Files.createDirectories(campaignDir.resolve("metadata"));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(campaignDir.resolve("metadata/scene-generation.json").toFile(), Map.of(
                    "provider", result.provider(),
                    "submittedSceneCount", result.submittedSceneCount(),
                    "cachedSceneCount", result.cachedSceneCount(),
                    "jobs", result.jobs(),
                    "createdAt", Instant.now().toString()));
            writeCheckpoint(productDir, "AI_SCENES_CREATED", "campaigns/" + campaignId + "/metadata/scene-generation.json");
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 Scene 생성 결과를 저장하지 못했어요.", e);
        }
        return new ShoppingShortsDtos.SceneGenerationResponse(
                productId,
                campaignId,
                result.provider(),
                result.submittedSceneCount(),
                result.cachedSceneCount(),
                result.jobs(),
                prompts.isEmpty()
                        ? "AI 영상 Scene이 없어 Remotion 렌더링 단계로 넘어갈 수 있습니다."
                        : "다음 단계는 TTS Provider 연결 또는 Remotion 렌더링입니다.");
    }

    public ShoppingShortsDtos.SceneGenerationResponse pollAiScenes(Long userId, String productId, String campaignId) {
        Path productDir = productDir(userId, productId);
        Path campaignDir = productDir.resolve("campaigns").resolve(campaignId).normalize();
        if (!campaignDir.startsWith(productDir.resolve("campaigns"))) {
            throw new IllegalArgumentException("잘못된 캠페인 경로입니다.");
        }
        ShoppingShortsVideoGenerationProvider.SceneGenerationResult result =
                videoGenerationProvider.pollScenes(campaignDir);
        try {
            Files.createDirectories(campaignDir.resolve("metadata"));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(campaignDir.resolve("metadata/scene-polling.json").toFile(), Map.of(
                    "provider", result.provider(),
                    "jobs", result.jobs(),
                    "createdAt", Instant.now().toString()));
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 Scene Polling 결과를 저장하지 못했어요.", e);
        }
        return new ShoppingShortsDtos.SceneGenerationResponse(
                productId,
                campaignId,
                result.provider(),
                result.submittedSceneCount(),
                result.cachedSceneCount(),
                result.jobs(),
                "완료된 Scene은 결과 URL 또는 결과 파일을 후속 렌더링 단계에서 사용합니다.");
    }

    public ShoppingShortsDtos.SceneGenerationResponse downloadAiScenes(Long userId, String productId, String campaignId) {
        Path productDir = productDir(userId, productId);
        Path campaignDir = productDir.resolve("campaigns").resolve(campaignId).normalize();
        if (!campaignDir.startsWith(productDir.resolve("campaigns"))) {
            throw new IllegalArgumentException("잘못된 캠페인 경로입니다.");
        }
        ShoppingShortsVideoGenerationProvider.SceneGenerationResult result =
                videoGenerationProvider.downloadScenes(campaignDir);
        try {
            Files.createDirectories(campaignDir.resolve("metadata"));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(campaignDir.resolve("metadata/scene-downloads.json").toFile(), Map.of(
                    "provider", result.provider(),
                    "downloadedSceneCount", result.submittedSceneCount(),
                    "cachedSceneCount", result.cachedSceneCount(),
                    "jobs", result.jobs(),
                    "createdAt", Instant.now().toString()));
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 Scene 다운로드 결과를 저장하지 못했어요.", e);
        }
        return new ShoppingShortsDtos.SceneGenerationResponse(
                productId,
                campaignId,
                result.provider(),
                result.submittedSceneCount(),
                result.cachedSceneCount(),
                result.jobs(),
                "다운로드된 Scene은 Remotion 렌더링 단계에서 사용할 수 있습니다.");
    }

    public ShoppingShortsDtos.TtsGenerationResponse generateTts(Long userId, String productId, String campaignId) {
        Path productDir = productDir(userId, productId);
        Path campaignDir = productDir.resolve("campaigns").resolve(campaignId).normalize();
        if (!campaignDir.startsWith(productDir.resolve("campaigns"))) {
            throw new IllegalArgumentException("잘못된 캠페인 경로입니다.");
        }
        ShoppingShortsTtsProvider.TtsResult result = ttsProvider.generate(campaignDir);
        try {
            Files.createDirectories(campaignDir.resolve("metadata"));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(campaignDir.resolve("metadata/tts-generation.json").toFile(), Map.of(
                    "provider", result.provider(),
                    "model", result.model(),
                    "status", result.status(),
                    "narrationPath", result.narrationPath(),
                    "audioPath", result.audioPath(),
                    "timingPath", result.timingPath(),
                    "segmentCount", result.segmentCount(),
                    "duration", result.duration(),
                    "createdAt", Instant.now().toString()));
            writeCheckpoint(productDir, "TTS_CREATED", "campaigns/" + campaignId + "/" + result.audioPath());
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 TTS 결과를 저장하지 못했어요.", e);
        }
        return new ShoppingShortsDtos.TtsGenerationResponse(
                productId,
                campaignId,
                result.provider(),
                result.model(),
                result.status(),
                result.narrationPath(),
                result.audioPath(),
                result.timingPath(),
                result.segmentCount(),
                result.duration(),
                "다음 단계는 자막 타이밍 보정 또는 Remotion 렌더링입니다.");
    }

    public ShoppingShortsDtos.RenderResponse renderCampaign(Long userId, String productId, String campaignId) {
        Path productDir = productDir(userId, productId);
        Path campaignDir = productDir.resolve("campaigns").resolve(campaignId).normalize();
        if (!campaignDir.startsWith(productDir.resolve("campaigns"))) {
            throw new IllegalArgumentException("잘못된 캠페인 경로입니다.");
        }
        ShoppingShortsRenderProvider.RenderResult result = renderProvider.render(campaignDir);
        writeCheckpoint(productDir, "DRAFT_RENDERED", "campaigns/" + campaignId + "/" + result.draftPath());
        writeCheckpoint(productDir, "FINAL_RENDERED", "campaigns/" + campaignId + "/" + result.finalPath());
        writeCheckpoint(productDir, "THUMBNAIL_CREATED", "campaigns/" + campaignId + "/" + result.thumbnailPath());
        return new ShoppingShortsDtos.RenderResponse(
                productId,
                campaignId,
                result.provider(),
                result.status(),
                result.draftPath(),
                result.finalPath(),
                result.thumbnailPath(),
                result.contactSheetPath(),
                result.metadataPath(),
                result.duration(),
                result.width(),
                result.height(),
                "다음 단계는 최종 품질 검증입니다.");
    }

    public ShoppingShortsDtos.QualityCheckResponse checkQuality(Long userId, String productId, String campaignId) {
        Path productDir = productDir(userId, productId);
        Path campaignDir = productDir.resolve("campaigns").resolve(campaignId).normalize();
        if (!campaignDir.startsWith(productDir.resolve("campaigns"))) {
            throw new IllegalArgumentException("잘못된 캠페인 경로입니다.");
        }
        ShoppingShortsQualityProvider.QualityResult result = qualityProvider.check(campaignDir);
        if ("PASS".equals(result.status())) {
            writeCheckpoint(productDir, "COMPLETED", "campaigns/" + campaignId + "/" + result.reportPath());
        }
        return new ShoppingShortsDtos.QualityCheckResponse(
                productId,
                campaignId,
                result.provider(),
                result.status(),
                result.checks(),
                result.reportPath(),
                "PASS".equals(result.status()) ? "최종 완료 상태입니다." : "실패 항목을 보완한 뒤 다시 검증해야 합니다.");
    }

    public Path campaignOutputFile(Long userId, String productId, String campaignId, String outputName) {
        Path productDir = productDir(userId, productId);
        Path campaignDir = productDir.resolve("campaigns").resolve(campaignId).normalize();
        if (!campaignDir.startsWith(productDir.resolve("campaigns"))) {
            throw new IllegalArgumentException("잘못된 캠페인 경로입니다.");
        }
        Path outputPath = switch (outputName) {
            case "final" -> campaignDir.resolve("renders/final.mp4");
            case "draft" -> campaignDir.resolve("renders/draft.mp4");
            case "thumbnail" -> campaignDir.resolve("renders/thumbnail.png");
            case "contact-sheet" -> campaignDir.resolve("renders/contact-sheet.jpg");
            case "quality" -> campaignDir.resolve("metadata/quality-check.json");
            case "storyboard" -> campaignDir.resolve("storyboard.json");
            default -> throw new IllegalArgumentException("지원하지 않는 쇼핑쇼츠 산출물입니다.");
        };
        Path normalized = outputPath.normalize();
        if (!normalized.startsWith(campaignDir) || !Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("쇼핑쇼츠 산출물을 찾지 못했어요.");
        }
        return normalized;
    }

    private List<Map<String, Object>> buildSubtitles(ShoppingShortsDtos.StoryboardResponse storyboard) {
        List<Map<String, Object>> subtitles = new ArrayList<>();
        double cursor = 0.0;
        for (ShoppingShortsDtos.StoryboardScene scene : storyboard.scenes()) {
            String text = scene.caption();
            double start = cursor;
            double end = cursor + scene.duration();
            if (StringUtils.hasText(text)) {
                subtitles.add(Map.of(
                        "start", start,
                        "end", end,
                        "text", text,
                        "emphasisWords", List.of(),
                        "position", scene.order() == 1 ? "CENTER" : "BOTTOM",
                        "animationType", scene.order() == 1 ? "POP" : "SLIDE_UP",
                        "fontSize", scene.order() == 1 ? 64 : 48));
            }
            cursor = end;
        }
        return subtitles;
    }

    private String buildSrt(List<Map<String, Object>> subtitles) {
        StringBuilder srt = new StringBuilder();
        for (int i = 0; i < subtitles.size(); i++) {
            Map<String, Object> subtitle = subtitles.get(i);
            srt.append(i + 1).append('\n');
            srt.append(formatSrtTime((Double) subtitle.get("start")))
                    .append(" --> ")
                    .append(formatSrtTime((Double) subtitle.get("end")))
                    .append('\n');
            srt.append(subtitle.get("text")).append("\n\n");
        }
        return srt.toString();
    }

    private String formatSrtTime(double seconds) {
        long millis = Math.round(seconds * 1000);
        long hours = millis / 3_600_000;
        long minutes = (millis % 3_600_000) / 60_000;
        long secs = (millis % 60_000) / 1000;
        long ms = millis % 1000;
        return "%02d:%02d:%02d,%03d".formatted(hours, minutes, secs, ms);
    }

    private ShoppingShortsDtos.CampaignGenerationResponse readCampaigns(Path productDir) {
        Path candidates = productDir.resolve("campaigns/candidates.json");
        if (!Files.isRegularFile(candidates)) {
            throw new IllegalStateException("먼저 광고 기획안을 생성해야 합니다.");
        }
        try {
            return objectMapper.readValue(candidates.toFile(), ShoppingShortsDtos.CampaignGenerationResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 광고 기획안을 읽지 못했어요.", e);
        }
    }

    private Path latestCampaignDir(Path productDir) {
        Path campaignsDir = productDir.resolve("campaigns");
        if (!Files.isDirectory(campaignsDir)) {
            return null;
        }
        try (Stream<Path> paths = Files.list(campaignsDir)) {
            return paths
                    .filter(Files::isDirectory)
                    .max(Comparator.comparingLong(this::lastModifiedMillis))
                    .orElse(null);
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 캠페인 목록을 읽지 못했어요.", e);
        }
    }

    private ShoppingShortsDtos.ProductionAssetResponse restoreProductionAssets(String productId, String campaignId, Path campaignDir) {
        Path promptsPath = campaignDir.resolve("prompts.json");
        Path subtitlesPath = campaignDir.resolve("subtitles/subtitle.json");
        if (!Files.isRegularFile(promptsPath) && !Files.isRegularFile(subtitlesPath)) {
            return null;
        }
        int promptCount = Files.isRegularFile(promptsPath) ? readPromptList(promptsPath).size() : 0;
        int subtitleCount = Files.isRegularFile(subtitlesPath) ? readMapList(subtitlesPath).size() : 0;
        List<String> createdFiles = new ArrayList<>();
        if (Files.isRegularFile(promptsPath)) {
            createdFiles.add("prompts.json");
        }
        if (Files.isRegularFile(subtitlesPath)) {
            createdFiles.add("subtitles/subtitle.json");
        }
        if (Files.isRegularFile(campaignDir.resolve("subtitles/subtitle.srt"))) {
            createdFiles.add("subtitles/subtitle.srt");
        }
        if (Files.isRegularFile(campaignDir.resolve("audio/narration.txt"))) {
            createdFiles.add("audio/narration.txt");
        }
        if (Files.isRegularFile(campaignDir.resolve("metadata/generation-cost.json"))) {
            createdFiles.add("metadata/generation-cost.json");
        }
        if (Files.isRegularFile(campaignDir.resolve("metadata/job-log.json"))) {
            createdFiles.add("metadata/job-log.json");
        }
        return new ShoppingShortsDtos.ProductionAssetResponse(
                productId,
                campaignId,
                createdFiles,
                promptCount,
                subtitleCount,
                promptCount == 0 ? "LOW" : "MEDIUM",
                "저장된 제작 패키지를 복원했습니다.");
    }

    private ShoppingShortsDtos.SceneGenerationResponse restoreSceneGeneration(String productId, String campaignId, Path campaignDir) {
        Path scenesDir = campaignDir.resolve("scenes");
        if (!Files.isDirectory(scenesDir)) {
            return null;
        }
        List<ShoppingShortsDtos.SceneJob> jobs = new ArrayList<>();
        try (Stream<Path> paths = Files.list(scenesDir)) {
            for (Path sceneDir : paths.filter(Files::isDirectory).sorted().toList()) {
                Path jobPath = sceneDir.resolve("job.json");
                if (!Files.isRegularFile(jobPath)) {
                    continue;
                }
                Map<String, Object> job = readMap(jobPath);
                String sceneId = stringValue(job.get("sceneId"), sceneDir.getFileName().toString());
                String status = stringValue(job.get("status"), "PROCESSING");
                String resultPath = stringValue(job.get("resultPath"), "");
                if (!StringUtils.hasText(resultPath)) {
                    resultPath = stringValue(job.get("resultUrlPath"), "");
                }
                if (!StringUtils.hasText(resultPath)) {
                    resultPath = stringValue(job.get("pollResponsePath"), "");
                }
                if (!StringUtils.hasText(resultPath)) {
                    resultPath = "scenes/" + sceneDir.getFileName() + "/job.json";
                }
                jobs.add(new ShoppingShortsDtos.SceneJob(
                        sceneId,
                        status,
                        stringValue(job.get("provider"), videoGenerationProvider.id()),
                        stringValue(job.get("model"), "unknown"),
                        stringValue(job.get("requestPath"), "scenes/" + sceneDir.getFileName() + "/request.json"),
                        resultPath,
                        stringValue(job.get("errorMessage"), null)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 Scene 상태를 복원하지 못했어요.", e);
        }
        if (jobs.isEmpty()) {
            return null;
        }
        int cached = (int) jobs.stream().filter(job -> "CACHED".equals(job.status())).count();
        return new ShoppingShortsDtos.SceneGenerationResponse(
                productId,
                campaignId,
                videoGenerationProvider.id(),
                0,
                cached,
                jobs,
                "저장된 Scene Job 상태를 복원했습니다.");
    }

    private ShoppingShortsDtos.TtsGenerationResponse restoreTtsGeneration(String productId, String campaignId, Path campaignDir) {
        Path metadata = campaignDir.resolve("metadata/tts-generation.json");
        if (!Files.isRegularFile(metadata)) {
            return null;
        }
        Map<String, Object> map = readMap(metadata);
        return new ShoppingShortsDtos.TtsGenerationResponse(
                productId,
                campaignId,
                stringValue(map.get("provider"), ttsProvider.id()),
                stringValue(map.get("model"), "unknown"),
                stringValue(map.get("status"), "COMPLETED"),
                stringValue(map.get("narrationPath"), "audio/narration.txt"),
                stringValue(map.get("audioPath"), "audio/narration.aiff"),
                stringValue(map.get("timingPath"), "audio/tts-timing.json"),
                intValue(map.get("segmentCount"), 0),
                doubleValue(map.get("duration"), 0.0),
                "저장된 TTS 결과를 복원했습니다.");
    }

    private ShoppingShortsDtos.RenderResponse restoreRenderResult(String productId, String campaignId, Path campaignDir) {
        Path metadata = campaignDir.resolve("metadata/render-output.json");
        if (!Files.isRegularFile(metadata) && !Files.isRegularFile(campaignDir.resolve("renders/final.mp4"))) {
            return null;
        }
        Map<String, Object> map = Files.isRegularFile(metadata) ? readMap(metadata) : Map.of();
        return new ShoppingShortsDtos.RenderResponse(
                productId,
                campaignId,
                stringValue(map.get("provider"), renderProvider.id()),
                stringValue(map.get("status"), "COMPLETED"),
                stringValue(map.get("draftPath"), "renders/draft.mp4"),
                stringValue(map.get("finalPath"), "renders/final.mp4"),
                stringValue(map.get("thumbnailPath"), "renders/thumbnail.png"),
                stringValue(map.get("contactSheetPath"), "renders/contact-sheet.jpg"),
                "metadata/render-output.json",
                doubleValue(map.get("duration"), 0.0),
                intValue(map.get("width"), 1080),
                intValue(map.get("height"), 1920),
                "저장된 렌더 결과를 복원했습니다.");
    }

    private ShoppingShortsDtos.QualityCheckResponse restoreQualityResult(String productId, String campaignId, Path campaignDir) {
        Path metadata = campaignDir.resolve("metadata/quality-check.json");
        if (!Files.isRegularFile(metadata)) {
            return null;
        }
        try {
            return objectMapper.readValue(metadata.toFile(), ShoppingShortsDtos.QualityCheckResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 품질 검증 결과를 복원하지 못했어요.", e);
        }
    }

    private <T> T readIfExists(Path path, Class<T> type) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return objectMapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 저장 파일을 읽지 못했어요: " + path.getFileName(), e);
        }
    }

    private List<Map<String, Object>> readMapList(Path path) {
        try {
            return objectMapper.readValue(
                    path.toFile(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 목록 파일을 읽지 못했어요: " + path.getFileName(), e);
        }
    }

    private Map<String, Object> readMap(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), Map.class);
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 메타데이터를 읽지 못했어요: " + path.getFileName(), e);
        }
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String firstText(List<String> values) {
        if (values == null) {
            return "";
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private List<Map<String, Object>> readPromptList(Path promptsPath) {
        try {
            return objectMapper.readValue(
                    promptsPath.toFile(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 Kling 프롬프트 파일을 읽지 못했어요.", e);
        }
    }

    private ShoppingShortsProductDocument readProduct(Path productJson) {
        try {
            return objectMapper.readValue(productJson.toFile(), ShoppingShortsProductDocument.class);
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 상품 파일을 읽지 못했어요: " + productJson.getFileName(), e);
        }
    }

    private Path productDir(Long userId, String productId) {
        Path productDir = productsDir(userId).resolve(productId).normalize();
        if (!productDir.startsWith(productsDir(userId)) || !Files.isRegularFile(productDir.resolve("product.json"))) {
            throw new IllegalArgumentException("쇼핑쇼츠 상품을 찾지 못했어요.");
        }
        return productDir;
    }

    private Path productsDir(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("쇼핑쇼츠 작업에는 로그인이 필요합니다.");
        }
        return workspaceRoot.resolve("users").resolve(String.valueOf(userId)).resolve("products").normalize();
    }

    private void writeCheckpoint(Path productDir, String stage, String outputPath) {
        Path checkpointPath = productDir.resolve("metadata/checkpoints.json");
        List<Map<String, Object>> checkpoints = new ArrayList<>();
        if (Files.isRegularFile(checkpointPath)) {
            try {
                checkpoints.addAll(objectMapper.readValue(
                        checkpointPath.toFile(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)));
            } catch (IOException ignored) {
                checkpoints.clear();
            }
        }
        checkpoints.add(Map.of(
                "stage", stage,
                "completedAt", Instant.now().toString(),
                "outputPath", outputPath,
                "provider", "LOCAL_MOCK",
                "model", "none"));
        try {
            Files.createDirectories(checkpointPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(checkpointPath.toFile(), checkpoints);
        } catch (IOException e) {
            throw new IllegalStateException("쇼핑쇼츠 체크포인트를 저장하지 못했어요.", e);
        }
    }

    private static String createProductId(String productName) {
        String slug = Normalizer.normalize(productName == null ? "product" : productName, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9가-힣]+", "-")
                .replaceAll("(^-|-$)", "");
        if (!StringUtils.hasText(slug)) {
            slug = "product";
        }
        if (slug.length() > 48) {
            slug = slug.substring(0, 48).replaceAll("-$", "");
        }
        return "prod-" + slug + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return new LinkedHashSet<>(values.stream()
                .map(ShoppingShortsWorkspaceService::clean)
                .filter(StringUtils::hasText)
                .toList()).stream().toList();
    }
}
