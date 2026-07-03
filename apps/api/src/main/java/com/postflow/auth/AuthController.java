package com.postflow.auth;

import com.postflow.auth.dto.UserDto;
import com.postflow.billing.EntitlementService;
import com.postflow.user.User;
import com.postflow.user.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인은 synub-sso(통합계정)에서 처리한다 — 프론트가 SSO를 직접 호출해 토큰을 받고,
 * 이 서비스는 그 JWT를 <b>검증만</b> 한다(PRODUCT_REGISTRATION §7). 여기선 검증된 신원의
 * 로컬 프로필/구독 상태만 노출한다.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final EntitlementService entitlementService;

    public AuthController(UserService userService, EntitlementService entitlementService) {
        this.userService = userService;
        this.entitlementService = entitlementService;
    }

    /** Current user. Pulls the authoritative plan from billing entitlements (session refresh). */
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Long userId) {
        User user = userService.getById(userId);
        entitlementService.syncPlan(userId, user.getExternalId()); // billing = source of truth
        return UserDto.from(userService.getById(userId));
    }
}
