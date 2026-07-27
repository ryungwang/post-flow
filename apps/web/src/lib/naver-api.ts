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

export const naverApi = {
  searchShop: (query: string, display = 10) =>
    api.get<NaverProduct[]>(`/naver/shop/search?query=${encodeURIComponent(query)}&display=${display}`),
};
