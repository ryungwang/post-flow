package com.postflow.instagram;

import com.postflow.instagram.dto.InstagramInsightsDto;
import com.postflow.instagram.dto.InstagramPostDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 인스타그램 전용 조회 엔드포인트(내 게시물·인사이트). 연결은 Facebook 연결 시 함께 등록된다. */
@RestController
@RequestMapping("/social/instagram")
public class InstagramController {

    private final InstagramReadService readService;

    public InstagramController(InstagramReadService readService) {
        this.readService = readService;
    }

    @GetMapping("/posts")
    public List<InstagramPostDto> posts(@AuthenticationPrincipal Long userId,
                                        @RequestParam(defaultValue = "25") int limit) {
        return readService.posts(userId, Math.min(Math.max(limit, 1), 50));
    }

    @GetMapping("/insights")
    public InstagramInsightsDto insights(@AuthenticationPrincipal Long userId) {
        return readService.insights(userId);
    }
}
