import { Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * 전체 화면/페이지 로딩 인디케이터 — 밋밋한 스피너 대신 **브랜드 로고**(펄스 + 회전 링).
 * 전 서비스 UX 표준(PATTERNS 2026-07-05): 중앙 로더는 회사 로고를 쓴다.
 */
export function BrandLoader({ label, className }: { label?: string; className?: string }) {
  return (
    <div className={cn("flex flex-col items-center justify-center gap-3", className)}>
      <div className="relative flex size-14 items-center justify-center">
        {/* 회전 링 */}
        <span className="absolute inset-0 animate-spin rounded-full border-2 border-transparent border-t-[var(--brand)]" />
        {/* 로고(펄스) */}
        <span className="bg-brand-gradient shadow-brand flex size-9 animate-pulse items-center justify-center rounded-lg text-brand-foreground">
          <Sparkles className="size-5" />
        </span>
      </div>
      {label && <span className="text-sm text-muted-foreground">{label}</span>}
    </div>
  );
}
