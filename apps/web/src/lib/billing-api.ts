import { api } from "@/lib/api";

export type CheckoutResult = { url?: string; upgraded?: boolean; plan?: string };

export const billingApi = {
  checkout: (plan: string) => api.post<CheckoutResult>("/billing/checkout", { plan }),
};
