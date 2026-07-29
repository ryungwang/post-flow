package com.postflow.shoppingshorts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(name = "shopping-shorts.video.provider", havingValue = "mock", matchIfMissing = true)
public class MockShoppingShortsVideoGenerationProvider implements ShoppingShortsVideoGenerationProvider {
    private final ObjectMapper objectMapper;

    public MockShoppingShortsVideoGenerationProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "local-mock";
    }

    @Override
    public SceneGenerationResult generateScenes(Path campaignDir, List<Map<String, Object>> prompts) {
        List<ShoppingShortsDtos.SceneJob> jobs = new ArrayList<>();
        int submitted = 0;
        int cached = 0;
        for (Map<String, Object> prompt : prompts) {
            String sceneId = String.valueOf(prompt.get("sceneId"));
            Path scenesRoot = campaignDir.resolve("scenes").normalize();
            Path sceneDir = scenesRoot.resolve(safePathSegment(sceneId)).normalize();
            if (!sceneDir.startsWith(scenesRoot)) {
                throw new IllegalArgumentException("잘못된 Scene 경로입니다.");
            }
            Path requestPath = sceneDir.resolve("request.json");
            Path resultPath = sceneDir.resolve("result.mock.json");
            Path jobPath = sceneDir.resolve("job.json");
            boolean exists = Files.isRegularFile(resultPath);
            String status = exists ? "CACHED" : "COMPLETED";
            try {
                Files.createDirectories(sceneDir);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(requestPath.toFile(), prompt);
                if (!exists) {
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(resultPath.toFile(), Map.of(
                            "sceneId", sceneId,
                            "provider", id(),
                            "model", "mock-video-v1",
                            "status", "COMPLETED",
                            "mock", true,
                            "message", "실제 Kling 호출 없이 Scene 결과를 예약한 Mock 산출물입니다.",
                            "createdAt", Instant.now().toString()));
                    submitted++;
                } else {
                    cached++;
                }
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(jobPath.toFile(), Map.of(
                        "sceneId", sceneId,
                        "jobType", "KLING_SCENE_GENERATION",
                        "status", status,
                        "provider", id(),
                        "model", "mock-video-v1",
                        "requestPath", campaignDir.relativize(requestPath).toString(),
                        "resultPath", campaignDir.relativize(resultPath).toString(),
                        "completedAt", Instant.now().toString()));
                jobs.add(new ShoppingShortsDtos.SceneJob(
                        sceneId,
                        status,
                        id(),
                        "mock-video-v1",
                        campaignDir.relativize(requestPath).toString(),
                        campaignDir.relativize(resultPath).toString(),
                        null));
            } catch (IOException e) {
                jobs.add(new ShoppingShortsDtos.SceneJob(
                        sceneId,
                        "FAILED",
                        id(),
                        "mock-video-v1",
                        campaignDir.relativize(requestPath).toString(),
                        campaignDir.relativize(resultPath).toString(),
                        e.getMessage()));
            }
        }
        return new SceneGenerationResult(id(), submitted, cached, jobs);
    }

    @Override
    public SceneGenerationResult pollScenes(Path campaignDir) {
        Path scenesDir = campaignDir.resolve("scenes");
        if (!Files.isDirectory(scenesDir)) {
            return new SceneGenerationResult(id(), 0, 0, List.of());
        }
        List<ShoppingShortsDtos.SceneJob> jobs = new ArrayList<>();
        try (Stream<Path> sceneDirs = Files.list(scenesDir)) {
            sceneDirs.filter(Files::isDirectory).forEach(sceneDir -> {
                String sceneId = sceneDir.getFileName().toString();
                Path requestPath = sceneDir.resolve("request.json");
                Path resultPath = sceneDir.resolve("result.mock.json");
                String status = Files.isRegularFile(resultPath) ? "COMPLETED" : "PENDING";
                jobs.add(new ShoppingShortsDtos.SceneJob(
                        sceneId,
                        status,
                        id(),
                        "mock-video-v1",
                        campaignDir.relativize(requestPath).toString(),
                        campaignDir.relativize(resultPath).toString(),
                        null));
            });
        } catch (IOException e) {
            throw new IllegalStateException("Mock Scene Job 상태를 읽지 못했어요.", e);
        }
        return new SceneGenerationResult(id(), 0, jobs.size(), jobs);
    }

    @Override
    public SceneGenerationResult downloadScenes(Path campaignDir) {
        return pollScenes(campaignDir);
    }

    private String safePathSegment(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isBlank()
                || cleaned.equals(".")
                || cleaned.equals("..")
                || !cleaned.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Scene ID가 파일명으로 사용할 수 없습니다.");
        }
        return cleaned;
    }
}
