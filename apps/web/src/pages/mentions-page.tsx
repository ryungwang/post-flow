import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { AtSign, ExternalLink, Loader2, Search } from "lucide-react";
import { threadsApi } from "@/lib/threads-api";
import { AccountSelector } from "@/components/account-selector";
import { useThreadsAccount } from "@/store/threads-account";

/** 나를 멘션한 게시물 인박스 — 응대 기회를 놓치지 않게. */
export function MentionsPage() {
  const accountId = useThreadsAccount((s) => s.accountId);
  const [q, setQ] = useState("");
  const { data, isLoading } = useQuery({
    queryKey: ["threads-mentions", accountId],
    queryFn: () => threadsApi.mentions(accountId),
  });
  const kw = q.trim().toLowerCase();
  const mentions = (data?.posts ?? []).filter(
    (m) => !kw || (m.text ?? "").toLowerCase().includes(kw) || (m.username ?? "").toLowerCase().includes(kw),
  );

  return (
    <div className="mx-auto max-w-6xl p-6">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">멘션</h1>
          <p className="mt-1 text-sm text-muted-foreground">나를 언급한 게시물이에요. 빠르게 응대하면 참여가 올라가요.</p>
        </div>
        <div className="shrink-0"><AccountSelector /></div>
      </div>

      {/* 검색 — 멘션 있을 때만 */}
      {data?.available && (data.posts.length > 0) && (
        <div className="relative mt-5 max-w-sm">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="멘션 내용·아이디 검색…"
            className="h-9 w-full rounded-lg border bg-background pl-8 pr-3 text-sm outline-none focus:border-brand"
          />
        </div>
      )}

      <div className="mt-4 rounded-xl border bg-card/40">
        {isLoading ? (
          <div className="flex justify-center py-16"><Loader2 className="size-5 animate-spin text-muted-foreground" /></div>
        ) : data && !data.available ? (
          <p className="p-8 text-center text-sm text-amber-600 dark:text-amber-400">
            멘션 조회는 threads_manage_mentions 권한 추가 + 재연결 후 이용할 수 있어요.
          </p>
        ) : !data || data.posts.length === 0 ? (
          <p className="p-8 text-center text-sm text-muted-foreground">아직 멘션이 없어요.</p>
        ) : mentions.length === 0 ? (
          <p className="p-8 text-center text-sm text-muted-foreground">검색 결과가 없어요.</p>
        ) : (
          <ul className="divide-y divide-border/60">
            {mentions.map((m) => (
              <li key={m.id} className="p-4">
                <div className="mb-1 flex items-center gap-1.5 text-sm">
                  <AtSign className="size-3.5 text-brand" />
                  <span className="font-medium">{m.username ?? "누군가"}</span>
                  {m.timestamp && (
                    <span className="text-xs text-muted-foreground">· {new Date(m.timestamp).toLocaleString("ko-KR")}</span>
                  )}
                </div>
                <p className="whitespace-pre-wrap break-words text-sm text-foreground/90">{m.text || "(텍스트 없음)"}</p>
                {m.permalink && (
                  <a href={m.permalink} target="_blank" rel="noreferrer"
                    className="mt-1.5 inline-flex items-center gap-1 text-xs text-brand hover:underline">
                    Threads에서 응답 <ExternalLink className="size-3" />
                  </a>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
