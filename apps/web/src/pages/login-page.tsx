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
    "키워드로 댓글 자동 응답",
    "글이 만든 실제 매출까지 추적",
  ];

  return (
    // 빌링 로그인 디자인 규칙 미러 — 배경 위 '떠있는 카드'(2단 분할), 슬라이드업 등장.
    <div className="relative flex min-h-dvh flex-col items-center justify-center gap-5 bg-gradient-to-b from-muted/20 via-muted/40 to-muted/70 p-4 sm:p-6">
      <div className="absolute right-4 top-4 z-10">
        <ThemeToggle />
      </div>

      <div className="animate-fade-up w-full max-w-5xl overflow-hidden rounded-3xl border border-border/60 shadow-2xl">
        <div className="grid md:min-h-[38rem] md:grid-cols-2">
          {/* 왼쪽 — 브랜드 패널(데스크톱 전용) */}
          <div className="bg-brand-animated relative hidden flex-col justify-between overflow-hidden p-12 text-brand-foreground md:flex">
            <div className="pointer-events-none absolute inset-0">
              <div className="animate-float absolute -left-20 -top-20 size-96 rounded-full bg-white/15 blur-3xl" />
              <div className="animate-float absolute -bottom-28 -right-16 size-[26rem] rounded-full bg-white/10 blur-3xl" style={{ animationDelay: "2s" }} />
            </div>

            <div className="relative flex items-center gap-3">
              <div className="flex size-11 items-center justify-center rounded-xl bg-white/20 backdrop-blur">
                <Sparkles className="size-6" />
              </div>
              <span className="text-2xl font-extrabold tracking-tight">PostFlow</span>
            </div>

            <div className="relative max-w-xs">
              <h2 className="text-[26px] font-extrabold leading-tight tracking-tight">
                한 번 만들고,
                <br />
                자동으로 성장시키세요.
              </h2>
              <p className="mt-3 text-sm leading-relaxed text-brand-foreground/70">
                콘텐츠 생성부터 예약 발행·댓글 자동화·매출 추적까지 — PostFlow 하나로.
              </p>
              <ul className="mt-8 space-y-3.5">
                {benefits.map((t) => (
                  <li key={t} className="flex items-center gap-3 text-sm text-brand-foreground/90">
                    <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-white/20">
                      <Check className="size-3" strokeWidth={3} />
                    </span>
                    {t}
                  </li>
                ))}
              </ul>
            </div>

            <div className="relative text-[11px] leading-relaxed text-brand-foreground/60">
              <a href={LEGAL.terms} target="_blank" rel="noreferrer" className="hover:text-brand-foreground">서비스 약관</a>
              <span className="mx-2 text-brand-foreground/30">·</span>
              <a href={LEGAL.privacy} target="_blank" rel="noreferrer" className="hover:text-brand-foreground">개인정보 처리방침</a>
            </div>
          </div>

          {/* 오른쪽 — 로그인 */}
          <div className="flex flex-col justify-center bg-card p-7 sm:p-10">
            {/* 모바일 브랜드 헤더 */}
            <div className="mb-5 flex flex-col items-center text-center md:hidden">
              <div className="bg-brand-gradient shadow-brand mb-2 flex size-12 items-center justify-center rounded-2xl text-brand-foreground">
                <Sparkles className="size-6" />
              </div>
              <h1 className="text-gradient-brand text-2xl font-extrabold tracking-tight">PostFlow</h1>
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
    </div>
  );
}
