import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ImagePlus, Loader2, Send, Trash2, X } from "lucide-react";
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
import { uploadMedia } from "@/lib/media-api";
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
  const fileRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
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
  const setMedia = useMutation({
    mutationFn: ({ id, url }: { id: number; url: string | null }) => postsApi.setMedia(id, url),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["posts"] }),
  });

  const onPickFile = async (id: number, file: File | undefined) => {
    if (!file) return;
    setUploading(true);
    try {
      const { url } = await uploadMedia(file);
      await setMedia.mutateAsync({ id, url });
    } finally {
      setUploading(false);
    }
  };

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

              {/* media */}
              <div className="mt-4">
                {post.mediaUrl ? (
                  <div className="group relative w-fit">
                    <img src={post.mediaUrl} alt="첨부 이미지" className="max-h-56 rounded-lg border object-cover" />
                    <button
                      onClick={() => setMedia.mutate({ id: post.id, url: null })}
                      className="absolute right-2 top-2 rounded-full bg-black/60 p-1 text-white opacity-0 transition-opacity group-hover:opacity-100"
                      title="이미지 제거"
                    >
                      <X className="size-4" />
                    </button>
                  </div>
                ) : (
                  <button
                    onClick={() => fileRef.current?.click()}
                    disabled={uploading}
                    className="flex items-center gap-2 rounded-lg border border-dashed px-3 py-2 text-sm text-muted-foreground transition-colors hover:border-brand/50 hover:text-foreground"
                  >
                    {uploading ? <Loader2 className="size-4 animate-spin" /> : <ImagePlus className="size-4" />}
                    이미지 첨부
                  </button>
                )}
                <input
                  ref={fileRef}
                  type="file"
                  accept="image/png,image/jpeg,image/gif,image/webp"
                  className="hidden"
                  onChange={(e) => onPickFile(post.id, e.target.files?.[0])}
                />
              </div>

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
