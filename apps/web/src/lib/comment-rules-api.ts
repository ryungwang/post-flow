import { api } from "@/lib/api";

export type CommentRule = {
  id: number;
  postId: number | null;
  provider: string | null; // 대상 SNS(THREADS/BLUESKY/…). null = 전체 SNS.
  keyword: string;
  replyTemplate: string;
  ctaLinkId: number | null;
  active: boolean;
};

export type CommentRuleInput = {
  postId?: number | null;
  provider?: string | null; // 대상 SNS. null·미지정 = 전체 SNS.
  keyword: string;
  replyTemplate: string;
  ctaLinkId?: number | null;
  active?: boolean;
};

export type TestResult = { matched: boolean; replyText: string | null };

export const commentRulesApi = {
  list: () => api.get<CommentRule[]>("/comment-rules"),
  create: (body: CommentRuleInput) => api.post<CommentRule>("/comment-rules", body),
  update: (id: number, body: CommentRuleInput) => api.put<CommentRule>(`/comment-rules/${id}`, body),
  remove: (id: number) => api.del<void>(`/comment-rules/${id}`),
  test: (id: number, comment: string) => api.post<TestResult>(`/comment-rules/${id}/test`, { comment }),
};
