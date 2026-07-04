import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Copy, Download, Eye, EyeOff, Loader2, LogOut, Monitor, Moon, RefreshCw, Sun, Webhook } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAuth } from "@/store/auth";
import { useTheme } from "@/components/theme-provider";
import { accountApi } from "@/lib/account-api";
import { BILLING_SUBSCRIPTIONS_URL } from "@/lib/billing-web";
import { threadsApi } from "@/lib/threads-api";
import { postsApi } from "@/lib/posts-api";
import { toCsv, downloadCsv, download } from "@/lib/csv";
import { LEGAL } from "@/lib/legal";
import { cn } from "@/lib/utils";

// 문구·가격은 synub 통합 결제 카탈로그가 진실. 여기 표기는 그와 일치시킨다.
const PLANS = [
  { key: "FREE", name: "Free", price: "₩0", features: ["총 10개 생성", "기본 기능 체험"] },
  { key: "BASIC", name: "Basic", price: "₩15,000", features: ["월 50개 스레드", "1개 채널 연동", "기본 템플릿"] },
  { key: "PRO", name: "Pro", price: "₩25,000", features: ["무제한 스레드", "5개 채널 연동", "예약 발행", "성과 분석"] },
];

const THEMES = [
  { key: "light", label: "라이트", icon: Sun },
  { key: "dark", label: "다크", icon: Moon },
  { key: "system", label: "시스템", icon: Monitor },
] as const;

function initialOf(name?: string | null) {
  return name?.trim()?.charAt(0)?.toUpperCase() || "U";
}

export function AccountPage() {
  const user = useAuth((s) => s.user);
  const clear = useAuth((s) => s.clear);
  const { theme, setTheme } = useTheme();
  const navigate = useNavigate();

  const logout = () => {
    clear();
    navigate("/login", { replace: true });
  };

  const currentPlan = user?.plan ?? "FREE";

  return (
    <div className="w-full px-6 py-7 lg:px-8 xl:px-10">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">계정</h1>
        <p className="mt-1 text-sm text-muted-foreground">프로필 · 플랜 · 환경 설정.</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Profile + summary */}
        <div className="space-y-6 lg:col-span-1">
          <Card>
            <CardContent className="flex flex-col items-center pt-6 text-center">
              <div className="bg-brand-gradient shadow-brand flex size-20 items-center justify-center overflow-hidden rounded-2xl text-2xl font-bold text-brand-foreground">
                {user?.profileImage ? (
                  <img src={user.profileImage} alt="" className="size-full object-cover" />
                ) : (
                  initialOf(user?.name)
                )}
              </div>
              <div className="mt-4 text-lg font-semibold">{user?.name ?? "사용자"}</div>
              <div className="text-sm text-muted-foreground">{user?.email ?? "—"}</div>
              <Badge variant="info" className="mt-3">{currentPlan} 플랜</Badge>
              <Button variant="outline" className="mt-6 w-full gap-2" onClick={logout}>
                <LogOut className="size-4" /> 로그아웃
              </Button>
            </CardContent>
          </Card>

          <AccountSummaryCard />
        </div>

        {/* Settings */}
        <div className="space-y-6 lg:col-span-2">
          <Card>
            <CardHeader>
              <CardTitle>테마</CardTitle>
              <CardDescription>라이트·다크·시스템 모드를 선택하세요.</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-3 gap-3">
                {THEMES.map((t) => {
                  const active = theme === t.key;
                  return (
                    <button
                      key={t.key}
                      onClick={() => setTheme(t.key)}
                      className={cn(
                        "flex flex-col items-center gap-2 rounded-lg border p-4 text-sm transition-all",
                        active
                          ? "border-brand/50 bg-brand/10 font-medium text-foreground"
                          : "text-muted-foreground hover:bg-accent/60 hover:text-foreground",
                      )}
                    >
                      <t.icon className="size-5" />
                      {t.label}
                    </button>
                  );
                })}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>플랜</CardTitle>
              <CardDescription>현재 플랜: {currentPlan}</CardDescription>
            </CardHeader>
            <CardContent>
              <UsageBar />
              <div className="grid gap-3 sm:grid-cols-3">
                {PLANS.map((p) => {
                  const active = p.key === currentPlan;
                  return (
                    <div
                      key={p.key}
                      className={cn(
                        "flex flex-col rounded-xl border p-4 transition-all",
                        active ? "border-brand/50 bg-brand/5 shadow-brand" : "hover:border-brand/30",
                      )}
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-semibold">{p.name}</span>
                        {active && <Check className="size-4 text-brand" />}
                      </div>
                      <div className="mt-1 text-lg font-bold tabular-nums">{p.price}<span className="ml-1 text-xs font-normal text-muted-foreground">/월</span></div>
                      <ul className="mt-3 flex-1 space-y-1.5">
                        {p.features.map((f) => (
                          <li key={f} className="flex items-center gap-1.5 text-sm text-foreground/80">
                            <Check className="size-3.5 shrink-0 text-brand" /> {f}
                          </li>
                        ))}
                      </ul>
                      {active ? (
                        <a
                          href={BILLING_SUBSCRIPTIONS_URL}
                          target="_blank"
                          rel="noreferrer"
                          className="bg-brand/10 text-brand hover:bg-brand/15 mt-4 w-full rounded-md py-1.5 text-center text-xs font-semibold transition-colors"
                        >
                          구독 관리
                        </a>
                      ) : p.key !== "FREE" ? (
                        <a
                          href={BILLING_SUBSCRIPTIONS_URL}
                          target="_blank"
                          rel="noreferrer"
                          className="border-border hover:border-brand/40 hover:text-brand mt-4 w-full rounded-md border py-1.5 text-center text-xs font-semibold text-muted-foreground transition-colors"
                        >
                          이 플랜으로
                        </a>
                      ) : null}
                    </div>
                  );
                })}
              </div>
              <p className="mt-3 text-xs text-muted-foreground">
                구독·결제·플랜 변경은{" "}
                <a href={BILLING_SUBSCRIPTIONS_URL} target="_blank" rel="noreferrer" className="text-brand font-medium hover:underline">
                  synub 통합 결제
                </a>
                에서 관리됩니다.
              </p>
            </CardContent>
          </Card>

          <WebhookCard />

          <ExportCard />

          <p className="pt-2 text-center text-xs text-muted-foreground">
            <a href={LEGAL.terms} target="_blank" rel="noreferrer" className="underline hover:text-foreground">이용약관</a>
            {" · "}
            <a href={LEGAL.privacy} target="_blank" rel="noreferrer" className="underline hover:text-foreground">개인정보처리방침</a>
          </p>
        </div>
      </div>
    </div>
  );
}

function UsageBar() {
  const { data } = useQuery({ queryKey: ["account", "usage"], queryFn: accountApi.usage });
  if (!data) return null;
  const unlimited = data.limit < 0;
  const pct = unlimited ? 100 : Math.min(100, Math.round((data.used / Math.max(1, data.limit)) * 100));
  return (
    <div className="mb-5 rounded-xl border border-border/60 p-4">
      {data.cancelScheduled && (
        <div className="mb-3 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-600 dark:text-amber-400">
          구독 취소 예약됨 — {data.currentPeriodEnd ? new Date(data.currentPeriodEnd).toLocaleDateString("ko-KR") + "까지" : "기간 말까지"} 현재 플랜을 이용할 수 있어요. 이후 자동으로 무료 전환됩니다.
        </div>
      )}
      <div className="flex items-center justify-between text-sm">
        <span className="font-medium">{data.lifetimeCap ? "무료 체험 생성 (총)" : "이번 달 AI 생성"}</span>
        <span className="tabular-nums text-muted-foreground">
          {unlimited ? `${data.used} / 무제한` : `${data.used} / ${data.limit}`}
        </span>
      </div>
      {!unlimited && (
        <div className="mt-2 h-2 overflow-hidden rounded-full bg-muted">
          <div className={cn("h-full rounded-full", pct >= 100 ? "bg-rose-500" : "bg-brand-gradient")} style={{ width: `${pct}%` }} />
        </div>
      )}
      <div className="mt-3 flex flex-wrap gap-1.5">
        {[
          { label: "예약 발행", on: data.canSchedule },
          { label: "시리즈 생성", on: data.canSeries },
          { label: "다중 계정", on: data.canMultiAccount },
        ].map((f) => (
          <Badge key={f.label} variant={f.on ? "success" : "secondary"}>
            {f.on ? "✓" : "🔒"} {f.label}
          </Badge>
        ))}
      </div>

      {data.plan !== "FREE" && data.currentPeriodEnd && (
        <div className="mt-3 border-t border-border/60 pt-3 text-xs text-muted-foreground">
          {data.cancelScheduled ? "이용 종료" : "다음 결제"}:{" "}
          <span className="font-medium text-foreground/80">{new Date(data.currentPeriodEnd).toLocaleDateString("ko-KR")}</span>
        </div>
      )}
    </div>
  );
}

function AccountSummaryCard() {
  const { data: usage } = useQuery({ queryKey: ["account", "usage"], queryFn: accountApi.usage });
  const { data: accounts } = useQuery({ queryKey: ["threads-accounts"], queryFn: threadsApi.accounts });
  const rows = [
    { label: "현재 플랜", value: usage?.plan ?? "—" },
    {
      label: usage?.cancelScheduled ? "이용 종료" : "다음 결제",
      value: usage?.currentPeriodEnd ? new Date(usage.currentPeriodEnd).toLocaleDateString("ko-KR") : "—",
    },
    { label: "연결 채널", value: `${accounts?.length ?? 0}개` },
    {
      label: usage?.lifetimeCap ? "무료 체험 (총)" : "이번 달 생성",
      value: usage ? `${usage.used} / ${usage.limit < 0 ? "무제한" : usage.limit}` : "—",
    },
  ];
  return (
    <Card>
      <CardHeader>
        <CardTitle>요약</CardTitle>
        <CardDescription>내 구독·이용 현황</CardDescription>
      </CardHeader>
      <CardContent>
        <ul className="divide-y divide-border/60 text-sm">
          {rows.map((r) => (
            <li key={r.label} className="flex items-center justify-between py-2.5">
              <span className="text-muted-foreground">{r.label}</span>
              <span className="font-medium tabular-nums">{r.value}</span>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}

function ExportCard() {
  const exportData = async (format: "json" | "csv") => {
    const posts = await postsApi.list();
    if (format === "json") {
      download(`postflow-posts-${today()}.json`, JSON.stringify(posts, null, 2), "application/json");
    } else {
      const csv = toCsv(
        ["id", "content", "hashtags", "cta", "status", "score", "scheduledAt", "publishedAt", "createdAt"],
        posts.map((p) => [p.id, p.content, (p.hashtags ?? []).join(" "), p.cta, p.status, p.score, p.scheduledAt, p.publishedAt, p.createdAt]),
      );
      downloadCsv(`postflow-posts-${today()}.csv`, csv);
    }
  };
  return (
    <Card className="mt-6">
      <CardHeader>
        <CardTitle>데이터 내보내기</CardTitle>
        <CardDescription>내 게시물 전체를 파일로 백업합니다.</CardDescription>
      </CardHeader>
      <CardContent className="flex gap-2">
        <Button variant="outline" size="sm" className="gap-1.5" onClick={() => exportData("csv")}>
          <Download className="size-4" /> CSV
        </Button>
        <Button variant="outline" size="sm" className="gap-1.5" onClick={() => exportData("json")}>
          <Download className="size-4" /> JSON
        </Button>
      </CardContent>
    </Card>
  );
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

function WebhookCard() {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ["account", "webhook"], queryFn: accountApi.webhook });
  const [reveal, setReveal] = useState(false);
  const [copied, setCopied] = useState<string | null>(null);
  const [guide, setGuide] = useState(false);
  const regen = useMutation({
    mutationFn: accountApi.regenerateWebhook,
    onSuccess: (d) => qc.setQueryData(["account", "webhook"], d),
  });

  const copy = async (text: string, which: string) => {
    await navigator.clipboard.writeText(text);
    setCopied(which);
    setTimeout(() => setCopied(null), 1500);
  };

  const mask = (s: string) => (reveal ? s : "•".repeat(Math.min(s.length, 32)));

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Webhook className="size-4 text-brand" /> 전환 웹훅
        </CardTitle>
        <CardDescription>외부 결제·스토어의 매출을 자동 귀속하려면 이 시크릿으로 요청에 서명하세요.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {isLoading || !data ? (
          <div className="flex items-center gap-2 py-4 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" /> 불러오는 중…
          </div>
        ) : (
          <>
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground">엔드포인트</label>
              <div className="flex gap-2">
                <Input readOnly value={data.endpoint} className="font-mono text-xs" />
                <Button variant="outline" size="icon" title="복사" onClick={() => copy(data.endpoint, "ep")}>
                  {copied === "ep" ? <Check className="size-4 text-emerald-600" /> : <Copy className="size-4" />}
                </Button>
              </div>
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground">시크릿 (HMAC-SHA256)</label>
              <div className="flex gap-2">
                <Input readOnly value={mask(data.secret)} className="font-mono text-xs" />
                <Button variant="outline" size="icon" title={reveal ? "숨기기" : "보기"} onClick={() => setReveal((v) => !v)}>
                  {reveal ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </Button>
                <Button variant="outline" size="icon" title="복사" onClick={() => copy(data.secret, "sec")}>
                  {copied === "sec" ? <Check className="size-4 text-emerald-600" /> : <Copy className="size-4" />}
                </Button>
              </div>
            </div>
            <div className="flex items-center justify-between pt-1">
              <Button variant="outline" size="sm" onClick={() => setGuide((v) => !v)}>
                {guide ? "연동 가이드 닫기" : "연동 가이드 보기"}
              </Button>
              <Button variant="ghost" size="sm" className="gap-1.5" disabled={regen.isPending} onClick={() => regen.mutate()}>
                {regen.isPending ? <Loader2 className="size-4 animate-spin" /> : <RefreshCw className="size-4" />} 재발급
              </Button>
            </div>

            {guide && (
              <div className="space-y-3 rounded-xl border border-border/60 bg-muted/30 p-4">
                <div>
                  <p className="text-sm font-medium">언제 호출하나요?</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    내 쇼핑몰·강의·예약 등 <b>외부에서 결제가 완료될 때</b>, 결제 완료 처리 코드에서 아래처럼 한 번 POST 하세요.
                    그러면 그 매출이 추적링크(slug)를 통해 해당 글의 ROI로 자동 집계됩니다.
                  </p>
                </div>
                <div>
                  <p className="mb-1 text-xs font-medium text-muted-foreground">본문 필드</p>
                  <ul className="space-y-0.5 text-xs text-muted-foreground">
                    <li>• <code className="rounded bg-muted px-1">slug</code> 또는 <code className="rounded bg-muted px-1">postId</code> — 필수(택1, 어느 글의 전환인지)</li>
                    <li>• <code className="rounded bg-muted px-1">amount</code> — 필수, 결제 금액</li>
                    <li>• <code className="rounded bg-muted px-1">currency</code> — 선택(기본 KRW), <code className="rounded bg-muted px-1">note</code> — 선택(메모)</li>
                  </ul>
                </div>
                <div>
                  <div className="mb-1 flex items-center justify-between">
                    <p className="text-xs font-medium text-muted-foreground">예제 (Node.js)</p>
                    <Button variant="ghost" size="sm" className="h-6 gap-1 px-2 text-xs" onClick={() => copy(snippet(data.endpoint), "code")}>
                      {copied === "code" ? <Check className="size-3 text-emerald-600" /> : <Copy className="size-3" />} 복사
                    </Button>
                  </div>
                  <pre className="overflow-x-auto rounded-lg bg-background p-3 text-[11px] leading-relaxed">{snippet(data.endpoint)}</pre>
                </div>
                <p className="text-[11px] text-muted-foreground">
                  서명은 <code className="rounded bg-muted px-1">HMAC-SHA256(타임스탬프 + "." + 본문)</code>, 시크릿은 위에서 복사하세요.
                  타임스탬프가 5분 이상 차이 나면 거부됩니다(재전송 방지).
                </p>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}

function snippet(endpoint: string) {
  return `const crypto = require("crypto");
const SECRET = "여기에_시크릿_붙여넣기";

const body = JSON.stringify({ slug: "추적링크_slug", amount: 29000, currency: "KRW" });
const ts = Math.floor(Date.now() / 1000).toString();
const sig = crypto.createHmac("sha256", SECRET).update(ts + "." + body).digest("hex");

await fetch("${endpoint}", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "X-PostFlow-Timestamp": ts,
    "X-PostFlow-Signature": sig,
  },
  body,
});`;
}
