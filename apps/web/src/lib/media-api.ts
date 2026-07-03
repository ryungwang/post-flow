import { getToken } from "@/store/auth";

const BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

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
  if (!res.ok) {
    let msg = "업로드에 실패했어요.";
    try {
      const b = await res.json();
      msg = b?.message || b?.error || msg;
    } catch {
      /* non-JSON */
    }
    throw new Error(msg);
  }
  return res.json();
}

const VIDEO_EXT = /\.(mp4|mov)(\?|$)/i;
export function isVideoUrl(url: string | null | undefined): boolean {
  return !!url && VIDEO_EXT.test(url);
}
