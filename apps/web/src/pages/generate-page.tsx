import { useState } from "react";
import { Check, Copy, Loader2, Pencil, Sparkles, Trash2, Wand2 } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { contentApi, type GeneratedCard } from "@/lib/content-api";
import { ApiError } from "@/lib/api";

const TOPIC_CHIPS = ["AI", "스타트업", "개발", "생산성", "여행", "음식", "운동", "육아"];
const GOALS = ["Engagement", "Followers", "Leads", "Sales", "Awareness", "Personal Branding", "Fun"];
const TONES = ["Expert", "Friendly", "Storytelling", "Controversial", "Educational", "Casual", "Personal", "Humor"];
const QUANTITIES = [5, 10, 30];
const MAX = 500;

export function GeneratePage() {
  const [topic, setTopic] = useState("");
  const [goal, setGoal] = useState("Engagement");
  const [tone, setTone] = useState("Friendly");
  const [quantity, setQuantity] = useState(5);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cards, setCards] = useState<GeneratedCard[] | null>(null);

  const generate = async () => {
    if (!topic.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const res = await contentApi.generate({ topic: topic.trim(), goal, tone, quantity });
      setCards(res.cards);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        setError("로그인이 필요해요. 다시 로그인해 주세요.");
      } else if (e instanceof ApiError && e.status >= 500) {
        setError("생성 중 오류가 발생했어요. AI 키 설정 또는 잠시 후 다시 시도해 주세요.");
      } else {
        setError("생성에 실패했어요. 입력을 확인하고 다시 시도해 주세요.");
      }
    } finally {
      setLoading(false);
    }
  };

  const updateCard = (i: number, patch: Partial<GeneratedCard>) =>
    setCards((cs) => (cs ? cs.map((c, idx) => (idx === i ? { ...c, ...patch } : c)) : cs));
  const removeCard = (i: number) =>
    setCards((cs) => (cs ? cs.filter((_, idx) => idx !== i) : cs));

  return (
    <div className="mx-auto w-full max-w-7xl px-6 py-7">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">AI 생성</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          주제만 입력하면 AI가 Threads 게시물을 만들어 줍니다.
        </p>
      </div>

      <Card className="p-6">
        <div className="grid gap-5">
          <div className="grid gap-2">
            <Label htmlFor="topic">주제</Label>
            <Input
              id="topic"
              placeholder="예: AI 스타트업, 주말 생산성, 제주 여행…"
              value={topic}
              onChange={(e) => setTopic(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && generate()}
            />
            <div className="flex flex-wrap gap-1.5 pt-0.5">
              {TOPIC_CHIPS.map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => setTopic(c)}
                  className="rounded-full border px-2.5 py-1 text-xs text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
                >
                  {c}
                </button>
              ))}
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <div className="grid gap-2">
              <Label>목표</Label>
              <Select value={goal} onValueChange={setGoal}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {GOALS.map((g) => (
                    <SelectItem key={g} value={g}>{g}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label>톤</Label>
              <Select value={tone} onValueChange={setTone}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TONES.map((t) => (
                    <SelectItem key={t} value={t}>{t}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label>개수</Label>
              <div className="flex h-9 items-center rounded-md border bg-background p-0.5">
                {QUANTITIES.map((q) => (
                  <button
                    key={q}
                    type="button"
                    onClick={() => setQuantity(q)}
                    className={`h-8 flex-1 rounded-[5px] text-sm font-medium transition-colors ${
                      quantity === q
                        ? "bg-primary text-primary-foreground"
                        : "text-muted-foreground hover:text-foreground"
                    }`}
                  >
                    {q}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Button onClick={generate} disabled={loading || !topic.trim()} className="gap-2">
              {loading ? <Loader2 className="size-4 animate-spin" /> : <Wand2 className="size-4" />}
              {loading ? "생성 중…" : `${quantity}개 생성`}
            </Button>
            {error && <span className="text-sm text-destructive">{error}</span>}
          </div>
        </div>
      </Card>

      <div className="mt-6">
        {loading && (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: Math.min(quantity, 6) }).map((_, i) => (
              <Card key={i} className="h-44 animate-pulse p-5">
                <div className="h-3 w-2/3 rounded bg-muted" />
                <div className="mt-3 h-3 w-full rounded bg-muted" />
                <div className="mt-2 h-3 w-5/6 rounded bg-muted" />
              </Card>
            ))}
          </div>
        )}

        {!loading && cards && cards.length > 0 && (
          <>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="font-semibold">생성 결과 {cards.length}개</h2>
            </div>
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {cards.map((card, i) => (
                <GeneratedCardView
                  key={i}
                  card={card}
                  onChange={(patch) => updateCard(i, patch)}
                  onDelete={() => removeCard(i)}
                />
              ))}
            </div>
          </>
        )}

        {!loading && cards && cards.length === 0 && (
          <EmptyHint text="생성된 카드가 없어요. 다시 시도해 보세요." />
        )}

        {!loading && !cards && (
          <EmptyHint text="주제를 입력하고 생성하면 카드가 여기에 나타납니다." />
        )}
      </div>
    </div>
  );
}

function EmptyHint({ text }: { text: string }) {
  return (
    <Card className="flex flex-col items-center justify-center gap-3 px-6 py-16 text-center">
      <div className="flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
        <Sparkles className="size-6" />
      </div>
      <p className="text-sm text-muted-foreground">{text}</p>
    </Card>
  );
}

function GeneratedCardView({
  card,
  onChange,
  onDelete,
}: {
  card: GeneratedCard;
  onChange: (patch: Partial<GeneratedCard>) => void;
  onDelete: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    const text = [card.content, card.hashtags.map((h) => `#${h}`).join(" "), card.cta]
      .filter(Boolean)
      .join("\n\n");
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const over = card.content.length > MAX;

  return (
    <Card className="flex flex-col p-5">
      {editing ? (
        <Textarea
          value={card.content}
          onChange={(e) => onChange({ content: e.target.value })}
          className="min-h-32 flex-1"
        />
      ) : (
        <p className="flex-1 whitespace-pre-wrap text-sm leading-relaxed">{card.content}</p>
      )}

      {card.hashtags.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1">
          {card.hashtags.map((h, idx) => (
            <Badge key={idx} variant="secondary">#{h}</Badge>
          ))}
        </div>
      )}

      {card.cta && <p className="mt-3 text-sm font-medium text-primary">{card.cta}</p>}

      <div className="mt-4 flex items-center justify-between border-t pt-3">
        <span className={`text-xs tabular-nums ${over ? "text-destructive" : "text-muted-foreground"}`}>
          {card.content.length}/{MAX}
        </span>
        <div className="flex items-center gap-1">
          <Button variant="ghost" size="icon" title="복사" onClick={copy}>
            {copied ? <Check className="size-4 text-emerald-600 dark:text-emerald-400" /> : <Copy className="size-4" />}
          </Button>
          <Button
            variant="ghost"
            size="icon"
            title={editing ? "완료" : "편집"}
            onClick={() => setEditing((v) => !v)}
          >
            {editing ? <Check className="size-4" /> : <Pencil className="size-4" />}
          </Button>
          <Button variant="ghost" size="icon" title="삭제" onClick={onDelete}>
            <Trash2 className="size-4" />
          </Button>
        </div>
      </div>
    </Card>
  );
}
