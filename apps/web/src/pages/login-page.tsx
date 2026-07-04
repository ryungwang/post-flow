import { useEffect, useRef, useState } from "react";
import { Navigate, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { BarChart3, CalendarClock, Loader2, MessageSquareReply, Sparkles, TrendingUp, Wand2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ThemeToggle } from "@/components/theme-toggle";
import { authApi, DEMO_LOGIN } from "@/lib/auth-api";
import { ApiError } from "@/lib/api";
import { setRefreshToken, useAuth } from "@/store/auth";
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
      const user = await authApi.me();
      setAuth(tokens.accessToken, user);
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

  const features = [
    { icon: Wand2, title: "AI 콘텐츠 생성", desc: "관심 끄는 글을 1분 만에" },
    { icon: CalendarClock, title: "예약·자동 발행", desc: "최적 시간대에 알아서" },
    { icon: MessageSquareReply, title: "댓글 자동화", desc: "키워드로 자동 응답·DM 유도" },
    { icon: TrendingUp, title: "ROI 추적", desc: "글이 만든 실제 매출까지" },
  ];

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* LEFT — brand / value panel */}
      <div className="bg-brand-animated relative hidden overflow-hidden p-12 text-brand-foreground lg:flex lg:flex-col">
        <div className="pointer-events-none absolute inset-0">
          <div className="absolute -left-20 -top-20 size-96 rounded-full bg-white/15 blur-3xl animate-float" />
          <div className="absolute -bottom-28 -right-16 size-[26rem] rounded-full bg-white/10 blur-3xl animate-float" style={{ animationDelay: "2s" }} />
        </div>

        <div className="relative flex items-center gap-2">
          <div className="flex size-9 items-center justify-center rounded-xl bg-white/20 backdrop-blur">
            <Sparkles className="size-5" />
          </div>
          <span className="text-xl font-bold tracking-tight">PostFlow</span>
        </div>

        <div className="relative my-auto max-w-md">
          <h1 className="text-4xl font-bold leading-tight tracking-tight">
            한 번 만들고,<br />자동으로 성장시키세요
          </h1>
          <p className="mt-4 text-base text-brand-foreground/80">
            Threads 콘텐츠 생성부터 예약 발행·댓글 자동화·매출 추적까지. 크리에이터를 위한 AI 자동화 워크스페이스.
          </p>

          <div className="mt-10 grid grid-cols-2 gap-4">
            {features.map((f) => (
              <div key={f.title} className="rounded-2xl bg-white/10 p-4 backdrop-blur-sm">
                <f.icon className="size-5" />
                <div className="mt-2 text-sm font-semibold">{f.title}</div>
                <div className="text-xs text-brand-foreground/75">{f.desc}</div>
              </div>
            ))}
          </div>
        </div>

        <div className="relative flex items-center gap-2 text-sm text-brand-foreground/70">
          <BarChart3 className="size-4" /> Create Once. Grow Automatically.
        </div>
      </div>

      {/* RIGHT — auth */}
      <div className="relative flex items-center justify-center bg-background px-6 py-12">
        <div className="absolute right-4 top-4">
          <ThemeToggle />
        </div>

        <div className="w-full max-w-sm animate-scale-in">
          {/* mobile brand (left panel hidden on small screens) */}
          <div className="mb-8 flex flex-col items-center text-center lg:hidden">
            <div className="bg-brand-gradient shadow-brand mb-3 flex size-12 items-center justify-center rounded-2xl text-brand-foreground">
              <Sparkles className="size-6" />
            </div>
            <h1 className="text-gradient-brand text-2xl font-bold tracking-tight">PostFlow</h1>
          </div>

          <div>
            <h2 className="text-2xl font-bold tracking-tight">로그인</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              synub 통합계정으로 로그인하세요. 하나의 계정으로 synub의 모든 서비스를 이용합니다.
            </p>
          </div>

          <form className="mt-7 space-y-3" onSubmit={onSubmit}>
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
            <Button type="submit" className="w-full gap-2" disabled={loading || !email.trim() || !password}>
              {loading && <Loader2 className="size-4 animate-spin" />} 로그인
            </Button>
          </form>

          {error && <p className="mt-3 text-center text-xs text-destructive">{error}</p>}

          <div className="mt-6 border-t pt-5">
            <Button variant="secondary" className="w-full" disabled={loading}
              onClick={() => doLogin(DEMO_LOGIN.email, DEMO_LOGIN.password)}>
              데모로 둘러보기
            </Button>
            <p className="mt-2 text-center text-[11px] text-muted-foreground">
              가입 없이 데모 계정으로 전체 기능을 체험할 수 있어요.
            </p>
          </div>

          <p className="mt-6 text-center text-xs text-muted-foreground">
            로그인 시{" "}
            <a href={LEGAL.terms} target="_blank" rel="noreferrer" className="underline hover:text-foreground">서비스 약관</a>
            과{" "}
            <a href={LEGAL.privacy} target="_blank" rel="noreferrer" className="underline hover:text-foreground">개인정보 처리방침</a>
            에 동의하게 됩니다.
          </p>
        </div>
      </div>
    </div>
  );
}
