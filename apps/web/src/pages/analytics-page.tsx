import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Activity, Download, Eye, Heart, Loader2, MessageCircle, Repeat2, Quote, Share2 } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { analyticsApi, type AnalyticsDashboard } from "@/lib/analytics-api";
import { roiApi } from "@/lib/roi-api";
import { RoiView } from "@/components/roi-view";
import { toCsv, downloadCsv } from "@/lib/csv";
import { cn } from "@/lib/utils";

const nf = new Intl.NumberFormat("ko-KR");

const PERIODS = [
  { label: "7일", days: 7 },
  { label: "30일", days: 30 },
  { label: "90일", days: 90 },
  { label: "전체", days: 0 },
] as const;

export function AnalyticsPage() {
  const [tab, setTab] = useState<"engagement" | "roi">("engagement");
  const [days, setDays] = useState<number>(0);
  const { data, isLoading, isError } = useQuery({
    queryKey: ["analytics", days],
    queryFn: () => analyticsApi.dashboard(days),
  });

  const exportCsv = async () => {
    if (tab === "roi") {
      const r = await roiApi.dashboard(days);
      const csv = toCsv(
        ["게시물", "매출", "전환수", "비용", "ROI%"],
        r.topByRevenue.map((p) => [p.content.replace(/\n/g, " "), p.revenue, p.conversions, p.cost, p.roiPercent ?? ""]),
      );
      downloadCsv(`postflow-roi-${days || "all"}.csv`, csv);
    } else {
      const d = data ?? (await analyticsApi.dashboard(days));
      const csv = toCsv(
        ["게시물", "조회수", "좋아요", "댓글"],
        d.topPosts.map((p) => [p.content.replace(/\n/g, " "), p.views, p.likes, p.replies]),
      );
      downloadCsv(`postflow-analytics-${days || "all"}.csv`, csv);
    }
  };

  return (
    <div className="w-full px-6 py-7 lg:px-8 xl:px-10">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">분석</h1>
          <p className="mt-1 text-sm text-muted-foreground">게시물 성과를 한눈에 — 참여와 수익(ROI).</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" className="gap-1.5" onClick={exportCsv}>
            <Download className="size-4" /> CSV
          </Button>
          <div className="flex items-center rounded-lg border bg-background p-0.5">
            {PERIODS.map((pp) => (
              <button
                key={pp.days}
                onClick={() => setDays(pp.days)}
                className={cn(
                  "rounded-md px-2.5 py-1.5 text-xs transition-colors",
                  days === pp.days ? "bg-accent font-medium text-foreground" : "text-muted-foreground hover:text-foreground",
                )}
              >
                {pp.label}
              </button>
            ))}
          </div>
          <div className="flex items-center rounded-lg border bg-background p-0.5">
            {([["engagement", "참여"], ["roi", "수익·ROI"]] as const).map(([k, label]) => (
              <button
                key={k}
                onClick={() => setTab(k)}
                className={cn(
                  "rounded-md px-3 py-1.5 text-sm transition-colors",
                  tab === k ? "bg-brand-gradient text-brand-foreground" : "text-muted-foreground hover:text-foreground",
                )}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {tab === "roi" ? (
        <RoiView days={days} />
      ) : isLoading ? (
        <div className="flex items-center justify-center gap-2 py-20 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> 불러오는 중…
        </div>
      ) : isError || !data ? (
        <Card className="py-20 text-center text-sm text-destructive">분석 데이터를 불러오지 못했어요.</Card>
      ) : (
        <Dashboard d={data} />
      )}
    </div>
  );
}

function Dashboard({ d }: { d: AnalyticsDashboard }) {
  const kpis = [
    { label: "총 조회수", value: d.views, icon: Eye },
    { label: "좋아요", value: d.likes, icon: Heart },
    { label: "댓글", value: d.replies, icon: MessageCircle },
    { label: "공유", value: d.shares, icon: Share2 },
    { label: "참여율", value: `${(d.engagementRate * 100).toFixed(1)}%`, icon: Activity, raw: true },
  ];

  const engagement = [
    { label: "좋아요", value: d.likes, icon: Heart, color: "bg-rose-500" },
    { label: "댓글", value: d.replies, icon: MessageCircle, color: "bg-sky-500" },
    { label: "리포스트", value: d.reposts, icon: Repeat2, color: "bg-emerald-500" },
    { label: "인용", value: d.quotes, icon: Quote, color: "bg-amber-500" },
    { label: "공유", value: d.shares, icon: Share2, color: "bg-violet-500" },
  ];
  const engMax = Math.max(1, ...engagement.map((e) => e.value));
  const topMax = Math.max(1, ...d.topPosts.map((p) => p.views));
  const hasData = d.views > 0 || d.topPosts.some((p) => p.views > 0);

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-5">
        {kpis.map((k, i) => (
          <Card
            key={k.label}
            className="lift animate-fade-up p-5 hover:border-brand/40 hover:shadow-brand"
            style={{ animationDelay: `${i * 70}ms` }}
          >
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">{k.label}</span>
              <span className="bg-brand/12 flex size-8 items-center justify-center rounded-lg text-brand">
                <k.icon className="size-4" />
              </span>
            </div>
            <div className="mt-3 text-2xl font-semibold tabular-nums tracking-tight">
              {k.raw ? (k.value as string) : nf.format(k.value as number)}
            </div>
          </Card>
        ))}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="p-6">
          <h2 className="font-semibold">참여 분해</h2>
          <p className="mt-0.5 text-sm text-muted-foreground">상호작용 유형별 합계</p>
          <div className="mt-5 space-y-4">
            {engagement.map((e) => (
              <div key={e.label}>
                <div className="mb-1 flex items-center justify-between text-sm">
                  <span className="flex items-center gap-1.5 text-muted-foreground">
                    <e.icon className="size-3.5" /> {e.label}
                  </span>
                  <span className="font-medium tabular-nums">{nf.format(e.value)}</span>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-muted">
                  <div
                    className={`h-full rounded-full ${e.color} transition-[width] duration-500`}
                    style={{ width: `${(e.value / engMax) * 100}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </Card>

        <Card className="p-6">
          <h2 className="font-semibold">상위 게시물</h2>
          <p className="mt-0.5 text-sm text-muted-foreground">조회수 기준 Top 5</p>
          {hasData ? (
            <div className="mt-5 space-y-4">
              {d.topPosts.map((p, i) => (
                <div key={p.postId}>
                  <div className="mb-1 flex items-center gap-2 text-sm">
                    <span className="text-gradient-brand w-5 shrink-0 font-bold tabular-nums">{i + 1}</span>
                    <span className="min-w-0 flex-1 truncate">{p.content}</span>
                    <span className="shrink-0 font-medium tabular-nums">{nf.format(p.views)}</span>
                  </div>
                  <div className="ml-7 h-2 overflow-hidden rounded-full bg-muted">
                    <div
                      className="bg-brand-gradient h-full rounded-full transition-[width] duration-500"
                      style={{ width: `${(p.views / topMax) * 100}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="mt-5 rounded-lg border border-dashed py-10 text-center text-sm text-muted-foreground">
              아직 성과 데이터가 없어요. Threads 연결 후 발행하면 수집됩니다.
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
