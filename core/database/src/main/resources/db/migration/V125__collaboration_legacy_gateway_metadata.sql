-- Current group workers need an exact original public-gateway version before
-- retained completion. This owned projection never exposes credential material.
GRANT SELECT(company_id,id,account_id,device_id,native_session_id,version,created_at,rotated_at,revoked_at)
    ON collaboration_gateway_sessions TO hris_collaboration_legacy_reader;
CREATE POLICY collaboration_gateway_owned_metadata ON collaboration_gateway_sessions FOR SELECT
    TO hris_collaboration_legacy_reader USING(account_id=current_actor_id());

CREATE VIEW collaboration_space_legacy_gateways WITH(security_barrier=true) AS
SELECT space.space_id,gateway.account_id,gateway.company_id AS legacy_company_id,
    gateway.device_id AS legacy_device_id,gateway.id AS gateway_id,gateway.native_session_id,
    gateway.version,gateway.created_at,gateway.rotated_at,gateway.revoked_at
FROM public.collaboration_space_directory space
CROSS JOIN public.collaboration_gateway_sessions gateway
WHERE gateway.account_id=public.current_actor_id();
ALTER VIEW collaboration_space_legacy_gateways OWNER TO hris_collaboration_legacy_reader;

-- An absent row means no visible owned gateway. Callers must separately validate
-- current native/group authority, exact original parent, state and version.
-- Retired or parent-purged heads remain metadata; they do not grant group adoption.
-- Hashes, replay envelopes, provider tokens, HR data and encrypted purposes do not
-- change. Application roles do not become members of the view's non-login owner.
