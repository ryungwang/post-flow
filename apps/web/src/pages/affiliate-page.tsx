// 콘텐츠 → 제휴(쿠팡파트너스): 리뷰형 소프트셀 + 대가성 고지문·subId·링크를 서버가 자동 부착.
// 네이버 쇼핑 검색으로 실제 상품 정보를 근거로 채운다.
import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { BookmarkPlus, Check, Copy, Film, Info, Link2, Loader2 } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { contentApi, type AffiliateResponse, type GeneratedCard } from "@/lib/content-api";
import { CoupangSourcePanel } from "@/components/coupang-source-panel";
import { affiliateVideoApi } from "@/lib/affiliate-video-api";
import { postsApi } from "@/lib/posts-api";
import { GENERATE_PLATFORMS as PLATFORMS } from "@/lib/platforms";
import { ScoreBadge } from "@/components/score-badge";
import { useToast } from "@/components/toast";
import { ApiError } from "@/lib/api";
import { coupangApi, type CoupangProduct } from "@/lib/coupang-api";

// 제휴 톤은 '자극적 스토리텔링'으로 고정(선택 UI 숨김). 본문은 제품 설명 없이 궁금증·몰입만.
const AFFILIATE_TONE = "Storytelling";
const QUANTITIES = [5, 10, 30];
// SNS + 블로그(쿠팡 HTML 배너 삽입). 블로그는 긴 리뷰 글 + 상품·배너·프로모션 HTML.
const AFF_PLATFORMS = [...PLATFORMS, { value: "BLOG", label: "블로그 (HTML)", hint: "긴 리뷰 글 + 쿠팡 HTML 배너 삽입" }];

export function AffiliatePage() {
  const { show } = useToast();
  const [productName, setProductName] = useState("");
  const [affiliateLink, setAffiliateLink] = useState("");
  const [coupangUrl, setCoupangUrl] = useState("");
  const [deeplinking, setDeeplinking] = useState(false);
  const genDeeplink = async () => {
    const u = coupangUrl.trim();
    if (!u) { show("쿠팡 상품 URL을 붙여넣어 주세요.", "error"); return; }
    setDeeplinking(true);
    try {
      const r = await coupangApi.deeplink(u);
      setAffiliateLink(r.shortUrl);
      show("제휴 딥링크를 자동 생성했어요.", "success");
    } catch (e) {
      show(e instanceof ApiError ? e.message : "딥링크 생성에 실패했어요.", "error");
    } finally {
      setDeeplinking(false);
    }
  };
  const [subIdPrefix, setSubIdPrefix] = useState("");
  const [productFeatures, setProductFeatures] = useState("");
  const [platform, setPlatform] = useState("THREADS");
  const [blogHtml, setBlogHtml] = useState("");
  const tone = AFFILIATE_TONE; // 고정: 자극적 스토리텔링 · 반말(선택 UI 숨김)
  const [quantity, setQuantity] = useState(5);
  const [disclosureAsComment, setDisclosureAsComment] = useState(false);
  const isBlog = platform === "BLOG";
  // 첫 댓글(=고지문 댓글) 지원 플랫폼. 나머지는 본문 고지로 폴백.
  const commentCapable = ["THREADS", "FACEBOOK", "INSTAGRAM", "MASTODON", "BLUESKY"].includes(platform);

  const [res, setRes] = useState<AffiliateResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 광고영상/이미지 참고용 제품 이미지 URL(Extension JSON or 쿠팡 소싱에서 채움)
  const [productImageUrl, setProductImageUrl] = useState<string | null>(null);
  // 생성한 광고영상을 글에 첨부(발행 시 media로 나감). 공개 URL.
  const [attachedVideoUrl, setAttachedVideoUrl] = useState<string | null>(null);
  // 영상 훅 문구 — 글 카드의 "이 글로 영상"으로 채워짐(직접 수정도 가능).
  const [videoHook, setVideoHook] = useState("");
  // 쿠팡 Extension 추출 JSON(수동 소싱 폴백)
  const [captureJson, setCaptureJson] = useState("");

  // 쿠팡 소싱 패널에서 상품 선택 → 폼 자동 채움. 상품을 새로 고르면 그 상품 기준으로 항상 갱신(스테일 방지).
  const pickCoupang = (p: CoupangProduct) => {
    setProductName(p.productName);
    if (p.productUrl) setAffiliateLink(p.productUrl);
    if (p.productImage) setProductImageUrl(p.productImage);
    const hints = [p.categoryName, p.isRocket ? "로켓배송" : null, p.productPrice ? `${p.productPrice.toLocaleString()}원` : null]
      .filter(Boolean).join(" · ");
    setProductFeatures(hints); // 새 상품 = 새 컨텍스트 → 항상 그 상품 값으로 덮어씀
    show("쿠팡 상품을 불러왔어요 — 제품명·제휴 링크가 채워졌어요.", "success");
  };

  const applyCaptureJson = () => {
    try {
      const p = JSON.parse(captureJson) as Record<string, unknown>;
      if (typeof p.productName === "string") setProductName(p.productName);
      const feats = Array.isArray(p.features) ? (p.features as unknown[]).filter((s) => typeof s === "string") as string[] : [];
      if (feats.length) setProductFeatures(feats.join(" · "));
      else if (typeof p.description === "string") setProductFeatures(p.description.slice(0, 500));
      if (typeof p.affiliateUrl === "string" && p.affiliateUrl.trim()) setAffiliateLink(p.affiliateUrl.trim());
      const imgs = Array.isArray(p.sourceImages) ? (p.sourceImages as unknown[]).filter((s) => typeof s === "string") as string[] : [];
      const img = imgs.find((s) => s.startsWith("https://")) ?? imgs[0] ?? null;
      if (img) setProductImageUrl(img);
      setCaptureJson("");
      show("쿠팡 Extension JSON 적용 완료 — 제품명·특징·파트너스 링크를 채웠어요.", "success");
    } catch {
      show("추출 JSON 형식이 올바르지 않아요.", "error");
    }
  };

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
        blogHtml: isBlog ? blogHtml.trim() || undefined : undefined,
        disclosureAsComment: !isBlog && disclosureAsComment,
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

  const platformLabel = AFF_PLATFORMS.find((p) => p.value === platform)?.label ?? platform;

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
        {/* 쿠팡 Open API 소싱 — 골드박스·베스트·검색으로 실제 팔리는 상품을 바로(제휴 링크 자동). */}
        <CoupangSourcePanel onPick={pickCoupang} />

        {/* 쿠팡 Extension 추출 JSON — 수동 소싱 폴백(상품 상세페이지에서 추출) */}
        <details className="rounded-lg border border-dashed p-3">
          <summary className="cursor-pointer text-sm font-medium">쿠팡 Extension 추출 JSON 가져오기 <span className="font-normal text-muted-foreground">(실제 쿠팡 상품 · 선택)</span></summary>
          <div className="mt-2 space-y-2">
            <Textarea
              rows={3}
              className="font-mono text-xs"
              placeholder={'쿠팡 상품 페이지에서 Extension으로 추출한 JSON을 붙여넣으세요. 예) {"productName":"...","affiliateUrl":"https://link.coupang.com/a/...","features":["..."],"sourceImages":["https://..."]}'}
              value={captureJson}
              onChange={(e) => setCaptureJson(e.target.value)}
            />
            <div className="flex items-center gap-2">
              <p className="flex-1 text-[11px] text-muted-foreground">붙여넣으면 제품명·특징·파트너스 링크·제품 이미지가 자동으로 채워져요. (AI 광고영상 이미지도 이걸로)</p>
              <Button variant="outline" size="sm" onClick={applyCaptureJson} disabled={!captureJson.trim()} className="shrink-0">적용</Button>
            </div>
          </div>
        </details>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label>제품명 *</Label>
            <Input placeholder="예: 무선 핸디 청소기 XYZ" value={productName} onChange={(e) => setProductName(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label>내 쿠팡파트너스 링크 *</Label>
            <Input placeholder="https://link.coupang.com/a/..." value={affiliateLink} autoCapitalize="none" onChange={(e) => setAffiliateLink(e.target.value)} />
            {/* 쿠팡 Open API 딥링크 자동생성 — 상품 URL만 붙여넣으면 위 제휴 링크가 채워짐(수동 링크 생성 불필요). */}
            <div className="flex gap-2">
              <Input
                placeholder="또는 쿠팡 상품 URL 붙여넣기 → 링크 자동생성"
                value={coupangUrl}
                autoCapitalize="none"
                onChange={(e) => setCoupangUrl(e.target.value)}
                className="text-xs"
              />
              <Button type="button" variant="outline" size="sm" onClick={genDeeplink} disabled={deeplinking || !coupangUrl.trim()} className="shrink-0 gap-1.5">
                {deeplinking ? <Loader2 className="size-3.5 animate-spin" /> : <Link2 className="size-3.5" />} 링크 생성
              </Button>
            </div>
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
                {AFF_PLATFORMS.map((p) => (<SelectItem key={p.value} value={p.value}>{p.label}</SelectItem>))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label>subId 접두어 <span className="text-muted-foreground">(선택)</span></Label>
            <Input placeholder="예: haru → subId=haru_threads" value={subIdPrefix} autoCapitalize="none" onChange={(e) => setSubIdPrefix(e.target.value)} />
          </div>
        </div>

        {isBlog && (
          <div className="space-y-1.5">
            <Label>쿠팡 HTML <span className="font-normal text-muted-foreground">(상품·배너·프로모션 · 선택)</span></Label>
            <Textarea
              rows={3}
              className="font-mono text-xs"
              placeholder={'쿠팡 파트너스에서 만든 HTML을 붙여넣으세요. 예) <a href="https://link.coupang.com/a/..." ...><img src="..."></a>'}
              value={blogHtml}
              onChange={(e) => setBlogHtml(e.target.value)}
            />
            <p className="text-[11px] text-muted-foreground">
              붙여넣은 배너(상품·카테고리·프로모션)가 <span className="font-medium">글 맨 위</span>에 삽입돼요. HTML을 넣으면 그 안의 링크(=subId는 쿠팡 '채널 아이디'로 지정)를 그대로 쓰고, 비우면 위 파트너스 링크에 subId를 붙여 하단에 넣어요.
            </p>
          </div>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
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

        {!isBlog && (
          <div className="space-y-1.5">
            <Label>대가성 고지 위치</Label>
            <div className="flex overflow-hidden rounded-md border text-sm">
              {([["body", "본문에 포함"], ["comment", "첫 댓글로"]] as const).map(([v, label]) => {
                const active = (v === "comment") === disclosureAsComment;
                return (
                  <button
                    key={v}
                    type="button"
                    onClick={() => setDisclosureAsComment(v === "comment")}
                    className={`flex-1 px-3 py-1.5 ${active ? "bg-foreground text-background" : "hover:bg-muted/50"}`}
                  >
                    {label}
                  </button>
                );
              })}
            </div>
            <p className="text-[11px] text-muted-foreground">
              {disclosureAsComment
                ? commentCapable
                  ? "본문엔 고지문을 넣지 않고, 발행 시 고지문이 자동으로 첫 댓글로 달려요(도달 저하 방지)."
                  : `${platformLabel}는 첫 댓글 자동이 안 돼서 이 플랫폼은 본문에 고지문이 들어갑니다.`
                : "고지문이 본문 하단에 포함됩니다."}
            </p>
          </div>
        )}

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

      <AffiliateVideoSection
        productName={productName}
        features={productFeatures}
        imageUrl={productImageUrl}
        attachedUrl={attachedVideoUrl}
        onAttach={setAttachedVideoUrl}
        hook={videoHook}
        onHookChange={setVideoHook}
      />

      {error && <p className="mt-4 text-sm text-destructive">{error}</p>}

      {res && (
        <div className="mt-6 space-y-4">
          {res.linkWithSubId && <LinkBanner res={res} platformLabel={platformLabel} />}
          {res.cards.map((c, i) => (
            <AffiliateCardView
              key={i}
              card={c}
              saveable={!isBlog}
              firstComment={res.firstComment ?? undefined}
              mediaUrl={isBlog ? null : attachedVideoUrl}
              onMakeVideo={(h) => {
                setVideoHook(h);
                window.scrollTo({ top: 0, behavior: "smooth" });
                show("이 글의 훅을 영상에 담았어요. 위 'AI 광고영상'에서 생성하세요.", "success");
              }}
              onSaved={() => show("임시저장했어요. 라이브러리에서 예약·발행하면 고지문 첫 댓글" + (attachedVideoUrl ? "·영상" : "") + "이 함께 나가요.", "success")}
            />
          ))}
        </div>
      )}
    </div>
  );
}

/** 독립 "AI 광고영상" 섹션 — 네이버에서 고른 제품 이미지로 6초 세로 광고영상(Kling 1컷). 비동기 폴링. */
function AffiliateVideoSection({ productName, features, imageUrl, attachedUrl, onAttach, hook, onHookChange }: {
  productName: string; features: string; imageUrl: string | null;
  attachedUrl: string | null; onAttach: (url: string | null) => void;
  hook: string; onHookChange: (v: string) => void;
}) {
  const { show } = useToast();
  const [jobId, setJobId] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [videoUrl, setVideoUrl] = useState<string | null>(null);
  const [vError, setVError] = useState<string | null>(null);

  const start = async () => {
    if (!productName.trim()) { show("제품명을 입력해 주세요.", "error"); return; }
    setVError(null); setVideoUrl(null); setStatus("SUBMITTED"); setJobId(null);
    try {
      const r = await affiliateVideoApi.submit({
        productName: productName.trim(),
        features: features.trim() || undefined,
        hook: hook.trim() || undefined,
        imageUrl: imageUrl || undefined, // 창작 씬(text2video)이라 제품 이미지는 안 씀 — 있으면 무시됨
      });
      setJobId(r.jobId);
      setStatus(r.status);
    } catch (e) {
      setStatus(null);
      setVError(e instanceof ApiError ? e.message : "영상 생성 시작에 실패했어요.");
    }
  };

  useEffect(() => {
    if (!jobId || status === "READY" || status === "FAILED") return;
    const t = setInterval(async () => {
      try {
        const s = await affiliateVideoApi.status(jobId);
        setStatus(s.status);
        if (s.status === "READY" && s.videoUrl) {
          clearInterval(t);
          setVideoUrl(s.videoUrl);
          onAttach(s.videoUrl); // 완성되면 자동으로 글에 첨부(발행 시 media로 나감)
        } else if (s.status === "FAILED") {
          clearInterval(t);
          setVError(s.error ?? "영상 생성에 실패했어요.");
        }
      } catch {
        /* 폴링 중 일시 오류는 무시하고 계속 */
      }
    }, 5000);
    return () => clearInterval(t);
  }, [jobId, status]);

  const busy = !!status && status !== "READY" && status !== "FAILED";

  return (
    <Card className="mt-6 space-y-3 p-5">
      <div className="flex items-center gap-2">
        <div className="flex size-8 items-center justify-center rounded-lg bg-gradient-to-tr from-indigo-500 to-fuchsia-500 text-white">
          <Film className="size-4" />
        </div>
        <h2 className="font-semibold">AI 광고영상 <span className="text-xs font-normal text-muted-foreground">(6초 · Kling · 세로 SNS)</span></h2>
      </div>
      <p className="text-xs text-muted-foreground">
        제품 테마로 <span className="font-medium">6초짜리 세로 창작 광고영상</span>을 만들어요. 제품을 그대로 보여주는 게 아니라, 테마에 맞는 <span className="font-medium">재밌는 창작 씬</span>이 나옵니다(제품 이미지 불필요).
      </p>
      <div className="flex gap-2">
        <Input placeholder="훅 문구(선택 · 비우면 자동 · 글 카드의 '이 글로 영상'으로 채워짐)" value={hook} onChange={(e) => onHookChange(e.target.value)} />
        <Button onClick={start} disabled={busy || !productName.trim()} className="shrink-0 gap-1.5">
          {busy ? <Loader2 className="size-4 animate-spin" /> : <Film className="size-4" />} 영상 생성
        </Button>
      </div>
      {busy && <p className="text-xs text-muted-foreground">생성 중… Kling 영상은 보통 <span className="font-medium">30초~2분</span> 걸려요. 창을 열어두세요.</p>}
      {vError && <p className="text-sm text-destructive">{vError}</p>}
      {videoUrl && (
        <div className="space-y-2">
          <video src={videoUrl} controls playsInline className="mx-auto max-h-[70vh] rounded-lg border" />
          <div className="flex flex-wrap items-center gap-3">
            {attachedUrl === videoUrl ? (
              <>
                <span className="text-xs font-medium text-emerald-600">✓ 글에 첨부됨 — 발행 시 함께 나가요</span>
                <Button variant="ghost" size="sm" onClick={() => onAttach(null)}>첨부 해제</Button>
              </>
            ) : (
              <Button variant="outline" size="sm" onClick={() => onAttach(videoUrl)}>글에 첨부</Button>
            )}
            <a href={videoUrl} download="affiliate-ad.mp4" className="text-xs text-brand underline">다운로드</a>
          </div>
          <p className="text-[11px] text-muted-foreground">첨부하면 아래 글 카드를 임시저장·발행할 때 이 영상이 media로 함께 발행돼요(Threads·IG 영상).</p>
        </div>
      )}
    </Card>
  );
}

/** 레이더 후보 한 줄 — 점수 + 검색·쇼핑 상승률·계절(확인 불가는 그대로 표기). 누르면 그 키워드로 상품 검색. */
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
        {res.firstComment
          ? "링크와 고지문이 발행 시 함께 첫 댓글로 나가요(본문은 글만 깨끗하게)."
          : res.linkInBody
            ? "이 링크가 각 게시물 본문에 이미 포함돼 있어요."
            : "이 플랫폼은 본문 링크가 클릭되지 않아, 이 링크를 프로필(bio) 링크로 넣어주세요. 게시물 CTA가 '프로필 링크 확인'으로 유도합니다."}
      </p>
    </div>
  );
}

function AffiliateCardView({ card, saveable = true, firstComment, mediaUrl, onMakeVideo, onSaved }: { card: GeneratedCard; saveable?: boolean; firstComment?: string; mediaUrl?: string | null; onMakeVideo?: (hook: string) => void; onSaved: () => void }) {
  const hookLine = () => {
    const first = card.content.split("\n").map((s) => s.trim()).find(Boolean) ?? card.content;
    return first.length > 40 ? first.slice(0, 40) : first;
  };
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
      await postsApi.create({ content: card.content, hashtags: card.hashtags, cta: card.cta, firstComment, mediaUrl });
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
            {copied ? <Check className="size-3.5" /> : <Copy className="size-3.5" />} {saveable ? "복사" : "HTML 복사"}
          </Button>
          {saveable && (
            <Button variant="outline" size="sm" onClick={save} disabled={saving} className="gap-1.5">
              {saving ? <Loader2 className="size-3.5 animate-spin" /> : <BookmarkPlus className="size-3.5" />} 임시저장
            </Button>
          )}
          {onMakeVideo && (
            <Button variant="outline" size="sm" onClick={() => onMakeVideo(hookLine())} className="gap-1.5">
              <Film className="size-3.5" /> 이 글로 영상
            </Button>
          )}
        </div>
      </div>
      {!saveable && (
        <p className="rounded-md border border-dashed px-2.5 py-1.5 text-[11px] text-muted-foreground">
          블로그 HTML은 SNS 발행용이 아니라 라이브러리에 저장하지 않아요. 위 <b>HTML 복사</b>로 복사해 블로그 편집기(HTML 모드)에 붙여넣으세요.
        </p>
      )}
      <p className="whitespace-pre-wrap text-sm leading-relaxed">{card.content}</p>
      {card.cta && <p className="text-sm font-medium text-foreground/80">{card.cta}</p>}
      {card.hashtags.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {card.hashtags.map((h) => (<Badge key={h} variant="secondary">#{h}</Badge>))}
        </div>
      )}
      {firstComment && (
        <div className="rounded-md border border-dashed px-2.5 py-1.5 text-[11px] text-muted-foreground">
          <span className="font-medium">첫 댓글(발행 시 자동):</span>
          <span className="mt-0.5 block whitespace-pre-wrap">{firstComment}</span>
        </div>
      )}
      {mediaUrl && (
        <p className="rounded-md border border-dashed px-2.5 py-1.5 text-[11px] text-emerald-600">
          광고영상 첨부됨 — 발행 시 함께 나가요
        </p>
      )}
    </Card>
  );
}
