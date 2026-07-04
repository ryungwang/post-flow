import { useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  BarChart3,
  CalendarClock,
  ChevronRight,
  HelpCircle,
  LayoutDashboard,
  Library,
  Link2,
  Lock,
  Megaphone,
  MessageSquareReply,
  Settings,
  Sparkles,
  Wand2,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { accountApi } from "@/lib/account-api";

type Leaf = { label: string; to: string; icon?: React.ComponentType<{ className?: string }>; pro?: boolean };
type Group = { label: string; icon: React.ComponentType<{ className?: string }>; children: Leaf[] };
type Item = Leaf | Group;

const NAV: Item[] = [
  { label: "대시보드", to: "/", icon: LayoutDashboard },
  {
    label: "콘텐츠",
    icon: Sparkles,
    children: [
      { label: "AI 생성", to: "/content/generate", icon: Wand2 },
      { label: "시리즈 생성", to: "/content/series", icon: Sparkles, pro: true },
      { label: "브랜드/제품", to: "/brands", icon: Megaphone },
      { label: "라이브러리", to: "/content/library", icon: Library },
    ],
  },
  { label: "스케줄", to: "/schedule", icon: CalendarClock },
  { label: "댓글 자동화", to: "/automation", icon: MessageSquareReply, pro: true },
  { label: "분석", to: "/analytics", icon: BarChart3, pro: true },
  { label: "자주 묻는 질문", to: "/help", icon: HelpCircle },
  {
    label: "설정",
    icon: Settings,
    children: [
      { label: "계정", to: "/settings/account", icon: Settings },
      { label: "Threads 연결", to: "/settings/threads", icon: Link2 },
    ],
  },
];

function isGroup(i: Item): i is Group {
  return (i as Group).children !== undefined;
}

const leafClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "group relative flex items-center gap-2.5 rounded-md px-3 py-2 text-sm transition-all",
    isActive
      ? "bg-brand/10 font-medium text-foreground shadow-[inset_2px_0_0_0_var(--brand)]"
      : "text-muted-foreground hover:translate-x-0.5 hover:bg-accent/60 hover:text-foreground",
  );

/** Pro 전용인데 미구독이면 자물쇠 표시 + 클릭 시 계정(구독)으로. 아니면 일반 NavLink. */
function NavLeaf({ leaf, locked }: { leaf: Leaf; locked: boolean }) {
  const navigate = useNavigate();
  if (locked) {
    return (
      <button
        onClick={() => navigate("/settings/account")}
        title="Pro 플랜 전용"
        className={cn(
          "group relative flex items-center gap-2.5 rounded-md px-3 py-2 text-sm text-muted-foreground/60 transition-all hover:bg-accent/40",
        )}
      >
        {leaf.icon && <leaf.icon className="size-4 shrink-0" />}
        <span className="flex-1 text-left">{leaf.label}</span>
        <Lock className="size-3.5 shrink-0 opacity-70" />
      </button>
    );
  }
  return (
    <NavLink to={leaf.to} end className={leafClass}>
      {leaf.icon && <leaf.icon className="size-4 shrink-0" />}
      <span>{leaf.label}</span>
    </NavLink>
  );
}

function NavGroup({ group, isPro }: { group: Group; isPro: boolean }) {
  const [open, setOpen] = useState(true);
  const Icon = group.icon;
  return (
    <div>
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium text-foreground/80 transition-colors hover:bg-accent/60"
      >
        <Icon className="size-4 shrink-0" />
        <span className="flex-1 text-left">{group.label}</span>
        <ChevronRight className={cn("size-4 transition-transform", open && "rotate-90")} />
      </button>
      {open && (
        <div className="ml-3.5 mt-0.5 flex flex-col gap-0.5 border-l pl-3">
          {group.children.map((leaf) => (
            <NavLeaf key={leaf.to} leaf={leaf} locked={!!leaf.pro && !isPro} />
          ))}
        </div>
      )}
    </div>
  );
}

export function AppSidebar() {
  // 플랜 확인 — Pro 전용 메뉴 잠금용. 로딩 중엔 잠그지 않음(깜빡임 방지).
  const { data: usage } = useQuery({ queryKey: ["account", "usage"], queryFn: accountApi.usage });
  const isPro = usage ? usage.plan === "PRO" : true;
  return (
    <aside className="flex h-full w-64 shrink-0 flex-col border-r bg-card/40">
      <nav className="flex flex-1 flex-col gap-1 overflow-y-auto p-3 pt-4">
        {NAV.map((item) =>
          isGroup(item) ? (
            <NavGroup key={item.label} group={item} isPro={isPro} />
          ) : (
            <NavLeaf key={item.to} leaf={item} locked={!!item.pro && !isPro} />
          ),
        )}
      </nav>
      <div className="border-t p-3">
        <p className="px-2 text-xs text-muted-foreground">Create Once. Grow Automatically.</p>
      </div>
    </aside>
  );
}
