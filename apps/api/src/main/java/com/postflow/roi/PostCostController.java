package com.postflow.roi;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/post-costs")
public class PostCostController {

    private final RoiService roiService;

    public PostCostController(RoiService roiService) {
        this.roiService = roiService;
    }

    public record CostRequest(
            @NotNull Long postId,
            @NotNull @PositiveOrZero BigDecimal amount,
            String currency,
            String note
    ) {
    }

    @PostMapping
    public Map<String, Object> set(@AuthenticationPrincipal Long userId, @RequestBody CostRequest req) {
        PostCost c = roiService.setCost(userId, req.postId(), req.amount(), req.currency(), req.note());
        return Map.of("id", c.getId(), "postId", c.getPostId(), "amount", c.getAmount());
    }
}
