import { api } from "@/lib/api";

export type PostStatus =
  | "DRAFT"
  | "SCHEDULED"
  | "PUBLISHING"
  | "PUBLISHED"
  | "FAILED"
  | "RECONNECT_REQUIRED";

export type Post = {
  id: number;
  content: string;
  hashtags: string[];
  cta: string | null;
  mediaUrl: string | null;
  score: number;
  status: PostStatus;
  scheduledAt: string | null;
  publishedAt: string | null;
  threadsMediaId: string | null;
  createdAt: string;
};

export type CreatePost = {
  content: string;
  hashtags?: string[];
  cta?: string | null;
  mediaUrl?: string | null;
  scheduledAt?: string | null;
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
  remove: (id: number) => api.del<void>(`/posts/${id}`),
};
