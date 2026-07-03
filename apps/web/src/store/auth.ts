import { create } from "zustand";

export type User = {
  id: number;
  email: string;
  name: string;
  profileImage?: string | null;
  plan: string;
  demo?: boolean;
};

type AuthState = {
  token: string | null;
  user: User | null;
  setToken: (token: string) => void;
  setAuth: (token: string, user: User) => void;
  clear: () => void;
};

const TOKEN_KEY = "postflow-token";
const REFRESH_KEY = "postflow-refresh";

export const useAuth = create<AuthState>((set) => ({
  token: localStorage.getItem(TOKEN_KEY),
  user: null,
  // 토큰만 먼저 저장(이후 /auth/me 호출이 Authorization 헤더에 실리도록).
  setToken: (token) => {
    localStorage.setItem(TOKEN_KEY, token);
    set({ token });
  },
  setAuth: (token, user) => {
    localStorage.setItem(TOKEN_KEY, token);
    set({ token, user });
  },
  clear: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    set({ token: null, user: null });
  },
}));

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setRefreshToken(token: string) {
  localStorage.setItem(REFRESH_KEY, token);
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY);
}
