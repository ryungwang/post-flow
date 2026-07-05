import { Button } from "@/components/ui/button";

/**
 * 예기치 못한 렌더 오류 시 화면(흰 화면 대신) — 회사 로고 + 안내 + 다시 시도.
 * Sentry.ErrorBoundary의 fallback으로 사용. 오류는 Sentry로 자동 전송됨.
 */
export function ErrorFallback({ onReset }: { onReset: () => void }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-5 bg-background px-6 text-center">
      <img src="/synub-symbol-light.png" alt="Synub" className="size-12 dark:hidden" />
      <img src="/synub-symbol-dark.png" alt="Synub" className="hidden size-12 dark:block" />
      <div className="space-y-1.5">
        <h1 className="text-lg font-semibold">잠시 문제가 생겼어요</h1>
        <p className="text-sm text-muted-foreground">
          예기치 못한 오류가 발생했어요. 다시 시도해 주세요.
          <br />
          계속되면 잠시 후 새로고침해 주세요.
        </p>
      </div>
      <div className="flex gap-2">
        <Button onClick={onReset}>다시 시도</Button>
        <Button variant="outline" onClick={() => (window.location.href = "/")}>
          홈으로
        </Button>
      </div>
    </div>
  );
}
