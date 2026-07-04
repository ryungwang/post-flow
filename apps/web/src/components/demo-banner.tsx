import { Sparkles } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/store/auth";
import { BILLING_WEB_URL } from "@/lib/billing-web";

/**
 * 체험(데모) 모드 배너 — 데모 계정 로그인 시 상단에 고정 노출.
 * 데모는 읽기전용(백엔드가 쓰기 차단)이라 "저장·발행 불가 + 구독하면 실제 이용" 안내.
 * 데모에서 빠져나갈 수 있게 "나가기"(로그아웃) 제공.
 */
export function DemoBanner() {
  const user = useAuth((s) => s.user);
  const clear = useAuth((s) => s.clear);
  const navigate = useNavigate();
  if (!user?.demo) return null;

  const exit = () => {
    clear();
    navigate("/login", { replace: true });
  };

  return (
    <div className="flex h-9 shrink-0 items-center justify-center gap-2 border-b border-amber-500/30 bg-amber-500/15 px-4 text-center text-xs font-medium text-amber-700 dark:text-amber-300">
      <Sparkles className="size-3.5 shrink-0" />
      <span>체험(데모) 모드입니다 — 둘러보기 전용이라 변경은 저장되지 않아요.</span>
      <a
        href={`${BILLING_WEB_URL}/products`}
        className="ml-1 rounded-full bg-amber-500/25 px-2.5 py-0.5 font-semibold underline-offset-2 transition-colors hover:bg-amber-500/40"
      >
        구독하고 시작하기
      </a>
      <button
        onClick={exit}
        className="rounded-full px-2.5 py-0.5 font-semibold underline underline-offset-2 transition-colors hover:bg-amber-500/25"
      >
        나가기
      </button>
    </div>
  );
}
