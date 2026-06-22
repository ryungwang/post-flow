import { api } from "@/lib/api";

export type ThreadsStatus = {
  connected: boolean;
  status: string; // CONNECTED / EXPIRED / RECONNECT_REQUIRED / NOT_CONNECTED
  expiresAt: string | null;
};

export type ThreadsAccount = {
  id: number;
  username: string;
  name: string | null;
  profilePictureUrl: string | null;
  followersCount: number | null;
  status: string;
  isDefault: boolean;
  expiresAt: string | null;
};

export const threadsApi = {
  status: () => api.get<ThreadsStatus>("/threads/status"),
  connectUrl: () => api.get<{ authorizeUrl: string }>("/threads/connect"),
  accounts: () => api.get<ThreadsAccount[]>("/threads/accounts"),
  setDefault: (id: number) => api.post<void>(`/threads/accounts/${id}/default`),
  disconnect: (id: number) => api.del<void>(`/threads/accounts/${id}`),
};
