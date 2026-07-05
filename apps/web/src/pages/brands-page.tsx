import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Megaphone, Pencil, Plus, Star, Trash2, X } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useConfirm } from "@/components/confirm-dialog";
import { PageLoading } from "@/components/page-loading";
import { brandApi, type Brand, type BrandInput } from "@/lib/brand-api";

const EMPTY: BrandInput = {
  name: "", description: "", audience: "", keyPoints: "", ctaText: "", url: "", isDefault: false,
};

export function BrandsPage() {
  const qc = useQueryClient();
  const confirm = useConfirm();
  const { data, isLoading } = useQuery({ queryKey: ["brands"], queryFn: brandApi.list });
  const [editing, setEditing] = useState<Brand | "new" | null>(null);

  const invalidate = () => qc.invalidateQueries({ queryKey: ["brands"] });
  const remove = useMutation({ mutationFn: (id: number) => brandApi.remove(id), onSuccess: invalidate });

  const brands = data ?? [];

  return (
    <div className="mx-auto w-full max-w-5xl px-6 py-8 lg:px-10">
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight">
            <Megaphone className="size-6 text-brand" /> 브랜드 / 홍보 제품
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            내 제품·서비스를 등록하면 생성 시 선택해 그 제품을 자연스럽게 홍보하는 글을 만들어요.
          </p>
        </div>
        {editing === null && (
          <Button className="gap-1.5" onClick={() => setEditing("new")}>
            <Plus className="size-4" /> 새 제품
          </Button>
        )}
      </div>

      {editing !== null && (
        <BrandForm
          initial={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => { invalidate(); setEditing(null); }}
        />
      )}

      {isLoading ? (
        <PageLoading />
      ) : brands.length === 0 && editing === null ? (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-center gap-3 py-14 text-center">
            <Megaphone className="size-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">등록한 제품이 없어요. 내 제품을 추가하면 홍보글을 자동 생성할 수 있어요.</p>
            <Button className="gap-1.5" onClick={() => setEditing("new")}><Plus className="size-4" /> 첫 제품 등록</Button>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {brands.map((b) => (
            <Card key={b.id} className="p-5">
              <div className="flex items-start justify-between gap-2">
                <div className="flex items-center gap-1.5 font-semibold">
                  {b.name}
                  {b.isDefault && <span className="inline-flex items-center gap-0.5 rounded-full bg-brand/10 px-2 py-0.5 text-[11px] text-brand"><Star className="size-3" /> 기본</span>}
                </div>
                <div className="flex gap-1">
                  <Button variant="ghost" size="icon" title="수정" onClick={() => setEditing(b)}><Pencil className="size-4" /></Button>
                  <Button variant="ghost" size="icon" title="삭제" className="text-destructive" disabled={remove.isPending}
                    onClick={async () => { if (await confirm({ title: "제품 삭제", description: `"${b.name}"을(를) 삭제할까요?`, confirmText: "삭제", destructive: true })) remove.mutate(b.id); }}>
                    <Trash2 className="size-4" />
                  </Button>
                </div>
              </div>
              {b.description && <p className="mt-1 text-sm text-muted-foreground">{b.description}</p>}
              <dl className="mt-3 space-y-1 text-xs text-muted-foreground">
                {b.audience && <div><span className="font-medium text-foreground/70">타깃</span> · {b.audience}</div>}
                {b.keyPoints && <div><span className="font-medium text-foreground/70">강점</span> · {b.keyPoints}</div>}
                {b.ctaText && <div><span className="font-medium text-foreground/70">CTA</span> · {b.ctaText}</div>}
                {b.url && <div className="truncate"><span className="font-medium text-foreground/70">링크</span> · {b.url}</div>}
              </dl>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

function BrandForm({ initial, onClose, onSaved }: { initial: Brand | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<BrandInput>(initial ? { ...initial } : EMPTY);
  const set = (k: keyof BrandInput, v: string | boolean) => setF((p) => ({ ...p, [k]: v }));
  const save = useMutation({
    mutationFn: () => (initial ? brandApi.update(initial.id, f) : brandApi.create(f)),
    onSuccess: onSaved,
  });

  return (
    <Card className="mb-6">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle>{initial ? "제품 수정" : "새 제품 등록"}</CardTitle>
            <CardDescription>구체적으로 적을수록 홍보글 품질이 올라가요.</CardDescription>
          </div>
          <Button variant="ghost" size="icon" onClick={onClose}><X className="size-4" /></Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="space-y-1.5">
          <Label>제품/서비스 이름 *</Label>
          <Input value={f.name} onChange={(e) => set("name", e.target.value)} placeholder="예: 북킵" />
        </div>
        <div className="space-y-1.5">
          <Label>한 줄 설명</Label>
          <Input value={f.description ?? ""} onChange={(e) => set("description", e.target.value)} placeholder="무엇을 파는지 — 예: 직장인용 자동 가계부 앱(카드 자동연동, 3초 기록)" />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label>타깃 고객</Label>
            <Input value={f.audience ?? ""} onChange={(e) => set("audience", e.target.value)} placeholder="예: 바쁜 2030 직장인" />
          </div>
          <div className="space-y-1.5">
            <Label>기본 CTA 문구</Label>
            <Input value={f.ctaText ?? ""} onChange={(e) => set("ctaText", e.target.value)} placeholder="예: 앱스토어에서 북킵 받기" />
          </div>
        </div>
        <div className="space-y-1.5">
          <Label>핵심 강점 (쉼표로 구분)</Label>
          <Textarea value={f.keyPoints ?? ""} onChange={(e) => set("keyPoints", e.target.value)} placeholder="예: 카드 자동연동, 3초 기록, 월간 리포트" rows={2} />
        </div>
        <div className="space-y-1.5">
          <Label>홍보 링크</Label>
          <Input value={f.url ?? ""} onChange={(e) => set("url", e.target.value)} placeholder="https://..." />
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" className="size-4 accent-[var(--brand)]" checked={f.isDefault} onChange={(e) => set("isDefault", e.target.checked)} />
          생성 시 기본으로 선택
        </label>
        <div className="flex justify-end gap-2 pt-1">
          <Button variant="outline" onClick={onClose}>취소</Button>
          <Button disabled={!f.name.trim() || save.isPending} onClick={() => save.mutate()} className="gap-1.5">
            {save.isPending && <Loader2 className="size-4 animate-spin" />} 저장
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
