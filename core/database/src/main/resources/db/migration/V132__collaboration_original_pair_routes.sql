-- A canonical pair retaining the same original company must choose one history.
-- Unique durable reservations arbitrate concurrent inserts even at a fixed snapshot.
CREATE TABLE collaboration_original_pair_routes (
    original_company_id uuid NOT NULL REFERENCES companies(id),
    first_account_id uuid NOT NULL REFERENCES accounts(id),
    second_account_id uuid NOT NULL REFERENCES accounts(id),
    origin varchar(16) NOT NULL CHECK(origin IN ('LEGACY','SPACE')),
    legacy_conversation_id uuid,
    space_id uuid,
    space_conversation_id uuid,
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    PRIMARY KEY(original_company_id,first_account_id,second_account_id),
    UNIQUE(original_company_id,legacy_conversation_id),
    UNIQUE(space_id,space_conversation_id),
    CHECK(first_account_id<second_account_id),
    CHECK((origin='LEGACY' AND legacy_conversation_id IS NOT NULL AND space_id IS NULL AND space_conversation_id IS NULL)
        OR (origin='SPACE' AND legacy_conversation_id IS NULL AND space_id IS NOT NULL AND space_conversation_id IS NOT NULL)),
    FOREIGN KEY(original_company_id,legacy_conversation_id)
        REFERENCES collaboration_direct_conversations(company_id,id) DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY(space_id,space_conversation_id)
        REFERENCES collaboration_space_room_intents(space_id,conversation_id) DEFERRABLE INITIALLY DEFERRED
);

ALTER TABLE collaboration_original_pair_routes ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_original_pair_routes FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_original_pair_route_read ON collaboration_original_pair_routes FOR SELECT
    USING(current_actor_id() IN(first_account_id,second_account_id)
        AND (current_company_id() IS NULL OR current_company_id()=original_company_id));
CREATE POLICY collaboration_original_pair_route_insert ON collaboration_original_pair_routes FOR INSERT
    WITH CHECK(current_actor_id() IN(first_account_id,second_account_id)
        AND ((origin='LEGACY' AND current_company_id()=original_company_id)
            OR (origin='SPACE' AND current_company_id() IS NULL)));
CREATE TRIGGER immutable_collaboration_original_pair_route
    BEFORE UPDATE OR DELETE ON collaboration_original_pair_routes
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

-- The selected row is inserted by the same transaction after its BEFORE trigger.
-- This deferred check also rejects orphan or mismatched direct reservation writes.
CREATE FUNCTION validate_collaboration_original_pair_route() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.origin='LEGACY' THEN
        IF NOT EXISTS(SELECT 1 FROM collaboration_direct_conversations c
            WHERE c.company_id=NEW.original_company_id AND c.id=NEW.legacy_conversation_id
                AND c.first_account_id=NEW.first_account_id AND c.second_account_id=NEW.second_account_id
                AND c.created_at=NEW.created_at) THEN
            RAISE EXCEPTION 'Original conversation route does not match its selected history' USING ERRCODE='23514';
        END IF;
    ELSE
        IF NOT EXISTS(SELECT 1 FROM collaboration_space_room_intents i
            WHERE i.space_id=NEW.space_id AND i.conversation_id=NEW.space_conversation_id AND i.origin='SPACE'
                AND i.first_account_id=NEW.first_account_id AND i.second_account_id=NEW.second_account_id
                AND i.created_at=NEW.created_at
                AND i.first_matrix_user_id='@hris_'||replace(NEW.original_company_id::text,'-','')||'_'||replace(NEW.first_account_id::text,'-','')||':'||i.matrix_server_name
                AND i.second_matrix_user_id='@hris_'||replace(NEW.original_company_id::text,'-','')||'_'||replace(NEW.second_account_id::text,'-','')||':'||i.matrix_server_name) THEN
            RAISE EXCEPTION 'Organization conversation route does not match its selected history' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER validate_collaboration_original_pair_route
    AFTER INSERT ON collaboration_original_pair_routes DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_collaboration_original_pair_route();

CREATE FUNCTION reserve_collaboration_original_pair_route() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO collaboration_original_pair_routes
        (original_company_id,first_account_id,second_account_id,origin,legacy_conversation_id,created_at)
    VALUES(NEW.company_id,NEW.first_account_id,NEW.second_account_id,'LEGACY',NEW.id,NEW.created_at)
    ON CONFLICT DO NOTHING;
    IF NOT EXISTS(SELECT 1 FROM collaboration_original_pair_routes r
        WHERE r.original_company_id=NEW.company_id AND r.first_account_id=NEW.first_account_id
            AND r.second_account_id=NEW.second_account_id AND r.origin='LEGACY'
            AND r.legacy_conversation_id=NEW.id AND r.created_at=NEW.created_at) THEN
        RAISE EXCEPTION 'Canonical pair already selected organization history' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER reserve_collaboration_original_pair_route BEFORE INSERT ON collaboration_direct_conversations
    FOR EACH ROW EXECUTE FUNCTION reserve_collaboration_original_pair_route();

CREATE FUNCTION reserve_collaboration_space_pair_route() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE original_company uuid;
BEGIN
    IF NEW.origin='LEGACY' THEN
        IF NOT EXISTS(SELECT 1 FROM collaboration_original_pair_routes r
            WHERE r.original_company_id=NEW.legacy_company_id AND r.first_account_id=NEW.first_account_id
                AND r.second_account_id=NEW.second_account_id AND r.origin='LEGACY'
                AND r.legacy_conversation_id=NEW.legacy_conversation_id) THEN
            RAISE EXCEPTION 'Original room route is unavailable' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.first_matrix_user_id ~ '^@hris_[a-f0-9]{32}_' THEN
        original_company := substring(NEW.first_matrix_user_id from 7 for 32)::uuid;
        IF NEW.second_matrix_user_id='@hris_'||replace(original_company::text,'-','')||'_'||replace(NEW.second_account_id::text,'-','')||':'||NEW.matrix_server_name THEN
            INSERT INTO collaboration_original_pair_routes
                (original_company_id,first_account_id,second_account_id,origin,space_id,space_conversation_id,created_at)
            VALUES(original_company,NEW.first_account_id,NEW.second_account_id,'SPACE',NEW.space_id,NEW.conversation_id,NEW.created_at)
            ON CONFLICT DO NOTHING;
            IF NOT EXISTS(SELECT 1 FROM collaboration_original_pair_routes r
                WHERE r.original_company_id=original_company AND r.first_account_id=NEW.first_account_id
                    AND r.second_account_id=NEW.second_account_id AND r.origin='SPACE'
                    AND r.space_id=NEW.space_id AND r.space_conversation_id=NEW.conversation_id
                    AND r.created_at=NEW.created_at) THEN
                RAISE EXCEPTION 'Canonical pair already selected original history' USING ERRCODE='23514';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER reserve_collaboration_space_pair_route BEFORE INSERT ON collaboration_space_room_intents
    FOR EACH ROW EXECUTE FUNCTION reserve_collaboration_space_pair_route();

-- Abort on conflicting preexisting histories; do not silently select a winner.
INSERT INTO collaboration_original_pair_routes
    (original_company_id,first_account_id,second_account_id,origin,legacy_conversation_id,created_at)
SELECT company_id,first_account_id,second_account_id,'LEGACY',id,created_at
FROM collaboration_direct_conversations;
INSERT INTO collaboration_original_pair_routes
    (original_company_id,first_account_id,second_account_id,origin,space_id,space_conversation_id,created_at)
SELECT substring(first_matrix_user_id from 7 for 32)::uuid,first_account_id,second_account_id,
    'SPACE',space_id,conversation_id,created_at
FROM collaboration_space_room_intents
WHERE origin='SPACE' AND first_matrix_user_id ~ '^@hris_[a-f0-9]{32}_'
    AND second_matrix_user_id='@hris_'||substring(first_matrix_user_id from 7 for 32)||'_'
        ||replace(second_account_id::text,'-','')||':'||matrix_server_name;
