import { useQuery } from "@tanstack/react-query";
import { ExternalLink, Loader2 } from "lucide-react";
import { mastodonApi } from "@/lib/mastodon-api";
import { Card, CardContent } from "@/components/ui/card";

function fmt(iso: string | null) {
  if (!iso) return "";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(iso));
}

export function MastodonMentionsPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["mastodon-mentions"],
    queryFn: () => mastodonApi.mentions(25),
  });

  return (
    <div className="mx-auto w-full max-w-3xl px-6 py-7">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Mastodon · 멘션</h1>
        <p className="mt-1 text-sm text-muted-foreground">나를 멘션한 게시물이에요. 답글은 마스토돈에서 달 수 있어요.</p>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="size-5 animate-spin text-muted-foreground" />
        </div>
      ) : !data?.length ? (
        <Card>
          <CardContent className="py-12 text-center text-sm text-muted-foreground">
            아직 멘션이 없어요.
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {data.map((m) => (
            <Card key={m.id}>
              <CardContent className="pt-5">
                <div className="flex items-center gap-2">
                  {m.authorAvatar && (
                    <img src={m.authorAvatar} alt="" className="size-7 rounded-full border object-cover" />
                  )}
                  <div className="min-w-0">
                    <div className="truncate text-sm font-medium">{m.authorName || m.authorHandle}</div>
                    <div className="truncate text-xs text-muted-foreground">@{m.authorHandle}</div>
                  </div>
                </div>
                <p className="mt-3 whitespace-pre-wrap text-sm">{m.text}</p>
                <div className="mt-3 flex items-center gap-3 text-xs text-muted-foreground">
                  <span>{fmt(m.createdAt)}</span>
                  {m.permalink && (
                    <a
                      href={m.permalink}
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
