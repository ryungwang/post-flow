import { api } from "@/lib/api";
import type { User } from "@/store/auth";

// synub-sso 토큰 응답(백엔드가 프록시). accessToken=RS256, refreshToken=갱신용.
export type TokenResponse = {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
};

/** 데모 체험 계정(전역 규칙: 모든 서비스에 데모 필수). synub-sso 시드 계정. */
export const DEMO_LOGIN = { email: "demo@synub.io", password: "demo1234" };

export const authApi = {
  login: (email: string, password: string) =>
    api.post<TokenResponse>("/auth/login", { email, password }),
  refresh: (refreshToken: string) =>
    api.post<TokenResponse>("/auth/refresh", { refreshToken }),
  me: () => api.get<User>("/auth/me"),
};
