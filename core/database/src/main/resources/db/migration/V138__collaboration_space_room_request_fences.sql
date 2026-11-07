-- Explicit stable-v2 non-execution evidence. This is not an operation receipt,
-- room publication, successor intent, provider permission or message acknowledgement.
CREATE TABLE collaboration_space_room_request_fences (
    account_id uuid NOT NULL REFERENCES accounts(id) CHECK(account_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    operation_id uuid NOT NULL CHECK(operation_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    space_id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    device_id uuid NOT NULL CHECK(device_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    expected_device_version bigint NOT NULL CHECK(expected_device_version BETWEEN 0 AND 999999),
    expected_request_account_id uuid,
    expected_request_revision bigint,
    payload_hash varchar(64) NOT NULL CHECK(payload_hash ~ '^[a-f0-9]{64}$'),
    fence_id uuid NOT NULL UNIQUE CHECK(fence_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    PRIMARY KEY(account_id,operation_id),
    FOREIGN KEY(space_id,conversation_id) REFERENCES collaboration_space_direct_conversations(space_id,id),
    CHECK((expected_request_account_id IS NULL AND expected_request_revision IS NULL)
        OR (expected_request_account_id IS NOT NULL AND expected_request_revision IS NOT NULL
            AND expected_request_account_id<>'00000000-0000-0000-0000-000000000000'::uuid
            AND expected_request_revision BETWEEN 0 AND 999999))
);
ALTER TABLE collaboration_space_room_request_fences ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_room_request_fences FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_space_room_request_fence_read ON collaboration_space_room_request_fences FOR SELECT
    USING(current_company_id() IS NULL AND account_id=current_actor_id());
CREATE POLICY collaboration_space_room_request_fence_insert ON collaboration_space_room_request_fences FOR INSERT
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND EXISTS(SELECT 1 FROM collaboration_space_directory s
            WHERE s.space_id=collaboration_space_room_request_fences.space_id));

CREATE FUNCTION protect_collaboration_space_room_request_fence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF current_company_id() IS NOT NULL OR NEW.account_id IS DISTINCT FROM current_actor_id()
        OR NEW.created_at>clock_timestamp() THEN
        RAISE EXCEPTION 'Invalid organization room fence owner or time' USING ERRCODE='23514';
    END IF;
    -- Exact existing OperationReceiptDataSource key, including Kotlin's literal null.
    -- Owning callers already acquired this before room/account/native guards.
    PERFORM pg_advisory_xact_lock(hashtextextended(
        'null:'||NEW.account_id::text||':collaboration.space_room_setup.v2:'||NEW.operation_id::text,0));
    IF EXISTS(SELECT 1 FROM operation_receipts r WHERE r.company_id IS NULL
        AND r.actor_id=NEW.account_id AND r.operation='collaboration.space_room_setup.v2'
        AND r.operation_id=NEW.operation_id) THEN
        RAISE EXCEPTION 'An executed room request cannot be fenced' USING ERRCODE='23514';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM collaboration_space_direct_conversations c
        WHERE c.space_id=NEW.space_id AND c.id=NEW.conversation_id
            AND NEW.account_id IN(c.first_account_id,c.second_account_id)
            AND (NEW.expected_request_account_id IS NULL
                OR NEW.expected_request_account_id IN(c.first_account_id,c.second_account_id))
            AND c.created_at<=NEW.created_at) THEN
        RAISE EXCEPTION 'Organization room fence conversation changed' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_space_room_request_fence BEFORE INSERT ON collaboration_space_room_request_fences
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_space_room_request_fence();
CREATE TRIGGER immutable_collaboration_space_room_request_fence BEFORE UPDATE OR DELETE ON collaboration_space_room_request_fences
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

-- A late stable writer must not commit a receipt after a fence, even if its queue
-- mutation preceded receipt insertion in the same transaction. Legacy is separate.
CREATE FUNCTION protect_collaboration_space_room_request_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('UPDATE','DELETE') THEN
        IF OLD.company_id IS NULL AND OLD.operation='collaboration.space_room_setup.v2' THEN
            RAISE EXCEPTION 'Stable room receipts are retained' USING ERRCODE='23514';
        END IF;
        IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    END IF;
    IF NEW.company_id IS NULL AND NEW.operation='collaboration.space_room_setup.v2' THEN
        IF TG_OP='UPDATE' THEN
            RAISE EXCEPTION 'Stable room receipts require original insertion' USING ERRCODE='23514';
        END IF;
        PERFORM pg_advisory_xact_lock(hashtextextended(
            'null:'||NEW.actor_id::text||':collaboration.space_room_setup.v2:'||NEW.operation_id::text,0));
        IF EXISTS(SELECT 1 FROM collaboration_space_room_request_fences f
            WHERE f.account_id=NEW.actor_id AND f.operation_id=NEW.operation_id) THEN
            RAISE EXCEPTION 'Original organization room request is fenced' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_space_room_request_receipt BEFORE INSERT OR UPDATE OR DELETE ON operation_receipts
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_space_room_request_receipt();

-- No expiry, delete policy or native/device foreign key. The original may outlive
-- that parent/device; current authentication and pair admission belong to the use case.
