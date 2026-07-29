package com.postflow.shoppingshorts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "shopping-shorts.tts.provider", havingValue = "openai")
public class OpenAiShoppingShortsTtsProvider implements ShoppingShortsTtsProvider {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String endpoint;
    private final String model;
    private final String voice;
    private final String ffprobePath;

    public OpenAiShoppingShortsTtsProvider(
            ObjectMapper objectMapper,
            @Value("${shopping-shorts.tts.openai.api-key:${SHOPPING_SHORTS_OPENAI_API_KEY:${OPENAI_API_KEY:}}}") String apiKey,
            @Value("${shopping-shorts.tts.openai.endpoint:${SHOPPING_SHORTS_OPENAI_TTS_ENDPOINT:https://api.openai.com/v1/audio/speech}}") String endpoint,
            @Value("${shopping-shorts.tts.openai.model:${SHOPPING_SHORTS_OPENAI_TTS_MODEL:gpt-4o-mini-tts}}") String model,
            @Value("${shopping-shorts.tts.openai.voice:${SHOPPING_SHORTS_OPENAI_TTS_VOICE:verse}}") String voice,
            @Value("${shopping-shorts.ffprobe.path:${SHOPPING_SHORTS_FFPROBE_PATH:/opt/homebrew/bin/ffprobe}}") String ffprobePath) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.model = model;
        this.voice = voice;
        this.ffprobePath = ffprobePath;
    }

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public TtsResult generate(Path campaignDir) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OpenAI TTS API 키가 없습니다. SHOPPING_SHORTS_OPENAI_API_KEY 또는 OPENAI_API_KEY를 설정하세요.");
        }
        Path narrationPath = campaignDir.resolve("audio/narration.txt");
        Path audioPath = campaignDir.resolve("audio/narration.mp3");
        Path timingPath = campaignDir.resolve("audio/tts-timing.json");
        Path metadataPath = campaignDir.resolve("audio/narration.openai.json");
        if (!Files.isRegularFile(narrationPath)) {
            throw new IllegalStateException("먼저 제작 패키지를 준비해 narration.txt를 생성해야 합니다.");
        }
        try {
            Files.createDirectories(campaignDir.resolve("audio"));
            String narration = Files.readString(narrationPath, StandardCharsets.UTF_8).trim();
            List<String> segments = splitSegments(narration);
            if (segments.isEmpty()) {
                throw new IllegalStateException("나레이션 원고가 비어 있습니다.");
            }

            Map<String, Object> payload = Map.of(
                    "model", model,
                    "voice", voice,
                    "input", narration,
                    "response_format", "mp3");
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMinutes(3))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI TTS 호출 실패: HTTP " + response.statusCode() + " " + responseText(response.body()));
            }
            Files.write(audioPath, response.body());

            double duration = readAudioDuration(audioPath);
            List<Map<String, Object>> timings = buildTimings(segments, duration);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(timingPath.toFile(), Map.of(
                    "provider", id(),
                    "model", model,
                    "voice", voice,
                    "duration", duration,
                    "segments", timings,
                    "createdAt", Instant.now().toString()));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataPath.toFile(), Map.of(
                    "provider", id(),
                    "model", model,
                    "voice", voice,
                    "status", "COMPLETED",
                    "audioPath", campaignDir.relativize(audioPath).toString(),
                    "timingPath", campaignDir.relativize(timingPath).toString(),
                    "createdAt", Instant.now().toString()));

            return new TtsResult(
                    id(),
                    model,
                    "COMPLETED",
                    campaignDir.relativize(narrationPath).toString(),
                    campaignDir.relativize(audioPath).toString(),
                    campaignDir.relativize(timingPath).toString(),
                    segments.size(),
                    duration);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("OpenAI TTS 음성 생성에 실패했어요.", e);
        }
    }

    private List<String> splitSegments(String narration) {
        return narration.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<Map<String, Object>> buildTimings(List<String> segments, double totalDuration) {
        double safeDuration = totalDuration > 0 ? totalDuration : segments.stream().mapToDouble(this::estimateDuration).sum();
        double totalWeight = segments.stream().mapToDouble(this::estimateDuration).sum();
        double cursor = 0.0;
        List<Map<String, Object>> timings = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            String text = segments.get(i);
            double duration = totalWeight <= 0 ? safeDuration / segments.size() : safeDuration * (estimateDuration(text) / totalWeight);
            double end = i == segments.size() - 1 ? safeDuration : cursor + duration;
            timings.add(Map.of(
                    "index", i + 1,
                    "start", cursor,
                    "end", end,
                    "text", text));
            cursor = end;
        }
        return timings;
    }

    private double estimateDuration(String text) {
        int chars = text == null ? 0 : text.codePointCount(0, text.length());
        return Math.max(1.2, chars / 7.0);
    }

    private double readAudioDuration(Path audioPath) throws IOException, InterruptedException {
        if (!Files.isRegularFile(Path.of(ffprobePath))) {
            return 0.0;
        }
        Process process = new ProcessBuilder(
                ffprobePath,
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                audioPath.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int code = process.waitFor();
        if (code != 0 || !StringUtils.hasText(output)) {
            return 0.0;
        }
        try {
            return Double.parseDouble(output);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private String responseText(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        String text = new String(body, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        return text.length() <= 500 ? text : text.substring(0, 500);
    }
}
