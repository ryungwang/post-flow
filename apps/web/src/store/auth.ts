import { create } from "zustand";

export type User = {
  id: number;
  email: string;
  name: string;
  profileImage?: string | null;
  plan: string;
  demo?: boolean;
};

/** 빌링 컨텍스트(개인/조직) — 이 값으로 entitlement를 판정. */
export type BillingContext = {
  type: string; // personal | org
  context: string; // "personal" | "org:SH-XXXX"
  orgCode: string | null;
  name: string;
  role: string | null;
};

type AuthState = {
  token: string | null;
  user: User | null;
  context: string; // 현재 선택 컨텍스트(빌링 entitlement 판정용)
  contexts: BillingContext[];
  setToken: (token: string) => void;
  setAuth: (token: string, user: User) => void;
  setContext: (context: string) => void;
  setContexts: (contexts: BillingContext[]) => void;
  clear: () => void;
};

const TOKEN_KEY = "postflow-token";
const REFRESH_KEY = "postflow-refresh";
const CONTEXT_KEY = "postflow-context";

export const useAuth = create<AuthState>((set) => ({
  token: localStorage.getItem(TOKEN_KEY),
  user: null,
  context: localStorage.getItem(CONTEXT_KEY) ?? "personal", // 개인 제품 기본 personal
  contexts: [],
  // 토큰만 먼저 저장(이후 /auth/me 호출이 Authorization 헤더에 실리도록).
  setToken: (token) => {
    localStorage.setItem(TOKEN_KEY, token);
    set({ token });
  },
  setAuth: (token, user) => {
    localStorage.setItem(TOKEN_KEY, token);
    set({ token, user });
  },
  setContext: (context) => {
    localStorage.setItem(CONTEXT_KEY, context);
    set({ context });
  },
  setContexts: (contexts) => set({ contexts }),
  clear: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    set({ token: null, user: null, contexts: [] });
  },
}));

export function getContext() {
  return localStorage.getItem(CONTEXT_KEY) ?? "personal";
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setRefreshToken(token: string) {
  localStorage.setItem(REFRESH_KEY, token);
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY);
}
