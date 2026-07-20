package com.postflow.mastodon;

import com.postflow.mastodon.dto.MastodonInsightsDto;
import com.postflow.mastodon.dto.MastodonMentionDto;
import com.postflow.mastodon.dto.MastodonPostDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 마스토돈 전용 조회 엔드포인트(내 게시물·인사이트·멘션). 연결(/social/mastodon/connect)은 SocialController. */
@RestController
@RequestMapping("/social/mastodon")
public class MastodonController {

    private final MastodonReadService readService;

    public MastodonController(MastodonReadService readService) {
        this.readService = readService;
    }

    @GetMapping("/posts")
    public List<MastodonPostDto> posts(@AuthenticationPrincipal Long userId,
                                       @RequestParam(defaultValue = "25") int limit) {
        return readService.posts(userId, clamp(limit));
    }

    @GetMapping("/insights")
    public MastodonInsightsDto insights(@AuthenticationPrincipal Long userId) {
        return readService.insights(userId);
    }

    @GetMapping("/mentions")
    public List<MastodonMentionDto> mentions(@AuthenticationPrincipal Long userId,
                                             @RequestParam(defaultValue = "25") int limit) {
        return readService.mentions(userId, clamp(limit));
    }

    private static int clamp(int limit) {
        return Math.min(Math.max(limit, 1), 40);
    }
}
