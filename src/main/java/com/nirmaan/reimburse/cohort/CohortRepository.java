package com.nirmaan.reimburse.cohort;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CohortRepository extends JpaRepository<Cohort, Long> {
    List<Cohort> findAllByOrderByStartDateDesc();

    Optional<Cohort> findByNameIgnoreCase(String name);
}
