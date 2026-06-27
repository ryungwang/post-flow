package com.postflow.billing;

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

/** A billing charge record (history). */
@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, length = 20)
    private String plan;

    /** first(첫 결제) | renew(정기 갱신) */
    @Column(nullable = false, length = 20)
    private String kind;

    /** DONE | FAILED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "order_id", length = 120)
    private String orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public static Payment of(Long userId, long amount, String currency, String plan,
                             String kind, String status, String provider, String orderId) {
        Payment p = new Payment();
        p.userId = userId;
        p.amount = amount;
        p.currency = currency;
        p.plan = plan;
        p.kind = kind;
        p.status = status;
        p.provider = provider;
        p.orderId = orderId;
        p.createdAt = Instant.now();
        return p;
    }
}
