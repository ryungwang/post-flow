import { api } from "@/lib/api";

export type WebhookInfo = { secret: string; endpoint: string };

export const accountApi = {
  webhook: () => api.get<WebhookInfo>("/account/webhook"),
  regenerateWebhook: () => api.post<WebhookInfo>("/account/webhook/regenerate"),
};
