package com.postflow.user;

/**
 * Plans mirror the synub-billing catalog for service {@code threads}:
 * FREE(무구독) · BASIC(월 50개·1채널) · PRO(무제한·5채널·예약·분석).
 */
public enum Plan {
    FREE,
    BASIC,
    PRO;

    /** Map a billing entitlement plan_code (basic/pro) to a local Plan; unknown/none → FREE. */
    public static Plan fromBillingCode(String code) {
        if (code == null) {
            return FREE;
        }
        return switch (code.toLowerCase()) {
            case "basic" -> BASIC;
            case "pro" -> PRO;
            default -> FREE;
        };
    }
}
