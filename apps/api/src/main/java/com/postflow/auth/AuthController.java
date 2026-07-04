package com.postflow.auth;

import com.postflow.auth.dto.UserDto;
import com.postflow.billing.EntitlementService;
import com.postflow.demo.DemoContentSeeder;
import com.postflow.user.User;
import com.postflow.user.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 로그인은 synub-sso(통합계정)에서 처리한다 — 프론트가 SSO를 직접 호출해 토큰을 받고,
 * 이 서비스는 그 JWT를 <b>검증만</b> 한다(PRODUCT_REGISTRATION §7). 여기선 검증된 신원의
 * 로컬 프로필/구독 상태만 노출한다. 구독 플랜은 synub-billing 컨텍스트 인지 entitlement가 진실.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final EntitlementService entitlementService;
    private final DemoContentSeeder demoContentSeeder;

    public AuthController(UserService userService, EntitlementService entitlementService,
                          DemoContentSeeder demoContentSeeder) {
        this.userService = userService;
        this.entitlementService = entitlementService;
        this.demoContentSeeder = demoContentSeeder;
    }

    /**
     * Current user. 선택 컨텍스트(개인/조직)의 빌링 entitlement로 플랜을 동기화해 반환.
     * context 미지정 시 개인(personal). 데모 계정은 빌링 조회 없이 로컬 상태만.
     */
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Long userId,
                      @RequestParam(name = "context", required = false) String context) {
        User user = userService.getById(userId);
        if (!user.isDemo()) {
            entitlementService.syncPlan(userId, user.getExternalId(), context); // billing = source of truth
        } else {
            demoContentSeeder.ensureSeeded(userId); // 데모 계정이면 샘플 콘텐츠 1회 시드(멱등)
        }
        return UserDto.from(userService.getById(userId));
    }

    /** 사용자의 컨텍스트 목록(개인 + 소속 조직) — 진입/스위처 소스. */
    @GetMapping("/contexts")
    public List<EntitlementService.Context> contexts(@AuthenticationPrincipal Long userId) {
        User user = userService.getById(userId);
        if (user.isDemo() || user.getExternalId() == null) {
            return List.of(new EntitlementService.Context("personal", "personal", null, "개인", null));
        }
        return entitlementService.listContexts(user.getExternalId());
    }
}
