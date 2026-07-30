import { api } from "@/lib/api";

const BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export type VideoSubmit = { productName: string; features?: string; hook?: string; imageUrl: string };
export type VideoSubmitResponse = { jobId: string; status: string; caption: string };
export type VideoStatus = { status: string; error: string | null; output: string | null };

export const affiliateVideoApi = {
  /** 광고영상 생성 시작(Kling 1컷). jobId 반환 → status 폴링. */
  submit: (req: VideoSubmit) => api.post<VideoSubmitResponse>("/ai/affiliate/video", req),
  /** 진행 상태 폴링. status=SUBMITTED|PROCESSING|READY|FAILED. */
  status: (jobId: string) => api.get<VideoStatus>(`/ai/affiliate/video/${encodeURIComponent(jobId)}/status`),
  /** 완성 영상을 인증 헤더로 받아 blob URL로(비디오 태그 src용 — 헤더를 못 실어서 blob 사용). */
  fetchVideoUrl: async (jobId: string): Promise<string> => {
    const token = localStorage.getItem("postflow-token");
    const res = await fetch(`${BASE}/ai/affiliate/video/${encodeURIComponent(jobId)}/output`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) throw new Error("영상을 불러오지 못했어요.");
    return URL.createObjectURL(await res.blob());
  },
};
