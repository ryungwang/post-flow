import { Flame } from "lucide-react";
import { cn } from "@/lib/utils";

/** Attention-score badge (0-100): green ≥80, amber ≥60, rose below (needs work). */
export function ScoreBadge({ score, compact, className }: { score: number; compact?: boolean; className?: string }) {
  const tone =
    score >= 80
      ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
      : score >= 60
        ? "border-amber-500/40 bg-amber-500/10 text-amber-600 dark:text-amber-400"
        : "border-rose-500/40 bg-rose-500/10 text-rose-600 dark:text-rose-400";
  return (
    <span
      title={`관심도 예측 점수 ${score}/100${score < 60 ? " · 개선 권장" : ""}`}
      className={cn(
        "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium tabular-nums",
        tone,
        className,
      )}
    >
      <Flame className="size-3" /> {compact ? score : `관심도 ${score}`}
    </span>
  );
}
