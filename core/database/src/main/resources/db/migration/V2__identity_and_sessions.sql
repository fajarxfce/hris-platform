CREATE TABLE accounts (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE CHECK (email = lower(email)),
    display_name varchar(200) NOT NULL,
    password_hash varchar(500),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    mfa_secret_encrypted text,
    mfa_last_counter bigint,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE platform_permissions (
    account_id uuid NOT NULL REFERENCES accounts(id),
    permission varchar(100) NOT NULL,
    PRIMARY KEY(account_id, permission)
);
CREATE TABLE company_memberships (
    company_id uuid NOT NULL REFERENCES companies(id),
    account_id uuid NOT NULL REFERENCES accounts(id),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(company_id, account_id)
);
CREATE TABLE membership_permissions (
    company_id uuid NOT NULL,
    account_id uuid NOT NULL,
    permission varchar(100) NOT NULL,
    PRIMARY KEY(company_id, account_id, permission),
    FOREIGN KEY(company_id, account_id) REFERENCES company_memberships(company_id, account_id)
);
ALTER TABLE company_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE company_memberships FORCE ROW LEVEL SECURITY;
CREATE POLICY membership_scope ON company_memberships USING (
    company_id = current_company_id() OR account_id = current_actor_id()
) WITH CHECK (company_id = current_company_id());
ALTER TABLE membership_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership_permissions FORCE ROW LEVEL SECURITY;
CREATE POLICY member_permission_scope ON membership_permissions USING (
    company_id = current_company_id() OR account_id = current_actor_id()
) WITH CHECK (company_id = current_company_id());
CREATE POLICY company_directory ON companies FOR SELECT USING (
    EXISTS(SELECT 1 FROM company_memberships m WHERE m.company_id=companies.id
        AND m.account_id=current_actor_id() AND m.active)
);
CREATE TABLE native_sessions (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES accounts(id),
    refresh_hash varchar(64) NOT NULL UNIQUE,
    authenticated_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    version bigint NOT NULL DEFAULT 0
);
CREATE TABLE consumed_refresh_tokens (
    token_hash varchar(64) PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES native_sessions(id),
    expires_at timestamptz NOT NULL
);
CREATE TABLE identity_challenges (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES accounts(id),
    kind varchar(32) NOT NULL,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz
);
CREATE TABLE oidc_identities (
    issuer varchar(500) NOT NULL,
    subject varchar(255) NOT NULL,
    account_id uuid NOT NULL REFERENCES accounts(id),
    PRIMARY KEY(issuer, subject)
);
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL PRIMARY KEY,
    SESSION_ID CHAR(36) NOT NULL UNIQUE,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100)
);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);
CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BYTEA NOT NULL,
    PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME)
);
