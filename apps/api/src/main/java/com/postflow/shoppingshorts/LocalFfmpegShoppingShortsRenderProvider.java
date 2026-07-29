package com.postflow.shoppingshorts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "shopping-shorts.render.provider", havingValue = "local-ffmpeg")
public class LocalFfmpegShoppingShortsRenderProvider implements ShoppingShortsRenderProvider {
    private static final double TRANSITION_SECONDS = 0.25;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String ffmpegPath;
    private final String fontPath;

    public LocalFfmpegShoppingShortsRenderProvider(
            ObjectMapper objectMapper,
            @Value("${shopping-shorts.ffmpeg.path:ffmpeg}") String ffmpegPath,
            @Value("${shopping-shorts.ffmpeg.font-path:/System/Library/Fonts/AppleSDGothicNeo.ttc}") String fontPath) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.ffmpegPath = ffmpegPath;
        this.fontPath = fontPath;
    }

    @Override
    public String id() {
        return "local-ffmpeg";
    }

    @Override
    public RenderResult render(Path campaignDir) {
        Path storyboardPath = campaignDir.resolve("storyboard.json");
        if (!Files.isRegularFile(storyboardPath)) {
            throw new IllegalStateException("먼저 스토리보드를 생성해야 합니다.");
        }
        try {
            Files.createDirectories(campaignDir.resolve("renders"));
            Files.createDirectories(campaignDir.resolve("metadata"));
            ShoppingShortsDtos.StoryboardResponse storyboard =
                    objectMapper.readValue(storyboardPath.toFile(), ShoppingShortsDtos.StoryboardResponse.class);
            Path sceneList = campaignDir.resolve("renders/scenes.txt");
            List<Double> sceneDurations = sceneDurations(campaignDir, storyboard);
            List<Path> sceneClips = renderSceneClips(campaignDir, storyboard, sceneDurations);
            writeConcatList(sceneList, sceneClips);

            Path draftPath = campaignDir.resolve("renders/draft.mp4");
            Path finalPath = campaignDir.resolve("renders/final.mp4");
            Path thumbnailPath = campaignDir.resolve("renders/thumbnail.png");
            Path contactSheetPath = campaignDir.resolve("renders/contact-sheet.jpg");
            Path metadataPath = campaignDir.resolve("metadata/render-output.json");
            Path audioPath = findAudioPath(campaignDir);
            if (audioPath == null) {
                throw new IllegalStateException("먼저 TTS를 생성해 audio/narration.* 음성 파일을 만들어야 합니다.");
            }

            renderDraftWithTransitions(sceneClips, sceneDurations, draftPath);
            // 최종 mux는 draft 비디오를 그대로 복사(-c:v copy) — 3번째 재인코딩을 없애 화질 손실 방지.
            run(List.of(ffmpegPath, "-y",
                    "-i", draftPath.toString(),
                    "-i", audioPath.toString(),
                    "-filter_complex", "[1:a]apad,loudnorm=I=-14:TP=-1.5:LRA=11[a]",
                    "-map", "0:v:0",
                    "-map", "[a]",
                    "-c:v", "copy",
                    "-c:a", "aac", "-b:a", "192k",
                    "-shortest",
                    "-movflags", "+faststart",
                    finalPath.toString()));
            run(List.of(ffmpegPath, "-y", "-ss", "00:00:01", "-i", finalPath.toString(),
                    "-frames:v", "1", thumbnailPath.toString()));
            writeContactSheet(finalPath, contactSheetPath);

            double duration = sceneDurations.stream().mapToDouble(Double::doubleValue).sum();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataPath.toFile(), Map.ofEntries(
                    Map.entry("provider", id()),
                    Map.entry("status", "COMPLETED"),
                    Map.entry("draftPath", campaignDir.relativize(draftPath).toString()),
                    Map.entry("finalPath", campaignDir.relativize(finalPath).toString()),
                    Map.entry("thumbnailPath", campaignDir.relativize(thumbnailPath).toString()),
                    Map.entry("contactSheetPath", campaignDir.relativize(contactSheetPath).toString()),
                    Map.entry("audioPath", campaignDir.relativize(audioPath).toString()),
                    Map.entry("audioMuxed", true),
                    Map.entry("duration", duration),
                    Map.entry("width", 1080),
                    Map.entry("height", 1920),
                    Map.entry("createdAt", Instant.now().toString())));

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
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("FFmpeg 렌더링에 실패했어요.", e);
        }
    }

    private List<Path> renderSceneClips(Path campaignDir, ShoppingShortsDtos.StoryboardResponse storyboard, List<Double> sceneDurations)
            throws IOException, InterruptedException {
        List<Path> clips = new ArrayList<>();
        for (int i = 0; i < storyboard.scenes().size(); i++) {
            ShoppingShortsDtos.StoryboardScene scene = storyboard.scenes().get(i);
            double sceneDuration = sceneDurations.get(i);
            double renderDuration = sceneDuration + (i < storyboard.scenes().size() - 1 ? TRANSITION_SECONDS : 0.0);
            Path rendersDir = campaignDir.resolve("renders").normalize();
            String sceneFileName = safePathSegment(scene.sceneId());
            Path clipPath = rendersDir.resolve("%s.mp4".formatted(sceneFileName)).normalize();
            Path imagePath = rendersDir.resolve("%s.png".formatted(sceneFileName)).normalize();
            if (!clipPath.startsWith(rendersDir) || !imagePath.startsWith(rendersDir)) {
                throw new IllegalArgumentException("잘못된 Scene 파일 경로입니다.");
            }
            String bg = scene.requiresAiGeneration() ? "0x102a43" : switch (scene.sourceType()) {
                case "TEXT_CARD" -> "0x111827";
                case "COMPOSITE" -> "0x263238";
                default -> "0x172554";
            };
            Path aiVideoPath = campaignDir.resolve("scenes").resolve(safePathSegment(scene.sceneId())).resolve("result.mp4").normalize();
            boolean aiReady = scene.requiresAiGeneration()
                    && aiVideoPath.startsWith(campaignDir) && Files.isRegularFile(aiVideoPath);
            if (aiReady) {
                Path overlayPath = rendersDir.resolve("%s-overlay.png".formatted(sceneFileName)).normalize();
                writeOverlayImage(scene, overlayPath);
                renderVideoScene(aiVideoPath, overlayPath, clipPath, renderDuration);
            } else {
                // AI 영상이 아직 없으면(mock/미생성/다운로드 전) throw하지 않고 정적 카드로 폴백 —
                // 파이프라인이 끝까지 돌아 미리보기 영상이 나온다(Kling 생성·다운로드 후 다시 렌더하면 실제 영상 반영).
                writeSceneImage(campaignDir, scene, imagePath, bg);
                renderStillScene(imagePath, clipPath, renderDuration, scene, i);
            }
            clips.add(clipPath);
        }
        return clips;
    }

    private void renderDraftWithTransitions(List<Path> sceneClips, List<Double> sceneDurations, Path draftPath)
            throws IOException, InterruptedException {
        if (sceneClips.isEmpty()) {
            throw new IllegalStateException("렌더링할 Scene 클립이 없습니다.");
        }
        if (sceneClips.size() == 1) {
            // 단일 클립은 이미 인코딩돼 있으니 복사(재인코딩 없음).
            run(List.of(ffmpegPath, "-y", "-i", sceneClips.getFirst().toString(),
                    "-c:v", "copy", "-an", draftPath.toString()));
            return;
        }

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        for (Path clip : sceneClips) {
            command.add("-i");
            command.add(clip.toString());
        }

        StringBuilder filter = new StringBuilder();
        for (int i = 0; i < sceneClips.size(); i++) {
            filter.append("[%d:v]settb=AVTB,setpts=PTS-STARTPTS,fps=30,format=yuv420p[v%d];".formatted(i, i));
        }

        double accumulatedDuration = sceneDurations.getFirst() + TRANSITION_SECONDS;
        String previous = "v0";
        for (int i = 1; i < sceneClips.size(); i++) {
            double offset = Math.max(0.1, accumulatedDuration - TRANSITION_SECONDS);
            String output = i == sceneClips.size() - 1 ? "vout" : "x%d".formatted(i);
            filter.append("[%s][v%d]xfade=transition=fade:duration=%.3f:offset=%.3f[%s];"
                    .formatted(previous, i, TRANSITION_SECONDS, offset, output));
            previous = output;
            accumulatedDuration += sceneDurations.get(i) + (i < sceneClips.size() - 1 ? TRANSITION_SECONDS : 0.0) - TRANSITION_SECONDS;
        }

        command.add("-filter_complex");
        command.add(filter.substring(0, filter.length() - 1));
        command.add("-map");
        command.add("[vout]");
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("medium");
        command.add("-crf");
        command.add("18");
        command.add("-profile:v");
        command.add("high");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-r");
        command.add("30");
        command.add("-an");
        command.add(draftPath.toString());
        run(command);
    }

    private List<Double> sceneDurations(Path campaignDir, ShoppingShortsDtos.StoryboardResponse storyboard) throws IOException {
        List<Double> fallback = storyboard.scenes().stream()
                .map(ShoppingShortsDtos.StoryboardScene::duration)
                .toList();
        Path timingPath = campaignDir.resolve("audio/tts-timing.json");
        if (!Files.isRegularFile(timingPath)) {
            return fallback;
        }
        Map<?, ?> timing = objectMapper.readValue(timingPath.toFile(), Map.class);
        Object segmentsValue = timing.get("segments");
        if (!(segmentsValue instanceof List<?> segments) || segments.size() != storyboard.scenes().size()) {
            return fallback;
        }
        List<Double> durations = new ArrayList<>();
        for (Object segmentValue : segments) {
            if (!(segmentValue instanceof Map<?, ?> segment)) {
                return fallback;
            }
            double start = decimal(segment.get("start"));
            double end = decimal(segment.get("end"));
            double duration = end - start;
            if (duration <= 0.5 || duration > 12.0) {
                return fallback;
            }
            durations.add(duration);
        }
        return durations;
    }

    private void renderVideoScene(Path videoPath, Path overlayPath, Path clipPath, double duration)
            throws IOException, InterruptedException {
        run(List.of(ffmpegPath, "-y",
                "-i", videoPath.toString(),
                "-loop", "1", "-i", overlayPath.toString(),
                "-t", "%.3f".formatted(duration),
                "-filter_complex",
                "[0:v]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setpts=PTS-STARTPTS[base];"
                        + "[base][1:v]overlay=0:0,format=yuv420p[v]",
                "-map", "[v]",
                "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-profile:v", "high",
                "-pix_fmt", "yuv420p", "-r", "30",
                "-an", clipPath.toString()));
    }

    private void renderStillScene(Path imagePath, Path clipPath, double duration,
                                  ShoppingShortsDtos.StoryboardScene scene, int sceneIndex)
            throws IOException, InterruptedException {
        int frames = Math.max(45, (int) Math.ceil(duration * 30));
        String motion = stillMotionExpression(scene, sceneIndex, frames);
        // 출력(1080x1920)의 2배로 프리스케일 → zoompan 정수 스텝이 서브픽셀이 되어 켄번스가 매끄럽고,
        // 2x 다운샘플이라 자막·이미지가 선명하다(기존 1240px는 헤드룸 부족 → 덜덜거림·소프트).
        String filter = "scale=2160:-2,"
                + motion
                + "d=%d:s=1080x1920:fps=30,"
                + "format=yuv420p";
        run(List.of(ffmpegPath, "-y",
                "-loop", "1", "-i", imagePath.toString(),
                "-frames:v", String.valueOf(frames),
                "-vf", filter.formatted(frames),
                "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-profile:v", "high",
                "-pix_fmt", "yuv420p", "-r", "30",
                "-an", clipPath.toString()));
    }

    private String stillMotionExpression(ShoppingShortsDtos.StoryboardScene scene, int sceneIndex, int frames) {
        String movement = scene.cameraMovement() == null ? "" : scene.cameraMovement().toLowerCase();
        String zoom = "z='min(zoom+0.0022,1.12)':";
        String x;
        String y;
        int denominator = Math.max(1, frames - 1);
        if (movement.contains("pan") || movement.contains("left") || sceneIndex % 3 == 1) {
            x = "x='(iw-iw/zoom)*(on/%d)':".formatted(denominator);
            y = "y='ih/2-(ih/zoom/2)':";
        } else if (movement.contains("up") || movement.contains("vertical") || sceneIndex % 3 == 2) {
            x = "x='iw/2-(iw/zoom/2)':";
            y = "y='(ih-ih/zoom)*(on/%d)':".formatted(denominator);
        } else {
            x = "x='iw/2-(iw/zoom/2)':";
            y = "y='ih/2-(ih/zoom/2)':";
        }
        return "zoompan=" + zoom + x + y;
    }

    private void writeContactSheet(Path finalPath, Path contactSheetPath) throws IOException, InterruptedException {
        run(List.of(ffmpegPath, "-y",
                "-i", finalPath.toString(),
                "-vf", "fps=1/3,scale=270:480,tile=4x3",
                "-frames:v", "1",
                "-update", "1",
                contactSheetPath.toString()));
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

    private void writeConcatList(Path listPath, List<Path> clips) throws IOException {
        StringBuilder out = new StringBuilder();
        for (Path clip : clips) {
            out.append("file '").append(clip.toAbsolutePath()).append("'\n");
        }
        Files.writeString(listPath, out.toString(), StandardCharsets.UTF_8);
    }

    private void run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException("command failed: " + String.join(" ", command) + "\n" + output);
        }
    }

    private void writeSceneImage(Path campaignDir, ShoppingShortsDtos.StoryboardScene scene, Path imagePath, String bg) throws IOException {
        BufferedImage image = new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(parseColor(bg));
            graphics.fillRect(0, 0, 1080, 1920);

            Font baseFont = loadFont();
            BufferedImage sourceImage = loadSceneSourceImage(campaignDir, scene);
            if (sourceImage != null && !"TEXT_CARD".equals(scene.sourceType())) {
                drawCover(graphics, sourceImage, 0, 0, 1080, 1920);
                // 전체를 균일하게 덮으면 칙칙해진다 → 하단(자막)·상단(라벨)만 그라디언트로 어둡게, 가운데 제품은 밝게.
                graphics.setPaint(new java.awt.GradientPaint(0, 820, new Color(0, 0, 0, 0), 0, 1920, new Color(0, 0, 0, 205)));
                graphics.fillRect(0, 820, 1080, 1100);
                graphics.setPaint(new java.awt.GradientPaint(0, 0, new Color(0, 0, 0, 150), 0, 340, new Color(0, 0, 0, 0)));
                graphics.fillRect(0, 0, 1080, 340);
                if ("COMPOSITE".equals(scene.sourceType())) {
                    drawContain(graphics, sourceImage, 150, 260, 780, 780);
                    drawTextPanel(graphics, baseFont, scene, 80, 1110, 920, 560);
                } else {
                    drawContain(graphics, sourceImage, 90, 260, 900, 900);
                    drawTextPanel(graphics, baseFont, scene, 80, 1230, 920, 430);
                }
            } else {
                graphics.setColor(new Color(167, 243, 208));
                graphics.setFont(baseFont.deriveFont(Font.BOLD, 40f));
                graphics.drawString(safeText(scene.purpose()), 70, 130);

                graphics.setColor(Color.WHITE);
                graphics.setFont(baseFont.deriveFont(Font.BOLD, 74f));
                drawWrapped(graphics, safeText(scene.caption()), 110, 760, 860, 100);

                graphics.setColor(new Color(203, 213, 225));
                graphics.setFont(baseFont.deriveFont(Font.PLAIN, 34f));
                drawWrapped(graphics, safeText(scene.narration()), 110, 1320, 860, 52);
            }

        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", imagePath.toFile());
    }

    private void writeOverlayImage(ShoppingShortsDtos.StoryboardScene scene, Path imagePath) throws IOException {
        BufferedImage image = new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Font baseFont = loadFont();
            drawTextPanel(graphics, baseFont, scene, 70, 1320, 940, 300);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", imagePath.toFile());
    }

    private void drawTextPanel(Graphics2D graphics, Font baseFont, ShoppingShortsDtos.StoryboardScene scene,
                               int x, int y, int width, int height) {
        graphics.setColor(new Color(0, 0, 0, 132));
        graphics.fillRoundRect(x, y, width, height, 34, 34);
        graphics.setColor(Color.WHITE);
        graphics.setFont(baseFont.deriveFont(Font.BOLD, 58f));
        drawWrapped(graphics, impactCaption(scene.caption()), x + 42, y + 86, width - 84, 70);
    }

    private void drawBrandBar(Graphics2D graphics, Font baseFont) {
        graphics.setColor(new Color(255, 255, 255, 72));
        graphics.fillRoundRect(70, 1710, 940, 90, 30, 30);
        graphics.setColor(new Color(241, 245, 249));
        graphics.setFont(baseFont.deriveFont(Font.BOLD, 32f));
        graphics.drawString("post-flow shopping shorts", 110, 1766);
    }

    private BufferedImage loadSceneSourceImage(Path campaignDir, ShoppingShortsDtos.StoryboardScene scene) {
        if (scene.sourceAssetIds() == null || scene.sourceAssetIds().isEmpty()) {
            return null;
        }
        String url = scene.sourceAssetIds().getFirst();
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            URI uri = validatedPublicHttpsUri(url);
            Path assetsDir = campaignDir.resolve("renders/assets").normalize();
            Files.createDirectories(assetsDir);
            Path target = assetsDir.resolve(safePathSegment(scene.sceneId()) + "-source.img").normalize();
            if (!target.startsWith(assetsDir)) {
                return null;
            }
            if (!Files.isRegularFile(target)) {
                HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return null;
                }
                try (InputStream in = response.body()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return ImageIO.read(target.toFile());
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private void drawCover(Graphics2D graphics, BufferedImage source, int x, int y, int width, int height) {
        double scale = Math.max((double) width / source.getWidth(), (double) height / source.getHeight());
        int drawWidth = (int) Math.ceil(source.getWidth() * scale);
        int drawHeight = (int) Math.ceil(source.getHeight() * scale);
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        graphics.drawImage(source, drawX, drawY, drawWidth, drawHeight, null);
    }

    private void drawContain(Graphics2D graphics, BufferedImage source, int x, int y, int width, int height) {
        double scale = Math.min((double) width / source.getWidth(), (double) height / source.getHeight());
        int drawWidth = (int) Math.ceil(source.getWidth() * scale);
        int drawHeight = (int) Math.ceil(source.getHeight() * scale);
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        graphics.setColor(new Color(255, 255, 255, 235));
        graphics.fillRoundRect(drawX - 22, drawY - 22, drawWidth + 44, drawHeight + 44, 44, 44);
        graphics.drawImage(source, drawX, drawY, drawWidth, drawHeight, null);
    }

    private URI validatedPublicHttpsUri(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("이미지 URL은 https만 허용됩니다.");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host) || isPrivateOrLocalHost(host)) {
            throw new IllegalArgumentException("이미지 URL host가 허용되지 않습니다.");
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
        return value.matches("172\\.(1[6-9]|2\\d|3[0-1])\\..*");
    }

    private void drawWrapped(Graphics2D graphics, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics metrics = graphics.getFontMetrics();
        List<String> lines = wrapForMetrics(text, metrics, maxWidth);
        int currentY = y;
        for (String line : lines) {
            graphics.drawString(line, x, currentY);
            currentY += lineHeight;
        }
    }

    private List<String> wrapForMetrics(String text, FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String value = StringUtils.hasText(text) ? text.trim() : "";
        StringBuilder line = new StringBuilder();
        for (int offset = 0; offset < value.length(); ) {
            int cp = value.codePointAt(offset);
            String next = new String(Character.toChars(cp));
            if (!line.isEmpty() && metrics.stringWidth(line + next) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(next);
            offset += Character.charCount(cp);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private Font loadFont() {
        Path path = Path.of(fontPath);
        if (Files.isRegularFile(path)) {
            try {
                return Font.createFont(Font.TRUETYPE_FONT, path.toFile());
            } catch (FontFormatException | IOException ignored) {
                // TTC 폰트처럼 JVM이 직접 열 수 없는 경우 시스템 폰트 fallback을 사용한다.
            }
        }
        return new Font("Apple SD Gothic Neo", Font.PLAIN, 64);
    }

    private Color parseColor(String value) {
        return Color.decode(value.replace("0x", "#"));
    }

    private String safeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        String text = value.trim();
        for (int offset = 0; offset < text.length(); ) {
            int cp = text.codePointAt(offset);
            if (isRenderableTextCodePoint(cp)) {
                out.appendCodePoint(cp);
            }
            offset += Character.charCount(cp);
        }
        return out.toString().replaceAll("\\s{2,}", " ").trim();
    }

    private String impactCaption(String value) {
        // 24자 중간 절단은 자막을 쪼가리로 만든다 → 절단하지 않고 그대로(줄바꿈은 drawWrapped가 처리).
        // 자막은 플래닝/스토리보드에서 이미 짧게 뽑으므로 여기서 자르지 않는다.
        return safeText(value);
    }

    private boolean isRenderableTextCodePoint(int cp) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        return block != Character.UnicodeBlock.EMOTICONS
                && block != Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS
                && block != Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS
                && block != Character.UnicodeBlock.TRANSPORT_AND_MAP_SYMBOLS
                && block != Character.UnicodeBlock.SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS
                && block != Character.UnicodeBlock.SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A
                && block != Character.UnicodeBlock.DINGBATS
                && cp != 0xFE0F
                && cp != 0x200D;
    }

    private double decimal(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private String safePathSegment(String value) {
        String cleaned = safeText(value);
        if (!StringUtils.hasText(cleaned)
                || cleaned.equals(".")
                || cleaned.equals("..")
                || !cleaned.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Scene ID가 파일명으로 사용할 수 없습니다.");
        }
        return cleaned;
    }
}
