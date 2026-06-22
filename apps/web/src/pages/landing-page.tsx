import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { CheckCircle2, Loader2, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { publicApi, type PublicCta } from "@/lib/public-api";

/** Public hosted lead-capture landing page (CTA short links in LEAD mode land here). */
export function LandingPage() {
  const { slug = "" } = useParams();
  const [cta, setCta] = useState<PublicCta | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  useEffect(() => {
    publicApi.cta(slug).then(setCta).catch(() => setNotFound(true));
  }, [slug]);

  const submit = async () => {
    if (!email) return;
    setSubmitting(true);
    try {
      const { destinationUrl } = await publicApi.submitLead(slug, email, name);
      setDone(true);
      setTimeout(() => {
        if (destinationUrl) window.location.href = destinationUrl;
      }, 1400);
    } catch {
      setSubmitting(false);
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-background px-4">
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="bg-brand-animated absolute -left-24 -top-24 size-96 rounded-full opacity-25 blur-3xl animate-float" />
        <div className="bg-brand-animated absolute -bottom-32 -right-24 size-[28rem] rounded-full opacity-20 blur-3xl animate-float" style={{ animationDelay: "2s" }} />
      </div>

      <div className="relative w-full max-w-md animate-scale-in">
        <div className="mb-6 flex items-center justify-center gap-2">
          <div className="bg-brand-gradient shadow-brand flex size-8 items-center justify-center rounded-lg text-brand-foreground">
            <Sparkles className="size-4" />
          </div>
          <span className="text-gradient-brand text-lg font-bold">PostFlow</span>
        </div>

        <div className="rounded-2xl border bg-card/80 p-7 shadow-xl backdrop-blur-sm">
          {notFound ? (
            <p className="py-8 text-center text-sm text-muted-foreground">링크를 찾을 수 없어요.</p>
          ) : !cta ? (
            <div className="flex justify-center py-8"><Loader2 className="size-5 animate-spin text-muted-foreground" /></div>
          ) : done ? (
            <div className="flex flex-col items-center gap-3 py-6 text-center">
              <CheckCircle2 className="size-10 text-emerald-500" />
              <p className="font-medium">신청 완료! 잠시 후 이동합니다…</p>
            </div>
          ) : (
            <>
              <h1 className="text-xl font-semibold tracking-tight">{cta.headline}</h1>
              {cta.label && <p className="mt-1.5 text-sm text-muted-foreground">{cta.label}</p>}
              <div className="mt-5 space-y-2.5">
                <Input placeholder="이름 (선택)" value={name} onChange={(e) => setName(e.target.value)} />
                <Input type="email" placeholder="이메일" value={email} onChange={(e) => setEmail(e.target.value)} onKeyDown={(e) => e.key === "Enter" && submit()} />
                <Button className="w-full" disabled={!email || submitting} onClick={submit}>
                  {submitting ? <Loader2 className="size-4 animate-spin" /> : "받아보기"}
                </Button>
              </div>
              <p className="mt-3 text-center text-[11px] text-muted-foreground">제출 시 마케팅 정보 수신에 동의하게 됩니다.</p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
