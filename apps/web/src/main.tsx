import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider, MutationCache } from "@tanstack/react-query";
import { ThemeProvider } from "@/components/theme-provider";
import { ConfirmProvider } from "@/components/confirm-dialog";
import { ToastProvider, getToast } from "@/components/toast";
import { GlobalLoadingBar } from "@/components/global-loading-bar";
import { ApiError } from "@/lib/api";
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
  // 사용자가 고칠 수 있는 실패(4xx — 플랜 한도·토큰 오류 등)는 서버가 보낸 이유를 그대로 보여준다.
  // 고정 문구만 띄우면 "왜 실패했는지"가 사라지기 때문. 단 5xx·네트워크 오류는 서버 문구가
  // 기술적이라("Internal Server Error") 사용자에게 의미가 없으므로 meta.error를 쓴다.
  onError: (err, _vars, _ctx, mutation) => {
    const m = mutation.options.meta as ToastMeta | undefined;
    const actionable =
      err instanceof ApiError && err.fromServer && err.status >= 400 && err.status < 500;
    const text = (actionable ? err.message : null) ?? m?.error;
    if (text) getToast()?.show(text, "error");
  },
  // 공통: 모든 mutation 성공 후 관련 쿼리 refetch를 await → 화면 갱신 끝날 때까지 스피너 유지
  // (안 그러면 POST만 끝나고 스피너가 먼저 꺼진 뒤 화면이 뒤늦게 바뀜). meta.noInvalidate로 opt-out.
  onSettled: async (_data, err, _vars, _ctx, mutation) => {
    const m = mutation.options.meta as ToastMeta | undefined;
    try {
      if (!err && !m?.noInvalidate) await queryClient.invalidateQueries();
    } finally {
      // refetch가 실패/지연돼도 로딩 토스트는 반드시 닫는다(스피너 잔존 방지).
      const id = loadingIds.get(mutation);
      if (id != null) { getToast()?.dismiss(id); loadingIds.delete(mutation); }
    }
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
