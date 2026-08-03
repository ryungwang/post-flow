import { useState } from "react";
import { Loader2, Search, Zap } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useToast } from "@/components/toast";
import { ApiError } from "@/lib/api";
import { coupangApi, COUPANG_CATEGORIES, type CoupangProduct } from "@/lib/coupang-api";

type Mode = "goldbox" | "best" | "search";

/** 쿠팡 실데이터로 SNS 제휴 소재 소싱 — 골드박스(특가)·카테고리 베스트·키워드 검색. 상품 클릭 시 폼 자동 채움. */
export function CoupangSourcePanel({ onPick }: { onPick: (p: CoupangProduct) => void }) {
  const { show } = useToast();
  const [mode, setMode] = useState<Mode>("goldbox");
  const [cat, setCat] = useState(COUPANG_CATEGORIES[0].id);
  const [q, setQ] = useState("");
  const [loading, setLoading] = useState(false);
  const [items, setItems] = useState<CoupangProduct[]>([]);

  const run = async (m: Mode) => {
    setMode(m);
    if (m === "search" && !q.trim()) { show("검색어를 입력해 주세요.", "error"); return; }
    setLoading(true);
    try {
      const r =
        m === "goldbox" ? await coupangApi.goldbox() :
        m === "best" ? await coupangApi.best(cat) :
        await coupangApi.search(q.trim());
      setItems(r);
      if (r.length === 0) show("결과가 없어요.", "error");
    } catch (e) {
      show(e instanceof ApiError ? e.message : "쿠팡 상품 조회에 실패했어요.", "error");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-2.5 rounded-lg border border-border/60 p-3">
      <div className="flex items-center gap-1.5 text-sm font-medium">
        <Zap className="size-3.5 text-brand" /> 쿠팡에서 소재 가져오기{" "}
        <span className="font-normal text-muted-foreground">(실제 팔리는 상품 · 제휴 링크 자동)</span>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant={mode === "goldbox" ? "default" : "outline"} size="sm" onClick={() => run("goldbox")}>골드박스(특가)</Button>
        <Button type="button" variant={mode === "best" ? "default" : "outline"} size="sm" onClick={() => setMode("best")}>카테고리 베스트</Button>
        <Button type="button" variant={mode === "search" ? "default" : "outline"} size="sm" onClick={() => setMode("search")}>검색</Button>
      </div>

      {mode === "best" && (
        <div className="flex gap-2">
          <Select value={cat} onValueChange={setCat}>
            <SelectTrigger className="h-9"><SelectValue /></SelectTrigger>
            <SelectContent>{COUPANG_CATEGORIES.map((c) => <SelectItem key={c.id} value={c.id}>{c.label}</SelectItem>)}</SelectContent>
          </Select>
          <Button type="button" size="sm" onClick={() => run("best")} disabled={loading} className="shrink-0">불러오기</Button>
        </div>
      )}
      {mode === "search" && (
        <div className="flex gap-2">
          <Input placeholder="쿠팡 상품 검색어" value={q} autoCapitalize="none" onChange={(e) => setQ(e.target.value)} onKeyDown={(e) => e.key === "Enter" && run("search")} />
          <Button type="button" size="sm" onClick={() => run("search")} disabled={loading || !q.trim()} className="shrink-0 gap-1.5"><Search className="size-3.5" /> 검색</Button>
        </div>
      )}

      {loading && <p className="flex items-center gap-1.5 text-xs text-muted-foreground"><Loader2 className="size-3.5 animate-spin" /> 불러오는 중…</p>}
      {!loading && items.length > 0 && (
        <div className="max-h-72 space-y-1.5 overflow-y-auto">
          {items.map((p) => (
            <button
              key={p.productId}
              type="button"
              onClick={() => onPick(p)}
              className="flex w-full items-center gap-2.5 rounded-md border border-border/60 p-2 text-left transition-colors hover:bg-accent/40"
            >
              {p.productImage && <img src={p.productImage} alt="" className="size-11 shrink-0 rounded object-cover" />}
              <div className="min-w-0 flex-1">
                <p className="line-clamp-2 text-xs font-medium">{p.productName}</p>
                <div className="mt-0.5 flex flex-wrap items-center gap-1 text-[11px] text-muted-foreground">
                  {p.productPrice != null && <span className="font-medium text-foreground">{p.productPrice.toLocaleString()}원</span>}
                  {p.isRocket && <Badge variant="secondary" className="px-1 py-0 text-[10px]">로켓</Badge>}
                  {p.categoryName && <span>· {p.categoryName}</span>}
                </div>
              </div>
            </button>
          ))}
        </div>
      )}
      <p className="text-[11px] text-muted-foreground">상품을 누르면 제품명·제휴 링크가 자동으로 채워져요. (골드박스=오늘 특가 · 베스트=잘 팔리는 순 · 검색=키워드)</p>
    </div>
  );
}
