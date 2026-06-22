import { Check, LogOut, Monitor, Moon, Sun } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/store/auth";
import { useTheme } from "@/components/theme-provider";
import { cn } from "@/lib/utils";

const PLANS = [
  { key: "FREE", name: "Free", price: "₩0", features: ["월 30회 생성"] },
  { key: "STARTER", name: "Starter", price: "₩9,900", features: ["월 300회 생성", "예약 발행"] },
  { key: "PRO", name: "Pro", price: "₩29,000", features: ["무제한 생성", "분석", "시리즈 생성"] },
  { key: "BUSINESS", name: "Business", price: "₩49,000", features: ["다중 계정", "우선 지원"] },
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
    window.google?.accounts.id.disableAutoSelect();
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
        {/* Profile */}
        <Card className="lg:col-span-1">
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
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                {PLANS.map((p) => {
                  const active = p.key === currentPlan;
                  return (
                    <div
                      key={p.key}
                      className={cn(
                        "rounded-xl border p-4 transition-all",
                        active ? "border-brand/50 bg-brand/5 shadow-brand" : "hover:border-brand/30",
                      )}
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-semibold">{p.name}</span>
                        {active && <Check className="size-4 text-brand" />}
                      </div>
                      <div className="mt-1 text-lg font-bold tabular-nums">{p.price}</div>
                      <ul className="mt-3 space-y-1">
                        {p.features.map((f) => (
                          <li key={f} className="flex items-center gap-1.5 text-xs text-muted-foreground">
                            <Check className="size-3 text-brand" /> {f}
                          </li>
                        ))}
                      </ul>
                      <Button
                        variant={active ? "secondary" : "default"}
                        size="sm"
                        className="mt-4 w-full"
                        disabled={active}
                      >
                        {active ? "사용 중" : "업그레이드"}
                      </Button>
                    </div>
                  );
                })}
              </div>
              <p className="mt-3 text-xs text-muted-foreground">결제 연동은 준비 중입니다.</p>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
