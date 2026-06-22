import { api } from "@/lib/api";

export type PostRevenue = {
  postId: number;
  content: string;
  revenue: number;
  conversions: number;
};

export type RoiDashboard = {
  views: number;
  clicks: number;
  leads: number;
  conversions: number;
  revenue: number;
  currency: string;
  ctr: number;
  leadRate: number;
  purchaseRate: number;
  rpm: number;
  revenuePerPost: number;
  roiPercent: number | null;
  topByRevenue: PostRevenue[];
};

export type CtaLink = {
  id: number;
  postId: number;
  slug: string;
  shortUrl: string;
  destinationUrl: string;
  label: string | null;
};

export const roiApi = {
  dashboard: () => api.get<RoiDashboard>("/roi/dashboard"),
  createCtaLink: (postId: number, destinationUrl: string, label?: string) =>
    api.post<CtaLink>(`/posts/${postId}/cta-links`, { destinationUrl, label }),
  createConversion: (postId: number, amount: number, note?: string) =>
    api.post<{ id: number }>("/conversions", { postId, amount, note }),
};
