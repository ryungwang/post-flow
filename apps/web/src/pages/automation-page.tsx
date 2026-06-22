import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, MessageSquareReply, Play, Plus, Trash2 } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { commentRulesApi, type CommentRule, type TestResult } from "@/lib/comment-rules-api";
import { cn } from "@/lib/utils";

export function AutomationPage() {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ["comment-rules"], queryFn: commentRulesApi.list });
  const rules = data ?? [];

  const [keyword, setKeyword] = useState("");
  const [template, setTemplate] = useState("관심 가져주셔서 감사해요! 여기서 받아보세요 👉 {link}");

  const invalidate = () => qc.invalidateQueries({ queryKey: ["comment-rules"] });

  const create = useMutation({
    mutationFn: () => commentRulesApi.create({ keyword, replyTemplate: template }),
    onSuccess: () => { setKeyword(""); invalidate(); },
  });
  const toggle = useMutation({
    mutationFn: (r: CommentRule) => commentRulesApi.update(r.id, { keyword: r.keyword, replyTemplate: r.replyTemplate, ctaLinkId: r.ctaLinkId, active: !r.active }),
    onSuccess: invalidate,
  });
  const remove = useMutation({
    mutationFn: (id: number) => commentRulesApi.remove(id),
    onSuccess: invalidate,
  });

  return (
    <div className="w-full px-6 py-7 lg:px-8 xl:px-10">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">댓글 자동화</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          게시물 댓글에 키워드가 달리면 자동으로 답글을 보냅니다. <code className="rounded bg-muted px-1">{"{link}"}</code> 는 추적 링크로 치환돼요.
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[360px_1fr]">
        {/* create */}
        <Card className="h-fit p-6">
          <h2 className="font-semibold">새 규칙</h2>
          <div className="mt-4 space-y-3">
            <div className="space-y-1.5">
              <Label>트리거 키워드</Label>
              <Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="예: CHECK" />
            </div>
            <div className="space-y-1.5">
              <Label>자동 답글 템플릿</Label>
              <Textarea rows={3} value={template} onChange={(e) => setTemplate(e.target.value)} />
            </div>
            <Button className="w-full gap-1.5" disabled={!keyword || !template || create.isPending} onClick={() => create.mutate()}>
              {create.isPending ? <Loader2 className="size-4 animate-spin" /> : <Plus className="size-4" />} 규칙 추가
            </Button>
            <p className="text-xs text-muted-foreground">전체 발행 게시물에 적용됩니다. (게시물 지정·추적 링크 연결은 곧 추가)</p>
          </div>
        </Card>

        {/* list */}
        <Card className="p-0">
          <div className="flex items-center justify-between border-b border-border/60 px-6 py-4">
            <h2 className="font-semibold">규칙 목록</h2>
            {!isLoading && <span className="text-sm text-muted-foreground">{rules.length}개</span>}
          </div>
          {isLoading ? (
            <div className="flex items-center justify-center gap-2 py-16 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" /> 불러오는 중…
            </div>
          ) : rules.length === 0 ? (
            <div className="flex flex-col items-center gap-3 py-16 text-center">
              <div className="flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
                <MessageSquareReply className="size-6" />
              </div>
              <p className="text-sm text-muted-foreground">아직 규칙이 없어요. 왼쪽에서 추가해 보세요.</p>
            </div>
          ) : (
            <ul className="divide-y divide-border/60">
              {rules.map((r) => (
                <RuleRow key={r.id} rule={r} onToggle={() => toggle.mutate(r)} onRemove={() => remove.mutate(r.id)} busy={toggle.isPending || remove.isPending} />
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}

function RuleRow({ rule, onToggle, onRemove, busy }: { rule: CommentRule; onToggle: () => void; onRemove: () => void; busy: boolean }) {
  const [sample, setSample] = useState("");
  const [result, setResult] = useState<TestResult | null>(null);
  const test = useMutation({
    mutationFn: () => commentRulesApi.test(rule.id, sample),
    onSuccess: setResult,
  });

  return (
    <li className="px-6 py-4">
      <div className="flex items-start gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <Badge variant="secondary">키워드: {rule.keyword}</Badge>
            <Badge variant={rule.active ? "success" : "secondary"}>{rule.active ? "활성" : "비활성"}</Badge>
          </div>
          <p className="mt-2 whitespace-pre-wrap text-sm text-muted-foreground">{rule.replyTemplate}</p>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <Button variant="ghost" size="sm" disabled={busy} onClick={onToggle}>{rule.active ? "끄기" : "켜기"}</Button>
          <Button variant="ghost" size="icon" title="삭제" disabled={busy} onClick={onRemove}><Trash2 className="size-4" /></Button>
        </div>
      </div>

      {/* test-match */}
      <div className="mt-3 flex items-center gap-2">
        <Input value={sample} onChange={(e) => setSample(e.target.value)} placeholder="댓글 예시로 매칭 테스트" className="h-9" />
        <Button variant="outline" size="sm" className="gap-1.5" disabled={!sample || test.isPending} onClick={() => test.mutate()}>
          {test.isPending ? <Loader2 className="size-4 animate-spin" /> : <Play className="size-4" />} 테스트
        </Button>
      </div>
      {result && (
        <div className={cn("mt-2 rounded-md border px-3 py-2 text-xs", result.matched ? "border-emerald-500/40 bg-emerald-500/5" : "border-border bg-muted/40")}>
          {result.matched ? (
            <>매칭됨 · 답글: <span className="font-medium text-foreground">{result.replyText}</span></>
          ) : (
            <span className="text-muted-foreground">매칭 안 됨 — 키워드가 포함되지 않았어요.</span>
          )}
        </div>
      )}
    </li>
  );
}
