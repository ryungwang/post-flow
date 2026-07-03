package com.postflow.analytics;

import com.postflow.analytics.dto.AnalyticsDashboardResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public AnalyticsDashboardResponse dashboard(@AuthenticationPrincipal Long userId,
                                                @RequestParam(defaultValue = "0") int days) {
        return analyticsService.dashboard(userId, days);
    }
}
