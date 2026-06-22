import { api } from "@/lib/api";

export type CheckoutResult = { url?: string; upgraded?: boolean; plan?: string };
export type PortalResult = { url?: string; canceled?: boolean };

export const billingApi = {
  checkout: (plan: string) => api.post<CheckoutResult>("/billing/checkout", { plan }),
  portal: () => api.post<PortalResult>("/billing/portal"),
};
