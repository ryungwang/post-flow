// synub 통합 결제(빌링) 웹. 구독·결제·플랜 변경은 여기서. dev=:3100, prod=app.synub.io.
export const BILLING_WEB_URL =
  import.meta.env.VITE_BILLING_WEB_URL ?? "http://localhost:3100";

/** 내 구독 관리 페이지. */
export const BILLING_SUBSCRIPTIONS_URL = `${BILLING_WEB_URL}/subscriptions`;

/** 고객 문의 페이지(빌링 통합). 개인 이메일 대신 여기로 안내. */
export const BILLING_CONTACT_URL = `${BILLING_WEB_URL}/contact`;
