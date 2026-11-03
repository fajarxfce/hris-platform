-- One organization group per installation. Its communication identity must not
-- change when the employee changes the selected HR company. Existing provider
-- identities, device keys, company rooms and permissions are deliberately retained.
CREATE TABLE collaboration_spaces (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    singleton boolean NOT NULL DEFAULT true CHECK(singleton),
    UNIQUE(singleton)
);
INSERT INTO collaboration_spaces DEFAULT VALUES;
ALTER TABLE collaboration_spaces ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_spaces FORCE ROW LEVEL SECURITY;
CREATE TRIGGER immutable_collaboration_space BEFORE UPDATE OR DELETE ON collaboration_spaces
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

-- The view owner can read only public contact and eligibility columns. It cannot
-- read email, credentials, profiles, employment, documents, sessions or keys.
-- Application roles are never made members of this non-login role.
DO $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_collaboration_directory_reader') THEN
        CREATE ROLE hris_collaboration_directory_reader NOLOGIN NOINHERIT NOSUPERUSER
            NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;
GRANT USAGE ON SCHEMA public TO hris_collaboration_directory_reader;
GRANT SELECT(id) ON collaboration_spaces TO hris_collaboration_directory_reader;
GRANT SELECT(id,display_name,active) ON accounts TO hris_collaboration_directory_reader;
GRANT SELECT(id,name,active) ON companies TO hris_collaboration_directory_reader;
GRANT SELECT(company_id,account_id,active)
    ON company_memberships TO hris_collaboration_directory_reader;
GRANT SELECT(company_id,account_id,permission)
    ON membership_permissions TO hris_collaboration_directory_reader;

CREATE POLICY collaboration_directory_space_read ON collaboration_spaces FOR SELECT
    TO hris_collaboration_directory_reader USING(true);
CREATE POLICY collaboration_directory_company_read ON companies FOR SELECT
    TO hris_collaboration_directory_reader USING(true);
CREATE POLICY collaboration_directory_membership_read ON company_memberships FOR SELECT
    TO hris_collaboration_directory_reader USING(true);
CREATE POLICY collaboration_directory_permission_read ON membership_permissions FOR SELECT
    TO hris_collaboration_directory_reader USING(true);

-- Eligibility in any active company admits this minimal organization directory.
-- Company selection neither expands HR permissions nor hides eligible colleagues.
-- This projection is not native-session, provider-device or room admission.
CREATE VIEW collaboration_space_directory WITH(security_barrier=true) AS
SELECT space.id AS space_id
FROM public.collaboration_spaces space
JOIN public.accounts caller ON caller.id=public.current_actor_id()
WHERE caller.active AND EXISTS(
    SELECT 1 FROM public.company_memberships membership
    JOIN public.companies company ON company.id=membership.company_id
    JOIN public.membership_permissions permission
        ON permission.company_id=membership.company_id
        AND permission.account_id=membership.account_id
    WHERE membership.account_id=caller.id AND membership.active AND company.active
        AND permission.permission='collaboration.chat'
);
ALTER VIEW collaboration_space_directory OWNER TO hris_collaboration_directory_reader;

-- One row per eligible account/company affiliation; use-case queries must bound
-- account pages and company-label aggregation. Personal login email and person/
-- employment identifiers are absent. Unlinked employees are a separate HR-owned
-- projection and are never represented as reachable chat accounts here.
CREATE VIEW collaboration_space_contacts WITH(security_barrier=true) AS
SELECT space.space_id,account.id AS account_id,account.display_name,
    company.id AS company_id,company.name AS company_label
FROM public.collaboration_space_directory space
CROSS JOIN public.accounts account
JOIN public.company_memberships membership ON membership.account_id=account.id
JOIN public.companies company ON company.id=membership.company_id
WHERE account.active AND membership.active AND company.active
    AND EXISTS(
        SELECT 1 FROM public.membership_permissions permission
        WHERE permission.company_id=membership.company_id
            AND permission.account_id=account.id AND permission.permission='collaboration.chat'
    );
ALTER VIEW collaboration_space_contacts OWNER TO hris_collaboration_directory_reader;
