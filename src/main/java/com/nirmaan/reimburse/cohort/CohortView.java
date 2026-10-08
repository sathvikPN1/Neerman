package com.nirmaan.reimburse.cohort;

import java.time.LocalDate;

public record CohortView(Long id, String name, LocalDate startDate, LocalDate endDate, long teamCount) {
}
