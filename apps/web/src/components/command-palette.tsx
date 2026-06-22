import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  BarChart3, CalendarClock, Library, LayoutDashboard, Link2, MessageSquareReply,
  Search, Settings, Sparkles, Wand2,
} from "lucide-react";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

type Cmd = { label: string; to: string; icon: React.ComponentType<{ className?: string }>; keywords?: string };

const COMMANDS: Cmd[] = [
  { label: "대시보드", to: "/", icon: LayoutDashboard, keywords: "dashboard home 홈" },
  { label: "AI 생성", to: "/content/generate", icon: Wand2, keywords: "generate 글쓰기 작성" },
  { label: "시리즈 생성", to: "/content/series", icon: Sparkles, keywords: "series" },
  { label: "라이브러리", to: "/content/library", icon: Library, keywords: "posts 게시물 글" },
  { label: "스케줄", to: "/schedule", icon: CalendarClock, keywords: "calendar 예약 캘린더" },
  { label: "댓글 자동화", to: "/automation", icon: MessageSquareReply, keywords: "comment automation" },
  { label: "분석", to: "/analytics", icon: BarChart3, keywords: "analytics roi 수익" },
  { label: "계정", to: "/settings/account", icon: Settings, keywords: "account 설정 플랜 결제" },
  { label: "Threads 연결", to: "/settings/threads", icon: Link2, keywords: "threads connect 연결" },
];

export function CommandPalette() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState("");

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((v) => !v);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  useEffect(() => { if (!open) setQ(""); }, [open]);

  const results = useMemo(() => {
    const query = q.trim().toLowerCase();
    if (!query) return COMMANDS;
    return COMMANDS.filter((c) => (c.label + " " + (c.keywords ?? "")).toLowerCase().includes(query));
  }, [q]);

  const go = (to: string) => { setOpen(false); navigate(to); };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent className="top-[20%] max-w-lg translate-y-0 gap-0 p-0">
        <DialogTitle className="sr-only">명령 팔레트</DialogTitle>
        <div className="flex items-center gap-2 border-b border-border/60 px-3">
          <Search className="size-4 text-muted-foreground" />
          <input
            autoFocus
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter" && results[0]) go(results[0].to); }}
            placeholder="화면 이동·검색… (⌘K)"
            className="h-12 flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
          />
        </div>
        <ul className="max-h-80 overflow-y-auto p-2">
          {results.length === 0 ? (
            <li className="px-3 py-6 text-center text-sm text-muted-foreground">결과 없음</li>
          ) : (
            results.map((c, i) => (
              <li key={c.to}>
                <button
                  onClick={() => go(c.to)}
                  className={cn(
                    "flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm transition-colors hover:bg-accent",
                    i === 0 && q && "bg-accent",
                  )}
                >
                  <c.icon className="size-4 text-muted-foreground" />
                  {c.label}
                </button>
              </li>
            ))
          )}
        </ul>
      </DialogContent>
    </Dialog>
  );
}
