package com.nirmaan.reimburse.user;

import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.nirmaan.reimburse.common.audit.AuditService.detail;

/**
 * Creates the first COO from BOOTSTRAP_COO_EMAIL / BOOTSTRAP_COO_PASSWORD when no COO exists yet.
 * Does nothing once a COO exists, so the variables can stay set.
 */
@Component
@Order(1)
public class CooBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CooBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final AuditService audit;

    public CooBootstrap(UserRepository users, PasswordEncoder passwordEncoder, AppProperties props, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.countActiveCoos() > 0) {
            return;
        }
        AppProperties.Bootstrap b = props.bootstrap();
        if (b == null || isBlank(b.cooEmail()) || isBlank(b.cooPassword())) {
            log.warn("No COO exists. Set BOOTSTRAP_COO_EMAIL and BOOTSTRAP_COO_PASSWORD to create one.");
            return;
        }
        if (users.findByEmail(b.cooEmail()).isPresent()) {
            throw new IllegalStateException("Bootstrap COO email " + b.cooEmail() + " belongs to an existing non-COO user");
        }
        User coo = new User(isBlank(b.cooName()) ? "COO" : b.cooName(), b.cooEmail(), Role.COO);
        coo.setPasswordHash(passwordEncoder.encode(b.cooPassword()));
        users.save(coo);
        audit.record(AuditEntity.USER, coo.getId(), "COO_BOOTSTRAPPED", null, null, null, null, null,
                detail("email", coo.getEmail()));
        log.info("Bootstrapped COO account {}", coo.getEmail());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
