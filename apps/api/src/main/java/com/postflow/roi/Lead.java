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

/** A captured lead attributed to a post/CTA link. */
@Getter
@Entity
@Table(name = "leads")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lead extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "cta_link_id")
    private Long ctaLinkId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 255)
    private String name;

    @Column(length = 60)
    private String source;

    @Column(nullable = false, length = 20)
    private String status = "NEW";

    public static Lead create(Long userId, Long postId, Long ctaLinkId, String email, String name, String source) {
        Lead l = new Lead();
        l.userId = userId;
        l.postId = postId;
        l.ctaLinkId = ctaLinkId;
        l.email = email;
        l.name = name;
        l.source = source;
        l.status = "NEW";
        return l;
    }
}
