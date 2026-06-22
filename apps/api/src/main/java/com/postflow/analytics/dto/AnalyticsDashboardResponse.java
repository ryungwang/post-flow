package com.postflow.analytics.dto;

import java.util.List;

/**
 * Aggregated analytics for the current user (PRD → Analytics).
 * Metric names follow the Threads Insights API ({@code replies}, not "comments";
 * {@code engagementRate} is computed).
 */
public record AnalyticsDashboardResponse(
        long totalPosts,
        long publishedPosts,
        long scheduledPosts,
        long views,
        long likes,
        long replies,
        long reposts,
        long quotes,
        long shares,
        double engagementRate,
        List<TopPostDto> topPosts
) {
}
