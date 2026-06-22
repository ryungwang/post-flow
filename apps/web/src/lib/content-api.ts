import { api } from "@/lib/api";

export type GeneratedCard = {
  content: string;
  hashtags: string[];
  cta: string;
};

export type GenerateResponse = {
  cards: GeneratedCard[];
  provider: string;
  model: string;
};

export type GenerateRequest = {
  topic: string;
  goal: string;
  tone: string;
  quantity: number;
};

export const contentApi = {
  generate: (req: GenerateRequest) =>
    api.post<GenerateResponse>("/ai/generate", req),
};
