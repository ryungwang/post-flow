import { useIsMutating } from "@tanstack/react-query";
import { cn } from "@/lib/utils";

/**
 * 전역 상단 로딩 바 — 어떤 mutation이든 진행 중이면 자동 표시(브랜드 그라디언트, 무한 슬라이드).
 * 전 서비스 UX 표준(PATTERNS 2026-07-05): "모든 비동기 호출에 로딩 표시"를 코드 0줄로 공통 처리.
 */
export function GlobalLoadingBar() {
  const active = useIsMutating() > 0;
  return (
    <div
      className={cn(
        "pointer-events-none fixed inset-x-0 top-0 z-[200] h-0.5 overflow-hidden transition-opacity duration-200",
        active ? "opacity-100" : "opacity-0",
      )}
      aria-hidden
    >
      <div className="bg-brand-gradient h-full w-1/3 animate-[loadingbar_1.1s_ease-in-out_infinite] rounded-full" />
    </div>
  );
}
