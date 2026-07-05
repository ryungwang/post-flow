import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AtSign, Info, Loader2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { threadsApi } from "@/lib/threads-api";
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

export function ThreadsSettingsPage() {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ["threads-status"], queryFn: threadsApi.status });
  const [connecting, setConnecting] = useState(false);

  // If this page is loaded inside the OAuth popup (callback redirect), notify the opener & close.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const result = params.get("threads");
    if (result && window.opener) {
      window.opener.postMessage({ type: "threads-oauth", result }, window.location.origin);
      window.close();
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
        <h1 className="text-2xl font-semibold tracking-tight">Threads 연결</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Threads 계정을 연결하면 예약·자동 발행을 사용할 수 있어요.
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

      <AccountsCard onAdd={connect} adding={connecting} />
    </div>
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
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["threads-accounts"] });
    qc.invalidateQueries({ queryKey: ["threads-status"] });
  };
  const setDefault = useMutation({
    mutationFn: (id: number) => threadsApi.setDefault(id),
    onSuccess: () => { invalidate(); toast.show("기본 계정으로 설정했어요.", "success"); },
    onError: () => toast.show("설정에 실패했어요.", "error"),
  });
  const disconnect = useMutation({
    mutationFn: (id: number) => threadsApi.disconnect(id),
    onSuccess: () => { invalidate(); toast.show("연결을 해제했어요.", "success"); },
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
    const tid = toast.show("연결 해제 중…", "loading");
    disconnect.mutate(id, { onSettled: () => toast.dismiss(tid) });
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
                  <Button variant="ghost" size="sm" disabled={setDefault.isPending} onClick={() => setDefault.mutate(a.id)}>기본으로</Button>
                )}
                <Button variant="ghost" size="sm" className="text-destructive" disabled={disconnect.isPending} onClick={() => askDisconnect(a.username, a.id)}>연결 해제</Button>
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
