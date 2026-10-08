package com.nirmaan.reimburse.settings;

import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.nirmaan.reimburse.common.audit.AuditService.detail;

/** Key/value platform settings (thresholds, SLAs, budget defaults). */
@Service
public class SettingsService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public SettingsService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Optional<String> get(String key) {
        List<String> values = jdbc.queryForList("select value from settings where key = ?", String.class, key);
        return values.stream().findFirst();
    }

    public BigDecimal getDecimal(String key, BigDecimal fallback) {
        return get(key).map(BigDecimal::new).orElse(fallback);
    }

    public int getInt(String key, int fallback) {
        return get(key).map(Integer::parseInt).orElse(fallback);
    }

    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Transactional
    public void set(String key, String value) {
        String old = get(key).orElse(null);
        jdbc.update("""
                insert into settings (key, value, updated_at) values (?, ?, now())
                on conflict (key) do update set value = excluded.value, updated_at = now()""", key, value);
        audit.record(AuditEntity.SETTING, null, "SETTING_CHANGED", detail("key", key, "from", old, "to", value));
    }
}
