package com.postflow.roi;

import com.postflow.roi.dto.RoiDashboardResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roi")
public class RoiController {

    private final RoiService roiService;

    public RoiController(RoiService roiService) {
        this.roiService = roiService;
    }

    @GetMapping("/dashboard")
    public RoiDashboardResponse dashboard(@AuthenticationPrincipal Long userId,
                                          @RequestParam(defaultValue = "0") int days) {
        return roiService.dashboard(userId, days);
    }
}
