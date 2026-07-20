import { useQuery } from "@tanstack/react-query";
import { FileText, Loader2, MessageCircle, Repeat2, Star, UserPlus, Users } from "lucide-react";
import { mastodonApi } from "@/lib/mastodon-api";
import { Card, CardContent } from "@/components/ui/card";

export function MastodonInsightsPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["mastodon-insights"],
    queryFn: mastodonApi.insights,
  });

  const stats = [
    { label: "팔로워", value: data?.followers, icon: Users },
    { label: "팔로잉", value: data?.following, icon: UserPlus },
    { label: "게시물", value: data?.posts, icon: FileText },
    { label: "즐겨찾기 (최근)", value: data?.totalFavourites, icon: Star },
    { label: "부스트 (최근)", value: data?.totalReblogs, icon: Repeat2 },
    { label: "답글 (최근)", value: data?.totalReplies, icon: MessageCircle },
  ];

  return (
    <div className="mx-auto max-w-6xl p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Mastodon · 인사이트</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {data ? `@${data.handle}@${data.instance}` : "연결된 마스토돈 계정"} · 참여 수치는 최근{" "}
          {data?.sampledPosts ?? 0}건 기준이에요.
        </p>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="size-5 animate-spin text-muted-foreground" />
        </div>
      ) : isError ? (
        // 실패를 0으로 보여주면 "수치가 사라졌다"는 오해를 부른다 — 실패는 실패로 표시.
        <p className="rounded-xl border bg-card/40 p-10 text-center text-sm text-muted-foreground">
          인사이트를 불러오지 못했어요. 마스토돈 계정 연결 상태를 확인해 주세요.
        </p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {stats.map((s) => (
            <Card key={s.label}>
              <CardContent className="flex items-center justify-between pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{s.label}</div>
                  <div className="mt-1 text-2xl font-bold tabular-nums">{(s.value ?? 0).toLocaleString()}</div>
                </div>
                <div className="flex size-10 items-center justify-center rounded-lg bg-[#6364ff]/10 text-[#6364ff]">
                  <s.icon className="size-5" />
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
