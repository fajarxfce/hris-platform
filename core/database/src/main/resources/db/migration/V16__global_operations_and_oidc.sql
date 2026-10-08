-- A separate generated scope retains one receipt contract for company and account operations.
ALTER TABLE companies ADD CONSTRAINT company_nonzero_id CHECK(id<>'00000000-0000-0000-0000-000000000000'::uuid);
ALTER TABLE operation_receipts DROP CONSTRAINT operation_receipts_pkey;
ALTER TABLE operation_receipts ALTER COLUMN company_id DROP NOT NULL;
ALTER TABLE operation_receipts ADD scope_id uuid GENERATED ALWAYS AS (coalesce(company_id,'00000000-0000-0000-0000-000000000000'::uuid)) STORED;
ALTER TABLE operation_receipts ADD PRIMARY KEY(scope_id,actor_id,operation,operation_id);
DROP POLICY receipt_scope ON operation_receipts;
CREATE POLICY receipt_scope ON operation_receipts USING (
    actor_id=current_actor_id() AND company_id IS NOT DISTINCT FROM current_company_id()
) WITH CHECK (actor_id=current_actor_id() AND company_id IS NOT DISTINCT FROM current_company_id());
ALTER TABLE oidc_identities ADD id uuid NOT NULL DEFAULT gen_random_uuid(),
    ADD active boolean NOT NULL DEFAULT true,ADD version bigint NOT NULL DEFAULT 0,
    ADD linked_at timestamptz NOT NULL DEFAULT now(),ADD linked_by uuid REFERENCES accounts(id),ADD UNIQUE(id);
CREATE UNIQUE INDEX oidc_one_provider_identity ON oidc_identities(account_id,issuer) WHERE active;
CREATE INDEX oidc_account_directory ON oidc_identities(account_id,id);
CREATE FUNCTION protect_oidc_identity() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (OLD.id,OLD.issuer,OLD.subject,OLD.account_id,OLD.linked_at,OLD.linked_by) IS DISTINCT FROM
        (NEW.id,NEW.issuer,NEW.subject,NEW.account_id,NEW.linked_at,NEW.linked_by) THEN
        RAISE EXCEPTION 'OIDC identity bindings are immutable' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_oidc_binding BEFORE UPDATE ON oidc_identities FOR EACH ROW EXECUTE FUNCTION protect_oidc_identity();
CREATE TRIGGER retained_oidc_binding BEFORE DELETE ON oidc_identities FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
