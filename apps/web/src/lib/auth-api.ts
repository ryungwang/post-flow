import { api } from "@/lib/api";
import type { BillingContext, User } from "@/store/auth";

// 로그인은 synub 통합계정(SSO)에서 직접 처리한다(제품은 자체 로그인/비번을 만들지 않음).
// 프론트가 SSO를 직접 호출 → 토큰 발급. 제품 백엔드는 그 JWT를 "검증"만 한다. (PRODUCT_REGISTRATION §7)
const SSO_BASE = import.meta.env.VITE_SSO_BASE_URL ?? "http://localhost:8090";

export type TokenResponse = {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
};

/** 데모 체험 계정(전역 규칙: 모든 서비스에 데모 필수). synub-sso 시드 계정. */
export const DEMO_LOGIN = { email: "demo@synub.io", password: "demo1234" };

// 통합세션(SSO 공유쿠키 synub_rt): SSO 호출은 전부 credentials:"include"로 쿠키 송수신.
// (계약: contracts/SSO_UNIFIED_SESSION.md — 한 번 로그인=전 *.synub.io 무폼 진입, 로그아웃 전역)
async function sso<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${SSO_BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const b = await res.json().catch(() => ({}));
    throw new Error(b.message ?? "요청에 실패했어요.");
  }
  return res.json();
}

export const authApi = {
  login: (email: string, password: string) =>
    sso<TokenResponse>("/auth/login", { email, password }),
  // 무폼 리프레시: 공유쿠키 우선(body 없음). refreshToken 주면 하위호환 body 방식.
  refresh: (refreshToken?: string) =>
    sso<TokenResponse>("/auth/refresh", refreshToken ? { refreshToken } : undefined),
  // 세션 생존확인(회전 없음 — 포커스마다 호출해도 안전). 204=유효 / 401=만료·전역 로그아웃됨. 상태코드만 반환.
  session: async (): Promise<number> => {
    const res = await fetch(`${SSO_BASE}/auth/session`, { credentials: "include" });
    return res.status;
  },
  // 전역 로그아웃: 공유쿠키까지 폐기. SSO 미도달이어도 로컬 정리는 호출측이 진행.
  logout: async (): Promise<void> => {
    try {
      await fetch(`${SSO_BASE}/auth/logout`, { method: "POST", credentials: "include" });
    } catch {
      /* 네트워크 오류여도 무시 — 로컬 로그아웃은 계속 */
    }
  },
  // 제품 백엔드가 SSO 토큰 검증 + 선택 컨텍스트의 빌링 entitlement로 플랜 동기화해 반환.
  me: (context?: string) => api.get<User>(`/auth/me${context ? `?context=${encodeURIComponent(context)}` : ""}`),
  // 사용자 컨텍스트 목록(개인 + 소속 조직) — 스위처 소스.
  contexts: () => api.get<BillingContext[]>("/auth/contexts"),
};
