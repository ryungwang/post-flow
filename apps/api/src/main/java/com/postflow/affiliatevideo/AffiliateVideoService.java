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
 * 제휴용 <b>짧고 굵은 SNS 창작 광고영상</b>(6초 · Kling 1컷) 생성 — 쇼핑쇼츠의 무거운 다단계와 별개의 린 흐름.
 * Claude가 제품 테마를 <b>재밌고 웃긴 창작 씬</b>(text-to-video)으로 각색하고(제품 자체는 화면에 안 나온다 —
 * 제품 사진을 억지로 움직이면 뭉개지므로), Kling text2video 1컷을 뽑아 <b>그 클립을 그대로</b> S3에 올려
 * 발행용 영상으로 쓴다. 짧은 클립이라 자막·ffmpeg 렌더는 안 한다(공유 prod 박스 보호 — 다운로드·업로드 I/O만).
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
        // 창작 씬(text-to-video)이라 제품 이미지는 필요 없다 — 제품을 보여주는 게 아니라 테마를 창작 씬으로 그린다.
        String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Path dir = campaignDir(userId, jobId);
        try {
            Files.createDirectories(dir);
            AdCopy copy = generateAdCopy(req);
            writeStoryboard(dir, req, copy);
            // imageUrl 없이 mode=TEXT_TO_VIDEO → Kling text2video(제품 이미지 애니메이션이 아니라 창작 씬).
            Map<String, Object> prompt = Map.of(
                    "sceneId", SCENE_ID,
                    "prompt", copy.klingPrompt(),
                    "negativePrompt", copy.negativePrompt(),
                    "duration", 5,
                    "mode", "TEXT_TO_VIDEO");
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
                // 공유 버킷(synub-prod-uploads-haru)은 비공개라 raw publicUrl은 403(검은 화면) — 생태계
                // 정석(office·center)대로 presigned URL 사용. plain <video src>라 CORS 불필요, Meta도 서버사이드로
                // 7일 내 가져감. SigV4 최대 7일(장기 예약 발행은 만료 가능 — 필요 시 발행 시점 재서명으로 보강).
                String publicUrl = storage.presignedUrl(key, java.time.Duration.ofDays(7));
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
                You are a creative director for viral, FUNNY Korean SNS short-video ads (스레드/릴스/쇼츠).
                You INVENT one original, hilarious, scroll-stopping 5-6s SCENE that makes people laugh or go "ㅋㅋㅋ"
                and share it. You convey a product's THEME/pain-point through a witty, absurd, or adorable creative
                concept — the actual product NEVER appears on screen. Return ONLY a JSON object. No markdown.
                """;
        String user = """
                Product THEME (context only — the product itself must NOT appear on screen): %s
                %s
                Situation/angle: %s

                Invent ONE creative scene that is genuinely FUNNY and shareable in a single 5s shot. The comedy
                must live in the VISUAL itself (Kling can't do setup→punchline editing) — so make the image absurd,
                exaggerated, or adorably ridiculous ON ITS OWN. Push it to the EXTREME: the more unexpected,
                over-the-top, dramatic and absurd, the better. Mild/cute-but-boring = fail. Aim for "wait, ㅋㅋㅋ 뭐야".

                STRONGLY prefer what actually reads as funny on Kling:
                - An expressive CUTE ANIMAL acting like a human in this theme's situation — this is the money shot
                  (a fluffy dog, a chubby cat, a hamster…). 예) 고양이가 리모컨 들고 에어컨 켜려다 좌절;
                  강아지가 고지서 보고 눈 튀어나올 듯 경악; 햄스터가 선풍기 앞에서 볼살 펄럭.
                - OR extreme cartoonish exaggeration / over-the-top reaction: a person melting like ice cream,
                  a jaw literally dropping, comically dramatic despair-then-relief.
                - Vary the concept each time (don't lock to one animal). Expressive faces, bold action.

                HARD BAN (this = 노잼): a normal realistic person just doing the literal thing (e.g., a man calmly
                looking at a bill at a table). Do NOT depict the situation literally/realistically. Push it to absurd.
                Cinematic, expressive, high quality. Vertical 9:16. Meme energy.

                Produce JSON:
                {
                  "klingPrompt": "ENGLISH text-to-video prompt for the funny creative scene. Vividly describe the subject, the comedic action/turning moment, setting, facial expression, lighting, mood. Cinematic, hilarious, adorable, high quality, believable. NO on-screen text or watermark. 5-6 seconds, vertical 9:16.",
                  "negativePrompt": "on-screen text, captions, watermark, logo, product box, deformed, extra limbs, distorted anatomy, blurry, low quality, creepy, uncanny",
                  "caption": "짧은 한국어 훅 한 줄(최대 16자, 선택). 이모지 금지."
                }
                Rules: keep it brand-safe and tasteful, no offensive content. The actual product must NOT be shown.
                Korean caption only in the 'caption' field.
                """.formatted(
                req.productName(),
                StringUtils.hasText(req.features()) ? "Features(theme hint): " + req.features() : "",
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
