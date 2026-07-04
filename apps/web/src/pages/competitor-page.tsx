import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { BadgeCheck, Eye, Heart, Loader2, Repeat2, Search, Users } from "lucide-react";
import { threadsApi, type ProfileLookup } from "@/lib/threads-api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

const nf = (n: number | null | undefined) => (n ?? 0).toLocaleString();

/** 경쟁사/인플루언서 공개 프로필 분석 — @username 입력 → 프로필 + 최근 7일 성과. */
export function CompetitorPage() {
  const [username, setUsername] = useState("");
  const [notFound, setNotFound] = useState(false);
  const { mutate, data, isPending } = useMutation({
    mutationFn: (u: string) => threadsApi.profileLookup(u),
    onSuccess: (d) => setNotFound(!d),
  });

  const search = () => {
    const u = username.trim();
    if (!u) return;
    setNotFound(false);
    mutate(u);
  };

  const p: ProfileLookup | null | undefined = data;
  const EXAMPLES = ["zuck", "mosseri", "threads", "meta"];
  const pick = (u: string) => { setUsername(u); setNotFound(false); mutate(u); };

  return (
    <div className="mx-auto max-w-6xl p-6">
      <h1 className="text-2xl font-bold tracking-tight">경쟁사 분석</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        벤치마킹할 계정의 @아이디를 넣으면 팔로워·최근 7일 성과를 보여줘요. 잘 되는 계정을 참고해 전략을 세우세요.
      </p>

      <div className="mt-5 flex gap-2">
        <Input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && search()}
          placeholder="@username (예: zuck)"
        />
        <Button onClick={search} disabled={isPending || !username.trim()} className="shrink-0 gap-1.5">
          {isPending ? <Loader2 className="size-4 animate-spin" /> : <Search className="size-4" />} 분석
        </Button>
      </div>

      {notFound && (
        <p className="mt-4 rounded-md border border-amber-500/30 bg-amber-500/5 px-3 py-2.5 text-sm text-amber-600 dark:text-amber-400">
          프로필을 못 찾았어요. 공개 계정 + 팔로워 100명 이상만 조회돼요. (threads_profile_discovery 권한·재연결 필요)
        </p>
      )}

      {/* 검색 전 빈 상태 — 예시 계정으로 바로 체험 */}
      {!p && !isPending && !notFound && (
        <div className="mt-8 flex flex-col items-center gap-4 rounded-xl border border-dashed bg-card/30 py-14 text-center">
          <div className="flex size-14 items-center justify-center rounded-full bg-brand/10">
            <Search className="size-7 text-brand" />
          </div>
          <div>
            <p className="font-medium">분석할 계정을 검색해 보세요</p>
            <p className="mt-1 text-sm text-muted-foreground">팔로워 · 조회수 · 좋아요 · 리포스트(최근 7일)를 한눈에.</p>
          </div>
          <div className="flex flex-wrap justify-center gap-2">
            <span className="self-center text-xs text-muted-foreground">예시:</span>
            {EXAMPLES.map((u) => (
              <button
                key={u}
                onClick={() => pick(u)}
                className="rounded-full border bg-background px-3 py-1 text-sm transition-colors hover:bg-accent"
              >
                @{u}
              </button>
            ))}
          </div>
        </div>
      )}

      {p && (
        <div className="mt-5 rounded-xl border bg-card/40 p-5">
          <div className="flex items-center gap-4">
            {p.profilePictureUrl ? (
              <img src={p.profilePictureUrl} alt="" className="size-16 rounded-full object-cover" />
            ) : (
              <div className="flex size-16 items-center justify-center rounded-full bg-brand/15 text-xl font-bold text-brand">
                {p.username.charAt(0).toUpperCase()}
              </div>
            )}
            <div className="min-w-0">
              <div className="flex items-center gap-1.5">
                <span className="text-lg font-bold">{p.name || p.username}</span>
                {p.isVerified && <BadgeCheck className="size-4 text-brand" />}
              </div>
              <div className="text-sm text-muted-foreground">@{p.username}</div>
            </div>
          </div>
          {p.biography && <p className="mt-3 whitespace-pre-wrap text-sm text-foreground/80">{p.biography}</p>}

          <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Stat icon={Users} label="팔로워" value={nf(p.followerCount)} />
            <Stat icon={Eye} label="조회(7일)" value={nf(p.viewsCount)} />
            <Stat icon={Heart} label="좋아요(7일)" value={nf(p.likesCount)} />
            <Stat icon={Repeat2} label="리포스트(7일)" value={nf(p.repostsCount)} />
          </div>
        </div>
      )}
    </div>
  );
}

function Stat({ icon: Icon, label, value }: { icon: React.ComponentType<{ className?: string }>; label: string; value: string }) {
  return (
    <div className="rounded-lg border bg-background/40 p-3">
      <div className="flex items-center gap-1 text-xs text-muted-foreground"><Icon className="size-3.5" /> {label}</div>
      <div className="mt-1 text-lg font-bold">{value}</div>
    </div>
  );
}
