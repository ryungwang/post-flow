package com.postflow.shoppingshorts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "shopping-shorts.render.provider", havingValue = "mock", matchIfMissing = true)
public class MockShoppingShortsRenderProvider implements ShoppingShortsRenderProvider {
    private final ObjectMapper objectMapper;

    public MockShoppingShortsRenderProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "local-mock";
    }

    @Override
    public RenderResult render(Path campaignDir) {
        Path storyboardPath = campaignDir.resolve("storyboard.json");
        if (!Files.isRegularFile(storyboardPath)) {
            throw new IllegalStateException("먼저 스토리보드를 생성해야 합니다.");
        }
        Path draftPath = campaignDir.resolve("renders/draft.mock.json");
        Path finalPath = campaignDir.resolve("renders/final.mock.json");
        Path thumbnailPath = campaignDir.resolve("renders/thumbnail.mock.json");
        Path contactSheetPath = campaignDir.resolve("renders/contact-sheet.mock.json");
        Path metadataPath = campaignDir.resolve("metadata/render-output.json");
        try {
            Files.createDirectories(campaignDir.resolve("renders"));
            Files.createDirectories(campaignDir.resolve("metadata"));
            ShoppingShortsDtos.StoryboardResponse storyboard =
                    objectMapper.readValue(storyboardPath.toFile(), ShoppingShortsDtos.StoryboardResponse.class);
            double duration = renderDuration(campaignDir, storyboard);
            List<Map<String, Object>> sceneSources = storyboard.scenes().stream()
                    .map(scene -> Map.<String, Object>of(
                            "sceneId", scene.sceneId(),
                            "sourceType", scene.sourceType(),
                            "duration", scene.duration(),
                            "caption", scene.caption()))
                    .toList();
            Map<String, Object> base = Map.of(
                    "provider", id(),
                    "mock", true,
                    "status", "COMPLETED",
                    "width", 1080,
                    "height", 1920,
                    "fps", 30,
                    "duration", duration,
                    "sceneSources", sceneSources,
                    "createdAt", Instant.now().toString());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(draftPath.toFile(), base);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(finalPath.toFile(), base);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(thumbnailPath.toFile(), Map.of(
                    "provider", id(),
                    "mock", true,
                    "status", "COMPLETED",
                    "width", 1080,
                    "height", 1920,
                    "title", storyboard.hook(),
                    "createdAt", Instant.now().toString()));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(contactSheetPath.toFile(), Map.of(
                    "provider", id(),
                    "mock", true,
                    "status", "COMPLETED",
                    "type", "CONTACT_SHEET",
                    "createdAt", Instant.now().toString()));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataPath.toFile(), Map.of(
                    "provider", id(),
                    "status", "COMPLETED",
                    "draftPath", campaignDir.relativize(draftPath).toString(),
                    "finalPath", campaignDir.relativize(finalPath).toString(),
                    "thumbnailPath", campaignDir.relativize(thumbnailPath).toString(),
                    "contactSheetPath", campaignDir.relativize(contactSheetPath).toString(),
                    "duration", duration,
                    "width", 1080,
                    "height", 1920,
                    "createdAt", Instant.now().toString()));
            return new RenderResult(
                    id(),
                    "COMPLETED",
                    campaignDir.relativize(draftPath).toString(),
                    campaignDir.relativize(finalPath).toString(),
                    campaignDir.relativize(thumbnailPath).toString(),
                    campaignDir.relativize(contactSheetPath).toString(),
                    campaignDir.relativize(metadataPath).toString(),
                    duration,
                    1080,
                    1920);
        } catch (IOException e) {
            throw new IllegalStateException("Mock 렌더 산출물을 저장하지 못했어요.", e);
        }
    }

    private double renderDuration(Path campaignDir, ShoppingShortsDtos.StoryboardResponse storyboard) throws IOException {
        Path timingPath = campaignDir.resolve("audio/tts-timing.json");
        if (Files.isRegularFile(timingPath)) {
            Map<?, ?> timing = objectMapper.readValue(timingPath.toFile(), Map.class);
            Object duration = timing.get("duration");
            if (duration instanceof Number number && number.doubleValue() > 0) {
                return number.doubleValue();
            }
        }
        return storyboard.scenes().stream().mapToDouble(ShoppingShortsDtos.StoryboardScene::duration).sum();
    }
}
