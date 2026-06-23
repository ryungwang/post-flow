import { useEffect, useState } from "react";
import { Flame } from "lucide-react";
import { CountUp } from "@/components/count-up";
import { cn } from "@/lib/utils";

/** Attention-score badge (0-100): green ≥80, amber ≥60, rose below (needs work). */
export function ScoreBadge({ score, compact, className }: { score: number; compact?: boolean; className?: string }) {
  const tone =
    score >= 80
      ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
      : score >= 60
        ? "border-amber-500/40 bg-amber-500/10 text-amber-600 dark:text-amber-400"
        : "border-rose-500/40 bg-rose-500/10 text-rose-600 dark:text-rose-400";
  const fillTone = score >= 80 ? "bg-emerald-500/25" : score >= 60 ? "bg-amber-500/25" : "bg-rose-500/25";

  // animate the fill width from 0 → score% on mount (respects reduced-motion)
  const [w, setW] = useState(0);
  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setW(score);
      return;
    }
    const r = requestAnimationFrame(() => setW(score));
    return () => cancelAnimationFrame(r);
  }, [score]);

  return (
    <span
      title={`관심도 예측 점수 ${score}/100${score < 60 ? " · 개선 권장" : ""}`}
      className={cn(
        "relative inline-flex items-center gap-1 overflow-hidden rounded-full border px-2 py-0.5 text-xs font-medium tabular-nums",
        tone,
        className,
      )}
    >
      <span
        aria-hidden
        className={cn("absolute inset-y-0 left-0 rounded-full transition-[width] duration-700 ease-out", fillTone)}
        style={{ width: `${w}%` }}
      />
      <Flame className="relative z-10 size-3" />
      <span className="relative z-10">
        {compact ? <CountUp value={score} durationMs={700} /> : <>관심도 <CountUp value={score} durationMs={700} /></>}
      </span>
    </span>
  );
}
