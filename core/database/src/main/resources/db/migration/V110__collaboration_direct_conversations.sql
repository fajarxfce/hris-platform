-- Canonical DM identity. This is durable intent, not proof that a Matrix room
-- exists or that its contents are currently admitted. Each company has its own
-- pair; both identities survive offboarding and ordinary session cleanup.
CREATE TABLE collaboration_direct_conversations (
    company_id uuid NOT NULL REFERENCES companies(id),
    id uuid NOT NULL,
    first_account_id uuid NOT NULL REFERENCES accounts(id),
    second_account_id uuid NOT NULL REFERENCES accounts(id),
    created_by uuid NOT NULL REFERENCES accounts(id),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    version bigint NOT NULL DEFAULT 0 CHECK(version=0),
    PRIMARY KEY(company_id,id),
    UNIQUE(company_id,first_account_id,second_account_id),
    CHECK(first_account_id<second_account_id),
    CHECK(created_by IN (first_account_id,second_account_id))
);
CREATE INDEX collaboration_direct_first ON collaboration_direct_conversations(company_id,first_account_id,id);
CREATE INDEX collaboration_direct_second ON collaboration_direct_conversations(company_id,second_account_id,id);

ALTER TABLE collaboration_direct_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_direct_conversations FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_direct_read ON collaboration_direct_conversations FOR SELECT
    USING(company_id=current_company_id() AND current_actor_id() IN (first_account_id,second_account_id));
CREATE POLICY collaboration_direct_insert ON collaboration_direct_conversations FOR INSERT
    WITH CHECK(company_id=current_company_id() AND created_by=current_actor_id()
        AND current_actor_id() IN (first_account_id,second_account_id));
-- No UPDATE/DELETE authority, including for the creator. Membership eligibility
-- is authorized by commands under current company/member/account guards. This
-- retained pair never silently becomes a group or gets reassigned to a new peer.
CREATE TRIGGER immutable_collaboration_direct BEFORE UPDATE OR DELETE ON collaboration_direct_conversations
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
