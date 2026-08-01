// 콘텐츠 → 제휴(쿠팡파트너스): 리뷰형 소프트셀 + 대가성 고지문·subId·링크를 서버가 자동 부착.
// 네이버 쇼핑 검색으로 실제 상품 정보를 근거로 채운다.
import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { BookmarkPlus, Check, Copy, Film, Info, Link2, Loader2, Search, TrendingUp } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { contentApi, type AffiliateResponse, type GeneratedCard } from "@/lib/content-api";
import { naverApi, type NaverProduct, type RadarProduct, type RadarResponse } from "@/lib/naver-api";
import { affiliateVideoApi } from "@/lib/affiliate-video-api";
import { postsApi } from "@/lib/posts-api";
import { GENERATE_PLATFORMS as PLATFORMS } from "@/lib/platforms";
import { ScoreBadge } from "@/components/score-badge";
import { useToast } from "@/components/toast";
import { ApiError } from "@/lib/api";

// 제휴 톤은 '자극적 스토리텔링'으로 고정(선택 UI 숨김). 본문은 제품 설명 없이 궁금증·몰입만.
const AFFILIATE_TONE = "Storytelling";
const QUANTITIES = [5, 10, 30];
// SNS + 블로그(쿠팡 HTML 배너 삽입). 블로그는 긴 리뷰 글 + 상품·배너·프로모션 HTML.
const AFF_PLATFORMS = [...PLATFORMS, { value: "BLOG", label: "블로그 (HTML)", hint: "긴 리뷰 글 + 쿠팡 HTML 배너 삽입" }];

export function AffiliatePage() {
  const { show } = useToast();
  const [productName, setProductName] = useState("");
  const [affiliateLink, setAffiliateLink] = useState("");
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

  // 네이버 상품 검색(제품 정보 근거 채우기)
  const [nq, setNq] = useState("");
  const [nResults, setNResults] = useState<NaverProduct[] | null>(null);
  const [nLoading, setNLoading] = useState(false);
  const [picked, setPicked] = useState<NaverProduct | null>(null);
  // 광고영상/이미지에 쓸 제품 이미지 URL(네이버 선택 or Extension JSON에서 채움)
  const [productImageUrl, setProductImageUrl] = useState<string | null>(null);
  // 생성한 광고영상을 글에 첨부(발행 시 media로 나감). 공개 URL.
  const [attachedVideoUrl, setAttachedVideoUrl] = useState<string | null>(null);
  // 영상 훅 문구 — 글 카드의 "이 글로 영상"으로 채워짐(직접 수정도 가능).
  const [videoHook, setVideoHook] = useState("");
  // 쿠팡 Extension 추출 JSON(실제 쿠팡 상품 데이터)
  const [captureJson, setCaptureJson] = useState("");

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
      if (img) {
        setProductImageUrl(img);
        setPicked({
          name: typeof p.productName === "string" ? p.productName : "쿠팡 상품",
          brand: typeof p.brand === "string" ? p.brand : null,
          maker: null,
          category: typeof p.category === "string" ? p.category : null,
          price: typeof p.price === "number" ? p.price : null,
          image: img,
          link: typeof p.productUrl === "string" ? p.productUrl : null,
          mallName: "쿠팡",
          productId: null,
        });
      }
      setCaptureJson("");
      show("쿠팡 Extension JSON 적용 완료 — 제품명·특징·파트너스 링크·이미지를 채웠어요.", "success");
    } catch {
      show("추출 JSON 형식이 올바르지 않아요.", "error");
    }
  };

  const searchNaver = async () => {
    if (!nq.trim()) return;
    setNLoading(true);
    try {
      setNResults(await naverApi.searchShop(nq.trim(), 10));
    } catch (e) {
      show(e instanceof ApiError ? e.message : "상품 검색에 실패했어요.", "error");
    } finally {
      setNLoading(false);
    }
  };

  // 상품 레이더 — 카테고리별 급상승 후보(네이버 DataLab 검색+쇼핑 점수)
  const [radarCat, setRadarCat] = useState("living");
  const [radarWindow, setRadarWindow] = useState<7 | 30>(7);
  const [radar, setRadar] = useState<RadarResponse | null>(null);
  const [radarLoading, setRadarLoading] = useState(false);

  const loadRadar = async (cat: string, win: 7 | 30) => {
    setRadarCat(cat);
    setRadarWindow(win);
    setRadarLoading(true);
    try {
      setRadar(await naverApi.radar(cat, win));
    } catch (e) {
      show(e instanceof ApiError ? e.message : "레이더 조회에 실패했어요.", "error");
    } finally {
      setRadarLoading(false);
    }
  };
  useEffect(() => { loadRadar("living", 7); }, []); // 최초 1회(카테고리 목록·후보 로드)

  // 후보 키워드로 바로 상품 검색(제품 정보 채우기로 연결)
  const searchByKeyword = (kw: string) => {
    setNq(kw);
    setNResults(null);
    setNLoading(true);
    naverApi.searchShop(kw, 10)
      .then((r) => setNResults(r))
      .catch((e) => show(e instanceof ApiError ? e.message : "상품 검색에 실패했어요.", "error"))
      .finally(() => setNLoading(false));
  };

  const pickProduct = (p: NaverProduct) => {
    setProductName(p.name);
    setPicked(p);
    if (p.image) setProductImageUrl(p.image);
    setNResults(null);
    // 브랜드·카테고리를 근거 힌트로(사용자가 이미 적은 특징은 보존, 가격은 넣지 않음).
    const hint = [p.brand && `브랜드 ${p.brand}`, p.maker && p.maker !== p.brand && `제조사 ${p.maker}`, p.category && `카테고리 ${p.category}`]
      .filter(Boolean).join(" · ");
    if (hint && !productFeatures.trim()) setProductFeatures(hint);
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
        {/* 상품 레이더 — 카테고리별 급상승 후보(검색+쇼핑 점수). 접이식 */}
        <details className="rounded-lg border border-dashed p-3">
          <summary className="flex cursor-pointer items-center justify-between gap-2">
            <span className="flex items-center gap-1.5 text-sm font-medium"><TrendingUp className="size-3.5" /> 상품 레이더 <span className="font-normal text-muted-foreground">(네이버 급상승 · 발굴용)</span></span>
          </summary>
          <div className="mt-2 flex justify-end">
            <div className="flex overflow-hidden rounded-md border text-xs">
              {([7, 30] as const).map((w) => (
                <button key={w} type="button" onClick={() => loadRadar(radarCat, w)} className={`px-2.5 py-1 ${radarWindow === w ? "bg-foreground text-background" : "hover:bg-muted/50"}`}>
                  최근 {w}일
                </button>
              ))}
            </div>
          </div>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {(radar?.categories ?? []).map((c) => (
              <Button key={c.key} variant={radarCat === c.key ? "default" : "outline"} size="sm" onClick={() => loadRadar(c.key, radarWindow)}>
                {c.label}
              </Button>
            ))}
          </div>
          {radar && !radar.dataLab && (
            <p className="text-xs text-amber-600">네이버 앱에 <span className="font-medium">데이터랩(검색어트렌드·쇼핑인사이트)</span> API 사용 설정이 필요해요. 켜면 상승률 점수가 채워집니다.</p>
          )}
          {radarLoading && <p className="flex items-center gap-1.5 text-xs text-muted-foreground"><Loader2 className="size-3.5 animate-spin" /> 레이더 분석 중…</p>}
          {radar && !radarLoading && (
            <ul className="max-h-72 space-y-1.5 overflow-y-auto pr-1">
              {radar.products.map((p) => (
                <RadarRow key={p.name} p={p} onSearch={() => searchByKeyword(p.name)} />
              ))}
            </ul>
          )}
          <p className="mt-2 text-[11px] text-muted-foreground">점수 = 검색 추이·쇼핑 상승률·계절·카테고리 적합(확인 불가 항목은 제외하고 정규화). 네이버 수요 지표(쿠팡 데이터 아님) — 발굴용 참고. 상품을 누르면 그 키워드로 검색해요.</p>
        </details>

        {/* 네이버 상품 검색 — 실제 상품 정보로 근거 자동 채움 */}
        <div className="space-y-2 rounded-lg border border-dashed p-3">
          <Label className="flex items-center gap-1.5"><Search className="size-3.5" /> 네이버에서 상품 정보 가져오기 <span className="font-normal text-muted-foreground">(선택 · 정확도↑)</span></Label>
          <div className="flex gap-2">
            <Input
              placeholder="상품 키워드 검색 (예: 무선 핸디 청소기)"
              value={nq}
              onChange={(e) => setNq(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && searchNaver()}
            />
            <Button variant="outline" onClick={searchNaver} disabled={nLoading || !nq.trim()} className="shrink-0 gap-1.5">
              {nLoading ? <Loader2 className="size-4 animate-spin" /> : <Search className="size-4" />} 검색
            </Button>
          </div>
          {nResults && nResults.length === 0 && <p className="text-xs text-muted-foreground">검색 결과가 없어요.</p>}
          {nResults && nResults.length > 0 && (
            <ul className="max-h-72 space-y-1 overflow-y-auto">
              {nResults.map((p, i) => (
                <li key={i}>
                  <button type="button" onClick={() => pickProduct(p)} className="flex w-full items-center gap-3 rounded-md border p-2 text-left hover:bg-muted/50">
                    {p.image && <img src={p.image} alt="" className="size-11 shrink-0 rounded object-cover" />}
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm font-medium">{p.name}</div>
                      <div className="truncate text-xs text-muted-foreground">
                        {[p.brand, p.category].filter(Boolean).join(" · ")}
                        {p.price != null && <span> · 참고가 {p.price.toLocaleString()}원</span>}
                      </div>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {picked && (
            <div className="flex items-center gap-3 rounded-md bg-muted/40 p-2 text-xs">
              {picked.image && <img src={picked.image} alt="" className="size-10 shrink-0 rounded object-cover" />}
              <div className="min-w-0 flex-1">
                <span className="font-medium">선택됨:</span> {picked.name}
                {picked.price != null && <span className="text-muted-foreground"> · 참고가 {picked.price.toLocaleString()}원(네이버)</span>}
                <div className="text-muted-foreground">가격·이미지는 네이버 기준 참고용 — 쿠팡과 다를 수 있어 본문엔 자동 삽입되지 않아요.</div>
              </div>
              <Button variant="ghost" size="sm" onClick={() => setPicked(null)}>해제</Button>
            </div>
          )}
        </div>

        {/* 쿠팡 Extension 추출 JSON — 실제 쿠팡 상품 데이터로 채움(네이버 프록시보다 정확) */}
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
    if (!imageUrl) { show("네이버에서 상품을 선택해 제품 이미지를 먼저 잡아주세요.", "error"); return; }
    if (!productName.trim()) { show("제품명을 입력해 주세요.", "error"); return; }
    setVError(null); setVideoUrl(null); setStatus("SUBMITTED"); setJobId(null);
    try {
      const r = await affiliateVideoApi.submit({
        productName: productName.trim(),
        features: features.trim() || undefined,
        hook: hook.trim() || undefined,
        imageUrl,
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
        네이버에서 고른 제품 이미지로 <span className="font-medium">6초짜리 세로 광고영상</span>을 만들어요. SNS는 음소거 자동재생이라 큰 자막이 전달합니다.
      </p>
      {!imageUrl && <p className="text-xs text-amber-600">위 "네이버에서 상품 정보 가져오기"에서 상품을 선택하면 그 이미지로 영상을 만들 수 있어요.</p>}
      <div className="flex gap-2">
        <Input placeholder="훅 문구(선택 · 비우면 자동 · 글 카드의 '이 글로 영상'으로 채워짐)" value={hook} onChange={(e) => onHookChange(e.target.value)} />
        <Button onClick={start} disabled={busy || !imageUrl} className="shrink-0 gap-1.5">
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
function RadarRow({ p, onSearch }: { p: RadarProduct; onSearch: () => void }) {
  const find = (label: string) => p.breakdown.find((b) => b.label === label);
  const search = find("검색 추이 상승률");
  const shop = find("쇼핑 검색 상승률");
  const season = find("계절·시기 적합성");
  return (
    <li>
      <button type="button" onClick={onSearch} className="flex w-full items-center gap-3 rounded-md border p-2 text-left hover:bg-muted/50">
        <ScoreBadge score={p.score} compact />
        <div className="min-w-0 flex-1">
          <div className="text-sm font-medium">{p.name}</div>
          <div className="flex flex-wrap gap-x-2 gap-y-0.5 text-[11px] text-muted-foreground">
            <span className={search?.status === "available" ? "text-emerald-600" : ""}>검색 {search?.status === "available" ? search.note : "확인 불가"}</span>
            <span className={shop?.status === "available" ? "text-emerald-600" : ""}>쇼핑 {shop?.status === "available" ? shop.note.replace("네이버쇼핑 ", "") : "확인 불가"}</span>
            {season?.status === "available" && <span>· {season.note}</span>}
          </div>
        </div>
        <Search className="size-4 shrink-0 text-muted-foreground" />
      </button>
    </li>
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
