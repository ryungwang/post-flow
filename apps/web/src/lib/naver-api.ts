import { api } from "@/lib/api";

/** 네이버 쇼핑 상품(제휴 근거용). price·image는 네이버 쇼핑 기준 → 참고용(쿠팡과 다를 수 있음). */
export type NaverProduct = {
  name: string;
  brand: string | null;
  maker: string | null;
  category: string | null;
  price: number | null;
  image: string | null;
  link: string | null;
  mallName: string | null;
  productId: string | null;
};

export type RadarCategory = { key: string; label: string };
/** 점수 근거 한 항목. status=available|unavailable(확인 불가), score는 unavailable이면 null. */
export type RadarScoreItem = { label: string; score: number | null; max: number; status: "available" | "unavailable"; note: string };
/** 점수화된 상품 후보(레이더). riseRate=검색 상승률(%), breakdown=점수 근거. */
export type RadarProduct = { name: string; category: string; score: number; riseRate: number | null; trend: number[]; breakdown: RadarScoreItem[] };
export type RadarResponse = { categories: RadarCategory[]; dataLab: boolean; products: RadarProduct[] };

export const naverApi = {
  searchShop: (query: string, display = 10) =>
    api.get<NaverProduct[]>(`/naver/shop/search?query=${encodeURIComponent(query)}&display=${display}`),
  /** 상품 레이더 — 카테고리별 급상승 후보(DataLab 검색+쇼핑 점수). window=7|30. */
  radar: (category: string, window: 7 | 30) =>
    api.get<RadarResponse>(`/naver/radar?category=${encodeURIComponent(category)}&window=${window}`),
};
