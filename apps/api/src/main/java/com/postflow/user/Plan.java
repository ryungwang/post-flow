package com.postflow.user;

/**
 * Plans mirror the synub-billing catalog for service {@code threads}:
 * FREE(무구독) · BASIC(월 50개·1채널) · PRO(무제한·5채널·예약·분석).
 */
public enum Plan {
    FREE,
    BASIC,
    PRO;

    /**
     * Map a billing entitlement plan_code to a local Plan; unknown/none → FREE.
     *
     * <p>Billing encodes the billing cycle in the code ({@code pro} vs {@code pro_yearly}), but the
     * plan tier is the same either way — so the cycle suffix is stripped before matching. Without
     * this, every annual subscriber (and the complimentary developer subscription, which picks the
     * highest-amount plan = {@code pro_yearly}) would fall through to FREE.
     */
    public static Plan fromBillingCode(String code) {
        if (code == null) {
            return FREE;
        }
        String tier = code.toLowerCase()
                .replaceFirst("_(yearly|monthly|annual)$", "");
        return switch (tier) {
            case "basic" -> BASIC;
            case "pro" -> PRO;
            default -> FREE;
        };
    }
}
