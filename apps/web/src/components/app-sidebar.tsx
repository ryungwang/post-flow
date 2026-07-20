import { useEffect, useState } from "react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useSidebar } from "@/components/sidebar-context";
import { useQuery } from "@tanstack/react-query";
import {
  AtSign,
  BarChart3,
  CalendarClock,
  ChevronRight,
  Cloud,
  Globe,
  HelpCircle,
  LayoutDashboard,
  Library,
  Link2,
  Lock,
  Megaphone,
  Menu,
  MessageSquareReply,
  Search,
  Settings,
  Sparkles,
  TrendingUp,
  Wand2,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { accountApi } from "@/lib/account-api";
import { socialApi, PROVIDER_LABEL } from "@/lib/social-api";

// platform 지정("THREADS"·"BLUESKY"…) → 그 SNS 미연결이면 잠금(자물쇠 + 채널 연결 화면으로 유도).
type Leaf = { label: string; to: string; icon?: React.ComponentType<{ className?: string }>; pro?: boolean; platform?: string };
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
  {
    label: "Threads",
    icon: AtSign,
    children: [
      { label: "내 게시물", to: "/content/threads-posts", icon: Library, platform: "THREADS" },
      { label: "멘션", to: "/mentions", icon: AtSign, platform: "THREADS" },
      { label: "댓글 자동화", to: "/automation", icon: MessageSquareReply, pro: true, platform: "THREADS" },
      { label: "인사이트", to: "/insights", icon: TrendingUp, pro: true, platform: "THREADS" },
      { label: "경쟁사 분석", to: "/competitors", icon: Search, pro: true, platform: "THREADS" },
    ],
  },
  {
    label: "Bluesky",
    icon: Cloud,
    children: [
      { label: "내 게시물", to: "/content/bluesky-posts", icon: Library, platform: "BLUESKY" },
      { label: "인사이트", to: "/bluesky-insights", icon: TrendingUp, platform: "BLUESKY" },
    ],
  },
  {
    label: "Mastodon",
    icon: Globe,
    children: [
      { label: "내 게시물", to: "/content/mastodon-posts", icon: Library, platform: "MASTODON" },
      { label: "멘션", to: "/mastodon-mentions", icon: AtSign, platform: "MASTODON" },
      { label: "인사이트", to: "/mastodon-insights", icon: TrendingUp, platform: "MASTODON" },
    ],
  },
  { label: "분석", to: "/analytics", icon: BarChart3, pro: true },
  { label: "자주 묻는 질문", to: "/help", icon: HelpCircle },
  {
    label: "설정",
    icon: Settings,
    children: [
      { label: "채널 연결", to: "/settings/threads", icon: Link2 },
      { label: "계정", to: "/settings/account", icon: Settings },
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

/**
 * 잠금 규칙: 해당 SNS 미연결(platform) → 채널 연결 화면으로, Pro 미구독(pro) → 구독 화면으로.
 * 둘 다면 연결이 우선(채널부터 연결해야 그 기능을 쓰므로 — 그룹 내 동선 통일).
 * connectedProviders=null이면 로딩 중이라 잠그지 않음.
 */
function NavLeaf({ leaf, isPro, connectedProviders }: { leaf: Leaf; isPro: boolean; connectedProviders: Set<string> | null }) {
  const navigate = useNavigate();
  const proLocked = !!leaf.pro && !isPro;
  const platformLocked = !!leaf.platform && connectedProviders != null && !connectedProviders.has(leaf.platform);
  if (proLocked || platformLocked) {
    const to = platformLocked ? "/settings/threads" : "/settings/account";
    const title = platformLocked
      ? `${PROVIDER_LABEL[leaf.platform!] ?? leaf.platform} 계정 연결이 필요해요`
      : "Pro 플랜 전용";
    return (
      <button
        onClick={() => navigate(to)}
        title={title}
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

function NavGroup({ group, isPro, connectedProviders }: { group: Group; isPro: boolean; connectedProviders: Set<string> | null }) {
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
            <NavLeaf key={leaf.to} leaf={leaf} isPro={isPro} connectedProviders={connectedProviders} />
          ))}
        </div>
      )}
    </div>
  );
}

/** 사이드바 본문(메뉴) — 데스크톱 aside·모바일 드로어가 공유. */
function SidebarNav() {
  // 플랜/연결 확인 — 메뉴 잠금용. 로딩 중엔 잠그지 않음(깜빡임 방지).
  const { data: usage } = useQuery({ queryKey: ["account", "usage"], queryFn: accountApi.usage });
  const { data: channels } = useQuery({ queryKey: ["social-channels"], queryFn: socialApi.channels });
  const isPro = usage ? usage.plan === "PRO" : true;
  // 연결된 플랫폼 집합(CONNECTED 채널 보유). null = 로딩 중 → 잠그지 않음(깜빡임 방지).
  const connectedProviders = channels
    ? new Set(channels.filter((c) => c.status === "CONNECTED").map((c) => c.provider))
    : null;
  return (
    <>
      <nav className="flex flex-1 flex-col gap-1 overflow-y-auto p-3 pt-4">
        {NAV.map((item) =>
          isGroup(item) ? (
            <NavGroup key={item.label} group={item} isPro={isPro} connectedProviders={connectedProviders} />
          ) : (
            <NavLeaf key={item.to} leaf={item} isPro={isPro} connectedProviders={connectedProviders} />
          ),
        )}
      </nav>
      <div className="border-t p-3">
        <p className="px-2 text-xs text-muted-foreground">Create Once. Grow Automatically.</p>
      </div>
    </>
  );
}

/** 데스크톱 사이드바 — md 이상에서만 표시(모바일은 드로어). */
export function AppSidebar() {
  return (
    <aside className="hidden h-full w-64 shrink-0 flex-col border-r bg-card/40 md:flex">
      <SidebarNav />
    </aside>
  );
}

/** 헤더의 모바일 메뉴 버튼(햄버거) — md 미만에서만. */
export function MobileMenuButton() {
  const { setMobileOpen } = useSidebar();
  return (
    <button
      onClick={() => setMobileOpen(true)}
      aria-label="메뉴 열기"
      className="-ml-1 rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground md:hidden"
    >
      <Menu className="size-5" />
    </button>
  );
}

/** 모바일 사이드바 드로어 — 햄버거로 열고, 오버레이·ESC·라우트 변경 시 닫힘. */
export function MobileSidebar() {
  const { mobileOpen, setMobileOpen } = useSidebar();
  const location = useLocation();
  // 라우트가 바뀌면(=메뉴 클릭·잠금 이동 등) 드로어 닫기.
  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname, setMobileOpen]);
  useEffect(() => {
    if (!mobileOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setMobileOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [mobileOpen, setMobileOpen]);
  if (!mobileOpen) return null;
  return (
    <div className="fixed inset-0 z-50 md:hidden" role="dialog" aria-modal="true">
      <div className="absolute inset-0 bg-black/50" onClick={() => setMobileOpen(false)} />
      <aside className="animate-slide-in-left absolute inset-y-0 left-0 flex w-64 max-w-[82%] flex-col border-r bg-card shadow-xl">
        <SidebarNav />
      </aside>
    </div>
  );
}
