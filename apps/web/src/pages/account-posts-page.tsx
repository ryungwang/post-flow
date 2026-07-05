import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { ChevronDown, Eye, ExternalLink, Heart, Loader2, MessageCircle, Repeat2, RefreshCw, Search, Send, Trash2 } from "lucide-react";
import { threadsApi, type ThreadsAccountPost } from "@/lib/threads-api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { AccountSelector } from "@/components/account-selector";
import { useConfirm } from "@/components/confirm-dialog";
import { useToast } from "@/components/toast";
import { useThreadsAccount } from "@/store/threads-account";
import { cn } from "@/lib/utils";

function Metrics({ p }: { p: ThreadsAccountPost }) {
  const items = [
    { icon: Heart, v: p.likes, color: "text-rose-500" },
    { icon: MessageCircle, v: p.replies, color: "text-sky-500" },
    { icon: Repeat2, v: (p.reposts ?? 0) + (p.quotes ?? 0), color: "text-emerald-500" },
    { icon: Send, v: p.shares, color: "text-violet-500" },
    { icon: Eye, v: p.views, color: "text-amber-500" },
  ];
  if (items.every((i) => i.v == null)) return null;
  return (
    <div className="flex flex-wrap items-center gap-5 text-sm">
      {items.map(({ icon: Icon, v, color }, i) => (
        <span key={i} className="inline-flex items-center gap-1.5 font-bold text-foreground">
          <Icon className={cn("size-5", color)} /> {(v ?? 0).toLocaleString()}
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
  const qc = useQueryClient();
  const confirm = useConfirm();
  const toast = useToast();
  const del = useMutation({
    mutationFn: () => threadsApi.deletePost(p.id),
    onSuccess: (r) => {
      if (r.deleted) {
        toast.show("게시물을 삭제했어요.", "success");
        qc.invalidateQueries({ queryKey: ["threads-account-posts"] });
      } else {
        toast.show("삭제에 실패했어요. threads_delete 권한 추가 + 재연결이 필요할 수 있어요.", "error");
      }
    },
    onError: () => toast.show("삭제 중 오류가 났어요. 잠시 후 다시 시도해 주세요.", "error"),
  });
  const onDelete = async () => {
    const ok = await confirm({
      title: "게시물 삭제",
      description: "이 게시물을 Threads에서 삭제할까요? 되돌릴 수 없어요.",
      confirmText: "삭제",
      destructive: true,
    });
    if (!ok) return;
    const id = toast.show("삭제 중…", "loading");
    del.mutate(undefined, { onSettled: () => toast.dismiss(id) });
  };
  return (
    <li
      className={cn(
        "flex items-start gap-3 rounded-xl border bg-card/40 p-4 pl-5 transition-colors hover:bg-accent/30",
        "relative overflow-hidden before:absolute before:inset-y-0 before:left-0 before:w-1",
        p.fromPostflow ? "before:bg-emerald-500" : "before:bg-border",
        del.isPending && "border-rose-500/40",
      )}
    >
      {/* 삭제 진행 오버레이 — 명확히 구분 */}
      {del.isPending && (
        <div className="absolute inset-0 z-10 flex items-center justify-center gap-2 rounded-xl bg-background/70 backdrop-blur-[1px]">
          <Loader2 className="size-4 animate-spin text-rose-500" />
          <span className="text-sm font-medium text-rose-500">삭제 중…</span>
        </div>
      )}
      {p.mediaUrl && p.mediaType !== "TEXT_POST" && (
        <img src={p.mediaUrl} alt="" className="size-14 shrink-0 rounded-md object-cover" />
      )}
      <div className="min-w-0 flex-1">
        <div className="mb-1.5 flex flex-wrap items-center gap-1.5">
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
            <button
              onClick={onDelete}
              disabled={del.isPending}
              title="Threads에서 삭제"
              className="inline-flex items-center text-xs text-muted-foreground transition-colors hover:text-red-500"
            >
              {del.isPending ? <Loader2 className="size-3.5 animate-spin" /> : <Trash2 className="size-3.5" />}
            </button>
          </div>
        </div>
        {openComments && <Replies mediaId={p.id} />}
      </div>
    </li>
  );
}

/** 작은 세그먼트 토글(필터/정렬용). */
function Segmented<T extends string>({ value, onChange, options }: { value: T; onChange: (v: T) => void; options: [T, string][] }) {
  return (
    <div className="flex rounded-lg border p-0.5 text-xs">
      {options.map(([k, label]) => (
        <button
          key={k}
          onClick={() => onChange(k)}
          className={cn(
            "rounded-md px-2.5 py-1.5 font-medium transition-colors whitespace-nowrap",
            value === k ? "bg-brand text-brand-foreground" : "text-muted-foreground hover:text-foreground",
          )}
        >
          {label}
        </button>
      ))}
    </div>
  );
}

/** 연결된 Threads 계정에 실제 올라간 게시물 목록 — 무한 스크롤 + PostFlow/외부 구분. */
export function AccountPostsPage() {
  const accountId = useThreadsAccount((s) => s.accountId);
  const { data, isLoading, isError, refetch, isFetching, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useInfiniteQuery({
      queryKey: ["threads-account-posts", accountId],
      queryFn: ({ pageParam }) => threadsApi.posts({ after: pageParam, limit: 10, accountId }),
      initialPageParam: undefined as string | undefined,
      getNextPageParam: (last) => last.nextCursor ?? undefined,
    });

  const [q, setQ] = useState("");
  const [source, setSource] = useState<"all" | "postflow" | "external">("all");
  const [sort, setSort] = useState<"recent" | "engagement" | "views">("recent");

  const loaded = data?.pages.flatMap((pg) => pg.posts) ?? [];
  const rate = (p: ThreadsAccountPost) => {
    const v = p.views ?? 0;
    return v > 0 ? ((p.likes ?? 0) + (p.replies ?? 0) + (p.reposts ?? 0) + (p.quotes ?? 0)) / v : 0;
  };
  const kw = q.trim().toLowerCase();
  const posts = loaded
    .filter((p) => source === "all" || (source === "postflow" ? p.fromPostflow : !p.fromPostflow))
    .filter((p) => !kw || (p.text ?? "").toLowerCase().includes(kw))
    .slice()
    .sort((a, b) =>
      sort === "recent" ? 0 : sort === "views" ? (b.views ?? 0) - (a.views ?? 0) : rate(b) - rate(a),
    );
  const filtering = kw !== "" || source !== "all" || sort !== "recent";

  // 스크롤 센티넬 — 화면에 들어오면 다음 페이지 로드.
  const sentinel = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    const el = sentinel.current;
    if (!el || !hasNextPage) return;
    const io = new IntersectionObserver(
      (entries) => { if (entries[0].isIntersecting && !isFetchingNextPage) fetchNextPage(); },
      { rootMargin: "300px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  return (
    <div className="mx-auto max-w-6xl p-6">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">내 Threads 게시물</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            연결된 Threads 계정에 실제 올라간 게시물이에요. PostFlow로 발행한 글과 외부에서 올린 글을 구분해 보여줘요.
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <AccountSelector />
          <Button variant="outline" size="sm" className="gap-1.5" onClick={() => refetch()} disabled={isFetching}>
            <RefreshCw className={`size-4 ${isFetching && !isFetchingNextPage ? "animate-spin" : ""}`} /> 새로고침
          </Button>
        </div>
      </div>

      {/* 필터 바 — 검색 · 출처 · 정렬 (로드된 게시물 기준) */}
      <div className="mt-5 flex flex-wrap items-center gap-2">
        <div className="relative min-w-[200px] flex-1">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="게시물 내용 검색…"
            className="h-9 w-full rounded-lg border bg-background pl-8 pr-3 text-sm outline-none focus:border-brand"
          />
        </div>
        <Segmented
          value={source}
          onChange={setSource}
          options={[["all", "전체"], ["postflow", "✨ PostFlow"], ["external", "외부"]]}
        />
        <Segmented
          value={sort}
          onChange={setSort}
          options={[["recent", "최신순"], ["engagement", "참여율순"], ["views", "조회순"]]}
        />
      </div>

      <div className="mt-4">
        {isLoading ? (
          <div className="flex items-center justify-center rounded-xl border bg-card/40 py-20">
            <Loader2 className="size-5 animate-spin text-muted-foreground" />
          </div>
        ) : isError ? (
          <p className="rounded-xl border bg-card/40 p-10 text-center text-sm text-muted-foreground">
            게시물을 불러오지 못했어요. Threads 계정 연결 상태를 확인해 주세요.
          </p>
        ) : posts.length === 0 ? (
          <p className="rounded-xl border bg-card/40 p-10 text-center text-sm text-muted-foreground">
            {filtering
              ? "조건에 맞는 게시물이 없어요. 필터를 바꾸거나 더 불러와 보세요."
              : "아직 게시물이 없어요. Threads에 글을 올리거나 PostFlow에서 발행해 보세요."}
          </p>
        ) : (
          <>
            {sort !== "recent" && hasNextPage && (
              <p className="mb-2 text-xs text-muted-foreground">
                ※ 정렬·필터는 <b>불러온 {loaded.length}개</b> 기준이에요. 아래 "더 보기"로 더 불러오면 반영돼요.
              </p>
            )}
            <ul className="flex flex-col gap-3">
              {posts.map((p) => (
                <PostRow key={p.id} p={p} />
              ))}
            </ul>
            <div ref={sentinel} className="flex justify-center py-6">
              {isFetchingNextPage ? (
                <Loader2 className="size-5 animate-spin text-muted-foreground" />
              ) : hasNextPage ? (
                <Button variant="ghost" size="sm" onClick={() => fetchNextPage()}>더 보기</Button>
              ) : (
                <span className="text-xs text-muted-foreground">
                  {filtering ? `조건에 맞는 ${posts.length}개 · 마지막이에요` : "마지막 게시물이에요"}
                </span>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
