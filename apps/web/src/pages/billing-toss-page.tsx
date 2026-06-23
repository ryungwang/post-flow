import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Loader2, CreditCard } from "lucide-react";
import { billingApi } from "@/lib/billing-api";
import { getTossPayments } from "@/lib/toss";

/** Toss billing: register a card (requestBillingAuth) → confirm on callback → activate plan. */
export function BillingTossPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState("결제를 준비하고 있어요…");
  const ran = useRef(false);

  const plan = params.get("plan") ?? "";
  const authKey = params.get("authKey");

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;

    (async () => {
      try {
        // step 2: card registered → confirm + charge + activate
        if (authKey) {
          setStatus("결제를 확정하고 있어요…");
          await billingApi.tossConfirm(authKey, plan);
          navigate("/settings/account?upgraded=1", { replace: true });
          return;
        }
        // step 1a: if a billing key already exists, charge without re-entering the card
        const charge = await billingApi.tossCharge(plan);
        if (charge.charged) {
          navigate("/settings/account?upgraded=1", { replace: true });
          return;
        }

        // step 1b: no saved card → register one (server-provided customerKey so auth/charge match)
        const { clientKey, customerKey } = await billingApi.tossConfig();
        const tp = await getTossPayments(clientKey);
        const origin = window.location.origin;
        await tp.requestBillingAuth("카드", {
          customerKey,
          successUrl: `${origin}/billing/toss?plan=${encodeURIComponent(plan)}`,
          failUrl: `${origin}/billing/toss?fail=1`,
        });
      } catch (e) {
        setError(
          params.get("fail")
            ? "카드 등록이 취소되었어요."
            : e instanceof Error ? e.message : "결제 진행 중 오류가 발생했어요.",
        );
      }
    })();
  }, [authKey, plan, navigate, params]);

  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 px-6 text-center">
      <div className="bg-brand-gradient flex size-12 items-center justify-center rounded-2xl text-brand-foreground">
        <CreditCard className="size-6" />
      </div>
      {error ? (
        <>
          <p className="text-sm text-destructive">{error}</p>
          <button onClick={() => navigate("/settings/account")} className="text-sm text-brand hover:underline">
            플랜으로 돌아가기
          </button>
        </>
      ) : (
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> {status}
        </p>
      )}
    </div>
  );
}
