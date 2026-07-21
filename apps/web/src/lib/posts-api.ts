import { api } from "@/lib/api";

export type PostStatus =
  | "DRAFT"
  | "SCHEDULED"
  | "PUBLISHING"
  | "PUBLISHED"
  | "PARTIAL"
  | "FAILED"
  | "RECONNECT_REQUIRED";

/** 채널별 발행 타겟(멀티플랫폼 팬아웃). */
export type PostTarget = {
  socialAccountId: number;
  provider: string; // THREADS / BLUESKY
  channel: string | null;
  status: "PENDING" | "PUBLISHING" | "PUBLISHED" | "FAILED" | "RECONNECT_REQUIRED" | "DELETED_ON_PLATFORM";
  platformPostId: string | null;
  error: string | null;
};

export type Post = {
  id: number;
  content: string;
  hashtags: string[];
  cta: string | null;
  mediaUrl: string | null;
  socialAccountId: number | null;
  score: number;
  status: PostStatus;
  scheduledAt: string | null;
  publishedAt: string | null;
  threadsMediaId: string | null;
  createdAt: string;
  targets: PostTarget[];
};

export type CreatePost = {
  content: string;
  hashtags?: string[];
  cta?: string | null;
  mediaUrl?: string | null;
  scheduledAt?: string | null;
  channelIds?: number[];
};

export type UpdatePost = {
  content?: string;
  hashtags?: string[];
  cta?: string | null;
  scheduledAt?: string | null;
};

export type Improvement = {
  id: number;
  content: string;
  score: number;
  status: PostStatus;
  tips: string[];
};

export const postsApi = {
  list: () => api.get<Post[]>("/posts"),
  improvements: (threshold = 60) => api.get<Improvement[]>(`/posts/improvements?threshold=${threshold}`),
  create: (body: CreatePost) => api.post<Post>("/posts", body),
  update: (id: number, body: UpdatePost) => api.put<Post>(`/posts/${id}`, body),
  publishNow: (id: number) => api.post<Post>(`/posts/${id}/publish`),
  setMedia: (id: number, mediaUrl: string | null) => api.put<Post>(`/posts/${id}/media`, { mediaUrl }),
  /** 발행 대상 채널 다중선택(팬아웃). channelIds = SocialAccount id 목록. */
  setChannels: (id: number, channelIds: number[]) => api.put<Post>(`/posts/${id}/channels`, { channelIds }),
  remove: (id: number) => api.del<void>(`/posts/${id}`),
};
