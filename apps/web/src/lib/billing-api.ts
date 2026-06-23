import { api } from "@/lib/api";

export type CheckoutResult = { url?: string; upgraded?: boolean; plan?: string };
export type PortalResult = { url?: string; canceled?: boolean };

export const billingApi = {
  checkout: (plan: string) => api.post<CheckoutResult>("/billing/checkout", { plan }),
  portal: () => api.post<PortalResult>("/billing/portal"),
  tossConfig: () => api.get<{ clientKey: string; customerKey: string }>("/billing/toss/config"),
  tossCharge: (plan: string) =>
    api.post<{ charged?: boolean; needCard?: boolean; plan?: string }>("/billing/toss/charge", { plan }),
  tossConfirm: (authKey: string, plan: string) =>
    api.post<{ ok: boolean; plan: string; status: string }>("/billing/toss/confirm", { authKey, plan }),
};
