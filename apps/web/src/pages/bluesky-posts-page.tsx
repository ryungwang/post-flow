import { useQuery } from "@tanstack/react-query";
import { ExternalLink, Heart, Loader2, MessageCircle, Repeat2 } from "lucide-react";
import { blueskyApi } from "@/lib/bluesky-api";
import { Card, CardContent } from "@/components/ui/card";

function fmt(iso: string | null) {
  if (!iso) return "";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(iso));
}

export function BlueskyPostsPage() {
  const { data, isLoading } = useQuery({ queryKey: ["bluesky-posts"], queryFn: () => blueskyApi.posts(25) });

  return (
    <div className="mx-auto w-full max-w-3xl px-6 py-7">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Bluesky · 내 게시물</h1>
        <p className="mt-1 text-sm text-muted-foreground">연결된 Bluesky 계정에 올라간 게시물과 반응이에요.</p>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="size-5 animate-spin text-muted-foreground" />
        </div>
      ) : !data?.length ? (
        <Card>
          <CardContent className="py-12 text-center text-sm text-muted-foreground">
            아직 게시물이 없어요. AI 생성에서 콘텐츠를 만들어 Bluesky 채널로 발행해 보세요.
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {data.map((p) => (
            <Card key={p.id}>
              <CardContent className="pt-5">
                <p className="whitespace-pre-wrap text-sm">{p.text}</p>
                {p.imageUrl && (
                  <img src={p.imageUrl} alt="" className="mt-3 max-h-72 rounded-lg border object-cover" />
                )}
                <div className="mt-3 flex items-center gap-4 text-xs text-muted-foreground">
                  <span className="flex items-center gap-1 tabular-nums"><Heart className="size-3.5" />{p.likes}</span>
                  <span className="flex items-center gap-1 tabular-nums"><Repeat2 className="size-3.5" />{p.reposts}</span>
                  <span className="flex items-center gap-1 tabular-nums"><MessageCircle className="size-3.5" />{p.replies}</span>
                  <span className="ml-2">{fmt(p.createdAt)}</span>
                  <a
                    href={p.permalink}
                    target="_blank"
                    rel="noreferrer"
                    className="ml-auto flex items-center gap-1 hover:text-foreground"
                  >
                    <ExternalLink className="size-3.5" /> 보기
                  </a>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
