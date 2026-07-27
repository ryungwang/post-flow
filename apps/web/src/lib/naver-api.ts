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

export type TrendCategory = { key: string; label: string };
/** 후보 키워드의 최근 쇼핑 트렌드. growth=상승률(%), score=최신 관심도지수. */
export type RisingKeyword = { keyword: string; growth: number; score: number };

export const naverApi = {
  searchShop: (query: string, display = 10) =>
    api.get<NaverProduct[]>(`/naver/shop/search?query=${encodeURIComponent(query)}&display=${display}`),
  trendCategories: () => api.get<TrendCategory[]>("/naver/trend/categories"),
  rising: (category: string) => api.get<RisingKeyword[]>(`/naver/trend/rising?category=${encodeURIComponent(category)}`),
};
