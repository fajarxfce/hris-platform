-- V121 retirement requires device REVOKED; ACTIVE filtering excludes that case.
-- This view carries public-key candidates, never native or bearer
-- authority. Receiving does not depend on short access/gateway/provider expiry.
DO $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_collaboration_space_recipient_reader') THEN
        CREATE ROLE hris_collaboration_space_recipient_reader NOLOGIN NOINHERIT NOSUPERUSER
            NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;
GRANT USAGE ON SCHEMA public TO hris_collaboration_space_recipient_reader;
GRANT SELECT(space_id,account_id,matrix_server_name,matrix_user_id,identity_selected_at)
    ON collaboration_space_members TO hris_collaboration_space_recipient_reader;
GRANT SELECT(id,active,security_version) ON accounts TO hris_collaboration_space_recipient_reader;
GRANT SELECT(space_id,id,account_id,origin,legacy_key_fingerprint,origin_native_session_id,
    matrix_user_id,matrix_device_id,state,version,created_at,updated_at,revoked_at)
    ON collaboration_space_devices TO hris_collaboration_space_recipient_reader;
GRANT SELECT(space_id,device_id,account_id,native_session_id,bound_at)
    ON collaboration_space_device_sessions TO hris_collaboration_space_recipient_reader;
GRANT SELECT(id,account_id,created_at,rotated_at,expires_at,revoked_at,credential_version)
    ON native_sessions TO hris_collaboration_space_recipient_reader;
GRANT SELECT(space_id,account_id,device_id,native_session_id,matrix_user_id,matrix_device_id,
    fingerprint,version,reserved_at,published_at)
    ON collaboration_space_key_publications TO hris_collaboration_space_recipient_reader;
GRANT SELECT(space_id,device_id,account_id,native_session_id,matrix_user_id,matrix_device_id,
    fingerprint,target_device_version,completed_at)
    ON collaboration_legacy_runtime_completions TO hris_collaboration_space_recipient_reader;
GRANT SELECT(space_id,conversation_id,first_account_id,second_account_id,
    first_matrix_user_id,second_matrix_user_id,matrix_server_name,created_at)
    ON collaboration_space_room_intents TO hris_collaboration_space_recipient_reader;
GRANT SELECT(space_id,conversation_id,created_at)
    ON collaboration_space_room_bindings TO hris_collaboration_space_recipient_reader;

-- The view owner cannot be assumed by the runtime role and cannot read any
-- credential/hash/ciphertext columns. Application RLS policies remain unchanged.
CREATE POLICY collaboration_space_recipient_device_read ON collaboration_space_devices FOR SELECT
    TO hris_collaboration_space_recipient_reader USING(true);
CREATE POLICY collaboration_space_recipient_binding_read ON collaboration_space_device_sessions FOR SELECT
    TO hris_collaboration_space_recipient_reader USING(true);
CREATE POLICY collaboration_space_recipient_native_read ON native_sessions FOR SELECT
    TO hris_collaboration_space_recipient_reader USING(true);
CREATE POLICY collaboration_space_recipient_key_read ON collaboration_space_key_publications FOR SELECT
    TO hris_collaboration_space_recipient_reader USING(true);
CREATE POLICY collaboration_space_recipient_completion_read ON collaboration_legacy_runtime_completions FOR SELECT
    TO hris_collaboration_space_recipient_reader USING(true);

CREATE VIEW collaboration_space_recipient_devices WITH(security_barrier=true) AS
SELECT d.space_id,d.account_id,d.id AS device_id,m.matrix_server_name,d.matrix_user_id,
    d.matrix_device_id,k.fingerprint,n.expires_at AS receive_until,
    (k.version=1) AS publication_confirmed
FROM public.collaboration_space_devices d
JOIN public.collaboration_space_members m
    ON m.space_id=d.space_id AND m.account_id=d.account_id AND m.matrix_user_id=d.matrix_user_id
JOIN public.collaboration_space_members caller
    ON caller.space_id=d.space_id AND caller.account_id=public.current_actor_id()
JOIN public.collaboration_space_key_publications k
    ON k.space_id=d.space_id AND k.account_id=d.account_id AND k.device_id=d.id
        AND k.native_session_id=d.origin_native_session_id
        AND k.matrix_user_id=d.matrix_user_id AND k.matrix_device_id=d.matrix_device_id
JOIN public.collaboration_space_device_sessions b
    ON b.space_id=d.space_id AND b.account_id=d.account_id AND b.device_id=d.id
        AND b.native_session_id=d.origin_native_session_id
JOIN public.native_sessions n ON n.id=b.native_session_id AND n.account_id=d.account_id
JOIN public.accounts a ON a.id=d.account_id
WHERE public.current_company_id() IS NULL
    AND d.state='ACTIVE' AND d.revoked_at IS NULL
    AND d.created_at<=statement_timestamp() AND d.updated_at<=statement_timestamp()
    AND m.identity_selected_at<=statement_timestamp()
    AND caller.identity_selected_at<=statement_timestamp()
    AND m.matrix_server_name=caller.matrix_server_name
    AND k.reserved_at<=statement_timestamp()
    AND ((k.version=0 AND k.published_at IS NULL)
        OR (k.version=1 AND k.published_at<=statement_timestamp()))
    AND b.bound_at<=statement_timestamp() AND n.created_at<=statement_timestamp()
    AND n.rotated_at<=statement_timestamp() AND n.expires_at>statement_timestamp()
    AND n.revoked_at IS NULL AND n.credential_version=a.security_version AND a.active
    AND (d.origin='SPACE' OR (d.origin='LEGACY'
        AND k.fingerprint=d.legacy_key_fingerprint AND EXISTS(
            SELECT 1 FROM public.collaboration_legacy_runtime_completions c
            WHERE c.space_id=d.space_id AND c.device_id=d.id AND c.account_id=d.account_id
                AND c.native_session_id=d.origin_native_session_id
                AND c.matrix_user_id=d.matrix_user_id AND c.matrix_device_id=d.matrix_device_id
                AND c.fingerprint=k.fingerprint AND c.target_device_version<=d.version
                AND c.completed_at<=k.reserved_at)))
    AND (d.account_id=caller.account_id OR EXISTS(
        SELECT 1 FROM public.collaboration_space_room_intents i
        JOIN public.collaboration_space_room_bindings room
            ON room.space_id=i.space_id AND room.conversation_id=i.conversation_id
        WHERE i.space_id=d.space_id AND i.created_at<=statement_timestamp()
            AND room.created_at<=statement_timestamp() AND i.matrix_server_name=m.matrix_server_name
            AND ((i.first_account_id=caller.account_id AND i.second_account_id=d.account_id
                AND i.first_matrix_user_id=caller.matrix_user_id AND i.second_matrix_user_id=d.matrix_user_id)
                OR (i.second_account_id=caller.account_id AND i.first_account_id=d.account_id
                    AND i.second_matrix_user_id=caller.matrix_user_id AND i.first_matrix_user_id=d.matrix_user_id))));
ALTER VIEW collaboration_space_recipient_devices OWNER TO hris_collaboration_space_recipient_reader;
