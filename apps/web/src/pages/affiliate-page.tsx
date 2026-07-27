// 콘텐츠 → 제휴(쿠팡파트너스): 리뷰형 소프트셀 + 대가성 고지문·subId·링크를 서버가 자동 부착.
import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { BookmarkPlus, Check, Copy, Info, Link2, Loader2 } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { contentApi, type AffiliateResponse, type GeneratedCard } from "@/lib/content-api";
import { postsApi } from "@/lib/posts-api";
import { GENERATE_PLATFORMS as PLATFORMS } from "@/lib/platforms";
import { ScoreBadge } from "@/components/score-badge";
import { useToast } from "@/components/toast";
import { ApiError } from "@/lib/api";

const TONES = [
  { value: "Friendly", label: "친근함" },
  { value: "Expert", label: "전문가" },
  { value: "Storytelling", label: "스토리텔링" },
  { value: "Casual", label: "캐주얼" },
  { value: "Educational", label: "교육적" },
];
const QUANTITIES = [5, 10, 30];

export function AffiliatePage() {
  const { show } = useToast();
  const [productName, setProductName] = useState("");
  const [affiliateLink, setAffiliateLink] = useState("");
  const [subIdPrefix, setSubIdPrefix] = useState("");
  const [productFeatures, setProductFeatures] = useState("");
  const [platform, setPlatform] = useState("THREADS");
  const [tone, setTone] = useState("Friendly");
  const [quantity, setQuantity] = useState(5);

  const [res, setRes] = useState<AffiliateResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const generate = async () => {
    if (!productName.trim() || !affiliateLink.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const r = await contentApi.generateAffiliate({
        productName: productName.trim(),
        productFeatures: productFeatures.trim() || undefined,
        affiliateLink: affiliateLink.trim(),
        subIdPrefix: subIdPrefix.trim() || undefined,
        tone,
        quantity,
        platform,
      });
      setRes(r);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) setError("로그인이 필요해요.");
      else if (e instanceof ApiError && e.status === 402) setError("플랜 생성 한도에 도달했어요. Pro 플랜에서 더 생성할 수 있어요.");
      else setError(e instanceof ApiError ? e.message : "생성에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setLoading(false);
    }
  };

  const platformLabel = PLATFORMS.find((p) => p.value === platform)?.label ?? platform;

  return (
    <div className="mx-auto w-full max-w-3xl px-6 py-7">
      <div className="mb-6 flex items-center gap-3">
        <div className="flex size-10 items-center justify-center rounded-lg bg-gradient-to-tr from-amber-400 to-rose-500 text-white">
          <Link2 className="size-5" />
        </div>
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">제휴 콘텐츠 (쿠팡파트너스)</h1>
          <p className="mt-0.5 text-sm text-muted-foreground">제품 링크만 넣으면 정직한 리뷰형 게시물 + 대가성 고지문·subId를 자동으로 붙여드려요.</p>
        </div>
      </div>

      {/* 컴플라이언스 고지 */}
      <div className="mb-5 flex items-start gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2.5 text-xs text-muted-foreground">
        <Info className="mt-0.5 size-3.5 shrink-0 text-amber-500" />
        <span>
          모든 게시물에 <span className="font-medium text-foreground/80">"이 포스팅은 쿠팡 파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다."</span> 고지문이 자동 삽입돼요(법적 필수, 제거 불가).
          없는 스펙·수치·후기는 만들지 않도록 설계돼 있어요. 실제 특징을 넣을수록 정확해집니다.
        </span>
      </div>

      <Card className="space-y-4 p-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label>제품명 *</Label>
            <Input placeholder="예: 무선 핸디 청소기 XYZ" value={productName} onChange={(e) => setProductName(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label>내 쿠팡파트너스 링크 *</Label>
            <Input placeholder="https://link.coupang.com/a/..." value={affiliateLink} autoCapitalize="none" onChange={(e) => setAffiliateLink(e.target.value)} />
          </div>
        </div>

        <div className="space-y-1.5">
          <Label>실제 특징·장점 <span className="text-muted-foreground">(선택 · 넣을수록 정확·안전)</span></Label>
          <Textarea
            rows={3}
            placeholder="예: 흡입력 강함, 무게 1.2kg으로 가벼움, 배터리 30분, 필터 물세척 가능 — 확인된 사실만"
            value={productFeatures}
            onChange={(e) => setProductFeatures(e.target.value)}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label>플랫폼</Label>
            <Select value={platform} onValueChange={setPlatform}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {PLATFORMS.map((p) => (<SelectItem key={p.value} value={p.value}>{p.label}</SelectItem>))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label>subId 접두어 <span className="text-muted-foreground">(선택)</span></Label>
            <Input placeholder="예: haru → subId=haru_threads" value={subIdPrefix} autoCapitalize="none" onChange={(e) => setSubIdPrefix(e.target.value)} />
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label>톤</Label>
            <Select value={tone} onValueChange={setTone}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {TONES.map((t) => (<SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label>개수</Label>
            <Select value={String(quantity)} onValueChange={(v) => setQuantity(Number(v))}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {QUANTITIES.map((q) => (<SelectItem key={q} value={String(q)}>{q}개</SelectItem>))}
              </SelectContent>
            </Select>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <p className="flex-1 text-xs text-muted-foreground">
            subId는 플랫폼별로 자동 부착돼 쿠팡 리포트에서 채널별 실적이 갈려요. ({platformLabel} → <code>subId={(subIdPrefix.trim() ? subIdPrefix.trim() + "_" : "")}{platform.toLowerCase()}</code>)
          </p>
          <Button onClick={generate} disabled={loading || !productName.trim() || !affiliateLink.trim()} className="gap-2">
            {loading && <Loader2 className="size-4 animate-spin" />}
            생성
          </Button>
        </div>
      </Card>

      {error && <p className="mt-4 text-sm text-destructive">{error}</p>}

      {res && (
        <div className="mt-6 space-y-4">
          <LinkBanner res={res} platformLabel={platformLabel} />
          {res.cards.map((c, i) => (
            <AffiliateCardView key={i} card={c} onSaved={() => show("임시저장했어요. 라이브러리에서 예약·발행할 수 있어요.", "success")} />
          ))}
        </div>
      )}
    </div>
  );
}

/** subId가 붙은 최종 링크 안내 — 특히 링크를 본문에 못 넣는 플랫폼(IG)에선 프로필 링크로 안내. */
function LinkBanner({ res, platformLabel }: { res: AffiliateResponse; platformLabel: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(res.linkWithSubId);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };
  return (
    <div className="rounded-lg border bg-muted/40 p-3">
      <div className="mb-1 flex items-center gap-2 text-sm font-medium">
        <Link2 className="size-4" /> {platformLabel}용 링크 (subId <code className="text-xs">{res.subId}</code> 포함)
      </div>
      <div className="flex items-center gap-2">
        <code className="min-w-0 flex-1 truncate rounded bg-background px-2 py-1.5 text-xs">{res.linkWithSubId}</code>
        <Button variant="outline" size="sm" onClick={copy} className="gap-1.5 shrink-0">
          {copied ? <Check className="size-3.5" /> : <Copy className="size-3.5" />} 복사
        </Button>
      </div>
      <p className="mt-2 text-xs text-muted-foreground">
        {res.linkInBody
          ? "이 링크가 각 게시물 본문에 이미 포함돼 있어요."
          : "이 플랫폼은 본문 링크가 클릭되지 않아, 이 링크를 프로필(bio) 링크로 넣어주세요. 게시물 CTA가 '프로필 링크 확인'으로 유도합니다."}
      </p>
    </div>
  );
}

function AffiliateCardView({ card, onSaved }: { card: GeneratedCard; onSaved: () => void }) {
  const qc = useQueryClient();
  const { show } = useToast();
  const [copied, setCopied] = useState(false);
  const [saving, setSaving] = useState(false);

  const fullText = card.hashtags.length
    ? `${card.content}\n\n${card.hashtags.map((h) => `#${h}`).join(" ")}`
    : card.content;

  const copy = async () => {
    await navigator.clipboard.writeText(fullText);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const save = async () => {
    setSaving(true);
    try {
      await postsApi.create({ content: card.content, hashtags: card.hashtags, cta: card.cta });
      qc.invalidateQueries({ queryKey: ["posts"] });
      onSaved();
    } catch {
      show("저장에 실패했어요.", "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="space-y-3 p-4">
      <div className="flex items-start justify-between gap-2">
        <ScoreBadge score={card.score} />
        <div className="flex gap-1.5">
          <Button variant="outline" size="sm" onClick={copy} className="gap-1.5">
            {copied ? <Check className="size-3.5" /> : <Copy className="size-3.5" />} 복사
          </Button>
          <Button variant="outline" size="sm" onClick={save} disabled={saving} className="gap-1.5">
            {saving ? <Loader2 className="size-3.5 animate-spin" /> : <BookmarkPlus className="size-3.5" />} 임시저장
          </Button>
        </div>
      </div>
      <p className="whitespace-pre-wrap text-sm leading-relaxed">{card.content}</p>
      {card.cta && <p className="text-sm font-medium text-foreground/80">{card.cta}</p>}
      {card.hashtags.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {card.hashtags.map((h) => (<Badge key={h} variant="secondary">#{h}</Badge>))}
        </div>
      )}
    </Card>
  );
}
