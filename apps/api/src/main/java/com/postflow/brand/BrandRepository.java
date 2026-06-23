package com.postflow.brand;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    List<Brand> findByUserIdOrderByIdAsc(Long userId);

    Optional<Brand> findByIdAndUserId(Long id, Long userId);
}
