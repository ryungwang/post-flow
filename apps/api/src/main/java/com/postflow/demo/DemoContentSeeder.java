package com.postflow.demo;

import com.postflow.analytics.AnalyticsRepository;
import com.postflow.analytics.PostAnalytics;
import com.postflow.brand.Brand;
import com.postflow.brand.BrandRepository;
import com.postflow.post.Post;
import com.postflow.post.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 데모(체험) 계정에 둘러볼 샘플 콘텐츠를 채워준다 — 브랜드 1 + 발행/예약/초안 포스트 + 지표.
 * 로그인(/auth/me) 시 데모 유저가 비어있을 때만 1회 시드(멱등). 데모는 read-only라 이후 그대로 유지된다.
 */
@Service
public class DemoContentSeeder {

    private final PostRepository postRepository;
    private final AnalyticsRepository analyticsRepository;
    private final BrandRepository brandRepository;

    public DemoContentSeeder(PostRepository postRepository, AnalyticsRepository analyticsRepository,
                             BrandRepository brandRepository) {
        this.postRepository = postRepository;
        this.analyticsRepository = analyticsRepository;
        this.brandRepository = brandRepository;
    }

    /** 데모 유저가 콘텐츠가 없으면 샘플을 시드한다(멱등). */
    @Transactional
    public void ensureSeeded(Long userId) {
        if (!postRepository.findByUserIdOrderByCreatedAtDesc(userId).isEmpty()) {
            return; // 이미 시드됨
        }
        if (brandRepository.findByUserIdOrderByIdAsc(userId).isEmpty()) {
            brandRepository.save(Brand.create(userId, "무디 캔들",
                    "천연 소이왁스로 만든 핸드메이드 향초 브랜드",
                    "20~30대 자취·1인가구, 홈카페·무드등 관심층",
                    "천연 소이왁스 · 12시간 지속 · 무해 발향 · 리필 가능",
                    "지금 첫 구매 15% 할인 받기", "https://example.com/moody", true));
        }

        Instant now = Instant.now();

        // 발행완료 2건 (+지표)
        Post p1 = published(userId,
                "향초 하나 켰을 뿐인데 방 공기가 바뀌었어요 🌿\n주말 홈카페, 무드등만으로는 2% 부족했던 이유.",
                List.of("홈카페", "무드등", "핸드메이드향초"), "첫 구매 15% 할인 →");
        Post p2 = published(userId,
                "\"향이 진짜 12시간 가요?\" 가장 많이 받은 질문.\n소이왁스라 그을음 없이 은은하게, 끝까지 같은 향으로.",
                List.of("소이왁스", "향초추천", "자취템"), "성분표 보러가기 →");
        postRepository.saveAll(List.of(p1, p2));
        analyticsRepository.save(PostAnalytics.of(p1.getId(), 4210, 318, 27, 41, 6, 12));
        analyticsRepository.save(PostAnalytics.of(p2.getId(), 1890, 154, 19, 15, 3, 5));

        // 예약 1건 (내일)
        Post s1 = Post.create(userId,
                "🕯️ 신상 '포근한 오후' 리필 출시 예고\n내일 오전 10시, 딱 100개 한정으로 먼저 열어요.",
                List.of("신상예고", "한정판", "리필향초"), "알림 신청하기 →", null);
        s1.schedule(now.plus(1, ChronoUnit.DAYS));

        // 초안 2건
        Post d1 = Post.create(userId,
                "향초 오래 쓰는 법 3가지 — 첫 점화 때 이거 안 하면 터널링 생겨요.",
                List.of("꿀팁", "향초관리"), "가이드 받기 →", null);
        Post d2 = Post.create(userId,
                "선물로 향초 고를 때 실패 안 하는 향 조합 추천 🎁",
                List.of("선물추천", "향추천"), "선물세트 보기 →", null);
        postRepository.saveAll(List.of(s1, d1, d2));
    }

    private Post published(Long userId, String content, List<String> tags, String cta) {
        Post p = Post.create(userId, content, tags, cta, null);
        p.markPublished("demo-" + Math.abs(content.hashCode()));
        return p;
    }
}
