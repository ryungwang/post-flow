import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Eye, Heart, Loader2, MessageCircle, Sparkles, TrendingUp, Users } from "lucide-react";
import { threadsApi, type DemoEntry, type ThreadsAccountPost } from "@/lib/threads-api";
import { AccountSelector } from "@/components/account-selector";
import { useThreadsAccount } from "@/store/threads-account";
import { cn } from "@/lib/utils";

/** 참여율 = (좋아요+댓글+리포스트+인용) / 조회. 조회 0이면 0. */
function engagementRate(p: ThreadsAccountPost) {
  const inter = (p.likes ?? 0) + (p.replies ?? 0) + (p.reposts ?? 0) + (p.quotes ?? 0);
  const views = p.views ?? 0;
  return views > 0 ? inter / views : 0;
}
const nf = (n: number) => n.toLocaleString();
const pct = (n: number) => `${(n * 100).toFixed(1)}%`;
const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

function Kpi({ icon: Icon, label, value, sub }: { icon: React.ComponentType<{ className?: string }>; label: string; value: string; sub?: string }) {
  return (
    <div className="rounded-xl border bg-card/40 p-4">
      <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <Icon className="size-4" /> {label}
      </div>
      <div className="mt-1.5 text-2xl font-bold tracking-tight">{value}</div>
      {sub && <div className="mt-0.5 text-xs text-muted-foreground">{sub}</div>}
    </div>
  );
}

function BarList({ title, entries, unit }: { title: string; entries: DemoEntry[]; unit?: string }) {
  const max = Math.max(1, ...entries.map((e) => e.value));
  return (
    <div className="rounded-xl border bg-card/40 p-4">
      <h3 className="mb-3 text-sm font-semibold">{title}</h3>
      {entries.length === 0 ? (
        <p className="py-6 text-center text-xs text-muted-foreground">데이터 없음 (팔로워 100명 이상부터 제공)</p>
      ) : (
        <ul className="space-y-2">
          {entries.slice(0, 6).map((e) => (
            <li key={e.label} className="text-xs">
              <div className="mb-1 flex justify-between">
                <span className="font-medium">{e.label}</span>
                <span className="text-muted-foreground">{nf(e.value)}{unit}</span>
              </div>
              <div className="h-1.5 overflow-hidden rounded-full bg-muted">
                <div className="h-full rounded-full bg-brand" style={{ width: `${(e.value / max) * 100}%` }} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function ThreadsInsightsPage() {
  const accountId = useThreadsAccount((s) => s.accountId);
  const [dayRange, setDayRange] = useState<"2w" | "all">("2w"); // 요일별 차트 기간
  const postsQ = useQuery({ queryKey: ["threads-insights-posts", accountId], queryFn: () => threadsApi.posts({ limit: 30, accountId }) });
  const insQ = useQuery({ queryKey: ["threads-insights", accountId], queryFn: () => threadsApi.insights(accountId ?? undefined) });

  const posts = postsQ.data?.posts ?? [];
  const withViews = posts.filter((p) => p.views != null);

  // ── 총계 & 참여율 (게시물 지표 합산) ──
  const sum = (f: (p: ThreadsAccountPost) => number) => posts.reduce((a, p) => a + f(p), 0);
  const totalViews = sum((p) => p.views ?? 0);
  const totalLikes = sum((p) => p.likes ?? 0);
  const totalReplies = sum((p) => p.replies ?? 0);
  const totalInter = totalLikes + totalReplies + sum((p) => (p.reposts ?? 0) + (p.quotes ?? 0));
  const avgEng = totalViews > 0 ? totalInter / totalViews : 0;

  // ── 베스트 게시물 TOP 5 (참여율) ──
  const best = [...withViews].sort((a, b) => engagementRate(b) - engagementRate(a)).slice(0, 5);

  // ── 요일별 평균 참여율 (서버 기간 조회: 최근 2주 / 전체) ──
  const dayQ = useQuery({
    queryKey: ["threads-day-engagement", accountId, dayRange],
    queryFn: () => threadsApi.dayEngagement(dayRange === "2w" ? 14 : 0, accountId),
  });
  const dayStats = dayQ.data?.stats ?? [];
  const daySampled = dayQ.data?.sampled ?? 0;
  const byDay = WEEKDAYS.map((label, i) => {
    const s = dayStats.find((x) => x.weekday === i);
    return { label, value: s?.avgEngagement ?? 0, count: s?.count ?? 0 };
  });
  const maxDay = Math.max(0.0001, ...byDay.map((d) => d.value));

  // ── PostFlow vs 외부 ──
  const grp = (fromPf: boolean) => {
    const ps = withViews.filter((p) => p.fromPostflow === fromPf);
    const eng = ps.length ? ps.reduce((a, p) => a + engagementRate(p), 0) / ps.length : 0;
    const views = ps.length ? ps.reduce((a, p) => a + (p.views ?? 0), 0) / ps.length : 0;
    return { count: ps.length, eng, views };
  };
  const pf = grp(true);
  const ext = grp(false);

  const ins = insQ.data;
  const loading = postsQ.isLoading || insQ.isLoading;

  return (
    <div className="mx-auto max-w-5xl p-6">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Threads 인사이트</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            연결된 Threads 계정의 실제 성과 데이터예요. 팔로워·참여율·인구통계와 어떤 글이 잘 되는지 한눈에.
          </p>
        </div>
        <div className="shrink-0"><AccountSelector /></div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-24">
          <Loader2 className="size-5 animate-spin text-muted-foreground" />
        </div>
      ) : (
        <div className="mt-6 space-y-6">
          {/* KPI */}
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Kpi icon={Users} label="팔로워" value={ins?.followers != null ? nf(ins.followers) : "—"} />
            <Kpi icon={Eye} label="총 조회수" value={nf(totalViews)} sub={`게시물 ${posts.length}개`} />
            <Kpi icon={Heart} label="총 좋아요" value={nf(totalLikes)} sub={`댓글 ${nf(totalReplies)}`} />
            <Kpi icon={TrendingUp} label="평균 참여율" value={pct(avgEng)} sub="(상호작용/조회)" />
          </div>

          {/* 인구통계 — 전부 비면(팔로워 100 미만) 4칸 대신 한 줄 안내로 축약 */}
          {(() => {
            const d = ins?.demographics;
            const anyDemo = !!d && [d.age, d.gender, d.country, d.city].some((e) => e && e.length > 0);
            return (
              <div>
                <h2 className="mb-3 text-sm font-semibold text-muted-foreground">팔로워 인구통계</h2>
                {anyDemo ? (
                  <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-4">
                    <BarList title="연령대" entries={d!.age} unit="명" />
                    <BarList title="성별" entries={d!.gender} unit="명" />
                    <BarList title="국가" entries={d!.country} unit="명" />
                    <BarList title="도시" entries={d!.city} unit="명" />
                  </div>
                ) : (
                  <div className="flex items-center gap-2 rounded-xl border bg-card/40 px-4 py-3 text-sm text-muted-foreground">
                    <Users className="size-4 shrink-0" />
                    팔로워 100명 이상이 되면 연령·성별·국가·도시 인구통계가 여기에 표시돼요.
                  </div>
                )}
              </div>
            );
          })()}

          {/* 베스트 게시물 + PostFlow 비교 */}
          <div className="grid gap-3 lg:grid-cols-3">
            <div className="rounded-xl border bg-card/40 p-4 lg:col-span-2">
              <h3 className="mb-3 flex items-center gap-1.5 text-sm font-semibold">
                <Sparkles className="size-4 text-brand" /> 베스트 게시물 (참여율순)
              </h3>
              {best.length === 0 ? (
                <p className="py-6 text-center text-xs text-muted-foreground">지표가 있는 게시물이 아직 없어요.</p>
              ) : (
                <ol className="space-y-2.5">
                  {best.map((p, i) => (
                    <li key={p.id} className="flex items-start gap-3">
                      <span className="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full bg-brand/15 text-xs font-bold text-brand">
                        {i + 1}
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm">{p.text || "(텍스트 없음)"}</p>
                        <div className="mt-0.5 flex flex-wrap gap-3 text-xs text-muted-foreground">
                          <span className="font-medium text-brand">참여율 {pct(engagementRate(p))}</span>
                          <span className="inline-flex items-center gap-0.5"><Eye className="size-3" /> {nf(p.views ?? 0)}</span>
                          <span className="inline-flex items-center gap-0.5"><Heart className="size-3" /> {nf(p.likes ?? 0)}</span>
                          <span className="inline-flex items-center gap-0.5"><MessageCircle className="size-3" /> {nf(p.replies ?? 0)}</span>
                          {p.fromPostflow && <span className="text-emerald-500">✨ PostFlow</span>}
                        </div>
                      </div>
                    </li>
                  ))}
                </ol>
              )}
            </div>

            <div className="rounded-xl border bg-card/40 p-4">
              <h3 className="mb-3 text-sm font-semibold">PostFlow vs 외부</h3>
              <div className="space-y-3">
                {[
                  { name: "✨ PostFlow 발행", g: pf, accent: "text-emerald-500" },
                  { name: "외부 게시", g: ext, accent: "text-muted-foreground" },
                ].map((r) => (
                  <div key={r.name} className="rounded-lg border p-3">
                    <div className={`text-xs font-semibold ${r.accent}`}>{r.name} · {r.g.count}개</div>
                    <div className="mt-1.5 flex justify-between text-xs">
                      <span className="text-muted-foreground">평균 참여율</span>
                      <span className="font-bold">{pct(r.g.eng)}</span>
                    </div>
                    <div className="mt-1 flex justify-between text-xs">
                      <span className="text-muted-foreground">평균 조회</span>
                      <span className="font-medium">{nf(Math.round(r.g.views))}</span>
                    </div>
                  </div>
                ))}
                {pf.count > 0 && ext.count > 0 && (
                  <p className="text-center text-xs text-muted-foreground">
                    {pf.eng >= ext.eng
                      ? "PostFlow 글이 외부보다 참여율이 높아요 🎉"
                      : "외부 글이 아직 앞서요 — 콘텐츠 개선 여지!"}
                  </p>
                )}
              </div>
            </div>
          </div>

          {/* 요일별 성과 */}
          <div className="rounded-xl border bg-card/40 p-4">
            <div className="mb-3 flex items-center justify-between gap-2">
              <h3 className="text-sm font-semibold">요일별 평균 참여율</h3>
              <div className="flex rounded-lg border p-0.5 text-xs">
                {([["2w", "최근 2주"], ["all", "전체"]] as const).map(([k, label]) => (
                  <button
                    key={k}
                    onClick={() => setDayRange(k)}
                    className={cn(
                      "rounded-md px-2.5 py-1 font-medium transition-colors",
                      dayRange === k ? "bg-brand text-brand-foreground" : "text-muted-foreground hover:text-foreground",
                    )}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>
            {dayQ.isLoading ? (
              <div className="flex justify-center py-10"><Loader2 className="size-5 animate-spin text-muted-foreground" /></div>
            ) : daySampled === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">
                {dayRange === "2w" ? "최근 2주에 지표 있는 게시물이 없어요. 전체로 보거나 글이 쌓이면 표시돼요." : "지표가 있는 게시물이 쌓이면 요일별 성과를 보여드려요."}
              </p>
            ) : (
              <>
                <div className="flex items-end gap-2">
                  {byDay.map((d) => {
                    const h = maxDay > 0 ? (d.value / maxDay) * 100 : 0;
                    return (
                      // 컬럼 전체를 hover 영역으로 + 커스텀 툴팁(빈 요일도 표시)
                      <div key={d.label} className="group relative flex flex-1 flex-col items-center gap-1">
                        <div className="pointer-events-none absolute -top-1 z-10 -translate-y-full whitespace-nowrap rounded-md border bg-popover px-2.5 py-1.5 text-xs opacity-0 shadow-md transition-opacity group-hover:opacity-100">
                          <span className="font-semibold">{d.label}요일</span>
                          {d.count > 0 ? (
                            <> · 참여율 <span className="font-semibold text-brand">{pct(d.value)}</span> · {d.count}개</>
                          ) : (
                            <> · 게시물 없음</>
                          )}
                        </div>
                        <div className="flex h-32 w-full items-end justify-center">
                          <div
                            className={cn("w-full max-w-[44px] rounded-t transition-all group-hover:opacity-90",
                              d.count ? "bg-brand/70" : "bg-muted")}
                            style={{ height: `${d.count ? Math.max(h, 8) : 3}%` }}
                          />
                        </div>
                        <span className="text-xs text-muted-foreground">{d.label}</span>
                        <span className="text-[10px] text-muted-foreground/70">{d.count || ""}</span>
                      </div>
                    );
                  })}
                </div>
                <p className="mt-2 text-center text-xs text-muted-foreground">
                  {dayRange === "2w" ? "최근 2주" : "최근 게시물"} 기준 · 막대에 마우스를 올리면 상세가 보여요(아래 숫자=게시물 수).
                </p>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
