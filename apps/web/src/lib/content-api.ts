import { api } from "@/lib/api";

export type GeneratedCard = {
  content: string;
  hashtags: string[];
  cta: string;
  score: number;
};

export type GenerateResponse = {
  cards: GeneratedCard[];
  provider: string;
  model: string;
};

export type GenerateRequest = {
  topic: string;
  goal: string;
  tone: string;
  quantity: number;
  brandId?: number | null;
  trendKeyword?: string | null;
  platform?: string; // THREADS/BLUESKY/MASTODON/INSTAGRAM/... — 플랫폼별 글자수·해시태그·훅. 미지정 시 THREADS.
};

export type GenerateAffiliateRequest = {
  productName: string;
  productFeatures?: string;
  affiliateLink: string;
  subIdPrefix?: string;
  tone: string;
  quantity: number;
  platform: string;
  blogHtml?: string; // 쿠팡 HTML(상품·배너·프로모션) — 블로그 모드에서 본문에 삽입
};

export type AffiliateResponse = {
  cards: GeneratedCard[];
  subId: string;
  linkWithSubId: string;
  linkInBody: boolean;
  provider: string;
  model: string;
};

export type HookVariant = { hook: string; score: number };

export type ScoreComponent = { label: string; score: number; max: number };
export type ScoreAnalysis = { total: number; components: ScoreComponent[]; tips: string[] };

export type Idea = { topic: string; topHook: HookVariant };

export const contentApi = {
  generate: (req: GenerateRequest) =>
    api.post<GenerateResponse>("/ai/generate", req),
  generateAffiliate: (req: GenerateAffiliateRequest) =>
    api.post<AffiliateResponse>("/ai/affiliate/generate", req),
  hooks: (topic: string, count = 6) =>
    api.post<HookVariant[]>("/ai/hooks", { topic, count }),
  score: (content: string, hashtags: string[], cta: string | null) =>
    api.post<ScoreAnalysis>("/ai/score", { content, hashtags, cta }),
  ideas: (count = 5, page = 0) => api.get<Idea[]>(`/ai/ideas?count=${count}&page=${page}`),
  hashtags: (topic: string, content: string) => api.post<string[]>("/ai/hashtags", { topic, content }),
};
