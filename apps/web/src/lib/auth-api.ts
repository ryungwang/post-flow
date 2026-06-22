import { api } from "@/lib/api";
import type { User } from "@/store/auth";

type LoginResponse = { token: string; user: User };

export const authApi = {
  loginWithGoogle: (idToken: string) =>
    api.post<LoginResponse>("/auth/google", { idToken }),
  me: () => api.get<User>("/auth/me"),
};
