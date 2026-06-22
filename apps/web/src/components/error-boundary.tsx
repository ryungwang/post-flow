import { Component, type ReactNode } from "react";
import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";

type Props = { children: ReactNode };
type State = { error: Error | null };

/** Catches render errors so one broken screen doesn't blank the whole app. */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  render() {
    if (this.state.error) {
      return (
        <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 px-6 text-center">
          <div className="flex size-12 items-center justify-center rounded-full bg-rose-500/10 text-rose-500">
            <AlertTriangle className="size-6" />
          </div>
          <div>
            <h2 className="text-lg font-semibold">화면을 불러오지 못했어요</h2>
            <p className="mt-1 text-sm text-muted-foreground">일시적인 오류일 수 있어요. 새로고침하거나 다시 시도해 주세요.</p>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => this.setState({ error: null })}>다시 시도</Button>
            <Button onClick={() => window.location.reload()}>새로고침</Button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
