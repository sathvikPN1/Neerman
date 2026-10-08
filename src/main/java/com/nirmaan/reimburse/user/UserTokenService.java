package com.nirmaan.reimburse.user;

import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** Single-use, expiring tokens for invitations. Only SHA-256 hashes are stored. */
@Service
public class UserTokenService {

    public static final String INVITE = "INVITE";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public UserTokenService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public String create(Long userId, String purpose, Duration ttl) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("insert into user_tokens (token_hash, user_id, purpose, expires_at) values (?, ?, ?, ?)",
                sha256(raw), userId, purpose, Timestamp.from(Instant.now(clock).plus(ttl)));
        return raw;
    }

    /** User id for a valid (unused, unexpired) token, without consuming it. */
    @Transactional(readOnly = true)
    public Optional<Long> peek(String raw, String purpose) {
        List<Long> ids = jdbc.queryForList("""
                        select user_id from user_tokens
                        where token_hash = ? and purpose = ? and used_at is null and expires_at > ?""",
                Long.class, sha256(raw), purpose, Timestamp.from(Instant.now(clock)));
        return ids.stream().findFirst();
    }

    @Transactional
    public Long consume(String raw, String purpose) {
        Long userId = peek(raw, purpose)
                .orElseThrow(() -> new BusinessRuleException("This link is invalid, already used or expired."));
        int updated = jdbc.update("update user_tokens set used_at = ? where token_hash = ? and used_at is null",
                Timestamp.from(Instant.now(clock)), sha256(raw));
        if (updated != 1) {
            throw new BusinessRuleException("This link has already been used.");
        }
        return userId;
    }

    static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
