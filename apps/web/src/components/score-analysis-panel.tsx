import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { BarChart3, Lightbulb, Loader2 } from "lucide-react";
import { contentApi, type ScoreAnalysis } from "@/lib/content-api";

/** Collapsible attention-score breakdown + improvement tips (reused on cards & detail modal). */
export function ScoreAnalysisPanel({
  content,
  hashtags,
  cta,
  score,
}: {
  content: string;
  hashtags: string[];
  cta: string | null;
  score: number;
}) {
  const [analysis, setAnalysis] = useState<ScoreAnalysis | null>(null);
  const analyze = useMutation({
    mutationFn: () => contentApi.score(content, hashtags, cta),
    onSuccess: setAnalysis,
  });

  return (
    <div className="rounded-lg border border-border/60 p-3">
      {analysis ? (
        <>
          <div className="mb-2 flex items-center gap-1.5 text-sm font-medium">
            <BarChart3 className="size-4 text-brand" /> 관심도 분석 · {analysis.total}/100
          </div>
          <div className="space-y-1.5">
            {analysis.components.map((c) => (
              <div key={c.label} className="flex items-center gap-2 text-xs">
                <span className="w-14 shrink-0 text-muted-foreground">{c.label}</span>
                <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                  <div className="bg-brand-gradient h-full rounded-full" style={{ width: `${(c.score / c.max) * 100}%` }} />
                </div>
                <span className="w-10 shrink-0 text-right tabular-nums text-muted-foreground">{c.score}/{c.max}</span>
              </div>
            ))}
          </div>
          <ul className="mt-3 space-y-1">
            {analysis.tips.map((t, i) => (
              <li key={i} className="flex items-start gap-1.5 text-xs text-muted-foreground">
                <Lightbulb className="mt-0.5 size-3 shrink-0 text-amber-500" /> {t}
              </li>
            ))}
          </ul>
        </>
      ) : (
        <button
          className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
          disabled={analyze.isPending}
          onClick={() => analyze.mutate()}
        >
          {analyze.isPending ? <Loader2 className="size-4 animate-spin" /> : <BarChart3 className="size-4" />}
          관심도 분석 보기 (왜 {score}점인지 + 올리는 법)
        </button>
      )}
    </div>
  );
}
