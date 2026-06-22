package com.postflow.roi;

import com.postflow.analytics.AnalyticsRepository;
import com.postflow.analytics.PostAnalytics;
import com.postflow.post.Post;
import com.postflow.post.PostRepository;
import com.postflow.roi.dto.CreateConversionRequest;
import com.postflow.roi.dto.RoiDashboardResponse;
import com.postflow.roi.dto.RoiDashboardResponse.PostRevenueDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoiService {

    private final PostRepository postRepository;
    private final AnalyticsRepository analyticsRepository;
    private final LinkClickRepository linkClickRepository;
    private final LeadRepository leadRepository;
    private final ConversionRepository conversionRepository;

    public RoiService(PostRepository postRepository,
                      AnalyticsRepository analyticsRepository,
                      LinkClickRepository linkClickRepository,
                      LeadRepository leadRepository,
                      ConversionRepository conversionRepository) {
        this.postRepository = postRepository;
        this.analyticsRepository = analyticsRepository;
        this.linkClickRepository = linkClickRepository;
        this.leadRepository = leadRepository;
        this.conversionRepository = conversionRepository;
    }

    @Transactional
    public Conversion createConversion(Long userId, CreateConversionRequest req) {
        return conversionRepository.save(Conversion.manual(
                userId, req.postId(), req.leadId(), req.amount(),
                req.currency(), req.occurredAt(), req.note()));
    }

    @Transactional
    public Lead createLead(Long userId, Long postId, Long ctaLinkId, String email, String name, String source) {
        return leadRepository.save(Lead.create(userId, postId, ctaLinkId, email, name, source));
    }

    @Transactional(readOnly = true)
    public RoiDashboardResponse dashboard(Long userId) {
        List<Post> posts = postRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Long> ids = posts.stream().map(Post::getId).toList();

        long views = 0;
        long clicks = 0;
        long leads = 0;
        if (!ids.isEmpty()) {
            views = analyticsRepository.findByPostIdIn(ids).stream().mapToLong(PostAnalytics::getViews).sum();
            clicks = linkClickRepository.countByPostIdIn(ids);
            leads = leadRepository.countByPostIdIn(ids);
        }

        List<Conversion> convs = conversionRepository.findByUserId(userId);
        long conversions = convs.size();
        double revenue = convs.stream()
                .map(Conversion::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .doubleValue();
        String currency = convs.stream().map(Conversion::getCurrency).findFirst().orElse("KRW");

        double ctr = views > 0 ? (double) clicks / views : 0.0;
        double leadRate = clicks > 0 ? (double) leads / clicks : 0.0;
        double purchaseRate = clicks > 0 ? (double) conversions / clicks : 0.0;
        double rpm = views > 0 ? revenue / views * 1000.0 : 0.0;
        double revenuePerPost = !posts.isEmpty() ? revenue / posts.size() : 0.0;

        // revenue per post
        Map<Long, Double> revByPost = new HashMap<>();
        Map<Long, Long> cntByPost = new HashMap<>();
        for (Conversion c : convs) {
            if (c.getPostId() == null) continue;
            revByPost.merge(c.getPostId(), c.getAmount() != null ? c.getAmount().doubleValue() : 0.0, Double::sum);
            cntByPost.merge(c.getPostId(), 1L, Long::sum);
        }
        Map<Long, String> contentById = new HashMap<>();
        for (Post p : posts) {
            contentById.put(p.getId(), p.getContent());
        }
        List<PostRevenueDto> top = revByPost.entrySet().stream()
                .map(e -> new PostRevenueDto(e.getKey(), contentById.getOrDefault(e.getKey(), ""),
                        e.getValue(), cntByPost.getOrDefault(e.getKey(), 0L)))
                .sorted(Comparator.comparingDouble(PostRevenueDto::revenue).reversed())
                .limit(5)
                .toList();

        return new RoiDashboardResponse(
                views, clicks, leads, conversions, revenue, currency,
                ctr, leadRate, purchaseRate, rpm, revenuePerPost, null, top);
    }
}
