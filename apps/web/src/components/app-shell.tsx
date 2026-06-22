import { Outlet } from "react-router-dom";
import { AppSidebar } from "@/components/app-sidebar";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { Plus } from "lucide-react";

export function AppShell() {
  return (
    <div className="flex h-screen overflow-hidden bg-background text-foreground">
      <AppSidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 items-center gap-3 border-b px-6">
          <div className="flex-1" />
          <Button size="sm" className="gap-1.5">
            <Plus className="size-4" />새 콘텐츠
          </Button>
          <ThemeToggle />
          <div className="ml-1 flex size-8 items-center justify-center rounded-full bg-secondary text-sm font-medium text-secondary-foreground">
            R
          </div>
        </header>
        <main className="flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
