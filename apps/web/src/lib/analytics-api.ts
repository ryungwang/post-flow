import { api } from "@/lib/api";

export type TopPost = {
  postId: number;
  content: string;
  views: number;
  likes: number;
  replies: number;
};

export type AnalyticsDashboard = {
  totalPosts: number;
  publishedPosts: number;
  scheduledPosts: number;
  views: number;
  likes: number;
  replies: number;
  reposts: number;
  quotes: number;
  shares: number;
  engagementRate: number;
  topPosts: TopPost[];
};

export type BestTime = { label: string; score: number };

export const analyticsApi = {
  dashboard: (days = 0) => api.get<AnalyticsDashboard>(`/analytics/dashboard?days=${days}`),
  bestTimes: () => api.get<BestTime[]>("/analytics/best-times"),
};
