-- Public canonical identity is a discovery fact, not device readiness, private-key
-- possession or provider/room authority. The restricted view owner remains non-login.
GRANT SELECT(space_id,account_id,matrix_server_name,matrix_user_id,created_at)
    ON collaboration_space_identities TO hris_collaboration_directory_reader;
CREATE POLICY collaboration_directory_identity_read ON collaboration_space_identities FOR SELECT
    TO hris_collaboration_directory_reader USING(true);
CREATE VIEW collaboration_space_members WITH(security_barrier=true) AS
SELECT DISTINCT contact.space_id,contact.account_id,contact.display_name,
    identity.matrix_server_name,identity.matrix_user_id,identity.created_at AS identity_selected_at
FROM public.collaboration_space_contacts contact
LEFT JOIN public.collaboration_space_identities identity
    ON identity.space_id=contact.space_id AND identity.account_id=contact.account_id;
ALTER VIEW collaboration_space_members OWNER TO hris_collaboration_directory_reader;

-- A separate immutable group pair. It can retain intent while a peer has not yet
-- selected a Matrix identity; this never fabricates a reachable encrypted recipient.
CREATE TABLE collaboration_space_direct_conversations (
    space_id uuid NOT NULL REFERENCES collaboration_spaces(id),
    id uuid NOT NULL,
    first_account_id uuid NOT NULL REFERENCES accounts(id),
    second_account_id uuid NOT NULL REFERENCES accounts(id),
    created_by uuid NOT NULL REFERENCES accounts(id),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    version bigint NOT NULL DEFAULT 0 CHECK(version=0),
    PRIMARY KEY(space_id,id),
    UNIQUE(space_id,first_account_id,second_account_id),
    CHECK(first_account_id<second_account_id),
    CHECK(created_by IN (first_account_id,second_account_id))
);
CREATE INDEX collaboration_space_direct_first ON collaboration_space_direct_conversations(space_id,first_account_id,id);
CREATE INDEX collaboration_space_direct_second ON collaboration_space_direct_conversations(space_id,second_account_id,id);
ALTER TABLE collaboration_space_direct_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_direct_conversations FORCE ROW LEVEL SECURITY;
-- Owned intent survives loss of eligibility; live commands must separately admit
-- the current native actor and recheck both members before/after mutation.
CREATE POLICY collaboration_space_direct_read ON collaboration_space_direct_conversations FOR SELECT
    USING(current_company_id() IS NULL AND current_actor_id() IN (first_account_id,second_account_id));
CREATE POLICY collaboration_space_direct_insert ON collaboration_space_direct_conversations FOR INSERT
    WITH CHECK(current_company_id() IS NULL AND created_by=current_actor_id()
        AND EXISTS(SELECT 1 FROM collaboration_space_members m WHERE m.space_id=collaboration_space_direct_conversations.space_id
            AND m.account_id=first_account_id)
        AND EXISTS(SELECT 1 FROM collaboration_space_members m WHERE m.space_id=collaboration_space_direct_conversations.space_id
            AND m.account_id=second_account_id));
CREATE FUNCTION validate_collaboration_space_direct() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.created_at>clock_timestamp() THEN
        RAISE EXCEPTION 'Invalid organization conversation time' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_collaboration_space_direct BEFORE INSERT ON collaboration_space_direct_conversations
    FOR EACH ROW EXECUTE FUNCTION validate_collaboration_space_direct();
CREATE TRIGGER immutable_collaboration_space_direct BEFORE UPDATE OR DELETE ON collaboration_space_direct_conversations
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

-- Safe original conversation/room inventory. No HR profile, background-job payload,
-- provider bearer, native session or encryption key is visible to this view owner.
GRANT SELECT(company_id,id,first_account_id,second_account_id,created_by,created_at,version)
    ON collaboration_direct_conversations TO hris_collaboration_legacy_reader;
GRANT SELECT(company_id,conversation_id,matrix_server_name,matrix_room_id,created_at)
    ON collaboration_direct_room_bindings TO hris_collaboration_legacy_reader;
CREATE POLICY collaboration_legacy_direct_owned ON collaboration_direct_conversations FOR SELECT
    TO hris_collaboration_legacy_reader USING(current_actor_id() IN (first_account_id,second_account_id));
CREATE POLICY collaboration_legacy_room_owned ON collaboration_direct_room_bindings FOR SELECT
    TO hris_collaboration_legacy_reader USING(EXISTS(
        SELECT 1 FROM collaboration_direct_conversations c
        WHERE c.company_id=collaboration_direct_room_bindings.company_id
            AND c.id=collaboration_direct_room_bindings.conversation_id
            AND current_actor_id() IN(c.first_account_id,c.second_account_id)));
CREATE VIEW collaboration_space_legacy_conversations WITH(security_barrier=true) AS
SELECT space.space_id,c.company_id AS legacy_company_id,c.id AS legacy_conversation_id,
    c.first_account_id,c.second_account_id,c.created_by,c.created_at,c.version,
    b.matrix_server_name,b.matrix_room_id,b.created_at AS room_bound_at
FROM public.collaboration_space_directory space
CROSS JOIN public.collaboration_direct_conversations c
LEFT JOIN public.collaboration_direct_room_bindings b
    ON b.company_id=c.company_id AND b.conversation_id=c.id
WHERE public.current_actor_id() IN(c.first_account_id,c.second_account_id);
ALTER VIEW collaboration_space_legacy_conversations OWNER TO hris_collaboration_legacy_reader;

-- Neither projection selects a peer identity, adopts a room, admits key recipients
-- or changes original company rows. A later room grant must verify exact canonical
-- participants, provider encryption/membership and current application authority.
