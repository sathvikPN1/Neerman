-- Nirmaan Reimbursement Platform — Phase 1 schema.
-- All money is NUMERIC(12,2) INR. All timestamps are TIMESTAMPTZ (stored UTC).

-- ---------------------------------------------------------------- users & permissions
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(100),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_active_at  TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL CHECK (role IN ('COO', 'NIRMAAN_STAFF', 'FINANCE', 'TEAM_MEMBER')),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE permissions (
    code        VARCHAR(40) PRIMARY KEY,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE user_permissions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission_code VARCHAR(40) NOT NULL REFERENCES permissions (code),
    granted_by      BIGINT REFERENCES users (id),
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT ux_user_permission UNIQUE (user_id, permission_code),
    -- STAFF_MANAGE belongs to the COO role only and can never be granted.
    CONSTRAINT ck_staff_manage_not_grantable CHECK (permission_code <> 'STAFF_MANAGE')
);
CREATE INDEX ix_user_permissions_expiry ON user_permissions (expires_at) WHERE expires_at IS NOT NULL;

CREATE TABLE role_default_permissions (
    role            VARCHAR(30) NOT NULL,
    permission_code VARCHAR(40) NOT NULL REFERENCES permissions (code),
    PRIMARY KEY (role, permission_code),
    CONSTRAINT ck_default_staff_manage CHECK (permission_code <> 'STAFF_MANAGE')
);

-- Single-use tokens for invites / password set-up. Only the SHA-256 hash is stored.
CREATE TABLE user_tokens (
    token_hash  VARCHAR(64) PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose     VARCHAR(20) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- cohorts & teams
CREATE TABLE cohorts (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL UNIQUE,
    start_date DATE         NOT NULL,
    end_date   DATE         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date)
);

CREATE TABLE teams (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(120)  NOT NULL UNIQUE,
    cohort_id       BIGINT        NOT NULL REFERENCES cohorts (id),
    program         VARCHAR(10)   NOT NULL CHECK (program IN ('PRATHAM', 'AKSHAR')),
    budget_amount   NUMERIC(12, 2) NOT NULL CHECK (budget_amount >= 0),
    priority        BOOLEAN       NOT NULL DEFAULT FALSE,
    priority_reason VARCHAR(500),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE team_members (
    team_id BIGINT NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (team_id, user_id)
);
-- A student belongs to exactly one team (see ASSUMPTIONS.md).
CREATE UNIQUE INDEX ux_team_members_user ON team_members (user_id);

-- ---------------------------------------------------------------- categories
CREATE TABLE categories (
    id                     BIGSERIAL PRIMARY KEY,
    name                   VARCHAR(120)  NOT NULL UNIQUE,
    allowed                BOOLEAN       NOT NULL,
    requires_pre_approval  BOOLEAN       NOT NULL DEFAULT FALSE,
    pre_approval_recommended BOOLEAN     NOT NULL DEFAULT FALSE,
    justification_required BOOLEAN       NOT NULL DEFAULT FALSE,
    description            VARCHAR(1000) NOT NULL,
    sort_order             INT           NOT NULL DEFAULT 0,
    active                 BOOLEAN       NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------- claims
CREATE SEQUENCE claim_code_seq START 1;

CREATE TABLE claims (
    id                        BIGSERIAL PRIMARY KEY,
    public_code               VARCHAR(20)    NOT NULL UNIQUE,
    team_id                   BIGINT         NOT NULL REFERENCES teams (id),
    submitted_by              BIGINT         NOT NULL REFERENCES users (id),
    category_id               BIGINT         NOT NULL REFERENCES categories (id),
    pre_approval_id           BIGINT,
    claimed_amount            NUMERIC(12, 2) NOT NULL CHECK (claimed_amount > 0),
    approved_amount           NUMERIC(12, 2) CHECK (approved_amount IS NULL OR approved_amount > 0),
    expense_date              DATE           NOT NULL,
    vendor_name               VARCHAR(200)   NOT NULL,
    vendor_gstin              VARCHAR(15),
    invoice_number            VARCHAR(60),
    invoice_date              DATE,
    justification             VARCHAR(2000)  NOT NULL,
    status                    VARCHAR(20)    NOT NULL
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'VERIFIED', 'RETURNED', 'APPROVED', 'PAID', 'REJECTED')),
    priority                  BOOLEAN        NOT NULL DEFAULT FALSE,
    priority_reason           VARCHAR(500),
    priority_requested        BOOLEAN        NOT NULL DEFAULT FALSE,
    priority_request_reason   VARCHAR(500),
    verified_by               BIGINT REFERENCES users (id),
    approved_by               BIGINT REFERENCES users (id),
    decision_reason           VARCHAR(1000),
    created_at                TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ    NOT NULL DEFAULT now(),
    submitted_at              TIMESTAMPTZ,
    stage_entered_at          TIMESTAMPTZ,
    verified_at               TIMESTAMPTZ,
    approved_at               TIMESTAMPTZ,
    paid_at                   TIMESTAMPTZ,
    version                   BIGINT         NOT NULL DEFAULT 0,
    CHECK (approved_amount IS NULL OR approved_amount <= claimed_amount)
);
CREATE INDEX ix_claims_team_status ON claims (team_id, status);
CREATE INDEX ix_claims_status ON claims (status, submitted_at);

CREATE TABLE claim_documents (
    id                BIGSERIAL PRIMARY KEY,
    claim_id          BIGINT       NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    type              VARCHAR(20)  NOT NULL CHECK (type IN ('INVOICE', 'PAYMENT_PROOF', 'OTHER')),
    storage_key       VARCHAR(300) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size              BIGINT       NOT NULL CHECK (size > 0 AND size <= 10485760),
    sha256            VARCHAR(64)  NOT NULL,
    uploaded_by       BIGINT REFERENCES users (id),
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_claim_documents_claim ON claim_documents (claim_id);
CREATE INDEX ix_claim_documents_sha ON claim_documents (sha256);

CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    claim_id   BIGINT        NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    author_id  BIGINT        NOT NULL REFERENCES users (id),
    body       VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_comments_claim ON comments (claim_id, created_at);

-- ---------------------------------------------------------------- payments
CREATE TABLE payout_batches (
    id         BIGSERIAL PRIMARY KEY,
    created_by BIGINT      NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status     VARCHAR(20) NOT NULL
);

CREATE TABLE payments (
    id          BIGSERIAL PRIMARY KEY,
    claim_id    BIGINT         NOT NULL UNIQUE REFERENCES claims (id),
    batch_id    BIGINT REFERENCES payout_batches (id),
    amount      NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    paid_on     DATE           NOT NULL,
    mode        VARCHAR(10)    NOT NULL CHECK (mode IN ('NEFT', 'IMPS', 'UPI')),
    utr         VARCHAR(40)    NOT NULL,
    recorded_by BIGINT         NOT NULL REFERENCES users (id),
    recorded_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- audit, notifications, settings
CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    entity_type     VARCHAR(30)  NOT NULL,
    entity_id       BIGINT,
    action          VARCHAR(50)  NOT NULL,
    actor_id        BIGINT REFERENCES users (id),
    on_behalf_of_id BIGINT REFERENCES users (id),
    permission_used VARCHAR(40),
    from_state      VARCHAR(30),
    to_state        VARCHAR(30),
    detail          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id, created_at);
CREATE INDEX ix_audit_created ON audit_log (created_at DESC);

-- The audit log is append-only: block UPDATE and DELETE at the database level.
CREATE FUNCTION audit_log_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    message    VARCHAR(500) NOT NULL,
    link       VARCHAR(300),
    read       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user ON notifications (user_id, read, created_at DESC);

CREATE TABLE settings (
    key        VARCHAR(80) PRIMARY KEY,
    value      VARCHAR(2000) NOT NULL,
    updated_by BIGINT REFERENCES users (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
