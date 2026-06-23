import { Heart, MessageCircle, MoreHorizontal, Repeat2, Send } from "lucide-react";

/** How a post will look as a Threads card (visual mockup — no API). */
export function ThreadsPreview({
  username,
  avatarUrl,
  content,
  hashtags,
  cta,
  mediaUrl,
}: {
  username: string;
  avatarUrl?: string | null;
  content: string;
  hashtags: string[];
  cta: string | null;
  mediaUrl: string | null;
}) {
  const handle = "@" + (username || "you").toLowerCase().replace(/\s+/g, "");
  return (
    <div className="rounded-2xl border border-border/70 bg-card p-4">
      <div className="flex gap-3">
        {avatarUrl ? (
          <img src={avatarUrl} alt="" className="size-9 shrink-0 rounded-full object-cover" />
        ) : (
          <div className="bg-brand-gradient flex size-9 shrink-0 items-center justify-center rounded-full text-sm font-semibold text-brand-foreground">
            {(username || "U").charAt(0).toUpperCase()}
          </div>
        )}
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5 text-sm">
            <span className="font-semibold">{username || "나"}</span>
            <span className="text-muted-foreground">{handle}</span>
            <span className="text-muted-foreground">· 방금</span>
            <MoreHorizontal className="ml-auto size-4 text-muted-foreground" />
          </div>

          <p className="mt-1 whitespace-pre-wrap text-sm leading-relaxed">{content}</p>

          {(hashtags?.length > 0 || cta) && (
            <p className="mt-1.5 text-sm leading-relaxed text-brand">
              {hashtags?.map((h) => `#${h}`).join(" ")}
              {cta && (hashtags?.length ? "\n" : "") + cta}
            </p>
          )}

          {mediaUrl && (
            <img src={mediaUrl} alt="" className="mt-3 max-h-72 w-full rounded-xl border object-cover" onError={(e) => { e.currentTarget.style.display = "none"; }} />
          )}

          <div className="mt-3 flex items-center gap-5 text-muted-foreground">
            <Heart className="size-[18px]" />
            <MessageCircle className="size-[18px]" />
            <Repeat2 className="size-[18px]" />
            <Send className="size-[18px]" />
          </div>
        </div>
      </div>
    </div>
  );
}
