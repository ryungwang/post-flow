import { Construction } from "lucide-react";
import { Card } from "@/components/ui/card";

export function PlaceholderPage({ title, description }: { title: string; description?: string }) {
  return (
    <div className="w-full px-6 py-7 lg:px-8 xl:px-10">
      <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
      {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
      <Card className="mt-6 flex flex-col items-center justify-center gap-3 px-6 py-20 text-center">
        <div className="flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
          <Construction className="size-6" />
        </div>
        <p className="text-sm text-muted-foreground">이 화면은 곧 제공될 예정이에요.</p>
      </Card>
    </div>
  );
}
