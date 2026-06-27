import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ImagePlus, Loader2, Pencil, Save, Send, Sparkles, Trash2, X } from "lucide-react";
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
import { uploadMedia, isVideoUrl } from "@/lib/media-api";
import { ScoreBadge } from "@/components/score-badge";
import { ScoreAnalysisPanel } from "@/components/score-analysis-panel";
import { ThreadsPreview } from "@/components/threads-preview";
import { contentApi } from "@/lib/content-api";
import { threadsApi } from "@/lib/threads-api";
import { CTA_TEMPLATES } from "@/lib/cta-templates";
import { useAuth } from "@/store/auth";
import { POST_STATUS_META } from "@/lib/post-status";
import { cn } from "@/lib/utils";

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
  const user = useAuth((s) => s.user);
  const fileRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [mode, setMode] = useState<"content" | "preview">("content");
  // local mirror so server-returned updates show without reopening
  const [local, setLocal] = useState<Post | null>(post);
  const [editing, setEditing] = useState(false);
  const [draftContent, setDraftContent] = useState("");
  const [draftHashtags, setDraftHashtags] = useState("");
  const [draftCta, setDraftCta] = useState("");

  useEffect(() => {
    setLocal(post);
    setEditing(false);
    setMode("content");
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
  const { data: accountsList } = useQuery({ queryKey: ["threads-accounts"], queryFn: threadsApi.accounts });
  const setAccount = useMutation({
    mutationFn: ({ id, accId }: { id: number; accId: number | null }) => postsApi.setAccount(id, accId),
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

  const suggestTags = useMutation({
    mutationFn: () => contentApi.hashtags("", draftContent || local?.content || ""),
  });

  const startEdit = () => {
    if (!local) return;
    setDraftContent(local.content);
    setDraftHashtags(local.hashtags?.join(", ") ?? "");
    setDraftCta(local.cta ?? "");
    setEditing(true);
  };

  const [uploadError, setUploadError] = useState<string | null>(null);
  const onPickFile = async (id: number, file: File | undefined) => {
    if (!file) return;
    setUploading(true);
    setUploadError(null);
    try {
      const { url } = await uploadMedia(file);
      await setMedia.mutateAsync({ id, url });
    } catch (e) {
      setUploadError(e instanceof Error ? e.message : "업로드 실패");
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
                    <div className="flex items-center justify-between">
                      <Label>해시태그 (쉼표/공백 구분)</Label>
                      <button
                        className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground disabled:opacity-50"
                        disabled={suggestTags.isPending}
                        onClick={() => suggestTags.mutate()}
                      >
                        {suggestTags.isPending ? <Loader2 className="size-3 animate-spin" /> : <Sparkles className="size-3" />} 추천
                      </button>
                    </div>
                    <Input value={draftHashtags} onChange={(e) => setDraftHashtags(e.target.value)} placeholder="AI, 콘텐츠자동화" />
                    {(suggestTags.data ?? []).length > 0 && (
                      <div className="flex flex-wrap gap-1 pt-1">
                        {suggestTags.data!.map((t) => {
                          const has = parseHashtags(draftHashtags).includes(t);
                          return (
                            <button
                              key={t}
                              disabled={has}
                              onClick={() => setDraftHashtags((cur) => (cur.trim() ? `${cur.trim()}, ${t}` : t))}
                              className={cn(
                                "rounded-full border px-2 py-0.5 text-xs transition-colors",
                                has ? "border-border bg-muted text-muted-foreground" : "border-brand/40 text-brand hover:bg-brand/10",
                              )}
                            >
                              #{t}
                            </button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                  <div className="space-y-1.5">
                    <Label>CTA</Label>
                    <Input value={draftCta} onChange={(e) => setDraftCta(e.target.value)} placeholder="무료 체크리스트 받기 → 댓글 CHECK" />
                    <div className="flex flex-wrap gap-1 pt-1">
                      {CTA_TEMPLATES.map((t) => (
                        <button
                          key={t}
                          onClick={() => setDraftCta(t)}
                          className={cn(
                            "rounded-full border px-2 py-0.5 text-xs transition-colors",
                            draftCta === t ? "border-brand/40 bg-brand/10 text-brand" : "text-muted-foreground hover:bg-accent hover:text-foreground",
                          )}
                        >
                          {t}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              ) : (
                <>
                  <div className="mb-3 flex w-fit items-center rounded-lg border bg-background p-0.5 text-xs">
                    {([["content", "내용"], ["preview", "미리보기"]] as const).map(([k, label]) => (
                      <button
                        key={k}
                        onClick={() => setMode(k)}
                        className={cn(
                          "rounded-md px-2.5 py-1 transition-colors",
                          mode === k ? "bg-accent font-medium text-foreground" : "text-muted-foreground hover:text-foreground",
                        )}
                      >
                        {label}
                      </button>
                    ))}
                  </div>

                  {mode === "preview" ? (
                    <ThreadsPreview
                      username={user?.name ?? "나"}
                      avatarUrl={user?.profileImage}
                      content={p.content}
                      hashtags={p.hashtags}
                      cta={p.cta}
                      mediaUrl={p.mediaUrl}
                    />
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

                  {/* media (image or video) */}
                  <div className="mt-4">
                    {p.mediaUrl ? (
                      <div className="group relative w-fit">
                        {isVideoUrl(p.mediaUrl) ? (
                          <video src={p.mediaUrl} controls className="max-h-56 rounded-lg border" />
                        ) : (
                          <img src={p.mediaUrl} alt="첨부 미디어" className="max-h-56 rounded-lg border object-cover" onError={(e) => { e.currentTarget.style.display = "none"; }} />
                        )}
                        <button
                          onClick={() => setMedia.mutate({ id: p.id, url: null })}
                          className="absolute right-2 top-2 rounded-full bg-black/60 p-1 text-white opacity-0 transition-opacity group-hover:opacity-100"
                          title="미디어 제거"
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
                        이미지·영상 첨부
                      </button>
                    )}
                    {uploadError && <p className="mt-1.5 text-xs text-destructive">{uploadError}</p>}
                    <input
                      ref={fileRef}
                      type="file"
                      accept="image/png,image/jpeg,image/gif,image/webp,video/mp4,video/quicktime"
                      className="hidden"
                      onChange={(e) => onPickFile(p.id, e.target.files?.[0])}
                    />
                  </div>

                  {/* publish target account (multi-account) */}
                  {(accountsList?.length ?? 0) > 1 && (
                    <div className="mt-4 flex items-center gap-2 text-sm">
                      <span className="text-muted-foreground">발행 계정</span>
                      <select
                        className="rounded-md border bg-background px-2 py-1 text-sm"
                        value={p.socialAccountId ?? ""}
                        onChange={(e) => setAccount.mutate({ id: p.id, accId: e.target.value ? Number(e.target.value) : null })}
                      >
                        <option value="">기본 계정</option>
                        {accountsList!.map((acc) => (
                          <option key={acc.id} value={acc.id}>@{acc.username}{acc.isDefault ? " (기본)" : ""}</option>
                        ))}
                      </select>
                    </div>
                  )}

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
