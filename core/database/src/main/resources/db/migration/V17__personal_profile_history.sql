CREATE TABLE person_profile_revisions (
    person_id uuid NOT NULL REFERENCES persons(id),
    revision bigint NOT NULL CHECK(revision >= 0),
    owner_company_id uuid NOT NULL REFERENCES companies(id),
    legal_name varchar(200) NOT NULL,
    birth_date date,
    nationality char(2) NOT NULL,
    email varchar(254),
    actor_id uuid REFERENCES accounts(id),
    reason varchar(1000) NOT NULL CHECK(length(trim(reason)) > 0),
    recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(person_id, revision)
);
INSERT INTO person_profile_revisions(person_id,revision,owner_company_id,legal_name,birth_date,nationality,email,reason,recorded_at)
SELECT id,version,owner_company_id,legal_name,birth_date,nationality,email,'Initial migrated profile',created_at FROM persons;
ALTER TABLE person_profile_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE person_profile_revisions FORCE ROW LEVEL SECURITY;
CREATE POLICY profile_revision_scope ON person_profile_revisions USING (
    EXISTS(SELECT 1 FROM persons p WHERE p.id=person_id)
) WITH CHECK (owner_company_id=current_company_id() AND actor_id=current_actor_id());
CREATE TRIGGER immutable_person_profile_history BEFORE UPDATE OR DELETE ON person_profile_revisions
FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE FUNCTION protect_person_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id,NEW.owner_company_id,NEW.account_id) IS DISTINCT FROM (OLD.id,OLD.owner_company_id,OLD.account_id)
       OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Person identity is immutable and changes must be versioned' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_person_identity BEFORE UPDATE ON persons FOR EACH ROW EXECUTE FUNCTION protect_person_identity();
