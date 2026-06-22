import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ThemeToggle } from "@/components/theme-toggle";
import { GoogleSignInButton } from "@/auth/google-sign-in-button";
import { authApi } from "@/lib/auth-api";
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

  return (
    <div className="relative flex min-h-screen items-center justify-center bg-background px-4">
      <div className="absolute right-4 top-4">
        <ThemeToggle />
      </div>

      <div className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center text-center">
          <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <Sparkles className="size-6" />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight">PostFlow</h1>
          <p className="mt-1.5 text-sm text-muted-foreground">
            Create Once. Grow Automatically.
          </p>
        </div>

        <div className="rounded-xl border bg-card p-6 shadow-sm">
          <h2 className="text-lg font-semibold">시작하기</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Google 계정으로 로그인하면 바로 콘텐츠를 만들 수 있어요.
          </p>

          <div className="mt-5">
            <GoogleSignInButton onCredential={handleCredential} />
          </div>

          {loading && (
            <p className="mt-3 text-center text-xs text-muted-foreground">로그인 중…</p>
          )}
          {error && (
            <p className="mt-3 text-center text-xs text-destructive">{error}</p>
          )}

          <p className="mt-4 text-center text-xs text-muted-foreground">
            로그인 시 서비스 약관과 개인정보 처리방침에 동의하게 됩니다.
          </p>

          {import.meta.env.DEV && (
            <div className="mt-4 border-t pt-4">
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
