import * as Sentry from "@sentry/react";

/**
 * Sentry 초기화 — `VITE_SENTRY_DSN`이 설정된 경우에만 활성화(없으면 완전 no-op).
 * DSN은 배포 env로 주입한다(레포에 넣지 않음). 로컬/미설정 시 아무 것도 전송하지 않음.
 */
export function initSentry() {
  const dsn = import.meta.env.VITE_SENTRY_DSN as string | undefined;
  if (!dsn) return;
  Sentry.init({
    dsn,
    environment: import.meta.env.MODE,
    release: import.meta.env.VITE_APP_VERSION as string | undefined,
    integrations: [Sentry.browserTracingIntegration()],
    // 성능 트레이스는 10%만 샘플링(비용 절감). 필요 시 조정.
    tracesSampleRate: 0.1,
    // 민감정보 최소화
    sendDefaultPii: false,
  });
}

export { Sentry };
