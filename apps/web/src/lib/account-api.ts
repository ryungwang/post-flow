import { api } from "@/lib/api";

export type WebhookInfo = { secret: string; endpoint: string };

export type Usage = {
  plan: string;
  used: number;
  limit: number; // -1 = unlimited
  canSchedule: boolean;
  canSeries: boolean;
  canMultiAccount: boolean;
};

export const accountApi = {
  webhook: () => api.get<WebhookInfo>("/account/webhook"),
  regenerateWebhook: () => api.post<WebhookInfo>("/account/webhook/regenerate"),
  usage: () => api.get<Usage>("/account/usage"),
};
