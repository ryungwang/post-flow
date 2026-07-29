package com.postflow.shoppingshorts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalFfmpegShoppingShortsRenderProviderTest {
    private static final Path FFMPEG = Path.of("/opt/homebrew/bin/ffmpeg");
    private static final Path FONT = Path.of("/System/Library/Fonts/AppleSDGothicNeo.ttc");

    @TempDir
    Path tempDir;

    @Test
    void rendersActualMp4AndThumbnail() throws Exception {
        assumeTrue(Files.isExecutable(FFMPEG), "ffmpeg is not installed");
        assumeTrue(Files.isRegularFile(FONT), "Korean font is not available");

        ObjectMapper objectMapper = new ObjectMapper();
        Path campaignDir = tempDir.resolve("campaign-review");
        Files.createDirectories(campaignDir);
        writeSilentWav(campaignDir.resolve("audio/narration.wav"), 8.0);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                campaignDir.resolve("storyboard.json").toFile(),
                storyboard());

        LocalFfmpegShoppingShortsRenderProvider provider =
                new LocalFfmpegShoppingShortsRenderProvider(objectMapper, FFMPEG.toString(), FONT.toString());

        ShoppingShortsRenderProvider.RenderResult result = provider.render(campaignDir);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.width()).isEqualTo(1080);
        assertThat(result.height()).isEqualTo(1920);
        assertThat(Files.size(campaignDir.resolve(result.finalPath()))).isGreaterThan(0);
        assertThat(Files.size(campaignDir.resolve(result.thumbnailPath()))).isGreaterThan(0);
        assertThat(Files.isRegularFile(campaignDir.resolve("metadata/render-output.json"))).isTrue();
        assertThat(Files.readString(campaignDir.resolve("metadata/render-output.json"))).contains("\"audioMuxed\" : true");
    }

    @Test
    void rejectsUnsafeSceneIdBeforeRendering() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Path campaignDir = tempDir.resolve("campaign-unsafe");
        Files.createDirectories(campaignDir);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                campaignDir.resolve("storyboard.json").toFile(),
                new ShoppingShortsDtos.StoryboardResponse(
                        "prod-test",
                        "campaign-unsafe",
                        "테스트 상품 쇼츠",
                        "리뷰형",
                        "구매 전 확인",
                        15,
                        List.of(scene("../escape", 1, "Hook", 2.0, "TEXT_CARD", "경로 탈출 시도")),
                        new ShoppingShortsDtos.YoutubeMetadata(
                                "테스트 상품 쇼츠",
                                "테스트 설명",
                                List.of("#쇼핑쇼츠"),
                                "고정댓글",
                                "https://link.coupang.com/a/test",
                                "고지"),
                        "고지"));

        LocalFfmpegShoppingShortsRenderProvider provider =
                new LocalFfmpegShoppingShortsRenderProvider(objectMapper, FFMPEG.toString(), FONT.toString());

        assertThatThrownBy(() -> provider.render(campaignDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Scene ID");
    }

    private ShoppingShortsDtos.StoryboardResponse storyboard() {
        return new ShoppingShortsDtos.StoryboardResponse(
                "prod-test",
                "campaign-review",
                "테스트 상품 쇼츠",
                "리뷰형",
                "구매 전 이 부분만 확인하세요",
                15,
                List.of(
                        scene("scene-01", 1, "Hook", 2.0, "TEXT_CARD", "구매 전 이 부분만 확인하세요"),
                        scene("scene-02", 2, "Product", 2.0, "ORIGINAL_IMAGE", "대표 이미지로 핵심 외형 확인"),
                        scene("scene-03", 3, "Proof", 2.0, "COMPOSITE", "⚡ 확인된 특징만 정리"),
                        scene("scene-04", 4, "CTA", 2.0, "TEXT_CARD", "최신 조건은 링크에서 확인")),
                new ShoppingShortsDtos.YoutubeMetadata(
                        "테스트 상품 쇼츠",
                        "테스트 설명\n\n이 포스팅은 쿠팡파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.",
                        List.of("#쿠팡파트너스", "#쇼핑쇼츠"),
                        "이 포스팅은 쿠팡파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.",
                        "https://link.coupang.com/a/test",
                        "이 포스팅은 쿠팡파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다."),
                "이 포스팅은 쿠팡파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.");
    }

    private void writeSilentWav(Path path, double durationSeconds) throws Exception {
        Files.createDirectories(path.getParent());
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

    private ShoppingShortsDtos.StoryboardScene scene(String id, int order, String purpose, double duration, String sourceType, String caption) {
        return new ShoppingShortsDtos.StoryboardScene(
                id,
                order,
                purpose,
                duration,
                sourceType,
                List.of(),
                caption,
                "Medium close-up",
                "Ken Burns zoom",
                "Cut",
                "Cut",
                caption,
                caption,
                "",
                "",
                "text, captions, watermark",
                false);
    }
}
