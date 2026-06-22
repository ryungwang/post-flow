import { api } from "@/lib/api";

export type Notification = {
  type: string;
  severity: "error" | "warning" | "info";
  message: string;
  link: string;
};

export const notificationsApi = {
  list: () => api.get<Notification[]>("/notifications"),
};
