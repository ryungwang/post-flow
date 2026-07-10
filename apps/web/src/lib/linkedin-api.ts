import { api } from "@/lib/api";

/** LinkedIn 연결 = OAuth2(팝업). 발행 전용(개인 프로필 읽기/분석 API는 파트너 승인 필요). */
export const linkedinApi = {
  /** 백엔드가 만든 authorize URL(팝업으로 열어 OAuth 진행). */
  connectUrl: () => api.get<{ authorizeUrl: string }>("/linkedin/connect"),
};
