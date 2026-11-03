package com.nirmaan.reimburse.mcp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Persistence for OAuth clients, grants, codes, tokens and upload links (plain JDBC). */
@Repository
public class OAuthStore {

    public record Client(String clientId, String clientName, List<String> redirectUris) {
    }

    public record Grant(long id, String clientId, String clientName, long userId, String scope,
                        Instant createdAt, Instant lastUsedAt, Instant revokedAt) {
        public boolean active() {
            return revokedAt == null;
        }
    }

    public record AuthCode(long grantId, String redirectUri, String codeChallenge, Instant expiresAt, Instant usedAt) {
    }

    public record Token(long grantId, String kind, Instant expiresAt, Instant revokedAt) {
    }

    public record UploadLink(long claimId, long userId, Instant expiresAt) {
    }

    private final JdbcTemplate jdbc;

    public OAuthStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- clients

    public void saveClient(Client c) {
        jdbc.update("insert into mcp_clients (client_id, client_name, redirect_uris) values (?, ?, ?)",
                c.clientId(), c.clientName(), String.join("\n", c.redirectUris()));
    }

    public Optional<Client> findClient(String clientId) {
        return jdbc.query("select client_id, client_name, redirect_uris from mcp_clients where client_id = ?",
                (rs, i) -> new Client(rs.getString(1), rs.getString(2), Arrays.asList(rs.getString(3).split("\n"))),
                clientId).stream().findFirst();
    }

    // ---------------------------------------------------------------- grants

    public long createGrant(String clientId, long userId, String scope) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into mcp_grants (client_id, user_id, scope) values (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, clientId);
            ps.setLong(2, userId);
            ps.setString(3, scope);
            return ps;
        }, keys);
        return ((Number) keys.getKeys().get("id")).longValue();
    }

    public Optional<Grant> findGrant(long id) {
        return queryGrants("where g.id = ?", id).stream().findFirst();
    }

    public List<Grant> grantsOfUser(long userId) {
        return queryGrants("where g.user_id = ? order by g.created_at desc", userId);
    }

    public void revokeGrant(long id, Instant at) {
        jdbc.update("update mcp_grants set revoked_at = ? where id = ? and revoked_at is null", ts(at), id);
        jdbc.update("update mcp_tokens set revoked_at = ? where grant_id = ? and revoked_at is null", ts(at), id);
    }

    public void touchGrant(long id, Instant at) {
        jdbc.update("update mcp_grants set last_used_at = ? where id = ?", ts(at), id);
    }

    private List<Grant> queryGrants(String where, Object... args) {
        return jdbc.query("""
                select g.id, g.client_id, c.client_name, g.user_id, g.scope, g.created_at, g.last_used_at, g.revoked_at
                from mcp_grants g join mcp_clients c on c.client_id = g.client_id""" + " " + where,
                (rs, i) -> new Grant(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5),
                        inst(rs.getTimestamp(6)), inst(rs.getTimestamp(7)), inst(rs.getTimestamp(8))), args);
    }

    // ---------------------------------------------------------------- authorization codes

    public void saveCode(String codeHash, long grantId, String redirectUri, String codeChallenge, Instant expiresAt) {
        jdbc.update("insert into mcp_auth_codes (code_hash, grant_id, redirect_uri, code_challenge, expires_at) values (?, ?, ?, ?, ?)",
                codeHash, grantId, redirectUri, codeChallenge, ts(expiresAt));
    }

    public Optional<AuthCode> findCode(String codeHash) {
        return jdbc.query("select grant_id, redirect_uri, code_challenge, expires_at, used_at from mcp_auth_codes where code_hash = ?",
                (rs, i) -> new AuthCode(rs.getLong(1), rs.getString(2), rs.getString(3), inst(rs.getTimestamp(4)),
                        inst(rs.getTimestamp(5))), codeHash).stream().findFirst();
    }

    /** Marks the code used; returns false if it had already been used (replay). */
    public boolean useCode(String codeHash, Instant at) {
        return jdbc.update("update mcp_auth_codes set used_at = ? where code_hash = ? and used_at is null", ts(at), codeHash) == 1;
    }

    // ---------------------------------------------------------------- tokens

    public void saveToken(String tokenHash, long grantId, String kind, Instant expiresAt) {
        jdbc.update("insert into mcp_tokens (token_hash, grant_id, kind, expires_at) values (?, ?, ?, ?)",
                tokenHash, grantId, kind, ts(expiresAt));
    }

    public Optional<Token> findToken(String tokenHash) {
        return jdbc.query("select grant_id, kind, expires_at, revoked_at from mcp_tokens where token_hash = ?",
                (rs, i) -> new Token(rs.getLong(1), rs.getString(2), inst(rs.getTimestamp(3)), inst(rs.getTimestamp(4))),
                tokenHash).stream().findFirst();
    }

    /** Revokes one token; returns false if it was already revoked (used for refresh-token rotation). */
    public boolean revokeToken(String tokenHash, Instant at) {
        return jdbc.update("update mcp_tokens set revoked_at = ? where token_hash = ? and revoked_at is null", ts(at), tokenHash) == 1;
    }

    // ---------------------------------------------------------------- upload links

    public void saveUploadLink(String tokenHash, long claimId, long userId, Instant expiresAt) {
        jdbc.update("insert into upload_links (token_hash, claim_id, user_id, expires_at) values (?, ?, ?, ?)",
                tokenHash, claimId, userId, ts(expiresAt));
    }

    public Optional<UploadLink> findUploadLink(String tokenHash) {
        return jdbc.query("select claim_id, user_id, expires_at from upload_links where token_hash = ?",
                (rs, i) -> new UploadLink(rs.getLong(1), rs.getLong(2), inst(rs.getTimestamp(3))), tokenHash)
                .stream().findFirst();
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Instant inst(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
