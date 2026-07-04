import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { ChevronDown, Eye, ExternalLink, Heart, Loader2, MessageCircle, Repeat2, RefreshCw, Send } from "lucide-react";
import { threadsApi, type ThreadsAccountPost } from "@/lib/threads-api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

function Metrics({ p }: { p: ThreadsAccountPost }) {
  const items = [
    { icon: Heart, v: p.likes },
    { icon: MessageCircle, v: p.replies },
    { icon: Repeat2, v: (p.reposts ?? 0) + (p.quotes ?? 0) },
    { icon: Send, v: p.shares },
    { icon: Eye, v: p.views },
  ];
  if (items.every((i) => i.v == null)) return null;
  return (
    <div className="flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
      {items.map(({ icon: Icon, v }, i) => (
        <span key={i} className="inline-flex items-center gap-1">
          <Icon className="size-3.5" /> {(v ?? 0).toLocaleString()}
        </span>
      ))}
    </div>
  );
}

/** 게시물 댓글 뷰어 — 펼칠 때만 조회(lazy). */
function Replies({ mediaId }: { mediaId: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ["threads-replies", mediaId],
    queryFn: () => threadsApi.replies(mediaId),
  });
  if (isLoading) return <div className="py-3 text-center text-xs text-muted-foreground"><Loader2 className="mx-auto size-4 animate-spin" /></div>;
  // 앱이 threads_manage_replies 검수 미승인 → 조회 불가.
  if (data && !data.available)
    return (
      <p className="mt-2 rounded-md border border-amber-500/30 bg-amber-500/5 px-3 py-2 text-xs text-amber-600 dark:text-amber-400">
        댓글 조회는 Threads 앱 검수(threads_manage_replies) 승인 후 이용할 수 있어요.
      </p>
    );
  const replies = data?.replies ?? [];
  if (replies.length === 0) return <p className="py-3 text-center text-xs text-muted-foreground">아직 댓글이 없어요.</p>;
  return (
    <ul className="mt-2 space-y-2 border-l-2 border-border/60 pl-3">
      {replies.map((r) => (
        <li key={r.id} className="text-sm">
          {r.username && <span className="mr-1.5 font-medium text-foreground">@{r.username}</span>}
          <span className="text-foreground/80">{r.text}</span>
        </li>
      ))}
    </ul>
  );
}

function PostRow({ p }: { p: ThreadsAccountPost }) {
  const [openComments, setOpenComments] = useState(false);
  const hasReplies = (p.replies ?? 0) > 0;
  return (
    <li className="flex items-start gap-3 p-4">
      {p.mediaUrl && p.mediaType !== "TEXT_POST" && (
        <img src={p.mediaUrl} alt="" className="size-14 shrink-0 rounded-md object-cover" />
      )}
      <div className="min-w-0 flex-1">
        <div className="mb-1 flex flex-wrap items-center gap-1.5">
          <Badge variant={p.fromPostflow ? "success" : "secondary"}>
            {p.fromPostflow ? "✨ PostFlow 발행" : "외부 게시"}
          </Badge>
          {p.mediaType && p.mediaType !== "TEXT_POST" && <Badge variant="secondary">{p.mediaType}</Badge>}
          {p.timestamp && (
            <span className="text-xs text-muted-foreground">{new Date(p.timestamp).toLocaleString("ko-KR")}</span>
          )}
        </div>
        <p className="whitespace-pre-wrap break-words text-sm text-foreground/90">
          {p.text || <span className="text-muted-foreground">(텍스트 없음)</span>}
        </p>
        <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
          <Metrics p={p} />
          <div className="ml-auto flex items-center gap-3">
            {hasReplies && (
              <button
                onClick={() => setOpenComments((o) => !o)}
                className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
              >
                댓글 {p.replies}개 <ChevronDown className={cn("size-3.5 transition-transform", openComments && "rotate-180")} />
              </button>
            )}
            {p.permalink && (
              <a href={p.permalink} target="_blank" rel="noreferrer"
                className="inline-flex items-center gap-1 text-xs text-brand hover:underline">
                Threads에서 보기 <ExternalLink className="size-3" />
              </a>
            )}
          </div>
        </div>
        {openComments && <Replies mediaId={p.id} />}
      </div>
    </li>
  );
}

/** 연결된 Threads 계정에 실제 올라간 게시물 목록 — PostFlow 발행 / 외부 구분 표시. */
export function AccountPostsPage() {
  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ["threads-account-posts"],
    queryFn: () => threadsApi.posts(),
  });

  return (
    <div className="w-full p-6">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">내 Threads 게시물</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            연결된 Threads 계정에 실제 올라간 게시물이에요. PostFlow로 발행한 글과 외부에서 올린 글을 구분해 보여줘요.
          </p>
        </div>
        <Button variant="outline" size="sm" className="gap-1.5 shrink-0" onClick={() => refetch()} disabled={isFetching}>
          <RefreshCw className={`size-4 ${isFetching ? "animate-spin" : ""}`} /> 새로고침
        </Button>
      </div>

      <div className="mt-6 rounded-xl border bg-card/40">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="size-5 animate-spin text-muted-foreground" />
          </div>
        ) : isError ? (
          <p className="p-10 text-center text-sm text-muted-foreground">
            게시물을 불러오지 못했어요. Threads 계정 연결 상태를 확인해 주세요.
          </p>
        ) : !data || data.length === 0 ? (
          <p className="p-10 text-center text-sm text-muted-foreground">
            아직 게시물이 없어요. Threads에 글을 올리거나 PostFlow에서 발행해 보세요.
          </p>
        ) : (
          <ul className="divide-y divide-border/60">
            {data.map((p) => (
              <PostRow key={p.id} p={p} />
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
