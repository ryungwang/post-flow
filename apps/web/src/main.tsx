import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider, MutationCache } from "@tanstack/react-query";
import { ThemeProvider } from "@/components/theme-provider";
import { ConfirmProvider } from "@/components/confirm-dialog";
import { ToastProvider, getToast } from "@/components/toast";
import { GlobalLoadingBar } from "@/components/global-loading-bar";
import App from "@/App";
import "@/index.css";

/**
 * 공통 처리 — mutation.meta로 로딩/성공/실패 토스트를 자동화(전 서비스 UX 표준).
 * 뮤테이션에 `meta: { loading, success, error }`만 붙이면 QueryClient가 알아서 토스트.
 * (native alert/confirm 금지 · 모든 비동기 로딩 표시 — PATTERNS 2026-07-05)
 */
type ToastMeta = { loading?: string; success?: string; error?: string };
const loadingIds = new Map<object, number>();

const mutationCache = new MutationCache({
  onMutate: (_vars, mutation) => {
    const m = mutation.options.meta as ToastMeta | undefined;
    if (m?.loading) {
      const id = getToast()?.show(m.loading, "loading");
      if (id != null) loadingIds.set(mutation, id);
    }
  },
  onSuccess: (_data, _vars, _ctx, mutation) => {
    const m = mutation.options.meta as ToastMeta | undefined;
    if (m?.success) getToast()?.show(m.success, "success");
  },
  onError: (_err, _vars, _ctx, mutation) => {
    const m = mutation.options.meta as ToastMeta | undefined;
    if (m?.error) getToast()?.show(m.error, "error");
  },
  onSettled: (_data, _err, _vars, _ctx, mutation) => {
    const id = loadingIds.get(mutation);
    if (id != null) { getToast()?.dismiss(id); loadingIds.delete(mutation); }
  },
});

const queryClient = new QueryClient({ mutationCache });

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <ToastProvider>
            <ConfirmProvider>
              <GlobalLoadingBar />
              <App />
            </ConfirmProvider>
          </ToastProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
);
