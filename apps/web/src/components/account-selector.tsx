import { useQuery } from "@tanstack/react-query";
import { Check, ChevronDown } from "lucide-react";
import { useState } from "react";
import { threadsApi } from "@/lib/threads-api";
import { useThreadsAccount } from "@/store/threads-account";
import { cn } from "@/lib/utils";

/**
 * 다계정용 Threads 계정 선택기. 계정이 2개 이상일 때만 노출.
 * 선택은 전역 store(useThreadsAccount)에 저장 — 내 게시물·인사이트·멘션이 공유.
 */
export function AccountSelector() {
  const { data: accounts } = useQuery({ queryKey: ["threads-accounts"], queryFn: threadsApi.accounts });
  const { accountId, setAccountId } = useThreadsAccount();
  const [open, setOpen] = useState(false);

  if (!accounts || accounts.length <= 1) return null;

  const current = accounts.find((a) => a.id === accountId) ?? accounts.find((a) => a.isDefault) ?? accounts[0];

  return (
    <div className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-2 rounded-lg border border-border/60 px-3 py-1.5 text-sm font-medium transition-colors hover:bg-accent"
      >
        {current.profilePictureUrl ? (
          <img src={current.profilePictureUrl} alt="" className="size-5 rounded-full object-cover" />
        ) : (
          <span className="flex size-5 items-center justify-center rounded-full bg-brand/15 text-[10px] font-bold text-brand">
            {current.username.charAt(0).toUpperCase()}
          </span>
        )}
        <span className="max-w-[10rem] truncate">@{current.username}</span>
        <ChevronDown className="size-4 opacity-60" />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute left-0 z-20 mt-1 w-56 overflow-hidden rounded-lg border bg-popover p-1 shadow-lg">
            {accounts.map((a) => (
              <button
                key={a.id}
                onClick={() => { setAccountId(a.id); setOpen(false); }}
                className={cn(
                  "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm transition-colors hover:bg-accent",
                  a.id === current.id && "bg-accent/60",
                )}
              >
                {a.profilePictureUrl ? (
                  <img src={a.profilePictureUrl} alt="" className="size-6 shrink-0 rounded-full object-cover" />
                ) : (
                  <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-brand/15 text-xs font-bold text-brand">
                    {a.username.charAt(0).toUpperCase()}
                  </span>
                )}
                <span className="min-w-0 flex-1 truncate">
                  @{a.username}
                  {a.isDefault && <span className="ml-1 text-[11px] text-muted-foreground">· 기본</span>}
                </span>
                {a.id === current.id && <Check className="size-4 shrink-0 text-brand" />}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
