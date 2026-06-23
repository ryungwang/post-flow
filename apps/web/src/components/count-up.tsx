import { useEffect, useRef, useState } from "react";

/** Animated number that counts up from 0 to {value}. Respects prefers-reduced-motion. */
export function CountUp({
  value,
  durationMs = 800,
  format = (n: number) => n.toLocaleString(),
  className,
}: {
  value: number;
  durationMs?: number;
  format?: (n: number) => string;
  className?: string;
}) {
  const [display, setDisplay] = useState(value);
  const raf = useRef<number | undefined>(undefined);

  useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduce || !Number.isFinite(value)) {
      setDisplay(value);
      return;
    }
    let startTs: number | undefined;
    const from = 0;
    const step = (ts: number) => {
      if (startTs === undefined) startTs = ts;
      const p = Math.min(1, (ts - startTs) / durationMs);
      const eased = 1 - Math.pow(1 - p, 3); // easeOutCubic
      setDisplay(from + (value - from) * eased);
      if (p < 1) raf.current = requestAnimationFrame(step);
    };
    raf.current = requestAnimationFrame(step);
    return () => {
      if (raf.current) cancelAnimationFrame(raf.current);
    };
  }, [value, durationMs]);

  return <span className={className}>{format(display)}</span>;
}
