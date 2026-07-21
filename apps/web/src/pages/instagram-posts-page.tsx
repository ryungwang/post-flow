import { useQuery } from "@tanstack/react-query";
import { ExternalLink, Heart, Loader2, MessageCircle } from "lucide-react";
import { instagramApi } from "@/lib/instagram-api";
import { Card, CardContent } from "@/components/ui/card";

function fmt(iso: string | null) {
  if (!iso) return "";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(iso));
}

export function InstagramPostsPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["instagram-posts"],
    queryFn: () => instagramApi.posts(25),
  });

  return (
    <div className="mx-auto max-w-6xl p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Instagram · 내 게시물</h1>
        <p className="mt-1 text-sm text-muted-foreground">연결된 인스타그램 계정에 올라간 게시물과 반응이에요.</p>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="size-5 animate-spin text-muted-foreground" />
        </div>
      ) : isError ? (
        <Card>
          <CardContent className="py-12 text-center text-sm text-muted-foreground">
            게시물을 불러오지 못했어요. Facebook 페이지 연결 상태를 확인해 주세요.
          </CardContent>
        </Card>
      ) : !data?.length ? (
        <Card>
          <CardContent className="py-12 text-center text-sm text-muted-foreground">
            아직 게시물이 없어요. AI 생성에서 콘텐츠를 만들어 Instagram 채널로 발행해 보세요.
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {data.map((p) => (
            <Card key={p.id}>
              <CardContent className="pt-5">
                {p.imageUrl && (
                  <img src={p.imageUrl} alt="" className="mb-3 aspect-square w-full rounded-lg border object-cover" />
                )}
                <p className="line-clamp-3 whitespace-pre-wrap text-sm">{p.caption}</p>
                <div className="mt-3 flex items-center gap-4 text-xs text-muted-foreground">
                  <span className="flex items-center gap-1 tabular-nums"><Heart className="size-3.5" />{p.likes}</span>
                  <span className="flex items-center gap-1 tabular-nums"><MessageCircle className="size-3.5" />{p.comments}</span>
                  <span className="ml-1">{fmt(p.createdAt)}</span>
                  {p.permalink && (
                    <a
                      href={p.permalink}
                      target="_blank"
                      rel="noreferrer"
                      className="ml-auto flex items-center gap-1 hover:text-foreground"
                    >
                      <ExternalLink className="size-3.5" /> 보기
                    </a>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
