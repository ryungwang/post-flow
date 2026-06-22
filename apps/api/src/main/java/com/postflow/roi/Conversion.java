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

import java.math.BigDecimal;
import java.time.Instant;

/** A revenue event attributed to a post (manual entry in MVP). */
@Getter
@Entity
@Table(name = "conversions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(name = "cta_link_id")
    private Long ctaLinkId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency = "KRW";

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false, length = 20)
    private String source = "MANUAL";

    public static Conversion manual(Long userId, Long postId, Long leadId, BigDecimal amount,
                                    String currency, Instant occurredAt, String note) {
        Conversion c = new Conversion();
        c.userId = userId;
        c.postId = postId;
        c.leadId = leadId;
        c.amount = amount;
        c.currency = currency != null ? currency : "KRW";
        c.occurredAt = occurredAt != null ? occurredAt : Instant.now();
        c.note = note;
        c.source = "MANUAL";
        return c;
    }

    public void markWebhook() {
        this.source = "WEBHOOK";
    }
}
