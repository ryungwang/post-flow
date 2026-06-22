import { useEffect, useState } from "react";
import {
  Eye,
  FileText,
  Heart,
  MessageCircle,
  TrendingUp,
  Users,
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { pingApi } from "@/lib/api";

const nf = new Intl.NumberFormat("ko-KR");

type Kpi = {
  label: string;
  value: number;
  delta: number;
  icon: React.ComponentType<{ className?: string }>;
};

const KPIS: Kpi[] = [
  { label: "총 게시물", value: 128, delta: 12, icon: FileText },
  { label: "총 조회수", value: 84210, delta: 8.4, icon: Eye },
  { label: "총 좋아요", value: 6230, delta: 5.1, icon: Heart },
  { label: "총 댓글", value: 1180, delta: -2.3, icon: MessageCircle },
  { label: "팔로워 증가", value: 942, delta: 18.6, icon: Users },
];

type Status = "published" | "scheduled" | "draft" | "failed";

const RECENT: {
  content: string;
  status: Status;
  time: string;
  views: number;
  likes: number;
  comments: number;
}[] = [
  { content: "AI 스타트업이 흔히 저지르는 3가지 실수", status: "published", time: "오늘 09:12", views: 4210, likes: 312, comments: 48 },
  { content: "주말에 써먹는 생산성 루틴 5가지", status: "published", time: "어제 18:30", views: 3180, likes: 256, comments: 33 },
  { content: "개발자 사이드프로젝트, 어떻게 꾸준히?", status: "scheduled", time: "내일 08:00", views: 0, likes: 0, comments: 0 },
  { content: "여행 사진 잘 찍는 구도 팁", status: "scheduled", time: "6/24 12:00", views: 0, likes: 0, comments: 0 },
  { content: "Threads 알고리즘 정리 (2026)", status: "draft", time: "—", views: 0, likes: 0, comments: 0 },
  { content: "런칭 회고: 첫 결제까지의 30일", status: "failed", time: "6/21 21:40", views: 0, likes: 0, comments: 0 },
];

const STATUS_META: Record<Status, { label: string; variant: "success" | "info" | "muted" | "warning" }> = {
  published: { label: "발행됨", variant: "success" },
  scheduled: { label: "예약됨", variant: "info" },
  draft: { label: "초안", variant: "muted" },
  failed: { label: "실패", variant: "warning" },
};

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
  return (
    <div className="w-full px-6 py-7 lg:px-8 xl:px-10">
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">대시보드</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            지난 30일 성과 요약 · Threads
          </p>
        </div>
        <ApiBadge />
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-5">
        {KPIS.map((kpi, i) => {
          const positive = kpi.delta >= 0;
          return (
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
                {nf.format(kpi.value)}
              </div>
              <div className="mt-1 flex items-center gap-1 text-xs">
                <TrendingUp
                  className={`size-3.5 ${positive ? "text-emerald-600 dark:text-emerald-400" : "rotate-180 text-amber-600 dark:text-amber-400"}`}
                />
                <span
                  className={`font-medium tabular-nums ${positive ? "text-emerald-700 dark:text-emerald-400" : "text-amber-700 dark:text-amber-400"}`}
                >
                  {positive ? "+" : ""}
                  {kpi.delta}%
                </span>
                <span className="text-muted-foreground">vs 지난달</span>
              </div>
            </Card>
          );
        })}
      </div>

      <Card className="mt-6">
        <div className="flex items-center justify-between border-b px-6 py-4">
          <h2 className="font-semibold">최근 게시물</h2>
          <span className="text-sm text-muted-foreground">{RECENT.length}개</span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-xs uppercase tracking-wide text-muted-foreground">
                <th className="px-6 py-3 font-medium">콘텐츠</th>
                <th className="px-4 py-3 font-medium">상태</th>
                <th className="px-4 py-3 font-medium">발행 시각</th>
                <th className="px-4 py-3 text-right font-medium">조회</th>
                <th className="px-4 py-3 text-right font-medium">좋아요</th>
                <th className="px-6 py-3 text-right font-medium">댓글</th>
              </tr>
            </thead>
            <tbody>
              {RECENT.map((p, i) => {
                const meta = STATUS_META[p.status];
                return (
                  <tr key={i} className="border-b last:border-0 hover:bg-accent/40">
                    <td className="max-w-md truncate px-6 py-3.5 font-medium">{p.content}</td>
                    <td className="px-4 py-3.5">
                      <Badge variant={meta.variant}>{meta.label}</Badge>
                    </td>
                    <td className="px-4 py-3.5 text-muted-foreground">{p.time}</td>
                    <td className="px-4 py-3.5 text-right tabular-nums">{nf.format(p.views)}</td>
                    <td className="px-4 py-3.5 text-right tabular-nums">{nf.format(p.likes)}</td>
                    <td className="px-6 py-3.5 text-right tabular-nums">{nf.format(p.comments)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
