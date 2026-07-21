import { useQuery } from "@tanstack/react-query";
import { FileText, Heart, Loader2, MessageCircle, UserPlus, Users } from "lucide-react";
import { instagramApi } from "@/lib/instagram-api";
import { Card, CardContent } from "@/components/ui/card";

export function InstagramInsightsPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["instagram-insights"],
    queryFn: instagramApi.insights,
  });

  const stats = [
    { label: "팔로워", value: data?.followers, icon: Users },
    { label: "팔로잉", value: data?.following, icon: UserPlus },
    { label: "게시물", value: data?.posts, icon: FileText },
    { label: "좋아요 (최근)", value: data?.totalLikes, icon: Heart },
    { label: "댓글 (최근)", value: data?.totalComments, icon: MessageCircle },
  ];

  return (
    <div className="mx-auto max-w-6xl p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Instagram · 인사이트</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {data ? `@${data.username}` : "연결된 인스타그램 계정"} · 참여 수치는 최근 {data?.sampledPosts ?? 0}건 기준이에요.
        </p>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="size-5 animate-spin text-muted-foreground" />
        </div>
      ) : isError ? (
        <p className="rounded-xl border bg-card/40 p-10 text-center text-sm text-muted-foreground">
          인사이트를 불러오지 못했어요. Facebook 페이지 연결 상태를 확인해 주세요.
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
                <div className="flex size-10 items-center justify-center rounded-lg bg-pink-500/10 text-pink-500">
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
