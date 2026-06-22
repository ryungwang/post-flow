package com.postflow.roi.dto;

import java.util.List;

/**
 * ROI funnel + revenue/cost aggregate. Views come from Threads Insights; clicks/leads/
 * conversions/revenue/cost from our own tracking. {@code roiPercent} is null when no cost
 * is recorded.
 */
public record RoiDashboardResponse(
        long views,
        long clicks,
        long leads,
        long conversions,
        double revenue,
        double cost,
        double netRevenue,
        String currency,
        double ctr,
        double leadRate,
        double purchaseRate,
        double rpm,
        double revenuePerPost,
        Double roiPercent,
        List<PostRevenueDto> topByRevenue
) {
    public record PostRevenueDto(
            Long postId,
            String content,
            double revenue,
            long conversions,
            double cost,
            Double roiPercent
    ) {
    }
}
