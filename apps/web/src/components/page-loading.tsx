import { BrandLoader } from "@/components/brand-loader";

/**
 * 페이지/섹션 데이터 로딩(query isLoading) 공통 인디케이터 — 항상 **중앙 정렬 + 회사 로고 로더**.
 * 좌측/상단에 스피너가 뜨는 불일치 방지(전 서비스 UX 표준 PATTERNS 2026-07-05).
 */
export function PageLoading({ label = "불러오는 중…", className }: { label?: string; className?: string }) {
  return (
    <div className={"flex min-h-[220px] items-center justify-center " + (className ?? "")}>
      <BrandLoader label={label} />
    </div>
  );
}
