import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronDown, HelpCircle, Mail, Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import { PageLoading } from "@/components/page-loading";
import { faqApi, type Faq } from "@/lib/faq-api";
import { BILLING_CONTACT_URL } from "@/lib/billing-web";
import { cn } from "@/lib/utils";

export function FaqPage() {
  const { data, isLoading } = useQuery({ queryKey: ["faqs"], queryFn: faqApi.list });
  const [q, setQ] = useState("");
  const [cat, setCat] = useState<string>("전체");
  const [open, setOpen] = useState<number | null>(null);

  const all = data ?? [];
  const cats = useMemo(() => {
    const counts = new Map<string, number>();
    for (const f of all) counts.set(f.category, (counts.get(f.category) ?? 0) + 1);
    return [{ name: "전체", count: all.length }, ...[...counts].map(([name, count]) => ({ name, count }))];
  }, [all]);

  const filtered = useMemo(() => {
    const query = q.trim().toLowerCase();
    return all.filter((f) => {
      if (cat !== "전체" && f.category !== cat) return false;
      if (query && !(f.question + " " + f.answer).toLowerCase().includes(query)) return false;
      return true;
    });
  }, [all, q, cat]);

  const groups = useMemo(() => {
    const m = new Map<string, Faq[]>();
    for (const f of filtered) {
      if (!m.has(f.category)) m.set(f.category, []);
      m.get(f.category)!.push(f);
    }
    return [...m.entries()];
  }, [filtered]);

  return (
    <div className="mx-auto w-full max-w-6xl px-6 py-8 lg:px-10">
      {/* hero */}
      <div className="mb-8 flex flex-col items-center text-center">
        <div className="bg-brand-gradient shadow-brand mb-4 flex size-14 items-center justify-center rounded-2xl text-brand-foreground">
          <HelpCircle className="size-7" />
        </div>
        <h1 className="text-3xl font-bold tracking-tight">무엇을 도와드릴까요?</h1>
        <p className="mt-2 text-sm text-muted-foreground">사용법과 자주 헷갈리는 부분을 모았어요. 키워드로 검색해 보세요.</p>
        <div className="relative mt-5 w-full max-w-xl">
          <Search className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="궁금한 내용을 검색…"
            className="h-12 rounded-full pl-11 shadow-sm"
          />
        </div>
      </div>

      <div className="grid gap-8 lg:grid-cols-[220px_1fr]">
        {/* category nav (sticky on desktop) */}
        <aside className="min-w-0 lg:sticky lg:top-20 lg:self-start">
          <div className="flex flex-wrap gap-2 lg:flex-col lg:flex-nowrap">
            {cats.map((c) => (
              <button
                key={c.name}
                onClick={() => setCat(c.name)}
                className={cn(
                  "flex shrink-0 items-center justify-between gap-2 rounded-xl px-3.5 py-2 text-sm transition-colors lg:shrink",
                  cat === c.name
                    ? "bg-brand/10 font-semibold text-foreground shadow-[inset_2px_0_0_0_var(--brand)] lg:shadow-[inset_3px_0_0_0_var(--brand)]"
                    : "text-muted-foreground hover:bg-accent/60 hover:text-foreground",
                )}
              >
                <span className="whitespace-nowrap">{c.name}</span>
                <span className="rounded-full bg-muted px-1.5 text-[11px] tabular-nums text-muted-foreground">{c.count}</span>
              </button>
            ))}
          </div>
        </aside>

        {/* content */}
        <div className="min-w-0">
          {isLoading ? (
            <PageLoading />
          ) : filtered.length === 0 ? (
            <div className="rounded-2xl border border-dashed py-16 text-center">
              <p className="text-sm text-muted-foreground">검색 결과가 없어요.</p>
              <a href={BILLING_CONTACT_URL} target="_blank" rel="noreferrer" className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-brand hover:underline">
                <Mail className="size-4" /> 직접 문의하기
              </a>
            </div>
          ) : (
            <div className="space-y-8">
              {groups.map(([category, items]) => (
                <section key={category}>
                  <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold">
                    <span className="bg-brand-gradient h-4 w-1 rounded-full" />
                    {category}
                    <span className="text-xs font-normal text-muted-foreground">{items.length}</span>
                  </h2>
                  <div className="space-y-2.5">
                    {items.map((f) => {
                      const isOpen = open === f.id;
                      return (
                        <div
                          key={f.id}
                          className={cn(
                            "rounded-2xl border transition-all",
                            isOpen ? "border-brand/40 bg-card shadow-brand" : "border-border/60 bg-card/50 hover:border-brand/30",
                          )}
                        >
                          <button
                            onClick={() => setOpen(isOpen ? null : f.id)}
                            className="flex w-full items-center gap-3 px-4 py-3.5 text-left"
                          >
                            <span className="bg-brand/12 flex size-6 shrink-0 items-center justify-center rounded-md text-xs font-bold text-brand">Q</span>
                            <span className="flex-1 text-sm font-medium">{f.question}</span>
                            <ChevronDown className={cn("size-4 shrink-0 text-muted-foreground transition-transform", isOpen && "rotate-180")} />
                          </button>
                          {isOpen && (
                            <div className="animate-fade-in px-4 pb-4 pl-[3.25rem]">
                              <div className="border-l-2 border-brand/30 pl-3 text-sm leading-relaxed text-muted-foreground whitespace-pre-line">
                                {f.answer}
                              </div>
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </section>
              ))}

              <div className="rounded-2xl border border-border/60 bg-muted/30 p-5 text-center">
                <p className="text-sm font-medium">원하는 답을 못 찾으셨나요?</p>
                <a href={BILLING_CONTACT_URL} target="_blank" rel="noreferrer" className="mt-1 inline-flex items-center gap-1 text-sm text-brand hover:underline">
                  <Mail className="size-4" /> 문의하기
                </a>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
