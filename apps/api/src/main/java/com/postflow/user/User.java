package com.postflow.user;

import com.postflow.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "profile_image")
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.FREE;

    /** Per-user HMAC secret for the conversion webhook (null until first issued). */
    @Column(name = "webhook_secret", length = 64)
    private String webhookSecret;

    public static User create(String email, String name, String profileImage) {
        User u = new User();
        u.email = email;
        u.name = name;
        u.profileImage = profileImage;
        u.plan = Plan.FREE;
        return u;
    }

    /** Refresh mutable profile fields from the identity provider on each login. */
    public void updateProfile(String name, String profileImage) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        this.profileImage = profileImage;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public void changePlan(Plan plan) {
        this.plan = plan;
    }
}
