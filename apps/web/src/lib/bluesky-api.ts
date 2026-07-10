import { api } from "@/lib/api";

export type BlueskyPost = {
  id: string;
  text: string | null;
  createdAt: string | null;
  likes: number;
  reposts: number;
  replies: number;
  imageUrl: string | null;
  permalink: string;
};

export type BlueskyInsights = {
  handle: string;
  displayName: string | null;
  avatar: string | null;
  followers: number;
  posts: number;
  totalLikes: number;
  totalReposts: number;
  totalReplies: number;
  sampledPosts: number;
};

export const blueskyApi = {
  posts: (limit = 25) => api.get<BlueskyPost[]>(`/social/bluesky/posts?limit=${limit}`),
  insights: () => api.get<BlueskyInsights>("/social/bluesky/insights"),
};
