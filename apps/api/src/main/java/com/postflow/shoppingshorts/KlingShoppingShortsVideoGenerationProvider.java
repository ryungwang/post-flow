package com.postflow.shoppingshorts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(name = "shopping-shorts.video.provider", havingValue = "kling")
public class KlingShoppingShortsVideoGenerationProvider implements ShoppingShortsVideoGenerationProvider {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String accessKey;
    private final String secretKey;
    private final String model;
    private final String mode;
    private final String textToVideoPath;
    private final String imageToVideoPath;
    private final String taskStatusPath;
    private final String textToVideoStatusPath;
    private final String imageToVideoStatusPath;
    private final ShoppingShortsCostSafety costSafety;

    public KlingShoppingShortsVideoGenerationProvider(
            ObjectMapper objectMapper,
            ShoppingShortsCostSafety costSafety,
            @Value("${shopping-shorts.kling.base-url:${KLING_BASE_URL:https://api.klingai.com}}") String baseUrl,
            @Value("${shopping-shorts.kling.api-key:${KLING_API_KEY:}}") String apiKey,
            @Value("${shopping-shorts.kling.access-key:${KLING_ACCESS_KEY:}}") String accessKey,
            @Value("${shopping-shorts.kling.secret-key:${KLING_SECRET_KEY:}}") String secretKey,
            @Value("${shopping-shorts.kling.model:${KLING_MODEL:kling-v2.1}}") String model,
            @Value("${shopping-shorts.kling.mode:${KLING_MODE:std}}") String mode,
            @Value("${shopping-shorts.kling.text-to-video-path:${KLING_TEXT_TO_VIDEO_PATH:/v1/videos/text2video}}") String textToVideoPath,
            @Value("${shopping-shorts.kling.image-to-video-path:${KLING_IMAGE_TO_VIDEO_PATH:/v1/videos/image2video}}") String imageToVideoPath,
            @Value("${shopping-shorts.kling.task-status-path:${KLING_TASK_STATUS_PATH:}}") String taskStatusPath,
            @Value("${shopping-shorts.kling.text-to-video-status-path:${KLING_TEXT_TO_VIDEO_STATUS_PATH:/v1/videos/text2video/{taskId}}}") String textToVideoStatusPath,
            @Value("${shopping-shorts.kling.image-to-video-status-path:${KLING_IMAGE_TO_VIDEO_STATUS_PATH:/v1/videos/image2video/{taskId}}}") String imageToVideoStatusPath) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.costSafety = costSafety;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.model = model;
        this.mode = mode;
        this.textToVideoPath = textToVideoPath;
        this.imageToVideoPath = imageToVideoPath;
        this.taskStatusPath = taskStatusPath;
        this.textToVideoStatusPath = textToVideoStatusPath;
        this.imageToVideoStatusPath = imageToVideoStatusPath;
    }

    @Override
    public String id() {
        return "kling";
    }

    @Override
    public SceneGenerationResult generateScenes(Path campaignDir, List<Map<String, Object>> prompts) {
        costSafety.requireLiveApiEnabled(id(), "scene-generation-submit");
        requireConfigured();
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
            Path resultPath = sceneDir.resolve("result.mp4");
            Path submitPath = sceneDir.resolve("submit-response.json");
            Path jobPath = sceneDir.resolve("job.json");
            if (Files.isRegularFile(resultPath)) {
                cached++;
                jobs.add(new ShoppingShortsDtos.SceneJob(
                        sceneId, "CACHED", id(), model,
                        campaignDir.relativize(requestPath).toString(),
                        campaignDir.relativize(resultPath).toString(), null));
                continue;
            }
            try {
                Files.createDirectories(sceneDir);
                Map<String, Object> requestBody = buildRequest(prompt);
                boolean imageToVideo = StringUtils.hasText((String) requestBody.get("image"));
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(requestPath.toFile(), requestBody);
                Map<String, Object> submitResponse = submit(requestBody, imageToVideo);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(submitPath.toFile(), submitResponse);
                String taskId = extractTaskId(submitResponse);
                Map<String, Object> job = new LinkedHashMap<>();
                job.put("sceneId", sceneId);
                job.put("jobType", "KLING_SCENE_GENERATION");
                job.put("status", "SUBMITTED");
                job.put("provider", id());
                job.put("model", model);
                job.put("taskId", taskId);
                job.put("taskKind", imageToVideo ? "image2video" : "text2video");
                job.put("requestPath", campaignDir.relativize(requestPath).toString());
                job.put("submitResponsePath", campaignDir.relativize(submitPath).toString());
                job.put("submittedAt", Instant.now().toString());
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(jobPath.toFile(), job);
                submitted++;
                jobs.add(new ShoppingShortsDtos.SceneJob(
                        sceneId, "SUBMITTED", id(), model,
                        campaignDir.relativize(requestPath).toString(),
                        campaignDir.relativize(submitPath).toString(), null));
            } catch (Exception e) {
                writeFailedJob(campaignDir, sceneDir, sceneId, requestPath, submitPath, jobPath, e);
                jobs.add(new ShoppingShortsDtos.SceneJob(
                        sceneId, "FAILED", id(), model,
                        campaignDir.relativize(requestPath).toString(),
                        campaignDir.relativize(submitPath).toString(), e.getMessage()));
            }
        }
        return new SceneGenerationResult(id(), submitted, cached, jobs);
    }

    @Override
    public SceneGenerationResult pollScenes(Path campaignDir) {
        costSafety.requireLiveApiEnabled(id(), "scene-generation-poll");
        requireConfigured();
        Path scenesDir = campaignDir.resolve("scenes");
        if (!Files.isDirectory(scenesDir)) {
            return new SceneGenerationResult(id(), 0, 0, List.of());
        }
        List<ShoppingShortsDtos.SceneJob> jobs = new ArrayList<>();
        try (Stream<Path> sceneDirs = Files.list(scenesDir)) {
            sceneDirs.filter(Files::isDirectory).forEach(sceneDir -> jobs.add(pollScene(campaignDir, sceneDir)));
        } catch (IOException e) {
            throw new IllegalStateException("Kling Scene Job 목록을 읽지 못했어요.", e);
        }
        return new SceneGenerationResult(id(), 0, 0, jobs);
    }

    @Override
    public SceneGenerationResult downloadScenes(Path campaignDir) {
        costSafety.requireLiveApiEnabled(id(), "scene-generation-download");
        Path scenesDir = campaignDir.resolve("scenes");
        if (!Files.isDirectory(scenesDir)) {
            return new SceneGenerationResult(id(), 0, 0, List.of());
        }
        List<ShoppingShortsDtos.SceneJob> jobs = new ArrayList<>();
        int downloaded = 0;
        int cached = 0;
        try (Stream<Path> sceneDirs = Files.list(scenesDir)) {
            List<Path> dirs = sceneDirs.filter(Files::isDirectory).toList();
            for (Path sceneDir : dirs) {
                DownloadOutcome outcome = downloadScene(campaignDir, sceneDir);
                jobs.add(outcome.job());
                if ("COMPLETED".equals(outcome.job().status())) {
                    downloaded++;
                } else if ("CACHED".equals(outcome.job().status())) {
                    cached++;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Kling Scene 결과 다운로드 목록을 읽지 못했어요.", e);
        }
        return new SceneGenerationResult(id(), downloaded, cached, jobs);
    }

    private DownloadOutcome downloadScene(Path campaignDir, Path sceneDir) {
        String sceneId = sceneDir.getFileName().toString();
        Path requestPath = sceneDir.resolve("request.json");
        Path resultUrlPath = sceneDir.resolve("result-url.txt");
        Path resultPath = sceneDir.resolve("result.mp4");
        Path jobPath = sceneDir.resolve("job.json");
        if (Files.isRegularFile(resultPath)) {
            return new DownloadOutcome(new ShoppingShortsDtos.SceneJob(
                    sceneId,
                    "CACHED",
                    id(),
                    model,
                    campaignDir.relativize(requestPath).toString(),
                    campaignDir.relativize(resultPath).toString(),
                    null));
        }
        try {
            if (!Files.isRegularFile(resultUrlPath)) {
                return new DownloadOutcome(new ShoppingShortsDtos.SceneJob(
                        sceneId,
                        "PROCESSING",
                        id(),
                        model,
                        campaignDir.relativize(requestPath).toString(),
                        campaignDir.relativize(resultUrlPath).toString(),
                        "아직 result-url.txt가 없습니다. Scene 상태 확인을 먼저 실행해 주세요."));
            }
            String url = Files.readString(resultUrlPath, StandardCharsets.UTF_8).trim();
            if (!StringUtils.hasText(url)) {
                throw new IllegalStateException("Kling 결과 URL이 비어 있습니다.");
            }
            downloadToFile(url, resultPath);
            updateDownloadJob(campaignDir, jobPath, resultPath);
            return new DownloadOutcome(new ShoppingShortsDtos.SceneJob(
                    sceneId,
                    "COMPLETED",
                    id(),
                    model,
                    campaignDir.relativize(requestPath).toString(),
                    campaignDir.relativize(resultPath).toString(),
                    null));
        } catch (Exception e) {
            return new DownloadOutcome(new ShoppingShortsDtos.SceneJob(
                    sceneId,
                    "FAILED",
                    id(),
                    model,
                    campaignDir.relativize(requestPath).toString(),
                    campaignDir.relativize(resultPath).toString(),
                    e.getMessage()));
        }
    }

    private ShoppingShortsDtos.SceneJob pollScene(Path campaignDir, Path sceneDir) {
        String sceneId = sceneDir.getFileName().toString();
        Path requestPath = sceneDir.resolve("request.json");
        Path jobPath = sceneDir.resolve("job.json");
        Path pollPath = sceneDir.resolve("poll-response.json");
        Path resultUrlPath = sceneDir.resolve("result-url.txt");
        try {
            Map<String, Object> job = objectMapper.readValue(jobPath.toFile(), new TypeReference<>() {});
            String taskId = String.valueOf(job.getOrDefault("taskId", ""));
            if (!StringUtils.hasText(taskId)) {
                throw new IllegalStateException("Kling taskId가 없습니다.");
            }
            Map<String, Object> response = poll(taskId, isImageToVideoJob(job, requestPath));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(pollPath.toFile(), response);
            String status = normalizeStatus(extractStatus(response));
            String videoUrl = extractVideoUrl(response);
            if ("COMPLETED".equals(status) && StringUtils.hasText(videoUrl)) {
                Files.writeString(resultUrlPath, videoUrl, StandardCharsets.UTF_8);
                job.put("resultUrlPath", campaignDir.relativize(resultUrlPath).toString());
            }
            job.put("status", status);
            job.put("lastPolledAt", Instant.now().toString());
            job.put("pollResponsePath", campaignDir.relativize(pollPath).toString());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jobPath.toFile(), job);
            return new ShoppingShortsDtos.SceneJob(
                    sceneId,
                    status,
                    id(),
                    model,
                    campaignDir.relativize(requestPath).toString(),
                    StringUtils.hasText(videoUrl)
                            ? campaignDir.relativize(resultUrlPath).toString()
                            : campaignDir.relativize(pollPath).toString(),
                    null);
        } catch (Exception e) {
            return new ShoppingShortsDtos.SceneJob(
                    sceneId,
                    "FAILED",
                    id(),
                    model,
                    campaignDir.relativize(requestPath).toString(),
                    campaignDir.relativize(pollPath).toString(),
                    e.getMessage());
        }
    }

    private Map<String, Object> buildRequest(Map<String, Object> prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("prompt", prompt.getOrDefault("prompt", ""));
        body.put("negative_prompt", prompt.getOrDefault("negativePrompt", ""));
        body.put("duration", Math.max(5, Math.round(((Number) prompt.getOrDefault("duration", 5)).doubleValue())));
        body.put("aspect_ratio", "9:16");
        body.put("mode", mode);
        boolean requiresImage = "IMAGE_TO_VIDEO".equalsIgnoreCase(String.valueOf(prompt.getOrDefault("mode", "")));
        Object imageUrl = prompt.get("imageUrl");
        if (imageUrl instanceof String value && StringUtils.hasText(value)) {
            body.put("image", value);
        } else if (requiresImage) {
            throw new IllegalStateException("Kling image-to-video Scene에 image가 없습니다. 상품 이미지 URL을 먼저 저장하세요.");
        }
        return body;
    }

    private Map<String, Object> submit(Map<String, Object> body, boolean imageToVideo) throws IOException, InterruptedException {
        String endpoint = baseUrl + (imageToVideo ? imageToVideoPath : textToVideoPath);
        URI endpointUri = validatedPublicHttpsUri(endpoint);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpointUri)
                .header("Authorization", "Bearer " + bearerToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {});
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Kling 제출 실패: HTTP " + response.statusCode() + " " + summarizeKlingError(parsed));
        }
        return parsed;
    }

    private Map<String, Object> poll(String taskId, boolean imageToVideo) throws IOException, InterruptedException {
        String configuredPath = StringUtils.hasText(taskStatusPath)
                ? taskStatusPath
                : (imageToVideo ? imageToVideoStatusPath : textToVideoStatusPath);
        String path = configuredPath.replace("{taskId}", taskId).replace("{task_id}", taskId);
        URI endpointUri = validatedPublicHttpsUri(baseUrl + path);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpointUri)
                .header("Authorization", "Bearer " + bearerToken())
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {});
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Kling 상태 확인 실패: HTTP " + response.statusCode() + " " + summarizeKlingError(parsed));
        }
        return parsed;
    }

    private String summarizeKlingError(Map<String, Object> response) {
        Object code = response.get("code");
        Object message = response.get("message");
        if (message == null && response.get("error") instanceof Map<?, ?> error) {
            message = error.get("message");
        }
        String codeText = code == null ? "" : "code=" + code + " ";
        String messageText = message == null ? response.toString() : String.valueOf(message);
        return (codeText + messageText).replaceAll("\\s+", " ").trim();
    }

    private boolean isImageToVideoJob(Map<String, Object> job, Path requestPath) throws IOException {
        Object taskKind = job.get("taskKind");
        if (taskKind instanceof String value && value.equalsIgnoreCase("image2video")) {
            return true;
        }
        if (taskKind instanceof String value && value.equalsIgnoreCase("text2video")) {
            return false;
        }
        if (!Files.isRegularFile(requestPath)) {
            return false;
        }
        Map<String, Object> request = objectMapper.readValue(requestPath.toFile(), new TypeReference<>() {});
        Object imageUrl = request.get("image");
        return imageUrl instanceof String value && StringUtils.hasText(value);
    }

    private void downloadToFile(String url, Path target) throws IOException, InterruptedException {
        URI uri = validatedPublicHttpsUri(url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();
        HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Kling result download failed: HTTP " + response.statusCode());
        }
        Files.createDirectories(target.getParent());
        try (java.io.InputStream in = response.body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private URI validatedPublicHttpsUri(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("Kling 외부 URL은 https만 허용됩니다.");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host) || isPrivateOrLocalHost(host)) {
            throw new IllegalStateException("Kling 외부 URL host가 허용되지 않습니다.");
        }
        return uri;
    }

    private boolean isPrivateOrLocalHost(String host) {
        String value = host.toLowerCase();
        if (value.equals("localhost") || value.endsWith(".localhost") || value.endsWith(".local")) {
            return true;
        }
        if (value.equals("0.0.0.0") || value.startsWith("127.") || value.equals("::1") || value.equals("[::1]")) {
            return true;
        }
        if (value.startsWith("10.") || value.startsWith("192.168.") || value.startsWith("169.254.")) {
            return true;
        }
        if (value.matches("172\\.(1[6-9]|2\\d|3[0-1])\\..*")) {
            return true;
        }
        return false;
    }

    private void updateDownloadJob(Path campaignDir, Path jobPath, Path resultPath) throws IOException {
        Map<String, Object> job = Files.isRegularFile(jobPath)
                ? objectMapper.readValue(jobPath.toFile(), new TypeReference<>() {})
                : new LinkedHashMap<>();
        job.put("status", "COMPLETED");
        job.put("resultPath", campaignDir.relativize(resultPath).toString());
        job.put("downloadedAt", Instant.now().toString());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(jobPath.toFile(), job);
    }

    private String bearerToken() {
        if (StringUtils.hasText(apiKey)) {
            return apiKey.trim();
        }
        Instant now = Instant.now();
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .header().add("alg", "HS256").add("typ", "JWT").and()
                .issuer(accessKey)
                .notBefore(Date.from(now.minusSeconds(5)))
                .expiration(Date.from(now.plusSeconds(1800)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private String extractTaskId(Map<String, Object> response) {
        Object direct = response.get("task_id");
        if (direct == null) {
            direct = response.get("taskId");
        }
        if (direct == null && response.get("data") instanceof Map<?, ?> data) {
            direct = data.get("task_id");
            if (direct == null) {
                direct = data.get("taskId");
            }
        }
        return direct == null ? "" : String.valueOf(direct);
    }

    private String extractStatus(Map<String, Object> response) {
        Object status = response.get("status");
        if (status == null) {
            status = response.get("task_status");
        }
        if (status == null && response.get("data") instanceof Map<?, ?> data) {
            status = data.get("status");
            if (status == null) {
                status = data.get("task_status");
            }
        }
        return status == null ? "" : String.valueOf(status);
    }

    private String normalizeStatus(String raw) {
        String value = raw == null ? "" : raw.toLowerCase();
        if (value.contains("succeed") || value.contains("success") || value.contains("complete")) {
            return "COMPLETED";
        }
        if (value.contains("fail") || value.contains("error")) {
            return "FAILED";
        }
        if (value.contains("submit")) {
            return "SUBMITTED";
        }
        return "PROCESSING";
    }

    private String extractVideoUrl(Map<String, Object> response) {
        Object direct = response.get("video_url");
        if (direct == null) {
            direct = response.get("url");
        }
        if (direct == null && response.get("data") instanceof Map<?, ?> data) {
            direct = data.get("video_url");
            if (direct == null) {
                direct = data.get("url");
            }
            if (direct == null && data.get("task_result") instanceof Map<?, ?> taskResult) {
                direct = firstVideoUrl(taskResult.get("videos"));
            }
        }
        return direct == null ? "" : String.valueOf(direct);
    }

    private Object firstVideoUrl(Object videos) {
        if (videos instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> first) {
            Object url = first.get("url");
            if (url == null) {
                url = first.get("video_url");
            }
            return url;
        }
        return null;
    }

    private void writeFailedJob(Path campaignDir, Path sceneDir, String sceneId, Path requestPath, Path submitPath, Path jobPath, Exception e) {
        try {
            Files.createDirectories(sceneDir);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jobPath.toFile(), Map.of(
                    "sceneId", sceneId,
                    "jobType", "KLING_SCENE_GENERATION",
                    "status", "FAILED",
                    "provider", id(),
                    "model", model,
                    "requestPath", campaignDir.relativize(requestPath).toString(),
                    "submitResponsePath", campaignDir.relativize(submitPath).toString(),
                    "errorMessage", e.getMessage(),
                    "completedAt", Instant.now().toString()));
        } catch (IOException ignored) {
            // keep original failure visible through API response
        }
    }

    private void requireConfigured() {
        if (StringUtils.hasText(apiKey)) {
            return;
        }
        if (!StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)) {
            throw new IllegalStateException("Kling API Key 또는 Access Key/Secret Key가 설정되지 않았습니다.");
        }
        if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Kling Secret Key는 HS256 서명에 충분한 길이여야 합니다.");
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://api.klingai.com";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
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

    private record DownloadOutcome(ShoppingShortsDtos.SceneJob job) {
    }
}
