-- The room requester can differ from the conversation creator. Preserve the
-- original binding creator for later canonical-user verification and adoption.
-- Append the column so the view's existing order, grants and owner are retained.
GRANT SELECT(account_id) ON collaboration_direct_room_bindings
    TO hris_collaboration_legacy_reader;
CREATE OR REPLACE VIEW collaboration_space_legacy_conversations WITH(security_barrier=true) AS
SELECT space.space_id,c.company_id AS legacy_company_id,c.id AS legacy_conversation_id,
    c.first_account_id,c.second_account_id,c.created_by,c.created_at,c.version,
    b.matrix_server_name,b.matrix_room_id,b.created_at AS room_bound_at,
    b.account_id AS room_created_by
FROM public.collaboration_space_directory space
CROSS JOIN public.collaboration_direct_conversations c
LEFT JOIN public.collaboration_direct_room_bindings b
    ON b.company_id=c.company_id AND b.conversation_id=c.id
WHERE public.current_actor_id() IN(c.first_account_id,c.second_account_id);
