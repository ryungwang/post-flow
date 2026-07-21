import { api } from "@/lib/api";

export type InstagramPost = {
  id: string;
  caption: string | null;
  createdAt: string | null;
  likes: number;
  comments: number;
  imageUrl: string | null;
  permalink: string | null;
};

export type InstagramInsights = {
  username: string;
  avatar: string | null;
  followers: number;
  following: number;
  posts: number;
  totalLikes: number;
  totalComments: number;
  sampledPosts: number;
};

export const instagramApi = {
  posts: (limit = 25) => api.get<InstagramPost[]>(`/social/instagram/posts?limit=${limit}`),
  insights: () => api.get<InstagramInsights>("/social/instagram/insights"),
};
