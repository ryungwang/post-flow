import { api } from "@/lib/api";

export type VideoSubmit = { productName: string; features?: string; hook?: string; imageUrl?: string };
export type VideoSubmitResponse = { jobId: string; status: string; caption: string };
/** videoUrl = 공개 URL(READY일 때). 미리보기·발행 media 로 그대로 사용. */
export type VideoStatus = { status: string; error: string | null; videoUrl: string | null };

export const affiliateVideoApi = {
  /** 광고영상 생성 시작(Kling 1컷). jobId 반환 → status 폴링. */
  submit: (req: VideoSubmit) => api.post<VideoSubmitResponse>("/ai/affiliate/video", req),
  /** 진행 상태 폴링. status=SUBMITTED|PROCESSING|READY|FAILED, READY면 videoUrl(공개). */
  status: (jobId: string) => api.get<VideoStatus>(`/ai/affiliate/video/${encodeURIComponent(jobId)}/status`),
};
