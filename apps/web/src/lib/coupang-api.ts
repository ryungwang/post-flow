import { api } from "@/lib/api";

/** 쿠팡 상품(소싱용). productUrl은 승인 파트너 기준 이미 제휴 딥링크. */
export type CoupangProduct = {
  productId: number;
  productName: string;
  productPrice: number | null;
  productImage: string | null;
  productUrl: string;
  categoryName: string | null;
  isRocket: boolean | null;
  isFreeShipping: boolean | null;
};

/** 카테고리 베스트용 쿠팡 고정 카테고리 코드. */
export const COUPANG_CATEGORIES: { id: string; label: string }[] = [
  { id: "1010", label: "가전디지털" },
  { id: "1021", label: "뷰티" },
  { id: "1017", label: "헬스/건강식품" },
  { id: "1025", label: "주방용품" },
  { id: "1024", label: "홈인테리어" },
  { id: "1026", label: "생활용품" },
  { id: "1029", label: "식품" },
  { id: "1011", label: "스포츠/레저" },
  { id: "1016", label: "반려동물용품" },
  { id: "1014", label: "완구/취미" },
  { id: "1001", label: "여성패션" },
  { id: "1002", label: "남성패션" },
  { id: "1020", label: "유아동패션" },
  { id: "1030", label: "출산/유아동" },
  { id: "1012", label: "자동차용품" },
  { id: "1015", label: "문구/오피스" },
  { id: "1013", label: "도서/음반" },
];

/** 쿠팡 파트너스 Open API — 딥링크 + 상품 소싱(골드박스·베스트·검색). SNS 제휴 소재를 쿠팡 실데이터로. */
export const coupangApi = {
  /** 상품 URL → 제휴 딥링크(subId는 생성 단계에서 앱이 플랫폼별로 덧붙임). */
  deeplink: (url: string) => api.post<{ shortUrl: string }>("/coupang/deeplink", { url }),
  /** 오늘의 골드박스(특가). */
  goldbox: () => api.get<CoupangProduct[]>("/coupang/goldbox"),
  /** 카테고리 베스트. */
  best: (categoryId: string, limit = 20) =>
    api.get<CoupangProduct[]>(`/coupang/best?categoryId=${categoryId}&limit=${limit}`),
  /** 키워드 검색. */
  search: (keyword: string, limit = 20) =>
    api.get<CoupangProduct[]>(`/coupang/search?keyword=${encodeURIComponent(keyword)}&limit=${limit}`),
};
