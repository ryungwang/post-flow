package com.postflow.account;

import com.postflow.user.UsageService;
import com.postflow.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Account settings: webhook secret + plan usage. */
@RestController
@RequestMapping("/account")
public class AccountController {

    private final UserService userService;
    private final UsageService usageService;
    private final String apiBaseUrl;

    public AccountController(UserService userService,
                            UsageService usageService,
                            @Value("${roi.short-link-base-url:http://localhost:8080}") String apiBaseUrl) {
        this.userService = userService;
        this.usageService = usageService;
        this.apiBaseUrl = apiBaseUrl;
    }

    @GetMapping("/usage")
    public UsageService.UsageDto usage(@AuthenticationPrincipal Long userId) {
        return usageService.usage(userId);
    }

    @GetMapping("/webhook")
    public Map<String, String> webhook(@AuthenticationPrincipal Long userId) {
        return response(userService.getOrCreateWebhookSecret(userId));
    }

    @PostMapping("/webhook/regenerate")
    public Map<String, String> regenerate(@AuthenticationPrincipal Long userId) {
        return response(userService.regenerateWebhookSecret(userId));
    }

    private Map<String, String> response(String secret) {
        return Map.of("secret", secret, "endpoint", apiBaseUrl + "/webhooks/conversions");
    }
}
