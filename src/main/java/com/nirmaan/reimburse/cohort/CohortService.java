package com.nirmaan.reimburse.cohort;

import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.exception.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.nirmaan.reimburse.common.audit.AuditService.detail;

@Service
public class CohortService {

    private final CohortRepository cohorts;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public CohortService(CohortRepository cohorts, JdbcTemplate jdbc, AuditService audit) {
        this.cohorts = cohorts;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** Readable by any staff-side user (used in filters and team forms). */
    @PreAuthorize("!hasRole('TEAM_MEMBER')")
    @Transactional(readOnly = true)
    public List<CohortView> list() {
        return cohorts.findAllByOrderByStartDateDesc().stream()
                .map(c -> new CohortView(c.getId(), c.getName(), c.getStartDate(), c.getEndDate(),
                        jdbc.queryForObject("select count(*) from teams where cohort_id = ?", Long.class, c.getId())))
                .toList();
    }

    @PreAuthorize("hasAuthority('COHORT_MANAGE')")
    @Transactional
    public Long save(CohortForm form) {
        if (form.endDate().isBefore(form.startDate())) {
            throw new BusinessRuleException("End date must be on or after the start date.");
        }
        cohorts.findByNameIgnoreCase(form.name().trim())
                .filter(c -> !c.getId().equals(form.id()))
                .ifPresent(c -> {
                    throw new BusinessRuleException("A cohort named \"" + form.name() + "\" already exists.");
                });
        Cohort cohort;
        if (form.id() == null) {
            cohort = cohorts.save(new Cohort(form.name(), form.startDate(), form.endDate()));
            audit.record(AuditEntity.COHORT, cohort.getId(), "COHORT_CREATED", detail("name", cohort.getName()));
        } else {
            cohort = cohorts.findById(form.id()).orElseThrow(() -> NotFoundException.of("Cohort", form.id()));
            cohort.update(form.name(), form.startDate(), form.endDate());
            audit.record(AuditEntity.COHORT, cohort.getId(), "COHORT_UPDATED", detail("name", cohort.getName()));
        }
        return cohort.getId();
    }
}
