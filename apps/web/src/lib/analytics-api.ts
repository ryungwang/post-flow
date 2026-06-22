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

export const analyticsApi = {
  dashboard: () => api.get<AnalyticsDashboard>("/analytics/dashboard"),
};
