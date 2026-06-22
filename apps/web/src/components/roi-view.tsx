import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Copy, DollarSign, Eye, Link2, Loader2, MousePointerClick, UserPlus } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { roiApi } from "@/lib/roi-api";
import { postsApi } from "@/lib/posts-api";

const nf = new Intl.NumberFormat("ko-KR");
const won = (n: number) => "₩" + nf.format(Math.round(n));
const pct = (n: number) => `${(n * 100).toFixed(2)}%`;

export function RoiView() {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ["roi"], queryFn: roiApi.dashboard });
  const { data: posts } = useQuery({ queryKey: ["posts"], queryFn: postsApi.list });

  if (isLoading || !data) {
    return (
      <div className="flex items-center justify-center gap-2 py-20 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" /> 불러오는 중…
      </div>
    );
  }

  const funnel = [
    { label: "조회", value: data.views, icon: Eye, note: "Threads Insights" },
    { label: "클릭", value: data.clicks, icon: MousePointerClick, note: `CTR ${pct(data.ctr)}` },
    { label: "리드", value: data.leads, icon: UserPlus, note: `리드율 ${pct(data.leadRate)}` },
    { label: "전환", value: data.conversions, icon: DollarSign, note: `구매전환 ${pct(data.purchaseRate)}` },
  ];
  const maxF = Math.max(1, ...funnel.map((f) => f.value));
  const topMax = Math.max(1, ...data.topByRevenue.map((p) => p.revenue));

  return (
    <div className="space-y-6">
      {/* revenue / cost KPIs */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {[
          { label: "귀속 매출", value: won(data.revenue) },
          { label: "총 비용", value: won(data.cost) },
          { label: "순이익", value: won(data.netRevenue), accent: data.netRevenue >= 0 },
          { label: "ROI (수익률)", value: data.roiPercent != null ? `${data.roiPercent.toFixed(0)}%` : "비용 미입력", roi: true },
        ].map((k, i) => (
          <Card key={k.label} className="lift animate-fade-up p-5 hover:border-brand/40 hover:shadow-brand" style={{ animationDelay: `${i * 60}ms` }}>
            <div className="text-sm text-muted-foreground">{k.label}</div>
            <div
              className={
                "mt-2 text-2xl font-semibold tabular-nums tracking-tight " +
                (k.roi && data.roiPercent != null
                  ? data.roiPercent >= 0 ? "text-emerald-600 dark:text-emerald-400" : "text-rose-600 dark:text-rose-400"
                  : k.accent === false ? "text-rose-600 dark:text-rose-400" : "")
              }
            >
              {k.value}
            </div>
          </Card>
        ))}
      </div>

      {/* funnel */}
      <Card className="p-6">
        <h2 className="font-semibold">수익 퍼널</h2>
        <p className="mt-0.5 text-sm text-muted-foreground">조회 → 클릭 → 리드 → 전환</p>
        <div className="mt-5 space-y-4">
          {funnel.map((f) => (
            <div key={f.label}>
              <div className="mb-1 flex items-center justify-between text-sm">
                <span className="flex items-center gap-1.5 text-muted-foreground">
                  <f.icon className="size-3.5" /> {f.label}
                  <span className="text-xs text-muted-foreground/70">· {f.note}</span>
                </span>
                <span className="font-medium tabular-nums">{nf.format(f.value)}</span>
              </div>
              <div className="h-2.5 overflow-hidden rounded-full bg-muted">
                <div className="bg-brand-gradient h-full rounded-full transition-[width] duration-500" style={{ width: `${(f.value / maxF) * 100}%` }} />
              </div>
            </div>
          ))}
        </div>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <ActionsCard posts={posts ?? []} onChanged={() => qc.invalidateQueries({ queryKey: ["roi"] })} />

        <Card className="p-6">
          <h2 className="font-semibold">매출 상위 게시물</h2>
          <p className="mt-0.5 text-sm text-muted-foreground">귀속 매출 기준</p>
          {data.topByRevenue.length > 0 ? (
            <div className="mt-5 space-y-4">
              {data.topByRevenue.map((p, i) => (
                <div key={p.postId}>
                  <div className="mb-1 flex items-center gap-2 text-sm">
                    <span className="text-gradient-brand w-5 shrink-0 font-bold tabular-nums">{i + 1}</span>
                    <span className="min-w-0 flex-1 truncate">{p.content}</span>
                    {p.roiPercent != null && (
                      <span className={`shrink-0 text-xs font-medium tabular-nums ${p.roiPercent >= 0 ? "text-emerald-600 dark:text-emerald-400" : "text-rose-600 dark:text-rose-400"}`}>
                        ROI {p.roiPercent.toFixed(0)}%
                      </span>
                    )}
                    <span className="shrink-0 font-medium tabular-nums">{won(p.revenue)}</span>
                  </div>
                  <div className="ml-7 h-2 overflow-hidden rounded-full bg-muted">
                    <div className="bg-brand-gradient h-full rounded-full" style={{ width: `${(p.revenue / topMax) * 100}%` }} />
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="mt-5 rounded-lg border border-dashed py-10 text-center text-sm text-muted-foreground">
              아직 매출이 없어요. 아래에서 추적 링크를 만들고 매출을 기록해 보세요.
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}

function ActionsCard({ posts, onChanged }: { posts: { id: number; content: string }[]; onChanged: () => void }) {
  const [linkPost, setLinkPost] = useState<string>("");
  const [dest, setDest] = useState("https://");
  const [captureLead, setCaptureLead] = useState(false);
  const [headline, setHeadline] = useState("무료 체크리스트 받기");
  const [created, setCreated] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const [revPost, setRevPost] = useState<string>("");
  const [amount, setAmount] = useState("");

  const [costPost, setCostPost] = useState<string>("");
  const [cost, setCost] = useState("");

  const createLink = useMutation({
    mutationFn: () => roiApi.createCtaLink(Number(linkPost), dest, undefined, captureLead, captureLead ? headline : undefined),
    onSuccess: (r) => setCreated(r.shortUrl),
  });
  const addRevenue = useMutation({
    mutationFn: () => roiApi.createConversion(Number(revPost), Number(amount)),
    onSuccess: () => {
      setAmount("");
      onChanged();
    },
  });
  const addCost = useMutation({
    mutationFn: () => roiApi.setCost(Number(costPost), Number(cost)),
    onSuccess: () => {
      setCost("");
      onChanged();
    },
  });

  const label = (id: number) => posts.find((p) => p.id === id)?.content?.slice(0, 24) ?? `#${id}`;

  return (
    <Card className="p-6">
      <h2 className="font-semibold">추적 & 매출 기록</h2>
      <p className="mt-0.5 text-sm text-muted-foreground">CTA 추적 링크를 만들고 매출을 귀속하세요.</p>

      {/* create tracking link */}
      <div className="mt-5 space-y-2">
        <Label>추적 링크 만들기</Label>
        <Select value={linkPost} onValueChange={setLinkPost}>
          <SelectTrigger><SelectValue placeholder="게시물 선택" /></SelectTrigger>
          <SelectContent>
            {posts.map((p) => <SelectItem key={p.id} value={String(p.id)}>{label(p.id)}</SelectItem>)}
          </SelectContent>
        </Select>
        <Input value={dest} onChange={(e) => setDest(e.target.value)} placeholder="https://도착 페이지" />
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <input type="checkbox" className="size-4 accent-[var(--brand)]" checked={captureLead} onChange={(e) => setCaptureLead(e.target.checked)} />
          리드 수집 폼 사용 (이메일 먼저 받고 이동)
        </label>
        {captureLead && (
          <Input value={headline} onChange={(e) => setHeadline(e.target.value)} placeholder="랜딩 헤드라인 (예: 무료 체크리스트 받기)" />
        )}
        <Button className="w-full gap-1.5" disabled={!linkPost || !dest.startsWith("http") || createLink.isPending} onClick={() => createLink.mutate()}>
          {createLink.isPending ? <Loader2 className="size-4 animate-spin" /> : <Link2 className="size-4" />} 링크 생성
        </Button>
        {created && (
          <div className="flex items-center gap-2 rounded-md border bg-muted/40 px-2.5 py-1.5 text-xs">
            <span className="min-w-0 flex-1 truncate">{created}</span>
            <button
              className="flex items-center gap-1 text-muted-foreground hover:text-foreground"
              onClick={async () => { await navigator.clipboard.writeText(created); setCopied(true); setTimeout(() => setCopied(false), 1500); }}
            >
              {copied ? <Check className="size-3.5 text-emerald-600" /> : <Copy className="size-3.5" />} 복사
            </button>
          </div>
        )}
      </div>

      {/* log revenue */}
      <div className="mt-5 space-y-2 border-t border-border/60 pt-5">
        <Label>매출 기록</Label>
        <Select value={revPost} onValueChange={setRevPost}>
          <SelectTrigger><SelectValue placeholder="게시물 선택" /></SelectTrigger>
          <SelectContent>
            {posts.map((p) => <SelectItem key={p.id} value={String(p.id)}>{label(p.id)}</SelectItem>)}
          </SelectContent>
        </Select>
        <Input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="금액 (원)" />
        <Button variant="outline" className="w-full gap-1.5" disabled={!revPost || !amount || Number(amount) <= 0 || addRevenue.isPending} onClick={() => addRevenue.mutate()}>
          {addRevenue.isPending ? <Loader2 className="size-4 animate-spin" /> : <DollarSign className="size-4" />} 매출 추가
        </Button>
      </div>

      {/* set cost (ROI denominator) */}
      <div className="mt-5 space-y-2 border-t border-border/60 pt-5">
        <Label>비용 입력 (광고·프로모션 → ROI%)</Label>
        <Select value={costPost} onValueChange={setCostPost}>
          <SelectTrigger><SelectValue placeholder="게시물 선택" /></SelectTrigger>
          <SelectContent>
            {posts.map((p) => <SelectItem key={p.id} value={String(p.id)}>{label(p.id)}</SelectItem>)}
          </SelectContent>
        </Select>
        <Input type="number" value={cost} onChange={(e) => setCost(e.target.value)} placeholder="비용 (원)" />
        <Button variant="outline" className="w-full gap-1.5" disabled={!costPost || cost === "" || Number(cost) < 0 || addCost.isPending} onClick={() => addCost.mutate()}>
          {addCost.isPending ? <Loader2 className="size-4 animate-spin" /> : <DollarSign className="size-4" />} 비용 저장
        </Button>
      </div>
    </Card>
  );
}
