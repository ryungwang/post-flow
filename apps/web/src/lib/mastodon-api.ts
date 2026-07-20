import { api } from "@/lib/api";

export type MastodonPost = {
  id: string;
  text: string | null;
  createdAt: string | null;
  favourites: number;
  reblogs: number;
  replies: number;
  imageUrl: string | null;
  permalink: string | null;
};

export type MastodonInsights = {
  handle: string;
  displayName: string | null;
  avatar: string | null;
  instance: string | null;
  followers: number;
  following: number;
  posts: number;
  totalFavourites: number;
  totalReblogs: number;
  totalReplies: number;
  sampledPosts: number;
};

export type MastodonMention = {
  id: string;
  statusId: string;
  authorHandle: string | null;
  authorName: string | null;
  authorAvatar: string | null;
  text: string | null;
  createdAt: string | null;
  permalink: string | null;
};

export const mastodonApi = {
  posts: (limit = 25) => api.get<MastodonPost[]>(`/social/mastodon/posts?limit=${limit}`),
  insights: () => api.get<MastodonInsights>("/social/mastodon/insights"),
  mentions: (limit = 25) => api.get<MastodonMention[]>(`/social/mastodon/mentions?limit=${limit}`),
};
