import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider, MutationCache } from "@tanstack/react-query";
import { ThemeProvider } from "@/components/theme-provider";
import { ConfirmProvider } from "@/components/confirm-dialog";
import { ToastProvider, getToast } from "@/components/toast";
import { GlobalLoadingBar } from "@/components/global-loading-bar";
import { initSentry, Sentry } from "@/lib/sentry";
import { ErrorFallback } from "@/components/error-fallback";
import App from "@/App";
import "@/index.css";

initSentry();

/**
 * 공통 처리 — mutation.meta로 로딩/성공/실패 토스트를 자동화(전 서비스 UX 표준).
 * 뮤테이션에 `meta: { loading, success, error }`만 붙이면 QueryClient가 알아서 토스트.
 * (native alert/confirm 금지 · 모든 비동기 로딩 표시 — PATTERNS 2026-07-05)
 */
type ToastMeta = { loading?: string; success?: string; error?: string; noInvalidate?: boolean };
const loadingIds = new Map<object, number>();

// eslint-disable-next-line prefer-const
let queryClient: QueryClient;

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
  // 공통: 모든 mutation 성공 후 관련 쿼리 refetch를 await → 화면 갱신 끝날 때까지 스피너 유지
  // (안 그러면 POST만 끝나고 스피너가 먼저 꺼진 뒤 화면이 뒤늦게 바뀜). meta.noInvalidate로 opt-out.
  onSettled: async (_data, err, _vars, _ctx, mutation) => {
    const m = mutation.options.meta as ToastMeta | undefined;
    if (!err && !m?.noInvalidate) {
      await queryClient.invalidateQueries();
    }
    const id = loadingIds.get(mutation);
    if (id != null) { getToast()?.dismiss(id); loadingIds.delete(mutation); }
  },
});

queryClient = new QueryClient({ mutationCache });

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <ToastProvider>
            <ConfirmProvider>
              <GlobalLoadingBar />
              <Sentry.ErrorBoundary fallback={({ resetError }) => <ErrorFallback onReset={resetError} />}>
                <App />
              </Sentry.ErrorBoundary>
            </ConfirmProvider>
          </ToastProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
);
