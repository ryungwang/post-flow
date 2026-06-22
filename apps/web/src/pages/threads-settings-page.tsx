import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { AtSign, Loader2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { threadsApi } from "@/lib/threads-api";

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
  const { data, isLoading } = useQuery({ queryKey: ["threads-status"], queryFn: threadsApi.status });
  const [connecting, setConnecting] = useState(false);

  const connect = async () => {
    setConnecting(true);
    try {
      const { authorizeUrl } = await threadsApi.connectUrl();
      window.location.href = authorizeUrl;
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
            연결에는 Threads 앱 설정(서버 키)이 필요합니다. 키 미설정 시 연결이 진행되지 않을 수 있어요.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
