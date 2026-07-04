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
  biography: string | null;
  followersCount: number | null;
  views: number | null;
  likes: number | null;
  replies: number | null;
  reposts: number | null;
  quotes: number | null;
  status: string;
  isDefault: boolean;
  expiresAt: string | null;
};

/** 연결된 Threads 계정에 실제 올라간 게시물 한 건(+PostFlow 발행 여부). */
export type ThreadsAccountPost = {
  id: string;
  text: string | null;
  timestamp: string | null;
  permalink: string | null;
  mediaType: string | null;
  mediaUrl: string | null;
  fromPostflow: boolean;
  views: number | null;
  likes: number | null;
  replies: number | null;
  reposts: number | null;
  quotes: number | null;
  shares: number | null;
};

export const threadsApi = {
  status: () => api.get<ThreadsStatus>("/threads/status"),
  connectUrl: () => api.get<{ authorizeUrl: string }>("/threads/connect"),
  accounts: () => api.get<ThreadsAccount[]>("/threads/accounts"),
  setDefault: (id: number) => api.post<void>(`/threads/accounts/${id}/default`),
  disconnect: (id: number) => api.del<void>(`/threads/accounts/${id}`),
  posts: (accountId?: number) =>
    api.get<ThreadsAccountPost[]>(`/threads/posts${accountId ? `?accountId=${accountId}` : ""}`),
};
