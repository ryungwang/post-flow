package com.postflow.bluesky;

import com.postflow.bluesky.dto.BlueskyInsightsDto;
import com.postflow.bluesky.dto.BlueskyPostDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Bluesky 전용 조회 엔드포인트(내 게시물·인사이트). 연결(/social/bluesky/connect)은 SocialController. */
@RestController
@RequestMapping("/social/bluesky")
public class BlueskyController {

    private final BlueskyReadService readService;

    public BlueskyController(BlueskyReadService readService) {
        this.readService = readService;
    }

    @GetMapping("/posts")
    public List<BlueskyPostDto> posts(@AuthenticationPrincipal Long userId,
                                      @RequestParam(defaultValue = "25") int limit) {
        return readService.posts(userId, Math.min(Math.max(limit, 1), 100));
    }

    @GetMapping("/insights")
    public BlueskyInsightsDto insights(@AuthenticationPrincipal Long userId) {
        return readService.insights(userId);
    }
}
