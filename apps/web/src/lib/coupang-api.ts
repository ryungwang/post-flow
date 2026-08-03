import { api } from "@/lib/api";

/** 쿠팡 파트너스 Open API. 상품 URL → 제휴 딥링크(link.coupang.com/a/...) 자동 변환. */
export const coupangApi = {
  /** 쿠팡 상품 URL을 제휴 딥링크로. subId는 생성 단계에서 플랫폼별로 앱이 덧붙인다. */
  deeplink: (url: string) => api.post<{ shortUrl: string }>("/coupang/deeplink", { url }),
};
