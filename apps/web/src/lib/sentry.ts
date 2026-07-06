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
    integrations: [
      Sentry.browserTracingIntegration(),
      // 세션 리플레이 — 에러 발생 시 직전 사용자 행동을 재생(디버깅). 텍스트·미디어 마스킹.
      Sentry.replayIntegration({ maskAllText: true, blockAllMedia: true }),
    ],
    // 성능 트레이스는 10%만 샘플링(비용 절감). 필요 시 조정.
    tracesSampleRate: 0.1,
    // 평상시 세션은 녹화 안 함(비용), 에러 난 세션만 100% 리플레이 캡처.
    replaysSessionSampleRate: 0,
    replaysOnErrorSampleRate: 1.0,
    // 민감정보 최소화
    sendDefaultPii: false,
  });
}

/** 로그인/로그아웃 시 에러에 사용자 식별자만 태깅(이메일·이름은 넣지 않음 — PII 최소화). */
export function setSentryUser(user: { id: number } | null) {
  Sentry.setUser(user ? { id: String(user.id) } : null);
}

export { Sentry };
