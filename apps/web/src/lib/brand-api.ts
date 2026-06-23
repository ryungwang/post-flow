import { api } from "@/lib/api";

export type Brand = {
  id: number;
  name: string;
  description: string | null;
  audience: string | null;
  keyPoints: string | null;
  ctaText: string | null;
  url: string | null;
  isDefault: boolean;
};

export type BrandInput = Omit<Brand, "id">;

export const brandApi = {
  list: () => api.get<Brand[]>("/brands"),
  create: (b: BrandInput) => api.post<Brand>("/brands", b),
  update: (id: number, b: BrandInput) => api.put<Brand>(`/brands/${id}`, b),
  remove: (id: number) => api.del<void>(`/brands/${id}`),
};
