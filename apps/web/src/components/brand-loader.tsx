import { cn } from "@/lib/utils";

/**
 * 전체 화면/페이지 로딩 인디케이터 — 밋밋한 스피너 대신 **회사(Synub) 로고**(펄스 + 회전 링).
 * 전 서비스 UX 표준(PATTERNS 2026-07-05): 중앙 로더는 **회사 로고**(각 제품 로고 아님).
 * 테마별 심볼 사용: 라이트=어두운 육각(`-light`), 다크=흰 육각(`-dark`).
 */
export function BrandLoader({ label, className }: { label?: string; className?: string }) {
  return (
    <div className={cn("flex flex-col items-center justify-center gap-3", className)}>
      <div className="relative flex size-14 items-center justify-center">
        {/* 회전 링 */}
        <span className="absolute inset-0 animate-spin rounded-full border-2 border-transparent border-t-[var(--brand)]" />
        {/* 회사 로고(펄스) — 테마별 심볼 */}
        <img src="/synub-symbol-light.png" alt="Synub" className="size-8 animate-pulse dark:hidden" />
        <img src="/synub-symbol-dark.png" alt="Synub" className="hidden size-8 animate-pulse dark:block" />
      </div>
      {label && <span className="text-sm text-muted-foreground">{label}</span>}
    </div>
  );
}
