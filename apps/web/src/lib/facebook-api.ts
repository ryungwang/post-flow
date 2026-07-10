import { api } from "@/lib/api";

/** Facebook 페이지 연결 = OAuth2(팝업). 관리하는 페이지를 채널로 등록해 발행. */
export const facebookApi = {
  /** 백엔드가 만든 로그인 다이얼로그 URL(팝업으로 열어 OAuth 진행). */
  connectUrl: () => api.get<{ authorizeUrl: string }>("/facebook/connect"),
};
