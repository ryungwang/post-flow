package com.postflow.shoppingshorts;

import java.time.Instant;
import java.util.List;

public final class ShoppingShortsDtos {
    private ShoppingShortsDtos() {
    }

    public record StatusResponse(
            String workspacePath,
            String disclosure,
            String defaultMode,
            String planningProvider,
            String storyboardProvider,
            String videoProvider,
            String ttsProvider,
            String renderProvider,
            String qualityProvider,
            boolean liveApiEnabled,
            String costSafetyMode,
            List<String> paidCallStages,
            int maxParallelScenes,
            List<Integer> supportedDurations,
            List<String> checkpoints
    ) {
    }

    public record ProductSummary(
            String productId,
            String productName,
            String brand,
            Long price,
            int sourceImageCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        static ProductSummary from(ShoppingShortsProductDocument product) {
            int imageCount = product.sourceImages() == null ? 0 : product.sourceImages().size();
            return new ProductSummary(
                    product.productId(),
                    product.productName(),
                    product.brand(),
                    product.price(),
                    imageCount,
                    product.createdAt(),
                    product.updatedAt());
        }
    }

    public record ProductCaptureResponse(
            String productId,
            String productPath,
            ProductSummary product
    ) {
    }

    public record ProductWorkspaceStateResponse(
            ProductSummary product,
            CampaignGenerationResponse campaigns,
            StoryboardResponse storyboard,
            ProductionAssetResponse productionAssets,
            SceneGenerationResponse sceneGeneration,
            TtsGenerationResponse ttsGeneration,
            RenderResponse renderResult,
            QualityCheckResponse qualityResult
    ) {
    }

    public record ValidationResponse(
            String productId,
            boolean valid,
            List<String> missingFields,
            List<String> warnings,
            String nextStep
    ) {
    }

    public record ProductAnalysis(
            String productId,
            List<String> targetSituations,
            List<String> sellingPoints,
            List<String> riskNotes,
            List<String> recommendedStyles,
            List<String> hookCandidates,
            int recommendedDuration
    ) {
    }

    public record CampaignCandidate(
            String campaignId,
            String style,
            String concept,
            String hook,
            String targetAudience,
            List<String> sellingPoints,
            String cta,
            int recommendedDuration,
            int estimatedAiSceneCount,
            int originalImageSceneCount,
            String estimatedCostLevel,
            String accuracyRiskLevel,
            int recommendationScore,
            String recommendationReason
    ) {
    }

    public record CampaignGenerationResponse(
            String productId,
            ProductAnalysis productAnalysis,
            List<CampaignCandidate> campaignCandidates
    ) {
    }

    public record SelectCampaignRequest(String campaignId) {
    }

    public record StoryboardScene(
            String sceneId,
            int order,
            String purpose,
            double duration,
            String sourceType,
            List<String> sourceAssetIds,
            String visualDescription,
            String cameraShot,
            String cameraMovement,
            String transitionIn,
            String transitionOut,
            String caption,
            String narration,
            String soundEffect,
            String klingPrompt,
            String negativePrompt,
            boolean requiresAiGeneration
    ) {
    }

    public record YoutubeMetadata(
            String title,
            String description,
            List<String> hashtags,
            String pinnedComment,
            String affiliateUrl,
            String disclosure
    ) {
    }

    public record StoryboardResponse(
            String productId,
            String campaignId,
            String title,
            String style,
            String hook,
            int duration,
            List<StoryboardScene> scenes,
            YoutubeMetadata youtube,
            String disclosure
    ) {
    }

    public record ProductionAssetResponse(
            String productId,
            String campaignId,
            List<String> createdFiles,
            int aiSceneCount,
            int subtitleCount,
            String estimatedCostLevel,
            String nextStep
    ) {
    }

    public record SceneJob(
            String sceneId,
            String status,
            String provider,
            String model,
            String requestPath,
            String resultPath,
            String errorMessage
    ) {
    }

    public record SceneGenerationResponse(
            String productId,
            String campaignId,
            String provider,
            int submittedSceneCount,
            int cachedSceneCount,
            List<SceneJob> jobs,
            String nextStep
    ) {
    }

    public record TtsGenerationResponse(
            String productId,
            String campaignId,
            String provider,
            String model,
            String status,
            String narrationPath,
            String audioPath,
            String timingPath,
            int segmentCount,
            double duration,
            String nextStep
    ) {
    }

    public record RenderResponse(
            String productId,
            String campaignId,
            String provider,
            String status,
            String draftPath,
            String finalPath,
            String thumbnailPath,
            String contactSheetPath,
            String metadataPath,
            double duration,
            int width,
            int height,
            String nextStep
    ) {
    }

    public record QualityCheckItem(
            String code,
            String label,
            String status,
            String message
    ) {
    }

    public record QualityCheckResponse(
            String productId,
            String campaignId,
            String provider,
            String status,
            List<QualityCheckItem> checks,
            String reportPath,
            String nextStep
    ) {
    }
}
