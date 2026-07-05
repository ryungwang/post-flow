import { useIsMutating } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { BrandLoader } from "@/components/brand-loader";

/**
 * 전역 로딩 오버레이 — 어떤 mutation이든 진행 중이면 **회사 로고 로더(BrandLoader)** 를 자동 표시.
 * 전 서비스 UX 표준(PATTERNS 2026-07-05): 모든 비동기 호출 로딩을 공통 처리 + 중앙 로더는 회사 로고.
 * 빠른 mutation 깜빡임 방지로 220ms 지연 후 표시.
 */
export function GlobalLoadingBar() {
  const mutating = useIsMutating() > 0;
  const [show, setShow] = useState(false);

  useEffect(() => {
    if (!mutating) { setShow(false); return; }
    const t = setTimeout(() => setShow(true), 220);
    return () => clearTimeout(t);
  }, [mutating]);

  if (!show) return null;
  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center bg-background/50 backdrop-blur-[1px]">
      <div className="rounded-2xl border bg-popover/90 px-8 py-7 shadow-xl">
        <BrandLoader />
      </div>
    </div>
  );
}
