package com.postflow.shoppingshorts;

import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/shopping-shorts")
public class ShoppingShortsController {
    private final ShoppingShortsWorkspaceService workspaceService;

    public ShoppingShortsController(ShoppingShortsWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/status")
    public ShoppingShortsDtos.StatusResponse status() {
        return workspaceService.status();
    }

    @GetMapping("/products")
    public List<ShoppingShortsDtos.ProductSummary> products(@AuthenticationPrincipal Long userId) {
        return workspaceService.listProducts(userId);
    }

    @GetMapping("/products/{productId}/state")
    public ShoppingShortsDtos.ProductWorkspaceStateResponse productState(
            @AuthenticationPrincipal Long userId,
            @org.springframework.web.bind.annotation.PathVariable String productId) {
        return workspaceService.productState(userId, productId);
    }

    @PostMapping("/products/capture")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingShortsDtos.ProductCaptureResponse captureProduct(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ShoppingShortsProductCaptureRequest request) {
        return workspaceService.captureProduct(userId, request);
    }

    @DeleteMapping("/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(
            @AuthenticationPrincipal Long userId,
            @org.springframework.web.bind.annotation.PathVariable String productId) {
        workspaceService.deleteProduct(userId, productId);
    }

    @PostMapping("/products/{productId}/validate")
    public ShoppingShortsDtos.ValidationResponse validateProduct(
            @AuthenticationPrincipal Long userId,
            @org.springframework.web.bind.annotation.PathVariable String productId) {
        return workspaceService.validateProduct(userId, productId);
    }

    @PostMapping("/products/{productId}/campaigns/generate")
    public ShoppingShortsDtos.CampaignGenerationResponse generateCampaigns(
            @AuthenticationPrincipal Long userId,
            @org.springframework.web.bind.annotation.PathVariable String productId) {
        return workspaceService.generateCampaigns(userId, productId);
    }

    @PostMapping("/products/{productId}/campaigns/select")
    public ShoppingShortsDtos.StoryboardResponse selectCampaign(
            @AuthenticationPrincipal Long userId,
            @org.springframework.web.bind.annotation.PathVariable String productId,
            @Valid @RequestBody ShoppingShortsDtos.SelectCampaignRequest request) {
        return workspaceService.selectCampaign(userId, productId, request.campaignId());
    }

    @PostMapping("/products/{productId}/campaigns/{campaignId}/prepare-assets")
    public ShoppingShortsDtos.ProductionAssetResponse prepareProductionAssets(
            @AuthenticationPrincipal Long userId,
            @org.springframework.web.bind.annotation.PathVariable String productId,
            @org.springframework.web.bind.annotation.PathVariable String campaignId) {
        return workspaceService.prepareProductionAssets(userId, productId, campaignId);
    }

    @PostMapping("/products/{productId}/campaigns/{campaignId}/scenes/generate")
    public ShoppingShortsDtos.SceneGenerationResponse generateAiScenes(
            @AuthenticationPrincipal Long userId,
            @org.springframework.web.bind.annotation.PathVariable String productId,
            @org.springframework.web.bind.annotation.PathVariable String campaignId) {
        return workspaceService.generateAiScenes(userId, productId, campaignId);
    }

    @PostMapping("/products/{productId}/campaigns/{campaignId}/scenes/poll")
    public ShoppingShortsDtos.SceneGenerationResponse pollAiScenes(
            @AuthenticationPrincipal Long userId,
            @org.springframework.web.bind.annotation.PathVariable String productId,
            @org.springframework.web.bind.annotation.PathVariable String campaignId) {
        return workspaceService.pollAiScenes(userId, productId, campaignId);
    }

    @PostMapping("/products/{productId}/campaigns/{campaignId}/scenes/download")
    public ShoppingShortsDtos.SceneGenerationResponse downloadAiScenes(
            @AuthenticationPrincipal Long userId,
            @org.springframework.web.bind.annotation.PathVariable String productId,
            @org.springframework.web.bind.annotation.PathVariable String campaignId) {
        return workspaceService.downloadAiScenes(userId, productId, campaignId);
    }

    @PostMapping("/products/{productId}/campaigns/{campaignId}/tts/generate")
    public ShoppingShortsDtos.TtsGenerationResponse generateTts(
            @AuthenticationPrincipal Long userId,
            @org.springframework.web.bind.annotation.PathVariable String productId,
            @org.springframework.web.bind.annotation.PathVariable String campaignId) {
        return workspaceService.generateTts(userId, productId, campaignId);
    }

    @PostMapping("/products/{productId}/campaigns/{campaignId}/render")
    public ShoppingShortsDtos.RenderResponse renderCampaign(
            @AuthenticationPrincipal Long userId,
            @org.springframework.web.bind.annotation.PathVariable String productId,
            @org.springframework.web.bind.annotation.PathVariable String campaignId) {
        return workspaceService.renderCampaign(userId, productId, campaignId);
    }

    @PostMapping("/products/{productId}/campaigns/{campaignId}/quality/check")
    public ShoppingShortsDtos.QualityCheckResponse checkQuality(
            @AuthenticationPrincipal Long userId,
            @org.springframework.web.bind.annotation.PathVariable String productId,
            @org.springframework.web.bind.annotation.PathVariable String campaignId) {
        return workspaceService.checkQuality(userId, productId, campaignId);
    }

    @GetMapping("/products/{productId}/campaigns/{campaignId}/outputs/{outputName}")
    public ResponseEntity<FileSystemResource> downloadOutput(
            @AuthenticationPrincipal Long userId,
            @org.springframework.web.bind.annotation.PathVariable String productId,
            @org.springframework.web.bind.annotation.PathVariable String campaignId,
            @org.springframework.web.bind.annotation.PathVariable String outputName) {
        Path output = workspaceService.campaignOutputFile(userId, productId, campaignId, outputName);
        return ResponseEntity.ok()
                .contentType(contentType(outputName))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + output.getFileName() + "\"")
                .body(new FileSystemResource(output));
    }

    private MediaType contentType(String outputName) {
        return switch (outputName) {
            case "final", "draft" -> MediaType.parseMediaType("video/mp4");
            case "thumbnail" -> MediaType.IMAGE_PNG;
            case "contact-sheet" -> MediaType.IMAGE_JPEG;
            case "quality", "storyboard" -> MediaType.APPLICATION_JSON;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
