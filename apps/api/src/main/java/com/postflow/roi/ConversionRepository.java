package com.postflow.roi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversionRepository extends JpaRepository<Conversion, Long> {
    List<Conversion> findByUserId(Long userId);
}
