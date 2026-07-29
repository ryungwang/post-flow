package com.postflow.shoppingshorts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "shopping-shorts.tts.provider", havingValue = "mock", matchIfMissing = true)
public class MockShoppingShortsTtsProvider implements ShoppingShortsTtsProvider {
    private final ObjectMapper objectMapper;

    public MockShoppingShortsTtsProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "local-mock";
    }

    @Override
    public TtsResult generate(Path campaignDir) {
        Path narrationPath = campaignDir.resolve("audio/narration.txt");
        Path audioPath = campaignDir.resolve("audio/narration.wav");
        Path metadataPath = campaignDir.resolve("audio/narration.mock.json");
        Path timingPath = campaignDir.resolve("audio/tts-timing.json");
        if (!Files.isRegularFile(narrationPath)) {
            throw new IllegalStateException("먼저 제작 패키지를 준비해 narration.txt를 생성해야 합니다.");
        }
        try {
            Files.createDirectories(campaignDir.resolve("audio"));
            String narration = Files.readString(narrationPath);
            List<String> segments = splitSegments(narration);
            List<Map<String, Object>> timings = new ArrayList<>();
            double cursor = 0.0;
            for (int i = 0; i < segments.size(); i++) {
                String text = segments.get(i);
                double duration = estimateDuration(text);
                timings.add(Map.of(
                        "index", i + 1,
                        "start", cursor,
                        "end", cursor + duration,
                        "text", text));
                cursor += duration + 0.25;
            }
            writeSilentWav(audioPath, cursor);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataPath.toFile(), Map.of(
                    "provider", id(),
                    "model", "mock-tts-v1",
                    "status", "COMPLETED",
                    "mock", true,
                    "message", "실제 음성 대신 무음 WAV를 생성했습니다.",
                    "createdAt", Instant.now().toString()));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(timingPath.toFile(), Map.of(
                    "provider", id(),
                    "model", "mock-tts-v1",
                    "voice", "mock-ko",
                    "speed", 1.0,
                    "duration", cursor,
                    "segments", timings));
            return new TtsResult(
                    id(),
                    "mock-tts-v1",
                    "COMPLETED",
                    campaignDir.relativize(narrationPath).toString(),
                    campaignDir.relativize(audioPath).toString(),
                    campaignDir.relativize(timingPath).toString(),
                    segments.size(),
                    cursor);
        } catch (IOException e) {
            throw new IllegalStateException("Mock TTS 산출물을 저장하지 못했어요.", e);
        }
    }

    private List<String> splitSegments(String narration) {
        return narration.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private double estimateDuration(String text) {
        int chars = text == null ? 0 : text.codePointCount(0, text.length());
        return Math.max(1.2, Math.min(5.0, chars / 7.5));
    }

    private void writeSilentWav(Path path, double durationSeconds) throws IOException {
        int sampleRate = 16_000;
        int channels = 1;
        int bitsPerSample = 16;
        int samples = Math.max(sampleRate, (int) Math.ceil(durationSeconds * sampleRate));
        int dataSize = samples * channels * (bitsPerSample / 8);
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes());
        header.putInt(36 + dataSize);
        header.put("WAVE".getBytes());
        header.put("fmt ".getBytes());
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) channels);
        header.putInt(sampleRate);
        header.putInt(sampleRate * channels * (bitsPerSample / 8));
        header.putShort((short) (channels * (bitsPerSample / 8)));
        header.putShort((short) bitsPerSample);
        header.put("data".getBytes());
        header.putInt(dataSize);

        byte[] data = new byte[44 + dataSize];
        System.arraycopy(header.array(), 0, data, 0, 44);
        Files.write(path, data);
    }
}
