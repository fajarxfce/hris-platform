-- A read-only projection for own devices and existing direct-conversation peers.
-- Private registry/key-publication RLS is unchanged for application roles. The
-- view owner has only the columns needed to derive public recipient eligibility;
-- application/worker roles are never made members of this non-login role.
DO $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_collaboration_recipient_reader') THEN
        CREATE ROLE hris_collaboration_recipient_reader NOLOGIN NOINHERIT NOSUPERUSER
            NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;
GRANT USAGE ON SCHEMA public TO hris_collaboration_recipient_reader;
GRANT SELECT(company_id,id,account_id,state,created_at,revoked_at)
    ON collaboration_devices TO hris_collaboration_recipient_reader;
GRANT SELECT(company_id,device_id,account_id,native_session_id)
    ON collaboration_device_sessions TO hris_collaboration_recipient_reader;
GRANT SELECT(company_id,device_id,account_id,fingerprint,version,published_at)
    ON collaboration_device_key_publications TO hris_collaboration_recipient_reader;
GRANT SELECT(id,account_id,created_at,expires_at,revoked_at,credential_version)
    ON native_sessions TO hris_collaboration_recipient_reader;
GRANT SELECT(id,active,security_version) ON accounts TO hris_collaboration_recipient_reader;
GRANT SELECT(id,active) ON companies TO hris_collaboration_recipient_reader;
GRANT SELECT(company_id,account_id,active)
    ON company_memberships TO hris_collaboration_recipient_reader;
GRANT SELECT(company_id,account_id,permission)
    ON membership_permissions TO hris_collaboration_recipient_reader;
GRANT SELECT(company_id,first_account_id,second_account_id)
    ON collaboration_direct_conversations TO hris_collaboration_recipient_reader;

CREATE POLICY collaboration_recipient_device_read ON collaboration_devices FOR SELECT
    TO hris_collaboration_recipient_reader USING(company_id=current_company_id());
CREATE POLICY collaboration_recipient_binding_read ON collaboration_device_sessions FOR SELECT
    TO hris_collaboration_recipient_reader USING(company_id=current_company_id());
CREATE POLICY collaboration_recipient_publication_read ON collaboration_device_key_publications FOR SELECT
    TO hris_collaboration_recipient_reader USING(company_id=current_company_id());

CREATE VIEW collaboration_recipient_devices WITH(security_barrier=true) AS
SELECT d.company_id,d.account_id,d.id AS device_id,k.fingerprint,max(s.expires_at) AS receive_until
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
    AND k.version=1 AND k.published_at<=CURRENT_TIMESTAMP
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
GROUP BY d.company_id,d.account_id,d.id,k.fingerprint;
ALTER VIEW collaboration_recipient_devices OWNER TO hris_collaboration_recipient_reader;
-- No keys, labels, native session IDs, login tokens, refresh metadata or MFA
-- timestamps are returned. Expired short access credentials do not revoke an
-- enrolled offline recipient. Current hard parent expiry/security/revocation do.
