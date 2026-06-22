package com.postflow.roi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CtaLinkRepository extends JpaRepository<CtaLink, Long> {
    Optional<CtaLink> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<CtaLink> findByUserIdOrderByIdDesc(Long userId);
}
