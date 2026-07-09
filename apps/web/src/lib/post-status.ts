import type { PostStatus } from "@/lib/posts-api";

type Variant = "success" | "info" | "muted" | "warning";

export const POST_STATUS_META: Record<PostStatus, { label: string; variant: Variant }> = {
  DRAFT: { label: "초안", variant: "muted" },
  SCHEDULED: { label: "예약됨", variant: "info" },
  PUBLISHING: { label: "발행 중", variant: "warning" },
  PUBLISHED: { label: "발행됨", variant: "success" },
  PARTIAL: { label: "일부 발행", variant: "warning" },
  FAILED: { label: "실패", variant: "warning" },
  RECONNECT_REQUIRED: { label: "재연결 필요", variant: "warning" },
};
