import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { BarChart3, CalendarClock, MessageSquareReply, Sparkles, TrendingUp, Wand2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ThemeToggle } from "@/components/theme-toggle";
import { GoogleSignInButton } from "@/auth/google-sign-in-button";
import { authApi } from "@/lib/auth-api";
import { LEGAL } from "@/lib/legal";
import { useAuth } from "@/store/auth";

export function LoginPage() {
  const token = useAuth((s) => s.token);
  const setAuth = useAuth((s) => s.setAuth);
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  if (token) {
    const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname;
    return <Navigate to={from ?? "/"} replace />;
  }

  const handleCredential = async (idToken: string) => {
    setLoading(true);
    setError(null);
    try {
      const { token: jwt, user } = await authApi.loginWithGoogle(idToken);
      setAuth(jwt, user);
      navigate("/", { replace: true });
    } catch {
      setError("로그인에 실패했어요. 다시 시도해 주세요.");
    } finally {
      setLoading(false);
    }
  };

  const devLogin = async () => {
    setLoading(true);
    setError(null);
    try {
      const { token: jwt, user } = await authApi.devLogin();
      setAuth(jwt, user);
      navigate("/", { replace: true });
    } catch {
      setError("개발용 로그인에 실패했어요. 백엔드(local) 실행을 확인하세요.");
    } finally {
      setLoading(false);
    }
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
            <h2 className="text-2xl font-bold tracking-tight">로그인 / 회원가입</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Google 계정으로 1초 만에 시작하세요.<br />처음이면 자동으로 가입돼요 — 별도 절차가 없습니다.
            </p>
          </div>

          <div className="mt-7">
            <GoogleSignInButton onCredential={handleCredential} />
          </div>
          <p className="mt-2 text-center text-xs text-muted-foreground">가입·로그인이 하나로 처리됩니다.</p>

          {loading && <p className="mt-3 text-center text-xs text-muted-foreground">로그인 중…</p>}
          {error && <p className="mt-3 text-center text-xs text-destructive">{error}</p>}

          <p className="mt-6 text-center text-xs text-muted-foreground">
            로그인 시{" "}
            <a href={LEGAL.terms} target="_blank" rel="noreferrer" className="underline hover:text-foreground">서비스 약관</a>
            과{" "}
            <a href={LEGAL.privacy} target="_blank" rel="noreferrer" className="underline hover:text-foreground">개인정보 처리방침</a>
            에 동의하게 됩니다.
          </p>

          {import.meta.env.DEV && (
            <div className="mt-6 border-t pt-5">
              <Button variant="secondary" className="w-full" onClick={devLogin} disabled={loading}>
                개발용 로그인 (로컬)
              </Button>
              <p className="mt-2 text-center text-[11px] text-muted-foreground">
                키 없이 화면을 둘러보기 위한 로컬 전용 로그인입니다.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
