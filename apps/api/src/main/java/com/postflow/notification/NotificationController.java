package com.postflow.notification;

import com.postflow.post.Post;
import com.postflow.post.PostRepository;
import com.postflow.post.PostStatus;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Derives actionable notifications from current state (no storage): failed publishes,
 * reconnect-required / expiring Threads tokens. Keyless.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final PostRepository postRepository;
    private final SocialAccountRepository socialAccountRepository;

    public NotificationController(PostRepository postRepository, SocialAccountRepository socialAccountRepository) {
        this.postRepository = postRepository;
        this.socialAccountRepository = socialAccountRepository;
    }

    public record NotificationDto(String type, String severity, String message, String link) {
    }

    @GetMapping
    public List<NotificationDto> list(@AuthenticationPrincipal Long userId) {
        List<NotificationDto> out = new ArrayList<>();
        List<Post> posts = postRepository.findByUserIdOrderByCreatedAtDesc(userId);

        long failed = posts.stream().filter(p -> p.getStatus() == PostStatus.FAILED).count();
        if (failed > 0) {
            out.add(new NotificationDto("publish_failed", "error",
                    "발행 실패한 게시물 " + failed + "건 — 확인이 필요해요.", "/content/library"));
        }
        long reconnect = posts.stream().filter(p -> p.getStatus() == PostStatus.RECONNECT_REQUIRED).count();
        if (reconnect > 0) {
            out.add(new NotificationDto("reconnect", "warning",
                    "Threads 재연결이 필요한 게시물 " + reconnect + "건이 있어요.", "/settings/threads"));
        }

        Instant soon = Instant.now().plus(7, ChronoUnit.DAYS);
        for (SocialAccount a : socialAccountRepository.findByUserIdAndProviderOrderByIdAsc(userId, SocialProvider.THREADS)) {
            String who = a.getUsername() != null ? "@" + a.getUsername() : "Threads";
            if (a.getStatus().name().equals("RECONNECT_REQUIRED") || a.getStatus().name().equals("EXPIRED")) {
                out.add(new NotificationDto("account", "warning", who + " 계정 재연결이 필요해요.", "/settings/threads"));
            } else if (a.getExpiresAt() != null && a.getExpiresAt().isBefore(soon)) {
                out.add(new NotificationDto("token", "warning", who + " 토큰이 곧 만료돼요. (자동 갱신 시도 중)", "/settings/threads"));
            }
        }
        return out;
    }
}
