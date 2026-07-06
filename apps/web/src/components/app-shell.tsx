import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { LogOut, Plus, Search, Sparkles } from "lucide-react";
import { AppSidebar, MobileSidebar, MobileMenuButton } from "@/components/app-sidebar";
import { SidebarProvider } from "@/components/sidebar-context";
import { ThemeToggle } from "@/components/theme-toggle";
import { NotificationBell } from "@/components/notification-bell";
import { ContextSwitcher } from "@/components/context-switcher";
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
    <SidebarProvider>
      <div className="flex h-screen flex-col overflow-hidden bg-background text-foreground">
        <CommandPalette />
        {/* 맨 위 풀폭 데모 배너(office 방식) */}
        <DemoBanner />
        {/* 풀폭 헤더 — 햄버거(모바일) + 로고 + 검색 + 유저 */}
        <header className="z-20 flex h-14 shrink-0 items-center gap-2 border-b border-border/60 bg-background/70 px-3 backdrop-blur-xl sm:gap-3 sm:px-6">
          <MobileMenuButton />
          <button
            onClick={() => navigate("/")}
            className="flex items-center gap-2"
            aria-label="PostFlow 홈"
          >
            <img src="/icon-192.png" alt="" className="size-7 rounded-[7px]" />
            <span className="text-gradient-brand hidden text-base font-bold tracking-tight min-[400px]:inline">PostFlow</span>
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
          {/* 모바일: 아이콘만 / 데스크톱: 텍스트 포함 */}
          <Button size="sm" className="gap-1.5 px-2.5 sm:px-3" onClick={() => navigate("/content/generate")}>
            <Plus className="size-4" />
            <span className="hidden sm:inline">새 콘텐츠</span>
          </Button>
          <ContextSwitcher />
          <NotificationBell />
          <div className="hidden sm:block">
            <ThemeToggle />
          </div>
          <div className="flex items-center gap-2 sm:pl-1">
            <div
              className="bg-brand-gradient text-brand-foreground flex size-8 shrink-0 items-center justify-center overflow-hidden rounded-full text-sm font-semibold"
              title={user?.email ?? undefined}
            >
              {user?.profileImage ? (
                <img src={user.profileImage} alt="" className="size-full object-cover" />
              ) : (
                initialOf(user?.name)
              )}
            </div>
            {user?.demo && (
              <Button size="sm" className="gap-1.5 px-2.5 sm:px-3" onClick={() => window.open(`${BILLING_WEB_URL}/products`, "_blank")}>
                <Sparkles className="size-4" />
                <span className="hidden sm:inline">구독하러가기</span>
              </Button>
            )}
            {/* 데모든 실계정이든 항상 나가기(로그아웃) 제공 — 데모에서 못 빠져나오는 문제 방지. */}
            <Button variant="ghost" size="icon" aria-label={user?.demo ? "데모 나가기" : "로그아웃"}
              title={user?.demo ? "데모 나가기" : "로그아웃"} onClick={logout}>
              <LogOut />
            </Button>
          </div>
        </header>
        {/* 헤더 아래: 사이드바(데스크톱) + 모바일 드로어 + 콘텐츠 */}
        <div className="flex min-h-0 flex-1">
          <AppSidebar />
          <MobileSidebar />
          <main className="app-ambient relative z-10 min-w-0 flex-1 overflow-y-auto overflow-x-hidden">
            <ErrorBoundary>
              <div key={location.pathname} className="animate-page-in">
                <Outlet />
              </div>
            </ErrorBoundary>
          </main>
        </div>
      </div>
    </SidebarProvider>
  );
}
