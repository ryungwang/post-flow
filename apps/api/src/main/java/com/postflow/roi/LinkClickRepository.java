package com.postflow.roi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LinkClickRepository extends JpaRepository<LinkClick, Long> {
    long countByPostIdIn(List<Long> postIds);
}
