import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AtSign, Cloud, Globe, Info, Linkedin, Loader2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { threadsApi } from "@/lib/threads-api";
import { linkedinApi } from "@/lib/linkedin-api";
import { accountApi } from "@/lib/account-api";
import { socialApi } from "@/lib/social-api";
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

export function ThreadsSettingsPage() {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ["threads-status"], queryFn: threadsApi.status });
  const [connecting, setConnecting] = useState(false);

  // If this page is loaded inside the OAuth popup (callback redirect), notify the opener & close.
  // Threads(?threads=) and LinkedIn(?linkedin=) share this settings page as their frontend redirect.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    for (const provider of ["threads", "linkedin"] as const) {
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
          SNS 채널을 연결하면 한 번 만든 콘텐츠를 예약·자동 발행할 수 있어요. (Threads · Bluesky · LinkedIn · Mastodon)
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
            {connected ? "다시 연결" : "Threads 연결하기"}
          </Button>
          <p className="mt-3 text-xs text-muted-foreground">
            {connected
              ? "다른 계정으로 바꾸려면 아래에서 연결 해제 후 다시 연결하세요. (다른 계정으로 인증하면 현재 연결이 교체됩니다.)"
              : "연결에는 Threads 앱 설정(서버 키)이 필요합니다. 키 미설정 시 연결이 진행되지 않을 수 있어요."}
          </p>
        </CardContent>
      </Card>

      <BlueskyCard />

      <LinkedInCard />

      <MastodonCard />

      <AccountsCard onAdd={connect} adding={connecting} />
    </div>
  );
}

/** Bluesky 연결 — OAuth 아님, 핸들 + 앱 비밀번호. 세션 토큰만 저장(앱 비번 미저장). */
function BlueskyCard() {
  const qc = useQueryClient();
  const { show } = useToast();
  const confirm = useConfirm();
  const [handle, setHandle] = useState("");
  const [appPassword, setAppPassword] = useState("");

  const { data: channels } = useQuery({ queryKey: ["social-channels"], queryFn: socialApi.channels });
  const bluesky = (channels ?? []).filter((c) => c.provider === "BLUESKY");

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

  const disconnect = useMutation({
    mutationFn: (id: number) => socialApi.disconnect(id),
    meta: { loading: "연결 해제 중…", success: "연결 해제됨", error: "연결 해제 실패" },
    onSuccess: invalidate,
  });

  const askDisconnect = async (username: string | null, id: number) => {
    const ok = await confirm({
      title: "채널 연결 해제",
      description: `${username ?? "이 계정"} 연결을 해제할까요? 예약된 발행은 이 채널로 나가지 않아요.`,
      confirmText: "연결 해제",
      destructive: true,
    });
    if (ok) disconnect.mutate(id);
  };

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
        {bluesky.length > 0 && (
          <div className="space-y-2">
            {bluesky.map((c) => (
              <div key={c.id} className="flex items-center gap-3 rounded-lg border p-3">
                <Cloud className="size-4 text-sky-500" />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium">@{c.username}</div>
                  <div className="text-xs text-muted-foreground">
                    {c.status === "RECONNECT_REQUIRED" ? "재연결 필요" : "연결됨"}
                    {c.isDefault && " · 기본 채널"}
                  </div>
                </div>
                <Button variant="ghost" size="sm" onClick={() => askDisconnect(c.username, c.id)}>
                  연결 해제
                </Button>
              </div>
            ))}
          </div>
        )}
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
        </p>
      </CardContent>
    </Card>
  );
}

/** LinkedIn 연결 — OAuth2(팝업). 발행 전용(개인 프로필 읽기/분석은 파트너 승인 필요). */
function LinkedInCard() {
  const qc = useQueryClient();
  const confirm = useConfirm();
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

  const disconnect = useMutation({
    mutationFn: (id: number) => socialApi.disconnect(id),
    meta: { loading: "연결 해제 중…", success: "연결 해제됨", error: "연결 해제 실패" },
    onSuccess: invalidate,
  });

  const askDisconnect = async (username: string | null, id: number) => {
    const ok = await confirm({
      title: "채널 연결 해제",
      description: `${username ?? "이 계정"} 연결을 해제할까요? 예약된 발행은 이 채널로 나가지 않아요.`,
      confirmText: "연결 해제",
      destructive: true,
    });
    if (ok) disconnect.mutate(id);
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
        {linkedin.length > 0 && (
          <div className="space-y-2">
            {linkedin.map((c) => (
              <div key={c.id} className="flex items-center gap-3 rounded-lg border p-3">
                <Linkedin className="size-4 text-[#0a66c2]" />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium">{c.name ?? c.username}</div>
                  <div className="text-xs text-muted-foreground">
                    {c.status === "RECONNECT_REQUIRED" ? "재연결 필요" : "연결됨"}
                    {c.isDefault && " · 기본 채널"}
                  </div>
                </div>
                <Button variant="ghost" size="sm" onClick={() => askDisconnect(c.name ?? c.username, c.id)}>
                  연결 해제
                </Button>
              </div>
            ))}
          </div>
        )}
        <Button onClick={connect} disabled={connecting} className="gap-2">
          {connecting && <Loader2 className="size-4 animate-spin" />}
          {linkedin.length > 0 ? "다시 연결" : "LinkedIn 연결하기"}
        </Button>
        <p className="text-xs text-muted-foreground">
          연결에는 LinkedIn 앱 설정(서버 키)이 필요해요. 키 미설정 시 연결이 진행되지 않을 수 있어요.
          개인 프로필 피드에 텍스트·이미지를 발행합니다.
        </p>
      </CardContent>
    </Card>
  );
}

/** Mastodon 연결 — OAuth 아님, 인스턴스 주소 + 액세스 토큰. 토큰만 저장(무료·심사 없음). */
function MastodonCard() {
  const qc = useQueryClient();
  const { show } = useToast();
  const confirm = useConfirm();
  const [instanceUrl, setInstanceUrl] = useState("");
  const [accessToken, setAccessToken] = useState("");

  const { data: channels } = useQuery({ queryKey: ["social-channels"], queryFn: socialApi.channels });
  const mastodon = (channels ?? []).filter((c) => c.provider === "MASTODON");

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

  const disconnect = useMutation({
    mutationFn: (id: number) => socialApi.disconnect(id),
    meta: { loading: "연결 해제 중…", success: "연결 해제됨", error: "연결 해제 실패" },
    onSuccess: invalidate,
  });

  const askDisconnect = async (username: string | null, id: number) => {
    const ok = await confirm({
      title: "채널 연결 해제",
      description: `${username ?? "이 계정"} 연결을 해제할까요? 예약된 발행은 이 채널로 나가지 않아요.`,
      confirmText: "연결 해제",
      destructive: true,
    });
    if (ok) disconnect.mutate(id);
  };

  const submit = () => {
    if (!instanceUrl.trim() || !accessToken.trim()) {
      show("인스턴스 주소와 액세스 토큰을 입력해 주세요.", "error");
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
            <CardDescription>인스턴스 주소와 액세스 토큰으로 연결해요. (무료 · 심사 없음)</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {mastodon.length > 0 && (
          <div className="space-y-2">
            {mastodon.map((c) => (
              <div key={c.id} className="flex items-center gap-3 rounded-lg border p-3">
                <Globe className="size-4 text-[#6364ff]" />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium">@{c.username}</div>
                  <div className="text-xs text-muted-foreground">
                    {c.status === "RECONNECT_REQUIRED" ? "재연결 필요" : "연결됨"}
                    {c.isDefault && " · 기본 채널"}
                  </div>
                </div>
                <Button variant="ghost" size="sm" onClick={() => askDisconnect(c.username, c.id)}>
                  연결 해제
                </Button>
              </div>
            ))}
          </div>
        )}
        <div className="grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
          <Input
            placeholder="인스턴스 (예: mastodon.social)"
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
          내 인스턴스 → <span className="font-medium">설정 → 개발 → 새 애플리케이션</span>에서 만든 앱의{" "}
          <span className="font-medium">액세스 토큰</span>을 넣으세요. (권한: <code>write</code> 포함)
          토큰만 보관하며, 텍스트·이미지를 발행합니다.
        </p>
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

function AccountsCard({ onAdd, adding }: { onAdd: () => void; adding: boolean }) {
  const qc = useQueryClient();
  const confirm = useConfirm();
  const toast = useToast();
  const { data: accounts } = useQuery({ queryKey: ["threads-accounts"], queryFn: threadsApi.accounts });
  const { data: usage } = useQuery({ queryKey: ["account", "usage"], queryFn: accountApi.usage });
  // refetch까지 기다려야 스피너(useIsMutating)가 화면 갱신 끝날 때까지 유지됨(안 그러면 스피너 먼저 꺼짐).
  const invalidate = () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: ["threads-accounts"] }),
      qc.invalidateQueries({ queryKey: ["threads-status"] }),
    ]);
  // 로딩 토스트 = meta(전역 MutationCache가 표시·해제) — 계정 행이 언마운트돼도 확실히 닫힘.
  const setDefault = useMutation({
    mutationFn: (id: number) => threadsApi.setDefault(id),
    meta: { loading: "기본 계정 설정 중…" },
    onSuccess: async () => { await invalidate(); toast.show("기본 계정으로 설정했어요.", "success"); },
    onError: () => toast.show("설정에 실패했어요.", "error"),
  });
  const onSetDefault = (id: number) => setDefault.mutate(id);
  const disconnect = useMutation({
    mutationFn: (id: number) => threadsApi.disconnect(id),
    meta: { loading: "연결 해제 중…" },
    onSuccess: async () => { await invalidate(); toast.show("연결을 해제했어요.", "success"); },
    onError: () => toast.show("연결 해제에 실패했어요.", "error"),
  });
  const askDisconnect = async (username: string, id: number) => {
    const ok = await confirm({
      title: "연결 해제",
      description: `@${username} 연결을 해제할까요? 해제 후 다른 Threads 계정으로 다시 연결할 수 있어요.`,
      confirmText: "연결 해제",
      destructive: true,
    });
    if (!ok) return;
    disconnect.mutate(id);
  };
  const list = accounts ?? [];
  if (list.length === 0) return null;

  return (
    <Card className="mt-6">
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <div>
            <CardTitle>연결된 계정</CardTitle>
            <CardDescription>발행은 기본 계정으로 나갑니다.</CardDescription>
          </div>
          {usage?.canMultiAccount && (
            <Button variant="outline" size="sm" disabled={adding} onClick={onAdd}>+ 계정 추가</Button>
          )}
        </div>
      </CardHeader>
      <CardContent>
        <ul className="divide-y divide-border/60">
          {list.map((a) => (
            <li key={a.id} className="py-4">
              <div className="flex items-center gap-3">
                {a.profilePictureUrl ? (
                  <img src={a.profilePictureUrl} alt="" className="size-12 shrink-0 rounded-full object-cover" />
                ) : (
                  <div className="bg-brand-gradient flex size-12 shrink-0 items-center justify-center rounded-full text-base font-semibold text-brand-foreground">
                    {(a.name ?? a.username).charAt(0).toUpperCase()}
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-1.5 text-sm font-medium">
                    {a.name ?? `@${a.username}`}
                    {a.isDefault && <Badge variant="success">기본</Badge>}
                  </div>
                  <div className="flex items-center gap-2 text-xs text-muted-foreground">
                    {a.name && <span>@{a.username}</span>}
                    <span>· {a.status}</span>
                  </div>
                  {a.biography && <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{a.biography}</p>}
                </div>
                {!a.isDefault && (
                  <Button variant="ghost" size="sm" disabled={setDefault.isPending} onClick={() => onSetDefault(a.id)}>
                    {setDefault.isPending && setDefault.variables === a.id ? <Loader2 className="size-4 animate-spin" /> : "기본으로"}
                  </Button>
                )}
                <Button variant="ghost" size="sm" className="text-destructive" disabled={disconnect.isPending} onClick={() => askDisconnect(a.username, a.id)}>
                  {disconnect.isPending && disconnect.variables === a.id ? <Loader2 className="size-4 animate-spin" /> : "연결 해제"}
                </Button>
              </div>
              <div className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-6">
                <Stat label="팔로워" value={a.followersCount} />
                <Stat label="조회" value={a.views} />
                <Stat label="좋아요" value={a.likes} />
                <Stat label="답글" value={a.replies} />
                <Stat label="리포스트" value={a.reposts} />
                <Stat label="인용" value={a.quotes} />
              </div>
              {(a.views != null || a.likes != null) && (
                <p className="mt-2 text-[11px] text-muted-foreground">조회·좋아요·답글·리포스트·인용은 최근 30일 기준</p>
              )}
            </li>
          ))}
        </ul>
        {!usage?.canMultiAccount && list.length >= 1 && (
          <p className="mt-3 text-xs text-muted-foreground">여러 채널을 동시에 연결하려면 <b className="text-foreground/70">Pro 플랜</b>이 필요해요.</p>
        )}
        {usage?.canMultiAccount && (
          <div className="mt-3 flex items-start gap-2 rounded-lg border border-border/60 bg-muted/40 px-3 py-2.5 text-xs text-muted-foreground">
            <Info className="mt-0.5 size-3.5 shrink-0" />
            <span>
              <b className="text-foreground/80">다른 계정을 추가하려면?</b> Threads는 브라우저에 로그인된 계정으로 연결돼요.
              먼저 <a href="https://www.threads.net/settings/account" target="_blank" rel="noreferrer" className="text-brand underline">threads.net에서 로그아웃</a>하거나
              <b> 시크릿 창</b>에서 "+ 계정 추가"를 누르면 다른 계정으로 로그인할 수 있어요.
            </span>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
