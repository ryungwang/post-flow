package com.postflow.roi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A click on a CTA short link, attributed to its post. IP stored as hash only. */
@Getter
@Entity
@Table(name = "link_clicks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cta_link_id", nullable = false)
    private Long ctaLinkId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(length = 1024)
    private String referrer;

    @Column(length = 512)
    private String ua;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    public static LinkClick of(Long ctaLinkId, Long postId, String referrer, String ua, String ipHash) {
        LinkClick c = new LinkClick();
        c.ctaLinkId = ctaLinkId;
        c.postId = postId;
        c.clickedAt = Instant.now();
        c.referrer = referrer;
        c.ua = ua;
        c.ipHash = ipHash;
        return c;
    }
}
