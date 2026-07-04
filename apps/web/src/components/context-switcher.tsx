import { useQueryClient } from "@tanstack/react-query";
import { Building2, Check, ChevronDown, User as UserIcon } from "lucide-react";
import { useState } from "react";
import { authApi } from "@/lib/auth-api";
import { useAuth } from "@/store/auth";
import { cn } from "@/lib/utils";

/**
 * 개인/회사 컨텍스트 스위처(빌링 컨텍스트 인지 entitlement). 컨텍스트가 2개 이상일 때만 노출.
 * 전환 시 선택 저장 → /auth/me 재조회(선택 컨텍스트로 플랜 판정) → 전체 쿼리 무효화.
 */
export function ContextSwitcher() {
  const contexts = useAuth((s) => s.contexts);
  const context = useAuth((s) => s.context);
  const setContext = useAuth((s) => s.setContext);
  const setAuth = useAuth((s) => s.setAuth);
  const token = useAuth((s) => s.token);
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  if (contexts.length <= 1) return null;

  const current = contexts.find((c) => c.context === context) ?? contexts[0];

  const pick = async (ctx: string) => {
    setOpen(false);
    if (ctx === context) return;
    setContext(ctx);
    if (token) {
      try {
        const u = await authApi.me(ctx); // 선택 컨텍스트로 플랜 재판정
        setAuth(token, u);
      } catch {
        /* keep prior */
      }
    }
    qc.invalidateQueries(); // 컨텍스트 바뀌면 데이터·usage 다시
  };

  return (
    <div className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-1.5 rounded-lg border border-border/60 px-2.5 py-1.5 text-xs font-medium transition-colors hover:bg-accent"
      >
        {current.type === "org" ? <Building2 className="size-3.5" /> : <UserIcon className="size-3.5" />}
        <span className="max-w-[8rem] truncate">{current.name}</span>
        <ChevronDown className="size-3.5 opacity-60" />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 z-20 mt-1 w-52 overflow-hidden rounded-lg border bg-popover p-1 shadow-lg">
            <p className="px-2 py-1.5 text-[11px] font-medium text-muted-foreground">컨텍스트 전환</p>
            {contexts.map((c) => (
              <button
                key={c.context}
                onClick={() => pick(c.context)}
                className={cn(
                  "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm transition-colors hover:bg-accent",
                  c.context === context && "bg-accent/60",
                )}
              >
                {c.type === "org" ? <Building2 className="size-4 shrink-0" /> : <UserIcon className="size-4 shrink-0" />}
                <span className="min-w-0 flex-1 truncate">
                  {c.name}
                  {c.role && <span className="ml-1 text-[11px] text-muted-foreground">· {c.role}</span>}
                </span>
                {c.context === context && <Check className="size-4 shrink-0 text-brand" />}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
