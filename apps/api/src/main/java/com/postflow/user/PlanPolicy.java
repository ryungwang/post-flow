package com.postflow.user;

/** Per-plan limits & feature flags (single source of truth for gating). */
public final class PlanPolicy {

    private PlanPolicy() {
    }

    /** Monthly AI generation cap; -1 = unlimited. */
    public static int monthlyGenerations(Plan plan) {
        return switch (plan) {
            case FREE -> 30;
            case STARTER -> 300;
            case PRO, BUSINESS -> -1;
        };
    }

    /** 예약 발행 — Starter 이상. */
    public static boolean canSchedule(Plan plan) {
        return plan.ordinal() >= Plan.STARTER.ordinal();
    }

    /** 시리즈 생성 — Pro 이상. */
    public static boolean canSeries(Plan plan) {
        return plan.ordinal() >= Plan.PRO.ordinal();
    }

    /** 다중 계정 — Business 전용. */
    public static boolean canMultiAccount(Plan plan) {
        return plan.ordinal() >= Plan.BUSINESS.ordinal();
    }
}
