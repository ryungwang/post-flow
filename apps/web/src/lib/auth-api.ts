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

async function sso<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${SSO_BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
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
  refresh: (refreshToken: string) =>
    sso<TokenResponse>("/auth/refresh", { refreshToken }),
  // 제품 백엔드가 SSO 토큰 검증 + 선택 컨텍스트의 빌링 entitlement로 플랜 동기화해 반환.
  me: (context?: string) => api.get<User>(`/auth/me${context ? `?context=${encodeURIComponent(context)}` : ""}`),
  // 사용자 컨텍스트 목록(개인 + 소속 조직) — 스위처 소스.
  contexts: () => api.get<BillingContext[]>("/auth/contexts"),
};
