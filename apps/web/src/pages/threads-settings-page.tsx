import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AtSign, Cloud, Facebook, Globe, Instagram, Linkedin, Loader2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { threadsApi } from "@/lib/threads-api";
import { linkedinApi } from "@/lib/linkedin-api";
import { facebookApi } from "@/lib/facebook-api";
import { instagramApi } from "@/lib/instagram-api";
import { mastodonApi } from "@/lib/mastodon-api";
import { blueskyApi } from "@/lib/bluesky-api";
import { socialApi } from "@/lib/social-api";
import { accountApi } from "@/lib/account-api";
import { useConfirm } from "@/components/confirm-dialog";
import { useToast } from "@/components/toast";
import { CountUp } from "@/components/count-up";

const STATUS_META: Record<string, { label: string; variant: "success" | "warning" | "muted" }> = {
  CONNECTED: { label: "연결됨", variant: "success" },
  EXPIRED: { label: "만료됨", variant: "warning" },
  RECONNECT_REQUIRED: { label: "재연결 필요", variant: "warning" },
  NOT_CONNECTED: { label: "미연결", variant: "muted" },
};

function fmt(iso: string | null) {
  if (!iso) return null;
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium" }).format(new Date(iso));
}

/** Pro 등 다계정 허용 플랜인지(각 SNS 카드가 '계정 추가' 안내를 켤지 판단). react-query 캐시 공유. */
function useCanMulti() {
  const { data } = useQuery({ queryKey: ["account", "usage"], queryFn: accountApi.usage });
  return !!data?.canMultiAccount;
}

/** 다계정 플랜에서 '다른 계정을 추가하려면 다른 계정으로 로그인' 안내(교체가 아니라 추가임을 명확히). */
function MultiAccountHint({ sns }: { sns: string }) {
  return (
    <p className="text-xs text-muted-foreground">
      이미 연결돼 있어도 <span className="font-medium">계정을 더 추가</span>할 수 있어요(Pro). 다른 {sns} 계정을 추가하려면
      브라우저에서 <span className="font-medium">그 계정으로 로그인</span>(또는 시크릿 창)한 뒤 위 버튼을 누르세요. 같은 계정이면 정보만 갱신돼요.
    </p>
  );
}

export function ThreadsSettingsPage() {
  const qc = useQueryClient();
  const canMulti = useCanMulti();
  const { data, isLoading } = useQuery({ queryKey: ["threads-status"], queryFn: threadsApi.status });
  const [connecting, setConnecting] = useState(false);

  // If this page is loaded inside the OAuth popup (callback redirect), notify the opener & close.
  // Threads(?threads=) and LinkedIn(?linkedin=) share this settings page as their frontend redirect.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    for (const provider of ["threads", "linkedin", "facebook", "instagram"] as const) {
      const result = params.get(provider);
      if (result && window.opener) {
        window.opener.postMessage({ type: `${provider}-oauth`, result }, window.location.origin);
        window.close();
        return;
      }
    }
  }, []);

  const connect = async () => {
    setConnecting(true);
    try {
      const { authorizeUrl } = await threadsApi.connectUrl();
      const w = 600;
      const h = 720;
      const left = window.screenX + (window.outerWidth - w) / 2;
      const top = window.screenY + (window.outerHeight - h) / 2;
      const popup = window.open(authorizeUrl, "threads-oauth", `width=${w},height=${h},left=${left},top=${top}`);
      if (!popup) {
        // popup blocked → fall back to full-page redirect
        window.location.href = authorizeUrl;
        return;
      }
      const onMessage = (e: MessageEvent) => {
        if (e.origin !== window.location.origin) return;
        if (e.data?.type === "threads-oauth") {
          window.removeEventListener("message", onMessage);
          clearInterval(timer);
          setConnecting(false);
          qc.invalidateQueries({ queryKey: ["threads-status"] }); qc.invalidateQueries({ queryKey: ["threads-accounts"] });
        }
      };
      window.addEventListener("message", onMessage);
      // safety: detect manual close
      const timer = setInterval(() => {
        if (popup.closed) {
          clearInterval(timer);
          window.removeEventListener("message", onMessage);
          setConnecting(false);
          qc.invalidateQueries({ queryKey: ["threads-status"] }); qc.invalidateQueries({ queryKey: ["threads-accounts"] });
        }
      }, 600);
    } catch {
      setConnecting(false);
    }
  };

  const meta = STATUS_META[data?.status ?? "NOT_CONNECTED"] ?? STATUS_META.NOT_CONNECTED;
  const connected = data?.connected;

  return (
    <div className="mx-auto w-full max-w-3xl px-6 py-7">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">채널 연결</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          SNS 채널을 연결하면 한 번 만든 콘텐츠를 예약·자동 발행할 수 있어요. (Threads · Bluesky · LinkedIn · Mastodon · Facebook · Instagram)
        </p>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-lg bg-foreground text-background">
              <AtSign className="size-5" />
            </div>
            <div className="flex-1">
              <CardTitle>Threads</CardTitle>
              <CardDescription>Meta OAuth로 안전하게 연결됩니다.</CardDescription>
            </div>
            {isLoading ? (
              <Loader2 className="size-4 animate-spin text-muted-foreground" />
            ) : (
              <Badge variant={meta.variant}>
                <span className="size-1.5 rounded-full bg-current" />
                {meta.label}
              </Badge>
            )}
          </div>
        </CardHeader>
        <CardContent>
          {connected && data?.expiresAt && (
            <p className="mb-4 text-sm text-muted-foreground">
              토큰 만료: <span className="tabular-nums">{fmt(data.expiresAt)}</span> · 만료 전 자동 갱신됩니다.
            </p>
          )}
          <Button onClick={connect} disabled={connecting} className="gap-2">
            {connecting && <Loader2 className="size-4 animate-spin" />}
            {!connected ? "Threads 연결하기" : canMulti ? "+ Threads 계정 추가" : "다시 연결"}
          </Button>
          <div className="mt-3">
            {!connected ? (
              <p className="text-xs text-muted-foreground">연결에는 Threads 앱 설정(서버 키)이 필요합니다. 키 미설정 시 연결이 진행되지 않을 수 있어요.</p>
            ) : canMulti ? (
              <MultiAccountHint sns="Threads" />
            ) : (
              <p className="text-xs text-muted-foreground">다른 계정으로 바꾸려면 아래에서 연결 해제 후 다시 연결하세요. (다른 계정으로 인증하면 현재 연결이 교체됩니다.)</p>
            )}
          </div>
        </CardContent>
      </Card>

      <BlueskyCard />

      <LinkedInCard />

      <MastodonCard />

      <FacebookCard />
      <InstagramCard />

      <ConnectedChannelsCard />
    </div>
  );
}

/** Bluesky 연결 — OAuth 아님, 핸들 + 앱 비밀번호. 세션 토큰만 저장(앱 비번 미저장). */
function BlueskyCard() {
  const qc = useQueryClient();
  const { show } = useToast();
  const canMulti = useCanMulti();
  const [handle, setHandle] = useState("");
  const [appPassword, setAppPassword] = useState("");

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["social-channels"] });
    qc.invalidateQueries({ queryKey: ["threads-accounts"] }); // 발행 채널 선택 갱신
  };

  const connect = useMutation({
    mutationFn: () => socialApi.connectBluesky(handle, appPassword),
    meta: { loading: "블루스카이 연결 중…", success: "블루스카이 연결됨", error: "블루스카이 연결 실패" },
    onSuccess: () => {
      setHandle("");
      setAppPassword("");
      invalidate();
    },
  });

  const submit = () => {
    if (!handle.trim() || !appPassword.trim()) {
      show("핸들과 앱 비밀번호를 입력해 주세요.", "error");
      return;
    }
    connect.mutate();
  };

  return (
    <Card className="mt-6">
      <CardHeader>
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-lg bg-sky-500 text-white">
            <Cloud className="size-5" />
          </div>
          <div className="flex-1">
            <CardTitle>Bluesky</CardTitle>
            <CardDescription>핸들과 앱 비밀번호로 연결해요. (무료 · 심사 없음)</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
          <Input
            placeholder="핸들 (예: name.bsky.social)"
            value={handle}
            autoCapitalize="none"
            onChange={(e) => setHandle(e.target.value)}
          />
          <Input
            type="password"
            placeholder="앱 비밀번호"
            value={appPassword}
            onChange={(e) => setAppPassword(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submit()}
          />
          <Button onClick={submit} disabled={connect.isPending} className="gap-2">
            {connect.isPending && <Loader2 className="size-4 animate-spin" />}
            연결
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">
          Bluesky 설정 → <span className="font-medium">앱 비밀번호(App Passwords)</span>에서 발급한 비밀번호를 넣으세요.
          일반 로그인 비밀번호가 아니에요. 앱 비밀번호는 저장하지 않고, 연결용 토큰만 보관합니다.
          {canMulti && <span> 다른 계정을 추가하려면 <span className="font-medium">다른 핸들·앱 비밀번호</span>를 넣으면 돼요(Pro).</span>}
        </p>
      </CardContent>
    </Card>
  );
}

/** LinkedIn 연결 — OAuth2(팝업). 발행 전용(개인 프로필 읽기/분석은 파트너 승인 필요). */
function LinkedInCard() {
  const qc = useQueryClient();
  const canMulti = useCanMulti();
  const [connecting, setConnecting] = useState(false);

  const { data: channels } = useQuery({ queryKey: ["social-channels"], queryFn: socialApi.channels });
  const linkedin = (channels ?? []).filter((c) => c.provider === "LINKEDIN");

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["social-channels"] });
    qc.invalidateQueries({ queryKey: ["threads-accounts"] }); // 발행 채널 선택 갱신
  };

  const connect = async () => {
    setConnecting(true);
    try {
      const { authorizeUrl } = await linkedinApi.connectUrl();
      const w = 600;
      const h = 720;
      const left = window.screenX + (window.outerWidth - w) / 2;
      const top = window.screenY + (window.outerHeight - h) / 2;
      const popup = window.open(authorizeUrl, "linkedin-oauth", `width=${w},height=${h},left=${left},top=${top}`);
      if (!popup) {
        window.location.href = authorizeUrl; // popup blocked → full-page redirect
        return;
      }
      const onMessage = (e: MessageEvent) => {
        if (e.origin !== window.location.origin) return;
        if (e.data?.type === "linkedin-oauth") {
          window.removeEventListener("message", onMessage);
          clearInterval(timer);
          setConnecting(false);
          invalidate();
        }
      };
      window.addEventListener("message", onMessage);
      const timer = setInterval(() => {
        if (popup.closed) {
          clearInterval(timer);
          window.removeEventListener("message", onMessage);
          setConnecting(false);
          invalidate();
        }
      }, 600);
    } catch {
      setConnecting(false);
    }
  };

  return (
    <Card className="mt-6">
      <CardHeader>
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-lg bg-[#0a66c2] text-white">
            <Linkedin className="size-5" />
          </div>
          <div className="flex-1">
            <CardTitle>LinkedIn</CardTitle>
            <CardDescription>OAuth로 안전하게 연결해요. 텍스트·이미지 게시물을 내 피드에 발행합니다.</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <Button onClick={connect} disabled={connecting} className="gap-2">
          {connecting && <Loader2 className="size-4 animate-spin" />}
          {linkedin.length === 0 ? "LinkedIn 연결하기" : canMulti ? "+ LinkedIn 계정 추가" : "다시 연결"}
        </Button>
        <p className="text-xs text-muted-foreground">
          연결에는 LinkedIn 앱 설정(서버 키)이 필요해요. 키 미설정 시 연결이 진행되지 않을 수 있어요.
          개인 프로필 피드에 텍스트·이미지를 발행합니다.
        </p>
        {canMulti && linkedin.length > 0 && <MultiAccountHint sns="LinkedIn" />}
      </CardContent>
    </Card>
  );
}

/** Mastodon 연결 — OAuth 아님, 인스턴스 주소 + 액세스 토큰. 토큰만 저장(무료·심사 없음). */
function MastodonCard() {
  const qc = useQueryClient();
  const { show } = useToast();
  const canMulti = useCanMulti();
  const [instanceUrl, setInstanceUrl] = useState("");
  const [accessToken, setAccessToken] = useState("");

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["social-channels"] });
    qc.invalidateQueries({ queryKey: ["threads-accounts"] });
  };

  const connect = useMutation({
    mutationFn: () => socialApi.connectMastodon(instanceUrl, accessToken),
    meta: { loading: "마스토돈 연결 중…", success: "마스토돈 연결됨", error: "마스토돈 연결 실패" },
    onSuccess: () => {
      setInstanceUrl("");
      setAccessToken("");
      invalidate();
    },
  });

  const submit = () => {
    if (!instanceUrl.trim() || !accessToken.trim()) {
      show("핸들(또는 인스턴스 주소)과 액세스 토큰을 모두 입력해 주세요.", "error");
      return;
    }
    connect.mutate();
  };

  return (
    <Card className="mt-6">
      <CardHeader>
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-lg bg-[#6364ff] text-white">
            <Globe className="size-5" />
          </div>
          <div className="flex-1">
            <CardTitle>Mastodon</CardTitle>
            <CardDescription>내 핸들과 액세스 토큰으로 연결해요. (무료 · 심사 없음)</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
          <Input
            placeholder="핸들 또는 인스턴스 (예: @me@mastodon.social)"
            value={instanceUrl}
            autoCapitalize="none"
            onChange={(e) => setInstanceUrl(e.target.value)}
          />
          <Input
            type="password"
            placeholder="액세스 토큰"
            value={accessToken}
            onChange={(e) => setAccessToken(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submit()}
          />
          <Button onClick={submit} disabled={connect.isPending} className="gap-2">
            {connect.isPending && <Loader2 className="size-4 animate-spin" />}
            연결
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">
          첫 칸엔 내 <span className="font-medium">핸들</span>(<code>@me@mastodon.social</code>)을 그대로
          붙여넣으면 돼요. 인스턴스 주소(<code>mastodon.social</code>)만 넣어도 됩니다. 둘째 칸엔 내 인스턴스 →{" "}
          <span className="font-medium">설정 → 개발 → 새 애플리케이션</span>에서 만든 앱의{" "}
          <span className="font-medium">액세스 토큰</span>을 넣으세요. (권한: <code>write</code> 포함)
          토큰만 보관하며, 텍스트·이미지를 발행합니다.
          {canMulti && <span> 다른 계정을 추가하려면 <span className="font-medium">다른 인스턴스·토큰</span>을 넣으면 돼요(Pro).</span>}
        </p>
      </CardContent>
    </Card>
  );
}

/** Facebook 페이지 연결 — OAuth2(팝업). 관리하는 페이지를 채널로 등록. 발행 텍스트+이미지. */
function FacebookCard() {
  const qc = useQueryClient();
  const canMulti = useCanMulti();
  const [connecting, setConnecting] = useState(false);

  const { data: channels } = useQuery({ queryKey: ["social-channels"], queryFn: socialApi.channels });
  const facebook = (channels ?? []).filter((c) => c.provider === "FACEBOOK");

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["social-channels"] });
    qc.invalidateQueries({ queryKey: ["threads-accounts"] });
  };

  const connect = async () => {
    setConnecting(true);
    try {
      const { authorizeUrl } = await facebookApi.connectUrl();
      const w = 600;
      const h = 720;
      const left = window.screenX + (window.outerWidth - w) / 2;
      const top = window.screenY + (window.outerHeight - h) / 2;
      const popup = window.open(authorizeUrl, "facebook-oauth", `width=${w},height=${h},left=${left},top=${top}`);
      if (!popup) {
        window.location.href = authorizeUrl;
        return;
      }
      const onMessage = (e: MessageEvent) => {
        if (e.origin !== window.location.origin) return;
        if (e.data?.type === "facebook-oauth") {
          window.removeEventListener("message", onMessage);
          clearInterval(timer);
          setConnecting(false);
          invalidate();
        }
      };
      window.addEventListener("message", onMessage);
      const timer = setInterval(() => {
        if (popup.closed) {
          clearInterval(timer);
          window.removeEventListener("message", onMessage);
          setConnecting(false);
          invalidate();
        }
      }, 600);
    } catch {
      setConnecting(false);
    }
  };

  return (
    <Card className="mt-6">
      <CardHeader>
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-lg bg-[#1877f2] text-white">
            <Facebook className="size-5" />
          </div>
          <div className="flex-1">
            <CardTitle>Facebook 페이지</CardTitle>
            <CardDescription>OAuth로 연결해요. 내가 관리하는 페이지에 텍스트·이미지를 발행합니다.</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <Button onClick={connect} disabled={connecting} className="gap-2">
          {connecting && <Loader2 className="size-4 animate-spin" />}
          {facebook.length === 0 ? "Facebook 페이지 연결하기" : canMulti ? "+ Facebook 페이지 추가" : "다시 연결"}
        </Button>
        <p className="text-xs text-muted-foreground">
          연결에는 Facebook 앱 설정(서버 키)이 필요해요. 키 미설정 시 연결이 진행되지 않을 수 있어요.
          내가 <span className="font-medium">관리자인 페이지</span>가 채널로 등록됩니다. (개인 타임라인이 아닌 페이지)
          페이지에 연결된 <span className="font-medium">인스타그램 비즈니스 계정</span>도 함께 등록돼요(이미지 발행).
        </p>
        {canMulti && facebook.length > 0 && <MultiAccountHint sns="Facebook" />}
      </CardContent>
    </Card>
  );
}

/** Instagram 직접 연결 — "Instagram API with Instagram login"(페북 페이지 불필요). 비즈니스·크리에이터 계정. */
function InstagramCard() {
  const qc = useQueryClient();
  const canMulti = useCanMulti();
  const [connecting, setConnecting] = useState(false);

  const { data: channels } = useQuery({ queryKey: ["social-channels"], queryFn: socialApi.channels });
  const instagram = (channels ?? []).filter((c) => c.provider === "INSTAGRAM");

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["social-channels"] });
    qc.invalidateQueries({ queryKey: ["threads-accounts"] });
  };

  const connect = async () => {
    setConnecting(true);
    try {
      const { authorizeUrl } = await instagramApi.connectUrl();
      const w = 600;
      const h = 720;
      const left = window.screenX + (window.outerWidth - w) / 2;
      const top = window.screenY + (window.outerHeight - h) / 2;
      const popup = window.open(authorizeUrl, "instagram-oauth", `width=${w},height=${h},left=${left},top=${top}`);
      if (!popup) {
        window.location.href = authorizeUrl;
        return;
      }
      const onMessage = (e: MessageEvent) => {
        if (e.origin !== window.location.origin) return;
        if (e.data?.type === "instagram-oauth") {
          window.removeEventListener("message", onMessage);
          clearInterval(timer);
          setConnecting(false);
          invalidate();
        }
      };
      window.addEventListener("message", onMessage);
      const timer = setInterval(() => {
        if (popup.closed) {
          clearInterval(timer);
          window.removeEventListener("message", onMessage);
          setConnecting(false);
          invalidate();
        }
      }, 600);
    } catch {
      setConnecting(false);
    }
  };

  return (
    <Card className="mt-6">
      <CardHeader>
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-lg bg-gradient-to-tr from-[#feda75] via-[#d62976] to-[#4f5bd5] text-white">
            <Instagram className="size-5" />
          </div>
          <div className="flex-1">
            <CardTitle>Instagram 직접 연결</CardTitle>
            <CardDescription>페이스북 페이지 없이 인스타그램 계정으로 바로 연결해요. 이미지 게시물을 발행합니다.</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <Button onClick={connect} disabled={connecting} className="gap-2">
          {connecting && <Loader2 className="size-4 animate-spin" />}
          {instagram.length === 0 ? "Instagram 계정 연결하기" : canMulti ? "+ Instagram 계정 추가" : "다시 연결"}
        </Button>
        <p className="text-xs text-muted-foreground">
          <span className="font-medium">비즈니스·크리에이터(프로페셔널) 계정</span>만 연결돼요. 개인 계정은 인스타그램 앱에서 프로페셔널로 전환해 주세요.
          발행 시 <span className="font-medium">이미지가 반드시 필요</span>합니다(인스타 정책). 페이스북 페이지에 연결된 계정이면 Facebook 카드로도 함께 등록돼요.
        </p>
        {canMulti && instagram.length > 0 && <MultiAccountHint sns="Instagram" />}
      </CardContent>
    </Card>
  );
}

function Stat({ label, value }: { label: string; value: number | null }) {
  return (
    <div className="rounded-lg border border-border/60 px-2 py-1.5 text-center">
      <div className="text-sm font-semibold tabular-nums">
        {value != null ? <CountUp value={value} /> : "—"}
      </div>
      <div className="text-[11px] text-muted-foreground">{label}</div>
    </div>
  );
}

/** SNS별 아이콘·라벨·색 (연결된 계정 통합 섹션 + 각 카드 헤더 공용). */
const CHANNEL_META: Record<string, { label: string; Icon: typeof AtSign; chip: string }> = {
  THREADS: { label: "Threads", Icon: AtSign, chip: "bg-foreground text-background" },
  INSTAGRAM: { label: "Instagram", Icon: Instagram, chip: "bg-gradient-to-tr from-[#feda75] via-[#d62976] to-[#4f5bd5] text-white" },
  FACEBOOK: { label: "Facebook", Icon: Facebook, chip: "bg-[#1877f2] text-white" },
  LINKEDIN: { label: "LinkedIn", Icon: Linkedin, chip: "bg-[#0a66c2] text-white" },
  MASTODON: { label: "Mastodon", Icon: Globe, chip: "bg-[#6364ff] text-white" },
  BLUESKY: { label: "Bluesky", Icon: Cloud, chip: "bg-sky-500 text-white" },
};
const CHANNEL_ORDER = ["THREADS", "INSTAGRAM", "FACEBOOK", "LINKEDIN", "MASTODON", "BLUESKY"];

/**
 * 연결된 계정 — 전 SNS를 한 섹션에 SNS별로 그룹핑해 보여준다. 각 provider 카드는 '연결'만 담당하고
 * 실제 연결된 계정 목록·기본 설정·연결 해제는 전부 여기서 관리한다. Threads 계정은 상세 지표(팔로워·조회 등)를
 * threads-accounts에서 끌어와 함께 표시한다.
 */
function ConnectedChannelsCard() {
  const qc = useQueryClient();
  const confirm = useConfirm();
  const toast = useToast();
  const { data: channels } = useQuery({ queryKey: ["social-channels"], queryFn: socialApi.channels });
  const { data: threads } = useQuery({ queryKey: ["threads-accounts"], queryFn: threadsApi.accounts });
  // 각 SNS 인사이트(팔로워·게시물 등) — 해당 채널이 있을 때만. 실패해도 조용히 지표만 생략.
  const has = (p: string) => (channels ?? []).some((c) => c.provider === p);
  const { data: igInsights } = useQuery({
    queryKey: ["instagram-insights"], queryFn: instagramApi.insights, enabled: has("INSTAGRAM"), retry: false,
  });
  const { data: mastoInsights } = useQuery({
    queryKey: ["mastodon-insights"], queryFn: mastodonApi.insights, enabled: has("MASTODON"), retry: false,
  });
  const { data: bskyInsights } = useQuery({
    queryKey: ["bluesky-insights"], queryFn: blueskyApi.insights, enabled: has("BLUESKY"), retry: false,
  });

  // refetch까지 기다려야 스피너(useIsMutating)가 화면 갱신 끝날 때까지 유지됨.
  const invalidate = () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: ["social-channels"] }),
      qc.invalidateQueries({ queryKey: ["threads-accounts"] }),
      qc.invalidateQueries({ queryKey: ["threads-status"] }),
    ]);
  const setDefault = useMutation({
    mutationFn: (id: number) => socialApi.setDefault(id),
    meta: { loading: "기본 채널 설정 중…" },
    onSuccess: async () => { await invalidate(); toast.show("기본 채널로 설정했어요.", "success"); },
    onError: () => toast.show("설정에 실패했어요.", "error"),
  });
  const disconnect = useMutation({
    mutationFn: (id: number) => socialApi.disconnect(id),
    meta: { loading: "연결 해제 중…" },
    onSuccess: async () => { await invalidate(); toast.show("연결을 해제했어요.", "success"); },
    onError: () => toast.show("연결 해제에 실패했어요.", "error"),
  });
  const askDisconnect = async (name: string, id: number) => {
    const ok = await confirm({
      title: "채널 연결 해제",
      description: `${name} 연결을 해제할까요? 예약된 발행은 이 채널로 나가지 않아요.`,
      confirmText: "연결 해제",
      destructive: true,
    });
    if (ok) disconnect.mutate(id);
  };

  const list = channels ?? [];
  if (list.length === 0) return null;
  const threadsStats = new Map((threads ?? []).map((a) => [a.id, a] as const));
  const groups = CHANNEL_ORDER
    .map((provider) => ({ provider, items: list.filter((c) => c.provider === provider) }))
    .filter((g) => g.items.length > 0);

  return (
    <Card className="mt-6">
      <CardHeader>
        <CardTitle>연결된 계정</CardTitle>
        <CardDescription>SNS별로 묶여 있어요. 발행은 각 SNS의 기본 채널로 나갑니다.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {groups.map(({ provider, items }) => {
          const meta = CHANNEL_META[provider] ?? { label: provider, Icon: AtSign, chip: "bg-muted" };
          const Icon = meta.Icon;
          return (
            <div key={provider}>
              <div className="mb-2 flex items-center gap-2">
                <div className={`flex size-6 items-center justify-center rounded-md ${meta.chip}`}>
                  <Icon className="size-3.5" />
                </div>
                <span className="text-sm font-semibold">{meta.label}</span>
                <span className="text-xs text-muted-foreground">{items.length}개</span>
              </div>
              <ul className="divide-y divide-border/60 overflow-hidden rounded-lg border">
                {items.map((c) => {
                  const st = threadsStats.get(c.id);
                  // name 이 빈 문자열("")일 수 있어 ?? 대신 || — 표시이름 없으면 @username 으로 폴백.
                  const dname = c.name?.trim();
                  const uname = c.username?.trim();
                  const title = dname || (uname ? `@${uname}` : "이름 없음");
                  // 인사이트는 계정 단일 집계 — username/handle(대소문자 무시) 일치하는 행에 붙인다.
                  const lc = uname?.toLowerCase();
                  const ig = c.provider === "INSTAGRAM" && igInsights?.username?.toLowerCase() === lc ? igInsights : null;
                  const masto = c.provider === "MASTODON" && mastoInsights?.handle?.toLowerCase() === lc ? mastoInsights : null;
                  const bsky = c.provider === "BLUESKY" && bskyInsights?.handle?.toLowerCase() === lc ? bskyInsights : null;
                  return (
                    <li key={c.id} className="p-3">
                      <div className="flex items-center gap-3">
                        {c.profilePictureUrl ? (
                          <img src={c.profilePictureUrl} alt="" className="size-9 shrink-0 rounded-full object-cover" />
                        ) : (
                          <div className={`flex size-9 shrink-0 items-center justify-center rounded-full ${meta.chip}`}>
                            <Icon className="size-4" />
                          </div>
                        )}
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-1.5 text-sm font-medium">
                            <span className="truncate">{title}</span>
                            {c.isDefault && <Badge variant="success">기본</Badge>}
                          </div>
                          <div className="truncate text-xs text-muted-foreground">
                            {dname && uname ? `@${uname} · ` : ""}
                            {c.status === "RECONNECT_REQUIRED" ? "재연결 필요" : "연결됨"}
                          </div>
                        </div>
                        {!c.isDefault && (
                          <Button variant="ghost" size="sm" disabled={setDefault.isPending} onClick={() => setDefault.mutate(c.id)}>
                            {setDefault.isPending && setDefault.variables === c.id ? <Loader2 className="size-4 animate-spin" /> : "기본으로"}
                          </Button>
                        )}
                        <Button variant="ghost" size="sm" className="text-destructive" disabled={disconnect.isPending} onClick={() => askDisconnect(title, c.id)}>
                          {disconnect.isPending && disconnect.variables === c.id ? <Loader2 className="size-4 animate-spin" /> : "연결 해제"}
                        </Button>
                      </div>
                      {st && (
                        <>
                          <div className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-6">
                            <Stat label="팔로워" value={st.followersCount} />
                            <Stat label="조회" value={st.views} />
                            <Stat label="좋아요" value={st.likes} />
                            <Stat label="답글" value={st.replies} />
                            <Stat label="리포스트" value={st.reposts} />
                            <Stat label="인용" value={st.quotes} />
                          </div>
                          {(st.views != null || st.likes != null) && (
                            <p className="mt-2 text-[11px] text-muted-foreground">조회·좋아요·답글·리포스트·인용은 최근 30일 기준</p>
                          )}
                        </>
                      )}
                      {ig && (
                        <>
                          <div className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-5">
                            <Stat label="팔로워" value={ig.followers} />
                            <Stat label="팔로잉" value={ig.following} />
                            <Stat label="게시물" value={ig.posts} />
                            <Stat label="좋아요" value={ig.totalLikes} />
                            <Stat label="댓글" value={ig.totalComments} />
                          </div>
                          <p className="mt-2 text-[11px] text-muted-foreground">좋아요·댓글은 최근 게시물 {ig.sampledPosts}개 합계</p>
                        </>
                      )}
                      {masto && (
                        <>
                          <div className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-6">
                            <Stat label="팔로워" value={masto.followers} />
                            <Stat label="팔로잉" value={masto.following} />
                            <Stat label="게시물" value={masto.posts} />
                            <Stat label="좋아요" value={masto.totalFavourites} />
                            <Stat label="부스트" value={masto.totalReblogs} />
                            <Stat label="답글" value={masto.totalReplies} />
                          </div>
                          <p className="mt-2 text-[11px] text-muted-foreground">좋아요·부스트·답글은 최근 게시물 {masto.sampledPosts}개 합계</p>
                        </>
                      )}
                      {bsky && (
                        <>
                          <div className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-5">
                            <Stat label="팔로워" value={bsky.followers} />
                            <Stat label="게시물" value={bsky.posts} />
                            <Stat label="좋아요" value={bsky.totalLikes} />
                            <Stat label="리포스트" value={bsky.totalReposts} />
                            <Stat label="답글" value={bsky.totalReplies} />
                          </div>
                          <p className="mt-2 text-[11px] text-muted-foreground">좋아요·리포스트·답글은 최근 게시물 {bsky.sampledPosts}개 합계</p>
                        </>
                      )}
                    </li>
                  );
                })}
              </ul>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}
