package com.postflow.user;

import com.postflow.aigeneration.AiGenerationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Plan limit enforcement + usage reporting. */
@Service
public class UsageService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserService userService;
    private final AiGenerationRepository aiGenerationRepository;

    public UsageService(UserService userService, AiGenerationRepository aiGenerationRepository) {
        this.userService = userService;
        this.aiGenerationRepository = aiGenerationRepository;
    }

    private Instant monthStart() {
        ZonedDateTime now = ZonedDateTime.now(KST);
        return now.withDayOfMonth(1).toLocalDate().atStartOfDay(KST).toInstant();
    }

    /** FREE=누적(총), 유료(BASIC/PRO)=이번 달 생성 수. */
    @Transactional(readOnly = true)
    public long usedGenerations(Long userId, Plan plan) {
        return PlanPolicy.isLifetimeCap(plan)
                ? aiGenerationRepository.countByUserId(userId)
                : aiGenerationRepository.countByUserIdAndCreatedAtAfter(userId, monthStart());
    }

    /**
     * 생성 가능 여부 강제 — 플랜별 캡(FREE 총10 / BASIC 월50 / PRO 월200). 백엔드 강제라
     * API 직접 호출 어뷰징도 월 한도로 차단된다. (PRO도 유한 → 원가·어뷰징 관리)
     */
    @Transactional(readOnly = true)
    public void assertCanGenerate(Long userId) {
        Plan plan = userService.getById(userId).getPlan();
        int cap = PlanPolicy.generationCap(plan);
        if (usedGenerations(userId, plan) >= cap) {
            throw new PlanLimitException(PlanPolicy.isLifetimeCap(plan)
                    ? "무료 체험 " + cap + "개를 모두 사용했어요. 구독하면 계속 생성할 수 있어요."
                    : "이번 달 생성 한도(" + cap + "개)를 모두 사용했어요. 업그레이드하면 더 만들 수 있어요.");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanAutomation(Long userId) {
        if (!PlanPolicy.canAutomation(userService.getById(userId).getPlan())) {
            throw new PlanLimitException("댓글 자동화는 Pro 플랜부터 사용할 수 있어요.");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanAnalytics(Long userId) {
        if (!PlanPolicy.canAnalytics(userService.getById(userId).getPlan())) {
            throw new PlanLimitException("성과 분석은 Pro 플랜부터 사용할 수 있어요.");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanSeries(Long userId) {
        if (!PlanPolicy.canSeries(userService.getById(userId).getPlan())) {
            throw new PlanLimitException("시리즈 생성은 Pro 플랜부터 사용할 수 있어요.");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanSchedule(Long userId) {
        if (!PlanPolicy.canSchedule(userService.getById(userId).getPlan())) {
            throw new PlanLimitException("예약 발행은 Pro 플랜부터 사용할 수 있어요.");
        }
    }

    public record UsageDto(String plan, long used, int limit, boolean canSchedule, boolean canSeries,
                           boolean canMultiAccount, boolean canAnalytics, boolean canAutomation,
                           boolean cancelScheduled, java.time.Instant currentPeriodEnd,
                           boolean lifetimeCap) {
    }

    @Transactional(readOnly = true)
    public UsageDto usage(Long userId) {
        var user = userService.getById(userId);
        Plan plan = user.getPlan();
        return new UsageDto(
                plan.name(),
                usedGenerations(userId, plan),
                PlanPolicy.generationCap(plan),
                PlanPolicy.canSchedule(plan),
                PlanPolicy.canSeries(plan),
                PlanPolicy.canMultiAccount(plan),
                PlanPolicy.canAnalytics(plan),
                PlanPolicy.canAutomation(plan),
                user.isCancelScheduled(),
                user.getCurrentPeriodEnd(),
                PlanPolicy.isLifetimeCap(plan));
    }
}
