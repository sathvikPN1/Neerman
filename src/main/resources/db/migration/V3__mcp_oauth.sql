-- AI connectors (MCP) for startup team members: OAuth 2.1 clients, grants, codes and tokens,
-- plus one-time document upload links. Codes and tokens are stored only as SHA-256 hashes.

-- Clients that registered themselves via Dynamic Client Registration (RFC 7591), e.g. ChatGPT, Claude.
-- Public clients only (no client secret); PKCE is mandatory.
CREATE TABLE mcp_clients (
    client_id     VARCHAR(64)   PRIMARY KEY,
    client_name   VARCHAR(120)  NOT NULL,
    redirect_uris VARCHAR(2600) NOT NULL,   -- newline-separated
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- A user's consent for one client. Revoking a grant invalidates all of its tokens.
CREATE TABLE mcp_grants (
    id           BIGSERIAL   PRIMARY KEY,
    client_id    VARCHAR(64) NOT NULL REFERENCES mcp_clients (client_id) ON DELETE CASCADE,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scope        VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ
);
CREATE INDEX ix_mcp_grants_user ON mcp_grants (user_id);

-- Authorization codes: single use, 5 minutes, bound to client, redirect URI and PKCE challenge.
CREATE TABLE mcp_auth_codes (
    code_hash      VARCHAR(64)  PRIMARY KEY,
    grant_id       BIGINT       NOT NULL REFERENCES mcp_grants (id) ON DELETE CASCADE,
    redirect_uri   VARCHAR(500) NOT NULL,
    code_challenge VARCHAR(128) NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    used_at        TIMESTAMPTZ
);

-- Access tokens (1 hour) and refresh tokens (30 days, rotated on every use).
CREATE TABLE mcp_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    grant_id   BIGINT      NOT NULL REFERENCES mcp_grants (id) ON DELETE CASCADE,
    kind       VARCHAR(10) NOT NULL CHECK (kind IN ('ACCESS', 'REFRESH')),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_mcp_tokens_grant ON mcp_tokens (grant_id);

-- One-time-style upload links handed out by the AI so the student can attach the real file in a browser.
CREATE TABLE upload_links (
    token_hash VARCHAR(64) PRIMARY KEY,
    claim_id   BIGINT      NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
