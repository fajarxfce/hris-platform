-- Provider key publication emits device-list changes before HRIS records its
-- readback. Include the already committed immutable reservation so a concurrent
-- SDK query can verify those provider keys instead of caching an empty device list.
-- Missing provider keys are allowed only for unconfirmed reservations. No key
-- bytes are stored here; the gateway still verifies every returned public key.
GRANT SELECT(reserved_at) ON collaboration_device_key_publications
    TO hris_collaboration_recipient_reader;

CREATE OR REPLACE VIEW collaboration_recipient_devices WITH(security_barrier=true) AS
SELECT d.company_id,d.account_id,d.id AS device_id,k.fingerprint,max(s.expires_at) AS receive_until,
    (k.version=1) AS publication_confirmed
FROM public.collaboration_devices d
JOIN public.collaboration_device_key_publications k
    ON k.company_id=d.company_id AND k.account_id=d.account_id AND k.device_id=d.id
JOIN public.collaboration_device_sessions b
    ON b.company_id=d.company_id AND b.account_id=d.account_id AND b.device_id=d.id
JOIN public.native_sessions s ON s.id=b.native_session_id AND s.account_id=b.account_id
JOIN public.accounts a ON a.id=d.account_id
JOIN public.companies c ON c.id=d.company_id
JOIN public.company_memberships m ON m.company_id=d.company_id AND m.account_id=d.account_id
JOIN public.accounts caller ON caller.id=public.current_actor_id()
JOIN public.company_memberships cm ON cm.company_id=d.company_id AND cm.account_id=caller.id
WHERE d.company_id=public.current_company_id()
    AND d.state='ACTIVE' AND d.revoked_at IS NULL AND d.created_at<=CURRENT_TIMESTAMP
    AND k.reserved_at<=CURRENT_TIMESTAMP
    AND ((k.version=0 AND k.published_at IS NULL)
        OR (k.version=1 AND k.published_at<=CURRENT_TIMESTAMP))
    AND s.revoked_at IS NULL AND s.created_at<=CURRENT_TIMESTAMP AND s.expires_at>CURRENT_TIMESTAMP
    AND s.credential_version=a.security_version
    AND a.active AND c.active AND m.active AND caller.active AND cm.active
    AND EXISTS(SELECT 1 FROM public.membership_permissions p
        WHERE p.company_id=d.company_id AND p.account_id=d.account_id AND p.permission='collaboration.chat')
    AND EXISTS(SELECT 1 FROM public.membership_permissions p
        WHERE p.company_id=d.company_id AND p.account_id=caller.id AND p.permission='collaboration.chat')
    AND (d.account_id=caller.id OR EXISTS(SELECT 1 FROM public.collaboration_direct_conversations dc
        WHERE dc.company_id=d.company_id
            AND caller.id IN(dc.first_account_id,dc.second_account_id)
            AND d.account_id IN(dc.first_account_id,dc.second_account_id)))
GROUP BY d.company_id,d.account_id,d.id,k.fingerprint,k.version;
-- Preserve the restricted view owner, security barrier and existing grants.
ALTER VIEW collaboration_recipient_devices OWNER TO hris_collaboration_recipient_reader;
