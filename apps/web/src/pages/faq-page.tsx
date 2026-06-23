import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronDown, HelpCircle, Loader2, Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import { faqApi, type Faq } from "@/lib/faq-api";
import { cn } from "@/lib/utils";

export function FaqPage() {
  const { data, isLoading } = useQuery({ queryKey: ["faqs"], queryFn: faqApi.list });
  const [q, setQ] = useState("");
  const [cat, setCat] = useState<string>("전체");
  const [open, setOpen] = useState<number | null>(null);

  const all = data ?? [];
  const categories = useMemo(() => ["전체", ...Array.from(new Set(all.map((f) => f.category)))], [all]);

  const filtered = useMemo(() => {
    const query = q.trim().toLowerCase();
    return all.filter((f) => {
      if (cat !== "전체" && f.category !== cat) return false;
      if (query && !(f.question + " " + f.answer).toLowerCase().includes(query)) return false;
      return true;
    });
  }, [all, q, cat]);

  // group filtered by category, preserving order
  const groups = useMemo(() => {
    const m = new Map<string, Faq[]>();
    for (const f of filtered) {
      if (!m.has(f.category)) m.set(f.category, []);
      m.get(f.category)!.push(f);
    }
    return [...m.entries()];
  }, [filtered]);

  return (
    <div className="mx-auto w-full max-w-3xl px-6 py-7">
      <div className="mb-6">
        <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight">
          <HelpCircle className="size-6 text-brand" /> 자주 묻는 질문
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">사용법과 자주 헷갈리는 부분을 모았어요. 검색해 보세요.</p>
      </div>

      <div className="relative mb-4">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="궁금한 내용을 검색…" className="h-11 pl-9" />
      </div>

      <div className="mb-5 flex flex-wrap gap-1.5">
        {categories.map((c) => (
          <button
            key={c}
            onClick={() => setCat(c)}
            className={cn(
              "rounded-full border px-3 py-1 text-xs transition-colors",
              cat === c ? "border-brand/50 bg-brand/10 font-medium text-foreground" : "text-muted-foreground hover:bg-accent/60",
            )}
          >
            {c}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> 불러오는 중…
        </div>
      ) : filtered.length === 0 ? (
        <p className="py-16 text-center text-sm text-muted-foreground">검색 결과가 없어요. 다른 키워드로 찾아보거나 deerkrg@gmail.com 으로 문의해 주세요.</p>
      ) : (
        <div className="space-y-6">
          {groups.map(([category, items]) => (
            <div key={category}>
              <h2 className="mb-2 px-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{category}</h2>
              <div className="overflow-hidden rounded-xl border border-border/60">
                {items.map((f) => (
                  <div key={f.id} className="border-b border-border/60 last:border-0">
                    <button
                      onClick={() => setOpen(open === f.id ? null : f.id)}
                      className="flex w-full items-center justify-between gap-3 px-4 py-3.5 text-left text-sm font-medium transition-colors hover:bg-accent/40"
                    >
                      {f.question}
                      <ChevronDown className={cn("size-4 shrink-0 text-muted-foreground transition-transform", open === f.id && "rotate-180")} />
                    </button>
                    {open === f.id && (
                      <div className="animate-fade-in whitespace-pre-line px-4 pb-4 text-sm leading-relaxed text-muted-foreground">
                        {f.answer}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
