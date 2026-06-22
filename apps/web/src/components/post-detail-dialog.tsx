import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Send, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { postsApi, type Post } from "@/lib/posts-api";
import { POST_STATUS_META } from "@/lib/post-status";

function fmt(iso: string | null) {
  if (!iso) return "—";
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(iso));
}

/** Reusable post detail modal (Library, Dashboard, …). */
export function PostDetailDialog({
  post,
  onOpenChange,
}: {
  post: Post | null;
  onOpenChange: (open: boolean) => void;
}) {
  const qc = useQueryClient();
  const publish = useMutation({
    mutationFn: (id: number) => postsApi.publishNow(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["posts"] }),
  });
  const remove = useMutation({
    mutationFn: (id: number) => postsApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["posts"] });
      onOpenChange(false);
    },
  });

  return (
    <Dialog open={!!post} onOpenChange={onOpenChange}>
      <DialogContent>
        {post && (
          <>
            <DialogHeader>
              <DialogTitle>게시물 상세</DialogTitle>
              <DialogDescription>
                <Badge variant={POST_STATUS_META[post.status].variant}>
                  {POST_STATUS_META[post.status].label}
                </Badge>
                <span className="ml-2 text-xs tabular-nums">{fmt(post.publishedAt ?? post.scheduledAt)}</span>
              </DialogDescription>
            </DialogHeader>

            <div className="max-h-[50vh] overflow-y-auto">
              <p className="whitespace-pre-wrap text-sm leading-relaxed">{post.content}</p>
              {post.hashtags?.length > 0 && (
                <div className="mt-3 flex flex-wrap gap-1">
                  {post.hashtags.map((h, i) => (
                    <Badge key={i} variant="secondary">#{h}</Badge>
                  ))}
                </div>
              )}
              {post.cta && <p className="mt-3 text-sm font-medium text-brand">{post.cta}</p>}
              <div className="mt-4 text-xs text-muted-foreground tabular-nums">
                {post.content.length}/500자
                {post.threadsMediaId && <> · Threads ID {post.threadsMediaId}</>}
              </div>
            </div>

            <DialogFooter>
              {post.status !== "PUBLISHED" && (
                <Button variant="outline" className="gap-1.5" disabled={publish.isPending} onClick={() => publish.mutate(post.id)}>
                  <Send className="size-4" /> 즉시 발행
                </Button>
              )}
              <Button variant="destructive" className="gap-1.5" disabled={remove.isPending} onClick={() => remove.mutate(post.id)}>
                <Trash2 className="size-4" /> 삭제
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
