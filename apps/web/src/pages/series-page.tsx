import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { BookmarkPlus, CalendarRange, Check, Loader2, Sparkles } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { seriesApi, type SeriesItem } from "@/lib/series-api";
import { postsApi } from "@/lib/posts-api";
import { ApiError } from "@/lib/api";

const DAYS = [7, 14, 30];

export function SeriesPage() {
  const [topic, setTopic] = useState("");
  const [days, setDays] = useState(7);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [items, setItems] = useState<SeriesItem[] | null>(null);

  const generate = async () => {
    if (!topic.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const res = await seriesApi.generate(topic.trim(), days);
      setItems(res.items);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) setError("로그인이 필요해요.");
      else if (e instanceof ApiError && e.status >= 500)
        setError("생성 중 오류가 발생했어요. AI 키 설정 또는 잠시 후 다시 시도해 주세요.");
      else setError("생성에 실패했어요. 다시 시도해 주세요.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-7xl px-6 py-7">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">시리즈 생성</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          주제 하나로 7·14·30일 콘텐츠 플랜을 한 번에 만듭니다.
        </p>
      </div>

      <Card className="p-6">
        <div className="grid gap-5">
          <div className="grid gap-2">
            <Label htmlFor="topic">주제</Label>
            <Input
              id="topic"
              placeholder="예: AI 스타트업 30일 성장기"
              value={topic}
              onChange={(e) => setTopic(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && generate()}
            />
          </div>
          <div className="grid gap-2 sm:max-w-xs">
            <Label>기간</Label>
            <div className="flex h-9 items-center rounded-md border bg-background p-0.5">
              {DAYS.map((d) => (
                <button
                  key={d}
                  type="button"
                  onClick={() => setDays(d)}
                  className={`h-8 flex-1 rounded-[5px] text-sm font-medium transition-colors ${
                    days === d ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground"
                  }`}
                >
                  {d}일
                </button>
              ))}
            </div>
          </div>
          <div className="flex items-center gap-3">
            <Button onClick={generate} disabled={loading || !topic.trim()} className="gap-2">
              {loading ? <Loader2 className="size-4 animate-spin" /> : <CalendarRange className="size-4" />}
              {loading ? "생성 중…" : `${days}일 플랜 생성`}
            </Button>
            {error && <span className="text-sm text-destructive">{error}</span>}
          </div>
        </div>
      </Card>

      <div className="mt-6">
        {loading && (
          <div className="grid gap-3">
            {Array.from({ length: Math.min(days, 5) }).map((_, i) => (
              <Card key={i} className="h-20 animate-pulse p-5">
                <div className="h-3 w-1/3 rounded bg-muted" />
                <div className="mt-3 h-3 w-2/3 rounded bg-muted" />
              </Card>
            ))}
          </div>
        )}

        {!loading && items && items.length > 0 && (
          <>
            <h2 className="mb-3 font-semibold">{items.length}일 플랜</h2>
            <div className="grid gap-3">
              {items.map((it) => (
                <SeriesRow key={it.day} item={it} />
              ))}
            </div>
          </>
        )}

        {!loading && !items && (
          <Card className="flex flex-col items-center justify-center gap-3 px-6 py-16 text-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
              <Sparkles className="size-6" />
            </div>
            <p className="text-sm text-muted-foreground">
              주제와 기간을 정하고 생성하면 일자별 플랜이 여기에 나타납니다.
            </p>
          </Card>
        )}
      </div>
    </div>
  );
}

function SeriesRow({ item }: { item: SeriesItem }) {
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const qc = useQueryClient();

  const save = async () => {
    setSaving(true);
    try {
      await postsApi.create({ content: item.content });
      qc.invalidateQueries({ queryKey: ["posts"] });
      setSaved(true);
    } catch {
      // keep silent; row stays actionable
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="flex items-start gap-4 p-5">
      <div className="flex size-12 shrink-0 flex-col items-center justify-center rounded-md bg-secondary text-secondary-foreground">
        <span className="text-[10px] uppercase text-muted-foreground">Day</span>
        <span className="text-lg font-semibold leading-none tabular-nums">{item.day}</span>
      </div>
      <div className="min-w-0 flex-1">
        <h3 className="font-semibold">{item.title}</h3>
        <p className="mt-1 whitespace-pre-wrap text-sm leading-relaxed text-muted-foreground">
          {item.content}
        </p>
        {item.hashtags?.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1">
            {item.hashtags.map((h, i) => (
              <Badge key={i} variant="secondary">#{h}</Badge>
            ))}
          </div>
        )}
      </div>
      <Button variant="outline" size="sm" className="shrink-0 gap-1.5" disabled={saving || saved} onClick={save}>
        {saving ? <Loader2 className="size-4 animate-spin" /> : saved ? <Check className="size-4" /> : <BookmarkPlus className="size-4" />}
        {saved ? "저장됨" : "저장"}
      </Button>
    </Card>
  );
}
