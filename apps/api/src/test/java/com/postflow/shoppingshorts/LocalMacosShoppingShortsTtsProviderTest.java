package com.postflow.shoppingshorts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalMacosShoppingShortsTtsProviderTest {
    private static final Path SAY = Path.of("/usr/bin/say");
    private static final Path FFPROBE = Path.of("/opt/homebrew/bin/ffprobe");

    @TempDir
    Path tempDir;

    @Test
    void generatesActualAudioAndTimingFiles() throws Exception {
        assumeTrue(Files.isExecutable(SAY), "macOS say is not available");

        Path campaignDir = tempDir.resolve("campaign-tts");
        Files.createDirectories(campaignDir.resolve("audio"));
        Files.writeString(campaignDir.resolve("audio/narration.txt"), "휴대용 선풍기의 핵심 기능을 확인합니다.\n최신 조건은 링크에서 확인하세요.");

        LocalMacosShoppingShortsTtsProvider provider = new LocalMacosShoppingShortsTtsProvider(
                new ObjectMapper(),
                SAY.toString(),
                "",
                185,
                FFPROBE.toString());

        ShoppingShortsTtsProvider.TtsResult result = provider.generate(campaignDir);

        assertThat(result.provider()).isEqualTo("local-macos-say");
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(Files.size(campaignDir.resolve(result.audioPath()))).isGreaterThan(0);
        assertThat(Files.isRegularFile(campaignDir.resolve(result.timingPath()))).isTrue();
        assertThat(result.segmentCount()).isEqualTo(2);
    }
}
