package com.postflow.social;

import com.postflow.bluesky.BlueskyConnectService;
import com.postflow.mastodon.MastodonConnectService;
import com.postflow.social.dto.ChannelDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cross-provider channel endpoints (multiplatform). Threads OAuth stays on /threads;
 * this hosts the unified channel list, Bluesky (app-password) connect, and generic
 * set-default / disconnect used by the connect UI and publish picker.
 */
@RestController
@RequestMapping("/social")
public class SocialController {

    private final SocialChannelService channelService;
    private final BlueskyConnectService blueskyConnectService;
    private final MastodonConnectService mastodonConnectService;

    public SocialController(SocialChannelService channelService,
                            BlueskyConnectService blueskyConnectService,
                            MastodonConnectService mastodonConnectService) {
        this.channelService = channelService;
        this.blueskyConnectService = blueskyConnectService;
        this.mastodonConnectService = mastodonConnectService;
    }

    /** All connected channels for the user, across every platform. */
    @GetMapping("/accounts")
    public List<ChannelDto> accounts(@AuthenticationPrincipal Long userId) {
        return channelService.list(userId);
    }

    /** Connect a Bluesky account with handle + app password (no OAuth). */
    @PostMapping("/bluesky/connect")
    public List<ChannelDto> connectBluesky(@AuthenticationPrincipal Long userId,
                                           @RequestBody BlueskyConnectRequest req) {
        blueskyConnectService.connect(userId, req.handle(), req.appPassword());
        return channelService.list(userId);
    }

    /** Connect a Mastodon account with an instance URL + personal access token (no OAuth). */
    @PostMapping("/mastodon/connect")
    public List<ChannelDto> connectMastodon(@AuthenticationPrincipal Long userId,
                                            @RequestBody MastodonConnectRequest req) {
        mastodonConnectService.connect(userId, req.instanceUrl(), req.accessToken());
        return channelService.list(userId);
    }

    @PostMapping("/accounts/{id}/default")
    public void setDefault(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        channelService.setDefault(userId, id);
    }

    @DeleteMapping("/accounts/{id}")
    public void disconnect(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        channelService.disconnect(userId, id);
    }

    public record BlueskyConnectRequest(String handle, String appPassword) {
    }

    public record MastodonConnectRequest(String instanceUrl, String accessToken) {
    }
}
