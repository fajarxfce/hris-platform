ALTER TABLE person_profile_revisions ADD COLUMN account_id uuid REFERENCES accounts(id);
ALTER TABLE person_profile_revisions DISABLE TRIGGER immutable_person_profile_history;
UPDATE person_profile_revisions h SET account_id=p.account_id FROM persons p WHERE p.id=h.person_id;
ALTER TABLE person_profile_revisions ENABLE TRIGGER immutable_person_profile_history;

CREATE TABLE person_account_links (
    person_id uuid PRIMARY KEY REFERENCES persons(id),
    company_id uuid NOT NULL REFERENCES companies(id),
    account_id uuid NOT NULL UNIQUE REFERENCES accounts(id),
    profile_version bigint NOT NULL CHECK(profile_version>0),
    actor_id uuid NOT NULL REFERENCES accounts(id),
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    recorded_at timestamptz NOT NULL DEFAULT now(),
    CHECK(actor_id<>account_id)
);
ALTER TABLE person_account_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE person_account_links FORCE ROW LEVEL SECURITY;
CREATE POLICY person_account_link_scope ON person_account_links
USING(company_id=current_company_id())
WITH CHECK(company_id=current_company_id() AND actor_id=current_actor_id()
    AND EXISTS(SELECT 1 FROM persons p WHERE p.id=person_id AND p.owner_company_id=current_company_id() AND p.account_id IS NULL));
CREATE TRIGGER immutable_person_account_links BEFORE UPDATE OR DELETE ON person_account_links
FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

CREATE OR REPLACE FUNCTION protect_person_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id,NEW.owner_company_id) IS DISTINCT FROM (OLD.id,OLD.owner_company_id)
       OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Person identity is immutable and changes must be versioned' USING ERRCODE='42501';
    END IF;
    IF NEW.account_id IS DISTINCT FROM OLD.account_id THEN
        IF OLD.account_id IS NOT NULL OR NEW.account_id IS NULL
            OR (NEW.legal_name,NEW.birth_date,NEW.nationality,NEW.email) IS DISTINCT FROM (OLD.legal_name,OLD.birth_date,OLD.nationality,OLD.email)
            OR NOT EXISTS(SELECT 1 FROM person_account_links l WHERE l.person_id=NEW.id AND l.account_id=NEW.account_id
                AND l.company_id=NEW.owner_company_id AND l.profile_version=NEW.version AND l.actor_id=current_actor_id()) THEN
            RAISE EXCEPTION 'Initial account binding requires an authorized record' USING ERRCODE='42501';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE FUNCTION verify_person_account_link() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM persons p JOIN person_profile_revisions h ON h.person_id=p.id
        WHERE p.id=NEW.person_id AND p.owner_company_id=NEW.company_id AND p.account_id=NEW.account_id
        AND p.version>=NEW.profile_version AND h.revision=NEW.profile_version AND h.account_id=NEW.account_id
        AND h.actor_id=NEW.actor_id) THEN
        RAISE EXCEPTION 'Account binding must include its profile history' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER person_account_link_attached AFTER INSERT ON person_account_links
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_person_account_link();
