import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Activity, Eye, FileText, Heart, Lightbulb, Loader2, MessageCircle, Pencil, RefreshCw, Sparkles, Wand2 } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { PostDetailDialog } from "@/components/post-detail-dialog";
import { ScoreBadge } from "@/components/score-badge";
import { pingApi } from "@/lib/api";
import { analyticsApi } from "@/lib/analytics-api";
import { contentApi } from "@/lib/content-api";
import { postsApi, type Post } from "@/lib/posts-api";
import { POST_STATUS_META } from "@/lib/post-status";

const nf = new Intl.NumberFormat("ko-KR");

function fmt(iso: string | null) {
  if (!iso) return "—";
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(iso));
}

function ApiBadge() {
  const [state, setState] = useState<"checking" | "up" | "down">("checking");
  useEffect(() => {
    pingApi().then((ok) => setState(ok ? "up" : "down"));
  }, []);
  const meta = {
    checking: { label: "API 확인 중", variant: "muted" as const },
    up: { label: "API 연결됨", variant: "success" as const },
    down: { label: "API 오프라인", variant: "warning" as const },
  }[state];
  return (
    <Badge variant={meta.variant}>
      <span className="size-1.5 rounded-full bg-current" />
      {meta.label}
    </Badge>
  );
}

export function DashboardPage() {
  const analytics = useQuery({ queryKey: ["analytics", 0], queryFn: () => analyticsApi.dashboard() });
  const postsQ = useQuery({ queryKey: ["posts"], queryFn: postsApi.list });
  const [selected, setSelected] = useState<Post | null>(null);

  const a = analytics.data;
  const kpis = [
    { label: "총 게시물", value: a ? nf.format(a.totalPosts) : "—", sub: a ? `발행 ${a.publishedPosts} · 예약 ${a.scheduledPosts}` : "", icon: FileText },
    { label: "총 조회수", value: a ? nf.format(a.views) : "—", icon: Eye },
    { label: "총 좋아요", value: a ? nf.format(a.likes) : "—", icon: Heart },
    { label: "총 댓글", value: a ? nf.format(a.replies) : "—", icon: MessageCircle },
    { label: "참여율", value: a ? `${(a.engagementRate * 100).toFixed(1)}%` : "—", icon: Activity },
  ];

  const recent = (postsQ.data ?? []).slice(0, 6);

  return (
    <div className="w-full px-6 py-7 lg:px-8 xl:px-10">
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">대시보드</h1>
          <p className="mt-1 text-sm text-muted-foreground">콘텐츠 성과 요약 · Threads</p>
        </div>
        <ApiBadge />
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-5">
        {kpis.map((kpi, i) => (
          <Card
            key={kpi.label}
            className="lift animate-fade-up p-5 hover:border-brand/40 hover:shadow-brand"
            style={{ animationDelay: `${i * 70}ms` }}
          >
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">{kpi.label}</span>
              <span className="bg-brand/12 flex size-8 items-center justify-center rounded-lg text-brand">
                <kpi.icon className="size-4" />
              </span>
            </div>
            <div className="mt-3 text-2xl font-semibold tabular-nums tracking-tight">
              {analytics.isLoading ? <Loader2 className="size-5 animate-spin text-muted-foreground" /> : kpi.value}
            </div>
            {kpi.sub && <div className="mt-1 text-xs text-muted-foreground">{kpi.sub}</div>}
          </Card>
        ))}
      </div>

      <Card className="mt-6">
        <div className="flex items-center justify-between border-b border-border/60 px-6 py-4">
          <h2 className="font-semibold">최근 게시물</h2>
          {!postsQ.isLoading && <span className="text-sm text-muted-foreground">{recent.length}개</span>}
        </div>
        {postsQ.isLoading ? (
          <div className="flex items-center justify-center gap-2 py-14 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" /> 불러오는 중…
          </div>
        ) : recent.length === 0 ? (
          <div className="py-14 text-center text-sm text-muted-foreground">
            아직 게시물이 없어요. AI 생성에서 콘텐츠를 만들어 저장해 보세요.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border/60 text-left text-xs uppercase tracking-wide text-muted-foreground">
                  <th className="px-6 py-3 font-medium">콘텐츠</th>
                  <th className="px-4 py-3 font-medium">상태</th>
                  <th className="px-6 py-3 font-medium">예약/발행</th>
                </tr>
              </thead>
              <tbody>
                {recent.map((p: Post) => {
                  const meta = POST_STATUS_META[p.status];
                  return (
                    <tr
                      key={p.id}
                      className="cursor-pointer border-b border-border/60 last:border-0 hover:bg-accent/40"
                      onClick={() => setSelected(p)}
                    >
                      <td className="max-w-xl truncate px-6 py-3.5 font-medium">{p.content}</td>
                      <td className="px-4 py-3.5">
                        <Badge variant={meta.variant}>{meta.label}</Badge>
                      </td>
                      <td className="px-6 py-3.5 text-muted-foreground tabular-nums">
                        {fmt(p.publishedAt ?? p.scheduledAt)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <ImprovementsBoard posts={postsQ.data ?? []} onSelect={setSelected} />

      <IdeaBoard />

      <PostDetailDialog post={selected} onOpenChange={(o) => !o && setSelected(null)} />
    </div>
  );
}

function ImprovementsBoard({ posts, onSelect }: { posts: Post[]; onSelect: (p: Post) => void }) {
  const { data, isLoading } = useQuery({ queryKey: ["improvements"], queryFn: () => postsApi.improvements(60) });
  const items = data ?? [];
  if (!isLoading && items.length === 0) return null;
  return (
    <Card className="mt-6 p-6">
      <div className="flex items-center gap-2">
        <Sparkles className="size-4 text-amber-500" />
        <h2 className="font-semibold">개선이 필요한 글</h2>
      </div>
      <p className="mt-0.5 text-sm text-muted-foreground">관심도 60 미만 — 한 번만 다듬으면 반응이 올라가요.</p>
      {isLoading ? (
        <div className="flex items-center gap-2 py-8 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> 불러오는 중…
        </div>
      ) : (
        <ul className="mt-4 divide-y divide-border/60">
          {items.map((it) => {
            const full = posts.find((p) => p.id === it.id);
            return (
              <li key={it.id} className="flex items-start gap-3 py-3">
                <ScoreBadge score={it.score} />
                <div className="min-w-0 flex-1">
                  <p className="line-clamp-1 text-sm font-medium">{it.content}</p>
                  <ul className="mt-1 space-y-0.5">
                    {it.tips.map((t, i) => (
                      <li key={i} className="flex items-start gap-1.5 text-xs text-muted-foreground">
                        <Lightbulb className="mt-0.5 size-3 shrink-0 text-amber-500" /> {t}
                      </li>
                    ))}
                  </ul>
                </div>
                {full && (
                  <Button variant="outline" size="sm" className="shrink-0 gap-1.5" onClick={() => onSelect(full)}>
                    <Pencil className="size-3.5" /> 편집
                  </Button>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}

function IdeaBoard() {
  const [page, setPage] = useState(0);
  const { data, isLoading, isFetching } = useQuery({
    queryKey: ["ideas", page],
    queryFn: () => contentApi.ideas(5, page),
  });
  return (
    <Card className="mt-6 p-6">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Lightbulb className="size-4 text-amber-500" />
          <h2 className="font-semibold">오늘의 게시글 추천</h2>
        </div>
        <Button variant="ghost" size="sm" className="gap-1.5" disabled={isFetching} onClick={() => setPage((p) => p + 1)}>
          <RefreshCw className={`size-3.5 ${isFetching ? "animate-spin" : ""}`} /> 다른 주제
        </Button>
      </div>
      <p className="mt-0.5 text-sm text-muted-foreground">바로 쓸 만한 주제와 가장 관심을 끌 훅이에요.</p>
      {isLoading ? (
        <div className="flex items-center gap-2 py-8 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> 불러오는 중…
        </div>
      ) : (
        <ul className="mt-4 divide-y divide-border/60">
          {(data ?? []).map((idea) => (
            <li key={idea.topic} className="flex items-center gap-3 py-3">
              <ScoreBadge score={idea.topHook.score} />
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium">{idea.topic}</div>
                <div className="truncate text-xs text-muted-foreground">“{idea.topHook.hook}”</div>
              </div>
              <Button asChild variant="outline" size="sm" className="shrink-0 gap-1.5">
                <Link to={`/content/generate?topic=${encodeURIComponent(idea.topic)}`}>
                  <Wand2 className="size-3.5" /> 생성
                </Link>
              </Button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
