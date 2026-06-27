import { api } from "@/lib/api";

export type SeriesItem = {
  day: number;
  title: string;
  content: string;
  hashtags: string[];
  cta: string;
  score: number;
};

export type SeriesResponse = {
  items: SeriesItem[];
  provider: string;
  model: string;
};

export const seriesApi = {
  generate: (topic: string, days: number, goal?: string, brandId?: number | null) =>
    api.post<SeriesResponse>("/ai/series", { topic, days, goal, brandId }),
};
