import { getToken } from "@/store/auth";

const BASE = import.meta.env.VITE_API_BASE_URL ?? "/api";

/** Upload an image; returns its publicly reachable URL. */
export async function uploadMedia(file: File): Promise<{ key: string; url: string }> {
  const form = new FormData();
  form.append("file", file);
  const token = getToken();
  const res = await fetch(`${BASE}/media`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  });
  if (!res.ok) throw new Error("upload failed");
  return res.json();
}
