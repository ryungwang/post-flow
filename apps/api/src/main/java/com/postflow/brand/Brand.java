package com.postflow.brand;

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

/** A product/campaign the user wants to promote; feeds into AI generation as context. */
@Getter
@Entity
@Table(name = "brand_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 500)
    private String audience;

    @Column(name = "key_points", length = 1000)
    private String keyPoints;

    @Column(name = "cta_text", length = 300)
    private String ctaText;

    @Column(length = 2048)
    private String url;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    public static Brand create(Long userId, String name, String description, String audience,
                               String keyPoints, String ctaText, String url, boolean isDefault) {
        Brand b = new Brand();
        b.userId = userId;
        b.apply(name, description, audience, keyPoints, ctaText, url, isDefault);
        return b;
    }

    public void apply(String name, String description, String audience,
                      String keyPoints, String ctaText, String url, boolean isDefault) {
        this.name = name;
        this.description = description;
        this.audience = audience;
        this.keyPoints = keyPoints;
        this.ctaText = ctaText;
        this.url = url;
        this.isDefault = isDefault;
    }
}
