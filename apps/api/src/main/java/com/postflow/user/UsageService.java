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

    private Instant dayStart() {
        return ZonedDateTime.now(KST).toLocalDate().atStartOfDay(KST).toInstant();
    }

    /** FREE=누적(총), 유료=이번 달 생성 수. */
    @Transactional(readOnly = true)
    public long usedGenerations(Long userId, Plan plan) {
        return PlanPolicy.isLifetimeCap(plan)
                ? aiGenerationRepository.countByUserId(userId)
                : aiGenerationRepository.countByUserIdAndCreatedAtAfter(userId, monthStart());
    }

    /**
     * 생성 가능 여부 강제. 순서: ① 전 플랜 공통 일일 어뷰징 상한(하드 실링) → ② 플랜별 캡.
     * 백엔드 강제라 API 직접 호출 어뷰징도 차단된다.
     */
    @Transactional(readOnly = true)
    public void assertCanGenerate(Long userId) {
        Plan plan = userService.getById(userId).getPlan();
        int cap = PlanPolicy.generationCap(plan);
        if (cap < 0) {
            // 무제한 플랜(PRO)만: 공정사용 일일 상한으로 스크립트 무한호출 방어. FREE/BASIC은 아래 캡이 이미 낮음.
            long today = aiGenerationRepository.countByUserIdAndCreatedAtAfter(userId, dayStart());
            if (today >= PlanPolicy.UNLIMITED_DAILY_CAP) {
                throw new PlanLimitException("하루 생성 한도(" + PlanPolicy.UNLIMITED_DAILY_CAP
                        + "건)를 넘었어요. 잠시 후 다시 시도해 주세요.");
            }
            return;
        }
        // FREE 총 10개 / BASIC 월 50개.
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
