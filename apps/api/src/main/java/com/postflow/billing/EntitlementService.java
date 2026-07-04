package com.postflow.billing;

import com.postflow.user.Plan;
import com.postflow.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * synub-billing(구독의 단일 진실)에서 컨텍스트 인지 entitlement를 조회해 로컬 플랜 캐시에 동기화한다.
 * post-flow는 개인 제품(org_only=false) — 기본 컨텍스트 personal, 조직 구독이 있으면 스위처에 노출.
 *
 * <p>빌링 API는 <b>/api</b> 프리픽스를 쓴다({@code /api/entitlements}, {@code /api/contexts}) — post-flow 자체 API가
 * /api를 안 쓰는 것과 별개(빌링은 다른 서비스). 인증은 서버-투-서버 헤더 {@code X-Service-Key}.
 */
@Service
public class EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class);

    /** post-flow 기본 컨텍스트 — 개인 제품이라 personal. */
    public static final String DEFAULT_CONTEXT = "personal";

    private final UserService userService;
    private final RestClient billing;
    private final String serviceKey;
    private final String serviceCode;

    public EntitlementService(UserService userService,
                              @Value("${synub.billing.base-url}") String billingBaseUrl,
                              @Value("${synub.billing.service-key}") String serviceKey,
                              @Value("${synub.billing.service-code:post-flow}") String serviceCode) {
        this.userService = userService;
        this.billing = RestClient.create(billingBaseUrl);
        this.serviceKey = serviceKey;
        this.serviceCode = serviceCode;
    }

    /** 컨텍스트 스코프 entitlement 응답(빌링). */
    public record Entitlement(boolean active, String plan, String expiresAt, List<String> features, String orgCode) {
    }

    /** 컨텍스트 하나(개인/조직) — 스위처 소스. */
    public record Context(String type, String context, String orgCode, String name, String role) {
    }

    private record ContextsResponse(String customer, List<Context> contexts) {
    }

    /** 사용자 컨텍스트 목록(개인 + 소속 조직). 실패 시 개인 컨텍스트만. */
    public List<Context> listContexts(String externalId) {
        try {
            ContextsResponse res = billing.get()
                    .uri(uri -> uri.path("/api/contexts").queryParam("customer", externalId).build())
                    .header("X-Service-Key", serviceKey)
                    .retrieve()
                    .body(ContextsResponse.class);
            if (res != null && res.contexts() != null && !res.contexts().isEmpty()) {
                return res.contexts();
            }
        } catch (Exception e) {
            log.warn("Contexts fetch failed for {}: {}", externalId, e.getMessage());
        }
        return List.of(new Context("personal", DEFAULT_CONTEXT, null, "개인", null));
    }

    /** 지정 컨텍스트로 entitlement 조회. null이면 빌링 미도달. */
    public Entitlement fetch(String externalId, String context) {
        try {
            return billing.get()
                    .uri(uri -> {
                        uri.path("/api/entitlements")
                                .queryParam("service", serviceCode)
                                .queryParam("customer", externalId);
                        if (context != null && !context.isBlank()) {
                            uri.queryParam("context", context);
                        }
                        return uri.build();
                    })
                    .header("X-Service-Key", serviceKey)
                    .retrieve()
                    .body(Entitlement.class);
        } catch (Exception e) {
            log.warn("Entitlement fetch failed for {} ({}): {}", externalId, context, e.getMessage());
            return null;
        }
    }

    /**
     * 선택 컨텍스트의 entitlement를 로컬 플랜 캐시에 반영. 빌링 = 단일 진실:
     * active면 그 플랜, 비활성이면 FREE로 강제 동기화(자체 기본값이 빌링을 덮지 않게).
     * 빌링 미도달(null)일 때만 로컬 캐시 유지.
     */
    public void syncPlan(Long userId, String externalId, String context) {
        if (externalId == null) {
            return;
        }
        Entitlement ent = fetch(externalId, context == null ? DEFAULT_CONTEXT : context);
        if (ent == null) {
            return; // 조회 실패 → 로컬 캐시 유지(다음 로그인/웹훅에서 보정)
        }
        Plan plan = ent.active() ? Plan.fromBillingCode(ent.plan()) : Plan.FREE;
        userService.applyEntitlement(userId, plan, toInstant(ent.expiresAt()));
    }

    private Instant toInstant(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return null;
        }
    }
}
