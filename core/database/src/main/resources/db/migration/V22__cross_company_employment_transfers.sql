CREATE FUNCTION current_secondary_company_id() RETURNS uuid LANGUAGE sql STABLE AS $$
    SELECT nullif(current_setting('hris.secondary_company_id',true),'')::uuid
$$;
CREATE POLICY secondary_company_read ON companies FOR SELECT USING(id=current_secondary_company_id());
CREATE POLICY secondary_cancellation_read ON employment_revision_cancellations FOR SELECT USING(company_id=current_secondary_company_id());
CREATE POLICY secondary_unit_read ON organization_units FOR SELECT USING(company_id=current_secondary_company_id());
CREATE POLICY secondary_member_read ON company_memberships FOR SELECT USING(company_id=current_secondary_company_id());
CREATE POLICY secondary_permission_read ON membership_permissions FOR SELECT USING(company_id=current_secondary_company_id());
CREATE POLICY secondary_person_read ON persons FOR SELECT USING(owner_company_id=current_secondary_company_id() OR EXISTS(
    SELECT 1 FROM employments e WHERE e.person_id=persons.id AND e.company_id=current_secondary_company_id()
));
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['employments','employment_revisions'] LOOP
        EXECUTE format('CREATE POLICY secondary_read ON %I FOR SELECT USING(company_id=current_secondary_company_id())',t);
        EXECUTE format('CREATE POLICY secondary_insert ON %I FOR INSERT WITH CHECK(company_id=current_secondary_company_id())',t);
        EXECUTE format('CREATE POLICY secondary_update ON %I FOR UPDATE USING(company_id=current_secondary_company_id()) WITH CHECK(company_id=current_secondary_company_id())',t);
    END LOOP;
    FOREACH t IN ARRAY ARRAY['audit_entries','outbox_events'] LOOP
        EXECUTE format('CREATE POLICY secondary_read ON %I FOR SELECT USING(company_id=current_secondary_company_id())',t);
        EXECUTE format('CREATE POLICY secondary_insert ON %I FOR INSERT WITH CHECK(company_id=current_secondary_company_id() AND actor_id=current_actor_id())',t);
    END LOOP;
END $$;
ALTER TABLE employments ADD CONSTRAINT employment_person_identity UNIQUE(company_id,id,person_id);
CREATE TABLE employment_transfers (
    id uuid PRIMARY KEY,
    source_company_id uuid NOT NULL,
    source_employment_id uuid NOT NULL,
    target_company_id uuid NOT NULL,
    target_employment_id uuid NOT NULL,
    person_id uuid NOT NULL,
    source_version bigint NOT NULL CHECK(source_version>0),
    effective_date date NOT NULL,
    actor_id uuid NOT NULL REFERENCES accounts(id),
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    recorded_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(source_company_id,source_employment_id),
    UNIQUE(target_company_id,target_employment_id),
    CHECK(source_company_id<>target_company_id),
    FOREIGN KEY(source_company_id,source_employment_id,person_id) REFERENCES employments(company_id,id,person_id),
    FOREIGN KEY(target_company_id,target_employment_id,person_id) REFERENCES employments(company_id,id,person_id)
);
ALTER TABLE employment_transfers ENABLE ROW LEVEL SECURITY;
ALTER TABLE employment_transfers FORCE ROW LEVEL SECURITY;
CREATE POLICY transfer_read ON employment_transfers FOR SELECT USING(current_company_id() IN (source_company_id,target_company_id));
CREATE POLICY transfer_write ON employment_transfers FOR INSERT WITH CHECK(source_company_id=current_company_id() AND target_company_id=current_secondary_company_id() AND actor_id=current_actor_id());
CREATE TRIGGER immutable_employment_transfer BEFORE UPDATE OR DELETE ON employment_transfers FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

CREATE INDEX employment_future_manager ON employment_revisions(company_id,manager_id,effective_from) WHERE manager_id IS NOT NULL;
