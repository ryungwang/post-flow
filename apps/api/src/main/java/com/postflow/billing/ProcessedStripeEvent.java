package com.postflow.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Records each Stripe event id we've applied, so webhook retries are idempotent. */
@Entity
@Table(name = "processed_stripe_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedStripeEvent {

    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedStripeEvent(String eventId, Instant processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }
}
