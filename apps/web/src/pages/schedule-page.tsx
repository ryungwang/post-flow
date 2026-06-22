import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { CalendarDays, ChevronLeft, ChevronRight, List } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { PostDetailDialog } from "@/components/post-detail-dialog";
import { ScoreBadge } from "@/components/score-badge";
import { postsApi, type Post } from "@/lib/posts-api";
import { POST_STATUS_META } from "@/lib/post-status";
import { cn } from "@/lib/utils";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
const DOT: Record<string, string> = {
  SCHEDULED: "bg-sky-500",
  PUBLISHED: "bg-emerald-500",
  PUBLISHING: "bg-amber-500",
  FAILED: "bg-rose-500",
  RECONNECT_REQUIRED: "bg-amber-500",
  DRAFT: "bg-muted-foreground",
};

function dateOf(p: Post): Date | null {
  const iso = p.scheduledAt ?? p.publishedAt;
  return iso ? new Date(iso) : null;
}
function key(d: Date) {
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}
function sameMonth(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth();
}

export function SchedulePage() {
  const { data } = useQuery({ queryKey: ["posts"], queryFn: postsApi.list });
  const [cursor, setCursor] = useState(() => new Date());
  const [view, setView] = useState<"calendar" | "list">("calendar");
  const [dayDate, setDayDate] = useState<Date | null>(null);
  const [selectedPost, setSelectedPost] = useState<Post | null>(null);

  const today = new Date();
  const posts = data ?? [];

  const byDay = useMemo(() => {
    const map = new Map<string, Post[]>();
    for (const p of posts) {
      const d = dateOf(p);
      if (!d) continue;
      const k = key(d);
      (map.get(k) ?? map.set(k, []).get(k)!).push(p);
    }
    return map;
  }, [posts]);

  const cells = useMemo(() => {
    const year = cursor.getFullYear();
    const month = cursor.getMonth();
    const startOffset = new Date(year, month, 1).getDay();
    return Array.from({ length: 42 }, (_, i) => new Date(year, month, 1 - startOffset + i));
  }, [cursor]);

  const upcoming = useMemo(
    () =>
      posts
        .filter((p) => p.scheduledAt && p.status === "SCHEDULED")
        .sort((a, b) => (a.scheduledAt! < b.scheduledAt! ? -1 : 1)),
    [posts],
  );

  const monthLabel = `${cursor.getFullYear()}년 ${cursor.getMonth() + 1}월`;
  const shift = (n: number) => setCursor(new Date(cursor.getFullYear(), cursor.getMonth() + n, 1));

  return (
    <div className="w-full px-6 py-7 lg:px-8 xl:px-10">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">스케줄</h1>
          <p className="mt-1 text-sm text-muted-foreground">예약·자동 발행 일정을 한눈에.</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex items-center rounded-lg border bg-background p-0.5">
            <button
              onClick={() => setView("calendar")}
              className={cn(
                "flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm transition-colors",
                view === "calendar" ? "bg-brand-gradient text-brand-foreground" : "text-muted-foreground hover:text-foreground",
              )}
            >
              <CalendarDays className="size-4" /> 캘린더
            </button>
            <button
              onClick={() => setView("list")}
              className={cn(
                "flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm transition-colors",
                view === "list" ? "bg-brand-gradient text-brand-foreground" : "text-muted-foreground hover:text-foreground",
              )}
            >
              <List className="size-4" /> 목록
            </button>
          </div>
        </div>
      </div>

      {view === "calendar" ? (
        <Card className="overflow-hidden p-0">
          <div className="flex items-center justify-between border-b border-border/60 px-5 py-3.5">
            <h2 className="text-lg font-semibold tabular-nums">{monthLabel}</h2>
            <div className="flex items-center gap-1">
              <Button variant="ghost" size="icon" aria-label="이전 달" onClick={() => shift(-1)}>
                <ChevronLeft className="size-4" />
              </Button>
              <Button variant="outline" size="sm" onClick={() => setCursor(new Date())}>
                오늘
              </Button>
              <Button variant="ghost" size="icon" aria-label="다음 달" onClick={() => shift(1)}>
                <ChevronRight className="size-4" />
              </Button>
            </div>
          </div>
          <div className="grid grid-cols-7 border-b border-border/60 text-center text-xs font-medium text-muted-foreground">
            {WEEKDAYS.map((w, i) => (
              <div key={w} className={cn("py-2", i === 0 && "text-rose-500", i === 6 && "text-sky-500")}>
                {w}
              </div>
            ))}
          </div>
          <div className="grid grid-cols-7">
            {cells.map((d, i) => {
              const inMonth = sameMonth(d, cursor);
              const isToday = key(d) === key(today);
              const items = byDay.get(key(d)) ?? [];
              const hasItems = items.length > 0;
              return (
                <div
                  key={i}
                  onClick={() => hasItems && setDayDate(d)}
                  className={cn(
                    "min-h-28 border-b border-r border-border/50 p-1.5 last:border-r-0 [&:nth-child(7n)]:border-r-0",
                    !inMonth && "bg-muted/30 text-muted-foreground",
                    hasItems && "cursor-pointer transition-colors hover:bg-accent/40",
                  )}
                >
                  <div className="mb-1 flex justify-end px-1">
                    <span
                      className={cn(
                        "flex size-6 items-center justify-center rounded-full text-xs tabular-nums",
                        isToday && "bg-brand-gradient font-semibold text-brand-foreground",
                      )}
                    >
                      {d.getDate()}
                    </span>
                  </div>
                  <div className="space-y-1">
                    {items.slice(0, 3).map((p) => (
                      <div
                        key={p.id}
                        title={p.content}
                        className="flex items-center gap-1 truncate rounded-md bg-accent/60 px-1.5 py-1 text-[11px]"
                      >
                        <span className={cn("size-1.5 shrink-0 rounded-full", DOT[p.status] ?? "bg-muted-foreground")} />
                        <span className="truncate">{p.content}</span>
                      </div>
                    ))}
                    {items.length > 3 && (
                      <div className="px-1.5 text-[11px] text-muted-foreground">+{items.length - 3}건 더</div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </Card>
      ) : (
        <Card>
          <div className="flex items-center justify-between border-b border-border/60 px-6 py-4">
            <h2 className="font-semibold">예정된 발행</h2>
            <span className="text-sm text-muted-foreground">{upcoming.length}건</span>
          </div>
          {upcoming.length === 0 ? (
            <div className="flex flex-col items-center justify-center gap-3 py-16 text-center">
              <div className="flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
                <CalendarDays className="size-6" />
              </div>
              <p className="text-sm text-muted-foreground">예약된 발행이 없어요.</p>
            </div>
          ) : (
            <ul className="divide-y divide-border/60">
              {upcoming.map((p) => (
                <li
                  key={p.id}
                  onClick={() => setSelectedPost(p)}
                  className="flex cursor-pointer items-center gap-4 px-6 py-3.5 hover:bg-accent/40"
                >
                  <div className="w-32 shrink-0 text-sm tabular-nums text-muted-foreground">
                    {new Intl.DateTimeFormat("ko-KR", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(p.scheduledAt!))}
                  </div>
                  <div className="min-w-0 flex-1 truncate text-sm font-medium">{p.content}</div>
                  <Badge variant={POST_STATUS_META[p.status].variant}>{POST_STATUS_META[p.status].label}</Badge>
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}

      {/* day list modal */}
      <Dialog open={!!dayDate} onOpenChange={(o) => !o && setDayDate(null)}>
        <DialogContent>
          {dayDate && (
            <>
              <DialogHeader>
                <DialogTitle>
                  {new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric", weekday: "short" }).format(dayDate)}
                </DialogTitle>
                <DialogDescription>{(byDay.get(key(dayDate)) ?? []).length}개 게시물 · 클릭하면 상세보기</DialogDescription>
              </DialogHeader>
              <ul className="max-h-[55vh] divide-y divide-border/60 overflow-y-auto">
                {(byDay.get(key(dayDate)) ?? []).map((p) => (
                  <li
                    key={p.id}
                    onClick={() => { setDayDate(null); setSelectedPost(p); }}
                    className="flex cursor-pointer items-start gap-3 py-3 hover:bg-accent/40"
                  >
                    <span className={cn("mt-1.5 size-2 shrink-0 rounded-full", DOT[p.status] ?? "bg-muted-foreground")} />
                    <div className="min-w-0 flex-1">
                      <p className="line-clamp-2 text-sm font-medium">{p.content}</p>
                      <div className="mt-1 flex items-center gap-2">
                        <Badge variant={POST_STATUS_META[p.status].variant}>{POST_STATUS_META[p.status].label}</Badge>
                        <ScoreBadge score={p.score} compact />
                        <span className="text-xs text-muted-foreground tabular-nums">
                          {(() => { const d = p.scheduledAt ?? p.publishedAt; return d ? new Intl.DateTimeFormat("ko-KR", { hour: "2-digit", minute: "2-digit" }).format(new Date(d)) : ""; })()}
                        </span>
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            </>
          )}
        </DialogContent>
      </Dialog>

      <PostDetailDialog post={selectedPost} onOpenChange={(o) => !o && setSelectedPost(null)} />
    </div>
  );
}
