CREATE TABLE document_retention_policies (
    company_id uuid NOT NULL REFERENCES companies(id),id uuid NOT NULL,
    classification varchar(16) NOT NULL CHECK(classification IN ('PERSONAL','HR_ONLY','RECEIPT')),
    version bigint NOT NULL CHECK(version BETWEEN 0 AND 9999),
    PRIMARY KEY(company_id,id),UNIQUE(company_id,classification)
);
CREATE TABLE document_retention_policy_revisions (
    company_id uuid NOT NULL,policy_id uuid NOT NULL,version bigint NOT NULL CHECK(version BETWEEN 0 AND 9999),
    retention_days integer CHECK(retention_days BETWEEN 1 AND 36500),
    actor_id uuid NOT NULL REFERENCES accounts(id),recorded_at timestamptz NOT NULL,reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    PRIMARY KEY(company_id,policy_id,version),FOREIGN KEY(company_id,policy_id) REFERENCES document_retention_policies(company_id,id)
);
ALTER TABLE document_retention_policies ADD FOREIGN KEY(company_id,id,version)
    REFERENCES document_retention_policy_revisions(company_id,policy_id,version) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE document_retention_states (
    company_id uuid NOT NULL,document_id uuid NOT NULL,version bigint NOT NULL CHECK(version BETWEEN 0 AND 9999),
    PRIMARY KEY(company_id,document_id),FOREIGN KEY(company_id,document_id) REFERENCES documents(company_id,id)
);
CREATE TABLE document_retention_changes (
    company_id uuid NOT NULL,document_id uuid NOT NULL,version bigint NOT NULL CHECK(version BETWEEN 0 AND 9999),
    kind varchar(20) NOT NULL CHECK(kind IN ('ARCHIVE','RESTORE','PLACE_HOLD','RELEASE_HOLD')),
    legal_hold boolean NOT NULL,archived_at timestamptz,policy_id uuid,policy_version bigint,
    retention_days integer CHECK(retention_days BETWEEN 1 AND 36500),eligible_at timestamptz,
    actor_id uuid NOT NULL REFERENCES accounts(id),recorded_at timestamptz NOT NULL,reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    PRIMARY KEY(company_id,document_id,version),FOREIGN KEY(company_id,document_id) REFERENCES document_retention_states(company_id,document_id),
    FOREIGN KEY(company_id,policy_id,policy_version) REFERENCES document_retention_policy_revisions(company_id,policy_id,version),
    CHECK((policy_id IS NULL)=(policy_version IS NULL)),
    CHECK(policy_id IS NOT NULL OR retention_days IS NULL),
    CHECK((archived_at IS NULL AND policy_id IS NULL AND policy_version IS NULL AND retention_days IS NULL AND eligible_at IS NULL) OR
        (archived_at IS NOT NULL AND ((retention_days IS NULL AND eligible_at IS NULL) OR
            (retention_days IS NOT NULL AND eligible_at IS NOT NULL AND eligible_at=archived_at+(retention_days*interval '24 hours')))))
);
ALTER TABLE document_retention_states ADD FOREIGN KEY(company_id,document_id,version)
    REFERENCES document_retention_changes(company_id,document_id,version) DEFERRABLE INITIALLY DEFERRED;
DO $$ DECLARE name text; BEGIN
    FOREACH name IN ARRAY ARRAY['document_retention_policies','document_retention_policy_revisions','document_retention_states','document_retention_changes'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',name);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',name);
        EXECUTE format('CREATE TRIGGER preserve_retention_history BEFORE DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',name);
    END LOOP;
    FOREACH name IN ARRAY ARRAY['document_retention_policy_revisions','document_retention_changes'] LOOP
        EXECUTE format('CREATE POLICY evidence_author ON %I AS RESTRICTIVE FOR INSERT WITH CHECK(actor_id=current_actor_id())',name);
        EXECUTE format('CREATE TRIGGER immutable_evidence BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',name);
    END LOOP;
END $$;
CREATE FUNCTION protect_document_retention_policy() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 THEN RAISE EXCEPTION 'Retention policies must start at revision zero' USING ERRCODE='23514';END IF;
    ELSIF (NEW.company_id,NEW.id,NEW.classification) IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.classification) OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Retention policy identity is immutable and changes must be sequenced' USING ERRCODE='42501';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER retention_policy_sequence BEFORE INSERT OR UPDATE ON document_retention_policies FOR EACH ROW EXECUTE FUNCTION protect_document_retention_policy();
CREATE FUNCTION protect_document_retention_state() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 THEN RAISE EXCEPTION 'Retention state must start at revision zero' USING ERRCODE='23514';END IF;
    ELSIF (NEW.company_id,NEW.document_id) IS DISTINCT FROM (OLD.company_id,OLD.document_id) OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Retention state identity is immutable and changes must be sequenced' USING ERRCODE='42501';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER retention_state_sequence BEFORE INSERT OR UPDATE ON document_retention_states FOR EACH ROW EXECUTE FUNCTION protect_document_retention_state();
CREATE FUNCTION validate_document_retention_policy_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM document_retention_policies p WHERE p.company_id=NEW.company_id AND p.id=NEW.policy_id AND p.version=NEW.version) THEN
        RAISE EXCEPTION 'Policy history must match its current revision' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER retention_policy_revision_scope BEFORE INSERT ON document_retention_policy_revisions FOR EACH ROW EXECUTE FUNCTION validate_document_retention_policy_revision();
CREATE FUNCTION validate_document_retention_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE prior document_retention_changes%ROWTYPE;
BEGIN
    IF NOT EXISTS(SELECT 1 FROM document_retention_states s WHERE s.company_id=NEW.company_id AND s.document_id=NEW.document_id AND s.version=NEW.version) THEN
        RAISE EXCEPTION 'Retention evidence must match the current state' USING ERRCODE='23514';END IF;
    SELECT * INTO prior FROM document_retention_changes WHERE company_id=NEW.company_id AND document_id=NEW.document_id AND version=NEW.version-1;
    IF NEW.version>0 AND prior IS NULL THEN RAISE EXCEPTION 'Retention history cannot skip revisions' USING ERRCODE='23514';END IF;
    IF NEW.kind='ARCHIVE' THEN
        IF prior.archived_at IS NOT NULL OR NEW.archived_at IS DISTINCT FROM NEW.recorded_at OR NEW.legal_hold<>coalesce(prior.legal_hold,false) THEN
            RAISE EXCEPTION 'Invalid archival transition' USING ERRCODE='23514';END IF;
        IF EXISTS(SELECT 1 FROM document_revisions r WHERE r.company_id=NEW.company_id AND r.document_id=NEW.document_id AND r.status IN ('UPLOADING','VALIDATING','VALIDATION_FAILED')) THEN
            RAISE EXCEPTION 'Cancel unfinished document revisions before archival' USING ERRCODE='23514';END IF;
        IF NEW.policy_id IS NULL AND EXISTS(SELECT 1 FROM document_retention_policies p JOIN documents d ON d.company_id=p.company_id AND d.classification=p.classification WHERE d.company_id=NEW.company_id AND d.id=NEW.document_id) THEN
            RAISE EXCEPTION 'Archival must retain the configured classification policy' USING ERRCODE='23514';END IF;
        IF NEW.policy_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM document_retention_policies p JOIN document_retention_policy_revisions r ON r.company_id=p.company_id AND r.policy_id=p.id AND r.version=p.version
            JOIN documents d ON d.company_id=p.company_id AND d.id=NEW.document_id AND d.classification=p.classification
            WHERE p.company_id=NEW.company_id AND p.id=NEW.policy_id AND p.version=NEW.policy_version AND r.retention_days IS NOT DISTINCT FROM NEW.retention_days) THEN
            RAISE EXCEPTION 'Archival must retain its current classification policy' USING ERRCODE='23514';END IF;
    ELSIF NEW.kind='RESTORE' THEN
        IF prior.archived_at IS NULL OR NEW.archived_at IS NOT NULL OR NEW.legal_hold<>prior.legal_hold THEN
            RAISE EXCEPTION 'Invalid restore transition' USING ERRCODE='23514';END IF;
    ELSE
        IF (NEW.archived_at,NEW.policy_id,NEW.policy_version,NEW.retention_days,NEW.eligible_at) IS DISTINCT FROM
            (prior.archived_at,prior.policy_id,prior.policy_version,prior.retention_days,prior.eligible_at) OR
            (NEW.kind='PLACE_HOLD' AND (coalesce(prior.legal_hold,false) OR NOT NEW.legal_hold)) OR
            (NEW.kind='RELEASE_HOLD' AND (NOT coalesce(prior.legal_hold,false) OR NEW.legal_hold)) THEN
            RAISE EXCEPTION 'Hold changes must preserve the archival snapshot' USING ERRCODE='23514';END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER retention_change_scope BEFORE INSERT ON document_retention_changes FOR EACH ROW EXECUTE FUNCTION validate_document_retention_change();
CREATE VIEW document_retention_policy_views WITH(security_invoker=true) AS
    SELECT p.company_id,p.id,p.classification,p.version AS current_version,r.version,r.retention_days,r.actor_id,r.recorded_at,r.reason
    FROM document_retention_policies p JOIN document_retention_policy_revisions r ON r.company_id=p.company_id AND r.policy_id=p.id;
CREATE VIEW document_retention_state_views WITH(security_invoker=true) AS
    SELECT c.*,s.version AS current_version FROM document_retention_states s JOIN document_retention_changes c ON c.company_id=s.company_id AND c.document_id=s.document_id;
CREATE FUNCTION block_archived_document_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF EXISTS(SELECT 1 FROM document_retention_state_views s WHERE s.company_id=NEW.company_id AND s.document_id=NEW.document_id AND s.version=s.current_version AND s.archived_at IS NOT NULL) THEN
        RAISE EXCEPTION 'Archived documents cannot accept new revisions' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER archived_document_revision BEFORE INSERT ON document_revisions FOR EACH ROW EXECUTE FUNCTION block_archived_document_revision();
CREATE FUNCTION block_archived_document_publication() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NEW.current_revision_id IS NOT NULL AND NEW.current_revision_id IS DISTINCT FROM OLD.current_revision_id AND EXISTS(
        SELECT 1 FROM document_retention_state_views s WHERE s.company_id=NEW.company_id AND s.document_id=NEW.id AND s.version=s.current_version AND s.archived_at IS NOT NULL) THEN
        RAISE EXCEPTION 'Archived documents cannot publish revisions' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER archived_document_publication BEFORE UPDATE ON documents FOR EACH ROW EXECUTE FUNCTION block_archived_document_publication();
