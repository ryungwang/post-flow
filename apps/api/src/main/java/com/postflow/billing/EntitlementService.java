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

/**
 * Pulls the authoritative subscription state from synub-billing (single source of truth) and
 * syncs it into the local plan cache used for gating. Called on login/session load.
 */
@Service
public class EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class);

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

    public record Entitlement(boolean active, String plan, String expiresAt) {
    }

    /** Fetch entitlement for an SSO user; null if billing is unreachable. */
    public Entitlement fetch(String externalId) {
        try {
            return billing.get()
                    .uri(uri -> uri.path("/entitlements")
                            .queryParam("service", serviceCode)
                            .queryParam("customer", externalId)
                            .build())
                    .header("X-Service-Key", serviceKey)
                    .retrieve()
                    .body(Entitlement.class);
        } catch (Exception e) {
            log.warn("Entitlement fetch failed for {}: {}", externalId, e.getMessage());
            return null;
        }
    }

    /** Pull entitlement and reconcile the local plan cache. No-op if billing is unreachable. */
    public void syncPlan(Long userId, String externalId) {
        if (externalId == null) {
            return;
        }
        Entitlement ent = fetch(externalId);
        if (ent == null) {
            return; // 조회 실패 → 로컬 캐시 유지(다음 로그인/웹훅에서 보정)
        }
        Plan plan = ent.active() ? Plan.fromBillingCode(ent.plan()) : Plan.FREE;
        Instant periodEnd = toInstant(ent.expiresAt());
        userService.applyEntitlement(userId, plan, periodEnd);
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
