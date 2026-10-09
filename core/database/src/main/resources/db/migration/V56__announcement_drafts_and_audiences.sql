CREATE TABLE audience_group_heads (
    company_id uuid NOT NULL REFERENCES companies(id), id uuid NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK(version BETWEEN 0 AND 999),
    PRIMARY KEY(company_id,id)
);
CREATE TABLE audience_group_revisions (
    company_id uuid NOT NULL, id uuid NOT NULL, version bigint NOT NULL CHECK(version BETWEEN 0 AND 999),
    name varchar(120) NOT NULL CHECK(length(trim(name))>0), active boolean NOT NULL,
    member_ids jsonb NOT NULL CHECK(jsonb_typeof(member_ids)='array' AND jsonb_array_length(member_ids)<=5000),
    member_count integer GENERATED ALWAYS AS (jsonb_array_length(member_ids)) STORED,
    recorded_at timestamptz NOT NULL CHECK(isfinite(recorded_at)), actor_id uuid NOT NULL REFERENCES accounts(id),
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    PRIMARY KEY(company_id,id,version), FOREIGN KEY(company_id,id) REFERENCES audience_group_heads(company_id,id)
);
ALTER TABLE audience_group_heads ADD FOREIGN KEY(company_id,id,version)
    REFERENCES audience_group_revisions(company_id,id,version) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE announcement_heads (
    company_id uuid NOT NULL REFERENCES companies(id), id uuid NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK(version BETWEEN 0 AND 999),
    PRIMARY KEY(company_id,id)
);
CREATE TABLE announcement_revisions (
    company_id uuid NOT NULL, id uuid NOT NULL, version bigint NOT NULL CHECK(version BETWEEN 0 AND 999),
    title varchar(200) NOT NULL CHECK(length(trim(title))>0),
    body varchar(16000) NOT NULL CHECK(length(trim(body))>0),
    audience_kind varchar(16) NOT NULL CHECK(audience_kind IN ('COMPANY','BRANCH','DEPARTMENT','GROUP')),
    target_ids jsonb NOT NULL CHECK(jsonb_typeof(target_ids)='array'),
    target_count integer GENERATED ALWAYS AS (jsonb_array_length(target_ids)) STORED,
    acknowledgement_required boolean NOT NULL, status varchar(16) NOT NULL CHECK(status='DRAFT'),
    recorded_at timestamptz NOT NULL CHECK(isfinite(recorded_at)), actor_id uuid NOT NULL REFERENCES accounts(id),
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    CHECK((audience_kind='COMPANY' AND jsonb_array_length(target_ids)=0) OR
        (audience_kind<>'COMPANY' AND jsonb_array_length(target_ids) BETWEEN 1 AND 32)),
    PRIMARY KEY(company_id,id,version), FOREIGN KEY(company_id,id) REFERENCES announcement_heads(company_id,id)
);
ALTER TABLE announcement_heads ADD FOREIGN KEY(company_id,id,version)
    REFERENCES announcement_revisions(company_id,id,version) DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION protect_communications_head() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 THEN RAISE EXCEPTION 'Communications history must start at zero' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    END IF;
    IF (NEW.company_id,NEW.id) IS DISTINCT FROM (OLD.company_id,OLD.id) OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Communications identity and version must be retained' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION validate_audience_group_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM audience_group_heads WHERE company_id=NEW.company_id AND id=NEW.id AND version=NEW.version) OR
        jsonb_array_length(NEW.member_ids)<>(SELECT count(DISTINCT value::uuid) FROM jsonb_array_elements_text(NEW.member_ids)) OR
        EXISTS(SELECT 1 FROM jsonb_array_elements_text(NEW.member_ids) m
            LEFT JOIN employments e ON e.company_id=NEW.company_id AND e.id=m.value::uuid WHERE e.id IS NULL) THEN
        RAISE EXCEPTION 'An audience revision requires matching company employment references' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER audience_revision_references BEFORE INSERT ON audience_group_revisions
    FOR EACH ROW EXECUTE FUNCTION validate_audience_group_revision();

CREATE FUNCTION validate_announcement_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM announcement_heads WHERE company_id=NEW.company_id AND id=NEW.id AND version=NEW.version) OR
        jsonb_array_length(NEW.target_ids)<>(SELECT count(DISTINCT value::uuid) FROM jsonb_array_elements_text(NEW.target_ids)) THEN
        RAISE EXCEPTION 'An announcement requires a matching revision and distinct targets' USING ERRCODE='23514';
    END IF;
    IF NEW.audience_kind='GROUP' AND EXISTS(SELECT 1 FROM jsonb_array_elements_text(NEW.target_ids) t
        LEFT JOIN audience_group_heads g ON g.company_id=NEW.company_id AND g.id=t.value::uuid WHERE g.id IS NULL) THEN
        RAISE EXCEPTION 'An announcement requires company group references' USING ERRCODE='23514';
    END IF;
    IF NEW.audience_kind IN ('BRANCH','DEPARTMENT') AND EXISTS(SELECT 1 FROM jsonb_array_elements_text(NEW.target_ids) t
        LEFT JOIN organization_units u ON u.company_id=NEW.company_id AND u.id=t.value::uuid AND u.kind=NEW.audience_kind WHERE u.id IS NULL) THEN
        RAISE EXCEPTION 'An announcement requires company unit references' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER announcement_revision_references BEFORE INSERT ON announcement_revisions
    FOR EACH ROW EXECUTE FUNCTION validate_announcement_revision();

DO $$ DECLARE name text; BEGIN
    FOREACH name IN ARRAY ARRAY['audience_group_heads','audience_group_revisions','announcement_heads','announcement_revisions'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',name);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',name);
    END LOOP;
    FOREACH name IN ARRAY ARRAY['audience_group_revisions','announcement_revisions'] LOOP
        EXECUTE format('CREATE POLICY revision_author ON %I AS RESTRICTIVE FOR INSERT WITH CHECK(actor_id=current_actor_id())',name);
        EXECUTE format('CREATE TRIGGER immutable_revision BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',name);
    END LOOP;
    FOREACH name IN ARRAY ARRAY['audience_group_heads','announcement_heads'] LOOP
        EXECUTE format('CREATE TRIGGER retained_head BEFORE DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',name);
        EXECUTE format('CREATE TRIGGER versioned_head BEFORE INSERT OR UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION protect_communications_head()',name);
    END LOOP;
END $$;
