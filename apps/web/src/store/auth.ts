import { create } from "zustand";

export type User = {
  id: number;
  email: string;
  name: string;
  profileImage?: string | null;
  plan: string;
};

type AuthState = {
  token: string | null;
  user: User | null;
  setAuth: (token: string, user: User) => void;
  clear: () => void;
};

const TOKEN_KEY = "postflow-token";

export const useAuth = create<AuthState>((set) => ({
  token: localStorage.getItem(TOKEN_KEY),
  user: null,
  setAuth: (token, user) => {
    localStorage.setItem(TOKEN_KEY, token);
    set({ token, user });
  },
  clear: () => {
    localStorage.removeItem(TOKEN_KEY);
    set({ token: null, user: null });
  },
}));

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}
