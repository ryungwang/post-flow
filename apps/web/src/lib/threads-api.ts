import { api } from "@/lib/api";

export type ThreadsStatus = {
  connected: boolean;
  status: string; // CONNECTED / EXPIRED / RECONNECT_REQUIRED / NOT_CONNECTED
  expiresAt: string | null;
};

export const threadsApi = {
  status: () => api.get<ThreadsStatus>("/threads/status"),
  connectUrl: () => api.get<{ authorizeUrl: string }>("/threads/connect"),
};
