package com.postflow.roi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeadRepository extends JpaRepository<Lead, Long> {
    long countByPostIdIn(List<Long> postIds);
}
