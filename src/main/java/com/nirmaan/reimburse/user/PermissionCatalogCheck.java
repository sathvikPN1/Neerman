package com.nirmaan.reimburse.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Fails start-up if the permissions table and the {@link Permission} enum drift apart. */
@Component
@Order(0)
public class PermissionCatalogCheck implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public PermissionCatalogCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> db = new HashSet<>(jdbc.queryForList("select code from permissions", String.class));
        Set<String> code = Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet());
        if (!db.equals(code)) {
            throw new IllegalStateException("permissions table " + db + " does not match Permission enum " + code
                    + ". Add a Flyway migration.");
        }
    }
}
