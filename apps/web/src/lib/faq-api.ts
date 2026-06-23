import { api } from "@/lib/api";

export type Faq = { id: number; category: string; question: string; answer: string };

export const faqApi = {
  list: () => api.get<Faq[]>("/faqs"),
};
