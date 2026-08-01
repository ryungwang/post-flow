package com.postflow.affiliatevideo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postflow.ai.LLMProvider;
import com.postflow.ai.ModelTier;
import com.postflow.ai.dto.GenerationRequest;
import com.postflow.ai.dto.GenerationResult;
import com.postflow.shoppingshorts.ShoppingShortsDtos;
import com.postflow.shoppingshorts.ShoppingShortsVideoGenerationProvider;
import com.postflow.shoppingshorts.ShoppingShortsVideoGenerationProvider.SceneGenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 제휴용 <b>짧고 굵은 SNS 광고영상</b>(6초 · Kling 1컷) 생성 — 쇼핑쇼츠의 무거운 다단계 파이프라인과 별개의
 * 독립 린 흐름. Claude가 Kling 프롬프트를 만들고, Kling image-to-video 1컷을 뽑아(제품 이미지 기반이라
 * 제품이 안 망가진다), <b>그 클립을 그대로</b> S3에 올려 발행용 영상으로 쓴다. SNS 짧은 클립엔 자막이
 * 불필요하므로 ffmpeg 렌더·자막 오버레이는 하지 않는다(공유 prod 박스 부담 방지 — 다운로드·업로드 I/O만).
 */
@Service
public class AffiliateVideoService {

    private static final Logger log = LoggerFactory.getLogger(AffiliateVideoService.class);
    private static final String SCENE_ID = "scene-01";
    private static final double SECONDS = 6.0;

    private final LLMProvider llm;
    private final ObjectMapper mapper;
    private final ObjectProvider<ShoppingShortsVideoGenerationProvider> videoProvider;
    private final com.postflow.storage.StorageService storage;
    private final Path root;

    // Kling 다운로드·S3 업로드(I/O만, ffmpeg 렌더 없음)는 요청 스레드가 아니라 여기서 돈다 — 폴링 요청이
    // 작업을 붙들어 톰캣 스레드를 고갈시키고 API를 502로 만드는 걸 막는다. 단일 스레드로 동시 처리도 제한.
    private final java.util.concurrent.ExecutorService worker =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "affiliate-video-worker");
                t.setDaemon(true);
                return t;
            });
    private final java.util.Set<String> inProgress = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public AffiliateVideoService(LLMProvider llm, ObjectMapper mapper,
                                 ObjectProvider<ShoppingShortsVideoGenerationProvider> videoProvider,
                                 com.postflow.storage.StorageService storage,
                                 @Value("${affiliate-video.work-dir:}") String workDir) {
        this.llm = llm;
        this.mapper = mapper;
        this.videoProvider = videoProvider;
        this.storage = storage;
        // 중간 작업 파일(스토리보드·씬·렌더)용 로컬 경로. 최종 mp4는 S3(StorageService)로 올린다.
        // prod 컨테이너는 작업 디렉터리(/app)가 읽기전용이라 쓰기 가능한 임시 디렉터리(java.io.tmpdir=/tmp)를 쓴다.
        String base = (workDir == null || workDir.isBlank())
                ? System.getProperty("java.io.tmpdir") : workDir;
        this.root = Path.of(base, "affiliate-videos");
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

    /**
     * 진행 상태 폴링(가볍게, 즉시 반환). Kling 씬이 완료되면 무거운 후처리(다운로드·렌더·업로드)는
     * 백그라운드 워커에 넘기고 바로 PROCESSING을 돌려준다 — 폴링 요청이 렌더를 붙들어 API를 502로 만들지 않게.
     */
    public AffiliateVideoDtos.StatusResponse status(Long userId, String jobId) {
        ShoppingShortsVideoGenerationProvider kling = videoProvider.getIfAvailable();
        if (kling == null) {
            throw new IllegalStateException("영상 생성이 설정되지 않았어요.");
        }
        Path dir = campaignDir(userId, jobId);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("작업을 찾을 수 없어요.");
        }
        Path urlFile = dir.resolve("renders/public-url.txt");
        Path errorFile = dir.resolve("renders/error.txt");
        Path resultMp4 = dir.resolve("scenes").resolve(SCENE_ID).resolve("result.mp4");
        try {
            if (Files.isRegularFile(urlFile)) {
                return new AffiliateVideoDtos.StatusResponse("READY", null, Files.readString(urlFile).trim());
            }
            if (Files.isRegularFile(errorFile)) {
                return new AffiliateVideoDtos.StatusResponse("FAILED", Files.readString(errorFile).trim(), null);
            }
            if (inProgress.contains(jobId)) {
                return new AffiliateVideoDtos.StatusResponse("PROCESSING", null, null); // 백그라운드 후처리 중
            }
            // Kling 씬이 아직이면 가벼운 폴링만. 완료면 후처리를 백그라운드로 넘긴다.
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
            }
            enqueuePostProcess(userId, jobId, dir); // Kling 완료 → 무거운 후처리는 백그라운드
            return new AffiliateVideoDtos.StatusResponse("PROCESSING", null, null);
        } catch (Exception e) {
            // 폴링 자체의 일시 오류는 실패로 확정하지 말고 계속 폴링하게(다음 호출에서 재시도).
            log.warn("광고영상 상태 조회 오류 (job {}): {}", jobId, e.toString());
            return new AffiliateVideoDtos.StatusResponse("PROCESSING", null, null);
        }
    }

    /**
     * Kling 클립을 그대로 SNS 영상으로 쓴다 — 자막 오버레이·ffmpeg 렌더 없이 다운로드→S3 업로드만.
     * (SNS 짧은 클립엔 자막 불필요. ffmpeg를 안 써서 공유 prod 박스에 부담이 없다.) I/O만이라 백그라운드에서 1회 수행.
     */
    private void enqueuePostProcess(Long userId, String jobId, Path dir) {
        if (!inProgress.add(jobId)) {
            return; // 이미 처리 중
        }
        worker.submit(() -> {
            Path resultMp4 = dir.resolve("scenes").resolve(SCENE_ID).resolve("result.mp4");
            Path urlFile = dir.resolve("renders/public-url.txt");
            try {
                if (!Files.isRegularFile(resultMp4)) {
                    videoProvider.getIfAvailable().downloadScenes(dir); // Kling result-url → result.mp4
                }
                Files.createDirectories(urlFile.getParent());
                String key = "affiliate-videos/" + userId + "/" + jobId + ".mp4";
                try (java.io.InputStream in = Files.newInputStream(resultMp4)) {
                    storage.upload(key, in, Files.size(resultMp4), "video/mp4");
                }
                String publicUrl = storage.publicUrl(key);
                Files.writeString(urlFile, publicUrl);
                log.info("광고영상 완료 job {} → {}", jobId, publicUrl);
            } catch (Exception e) {
                log.warn("광고영상 후처리 실패 (job {}): {}", jobId, e.toString());
                writeError(dir, e.getMessage());
            } finally {
                inProgress.remove(jobId);
            }
        });
    }

    /** 후처리 실패를 마커 파일로 남겨 다음 폴링이 FAILED를 반환하게 한다. */
    private void writeError(Path dir, String message) {
        try {
            Path errorFile = dir.resolve("renders/error.txt");
            Files.createDirectories(errorFile.getParent());
            Files.writeString(errorFile, message == null || message.isBlank() ? "렌더/업로드에 실패했어요." : message);
        } catch (IOException ignored) {
            // 마커 기록 실패는 무시 — 다음 폴링에서 다시 시도된다.
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
}
