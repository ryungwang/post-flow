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
    private final PostCostRepository postCostRepository;

    public RoiService(PostRepository postRepository,
                      AnalyticsRepository analyticsRepository,
                      LinkClickRepository linkClickRepository,
                      LeadRepository leadRepository,
                      ConversionRepository conversionRepository,
                      PostCostRepository postCostRepository) {
        this.postRepository = postRepository;
        this.analyticsRepository = analyticsRepository;
        this.linkClickRepository = linkClickRepository;
        this.leadRepository = leadRepository;
        this.conversionRepository = conversionRepository;
        this.postCostRepository = postCostRepository;
    }

    @Transactional
    public PostCost setCost(Long userId, Long postId, java.math.BigDecimal amount, String currency, String note) {
        return postCostRepository.findByPostId(postId)
                .map(c -> { c.update(amount, currency, note); return c; })
                .orElseGet(() -> postCostRepository.save(PostCost.create(userId, postId, amount, currency, note)));
    }

    @Transactional
    public Conversion createConversion(Long userId, CreateConversionRequest req) {
        return conversionRepository.save(Conversion.manual(
                userId, req.postId(), req.leadId(), req.amount(),
                req.currency(), req.occurredAt(), req.note()));
    }

    @Transactional
    public Conversion recordWebhookConversion(Long userId, Long postId, java.math.BigDecimal amount,
                                              String currency, String note) {
        Conversion c = Conversion.manual(userId, postId, null, amount, currency, null, note);
        c.markWebhook();
        return conversionRepository.save(c);
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

        // costs by post
        Map<Long, Double> costByPost = new HashMap<>();
        double totalCost = 0.0;
        for (PostCost pc : postCostRepository.findByUserId(userId)) {
            double amt = pc.getAmount() != null ? pc.getAmount().doubleValue() : 0.0;
            costByPost.put(pc.getPostId(), amt);
            totalCost += amt;
        }

        double ctr = views > 0 ? (double) clicks / views : 0.0;
        double leadRate = clicks > 0 ? (double) leads / clicks : 0.0;
        double purchaseRate = clicks > 0 ? (double) conversions / clicks : 0.0;
        double rpm = views > 0 ? revenue / views * 1000.0 : 0.0;
        double revenuePerPost = !posts.isEmpty() ? revenue / posts.size() : 0.0;
        double netRevenue = revenue - totalCost;
        Double roiPercent = totalCost > 0 ? (revenue - totalCost) / totalCost * 100.0 : null;

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
                .map(e -> {
                    double rev = e.getValue();
                    double cost = costByPost.getOrDefault(e.getKey(), 0.0);
                    Double roi = cost > 0 ? (rev - cost) / cost * 100.0 : null;
                    return new PostRevenueDto(e.getKey(), contentById.getOrDefault(e.getKey(), ""),
                            rev, cntByPost.getOrDefault(e.getKey(), 0L), cost, roi);
                })
                .sorted(Comparator.comparingDouble(PostRevenueDto::revenue).reversed())
                .limit(5)
                .toList();

        return new RoiDashboardResponse(
                views, clicks, leads, conversions, revenue, totalCost, netRevenue, currency,
                ctr, leadRate, purchaseRate, rpm, revenuePerPost, roiPercent, top);
    }
}
