-- Preserve the original provider identities and crypto namespaces during the
-- organization-space migration. This owned metadata projection never supplies a
-- bearer, decryption key, HR permission, device adoption or room grant.
DO $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_collaboration_legacy_reader') THEN
        CREATE ROLE hris_collaboration_legacy_reader NOLOGIN NOINHERIT NOSUPERUSER
            NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;
GRANT USAGE ON SCHEMA public TO hris_collaboration_legacy_reader;
GRANT SELECT ON collaboration_space_directory TO hris_collaboration_legacy_reader;
GRANT SELECT(company_id,id,account_id,platform,state,version,created_at,revoked_at)
    ON collaboration_devices TO hris_collaboration_legacy_reader;
GRANT SELECT(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,created_at,cleared_at)
    ON collaboration_provider_sessions TO hris_collaboration_legacy_reader;
CREATE POLICY collaboration_provider_owned_metadata ON collaboration_provider_sessions FOR SELECT
    TO hris_collaboration_legacy_reader USING(account_id=current_actor_id());

CREATE VIEW collaboration_space_legacy_devices WITH(security_barrier=true) AS
SELECT space.space_id,device.account_id,device.company_id AS legacy_company_id,
    device.id AS legacy_device_id,device.platform,device.state,device.version AS device_version,
    device.created_at AS device_created_at,device.revoked_at AS device_revoked_at,
    provider.matrix_user_id,provider.matrix_device_id,provider.generation AS credential_generation,
    provider.created_at AS identity_observed_at,provider.cleared_at AS credential_retired_at
FROM public.collaboration_space_directory space
CROSS JOIN public.collaboration_devices device
JOIN public.collaboration_provider_sessions provider
    ON provider.company_id=device.company_id AND provider.device_id=device.id
    AND provider.account_id=device.account_id
WHERE device.account_id=public.current_actor_id();
ALTER VIEW collaboration_space_legacy_devices OWNER TO hris_collaboration_legacy_reader;

-- No original row is rewritten and no identity is selected automatically. The
-- original company is a retained crypto namespace, never a substitute space ID.
-- Do not join native bindings here: ordinary parent cleanup must not erase this
-- record of the provider identity. Adoption separately requires a current parent.
