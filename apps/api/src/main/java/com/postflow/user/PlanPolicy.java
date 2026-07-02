package com.postflow.user;

/** Per-plan limits & feature flags (single source of truth for gating). */
public final class PlanPolicy {

    private PlanPolicy() {
    }

    /** Monthly AI generation cap; -1 = unlimited. (catalog: BASIC 월 50개, PRO 무제한) */
    public static int monthlyGenerations(Plan plan) {
        return switch (plan) {
            case FREE -> 30;
            case BASIC -> 50;
            case PRO -> -1;
        };
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
}
