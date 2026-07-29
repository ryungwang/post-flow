package com.postflow.shoppingshorts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnExpression("'${shopping-shorts.quality.provider:local-file}' == 'local-file' || '${shopping-shorts.quality.provider:local-file}' == 'mock'")
public class MockShoppingShortsQualityProvider implements ShoppingShortsQualityProvider {
    private static final String DISCLOSURE = "이 포스팅은 쿠팡파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.";

    private final ObjectMapper objectMapper;
    private final String ffmpegPath;
    private final String ffprobePath;

    public MockShoppingShortsQualityProvider(ObjectMapper objectMapper) {
        this(objectMapper, "ffmpeg", "ffprobe");
    }

    @Autowired
    public MockShoppingShortsQualityProvider(
            ObjectMapper objectMapper,
            @Value("${shopping-shorts.ffmpeg.path:ffmpeg}") String ffmpegPath,
            @Value("${shopping-shorts.ffprobe.path:${SHOPPING_SHORTS_FFPROBE_PATH:ffprobe}}") String ffprobePath) {
        this.objectMapper = objectMapper;
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
    }

    @Override
    public String id() {
        return "local-file";
    }

    @Override
    public QualityResult check(Path campaignDir) {
        List<ShoppingShortsDtos.QualityCheckItem> checks = new ArrayList<>();
        Path storyboardPath = campaignDir.resolve("storyboard.json");
        Path renderOutputPath = campaignDir.resolve("metadata/render-output.json");
        Path youtubePath = campaignDir.resolve("metadata/youtube.json");
        Path subtitlePath = campaignDir.resolve("subtitles/subtitle.json");
        Path narrationPath = campaignDir.resolve("audio/narration.txt");
        Path audioPath = findAudioPath(campaignDir);

        checks.add(fileCheck("STORYBOARD_EXISTS", "스토리보드 존재", storyboardPath));
        checks.add(fileCheck("RENDER_OUTPUT_EXISTS", "렌더 산출물 존재", renderOutputPath));
        checks.add(fileCheck("YOUTUBE_METADATA_EXISTS", "유튜브 메타데이터 존재", youtubePath));
        checks.add(fileCheck("SUBTITLE_EXISTS", "자막 파일 존재", subtitlePath));
        checks.add(fileCheck("NARRATION_EXISTS", "나레이션 원고 존재", narrationPath));
        checks.add(audioPath == null
                ? new ShoppingShortsDtos.QualityCheckItem("AUDIO_EXISTS", "나레이션 음성 파일 존재", "FAIL", "audio/narration.* 파일이 없습니다.")
                : fileCheck("AUDIO_EXISTS", "나레이션 음성 파일 존재", audioPath));

        checkRenderSpec(renderOutputPath, checks);
        checkFinalMedia(campaignDir, renderOutputPath, checks);
        checkAudioVideoSync(renderOutputPath, campaignDir.resolve("audio/tts-timing.json"), checks);
        checkYoutubeMetadata(youtubePath, checks);
        checkStoryboard(storyboardPath, checks);

        String status = checks.stream().allMatch(c -> "PASS".equals(c.status())) ? "PASS" : "FAIL";
        Path reportPath = campaignDir.resolve("metadata/quality-check.json");
        try {
            Files.createDirectories(reportPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), Map.of(
                    "provider", id(),
                    "status", status,
                    "checks", checks,
                    "createdAt", Instant.now().toString()));
        } catch (IOException e) {
            throw new IllegalStateException("품질 검증 리포트를 저장하지 못했어요.", e);
        }
        return new QualityResult(id(), status, checks, campaignDir.relativize(reportPath).toString());
    }

    private ShoppingShortsDtos.QualityCheckItem fileCheck(String code, String label, Path path) {
        boolean exists = Files.isRegularFile(path);
        return new ShoppingShortsDtos.QualityCheckItem(
                code,
                label,
                exists ? "PASS" : "FAIL",
                exists ? "파일 확인됨" : "파일이 없습니다: " + path.getFileName());
    }

    private Path findAudioPath(Path campaignDir) {
        List<String> candidates = List.of(
                "audio/narration.aiff",
                "audio/narration.wav",
                "audio/narration.mp3",
                "audio/narration.m4a",
                "audio/narration.aac");
        for (String candidate : candidates) {
            Path path = campaignDir.resolve(candidate).normalize();
            if (path.startsWith(campaignDir) && Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    private void checkRenderSpec(Path renderOutputPath, List<ShoppingShortsDtos.QualityCheckItem> checks) {
        if (!Files.isRegularFile(renderOutputPath)) {
            return;
        }
        try {
            Map<?, ?> render = objectMapper.readValue(renderOutputPath.toFile(), Map.class);
            int width = number(render.get("width"));
            int height = number(render.get("height"));
            double duration = decimal(render.get("duration"));
            checks.add(new ShoppingShortsDtos.QualityCheckItem(
                    "VIDEO_RATIO",
                    "9:16 해상도",
                    width == 1080 && height == 1920 ? "PASS" : "FAIL",
                    width + "x" + height));
            checks.add(new ShoppingShortsDtos.QualityCheckItem(
                    "VIDEO_DURATION",
                    "영상 길이",
                    duration >= 10 && duration <= 35 ? "PASS" : "FAIL",
                    "%.1f초".formatted(duration)));
            String finalPath = string(render.get("finalPath"));
            checks.add(passFail(
                    "FINAL_MP4_EXISTS",
                    "최종 MP4 파일 존재",
                    StringUtils.hasText(finalPath) && Files.isRegularFile(renderOutputPath.getParent().getParent().resolve(finalPath).normalize()),
                    StringUtils.hasText(finalPath) ? finalPath : "finalPath 없음"));
        } catch (IOException e) {
            checks.add(new ShoppingShortsDtos.QualityCheckItem("RENDER_OUTPUT_PARSE", "렌더 메타데이터 파싱", "FAIL", e.getMessage()));
        }
    }

    private void checkFinalMedia(Path campaignDir, Path renderOutputPath, List<ShoppingShortsDtos.QualityCheckItem> checks) {
        if (!Files.isRegularFile(renderOutputPath)) {
            return;
        }
        try {
            Map<?, ?> render = objectMapper.readValue(renderOutputPath.toFile(), Map.class);
            String finalPathValue = string(render.get("finalPath"));
            if (!StringUtils.hasText(finalPathValue)) {
                return;
            }
            Path finalPath = campaignDir.resolve(finalPathValue).normalize();
            if (!finalPath.startsWith(campaignDir) || !Files.isRegularFile(finalPath) || !finalPath.toString().endsWith(".mp4")) {
                return;
            }
            MediaProbe probe = probe(finalPath);
            checks.add(passFail("VIDEO_STREAM_EXISTS", "비디오 스트림 존재", probe.hasVideo(), "video=" + probe.hasVideo()));
            checks.add(passFail("AUDIO_STREAM_EXISTS", "오디오 스트림 존재", probe.hasAudio(), "audio=" + probe.hasAudio()));
            checks.add(passFail("ACTUAL_VIDEO_RATIO", "실제 9:16 해상도", probe.width() == 1080 && probe.height() == 1920,
                    "%dx%d".formatted(probe.width(), probe.height())));
            checks.add(passFail("ACTUAL_VIDEO_DURATION", "실제 영상 길이", probe.duration() >= 10 && probe.duration() <= 35,
                    "%.1f초".formatted(probe.duration())));

            FreezeProbe freeze = freezeProbe(finalPath);
            double freezeShare = probe.duration() > 0 ? freeze.totalSeconds() / probe.duration() : 1.0;
            checks.add(passFail(
                    "MOTION_ACTIVITY",
                    "정지 화면 과다 여부",
                    freeze.longestSeconds() < 3.0 && freezeShare < 0.35,
                    "longest %.1fs / total %.1fs / %.0f%%".formatted(
                            freeze.longestSeconds(),
                            freeze.totalSeconds(),
                            freezeShare * 100)));

            LoudnessProbe loudness = loudnessProbe(finalPath);
            checks.add(passFail(
                    "AUDIO_LOUDNESS",
                    "쇼츠 음량 기준",
                    loudness.integratedLufs() >= -18.0,
                    loudness.integratedLufs() == null ? "측정 실패" : "%.1f LUFS".formatted(loudness.integratedLufs())));
        } catch (Exception e) {
            checks.add(new ShoppingShortsDtos.QualityCheckItem("MEDIA_PROBE", "실제 MP4 검사", "FAIL", e.getMessage()));
        }
    }

    private void checkYoutubeMetadata(Path youtubePath, List<ShoppingShortsDtos.QualityCheckItem> checks) {
        if (!Files.isRegularFile(youtubePath)) {
            return;
        }
        try {
            Map<?, ?> youtube = objectMapper.readValue(youtubePath.toFile(), Map.class);
            String title = string(youtube.get("title"));
            String description = string(youtube.get("description"));
            String pinnedComment = string(youtube.get("pinnedComment"));
            String affiliateUrl = string(youtube.get("affiliateUrl"));
            checks.add(passFail("YOUTUBE_TITLE", "제목 존재", StringUtils.hasText(title), title));
            checks.add(passFail("AFFILIATE_URL", "파트너스 URL 존재", StringUtils.hasText(affiliateUrl), affiliateUrl));
            checks.add(passFail(
                    "DISCLOSURE_IN_DESCRIPTION",
                    "설명 고지 문구",
                    description.contains(DISCLOSURE),
                    "description"));
            checks.add(passFail(
                    "DISCLOSURE_IN_PINNED_COMMENT",
                    "고정댓글 고지 문구",
                    pinnedComment.contains(DISCLOSURE),
                    "pinnedComment"));
        } catch (IOException e) {
            checks.add(new ShoppingShortsDtos.QualityCheckItem("YOUTUBE_METADATA_PARSE", "유튜브 메타데이터 파싱", "FAIL", e.getMessage()));
        }
    }

    private void checkAudioVideoSync(Path renderOutputPath, Path timingPath, List<ShoppingShortsDtos.QualityCheckItem> checks) {
        if (!Files.isRegularFile(renderOutputPath) || !Files.isRegularFile(timingPath)) {
            return;
        }
        try {
            Map<?, ?> render = objectMapper.readValue(renderOutputPath.toFile(), Map.class);
            Map<?, ?> timing = objectMapper.readValue(timingPath.toFile(), Map.class);
            double videoDuration = decimal(render.get("duration"));
            double audioDuration = decimal(timing.get("duration"));
            double diff = Math.abs(videoDuration - audioDuration);
            checks.add(new ShoppingShortsDtos.QualityCheckItem(
                    "AUDIO_VIDEO_SYNC",
                    "음성/영상 길이 싱크",
                    diff <= 0.75 ? "PASS" : "FAIL",
                    "video %.1fs / audio %.1fs / diff %.1fs".formatted(videoDuration, audioDuration, diff)));
        } catch (IOException e) {
            checks.add(new ShoppingShortsDtos.QualityCheckItem("AUDIO_VIDEO_SYNC_PARSE", "음성/영상 싱크 파싱", "FAIL", e.getMessage()));
        }
    }

    private void checkStoryboard(Path storyboardPath, List<ShoppingShortsDtos.QualityCheckItem> checks) {
        if (!Files.isRegularFile(storyboardPath)) {
            return;
        }
        try {
            ShoppingShortsDtos.StoryboardResponse storyboard =
                    objectMapper.readValue(storyboardPath.toFile(), ShoppingShortsDtos.StoryboardResponse.class);
            checks.add(passFail("CTA_EXISTS", "CTA 존재", StringUtils.hasText(storyboard.youtube().affiliateUrl()), storyboard.youtube().affiliateUrl()));
            checks.add(passFail("SCENE_COUNT", "Scene 수", storyboard.scenes().size() >= 4 && storyboard.scenes().size() <= 7, storyboard.scenes().size() + "개"));
        } catch (IOException e) {
            checks.add(new ShoppingShortsDtos.QualityCheckItem("STORYBOARD_PARSE", "스토리보드 파싱", "FAIL", e.getMessage()));
        }
    }

    private ShoppingShortsDtos.QualityCheckItem passFail(String code, String label, boolean pass, String message) {
        return new ShoppingShortsDtos.QualityCheckItem(code, label, pass ? "PASS" : "FAIL", message);
    }

    private MediaProbe probe(Path videoPath) throws IOException, InterruptedException {
        String output = run(List.of(
                ffprobePath,
                "-v", "error",
                "-show_entries", "stream=codec_type,width,height:format=duration",
                "-of", "json",
                videoPath.toString()));
        Map<?, ?> parsed = objectMapper.readValue(output, Map.class);
        List<?> streams = parsed.get("streams") instanceof List<?> list ? list : List.of();
        boolean hasVideo = false;
        boolean hasAudio = false;
        int width = 0;
        int height = 0;
        for (Object streamValue : streams) {
            if (!(streamValue instanceof Map<?, ?> stream)) {
                continue;
            }
            String type = string(stream.get("codec_type"));
            if ("video".equals(type)) {
                hasVideo = true;
                width = number(stream.get("width"));
                height = number(stream.get("height"));
            }
            if ("audio".equals(type)) {
                hasAudio = true;
            }
        }
        double duration = 0.0;
        if (parsed.get("format") instanceof Map<?, ?> format) {
            duration = decimal(format.get("duration"));
        }
        return new MediaProbe(hasVideo, hasAudio, width, height, duration);
    }

    private FreezeProbe freezeProbe(Path videoPath) throws IOException, InterruptedException {
        String output = run(List.of(
                ffmpegPath,
                "-hide_banner",
                "-nostats",
                "-i", videoPath.toString(),
                "-vf", "freezedetect=n=-60dB:d=2",
                "-an",
                "-f", "null",
                "-"));
        Matcher matcher = Pattern.compile("freeze_duration:\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(output);
        double total = 0.0;
        double longest = 0.0;
        while (matcher.find()) {
            double duration = Double.parseDouble(matcher.group(1));
            total += duration;
            longest = Math.max(longest, duration);
        }
        return new FreezeProbe(total, longest);
    }

    private LoudnessProbe loudnessProbe(Path videoPath) throws IOException, InterruptedException {
        String output = run(List.of(
                ffmpegPath,
                "-hide_banner",
                "-nostats",
                "-i", videoPath.toString(),
                "-af", "ebur128",
                "-f", "null",
                "-"));
        Matcher matcher = Pattern.compile("\\bI:\\s*(-?[0-9]+(?:\\.[0-9]+)?)\\s*LUFS").matcher(output);
        Double lufs = null;
        while (matcher.find()) {
            lufs = Double.parseDouble(matcher.group(1));
        }
        return new LoudnessProbe(lufs);
    }

    private String run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException("command failed: " + String.join(" ", command) + "\n" + output);
        }
        return output;
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private double decimal(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    private record MediaProbe(boolean hasVideo, boolean hasAudio, int width, int height, double duration) {
    }

    private record FreezeProbe(double totalSeconds, double longestSeconds) {
    }

    private record LoudnessProbe(Double integratedLufs) {
    }
}
