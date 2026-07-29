import { api } from "@/lib/api";
import { getToken } from "@/store/auth";

export type ShoppingShortsStatus = {
  workspacePath: string;
  disclosure: string;
  defaultMode: "BASIC";
  planningProvider: string;
  storyboardProvider: string;
  videoProvider: string;
  ttsProvider: string;
  renderProvider: string;
  qualityProvider: string;
  liveApiEnabled: boolean;
  costSafetyMode: string;
  paidCallStages: string[];
  maxParallelScenes: number;
  supportedDurations: number[];
  checkpoints: string[];
};

export type ShoppingShortsProductSummary = {
  productId: string;
  productName: string;
  brand?: string;
  price?: number;
  sourceImageCount: number;
  createdAt: string;
  updatedAt: string;
};

export type ShoppingShortsProductCaptureRequest = {
  productName: string;
  brand?: string;
  category?: string;
  price?: number;
  originalPrice?: number;
  discountRate?: number;
  options?: string[];
  features?: string[];
  description?: string;
  productUrl?: string;
  affiliateUrl: string;
  sourceImages?: string[];
  extractedAt?: string;
};

export type ShoppingShortsProductCaptureResponse = {
  productId: string;
  productPath: string;
  product: ShoppingShortsProductSummary;
};

export type ShoppingShortsProductWorkspaceState = {
  product: ShoppingShortsProductSummary;
  campaigns?: ShoppingShortsCampaignGenerationResponse | null;
  storyboard?: ShoppingShortsStoryboardResponse | null;
  productionAssets?: ShoppingShortsProductionAssetResponse | null;
  sceneGeneration?: ShoppingShortsSceneGenerationResponse | null;
  ttsGeneration?: ShoppingShortsTtsGenerationResponse | null;
  renderResult?: ShoppingShortsRenderResponse | null;
  qualityResult?: ShoppingShortsQualityCheckResponse | null;
};

export type ShoppingShortsValidationResponse = {
  productId: string;
  valid: boolean;
  missingFields: string[];
  warnings: string[];
  nextStep: string;
};

export type ShoppingShortsProductAnalysis = {
  productId: string;
  targetSituations: string[];
  sellingPoints: string[];
  riskNotes: string[];
  recommendedStyles: string[];
  hookCandidates?: string[];
  recommendedDuration: number;
};

export type ShoppingShortsCampaignCandidate = {
  campaignId: string;
  style: string;
  concept: string;
  hook: string;
  targetAudience: string;
  sellingPoints: string[];
  cta: string;
  recommendedDuration: number;
  estimatedAiSceneCount: number;
  originalImageSceneCount: number;
  estimatedCostLevel: string;
  accuracyRiskLevel: string;
  recommendationScore: number;
  recommendationReason: string;
};

export type ShoppingShortsCampaignGenerationResponse = {
  productId: string;
  productAnalysis: ShoppingShortsProductAnalysis;
  campaignCandidates: ShoppingShortsCampaignCandidate[];
};

export type ShoppingShortsStoryboardScene = {
  sceneId: string;
  order: number;
  purpose: string;
  duration: number;
  sourceType: "ORIGINAL_IMAGE" | "AI_VIDEO" | "TEXT_CARD" | "COMPOSITE";
  sourceAssetIds: string[];
  visualDescription: string;
  cameraShot: string;
  cameraMovement: string;
  transitionIn: string;
  transitionOut: string;
  caption: string;
  narration: string;
  soundEffect: string;
  klingPrompt: string;
  negativePrompt: string;
  requiresAiGeneration: boolean;
};

export type ShoppingShortsStoryboardResponse = {
  productId: string;
  campaignId: string;
  title: string;
  style: string;
  hook: string;
  duration: number;
  scenes: ShoppingShortsStoryboardScene[];
  youtube: {
    title: string;
    description: string;
    hashtags: string[];
    pinnedComment: string;
    affiliateUrl: string;
    disclosure: string;
  };
  disclosure: string;
};

export type ShoppingShortsProductionAssetResponse = {
  productId: string;
  campaignId: string;
  createdFiles: string[];
  aiSceneCount: number;
  subtitleCount: number;
  estimatedCostLevel: string;
  nextStep: string;
};

export type ShoppingShortsSceneJob = {
  sceneId: string;
  status: string;
  provider: string;
  model: string;
  requestPath: string;
  resultPath: string;
  errorMessage?: string;
};

export type ShoppingShortsSceneGenerationResponse = {
  productId: string;
  campaignId: string;
  provider: string;
  submittedSceneCount: number;
  cachedSceneCount: number;
  jobs: ShoppingShortsSceneJob[];
  nextStep: string;
};

export type ShoppingShortsTtsGenerationResponse = {
  productId: string;
  campaignId: string;
  provider: string;
  model: string;
  status: string;
  narrationPath: string;
  audioPath: string;
  timingPath: string;
  segmentCount: number;
  duration: number;
  nextStep: string;
};

export type ShoppingShortsRenderResponse = {
  productId: string;
  campaignId: string;
  provider: string;
  status: string;
  draftPath: string;
  finalPath: string;
  thumbnailPath: string;
  contactSheetPath: string;
  metadataPath: string;
  duration: number;
  width: number;
  height: number;
  nextStep: string;
};

export type ShoppingShortsQualityCheckItem = {
  code: string;
  label: string;
  status: "PASS" | "FAIL" | string;
  message: string;
};

export type ShoppingShortsQualityCheckResponse = {
  productId: string;
  campaignId: string;
  provider: string;
  status: "PASS" | "FAIL" | string;
  checks: ShoppingShortsQualityCheckItem[];
  reportPath: string;
  nextStep: string;
};

const segment = (value: string) => encodeURIComponent(value);
const BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
export type ShoppingShortsOutputName = "final" | "draft" | "thumbnail" | "contact-sheet" | "quality" | "storyboard";

async function downloadOutput(productId: string, campaignId: string, outputName: ShoppingShortsOutputName) {
  const token = getToken();
  const res = await fetch(
    `${BASE}/shopping-shorts/products/${segment(productId)}/campaigns/${segment(campaignId)}/outputs/${segment(outputName)}`,
    { headers: token ? { Authorization: `Bearer ${token}` } : {} },
  );
  if (!res.ok) {
    throw new Error(`Download failed: ${res.status}`);
  }
  return res.blob();
}

export const shoppingShortsApi = {
  status: () => api.get<ShoppingShortsStatus>("/shopping-shorts/status"),
  products: () => api.get<ShoppingShortsProductSummary[]>("/shopping-shorts/products"),
  productState: (productId: string) =>
    api.get<ShoppingShortsProductWorkspaceState>(`/shopping-shorts/products/${segment(productId)}/state`),
  captureProduct: (req: ShoppingShortsProductCaptureRequest) =>
    api.post<ShoppingShortsProductCaptureResponse>("/shopping-shorts/products/capture", req),
  deleteProduct: (productId: string) =>
    api.del<void>(`/shopping-shorts/products/${segment(productId)}`),
  validateProduct: (productId: string) =>
    api.post<ShoppingShortsValidationResponse>(`/shopping-shorts/products/${segment(productId)}/validate`),
  generateCampaigns: (productId: string) =>
    api.post<ShoppingShortsCampaignGenerationResponse>(`/shopping-shorts/products/${segment(productId)}/campaigns/generate`),
  selectCampaign: (productId: string, campaignId: string) =>
    api.post<ShoppingShortsStoryboardResponse>(`/shopping-shorts/products/${segment(productId)}/campaigns/select`, { campaignId }),
  prepareProductionAssets: (productId: string, campaignId: string) =>
    api.post<ShoppingShortsProductionAssetResponse>(
      `/shopping-shorts/products/${segment(productId)}/campaigns/${segment(campaignId)}/prepare-assets`,
    ),
  generateAiScenes: (productId: string, campaignId: string) =>
    api.post<ShoppingShortsSceneGenerationResponse>(
      `/shopping-shorts/products/${segment(productId)}/campaigns/${segment(campaignId)}/scenes/generate`,
    ),
  pollAiScenes: (productId: string, campaignId: string) =>
    api.post<ShoppingShortsSceneGenerationResponse>(
      `/shopping-shorts/products/${segment(productId)}/campaigns/${segment(campaignId)}/scenes/poll`,
    ),
  downloadAiScenes: (productId: string, campaignId: string) =>
    api.post<ShoppingShortsSceneGenerationResponse>(
      `/shopping-shorts/products/${segment(productId)}/campaigns/${segment(campaignId)}/scenes/download`,
    ),
  generateTts: (productId: string, campaignId: string) =>
    api.post<ShoppingShortsTtsGenerationResponse>(
      `/shopping-shorts/products/${segment(productId)}/campaigns/${segment(campaignId)}/tts/generate`,
    ),
  renderCampaign: (productId: string, campaignId: string) =>
    api.post<ShoppingShortsRenderResponse>(
      `/shopping-shorts/products/${segment(productId)}/campaigns/${segment(campaignId)}/render`,
    ),
  checkQuality: (productId: string, campaignId: string) =>
    api.post<ShoppingShortsQualityCheckResponse>(
      `/shopping-shorts/products/${segment(productId)}/campaigns/${segment(campaignId)}/quality/check`,
    ),
  downloadOutput,
};
