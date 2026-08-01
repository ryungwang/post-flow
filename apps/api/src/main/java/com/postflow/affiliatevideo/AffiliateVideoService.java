package com.postflow.affiliatevideo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postflow.ai.LLMProvider;
import com.postflow.ai.ModelTier;
import com.postflow.ai.dto.GenerationRequest;
import com.postflow.ai.dto.GenerationResult;
import com.postflow.shoppingshorts.ShoppingShortsDtos;
import com.postflow.shoppingshorts.ShoppingShortsRenderProvider;
import com.postflow.shoppingshorts.ShoppingShortsVideoGenerationProvider;
import com.postflow.shoppingshorts.ShoppingShortsVideoGenerationProvider.SceneGenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 제휴용 <b>짧고 굵은 SNS 광고영상</b>(6초 · Kling 1컷) 생성 — 쇼핑쇼츠의 무거운 다단계 파이프라인과 별개의
 * 독립 린 흐름. Claude가 시네마틱 Kling 프롬프트 + 훅 자막을 만들고, Kling image-to-video 1컷을 뽑아
 * (제품 이미지 기반이라 제품이 안 망가진다), 훅 자막을 크게 오버레이해 렌더한다. SNS는 음소거 자동재생이라
 * 내레이션 없이 자막이 전달을 담당 → 무음 트랙만 넣는다. Kling·렌더 provider가 설정된 경우에만 동작.
 */
@Service
public class AffiliateVideoService {

    private static final Logger log = LoggerFactory.getLogger(AffiliateVideoService.class);
    private static final String SCENE_ID = "scene-01";
    private static final double SECONDS = 6.0;

    private final LLMProvider llm;
    private final ObjectMapper mapper;
    private final ObjectProvider<ShoppingShortsVideoGenerationProvider> videoProvider;
    private final ObjectProvider<ShoppingShortsRenderProvider> renderProvider;
    private final com.postflow.storage.StorageService storage;
    private final String ffmpegPath;
    private final Path root;

    public AffiliateVideoService(LLMProvider llm, ObjectMapper mapper,
                                 ObjectProvider<ShoppingShortsVideoGenerationProvider> videoProvider,
                                 ObjectProvider<ShoppingShortsRenderProvider> renderProvider,
                                 com.postflow.storage.StorageService storage,
                                 @Value("${shopping-shorts.ffmpeg.path:ffmpeg}") String ffmpegPath) {
        this.llm = llm;
        this.mapper = mapper;
        this.videoProvider = videoProvider;
        this.renderProvider = renderProvider;
        this.storage = storage;
        this.ffmpegPath = ffmpegPath;
        this.root = Path.of(System.getProperty("user.dir"), "var", "affiliate-videos");
    }

    /** 광고영상 생성 시작 — Claude 프롬프트 → Kling 제출. jobId를 돌려주고, 진행은 status로 폴링한다. */
    public AffiliateVideoDtos.SubmitResponse submit(Long userId, AffiliateVideoDtos.SubmitRequest req) {
        ShoppingShortsVideoGenerationProvider kling = videoProvider.getIfAvailable();
        if (kling == null) {
            throw new IllegalStateException("영상 생성이 설정되지 않았어요. (SHOPPING_SHORTS_VIDEO_PROVIDER=kling + Kling 키 필요)");
        }
        if (!StringUtils.hasText(req.imageUrl())) {
            throw new IllegalArgumentException("제품 이미지 URL이 필요해요. (네이버 검색/업로드 이미지)");
        }
        String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Path dir = campaignDir(userId, jobId);
        try {
            Files.createDirectories(dir);
            AdCopy copy = generateAdCopy(req);
            writeStoryboard(dir, req, copy);
            Map<String, Object> prompt = Map.of(
                    "sceneId", SCENE_ID,
                    "prompt", copy.klingPrompt(),
                    "negativePrompt", copy.negativePrompt(),
                    "duration", 5,
                    "imageUrl", req.imageUrl(),
                    "mode", "IMAGE_TO_VIDEO");
            SceneGenerationResult result = kling.generateScenes(dir, List.of(prompt));
            String status = result.jobs().isEmpty() ? "FAILED" : result.jobs().getFirst().status();
            return new AffiliateVideoDtos.SubmitResponse(jobId, status, copy.caption());
        } catch (IOException e) {
            // 작업 디렉터리 생성/스토리보드 기록 등 파일 IO 실패(prod 쓰기 권한·경로 등). 원인을 노출해 진단.
            log.warn("광고영상 작업 생성 실패(IO) user {} job {} dir {}: {}", userId, jobId, dir, e.toString());
            throw new IllegalStateException("광고영상 작업 생성에 실패했어요. (" + e.getMessage() + ")", e);
        } catch (RuntimeException e) {
            // Kling 제출·카피 생성 등 런타임 실패도 원인을 남긴다(원 메시지는 그대로 전달).
            log.warn("광고영상 작업 생성 실패 user {} job {}: {}", userId, jobId, e.toString());
            throw e;
        }
    }

    /** 진행 상태 폴링 — Kling 완료되면 다운로드 → (무음) → 렌더 → READY(+영상). */
    public AffiliateVideoDtos.StatusResponse status(Long userId, String jobId) {
        ShoppingShortsVideoGenerationProvider kling = videoProvider.getIfAvailable();
        ShoppingShortsRenderProvider render = renderProvider.getIfAvailable();
        if (kling == null || render == null) {
            throw new IllegalStateException("영상 생성/렌더가 설정되지 않았어요.");
        }
        Path dir = campaignDir(userId, jobId);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("작업을 찾을 수 없어요.");
        }
        Path finalPath = dir.resolve("renders/final.mp4");
        Path urlFile = dir.resolve("renders/public-url.txt");
        try {
            if (Files.isRegularFile(urlFile)) { // 이미 업로드됨 — 공개 URL 재사용
                return new AffiliateVideoDtos.StatusResponse("READY", null, Files.readString(urlFile).trim());
            }
            if (!Files.isRegularFile(finalPath)) {
                Path resultMp4 = dir.resolve("scenes").resolve(SCENE_ID).resolve("result.mp4");
                if (!Files.isRegularFile(resultMp4)) {
                    SceneGenerationResult polled = kling.pollScenes(dir);
                    String s = polled.jobs().isEmpty() ? "PROCESSING" : polled.jobs().getFirst().status();
                    if ("FAILED".equals(s)) {
                        String err = polled.jobs().isEmpty() ? null : polled.jobs().getFirst().errorMessage();
                        return new AffiliateVideoDtos.StatusResponse("FAILED", err, null);
                    }
                    if (!"COMPLETED".equals(s)) {
                        return new AffiliateVideoDtos.StatusResponse("PROCESSING", null, null);
                    }
                    kling.downloadScenes(dir); // result-url → result.mp4
                    if (!Files.isRegularFile(resultMp4)) {
                        return new AffiliateVideoDtos.StatusResponse("PROCESSING", null, null);
                    }
                }
                ensureSilentAudio(dir);
                render.render(dir); // Kling 1컷 + 훅 자막 오버레이 → final.mp4
            }
            // 공개 스토리지에 업로드 → 미리보기 + 발행 media_url 로 쓸 공개 URL
            String key = "affiliate-videos/" + userId + "/" + jobId + ".mp4";
            try (java.io.InputStream in = Files.newInputStream(finalPath)) {
                storage.upload(key, in, Files.size(finalPath), "video/mp4");
            }
            String publicUrl = storage.publicUrl(key);
            Files.writeString(urlFile, publicUrl);
            return new AffiliateVideoDtos.StatusResponse("READY", null, publicUrl);
        } catch (Exception e) {
            log.warn("광고영상 렌더/업로드 실패 (job {}): {}", jobId, e.getMessage());
            return new AffiliateVideoDtos.StatusResponse("FAILED", e.getMessage(), null);
        }
    }

    /** 완성된 영상 파일 경로(다운로드/미리보기 서빙용). 소유자 검증 후 반환. */
    public Path outputFile(Long userId, String jobId) {
        Path dir = campaignDir(userId, jobId);
        Path finalPath = dir.resolve("renders/final.mp4").normalize();
        if (!finalPath.startsWith(dir) || !Files.isRegularFile(finalPath)) {
            throw new IllegalArgumentException("완성된 영상이 없어요.");
        }
        return finalPath;
    }

    // ── 내부 ──

    private Path campaignDir(Long userId, String jobId) {
        String safeJob = jobId == null ? "" : jobId.replaceAll("[^a-zA-Z0-9]", "");
        if (safeJob.isBlank()) {
            throw new IllegalArgumentException("jobId가 올바르지 않아요.");
        }
        Path base = root.resolve("users").resolve(String.valueOf(userId)).normalize();
        Path dir = base.resolve(safeJob).normalize();
        if (!dir.startsWith(base)) {
            throw new IllegalArgumentException("잘못된 작업 경로입니다.");
        }
        return dir;
    }

    private record AdCopy(String klingPrompt, String negativePrompt, String caption) {
    }

    /** Claude로 시네마틱 Kling 프롬프트(영문) + 훅 자막(한국어) 생성. 사실만, 텍스트/워터마크 금지. */
    private AdCopy generateAdCopy(AffiliateVideoDtos.SubmitRequest req) {
        String system = """
                You are a viral short-form ad director for Korean SNS reels/shorts (스레드/릴스/쇼츠).
                Your job: ONE scroll-stopping 5-6s image-to-video shot that is FUN, punchy, and impossible to
                scroll past — NOT a slow, tasteful premium product pan (that gets ignored).
                Return ONLY a JSON object. No markdown, no prose.
                """;
        String user = """
                Product: %s
                %s
                Hook angle: %s

                This is an image-to-video shot ANCHORED on the product photo — the product must stay the exact
                same shape and color, no deformation. Within that, make it as dynamic, lively and playful as possible.

                Design ONE high-energy moment that stops the thumb in the first 0.5 seconds:
                - Bold KINETIC camera: fast punch-in / snap zoom / quick orbit / whip-pan — never a slow drift.
                - A FUN, exaggerated visualization of THIS product's benefit — pick what fits:
                  cooling/AC → frost bursts, cool-air streams, heat-shimmer melting away, ice crystals;
                  cleaning → grime instantly vanishing (oddly-satisfying); kitchen → sizzling/steam pops;
                  beauty → sparkle/glow bloom. Satisfying, snappy, a little over-the-top and funny is GOOD.
                - Lively energy: light bursts, particles, vivid pops of color, snappy motion. Meme-adjacent
                  playfulness is welcome as long as the product stays believable.

                Produce JSON:
                {
                  "klingPrompt": "ENGLISH image-to-video prompt for THIS exact product. LEAD with the dynamic camera move and the fun benefit-visualization effect, then lighting/mood. Keep the product 100%% faithful. High-energy, scroll-stopping, playful commercial. 5-6 seconds.",
                  "negativePrompt": "slow, boring, static, dull, sleepy, lifeless, text, captions, watermark, logo, extra fingers, deformed product, wrong product shape, color change, blurry, low quality",
                  "caption": "짧고 강한 한국어 훅 자막 한 줄(최대 16자). 재치있고 궁금하게, 스크롤 멈추게. 과장·허위·가격 금지, 이모지 금지."
                }
                Rules: do not invent specs, prices, discounts, or fake results. Korean caption only in the 'caption' field.
                """.formatted(
                req.productName(),
                StringUtils.hasText(req.features()) ? "Features: " + req.features() : "",
                StringUtils.hasText(req.hook()) ? req.hook() : req.productName());

        String raw = "";
        try {
            GenerationResult result = llm.generate(GenerationRequest.builder()
                    .systemPrompt(system).prompt(user)
                    .maxTokens(700).tier(ModelTier.STANDARD).cacheHint(false).build());
            raw = result.text() == null ? "" : result.text().trim();
            int a = raw.indexOf('{');
            int b = raw.lastIndexOf('}');
            JsonNode n = mapper.readTree(a >= 0 && b > a ? raw.substring(a, b + 1) : raw);
            String kling = n.path("klingPrompt").asText("");
            String neg = n.path("negativePrompt").asText(
                    "text, watermark, logo, deformed product, color change, blurry, low quality");
            String cap = n.path("caption").asText("");
            if (!StringUtils.hasText(kling)) {
                throw new IllegalStateException("Kling 프롬프트가 비었어요.");
            }
            return new AdCopy(kling, neg, cap);
        } catch (Exception e) {
            // 원본 응답을 로그에 남겨 진단 가능하게(모델이 JSON 안 준 경우 등). 명확한 영상용 메시지로 던진다.
            log.warn("광고 카피 생성/파싱 실패: {} | raw={}", e.getMessage(),
                    raw.length() > 300 ? raw.substring(0, 300) + "…" : raw);
            throw new IllegalStateException("광고 카피 생성에 실패했어요. 다시 시도해 주세요. (" + e.getMessage() + ")", e);
        }
    }

    private void writeStoryboard(Path dir, AffiliateVideoDtos.SubmitRequest req, AdCopy copy) throws IOException {
        ShoppingShortsDtos.StoryboardScene scene = new ShoppingShortsDtos.StoryboardScene(
                SCENE_ID, 1, "광고", SECONDS, "AI_VIDEO", List.of(req.imageUrl()),
                copy.klingPrompt(), "", "dynamic punch-in", "", "", copy.caption(), "", "",
                copy.klingPrompt(), copy.negativePrompt(), true);
        ShoppingShortsDtos.StoryboardResponse storyboard = new ShoppingShortsDtos.StoryboardResponse(
                "affiliate", "ad", req.productName(), "cinematic-ad", copy.caption(),
                (int) SECONDS, List.of(scene), null, null);
        mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("storyboard.json").toFile(), storyboard);
    }

    /** SNS는 음소거 자동재생 → 무음 트랙만 넣어 렌더가 audio를 요구하는 문제를 통과시킨다. */
    private void ensureSilentAudio(Path dir) throws IOException, InterruptedException {
        Path audio = dir.resolve("audio/narration.m4a");
        if (Files.isRegularFile(audio)) {
            return;
        }
        Files.createDirectories(audio.getParent());
        Process p = new ProcessBuilder(ffmpegPath, "-y",
                "-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo",
                "-t", String.valueOf((int) SECONDS + 1),
                "-c:a", "aac", "-b:a", "96k", audio.toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) {
            throw new IllegalStateException("무음 트랙 생성 실패: " + out);
        }
    }
}
