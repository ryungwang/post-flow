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
  scheduledAt?: string | null;
};

export const postsApi = {
  list: () => api.get<Post[]>("/posts"),
  create: (body: CreatePost) => api.post<Post>("/posts", body),
  publishNow: (id: number) => api.post<Post>(`/posts/${id}/publish`),
  remove: (id: number) => api.del<void>(`/posts/${id}`),
};
