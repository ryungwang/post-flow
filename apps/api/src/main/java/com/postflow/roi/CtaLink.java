package com.postflow.roi;

import com.postflow.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Trackable short link attached to a post's CTA. */
@Getter
@Entity
@Table(name = "cta_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CtaLink extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 16)
    private String slug;

    @Column(name = "destination_url", nullable = false, length = 2048)
    private String destinationUrl;

    @Column(length = 255)
    private String label;

    /** When true, the short link routes to a hosted lead-capture page before the destination. */
    @Column(name = "capture_lead", nullable = false)
    private boolean captureLead = false;

    @Column(length = 255)
    private String headline;

    public static CtaLink create(Long postId, Long userId, String slug, String destinationUrl,
                                 String label, boolean captureLead, String headline) {
        CtaLink c = new CtaLink();
        c.postId = postId;
        c.userId = userId;
        c.slug = slug;
        c.destinationUrl = destinationUrl;
        c.label = label;
        c.captureLead = captureLead;
        c.headline = headline;
        return c;
    }
}
