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

    /** FREE=누적(총), 유료=이번 달 생성 수. */
    @Transactional(readOnly = true)
    public long usedGenerations(Long userId, Plan plan) {
        return PlanPolicy.isLifetimeCap(plan)
                ? aiGenerationRepository.countByUserId(userId)
                : aiGenerationRepository.countByUserIdAndCreatedAtAfter(userId, monthStart());
    }

    /** Throw if the user is at/over their generation cap (FREE 총 10개 / 유료 월 한도). */
    @Transactional(readOnly = true)
    public void assertCanGenerate(Long userId) {
        Plan plan = userService.getById(userId).getPlan();
        int cap = PlanPolicy.generationCap(plan);
        if (cap >= 0 && usedGenerations(userId, plan) >= cap) {
            throw new PlanLimitException(PlanPolicy.isLifetimeCap(plan)
                    ? "무료 체험 " + cap + "개를 모두 사용했어요. 구독하면 계속 생성할 수 있어요."
                    : "이번 달 생성 한도(" + cap + "개)를 모두 사용했어요. 업그레이드하면 더 만들 수 있어요.");
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
                           boolean canMultiAccount, boolean cancelScheduled, java.time.Instant currentPeriodEnd,
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
                user.isCancelScheduled(),
                user.getCurrentPeriodEnd(),
                PlanPolicy.isLifetimeCap(plan));
    }
}
