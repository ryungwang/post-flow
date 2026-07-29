package com.postflow.shoppingshorts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "shopping-shorts.tts.provider", havingValue = "local-macos")
public class LocalMacosShoppingShortsTtsProvider implements ShoppingShortsTtsProvider {
    private final ObjectMapper objectMapper;
    private final String sayPath;
    private final String voice;
    private final int rate;
    private final String ffprobePath;

    public LocalMacosShoppingShortsTtsProvider(
            ObjectMapper objectMapper,
            @Value("${shopping-shorts.tts.say-path:${SHOPPING_SHORTS_TTS_SAY_PATH:/usr/bin/say}}") String sayPath,
            @Value("${shopping-shorts.tts.voice:${SHOPPING_SHORTS_TTS_VOICE:}}") String voice,
            @Value("${shopping-shorts.tts.rate:${SHOPPING_SHORTS_TTS_RATE:185}}") int rate,
            @Value("${shopping-shorts.ffprobe.path:${SHOPPING_SHORTS_FFPROBE_PATH:/opt/homebrew/bin/ffprobe}}") String ffprobePath) {
        this.objectMapper = objectMapper;
        this.sayPath = sayPath;
        this.voice = voice;
        this.rate = rate;
        this.ffprobePath = ffprobePath;
    }

    @Override
    public String id() {
        return "local-macos-say";
    }

    @Override
    public TtsResult generate(Path campaignDir) {
        Path narrationPath = campaignDir.resolve("audio/narration.txt");
        Path audioPath = campaignDir.resolve("audio/narration.aiff");
        Path timingPath = campaignDir.resolve("audio/tts-timing.json");
        if (!Files.isRegularFile(narrationPath)) {
            throw new IllegalStateException("먼저 제작 패키지를 준비해 narration.txt를 생성해야 합니다.");
        }
        try {
            Files.createDirectories(campaignDir.resolve("audio"));
            String narration = Files.readString(narrationPath);
            List<String> segments = splitSegments(narration);
            if (segments.isEmpty()) {
                throw new IllegalStateException("나레이션 원고가 비어 있습니다.");
            }

            List<String> command = new ArrayList<>();
            command.add(sayPath);
            if (StringUtils.hasText(voice)) {
                command.add("-v");
                command.add(voice.trim());
            }
            if (rate > 0) {
                command.add("-r");
                command.add(String.valueOf(rate));
            }
            command.add("-o");
            command.add(audioPath.toString());
            command.add("-f");
            command.add(narrationPath.toString());
            run(command);

            double duration = readAudioDuration(audioPath);
            List<Map<String, Object>> timings = buildTimings(segments, duration);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(timingPath.toFile(), Map.of(
                    "provider", id(),
                    "model", "macos-say",
                    "voice", StringUtils.hasText(voice) ? voice.trim() : "system-default",
                    "rate", rate,
                    "duration", duration,
                    "segments", timings,
                    "createdAt", Instant.now().toString()));
            return new TtsResult(
                    id(),
                    "macos-say",
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
            throw new IllegalStateException("macOS TTS 음성 생성에 실패했어요.", e);
        }
    }

    private List<String> splitSegments(String narration) {
        return narration.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<Map<String, Object>> buildTimings(List<String> segments, double totalDuration) {
        double totalWeight = segments.stream().mapToDouble(this::estimateDuration).sum();
        double cursor = 0.0;
        List<Map<String, Object>> timings = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            String text = segments.get(i);
            double duration = totalWeight <= 0 ? totalDuration / segments.size() : totalDuration * (estimateDuration(text) / totalWeight);
            double end = i == segments.size() - 1 ? totalDuration : cursor + duration;
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
        return Math.max(1.0, chars / 7.5);
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

    private void run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException("command failed: " + String.join(" ", command) + "\n" + output);
        }
    }
}
