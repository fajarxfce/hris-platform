-- A stable logical account selects one provider user for new organization rooms.
-- Existing provider users/devices/ciphertext remain explicit legacy identities;
-- this selection is neither a provider credential nor proof of a local SDK store.
ALTER TABLE collaboration_provider_sessions ADD CONSTRAINT collaboration_provider_identity_reference
    UNIQUE(company_id,device_id,account_id,matrix_user_id);

CREATE TABLE collaboration_space_identities (
    space_id uuid NOT NULL REFERENCES collaboration_spaces(id),
    account_id uuid NOT NULL REFERENCES accounts(id),
    matrix_server_name varchar(180) NOT NULL CHECK(length(matrix_server_name)>0
        AND matrix_server_name !~ '[[:space:][:cntrl:]/?#@]'),
    matrix_user_id varchar(255) NOT NULL CHECK(matrix_user_id ~ '^@[^[:space:]:]+:[^[:space:]]+$'),
    origin varchar(16) NOT NULL CHECK(origin IN ('LEGACY','SPACE')),
    legacy_company_id uuid,
    legacy_device_id uuid,
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    version bigint NOT NULL DEFAULT 0 CHECK(version=0),
    PRIMARY KEY(space_id,account_id),
    UNIQUE(matrix_user_id),
    CHECK(substring(matrix_user_id from position(':' in matrix_user_id)+1)=matrix_server_name),
    CHECK((origin='LEGACY' AND legacy_company_id IS NOT NULL AND legacy_device_id IS NOT NULL
        AND split_part(matrix_user_id,':',1)='@hris_'||replace(legacy_company_id::text,'-','')||'_'||replace(account_id::text,'-',''))
        OR (origin='SPACE' AND legacy_company_id IS NULL AND legacy_device_id IS NULL
        AND split_part(matrix_user_id,':',1)='@hrisg_'||replace(space_id::text,'-','')||'_'||replace(account_id::text,'-',''))),
    FOREIGN KEY(legacy_company_id,legacy_device_id,account_id,matrix_user_id)
        REFERENCES collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id)
);
ALTER TABLE collaboration_space_identities ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_identities FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_space_identity_read ON collaboration_space_identities FOR SELECT
    USING(account_id=current_actor_id() AND EXISTS(
        SELECT 1 FROM collaboration_space_directory space
        WHERE space.space_id=collaboration_space_identities.space_id));
CREATE POLICY collaboration_space_identity_insert ON collaboration_space_identities FOR INSERT
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id() AND EXISTS(
        SELECT 1 FROM collaboration_space_directory space
        WHERE space.space_id=collaboration_space_identities.space_id));
CREATE TRIGGER immutable_collaboration_space_identity BEFORE UPDATE OR DELETE ON collaboration_space_identities
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

-- Deliberately empty. A future native selection command takes the account guard,
-- rechecks group/session/client admission, prefers a retained user on this server,
-- and records its chosen identity once. Other legacy users/stores/rooms are retained.
-- Native-session expiry, company switching and credential retirement cannot rewrite
-- the selection. No Matrix API call, token copy or cryptographic key migration occurs.
