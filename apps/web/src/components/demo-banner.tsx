import { Sparkles } from "lucide-react";
import { useAuth } from "@/store/auth";
import { BILLING_WEB_URL } from "@/lib/billing-web";

/**
 * 체험(데모) 모드 배너 — 데모 계정 로그인 시 상단에 고정 노출.
 * 데모는 읽기전용(백엔드가 쓰기 차단)이라 "저장·발행 불가 + 구독하면 실제 이용" 안내.
 */
export function DemoBanner() {
  const user = useAuth((s) => s.user);
  if (!user?.demo) return null;

  return (
    <div className="flex items-center justify-center gap-2 bg-brand-gradient px-4 py-2 text-center text-xs font-medium text-brand-foreground sm:text-sm">
      <Sparkles className="size-4 shrink-0" />
      <span>체험 모드로 둘러보는 중이에요 — 저장·발행은 되지 않아요.</span>
      <a
        href={`${BILLING_WEB_URL}/products`}
        className="ml-1 rounded-full bg-white/20 px-3 py-0.5 font-semibold underline-offset-2 transition-colors hover:bg-white/30"
      >
        구독하고 시작하기
      </a>
    </div>
  );
}
