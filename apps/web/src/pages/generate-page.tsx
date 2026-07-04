import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, BookmarkPlus, Check, Copy, Eye, Loader2, Megaphone, Pencil, Send, Sparkles, Trash2, Wand2 } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { postsApi } from "@/lib/posts-api";
import { brandApi } from "@/lib/brand-api";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { contentApi, type GeneratedCard, type HookVariant } from "@/lib/content-api";
import { ScoreBadge } from "@/components/score-badge";
import { ScoreAnalysisPanel } from "@/components/score-analysis-panel";
import { ThreadsPreview } from "@/components/threads-preview";
import { useAuth } from "@/store/auth";
import { ApiError } from "@/lib/api";

const TOPIC_CHIPS = ["AI", "스타트업", "개발", "생산성", "여행", "음식", "운동", "육아"];
import { GENERATE_GOALS as GOALS } from "@/lib/goals";
const TONES = [
  { value: "Expert", label: "전문가" },
  { value: "Friendly", label: "친근함" },
  { value: "Storytelling", label: "스토리텔링" },
  { value: "Controversial", label: "도발적" },
  { value: "Educational", label: "교육적" },
  { value: "Casual", label: "캐주얼" },
  { value: "Personal", label: "개인적" },
  { value: "Humor", label: "유머" },
];
const QUANTITIES = [5, 10, 30];
const MAX = 500;

export function GeneratePage() {
  const [params] = useSearchParams();
  const [topic, setTopic] = useState(params.get("topic") ?? "");
  const [goal, setGoal] = useState("Engagement");
  const [tone, setTone] = useState("Friendly");
  const { data: brands = [] } = useQuery({ queryKey: ["brands"], queryFn: brandApi.list });
  const [brandId, setBrandId] = useState("none");
  const currentGoal = GOALS.find((g) => g.value === goal) ?? GOALS[0];
  const noBrand = brandId === "none";
  const [quantity, setQuantity] = useState(5);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cards, setCards] = useState<GeneratedCard[] | null>(null);

  const [sort, setSort] = useState<"score" | "original">("score");

  const [hookOpen, setHookOpen] = useState(false);
  const [hooks, setHooks] = useState<HookVariant[] | null>(null);
  const [hooksLoading, setHooksLoading] = useState(false);
  const [copiedHook, setCopiedHook] = useState<string | null>(null);

  const compareHooks = async () => {
    setHookOpen(true);
    setHooksLoading(true);
    setHooks(null);
    try {
      setHooks(await contentApi.hooks(topic.trim(), 8));
    } finally {
      setHooksLoading(false);
    }
  };

  const generate = async () => {
    if (!topic.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const res = await contentApi.generate({ topic: topic.trim(), goal, tone, quantity, brandId: brandId === "none" ? null : Number(brandId) });
      setCards(res.cards);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        setError("로그인이 필요해요. 다시 로그인해 주세요.");
      } else if (e instanceof ApiError && e.status === 402) {
        setError(e.message); // 플랜 한도
      } else if (e instanceof ApiError && e.message && !e.message.startsWith("Request failed")) {
        setError(e.message); // 백엔드 친절 메시지(예: 크레딧 부족)
      } else {
        setError("생성에 실패했어요. 잠시 후 다시 시도해 주세요.");
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
    <div className="w-full px-6 py-7 lg:px-8 xl:px-10">
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

          {brands.length > 0 && (
            <div className="grid gap-2">
              <Label className="flex items-center gap-1.5"><Megaphone className="size-3.5 text-brand" /> 홍보 대상 (선택 시 이 제품을 자연스럽게 홍보)</Label>
              <Select value={brandId} onValueChange={setBrandId}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">없음 (일반 글)</SelectItem>
                  {brands.map((b) => (
                    <SelectItem key={b.id} value={String(b.id)}>{b.name}{b.isDefault ? " (기본)" : ""}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}

          <div className="grid items-start gap-4 sm:grid-cols-3">
            <div className="grid gap-2">
              <Label>목표</Label>
              <Select value={goal} onValueChange={setGoal}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {GOALS.map((g) => (
                    <SelectItem key={g.value} value={g.value}>{g.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">{currentGoal.desc}</p>
            </div>
            <div className="grid gap-2">
              <Label>톤</Label>
              <Select value={tone} onValueChange={setTone}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TONES.map((t) => (
                    <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>
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

          {currentGoal.need === "required" && noBrand && (
            <div className="flex items-start gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2.5 text-xs text-amber-700 dark:text-amber-400">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" />
              <div className="flex-1">
                <b>{currentGoal.label}</b>은(는) 홍보 대상이 없으면 의도와 다른 일반 글이 생성될 수 있어요.
                {brands.length > 0 ? " 위에서 홍보 대상을 선택하거나 " : " 등록된 제품이 없어요 — "}
                <Link to="/brands" className="font-semibold underline">{brands.length > 0 ? "새 제품 추가" : "브랜드/제품 추가"}</Link>.
              </div>
            </div>
          )}
          {currentGoal.need === "helpful" && noBrand && (
            <div className="flex items-start gap-2 rounded-lg border border-border/60 bg-muted/40 px-3 py-2.5 text-xs text-muted-foreground">
              <Megaphone className="mt-0.5 size-4 shrink-0 text-brand" />
              <div className="flex-1">
                <b className="text-foreground/80">{currentGoal.label}</b>은(는) 홍보 대상을 고르면 더 효과적이에요(필수는 아님).{" "}
                <Link to="/brands" className="font-semibold underline">브랜드/제품 관리</Link>.
              </div>
            </div>
          )}

          <div className="flex items-center gap-3">
            <Button onClick={generate} disabled={loading || !topic.trim()} className="gap-2">
              {loading ? <Loader2 className="size-4 animate-spin" /> : <Wand2 className="size-4" />}
              {loading ? "생성 중…" : `${quantity}개 생성`}
            </Button>
            <Button variant="outline" onClick={compareHooks} disabled={!topic.trim()} className="gap-2">
              <Sparkles className="size-4" /> 훅 비교
            </Button>
            {error && (
              <span className="flex items-center gap-2 text-sm text-destructive">
                {error}
              </span>
            )}
          </div>
        </div>
      </Card>

      <Dialog open={hookOpen} onOpenChange={setHookOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>훅 변형 비교</DialogTitle>
            <DialogDescription>"{topic}" 주제의 첫 문장(훅) 후보를 관심도 점수로 랭킹했어요. 마음에 드는 훅을 복사해 쓰세요.</DialogDescription>
          </DialogHeader>
          <div className="max-h-[55vh] space-y-2 overflow-y-auto">
            {hooksLoading ? (
              <div className="flex items-center justify-center gap-2 py-10 text-sm text-muted-foreground">
                <Loader2 className="size-4 animate-spin" /> 불러오는 중…
              </div>
            ) : (
              (hooks ?? []).map((h, i) => (
                <div key={i} className="flex items-center gap-3 rounded-lg border border-border/60 px-3 py-2.5 hover:bg-accent/40">
                  <ScoreBadge score={h.score} />
                  <span className="min-w-0 flex-1 text-sm font-medium">{h.hook}</span>
                  <button
                    className="flex shrink-0 items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
                    onClick={async () => { await navigator.clipboard.writeText(h.hook); setCopiedHook(h.hook); setTimeout(() => setCopiedHook(null), 1500); }}
                  >
                    {copiedHook === h.hook ? <Check className="size-3.5 text-emerald-600" /> : <Copy className="size-3.5" />} 복사
                  </button>
                </div>
              ))
            )}
          </div>
        </DialogContent>
      </Dialog>

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
              <div className="flex items-center rounded-lg border bg-background p-0.5 text-xs">
                {([["score", "관심도순"], ["original", "생성순"]] as const).map(([k, label]) => (
                  <button
                    key={k}
                    onClick={() => setSort(k)}
                    className={`rounded-md px-2.5 py-1 transition-colors ${sort === k ? "bg-accent font-medium text-foreground" : "text-muted-foreground hover:text-foreground"}`}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {cards
                .map((card, i) => ({ card, i }))
                .sort((a, b) => (sort === "score" ? b.card.score - a.card.score : a.i - b.i))
                .map(({ card, i }) => (
                  <div key={i} className="animate-fade-up">
                    <GeneratedCardView
                      card={card}
                      onChange={(patch) => updateCard(i, patch)}
                      onDelete={() => removeCard(i)}
                    />
                  </div>
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

type SaveState = "idle" | "saving" | "saved" | "publishing" | "published" | "error";

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
  const [preview, setPreview] = useState(false);
  const [copied, setCopied] = useState(false);
  const [save, setSave] = useState<SaveState>("idle");
  const qc = useQueryClient();
  const user = useAuth((s) => s.user);

  const fullText = () =>
    [card.content, card.hashtags.map((h) => `#${h}`).join(" "), card.cta]
      .filter(Boolean)
      .join("\n\n");

  const copy = async () => {
    await navigator.clipboard.writeText(fullText());
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const saveDraft = async () => {
    setSave("saving");
    try {
      await postsApi.create({ content: card.content, hashtags: card.hashtags, cta: card.cta });
      qc.invalidateQueries({ queryKey: ["posts"] });
      setSave("saved");
    } catch {
      setSave("error");
    }
  };

  const publish = async () => {
    setSave("publishing");
    try {
      const post = await postsApi.create({ content: card.content, hashtags: card.hashtags, cta: card.cta });
      await postsApi.publishNow(post.id);
      qc.invalidateQueries({ queryKey: ["posts"] });
      setSave("published");
    } catch {
      setSave("error");
    }
  };

  const over = card.content.length > MAX;
  const busy = save === "saving" || save === "publishing";

  return (
    <Card className="lift flex h-full flex-col p-5">
      <div className="mb-3 flex items-center justify-between">
        <button
          className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
          onClick={() => setPreview((v) => !v)}
        >
          <Eye className="size-3.5" /> {preview ? "내용" : "미리보기"}
        </button>
        <ScoreBadge score={card.score} />
      </div>

      {preview && !editing ? (
        <div className="flex-1">
          <ThreadsPreview username={user?.name ?? "나"} avatarUrl={user?.profileImage} content={card.content} hashtags={card.hashtags} cta={card.cta} mediaUrl={null} />
        </div>
      ) : (
        <>
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

          {!editing && (
            <div className="mt-3">
              <ScoreAnalysisPanel content={card.content} hashtags={card.hashtags} cta={card.cta} score={card.score} />
            </div>
          )}
        </>
      )}

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
      <div className="mt-2 flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          className="flex-1 gap-1.5"
          disabled={busy || save === "saved"}
          onClick={saveDraft}
        >
          {save === "saving" ? <Loader2 className="size-4 animate-spin" /> : <BookmarkPlus className="size-4" />}
          {save === "saved" ? "저장됨" : "저장"}
        </Button>
        <Button
          size="sm"
          className="flex-1 gap-1.5"
          disabled={busy || save === "published"}
          onClick={publish}
        >
          {save === "publishing" ? <Loader2 className="size-4 animate-spin" /> : <Send className="size-4" />}
          {save === "published" ? "발행됨" : "발행"}
        </Button>
      </div>
      {save === "error" && (
        <p className="mt-2 text-xs text-destructive">처리에 실패했어요. (Threads 연결/키 확인)</p>
      )}
    </Card>
  );
}
