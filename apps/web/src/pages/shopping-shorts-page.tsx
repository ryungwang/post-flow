import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  Clapperboard,
  ClipboardPaste,
  Download,
  FolderOpen,
  FileText,
  ImagePlus,
  Link2,
  Loader2,
  Mic2,
  PackageSearch,
  PanelsTopLeft,
  Play,
  Save,
  Search,
  ShieldCheck,
  TrendingUp,
  Trash2,
  Wand2,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/toast";
import { ApiError } from "@/lib/api";
import { naverApi, type RadarProduct, type RadarResponse } from "@/lib/naver-api";
import {
  shoppingShortsApi,
  type ShoppingShortsCampaignGenerationResponse,
  type ShoppingShortsOutputName,
  type ShoppingShortsProductCaptureRequest,
  type ShoppingShortsProductSummary,
  type ShoppingShortsProductionAssetResponse,
  type ShoppingShortsQualityCheckResponse,
  type ShoppingShortsRenderResponse,
  type ShoppingShortsSceneGenerationResponse,
  type ShoppingShortsStoryboardResponse,
  type ShoppingShortsStatus,
  type ShoppingShortsTtsGenerationResponse,
  type ShoppingShortsValidationResponse,
} from "@/lib/shopping-shorts-api";

const FEATURE_PLACEHOLDER = "예: 600ml 대용량, 물세척 가능 필터, 구성품 3종";
const IMAGE_PLACEHOLDER = "https://image.example.com/product-main.jpg";
const FREE_FLOW_CHECKS = [
  "상품 검증 통과",
  "기획안 3개 생성",
  "스토리보드 장면 생성",
  "렌더 COMPLETED",
  "품질검사 PASS",
];
const TEST_CHECKLISTS = [
  {
    id: "free",
    title: "무료 전체 플로우 테스트",
    cost: "비용 없음",
    checks: ["기획/스토리보드는 mock인지", "TTS Provider가 local-macos-say인지", "최종 MP4가 오디오 포함으로 생성되는지", "다운로드 버튼이 보이는지", "품질검사가 PASS인지"],
  },
  {
    id: "claude-planning",
    title: "실제 1단계: Claude 기획안만",
    cost: "Claude 비용",
    checks: ["기획 Provider가 claude인지", "상품 특징을 과장하지 않는지", "후킹 문구 3개가 서로 다른지", "위험/주의사항이 포함되는지"],
  },
  {
    id: "claude-storyboard",
    title: "실제 2단계: Claude 기획안 + 스토리보드",
    cost: "Claude 비용",
    checks: ["스토리보드 Provider가 claude인지", "장면 순서가 자연스러운지", "자막/나레이션이 상품 근거 안에 있는지", "Kling 프롬프트에 가격/허위문구가 없는지"],
  },
  {
    id: "kling",
    title: "실제 3단계: Kling 영상",
    cost: "Kling 비용",
    checks: ["영상 Provider가 kling인지", "AI Scene 생성 후 Job이 SUBMITTED인지", "상태 확인 후 완료/실패가 보이는지", "Scene 다운로드 결과 파일이 생기는지", "최종 MP4에 음성이 포함되는지"],
  },
];

const fileSafeName = (value: string) => value.trim().replace(/[\\/:*?"<>|]+/g, "-").slice(0, 80) || "shopping-shorts";
const cleanDisplayText = (value: string | undefined, max = 0) => {
  const cleaned = (value ?? "")
    .replace(/[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}\uFE0F\u200D]/gu, "")
    .replace(/\s+/g, " ")
    .trim();
  return max > 0 && cleaned.length > max ? `${cleaned.slice(0, max).trim()}...` : cleaned;
};

export function ShoppingShortsPage() {
  const { show } = useToast();
  const [status, setStatus] = useState<ShoppingShortsStatus | null>(null);
  const [products, setProducts] = useState<ShoppingShortsProductSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deletingProductId, setDeletingProductId] = useState<string | null>(null);
  const [downloadingOutput, setDownloadingOutput] = useState<ShoppingShortsOutputName | null>(null);
  const [workflowLoading, setWorkflowLoading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sceneAutoPoll, setSceneAutoPoll] = useState<{ active: boolean; lastCheckedAt?: string }>({ active: false });
  const [selectedProduct, setSelectedProduct] = useState<ShoppingShortsProductSummary | null>(null);
  const [validation, setValidation] = useState<ShoppingShortsValidationResponse | null>(null);
  const [campaigns, setCampaigns] = useState<ShoppingShortsCampaignGenerationResponse | null>(null);
  const [storyboard, setStoryboard] = useState<ShoppingShortsStoryboardResponse | null>(null);
  const [productionAssets, setProductionAssets] = useState<ShoppingShortsProductionAssetResponse | null>(null);
  const [sceneGeneration, setSceneGeneration] = useState<ShoppingShortsSceneGenerationResponse | null>(null);
  const [ttsGeneration, setTtsGeneration] = useState<ShoppingShortsTtsGenerationResponse | null>(null);
  const [renderResult, setRenderResult] = useState<ShoppingShortsRenderResponse | null>(null);
  const [qualityResult, setQualityResult] = useState<ShoppingShortsQualityCheckResponse | null>(null);
  const [radarCat, setRadarCat] = useState("living");
  const [radarWindow, setRadarWindow] = useState<7 | 30>(7);
  const [radar, setRadar] = useState<RadarResponse | null>(null);
  const [radarLoading, setRadarLoading] = useState(false);
  const radarRequestSeq = useRef(0);

  const [productName, setProductName] = useState("");
  const [brand, setBrand] = useState("");
  const [category, setCategory] = useState("");
  const [price, setPrice] = useState("");
  const [productUrl, setProductUrl] = useState("");
  const [affiliateUrl, setAffiliateUrl] = useState("");
  const [features, setFeatures] = useState("");
  const [description, setDescription] = useState("");
  const [sourceImages, setSourceImages] = useState("");
  const [captureJson, setCaptureJson] = useState("");
  const sceneAutoPollInFlight = useRef(false);

  const imageUrls = useMemo(
    () => sourceImages.split(/\r?\n/).map((v) => v.trim()).filter(Boolean),
    [sourceImages],
  );
  const featureList = useMemo(
    () => features.split(/\r?\n/).map((v) => v.trim()).filter(Boolean),
    [features],
  );
  const canSave = productName.trim().length > 0 && affiliateUrl.trim().length > 0;
  const liveApiLocked = status?.liveApiEnabled === false;
  const planningLocked = liveApiLocked && status?.planningProvider === "claude";
  const storyboardLocked = liveApiLocked && status?.storyboardProvider === "claude";
  const videoLocked = liveApiLocked && status?.videoProvider === "kling";
  const paidProviderLocked = planningLocked || storyboardLocked || videoLocked;
  const currentTestMode = useMemo(() => {
    if (!status) return "free";
    if (status.videoProvider === "kling") return "kling";
    if (status.planningProvider === "claude" && status.storyboardProvider === "claude") return "claude-storyboard";
    if (status.planningProvider === "claude") return "claude-planning";
    return "free";
  }, [status]);
  const sceneJobs = sceneGeneration?.jobs ?? [];
  const hasSceneJobs = sceneJobs.length > 0;
  const hasPendingSceneJobs = sceneJobs.some((job) => ["SUBMITTED", "PROCESSING"].includes(job.status));
  const hasFailedSceneJobs = sceneJobs.some((job) => job.status === "FAILED");
  const canDownloadAiScenes = hasSceneJobs && sceneJobs.every((job) => ["COMPLETED", "CACHED"].includes(job.status));
  const canGenerateAiScenes = !hasSceneJobs || hasFailedSceneJobs;

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [workspaceStatus, savedProducts] = await Promise.all([
        shoppingShortsApi.status(),
        shoppingShortsApi.products(),
      ]);
      setStatus(workspaceStatus);
      setProducts(savedProducts);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "쇼핑쇼츠 작업공간을 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    void loadRadar("living", 7);
  }, []);

  const loadRadar = async (category: string, window: 7 | 30) => {
    const requestSeq = radarRequestSeq.current + 1;
    radarRequestSeq.current = requestSeq;
    setRadarCat(category);
    setRadarWindow(window);
    setRadarLoading(true);
    try {
      const nextRadar = await naverApi.radar(category, window);
      if (radarRequestSeq.current !== requestSeq) return;
      setRadar(nextRadar);
    } catch (e) {
      if (radarRequestSeq.current !== requestSeq) return;
      show(e instanceof ApiError ? e.message : "상품 레이더를 불러오지 못했어요.", "error");
    } finally {
      if (radarRequestSeq.current === requestSeq) {
        setRadarLoading(false);
      }
    }
  };

  const pickRadarProduct = (product: RadarProduct) => {
    setProductName(product.name);
    const label = radar?.categories.find((c) => c.key === product.category)?.label;
    setCategory(label ?? product.category);
    show("레이더 후보를 상품명에 적용했어요. 실제 쿠팡 상품 페이지에서 Extension 추출로 확정해 주세요.", "success");
  };

  const saveProduct = async () => {
    if (!canSave) return;
    setSaving(true);
    setError(null);
    try {
      const payload: ShoppingShortsProductCaptureRequest = {
        productName: productName.trim(),
        brand: brand.trim() || undefined,
        category: category.trim() || undefined,
        price: price.trim() ? Number(price.replaceAll(",", "")) : undefined,
        productUrl: productUrl.trim() || undefined,
        affiliateUrl: affiliateUrl.trim(),
        features: featureList,
        description: description.trim() || undefined,
        sourceImages: imageUrls,
        extractedAt: new Date().toISOString(),
      };
      const res = await shoppingShortsApi.captureProduct(payload);
      setProducts((prev) => [res.product, ...prev.filter((p) => p.productId !== res.product.productId)]);
      setSelectedProduct(res.product);
      setValidation(null);
      setCampaigns(null);
      setStoryboard(null);
      setProductionAssets(null);
      setSceneGeneration(null);
      setTtsGeneration(null);
      setRenderResult(null);
      setQualityResult(null);
      show("상품 원본 데이터를 저장했어요.", "success");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "상품 저장에 실패했어요.");
    } finally {
      setSaving(false);
    }
  };

  const deleteProduct = async (product: ShoppingShortsProductSummary) => {
    const ok = window.confirm(`저장된 상품 "${product.productName}"을 삭제할까요?\n생성된 기획안, 장면, 렌더 파일도 함께 삭제됩니다.`);
    if (!ok) return;
    setDeletingProductId(product.productId);
    setError(null);
    try {
      await shoppingShortsApi.deleteProduct(product.productId);
      setProducts((prev) => prev.filter((p) => p.productId !== product.productId));
      if (selectedProduct?.productId === product.productId) {
        setSelectedProduct(null);
        setValidation(null);
        setCampaigns(null);
        setStoryboard(null);
        setProductionAssets(null);
        setSceneGeneration(null);
        setTtsGeneration(null);
        setRenderResult(null);
        setQualityResult(null);
      }
      show("저장된 상품을 삭제했어요.", "success");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "상품 삭제에 실패했어요.");
    } finally {
      setDeletingProductId(null);
    }
  };

  const applyCaptureJson = () => {
    if (!captureJson.trim()) return;
    try {
      const parsed = JSON.parse(captureJson) as Partial<ShoppingShortsProductCaptureRequest>;
      setProductName(parsed.productName ?? "");
      setBrand(parsed.brand ?? "");
      setCategory(parsed.category ?? "");
      setPrice(parsed.price == null ? "" : String(parsed.price));
      setProductUrl(parsed.productUrl ?? "");
      setAffiliateUrl((current) => parsed.affiliateUrl?.trim() || current);
      setFeatures((parsed.features ?? []).join("\n"));
      setDescription(parsed.description ?? "");
      setSourceImages((parsed.sourceImages ?? []).join("\n"));
      show("Extension 추출 데이터를 적용했어요. 쿠팡파트너스 URL만 확인해 주세요.", "success");
    } catch {
      setError("추출 JSON 형식이 올바르지 않습니다.");
    }
  };

  const validateSelected = async (product: ShoppingShortsProductSummary) => {
    setWorkflowLoading("validate");
    setError(null);
    setSelectedProduct(product);
    setCampaigns(null);
    setStoryboard(null);
    setProductionAssets(null);
    setSceneGeneration(null);
    setTtsGeneration(null);
    setRenderResult(null);
    setQualityResult(null);
    try {
      const res = await shoppingShortsApi.validateProduct(product.productId);
      setValidation(res);
      show(res.valid ? "상품 검증을 통과했어요." : "필수값 보완이 필요해요.", res.valid ? "success" : "error");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "상품 검증에 실패했어요.");
    } finally {
      setWorkflowLoading(null);
    }
  };

  const restoreProductState = async (product: ShoppingShortsProductSummary) => {
    setWorkflowLoading(`restore-${product.productId}`);
    setError(null);
    try {
      const res = await shoppingShortsApi.productState(product.productId);
      setSelectedProduct(res.product);
      setValidation({
        productId: res.product.productId,
        valid: true,
        missingFields: [],
        warnings: [],
        nextStep: res.storyboard ? "저장된 쇼핑쇼츠 작업을 복원했습니다." : "기획안을 생성하거나 선택할 수 있습니다.",
      });
      setCampaigns(res.campaigns ?? null);
      setStoryboard(res.storyboard ?? null);
      setProductionAssets(res.productionAssets ?? null);
      setSceneGeneration(res.sceneGeneration ?? null);
      setTtsGeneration(res.ttsGeneration ?? null);
      setRenderResult(res.renderResult ?? null);
      setQualityResult(res.qualityResult ?? null);
      show(res.renderResult ? "저장된 최종 결과물을 복원했어요." : "저장된 작업 상태를 복원했어요.", "success");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "저장된 작업 상태를 복원하지 못했어요.");
    } finally {
      setWorkflowLoading(null);
    }
  };

  const generateCampaigns = async () => {
    if (!selectedProduct) return;
    setWorkflowLoading("campaigns");
    setError(null);
    setStoryboard(null);
    setProductionAssets(null);
    setSceneGeneration(null);
    setTtsGeneration(null);
    setRenderResult(null);
    setQualityResult(null);
    try {
      const res = await shoppingShortsApi.generateCampaigns(selectedProduct.productId);
      setCampaigns(res);
      setValidation({
        productId: selectedProduct.productId,
        valid: true,
        missingFields: [],
        warnings: validation?.warnings ?? [],
        nextStep: "기획안을 선택할 수 있습니다.",
      });
      show("광고 기획안 3개를 생성했어요.", "success");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "광고 기획안 생성에 실패했어요.");
    } finally {
      setWorkflowLoading(null);
    }
  };

  const selectCampaign = async (campaignId: string) => {
    if (!selectedProduct) return;
    setWorkflowLoading(campaignId);
    setError(null);
    try {
      const res = await shoppingShortsApi.selectCampaign(selectedProduct.productId, campaignId);
      setStoryboard(res);
      setProductionAssets(null);
      setSceneGeneration(null);
      setTtsGeneration(null);
      setRenderResult(null);
      setQualityResult(null);
      show("스토리보드를 저장했어요.", "success");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "기획안 선택에 실패했어요.");
    } finally {
      setWorkflowLoading(null);
    }
  };

  const prepareProductionAssets = async () => {
    if (!selectedProduct || !storyboard) return;
    setWorkflowLoading("assets");
    setError(null);
    try {
      const res = await shoppingShortsApi.prepareProductionAssets(selectedProduct.productId, storyboard.campaignId);
      setProductionAssets(res);
      setSceneGeneration(null);
      setTtsGeneration(null);
      setRenderResult(null);
      setQualityResult(null);
      show("제작 패키지를 준비했어요.", "success");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "제작 패키지 준비에 실패했어요.");
    } finally {
      setWorkflowLoading(null);
    }
  };

  const generateAiScenes = async () => {
    if (!selectedProduct || !storyboard) return;
    setWorkflowLoading("scenes");
    setError(null);
    try {
      const res = await shoppingShortsApi.generateAiScenes(selectedProduct.productId, storyboard.campaignId);
      setSceneGeneration(res);
      show(res.submittedSceneCount > 0 ? "AI Scene 작업을 생성했어요." : "AI Scene이 없어 다음 단계로 넘어갈 수 있어요.", "success");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "AI Scene 생성에 실패했어요.");
    } finally {
      setWorkflowLoading(null);
    }
  };

  const pollAiScenes = useCallback(async (options?: { silent?: boolean }) => {
    if (!selectedProduct || !storyboard) return;
    if (options?.silent) {
      if (sceneAutoPollInFlight.current) return;
      sceneAutoPollInFlight.current = true;
    } else {
      setWorkflowLoading("scene-poll");
    }
    setError(null);
    try {
      const res = await shoppingShortsApi.pollAiScenes(selectedProduct.productId, storyboard.campaignId);
      setSceneGeneration(res);
      setSceneAutoPoll({ active: res.jobs.some((job) => ["SUBMITTED", "PROCESSING"].includes(job.status)), lastCheckedAt: new Date().toLocaleTimeString() });
      if (!options?.silent) {
        show("Scene 상태를 확인했어요.", "success");
      } else if (res.jobs.some((job) => ["COMPLETED", "CACHED"].includes(job.status))) {
        show("Kling Scene 생성이 완료됐어요. 다운로드할 수 있습니다.", "success");
      } else if (res.jobs.some((job) => job.status === "FAILED")) {
        show("Kling Scene 생성 실패가 감지됐어요.", "error");
      }
    } catch (e) {
      if (!options?.silent) {
        setError(e instanceof ApiError ? e.message : "Scene 상태 확인에 실패했어요.");
      }
    } finally {
      if (options?.silent) {
        sceneAutoPollInFlight.current = false;
      } else {
        setWorkflowLoading(null);
      }
    }
  }, [selectedProduct, show, storyboard]);

  useEffect(() => {
    if (!selectedProduct || !storyboard || !hasPendingSceneJobs || videoLocked) {
      setSceneAutoPoll((prev) => (prev.active ? { ...prev, active: false } : prev));
      return;
    }
    setSceneAutoPoll((prev) => ({ ...prev, active: true }));
    const timer = window.setInterval(() => {
      void pollAiScenes({ silent: true });
    }, 20_000);
    return () => window.clearInterval(timer);
  }, [hasPendingSceneJobs, pollAiScenes, selectedProduct, storyboard, videoLocked]);

  const downloadAiScenes = async () => {
    if (!selectedProduct || !storyboard) return;
    setWorkflowLoading("scene-download");
    setError(null);
    try {
      const res = await shoppingShortsApi.downloadAiScenes(selectedProduct.productId, storyboard.campaignId);
      setSceneGeneration(res);
      show("Scene 결과 다운로드 상태를 반영했어요.", "success");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Scene 결과 다운로드에 실패했어요.");
    } finally {
      setWorkflowLoading(null);
    }
  };

  const generateTts = async () => {
    if (!selectedProduct || !storyboard) return;
    setWorkflowLoading("tts");
    setError(null);
    try {
      const res = await shoppingShortsApi.generateTts(selectedProduct.productId, storyboard.campaignId);
      setTtsGeneration(res);
      show("TTS 산출물을 생성했어요.", "success");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "TTS 생성에 실패했어요.");
    } finally {
      setWorkflowLoading(null);
    }
  };

  const renderCampaign = async () => {
    if (!selectedProduct || !storyboard) return;
    setWorkflowLoading("render");
    setError(null);
    try {
      const res = await shoppingShortsApi.renderCampaign(selectedProduct.productId, storyboard.campaignId);
      setRenderResult(res);
      setQualityResult(null);
      show("렌더 산출물을 생성했어요.", "success");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "렌더링에 실패했어요.");
    } finally {
      setWorkflowLoading(null);
    }
  };

  const checkQuality = async () => {
    if (!selectedProduct || !storyboard) return;
    setWorkflowLoading("quality");
    setError(null);
    try {
      const res = await shoppingShortsApi.checkQuality(selectedProduct.productId, storyboard.campaignId);
      setQualityResult(res);
      show(res.status === "PASS" ? "최종 검증을 통과했어요." : "검증 실패 항목이 있어요.", res.status === "PASS" ? "success" : "error");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "품질 검증에 실패했어요.");
    } finally {
      setWorkflowLoading(null);
    }
  };

  const downloadCampaignOutput = async (outputName: ShoppingShortsOutputName, filename: string) => {
    if (!selectedProduct || !storyboard) return;
    setDownloadingOutput(outputName);
    setError(null);
    try {
      const blob = await shoppingShortsApi.downloadOutput(selectedProduct.productId, storyboard.campaignId, outputName);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      show("산출물 다운로드를 시작했어요.", "success");
    } catch (e) {
      setError(e instanceof Error ? e.message : "산출물 다운로드에 실패했어요.");
    } finally {
      setDownloadingOutput(null);
    }
  };

  return (
    <div className="mx-auto w-full max-w-6xl px-6 py-7">
      <div className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-lg bg-gradient-to-tr from-emerald-500 to-cyan-500 text-white">
            <Clapperboard className="size-5" />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">쇼핑쇼츠생성</h1>
            <p className="mt-0.5 text-sm text-muted-foreground">쿠팡파트너스 쇼츠 제작을 위한 별도 작업공간</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Badge variant="success" className="gap-1.5"><ShieldCheck className="size-3.5" /> 격리 API</Badge>
          <Badge variant="info" className="gap-1.5"><Clapperboard className="size-3.5" /> 9:16 쇼츠</Badge>
          <Badge variant="warning" className="gap-1.5"><AlertTriangle className="size-3.5" /> 크롤링 없음</Badge>
          <Badge variant={status?.liveApiEnabled ? "warning" : "success"} className="gap-1.5">
            <ShieldCheck className="size-3.5" /> {status?.liveApiEnabled ? "실 API 허용" : "비용 잠금"}
          </Badge>
        </div>
      </div>

      {loading ? (
        <div className="flex min-h-[40vh] items-center justify-center text-sm text-muted-foreground">
          <Loader2 className="mr-2 size-4 animate-spin" /> 작업공간 확인 중
        </div>
      ) : (
        <div className="grid gap-8 xl:grid-cols-[minmax(0,1fr)_360px]">
          <div className="space-y-8">
            {error && (
              <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {error}
              </div>
            )}
            {paidProviderLocked && (
              <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm text-amber-700">
                실 API 호출이 잠겨 있습니다. 현재 provider 설정 중 비용 가능 항목이 있어 해당 버튼을 막았습니다.
                실제 호출 전에는 서버에서 `SHOPPING_SHORTS_LIVE_API_ENABLED=true`를 명시해야 합니다.
              </div>
            )}

            <Card className="p-5">
              <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h2 className="font-semibold">테스트별 확인표</h2>
                  <p className="mt-0.5 text-sm text-muted-foreground">현재 설정에 맞는 테스트를 강조 표시합니다. 각 테스트에서 아래 항목만 보면 됩니다.</p>
                </div>
                <Badge variant={status?.liveApiEnabled ? "warning" : "success"}>
                  현재 {TEST_CHECKLISTS.find((item) => item.id === currentTestMode)?.title ?? "무료 전체 플로우"}
                </Badge>
              </div>
              <div className="grid gap-3 lg:grid-cols-2">
                {TEST_CHECKLISTS.map((test) => (
                  <div key={test.id} className={`rounded-lg border p-3 ${currentTestMode === test.id ? "border-emerald-500/40 bg-emerald-500/10" : "bg-muted/10"}`}>
                    <div className="mb-2 flex items-center justify-between gap-2">
                      <div className="font-medium">{test.title}</div>
                      <Badge variant={test.cost === "비용 없음" ? "success" : "warning"}>{test.cost}</Badge>
                    </div>
                    <div className="space-y-1.5 text-sm text-muted-foreground">
                      {test.checks.map((check, index) => (
                        <div key={check} className="flex gap-2">
                          <span className="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full bg-background text-[11px]">{index + 1}</span>
                          <span>{check}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {FREE_FLOW_CHECKS.map((check) => (
                  <Badge key={check} variant="muted">{check}</Badge>
                ))}
              </div>
            </Card>

            <Card className="p-5">
              <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h2 className="flex items-center gap-2 font-semibold"><TrendingUp className="size-4" /> 쇼츠 상품 레이더</h2>
                  <p className="mt-0.5 text-sm text-muted-foreground">급상승 후보를 보고 쇼츠로 만들 상품 키워드를 고릅니다. 쿠팡 원본 정보는 Extension으로 확정합니다.</p>
                </div>
                <div className="flex overflow-hidden rounded-md border text-xs">
                  {([7, 30] as const).map((window) => (
                    <button
                      key={window}
                      type="button"
                      onClick={() => loadRadar(radarCat, window)}
                      className={`px-2.5 py-1 ${radarWindow === window ? "bg-foreground text-background" : "hover:bg-muted/50"}`}
                    >
                      최근 {window}일
                    </button>
                  ))}
                </div>
              </div>

              <div className="mb-3 flex flex-wrap gap-1.5">
                {(radar?.categories ?? []).map((category) => (
                  <Button
                    key={category.key}
                    variant={radarCat === category.key ? "default" : "outline"}
                    size="sm"
                    onClick={() => loadRadar(category.key, radarWindow)}
                  >
                    {category.label}
                  </Button>
                ))}
              </div>

              {radar && !radar.dataLab && (
                <p className="mb-3 text-xs text-amber-600">네이버 DataLab 설정이 없으면 검색/쇼핑 상승률은 확인 불가로 표시되고, 계절성과 카테고리 적합도 중심으로 정렬됩니다.</p>
              )}

              {radarLoading ? (
                <p className="flex items-center gap-1.5 text-sm text-muted-foreground"><Loader2 className="size-4 animate-spin" /> 레이더 분석 중</p>
              ) : radar?.products.length ? (
                <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
                  {radar.products.slice(0, 9).map((product) => (
                    <button
                      key={`${product.category}-${product.name}`}
                      type="button"
                      onClick={() => pickRadarProduct(product)}
                      className="rounded-lg border p-3 text-left transition-colors hover:bg-muted/50"
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <div className="font-medium">{product.name}</div>
                          <div className="mt-1 text-xs text-muted-foreground">{radar.categories.find((c) => c.key === product.category)?.label ?? product.category}</div>
                        </div>
                        <Badge variant={product.score >= 80 ? "success" : product.score >= 60 ? "warning" : "muted"}>{product.score}점</Badge>
                      </div>
                      <div className="mt-2 flex items-center gap-1.5 text-xs text-muted-foreground">
                        <Search className="size-3.5" />
                        {product.breakdown.find((b) => b.label === "검색 추이 상승률")?.note ?? "검색 추이 확인 불가"}
                      </div>
                    </button>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">레이더 후보가 없습니다.</p>
              )}
            </Card>

            <Card className="p-5">
              <div className="mb-4 flex items-center justify-between gap-3">
                <div>
                  <h2 className="font-semibold">상품 원본 데이터</h2>
                  <p className="mt-0.5 text-sm text-muted-foreground">지금은 상품명과 쿠팡파트너스 URL만 넣어도 저장됩니다.</p>
                </div>
                <Badge variant="muted">PRODUCT_CAPTURED</Badge>
              </div>

              <div className="mb-4 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-3 py-2.5 text-sm text-muted-foreground">
                <span className="font-medium text-foreground">실제 운영 흐름:</span> 쿠팡 상품 페이지에서 Extension 버튼을 누르면 상품명, 가격, 이미지, 상세 문구는 자동으로 채워집니다. 아래 보완 항목은 추출이 부족할 때만 직접 고치면 됩니다.
              </div>

              <details className="mb-4 rounded-lg border border-dashed p-3">
                <summary className="cursor-pointer text-sm font-medium">Extension 추출 JSON 가져오기</summary>
                <div className="mt-3 space-y-2">
                  <Textarea
                    rows={5}
                    className="font-mono text-xs"
                    value={captureJson}
                    onChange={(e) => setCaptureJson(e.target.value)}
                    placeholder='{"productName":"...", "price":29900, "sourceImages":["..."]}'
                  />
                  <div className="flex items-center justify-between gap-3">
                    <p className="text-xs text-muted-foreground">Extension에서 복사한 JSON을 붙여넣으면 상품 필드가 채워집니다.</p>
                    <Button variant="outline" size="sm" onClick={applyCaptureJson} disabled={!captureJson.trim()} className="shrink-0 gap-1.5">
                      <ClipboardPaste className="size-3.5" />
                      적용
                    </Button>
                  </div>
                </div>
              </details>

              <div className="grid gap-4 md:grid-cols-2">
                <div className="space-y-1.5">
                  <Label>상품명 *</Label>
                  <Input value={productName} onChange={(e) => setProductName(e.target.value)} placeholder="예: 무선 핸디 청소기 XYZ" />
                </div>
                <div className="space-y-1.5">
                  <Label>쿠팡파트너스 URL *</Label>
                  <Input value={affiliateUrl} onChange={(e) => setAffiliateUrl(e.target.value)} placeholder="https://link.coupang.com/a/..." />
                </div>
              </div>

              <details className="mt-4 rounded-lg border border-dashed p-3">
                <summary className="cursor-pointer text-sm font-medium">선택 보완 항목 열기</summary>
                <div className="mt-4 grid gap-4 md:grid-cols-2">
                  <div className="space-y-1.5">
                    <Label>브랜드</Label>
                    <Input value={brand} onChange={(e) => setBrand(e.target.value)} placeholder="브랜드명" />
                  </div>
                  <div className="space-y-1.5">
                    <Label>카테고리</Label>
                    <Input value={category} onChange={(e) => setCategory(e.target.value)} placeholder="생활용품 / 주방 / 뷰티" />
                  </div>
                  <div className="space-y-1.5">
                    <Label>현재 가격</Label>
                    <Input inputMode="numeric" value={price} onChange={(e) => setPrice(e.target.value)} placeholder="29900" />
                  </div>
                  <div className="space-y-1.5">
                    <Label>상품 페이지 URL</Label>
                    <Input value={productUrl} onChange={(e) => setProductUrl(e.target.value)} placeholder="https://www.coupang.com/vp/products/..." />
                  </div>
                </div>

                <div className="mt-4 grid gap-4 lg:grid-cols-2">
                  <div className="space-y-1.5">
                    <Label>확인된 특징</Label>
                    <Textarea rows={5} value={features} onChange={(e) => setFeatures(e.target.value)} placeholder={FEATURE_PLACEHOLDER} />
                  </div>
                  <div className="space-y-1.5">
                    <Label>상품 설명</Label>
                    <Textarea rows={5} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="상세페이지에서 확인한 설명만 입력" />
                  </div>
                </div>

                <div className="mt-4 space-y-1.5">
                  <Label className="flex items-center gap-1.5"><ImagePlus className="size-3.5" /> 이미지 URL</Label>
                  <Textarea rows={4} value={sourceImages} onChange={(e) => setSourceImages(e.target.value)} placeholder={`${IMAGE_PLACEHOLDER}\nhttps://image.example.com/detail-01.jpg`} />
                  <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
                    <span>이미지 {imageUrls.length}개</span>
                    <span>특징 {featureList.length}개</span>
                  </div>
                </div>
              </details>

              <div className="mt-5 flex justify-end">
                <Button onClick={saveProduct} disabled={!canSave || saving} className="gap-2">
                  {saving ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
                  원본 저장
                </Button>
              </div>
            </Card>

            <Card className="p-5">
              <div className="mb-4 flex items-center justify-between gap-3">
                <div>
                  <h2 className="font-semibold">저장된 상품</h2>
                  <p className="mt-0.5 text-sm text-muted-foreground">상품별 원본 데이터는 쇼츠 캠페인에서 재사용됩니다.</p>
                </div>
                <Button variant="outline" size="sm" onClick={load}>새로고침</Button>
              </div>
              {products.length === 0 ? (
                <div className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
                  저장된 상품이 없습니다.
                </div>
              ) : (
                <div className="overflow-hidden rounded-lg border">
                  <table className="w-full text-sm">
                    <thead className="bg-muted/50 text-xs text-muted-foreground">
                      <tr>
                        <th className="px-4 py-3 text-left font-medium">상품</th>
                        <th className="px-4 py-3 text-left font-medium">이미지</th>
                        <th className="px-4 py-3 text-left font-medium">저장 시각</th>
                        <th className="px-4 py-3 text-right font-medium">작업</th>
                      </tr>
                    </thead>
                    <tbody>
                      {products.map((p) => (
                        <tr key={p.productId} className="border-t">
                          <td className="px-4 py-3">
                            <div className="font-medium">{p.productName}</div>
                            <div className="mt-0.5 text-xs text-muted-foreground">{p.brand || "브랜드 없음"} · {p.productId}</div>
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">{p.sourceImageCount}개</td>
                          <td className="px-4 py-3 text-muted-foreground">{new Date(p.updatedAt).toLocaleString()}</td>
                          <td className="px-4 py-3 text-right">
                            <div className="flex justify-end gap-2">
                              <Button variant="outline" size="sm" onClick={() => restoreProductState(p)} disabled={workflowLoading != null || deletingProductId != null}>
                                {workflowLoading === `restore-${p.productId}` ? <Loader2 className="size-3.5 animate-spin" /> : <FolderOpen className="size-3.5" />}
                                이어가기
                              </Button>
                              <Button variant="outline" size="sm" onClick={() => validateSelected(p)} disabled={workflowLoading != null || deletingProductId != null}>
                                {workflowLoading === "validate" && selectedProduct?.productId === p.productId ? <Loader2 className="size-3.5 animate-spin" /> : <CheckCircle2 className="size-3.5" />}
                                시작
                              </Button>
                              <Button variant="ghost" size="sm" onClick={() => deleteProduct(p)} disabled={workflowLoading != null || deletingProductId != null} className="text-destructive hover:text-destructive">
                                {deletingProductId === p.productId ? <Loader2 className="size-3.5 animate-spin" /> : <Trash2 className="size-3.5" />}
                                삭제
                              </Button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>

            {selectedProduct && (
              <Card className="p-5">
                <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <h2 className="font-semibold">제작 워크플로우</h2>
                    <p className="mt-0.5 text-sm text-muted-foreground">{selectedProduct.productName}</p>
                  </div>
                  <div className="flex gap-2">
                    <Button variant="outline" onClick={() => validateSelected(selectedProduct)} disabled={workflowLoading != null} className="gap-2">
                      {workflowLoading === "validate" ? <Loader2 className="size-4 animate-spin" /> : <CheckCircle2 className="size-4" />}
                      1 상품 검증
                    </Button>
                    <Button onClick={generateCampaigns} disabled={workflowLoading != null || validation?.valid === false || planningLocked} className="gap-2">
                      {workflowLoading === "campaigns" ? <Loader2 className="size-4 animate-spin" /> : <Wand2 className="size-4" />}
                      {planningLocked ? "기획안 생성 잠금" : "2 기획안 생성"}
                    </Button>
                  </div>
                </div>

                {validation && (
                  <div className={`mb-4 rounded-lg border px-3 py-2.5 text-sm ${validation.valid ? "border-emerald-500/30 bg-emerald-500/10" : "border-destructive/30 bg-destructive/10"}`}>
                    <div className="font-medium">{validation.valid ? "검증 통과" : "필수값 부족"}</div>
                    <div className="mt-1 text-muted-foreground">{validation.nextStep}</div>
                    {validation.missingFields.length > 0 && (
                      <div className="mt-2 text-destructive">부족한 값: {validation.missingFields.join(", ")}</div>
                    )}
                    {validation.warnings.length > 0 && (
                      <ul className="mt-2 space-y-1 text-muted-foreground">
                        {validation.warnings.map((w) => <li key={w}>- {w}</li>)}
                      </ul>
                    )}
                  </div>
                )}

                {campaigns && (
                  <div className="space-y-4">
                    <div className="rounded-lg border bg-muted/30 p-3">
                      <div className="mb-2 text-sm font-medium">상품 분석</div>
                      <div className="grid gap-3 text-sm md:grid-cols-2">
                        <div>
                          <div className="text-xs text-muted-foreground">추천 스타일</div>
                          <div className="mt-1 flex flex-wrap gap-1.5">
                            {campaigns.productAnalysis.recommendedStyles.map((s) => <Badge key={s} variant="info">{cleanDisplayText(s, 36)}</Badge>)}
                          </div>
                        </div>
                        <div>
                          <div className="text-xs text-muted-foreground">주의 사항</div>
                          <div className="mt-1 text-muted-foreground">{cleanDisplayText(campaigns.productAnalysis.riskNotes.join(" · "), 180)}</div>
                        </div>
                      </div>
                      {(campaigns.productAnalysis.hookCandidates?.length ?? 0) > 0 && (
                        <div className="mt-3">
                          <div className="text-xs text-muted-foreground">3초 훅 후보</div>
                          <div className="mt-1 flex flex-wrap gap-1.5">
                            {campaigns.productAnalysis.hookCandidates?.map((hook) => (
                              <Badge key={hook} variant="secondary">{cleanDisplayText(hook, 15)}</Badge>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>

                    <div className="grid gap-3 lg:grid-cols-3">
                      {campaigns.campaignCandidates.map((c) => (
                        <div key={c.campaignId} className="rounded-lg border p-4">
                          <div className="mb-2 flex items-center justify-between gap-2">
                            <Badge variant="secondary">{cleanDisplayText(c.style, 28)}</Badge>
                            <span className="text-sm font-semibold">{c.recommendationScore}점</span>
                          </div>
                          <h3 className="min-h-[48px] font-semibold leading-6">{cleanDisplayText(c.hook, 34)}</h3>
                          <p className="mt-2 min-h-[72px] overflow-hidden text-sm leading-6 text-muted-foreground">{cleanDisplayText(c.concept, 92)}</p>
                          <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
                            <div className="rounded-md bg-muted/40 p-2">
                              <div className="text-muted-foreground">길이</div>
                              <div className="mt-0.5 font-medium">{c.recommendedDuration}초</div>
                            </div>
                            <div className="rounded-md bg-muted/40 p-2">
                              <div className="text-muted-foreground">AI 장면</div>
                              <div className="mt-0.5 font-medium">{c.estimatedAiSceneCount}개</div>
                            </div>
                          </div>
                          <p className="mt-3 min-h-[60px] overflow-hidden text-xs leading-5 text-muted-foreground">{cleanDisplayText(c.recommendationReason, 96)}</p>
                          <Button type="button" className="mt-4 w-full gap-2" onClick={() => selectCampaign(c.campaignId)} disabled={workflowLoading != null || storyboardLocked}>
                            {workflowLoading === c.campaignId ? <Loader2 className="size-4 animate-spin" /> : <PanelsTopLeft className="size-4" />}
                            {storyboardLocked ? "스토리보드 잠금" : "이 기획 선택"}
                          </Button>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {storyboard && (
                  <div className="mt-5 rounded-lg border p-4">
                    <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <h3 className="font-semibold">{storyboard.title}</h3>
                        <p className="mt-0.5 text-sm text-muted-foreground">{storyboard.duration}초 · {storyboard.style} · {storyboard.scenes.length}개 장면</p>
                      </div>
                      <Badge variant="success">STORYBOARD_CREATED</Badge>
                    </div>
                    <div className="space-y-2">
                      {storyboard.scenes.map((scene) => (
                        <div key={scene.sceneId} className="grid gap-2 rounded-md border bg-muted/20 p-3 text-sm md:grid-cols-[72px_140px_minmax(0,1fr)]">
                          <div className="font-medium">Scene {scene.order}</div>
                          <div className="text-muted-foreground">{scene.sourceType} · {scene.duration}s</div>
                          <div>
                            <div>{scene.caption}</div>
                            {scene.requiresAiGeneration && <div className="mt-1 text-xs text-amber-600">Kling 필요: {scene.klingPrompt}</div>}
                          </div>
                        </div>
                      ))}
                    </div>
                    <div className="mt-4 rounded-md bg-muted/30 p-3 text-sm">
                      <div className="font-medium">YouTube 메타데이터</div>
                      <div className="mt-1 text-muted-foreground">{storyboard.youtube.title}</div>
                      <div className="mt-1 text-xs text-muted-foreground">{storyboard.youtube.hashtags.join(" ")}</div>
                    </div>
                    <div className="mt-4 flex justify-end">
                      <Button onClick={prepareProductionAssets} disabled={workflowLoading != null} className="gap-2">
                        {workflowLoading === "assets" ? <Loader2 className="size-4 animate-spin" /> : <FileText className="size-4" />}
                        3 제작 패키지 준비
                      </Button>
                    </div>
                    {productionAssets && (
                      <div className="mt-4 rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-sm">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="font-medium">제작 패키지 준비 완료</div>
                          <Badge variant="success">비용 {productionAssets.estimatedCostLevel}</Badge>
                        </div>
                        <div className="mt-2 grid gap-2 text-xs sm:grid-cols-3">
                          <div className="rounded-md bg-background/60 p-2">
                            <div className="text-muted-foreground">AI 장면</div>
                            <div className="mt-0.5 font-medium">{productionAssets.aiSceneCount}개</div>
                          </div>
                          <div className="rounded-md bg-background/60 p-2">
                            <div className="text-muted-foreground">자막</div>
                            <div className="mt-0.5 font-medium">{productionAssets.subtitleCount}개</div>
                          </div>
                          <div className="rounded-md bg-background/60 p-2">
                            <div className="text-muted-foreground">다음 단계</div>
                            <div className="mt-0.5 font-medium">Provider 연결</div>
                          </div>
                        </div>
                        <div className="mt-3 text-xs text-muted-foreground">
                          생성 파일: {productionAssets.createdFiles.join(", ")}
                        </div>
                        <div className="mt-4 flex flex-wrap justify-end gap-2">
                          <Button onClick={generateAiScenes} disabled={workflowLoading != null || videoLocked || !canGenerateAiScenes} className="gap-2">
                            {workflowLoading === "scenes" ? <Loader2 className="size-4 animate-spin" /> : <Clapperboard className="size-4" />}
                            {videoLocked ? "AI Scene 잠금" : hasPendingSceneJobs ? "AI Scene 생성 중" : canDownloadAiScenes ? "AI Scene 생성 완료" : "4 AI Scene 생성"}
                          </Button>
                          <Button variant="outline" onClick={() => pollAiScenes()} disabled={workflowLoading != null || videoLocked} className="gap-2">
                            {workflowLoading === "scene-poll" ? <Loader2 className="size-4 animate-spin" /> : <Search className="size-4" />}
                            {videoLocked ? "Scene 상태 잠금" : "5 Scene 상태 확인"}
                          </Button>
                          <Button variant="outline" onClick={downloadAiScenes} disabled={workflowLoading != null || videoLocked || !canDownloadAiScenes} className="gap-2">
                            {workflowLoading === "scene-download" ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
                            {videoLocked ? "Scene 다운로드 잠금" : "6 Scene 다운로드"}
                          </Button>
                          <Button variant="outline" onClick={generateTts} disabled={workflowLoading != null} className="gap-2">
                            {workflowLoading === "tts" ? <Loader2 className="size-4 animate-spin" /> : <Mic2 className="size-4" />}
                            7 TTS 생성
                          </Button>
                          <Button variant="outline" onClick={renderCampaign} disabled={workflowLoading != null || !ttsGeneration} className="gap-2">
                            {workflowLoading === "render" ? <Loader2 className="size-4 animate-spin" /> : <Play className="size-4" />}
                            8 렌더
                          </Button>
                          <Button variant="outline" onClick={checkQuality} disabled={workflowLoading != null || !renderResult} className="gap-2">
                            {workflowLoading === "quality" ? <Loader2 className="size-4 animate-spin" /> : <CheckCircle2 className="size-4" />}
                            9 최종 검증
                          </Button>
                        </div>
                      </div>
                    )}
                    {qualityResult && (
                      <div className={`mt-4 rounded-lg border p-3 text-sm ${qualityResult.status === "PASS" ? "border-emerald-500/30 bg-emerald-500/10" : "border-destructive/30 bg-destructive/10"}`}>
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="font-medium">최종 품질 검증</div>
                          <Badge variant={qualityResult.status === "PASS" ? "success" : "warning"}>{qualityResult.status}</Badge>
                        </div>
                        <div className="mt-3 grid gap-2 md:grid-cols-2">
                          {qualityResult.checks.map((check) => (
                            <div key={check.code} className="rounded-md border bg-background/60 p-2 text-xs">
                              <div className="flex items-center justify-between gap-2">
                                <span className="font-medium">{check.label}</span>
                                <Badge variant={check.status === "PASS" ? "success" : "warning"}>{check.status}</Badge>
                              </div>
                              <div className="mt-1 text-muted-foreground">{check.message}</div>
                            </div>
                          ))}
                        </div>
                        <div className="mt-3 break-all text-xs text-muted-foreground">Report: {qualityResult.reportPath}</div>
                      </div>
                    )}
                    {renderResult && (
                      <div className="mt-4 rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-4 text-sm">
                        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                          <div>
                            <div className="text-base font-semibold">최종 결과물</div>
                            <div className="mt-1 text-muted-foreground">완성된 쇼츠 영상은 아래 버튼으로 바로 받을 수 있습니다.</div>
                          </div>
                          <Badge variant="success">{renderResult.status}</Badge>
                        </div>
                        <div className="mt-4 flex flex-wrap gap-2">
                          <Button
                            onClick={() => downloadCampaignOutput("final", `${fileSafeName(selectedProduct.productName)}-final.mp4`)}
                            disabled={downloadingOutput != null}
                            className="gap-2"
                          >
                            {downloadingOutput === "final" ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
                            최종 영상 MP4 다운로드
                          </Button>
                          <Button
                            variant="outline"
                            onClick={() => downloadCampaignOutput("thumbnail", `${fileSafeName(selectedProduct.productName)}-thumbnail.png`)}
                            disabled={downloadingOutput != null}
                            className="gap-2"
                          >
                            {downloadingOutput === "thumbnail" ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
                            썸네일 다운로드
                          </Button>
                          <Button
                            variant="outline"
                            onClick={() => downloadCampaignOutput("contact-sheet", `${fileSafeName(selectedProduct.productName)}-frames.jpg`)}
                            disabled={downloadingOutput != null}
                            className="gap-2"
                          >
                            {downloadingOutput === "contact-sheet" ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
                            프레임 시트 다운로드
                          </Button>
                          <Button
                            variant="outline"
                            onClick={() => downloadCampaignOutput("draft", `${fileSafeName(selectedProduct.productName)}-draft.mp4`)}
                            disabled={downloadingOutput != null}
                            className="gap-2"
                          >
                            {downloadingOutput === "draft" ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
                            드래프트 다운로드
                          </Button>
                          {qualityResult && (
                            <Button
                              variant="outline"
                              onClick={() => downloadCampaignOutput("quality", `${fileSafeName(selectedProduct.productName)}-quality.json`)}
                              disabled={downloadingOutput != null}
                              className="gap-2"
                            >
                              {downloadingOutput === "quality" ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
                              검수 리포트 다운로드
                            </Button>
                          )}
                        </div>
                        <div className="mt-4 grid gap-2 text-xs sm:grid-cols-3">
                          <div className="rounded-md bg-background/60 p-2">
                            <div className="text-muted-foreground">규격</div>
                            <div className="mt-0.5 font-medium">{renderResult.width}×{renderResult.height}</div>
                          </div>
                          <div className="rounded-md bg-background/60 p-2">
                            <div className="text-muted-foreground">길이</div>
                            <div className="mt-0.5 font-medium">{renderResult.duration.toFixed(1)}초</div>
                          </div>
                          <div className="rounded-md bg-background/60 p-2">
                            <div className="text-muted-foreground">Provider</div>
                            <div className="mt-0.5 font-medium">{renderResult.provider}</div>
                          </div>
                        </div>
                        <div className="mt-3 rounded-md bg-background/60 p-3 text-xs text-muted-foreground">
                          <div className="font-medium text-foreground">저장된 서버 파일</div>
                          <div className="mt-1 break-all">최종 영상: {renderResult.finalPath}</div>
                          <div className="break-all">썸네일: {renderResult.thumbnailPath}</div>
                          <div className="break-all">프레임 시트: {renderResult.contactSheetPath}</div>
                          <div className="break-all">드래프트: {renderResult.draftPath}</div>
                        </div>
                      </div>
                    )}
                    {ttsGeneration && (
                      <div className="mt-4 rounded-lg border p-3 text-sm">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="font-medium">TTS 결과</div>
                          <Badge variant="success">{ttsGeneration.status}</Badge>
                        </div>
                        <div className="mt-2 grid gap-2 text-xs sm:grid-cols-3">
                          <div className="rounded-md bg-muted/40 p-2">
                            <div className="text-muted-foreground">Provider</div>
                            <div className="mt-0.5 font-medium">{ttsGeneration.provider}</div>
                          </div>
                          <div className="rounded-md bg-muted/40 p-2">
                            <div className="text-muted-foreground">문장</div>
                            <div className="mt-0.5 font-medium">{ttsGeneration.segmentCount}개</div>
                          </div>
                          <div className="rounded-md bg-muted/40 p-2">
                            <div className="text-muted-foreground">예상 길이</div>
                            <div className="mt-0.5 font-medium">{ttsGeneration.duration.toFixed(1)}초</div>
                          </div>
                        </div>
                        <div className="mt-3 space-y-1 text-xs text-muted-foreground">
                          <div className="break-all">Audio: {ttsGeneration.audioPath}</div>
                          <div className="break-all">Timing: {ttsGeneration.timingPath}</div>
                        </div>
                      </div>
                    )}
                    {sceneGeneration && (
                      <div className="mt-4 rounded-lg border p-3 text-sm">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="font-medium">Scene Job 결과</div>
                          <Badge variant="info">{sceneGeneration.provider}</Badge>
                        </div>
                        <div className="mt-2 grid gap-2 text-xs sm:grid-cols-3">
                          <div className="rounded-md bg-muted/40 p-2">
                            <div className="text-muted-foreground">신규</div>
                            <div className="mt-0.5 font-medium">{sceneGeneration.submittedSceneCount}개</div>
                          </div>
                          <div className="rounded-md bg-muted/40 p-2">
                            <div className="text-muted-foreground">캐시</div>
                            <div className="mt-0.5 font-medium">{sceneGeneration.cachedSceneCount}개</div>
                          </div>
                          <div className="rounded-md bg-muted/40 p-2">
                            <div className="text-muted-foreground">Job</div>
                            <div className="mt-0.5 font-medium">{sceneGeneration.jobs.length}개</div>
                          </div>
                        </div>
                        {hasPendingSceneJobs && (
                          <div className="mt-3 rounded-md border border-amber-500/30 bg-amber-500/10 p-2 text-xs text-amber-700">
                            Kling 영상 생성 중입니다. 화면이 열려 있는 동안 20초마다 자동으로 상태를 확인합니다.
                            {sceneAutoPoll.lastCheckedAt && <span className="ml-1">마지막 확인: {sceneAutoPoll.lastCheckedAt}</span>}
                          </div>
                        )}
                        {hasFailedSceneJobs && (
                          <div className="mt-3 rounded-md border border-destructive/30 bg-destructive/10 p-2 text-xs text-destructive">
                            실패한 Scene이 있습니다. 오류 메시지를 확인한 뒤 설정이나 프롬프트를 조정하고 다시 생성해 주세요.
                          </div>
                        )}
                        {canDownloadAiScenes && (
                          <div className="mt-3 rounded-md border border-emerald-500/30 bg-emerald-500/10 p-2 text-xs text-emerald-700">
                            Scene 결과 URL이 준비됐습니다. <span className="font-medium">6 Scene 다운로드</span>를 눌러 로컬 파일로 저장하세요.
                          </div>
                        )}
                        {sceneGeneration.jobs.length > 0 ? (
                          <div className="mt-3 space-y-2">
                            {sceneGeneration.jobs.map((job) => (
                              <div key={job.sceneId} className="rounded-md border bg-muted/20 p-2 text-xs">
                                <div className="flex items-center justify-between gap-2">
                                  <span className="font-medium">{job.sceneId}</span>
                                  <Badge variant={job.status === "FAILED" ? "warning" : job.status === "CACHED" ? "muted" : ["SUBMITTED", "PROCESSING"].includes(job.status) ? "info" : "success"}>{job.status}</Badge>
                                </div>
                                <div className="mt-1 break-all text-muted-foreground">{job.resultPath}</div>
                                {job.errorMessage && (
                                  <div className={`mt-1 ${job.status === "FAILED" ? "text-destructive" : "text-amber-600"}`}>
                                    {job.errorMessage}
                                  </div>
                                )}
                              </div>
                            ))}
                          </div>
                        ) : (
                          <p className="mt-3 text-xs text-muted-foreground">{sceneGeneration.nextStep}</p>
                        )}
                      </div>
                    )}
                  </div>
                )}
              </Card>
            )}
          </div>

          <aside className="space-y-6">
            <Card className="p-5">
              <div className="mb-3 flex items-center gap-2 font-semibold">
                <PackageSearch className="size-4" /> 작업공간
              </div>
              <dl className="space-y-3 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">저장 경로</dt>
                  <dd className="mt-1 break-all rounded-md bg-muted/50 px-2 py-1.5 font-mono text-xs">{status?.workspacePath ?? "-"}</dd>
                </div>
                <div className={`rounded-md border p-3 ${status?.liveApiEnabled ? "border-amber-500/40 bg-amber-500/10" : "border-emerald-500/30 bg-emerald-500/10"}`}>
                  <div className="text-xs text-muted-foreground">비용 안전모드</div>
                  <div className="mt-1 font-medium">{status?.costSafetyMode ?? "SAFE_MOCK_LOCKED"}</div>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div className="rounded-md border p-3">
                    <div className="text-xs text-muted-foreground">기본 모드</div>
                    <div className="mt-1 font-medium">{status?.defaultMode ?? "BASIC"}</div>
                  </div>
                  <div className="rounded-md border p-3">
                    <div className="text-xs text-muted-foreground">기획 Provider</div>
                    <div className="mt-1 font-medium">{status?.planningProvider ?? "local-mock"}</div>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div className="rounded-md border p-3">
                    <div className="text-xs text-muted-foreground">스토리보드 Provider</div>
                    <div className="mt-1 font-medium">{status?.storyboardProvider ?? "local-mock"}</div>
                  </div>
                  <div className="rounded-md border p-3">
                    <div className="text-xs text-muted-foreground">영상 Provider</div>
                    <div className="mt-1 font-medium">{status?.videoProvider ?? "local-mock"}</div>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div className="rounded-md border p-3">
                    <div className="text-xs text-muted-foreground">TTS Provider</div>
                    <div className="mt-1 font-medium">{status?.ttsProvider ?? "local-mock"}</div>
                  </div>
                  <div className="rounded-md border p-3">
                    <div className="text-xs text-muted-foreground">Render Provider</div>
                    <div className="mt-1 font-medium">{status?.renderProvider ?? "local-mock"}</div>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div className="rounded-md border p-3">
                    <div className="text-xs text-muted-foreground">Quality Provider</div>
                    <div className="mt-1 font-medium">{status?.qualityProvider ?? "local-mock"}</div>
                  </div>
                  <div className="rounded-md border p-3">
                    <div className="text-xs text-muted-foreground">동시 장면</div>
                    <div className="mt-1 font-medium">{status?.maxParallelScenes ?? 2}</div>
                  </div>
                </div>
              </dl>
            </Card>

            <Card className="p-5">
              <div className="mb-3 flex items-center gap-2 font-semibold">
                <FolderOpen className="size-4" /> Extension
              </div>
              <ol className="space-y-2 text-sm text-muted-foreground">
                <li>1. Chrome에서 <span className="font-medium text-foreground">chrome://extensions</span> 열기</li>
                <li>2. 개발자 모드 켜기</li>
                <li>3. 압축해제된 확장 프로그램 로드</li>
                <li className="break-all rounded-md bg-muted/50 px-2 py-1.5 font-mono text-xs text-foreground">
                  /Users/haru/intellij-workspace/post-flow/apps/shopping-shorts-extension
                </li>
              </ol>
              <p className="mt-3 text-xs text-muted-foreground">
                Extension은 현재 쿠팡 탭 DOM만 읽고 JSON을 클립보드에 복사합니다.
              </p>
            </Card>

            <Card className="p-5">
              <div className="mb-3 flex items-center gap-2 font-semibold">
                <CheckCircle2 className="size-4" /> 체크포인트
              </div>
              <div className="space-y-2">
                {(status?.checkpoints ?? []).slice(0, 8).map((stage, index) => (
                  <div key={stage} className="flex items-center gap-2 text-sm">
                    <span className="flex size-5 items-center justify-center rounded-full bg-muted text-[11px] text-muted-foreground">{index + 1}</span>
                    <span>{stage}</span>
                  </div>
                ))}
              </div>
            </Card>

            <Card className="p-5">
              <div className="mb-3 flex items-center gap-2 font-semibold">
                <Link2 className="size-4" /> 고지 문구
              </div>
              <p className="text-sm leading-6 text-muted-foreground">{status?.disclosure}</p>
            </Card>
          </aside>
        </div>
      )}
    </div>
  );
}
