import { useEffect, useRef, useState } from "react";
import { Navigate, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { Check, Loader2, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ThemeToggle } from "@/components/theme-toggle";
import { authApi, DEMO_LOGIN } from "@/lib/auth-api";
import { ApiError } from "@/lib/api";
import { getContext, setRefreshToken, useAuth } from "@/store/auth";
import { LEGAL } from "@/lib/legal";
import { COMPANY } from "@/lib/company";
import { cn } from "@/lib/utils";

export function LoginPage() {
  const token = useAuth((s) => s.token);
  const setToken = useAuth((s) => s.setToken);
  const setAuth = useAuth((s) => s.setAuth);
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const autoDemo = params.has("demo"); // 무로그인 체험 진입점(빌링 데모 URL)
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const autoRan = useRef(false);

  // ?demo → 데모 계정으로 자동 로그인해 바로 둘러보기(로그인 화면을 거치지 않음).
  useEffect(() => {
    if (autoDemo && !token && !autoRan.current) {
      autoRan.current = true;
      doLogin(DEMO_LOGIN.email, DEMO_LOGIN.password);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoDemo, token]);

  if (token) {
    const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname;
    return <Navigate to={from ?? "/"} replace />;
  }

  const doLogin = async (mail: string, pw: string) => {
    setLoading(true);
    setError(null);
    try {
      const tokens = await authApi.login(mail, pw);
      setToken(tokens.accessToken); // 먼저 저장해야 /auth/me 가 Authorization 헤더에 실림
      setRefreshToken(tokens.refreshToken);
      const user = await authApi.me(getContext()); // 선택 컨텍스트(기본 개인)로 플랜 판정
      setAuth(tokens.accessToken, user);
      authApi.contexts().then((cs) => useAuth.getState().setContexts(cs)).catch(() => {});
      navigate("/", { replace: true });
    } catch (e) {
      // SSO 인증은 됐지만 접근 허가 계정이 아님(비공개 베타) → 백엔드 403 메시지 노출.
      if (e instanceof ApiError && e.status === 403) {
        useAuth.getState().clear();
        setError(e.message);
      } else {
        setError("로그인에 실패했어요. 이메일·비밀번호를 확인해 주세요.");
      }
    } finally {
      setLoading(false);
    }
  };

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (email.trim() && password) doLogin(email.trim(), password);
  };

  const benefits = [
    "AI가 관심 끄는 글을 1분 만에",
    "최적 시간대에 예약·자동 발행",
    "댓글 자동 응답·매출 추적까지",
  ];

  return (
    // 로그인 디자인 정본(LOGIN_PAGE_DESIGN) — 배경 위 '떠있는 카드'(2단 분할), 슬라이드업 등장.
    <div className="relative flex min-h-dvh flex-col items-center justify-center gap-5 bg-gradient-to-b from-muted/20 via-muted/40 to-muted/70 p-4 sm:p-6">
      <div className="absolute right-4 top-4 z-10">
        <ThemeToggle />
      </div>

      <div className="animate-slide-up w-full max-w-5xl overflow-hidden rounded-3xl border border-border/60 shadow-[var(--shadow-pop)]">
        <div className="grid md:min-h-[38rem] md:grid-cols-2">
          {/* 왼쪽 — 브랜드 패널(데스크톱 전용, 어두운 네이비. 브랜드 3색은 로고에만) */}
          <div className="dark relative hidden flex-col justify-between bg-gradient-to-br from-[#14233f] to-[#0a0f1c] p-12 text-white md:flex">
            <div className="flex items-center gap-3">
              <img src="/synub-symbol-dark.png" alt="Synub" className="size-11" />
              <span className="text-2xl font-extrabold tracking-tight">PostFlow</span>
            </div>

            <div className="max-w-xs">
              <h2 className="text-[26px] font-extrabold leading-tight tracking-tight">
                한 번 만들고,
                <br />
                자동으로 성장시키세요.
              </h2>
              <p className="mt-3 text-sm leading-relaxed text-white/60">
                콘텐츠 생성부터 예약 발행·댓글 자동화·매출 추적까지 — PostFlow 하나로.
              </p>
              <ul className="mt-8 space-y-3.5">
                {benefits.map((t) => (
                  <li key={t} className="flex items-center gap-3 text-sm text-white/85">
                    <span className="bg-success/20 flex size-5 shrink-0 items-center justify-center rounded-full">
                      <Check className="text-success size-3" strokeWidth={3} />
                    </span>
                    {t}
                  </li>
                ))}
              </ul>
            </div>

            <Fineprint tone="dark" align="left" />
          </div>

          {/* 오른쪽 — 로그인 */}
          <div className="flex flex-col justify-center bg-card p-7 sm:p-10">
            {/* 모바일 브랜드 헤더 */}
            <div className="mb-5 flex flex-col items-center text-center md:hidden">
              <img src="/synub-symbol-light.png" alt="Synub" className="mb-2 size-12 dark:hidden" />
              <img src="/synub-symbol-dark.png" alt="Synub" className="mb-2 hidden size-12 dark:block" />
              <h1 className="text-2xl font-extrabold tracking-tight">PostFlow</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                통합계정 하나로 synub의 모든 서비스를 이용하세요.
              </p>
            </div>
            {/* 데스크톱 제목 */}
            <div className="mb-5 hidden md:block">
              <h1 className="text-2xl font-extrabold tracking-tight">로그인</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                synub 통합계정으로 synub의 모든 서비스를 이용하세요.
              </p>
            </div>

            <form className="space-y-3.5" onSubmit={onSubmit}>
              <div className="space-y-1.5">
                <Label htmlFor="email">이메일</Label>
                {/* type=text: 일반 유저는 이메일, 운영 계정(haru/sky/admin)은 아이디 로그인 —
                    type=email이면 '@' 없는 아이디가 브라우저 검증에 막힌다. 인증은 SSO가 게이트. */}
                <Input id="email" type="text" autoComplete="username" placeholder="you@example.com"
                  value={email} onChange={(e) => setEmail(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="password">비밀번호</Label>
                <Input id="password" type="password" autoComplete="current-password" placeholder="••••••••"
                  value={password} onChange={(e) => setPassword(e.target.value)} />
              </div>
              {error && <p className="text-xs text-destructive">{error}</p>}
              <Button type="submit" size="lg" className="w-full gap-2" disabled={loading || !email.trim() || !password}>
                {loading && <Loader2 className="size-4 animate-spin" />} 로그인
              </Button>
            </form>

            <div className="my-4 flex items-center gap-3 text-xs text-muted-foreground">
              <div className="h-px flex-1 bg-border" />
              또는
              <div className="h-px flex-1 bg-border" />
            </div>

            <Button variant="outline" size="lg" className="w-full gap-2" disabled={loading}
              onClick={() => doLogin(DEMO_LOGIN.email, DEMO_LOGIN.password)}>
              <Sparkles className="size-4" /> 데모로 둘러보기
            </Button>
            <p className="mt-2 text-center text-[11px] text-muted-foreground">
              가입 없이 데모 계정으로 전체 기능을 체험할 수 있어요.
            </p>
          </div>
        </div>
      </div>

      {/* 모바일 사업자정보 Fineprint(데스크톱은 왼쪽 패널 하단이 대신) */}
      <Fineprint tone="light" align="center" className="md:hidden" />
    </div>
  );
}

/** 전자상거래 표시 — 요금·약관·개인정보 링크 + 사업자정보. 밝은/어두운 톤 공용. */
function Fineprint({ tone, align, className }: { tone: "light" | "dark"; align: "center" | "left"; className?: string }) {
  const linkCls = tone === "dark" ? "text-white/70 hover:text-white" : "text-muted-foreground hover:text-foreground";
  const infoCls = tone === "dark" ? "text-white/45" : "text-muted-foreground/70";
  const sepCls = tone === "dark" ? "text-white/25" : "text-border";
  const borderCls = tone === "dark" ? "border-white/10" : "border-border/60";
  const justify = align === "center" ? "justify-center" : "justify-start";
  return (
    <div className={cn("text-[11px] leading-relaxed", align === "center" ? "text-center" : "text-left", className)}>
      <div className={cn("flex flex-wrap items-center gap-x-3 gap-y-1 font-medium", justify)}>
        <a href={LEGAL.terms} target="_blank" rel="noreferrer" className={linkCls}>이용약관</a>
        <span className={sepCls}>·</span>
        <a href={LEGAL.privacy} target="_blank" rel="noreferrer" className={cn("font-semibold", linkCls)}>개인정보처리방침</a>
      </div>
      <div className={cn("mt-3 space-y-1 border-t pt-3", infoCls, borderCls)}>
        <div className={cn("flex flex-wrap items-center gap-x-2 gap-y-0.5", justify)}>
          {[COMPANY.legalName, `대표 ${COMPANY.ceo}`, `사업자등록번호 ${COMPANY.bizRegNo}`, `통신판매업 ${COMPANY.mailOrderNo}`].map((item, i) => (
            <span key={item} className="flex items-center gap-2">
              {i > 0 && <span className={sepCls}>·</span>}
              <span className="whitespace-nowrap">{item}</span>
            </span>
          ))}
        </div>
        <p>{COMPANY.address}</p>
        <div className={cn("flex flex-wrap items-center gap-x-2", justify)}>
          <a href={`tel:${COMPANY.tel.replace(/[^0-9+]/g, "")}`} className="whitespace-nowrap hover:underline">고객센터 {COMPANY.tel}</a>
          <span className={sepCls}>·</span>
          <a href={`mailto:${COMPANY.email}`} className="whitespace-nowrap hover:underline">{COMPANY.email}</a>
        </div>
      </div>
    </div>
  );
}
