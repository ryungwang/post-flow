package com.postflow.aigeneration;

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

/**
 * Generation audit record. Stores {@code provider} + {@code model} together for usage
 * accounting, billing reconciliation, and A/B comparison across vendors.
 */
@Getter
@Entity
@Table(name = "ai_generations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiGeneration extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(columnDefinition = "text")
    private String result;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens = 0;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens = 0;
}
