import { cn } from "@/lib/utils";

/**
 * 전체 화면/페이지 로딩 인디케이터 — 회사(Synub) 로고 + 확실히 보이는 회전 링.
 * 전 서비스 UX 표준(PATTERNS 2026-07-05): 중앙 로더는 회사 로고(각 제품 로고 아님).
 * 링 색은 인라인 스타일로 지정 — Tailwind `border-{color}` 단축과 `border-t-*`의 순서 충돌 회피.
 */
export function BrandLoader({ label, className }: { label?: string; className?: string }) {
  return (
    <div className={cn("flex flex-col items-center justify-center gap-3", className)}>
      <div className="relative flex size-16 items-center justify-center">
        {/* 회전 링 — 회색 트랙 + 브랜드 상단 아크 (인라인으로 확실히) */}
        <span
          className="absolute inset-0 animate-spin rounded-full"
          style={{
            border: "3px solid rgba(120,120,135,0.25)",
            borderTopColor: "var(--brand)",
          }}
        />
        {/* 회사 로고 — 테마별 심볼 */}
        <img src="/synub-symbol-light.png" alt="Synub" className="size-9 dark:hidden" />
        <img src="/synub-symbol-dark.png" alt="Synub" className="hidden size-9 dark:block" />
      </div>
      {label && <span className="text-sm text-muted-foreground">{label}</span>}
    </div>
  );
}
