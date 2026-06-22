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

/** Per-post ad/promotion spend — ROI% denominator. One row per post (upserted). */
@Getter
@Entity
@Table(name = "post_costs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostCost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false, unique = true)
    private Long postId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency = "KRW";

    @Column(length = 500)
    private String note;

    public static PostCost create(Long userId, Long postId, BigDecimal amount, String currency, String note) {
        PostCost c = new PostCost();
        c.userId = userId;
        c.postId = postId;
        c.amount = amount;
        c.currency = currency != null ? currency : "KRW";
        c.note = note;
        return c;
    }

    public void update(BigDecimal amount, String currency, String note) {
        this.amount = amount;
        if (currency != null) this.currency = currency;
        this.note = note;
    }
}
