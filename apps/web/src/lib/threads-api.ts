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
  insights: (accountId?: number) =>
    api.get<ThreadsInsights>(`/threads/insights${accountId ? `?accountId=${accountId}` : ""}`),
  replies: (mediaId: string) => api.get<ThreadsReply[]>(`/threads/posts/${mediaId}/replies`),
};

/** 인구통계 한 항목(라벨=연령대/성별/국가/도시, 값=팔로워 수). */
export type DemoEntry = { label: string; value: number };

/** 계정 인사이트: 팔로워 수 + 팔로워 인구통계. */
export type ThreadsInsights = {
  followers: number | null;
  demographics: {
    age: DemoEntry[];
    gender: DemoEntry[];
    country: DemoEntry[];
    city: DemoEntry[];
  };
};

/** 게시물 댓글(답글) 한 건. */
export type ThreadsReply = { id: string; text: string | null; username: string | null };
