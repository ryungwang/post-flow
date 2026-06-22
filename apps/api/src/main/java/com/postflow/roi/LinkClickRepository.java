package com.postflow.roi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface LinkClickRepository extends JpaRepository<LinkClick, Long> {
    long countByPostIdIn(List<Long> postIds);

    /** Dedup guard: a click from the same hashed IP for the same link within a recent window. */
    boolean existsByCtaLinkIdAndIpHashAndClickedAtAfter(Long ctaLinkId, String ipHash, Instant after);
}
