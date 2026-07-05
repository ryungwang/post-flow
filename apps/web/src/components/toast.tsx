import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";
import { CheckCircle2, Loader2, X, XCircle } from "lucide-react";
import { cn } from "@/lib/utils";

type ToastType = "success" | "error" | "loading" | "info";
type Toast = { id: number; message: string; type: ToastType };

type ToastApi = {
  /** 토스트 표시 → id 반환. */
  show: (message: string, type?: ToastType) => number;
  /** 기존 토스트를 갱신(예: loading → success). */
  update: (id: number, message: string, type: ToastType) => void;
  dismiss: (id: number) => void;
  /** Promise를 감싸 로딩→성공/실패 자동 전환. */
  promise: <T>(p: Promise<T>, msg: { loading: string; success: string; error: string }) => Promise<T>;
};

const ToastContext = createContext<ToastApi | null>(null);

/** React 밖(예: QueryClient MutationCache)에서 토스트를 쓰기 위한 싱글턴. ToastProvider가 등록. */
let toastSingleton: ToastApi | null = null;
export function getToast() {
  return toastSingleton;
}

/** 어디서든 `const toast = useToast(); toast.show("저장됨","success")`. */
export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used within ToastProvider");
  return ctx;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const seq = useRef(0);
  const timers = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());

  const dismiss = useCallback((id: number) => {
    setToasts((t) => t.filter((x) => x.id !== id));
    const tm = timers.current.get(id);
    if (tm) { clearTimeout(tm); timers.current.delete(id); }
  }, []);

  const autoHide = useCallback((id: number, type: ToastType) => {
    const tm = timers.current.get(id);
    if (tm) clearTimeout(tm);
    if (type !== "loading") {
      timers.current.set(id, setTimeout(() => dismiss(id), 3200));
    }
  }, [dismiss]);

  const show = useCallback((message: string, type: ToastType = "info") => {
    const id = ++seq.current;
    setToasts((t) => [...t, { id, message, type }]);
    autoHide(id, type);
    return id;
  }, [autoHide]);

  const update = useCallback((id: number, message: string, type: ToastType) => {
    setToasts((t) => t.map((x) => (x.id === id ? { ...x, message, type } : x)));
    autoHide(id, type);
  }, [autoHide]);

  const promise = useCallback(<T,>(p: Promise<T>, msg: { loading: string; success: string; error: string }) => {
    const id = show(msg.loading, "loading");
    return p.then(
      (v) => { update(id, msg.success, "success"); return v; },
      (e) => { update(id, msg.error, "error"); throw e; },
    );
  }, [show, update]);

  const api: ToastApi = { show, update, dismiss, promise };
  toastSingleton = api; // React 밖에서도 접근

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-[100] flex flex-col gap-2">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={cn(
              "pointer-events-auto flex min-w-[240px] max-w-sm items-center gap-2.5 rounded-xl border bg-popover px-4 py-3 text-sm shadow-lg",
              "animate-in slide-in-from-bottom-2 fade-in",
            )}
          >
            {t.type === "success" && <CheckCircle2 className="size-5 shrink-0 text-emerald-500" />}
            {t.type === "error" && <XCircle className="size-5 shrink-0 text-rose-500" />}
            {t.type === "loading" && <Loader2 className="size-5 shrink-0 animate-spin text-brand" />}
            {t.type === "info" && <span className="size-2 shrink-0 rounded-full bg-brand" />}
            <span className="flex-1">{t.message}</span>
            {t.type !== "loading" && (
              <button onClick={() => dismiss(t.id)} className="shrink-0 text-muted-foreground hover:text-foreground">
                <X className="size-4" />
              </button>
            )}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
