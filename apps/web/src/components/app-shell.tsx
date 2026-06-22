import { Outlet, useNavigate } from "react-router-dom";
import { LogOut, Plus } from "lucide-react";
import { AppSidebar } from "@/components/app-sidebar";
import { ThemeToggle } from "@/components/theme-toggle";
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
      <div className="app-ambient flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-border/60 bg-background/70 px-6 backdrop-blur-xl">
          <div className="flex-1" />
          <Button size="sm" className="gap-1.5">
            <Plus className="size-4" />새 콘텐츠
          </Button>
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
          <Outlet />
        </main>
      </div>
    </div>
  );
}
