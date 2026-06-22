import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { AlertTriangle, Bell, CheckCircle2 } from "lucide-react";
import { notificationsApi } from "@/lib/notifications-api";
import { cn } from "@/lib/utils";

export function NotificationBell() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const { data } = useQuery({
    queryKey: ["notifications"],
    queryFn: notificationsApi.list,
    refetchInterval: 60_000,
  });
  const items = data ?? [];

  return (
    <div className="relative">
      <button
        aria-label="알림"
        onClick={() => setOpen((v) => !v)}
        className="relative flex size-9 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
      >
        <Bell className="size-5" />
        {items.length > 0 && (
          <span className="absolute right-1.5 top-1.5 flex size-2 rounded-full bg-rose-500 ring-2 ring-background" />
        )}
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />
          <div className="card-soft absolute right-0 top-11 z-40 w-80 rounded-xl border border-border/60 bg-card p-2 shadow-xl">
            <div className="px-2 py-1.5 text-sm font-semibold">알림</div>
            {items.length === 0 ? (
              <div className="flex flex-col items-center gap-2 px-2 py-8 text-center text-sm text-muted-foreground">
                <CheckCircle2 className="size-6 text-emerald-500" />
                새 알림이 없어요.
              </div>
            ) : (
              <ul className="max-h-80 space-y-0.5 overflow-y-auto">
                {items.map((n, i) => (
                  <li key={i}>
                    <button
                      onClick={() => { setOpen(false); navigate(n.link); }}
                      className="flex w-full items-start gap-2 rounded-lg px-2 py-2 text-left text-sm transition-colors hover:bg-accent"
                    >
                      <AlertTriangle className={cn("mt-0.5 size-4 shrink-0", n.severity === "error" ? "text-rose-500" : "text-amber-500")} />
                      <span className="flex-1">{n.message}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}
    </div>
  );
}
