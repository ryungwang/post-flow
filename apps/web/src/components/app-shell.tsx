import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { LogOut, Plus, Search, Sparkles } from "lucide-react";
import { AppSidebar } from "@/components/app-sidebar";
import { ThemeToggle } from "@/components/theme-toggle";
import { NotificationBell } from "@/components/notification-bell";
import { DemoBanner } from "@/components/demo-banner";
import { CommandPalette } from "@/components/command-palette";
import { ErrorBoundary } from "@/components/error-boundary";
import { Button } from "@/components/ui/button";
import { BILLING_WEB_URL } from "@/lib/billing-web";
import { useAuth } from "@/store/auth";

function initialOf(name?: string | null) {
  return name?.trim()?.charAt(0)?.toUpperCase() || "U";
}

export function AppShell() {
  const user = useAuth((s) => s.user);
  const clear = useAuth((s) => s.clear);
  const navigate = useNavigate();
  const location = useLocation();

  const logout = () => {
    clear();
    navigate("/login", { replace: true });
  };

  return (
    <div className="flex h-screen flex-col overflow-hidden bg-background text-foreground">
      <CommandPalette />
      {/* 맨 위 풀폭 데모 배너(office 방식) */}
      <DemoBanner />
      {/* 풀폭 헤더 — 로고 좌상단 + 검색 + 유저 */}
      <header className="z-20 flex h-14 shrink-0 items-center gap-3 border-b border-border/60 bg-background/70 px-4 backdrop-blur-xl sm:px-6">
        <button
          onClick={() => navigate("/")}
          className="flex items-center gap-2"
          aria-label="PostFlow 홈"
        >
          <span className="bg-brand-gradient shadow-brand flex size-7 items-center justify-center rounded-md text-brand-foreground">
            <Sparkles className="size-4" />
          </span>
          <span className="text-gradient-brand text-base font-bold tracking-tight">PostFlow</span>
        </button>
        <div className="mx-1 hidden h-5 w-px bg-border/60 sm:block" />
        <button
          onClick={() => window.dispatchEvent(new KeyboardEvent("keydown", { key: "k", metaKey: true }))}
          className="hidden items-center gap-2 rounded-lg border border-border/60 px-3 py-1.5 text-xs text-muted-foreground transition-colors hover:bg-accent sm:flex"
        >
          <Search className="size-3.5" /> 검색·이동
          <kbd className="rounded bg-muted px-1.5 py-0.5 text-[10px]">⌘K</kbd>
        </button>
        <div className="flex-1" />
        <Button size="sm" className="gap-1.5" onClick={() => navigate("/content/generate")}>
          <Plus className="size-4" />새 콘텐츠
        </Button>
        <NotificationBell />
        <ThemeToggle />
        <div className="flex items-center gap-2 pl-1">
          <div
            className="bg-brand-gradient text-brand-foreground flex size-8 items-center justify-center overflow-hidden rounded-full text-sm font-semibold"
            title={user?.email ?? undefined}
          >
            {user?.profileImage ? (
              <img src={user.profileImage} alt="" className="size-full object-cover" />
            ) : (
              initialOf(user?.name)
            )}
          </div>
          {user?.demo ? (
            <Button size="sm" className="gap-1.5" onClick={() => window.open(`${BILLING_WEB_URL}/products`, "_blank")}>
              <Sparkles className="size-4" />구독하러가기
            </Button>
          ) : (
            <Button variant="ghost" size="icon" aria-label="로그아웃" title="로그아웃" onClick={logout}>
              <LogOut />
            </Button>
          )}
        </div>
      </header>
      {/* 헤더 아래: 사이드바(메뉴) + 콘텐츠 */}
      <div className="flex min-h-0 flex-1">
        <AppSidebar />
        <main className="app-ambient relative z-10 min-w-0 flex-1 overflow-y-auto">
          <ErrorBoundary>
            <div key={location.pathname} className="animate-page-in">
              <Outlet />
            </div>
          </ErrorBoundary>
        </main>
      </div>
    </div>
  );
}
