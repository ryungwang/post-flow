package com.postflow.user;

/** Per-plan limits & feature flags (single source of truth for gating). */
public final class PlanPolicy {

    private PlanPolicy() {
    }

    /** AI 생성 한도. FREE=총 10개(누적 무료체험), BASIC=월 50개, PRO=월 200개. '무제한' 아님(손익·어뷰징 관리). */
    public static int generationCap(Plan plan) {
        return switch (plan) {
            case FREE -> 10;
            case BASIC -> 50;
            case PRO -> 200;
        };
    }

    /** FREE는 누적(총) 한도, 유료(BASIC/PRO)는 월 한도. */
    public static boolean isLifetimeCap(Plan plan) {
        return plan == Plan.FREE;
    }

    /** 예약 발행 — PRO 전용(카탈로그). */
    public static boolean canSchedule(Plan plan) {
        return plan == Plan.PRO;
    }

    /** 시리즈 생성 — PRO 전용. */
    public static boolean canSeries(Plan plan) {
        return plan == Plan.PRO;
    }

    /** 다중 계정(채널) — PRO 전용(5개 채널). */
    public static boolean canMultiAccount(Plan plan) {
        return plan == Plan.PRO;
    }

    /** 성과 분석(분석 페이지) — PRO 전용(카탈로그). */
    public static boolean canAnalytics(Plan plan) {
        return plan == Plan.PRO;
    }

    /** 댓글 자동화 — PRO 전용. */
    public static boolean canAutomation(Plan plan) {
        return plan == Plan.PRO;
    }
}
