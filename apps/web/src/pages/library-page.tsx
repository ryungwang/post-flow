import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Library, Loader2, Send, Trash2 } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { PostDetailDialog } from "@/components/post-detail-dialog";
import { postsApi, type Post } from "@/lib/posts-api";
import { POST_STATUS_META } from "@/lib/post-status";

function fmt(iso: string | null) {
  if (!iso) return "—";
  const d = new Date(iso);
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(d);
}

export function LibraryPage() {
  const qc = useQueryClient();
  const { data, isLoading, isError } = useQuery({ queryKey: ["posts"], queryFn: postsApi.list });
  const [selected, setSelected] = useState<Post | null>(null);

  const publish = useMutation({
    mutationFn: (id: number) => postsApi.publishNow(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["posts"] }),
  });
  const remove = useMutation({
    mutationFn: (id: number) => postsApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["posts"] });
      setSelected(null);
    },
  });

  const posts = data ?? [];

  return (
    <div className="w-full px-6 py-7 lg:px-8 xl:px-10">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">라이브러리</h1>
        <p className="mt-1 text-sm text-muted-foreground">생성·저장된 콘텐츠를 관리합니다. (행을 클릭하면 상세보기)</p>
      </div>

      <Card>
        <div className="flex items-center justify-between border-b border-border/60 px-6 py-4">
          <h2 className="font-semibold">콘텐츠</h2>
          {!isLoading && <span className="text-sm text-muted-foreground">{posts.length}개</span>}
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" /> 불러오는 중…
          </div>
        ) : isError ? (
          <div className="py-16 text-center text-sm text-destructive">불러오기에 실패했어요.</div>
        ) : posts.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-3 py-16 text-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
              <Library className="size-6" />
            </div>
            <p className="text-sm text-muted-foreground">
              아직 저장된 콘텐츠가 없어요. AI 생성에서 카드를 저장해 보세요.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border/60 text-left text-xs uppercase tracking-wide text-muted-foreground">
                  <th className="px-6 py-3 font-medium">콘텐츠</th>
                  <th className="px-4 py-3 font-medium">상태</th>
                  <th className="px-4 py-3 font-medium">예약/발행</th>
                  <th className="px-6 py-3 text-right font-medium">작업</th>
                </tr>
              </thead>
              <tbody>
                {posts.map((p: Post) => {
                  const meta = POST_STATUS_META[p.status];
                  const when = p.publishedAt ?? p.scheduledAt;
                  const busy =
                    (publish.isPending && publish.variables === p.id) ||
                    (remove.isPending && remove.variables === p.id);
                  return (
                    <tr
                      key={p.id}
                      className="cursor-pointer border-b border-border/60 align-top last:border-0 hover:bg-accent/40"
                      onClick={() => setSelected(p)}
                    >
                      <td className="px-6 py-3.5">
                        <div className="max-w-xl">
                          <p className="line-clamp-3 whitespace-pre-line font-medium">{p.content}</p>
                          {p.hashtags?.length > 0 && (
                            <div className="mt-1.5 flex flex-wrap gap-1">
                              {p.hashtags.map((h, i) => (
                                <Badge key={i} variant="secondary">#{h}</Badge>
                              ))}
                            </div>
                          )}
                          {p.cta && <p className="mt-1.5 text-xs font-medium text-brand">{p.cta}</p>}
                        </div>
                      </td>
                      <td className="px-4 py-3.5">
                        <Badge variant={meta.variant}>{meta.label}</Badge>
                      </td>
                      <td className="px-4 py-3.5 text-muted-foreground">{fmt(when)}</td>
                      <td className="px-6 py-3.5" onClick={(e) => e.stopPropagation()}>
                        <div className="flex items-center justify-end gap-1">
                          {p.status !== "PUBLISHED" && (
                            <Button variant="ghost" size="icon" title="즉시 발행" disabled={busy} onClick={() => publish.mutate(p.id)}>
                              <Send className="size-4" />
                            </Button>
                          )}
                          <Button variant="ghost" size="icon" title="삭제" disabled={busy} onClick={() => remove.mutate(p.id)}>
                            <Trash2 className="size-4" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <PostDetailDialog post={selected} onOpenChange={(o) => !o && setSelected(null)} />
    </div>
  );
}
