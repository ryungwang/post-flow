import { useIsMutating } from "@tanstack/react-query";
import { BrandLoader } from "@/components/brand-loader";

/**
 * 전역 로딩 오버레이 — 어떤 mutation이든 진행 중이면 **회사 로고 로더로 화면 전체를 막는다**.
 * 전 서비스 UX 표준(PATTERNS 2026-07-05): 모든 비동기 호출은 blocking 로딩 + 중앙 회사 로고.
 * 지연/깜빡임 걱정 없이 mutation 시작 즉시 표시(더블클릭·중복요청도 차단).
 */
export function GlobalLoadingBar() {
  const mutating = useIsMutating() > 0;
  if (!mutating) return null;
  return (
    <div className="fixed inset-0 z-[300] flex items-center justify-center bg-background/60 backdrop-blur-[2px]">
      <BrandLoader />
    </div>
  );
}
