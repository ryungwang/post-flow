import { useEffect, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ImagePlus, Loader2, Pencil, Save, Send, Trash2, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
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
import { ScoreBadge } from "@/components/score-badge";
import { ScoreAnalysisPanel } from "@/components/score-analysis-panel";
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

const parseHashtags = (raw: string) =>
  raw.split(/[\s,]+/).map((s) => s.replace(/^#/, "").trim()).filter(Boolean);

/** Reusable post detail modal with inline editing (Library, Dashboard, …). */
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
  // local mirror so server-returned updates show without reopening
  const [local, setLocal] = useState<Post | null>(post);
  const [editing, setEditing] = useState(false);
  const [draftContent, setDraftContent] = useState("");
  const [draftHashtags, setDraftHashtags] = useState("");
  const [draftCta, setDraftCta] = useState("");

  useEffect(() => {
    setLocal(post);
    setEditing(false);
  }, [post?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["posts"] });
    qc.invalidateQueries({ queryKey: ["improvements"] });
  };
  const onSaved = (data: Post) => { setLocal(data); invalidate(); };

  const publish = useMutation({
    mutationFn: (id: number) => postsApi.publishNow(id),
    onSuccess: onSaved,
  });
  const remove = useMutation({
    mutationFn: (id: number) => postsApi.remove(id),
    onSuccess: () => { invalidate(); onOpenChange(false); },
  });
  const setMedia = useMutation({
    mutationFn: ({ id, url }: { id: number; url: string | null }) => postsApi.setMedia(id, url),
    onSuccess: onSaved,
  });
  const save = useMutation({
    mutationFn: (id: number) => postsApi.update(id, {
      content: draftContent,
      hashtags: parseHashtags(draftHashtags),
      cta: draftCta,
    }),
    onSuccess: (data) => { onSaved(data); setEditing(false); },
  });

  const startEdit = () => {
    if (!local) return;
    setDraftContent(local.content);
    setDraftHashtags(local.hashtags?.join(", ") ?? "");
    setDraftCta(local.cta ?? "");
    setEditing(true);
  };

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

  const p = local;

  return (
    <Dialog open={!!post} onOpenChange={onOpenChange}>
      <DialogContent>
        {p && (
          <>
            <DialogHeader>
              <DialogTitle>{editing ? "게시물 편집" : "게시물 상세"}</DialogTitle>
              <DialogDescription className="flex items-center gap-2">
                <Badge variant={POST_STATUS_META[p.status].variant}>
                  {POST_STATUS_META[p.status].label}
                </Badge>
                <ScoreBadge score={p.score} />
                <span className="text-xs tabular-nums">{fmt(p.publishedAt ?? p.scheduledAt)}</span>
              </DialogDescription>
            </DialogHeader>

            <div className="max-h-[55vh] overflow-y-auto">
              {editing ? (
                <div className="space-y-3">
                  <div className="space-y-1.5">
                    <Label>본문</Label>
                    <Textarea rows={6} maxLength={500} value={draftContent} onChange={(e) => setDraftContent(e.target.value)} />
                    <div className="text-right text-xs text-muted-foreground tabular-nums">{draftContent.length}/500</div>
                  </div>
                  <div className="space-y-1.5">
                    <Label>해시태그 (쉼표/공백 구분)</Label>
                    <Input value={draftHashtags} onChange={(e) => setDraftHashtags(e.target.value)} placeholder="AI, 콘텐츠자동화" />
                  </div>
                  <div className="space-y-1.5">
                    <Label>CTA</Label>
                    <Input value={draftCta} onChange={(e) => setDraftCta(e.target.value)} placeholder="무료 체크리스트 받기 → 댓글 CHECK" />
                  </div>
                </div>
              ) : (
                <>
                  <p className="whitespace-pre-wrap text-sm leading-relaxed">{p.content}</p>
                  {p.hashtags?.length > 0 && (
                    <div className="mt-3 flex flex-wrap gap-1">
                      {p.hashtags.map((h, i) => (
                        <Badge key={i} variant="secondary">#{h}</Badge>
                      ))}
                    </div>
                  )}
                  {p.cta && <p className="mt-3 text-sm font-medium text-brand">{p.cta}</p>}

                  {/* media */}
                  <div className="mt-4">
                    {p.mediaUrl ? (
                      <div className="group relative w-fit">
                        <img src={p.mediaUrl} alt="첨부 이미지" className="max-h-56 rounded-lg border object-cover" />
                        <button
                          onClick={() => setMedia.mutate({ id: p.id, url: null })}
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
                      onChange={(e) => onPickFile(p.id, e.target.files?.[0])}
                    />
                  </div>

                  {/* attention analysis */}
                  <div className="mt-4">
                    <ScoreAnalysisPanel content={p.content} hashtags={p.hashtags} cta={p.cta} score={p.score} />
                  </div>

                  <div className="mt-3 text-xs text-muted-foreground tabular-nums">
                    {p.content.length}/500자
                    {p.threadsMediaId && <> · Threads ID {p.threadsMediaId}</>}
                  </div>
                </>
              )}
            </div>

            <DialogFooter>
              {editing ? (
                <>
                  <Button variant="ghost" onClick={() => setEditing(false)} disabled={save.isPending}>취소</Button>
                  <Button className="gap-1.5" disabled={!draftContent.trim() || save.isPending} onClick={() => save.mutate(p.id)}>
                    {save.isPending ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />} 저장
                  </Button>
                </>
              ) : (
                <>
                  <Button variant="outline" className="mr-auto gap-1.5" onClick={startEdit}>
                    <Pencil className="size-4" /> 편집
                  </Button>
                  {p.status !== "PUBLISHED" && (
                    <Button variant="outline" className="gap-1.5" disabled={publish.isPending} onClick={() => publish.mutate(p.id)}>
                      <Send className="size-4" /> 즉시 발행
                    </Button>
                  )}
                  <Button variant="destructive" className="gap-1.5" disabled={remove.isPending} onClick={() => remove.mutate(p.id)}>
                    <Trash2 className="size-4" /> 삭제
                  </Button>
                </>
              )}
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
