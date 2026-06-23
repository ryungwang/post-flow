import { useState } from "react";
import { Link, NavLink } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  BarChart3,
  CalendarClock,
  ChevronRight,
  HelpCircle,
  LayoutDashboard,
  Library,
  Link2,
  MessageSquareReply,
  Rocket,
  Settings,
  Sparkles,
  Wand2,
} from "lucide-react";
import { accountApi } from "@/lib/account-api";
import { cn } from "@/lib/utils";

type Leaf = { label: string; to: string; icon?: React.ComponentType<{ className?: string }> };
type Group = { label: string; icon: React.ComponentType<{ className?: string }>; children: Leaf[] };
type Item = Leaf | Group;

const NAV: Item[] = [
  { label: "대시보드", to: "/", icon: LayoutDashboard },
  {
    label: "콘텐츠",
    icon: Sparkles,
    children: [
      { label: "AI 생성", to: "/content/generate", icon: Wand2 },
      { label: "시리즈 생성", to: "/content/series", icon: Sparkles },
      { label: "라이브러리", to: "/content/library", icon: Library },
    ],
  },
  { label: "스케줄", to: "/schedule", icon: CalendarClock },
  { label: "댓글 자동화", to: "/automation", icon: MessageSquareReply },
  { label: "분석", to: "/analytics", icon: BarChart3 },
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

function NavGroup({ group }: { group: Group }) {
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
            <NavLink key={leaf.to} to={leaf.to} className={leafClass}>
              {leaf.icon && <leaf.icon className="size-4 shrink-0" />}
              <span>{leaf.label}</span>
            </NavLink>
          ))}
        </div>
      )}
    </div>
  );
}

export function AppSidebar() {
  return (
    <aside className="flex h-full w-64 shrink-0 flex-col border-r bg-card/40">
      <div className="flex h-14 items-center gap-2 border-b px-5">
        <div className="bg-brand-gradient shadow-brand flex size-7 items-center justify-center rounded-md text-brand-foreground">
          <Sparkles className="size-4" />
        </div>
        <span className="text-gradient-brand text-base font-bold tracking-tight">PostFlow</span>
      </div>
      <nav className="flex flex-1 flex-col gap-1 overflow-y-auto p-3">
        {NAV.map((item) =>
          isGroup(item) ? (
            <NavGroup key={item.label} group={item} />
          ) : (
            <NavLink key={item.to} to={item.to} end className={leafClass}>
              {item.icon && <item.icon className="size-4 shrink-0" />}
              <span>{item.label}</span>
            </NavLink>
          ),
        )}
      </nav>
      <div className="border-t p-3">
        <UpgradeCta />
        <p className="px-2 text-xs text-muted-foreground">Create Once. Grow Automatically.</p>
      </div>
    </aside>
  );
}

const PLAN_LABEL: Record<string, string> = { FREE: "Free", STARTER: "Starter", PRO: "Pro", BUSINESS: "Business" };
const NEXT_PLAN: Record<string, string> = { FREE: "Starter", STARTER: "Pro", PRO: "Business" };

function UpgradeCta() {
  const { data } = useQuery({ queryKey: ["account", "usage"], queryFn: accountApi.usage });
  if (!data || data.plan === "BUSINESS") return null; // 최상위 플랜이면 숨김
  const next = NEXT_PLAN[data.plan] ?? "Pro";
  return (
    <Link
      to="/settings/account"
      className="bg-brand-gradient shadow-brand mb-3 flex items-center gap-2 rounded-xl px-3 py-2.5 text-sm font-semibold text-brand-foreground transition-transform hover:scale-[1.02]"
    >
      <Rocket className="size-4 shrink-0" />
      <span className="flex-1">{PLAN_LABEL[data.plan] ?? data.plan} → {next} 업그레이드</span>
    </Link>
  );
}
