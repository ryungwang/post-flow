import { useQuery } from "@tanstack/react-query";
import { FileText, Heart, Loader2, MessageCircle, Repeat2, Users } from "lucide-react";
import { blueskyApi } from "@/lib/bluesky-api";
import { Card, CardContent } from "@/components/ui/card";

export function BlueskyInsightsPage() {
  const { data, isLoading } = useQuery({ queryKey: ["bluesky-insights"], queryFn: blueskyApi.insights });

  const stats = [
    { label: "팔로워", value: data?.followers, icon: Users },
    { label: "게시물", value: data?.posts, icon: FileText },
    { label: "좋아요 (최근)", value: data?.totalLikes, icon: Heart },
    { label: "리포스트 (최근)", value: data?.totalReposts, icon: Repeat2 },
    { label: "답글 (최근)", value: data?.totalReplies, icon: MessageCircle },
  ];

  return (
    <div className="w-full px-6 py-7 lg:px-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Bluesky · 인사이트</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {data ? `@${data.handle}` : "연결된 Bluesky 계정"} · 참여 수치는 최근 {data?.sampledPosts ?? 0}건 기준이에요.
        </p>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="size-5 animate-spin text-muted-foreground" />
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {stats.map((s) => (
            <Card key={s.label}>
              <CardContent className="flex items-center justify-between pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{s.label}</div>
                  <div className="mt-1 text-2xl font-bold tabular-nums">{(s.value ?? 0).toLocaleString()}</div>
                </div>
                <div className="flex size-10 items-center justify-center rounded-lg bg-sky-500/10 text-sky-500">
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
