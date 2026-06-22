import { Outlet, useNavigate } from "react-router-dom";
import { LogOut, Plus, Search } from "lucide-react";
import { AppSidebar } from "@/components/app-sidebar";
import { ThemeToggle } from "@/components/theme-toggle";
import { NotificationBell } from "@/components/notification-bell";
import { CommandPalette } from "@/components/command-palette";
import { ErrorBoundary } from "@/components/error-boundary";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/store/auth";

function initialOf(name?: string | null) {
  return name?.trim()?.charAt(0)?.toUpperCase() || "U";
}

export function AppShell() {
  const user = useAuth((s) => s.user);
  const clear = useAuth((s) => s.clear);
  const navigate = useNavigate();

  const logout = () => {
    window.google?.accounts.id.disableAutoSelect();
    clear();
    navigate("/login", { replace: true });
  };

  return (
    <div className="flex h-screen overflow-hidden bg-background text-foreground">
      <AppSidebar />
      <CommandPalette />
      <div className="app-ambient flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-border/60 bg-background/70 px-6 backdrop-blur-xl">
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
            <Button variant="ghost" size="icon" aria-label="로그아웃" title="로그아웃" onClick={logout}>
              <LogOut />
            </Button>
          </div>
        </header>
        <main className="relative z-10 flex-1 overflow-y-auto">
          <ErrorBoundary>
            <Outlet />
          </ErrorBoundary>
        </main>
      </div>
    </div>
  );
}
