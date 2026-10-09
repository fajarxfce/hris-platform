ALTER TABLE announcement_revisions DROP CONSTRAINT announcement_revisions_status_check;
ALTER TABLE announcement_revisions ADD CONSTRAINT announcement_status CHECK(status IN ('DRAFT','QUEUED','PUBLISHED','ARCHIVED'));
ALTER TABLE announcement_revisions
    ADD COLUMN publication_job_id uuid REFERENCES background_jobs(id),
    ADD COLUMN scheduled_for timestamptz CHECK(scheduled_for IS NULL OR isfinite(scheduled_for)),
    ADD COLUMN published_at timestamptz CHECK(published_at IS NULL OR isfinite(published_at)),
    ADD COLUMN recipient_count integer NOT NULL DEFAULT 0 CHECK(recipient_count BETWEEN 0 AND 5000),
    ADD COLUMN publication_attempts integer NOT NULL DEFAULT 0 CHECK(publication_attempts BETWEEN 0 AND 8);
ALTER TABLE announcement_revisions ADD CONSTRAINT announcement_publication_state CHECK(
    (status='DRAFT' AND publication_job_id IS NULL AND scheduled_for IS NULL AND published_at IS NULL AND recipient_count=0) OR
    (status='QUEUED' AND publication_job_id IS NOT NULL AND published_at IS NULL AND recipient_count=0 AND publication_attempts>0) OR
    (status='PUBLISHED' AND publication_job_id IS NOT NULL AND published_at IS NOT NULL AND recipient_count>0 AND publication_attempts>0) OR
    (status='ARCHIVED' AND ((published_at IS NULL AND recipient_count=0) OR
        (published_at IS NOT NULL AND publication_job_id IS NOT NULL AND recipient_count>0 AND publication_attempts>0)))
);
ALTER TABLE announcement_revisions ADD CONSTRAINT announcement_publication_time CHECK(
    scheduled_for IS NULL OR published_at IS NULL OR published_at>=scheduled_for
);

CREATE INDEX announcement_job_reference ON announcement_revisions(company_id,publication_job_id,version) WHERE publication_job_id IS NOT NULL;

CREATE TABLE announcement_publications (
    company_id uuid NOT NULL, id uuid NOT NULL REFERENCES background_jobs(id),
    announcement_id uuid NOT NULL, content_version bigint NOT NULL,
    published_at timestamptz NOT NULL CHECK(isfinite(published_at)), actor_id uuid NOT NULL REFERENCES accounts(id),
    recipients jsonb NOT NULL CHECK(jsonb_typeof(recipients)='object' AND octet_length(recipients::text)<=1048576),
    recipient_count integer NOT NULL CHECK(recipient_count BETWEEN 1 AND 5000),
    audience_versions jsonb NOT NULL CHECK(jsonb_typeof(audience_versions)='object' AND octet_length(audience_versions::text)<=4096),
    PRIMARY KEY(company_id,id), UNIQUE(company_id,announcement_id),
    FOREIGN KEY(company_id,announcement_id,content_version) REFERENCES announcement_revisions(company_id,id,version)
);

CREATE TABLE inbox_items (
    company_id uuid NOT NULL, id uuid NOT NULL DEFAULT gen_random_uuid(),
    publication_id uuid NOT NULL, announcement_id uuid NOT NULL,
    owner_account_id uuid NOT NULL REFERENCES accounts(id), employment_id uuid NOT NULL,
    acknowledgement_required boolean NOT NULL, delivered_at timestamptz NOT NULL CHECK(isfinite(delivered_at)),
    read_at timestamptz CHECK(read_at IS NULL OR (isfinite(read_at) AND read_at>=delivered_at)),
    acknowledged_at timestamptz CHECK(acknowledged_at IS NULL OR (isfinite(acknowledged_at) AND acknowledged_at>=read_at)),
    withdrawn boolean NOT NULL DEFAULT false, version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
    PRIMARY KEY(company_id,id), UNIQUE(company_id,publication_id,owner_account_id),
    FOREIGN KEY(company_id,publication_id) REFERENCES announcement_publications(company_id,id),
    FOREIGN KEY(company_id,announcement_id) REFERENCES announcement_heads(company_id,id),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    CHECK(acknowledged_at IS NULL OR (acknowledgement_required AND read_at IS NOT NULL))
);
CREATE INDEX inbox_owner_page ON inbox_items(company_id,owner_account_id,id) WHERE NOT withdrawn;
CREATE INDEX inbox_announcement ON inbox_items(company_id,announcement_id,id);

DO $$ DECLARE name text; BEGIN
    FOREACH name IN ARRAY ARRAY['announcement_publications','inbox_items'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',name);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',name);
    END LOOP;
END $$;
CREATE POLICY publication_author ON announcement_publications AS RESTRICTIVE FOR INSERT WITH CHECK(actor_id=current_actor_id());
CREATE TRIGGER immutable_publication BEFORE UPDATE OR DELETE ON announcement_publications FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TRIGGER retain_inbox BEFORE DELETE ON inbox_items FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

CREATE FUNCTION validate_announcement_publication() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE job background_jobs%ROWTYPE; content announcement_revisions%ROWTYPE;
BEGIN
    SELECT * INTO job FROM background_jobs WHERE id=NEW.id AND company_id=NEW.company_id;
    SELECT * INTO content FROM announcement_revisions WHERE company_id=NEW.company_id AND id=NEW.announcement_id AND version=NEW.content_version;
    IF job.id IS NULL OR job.kind<>'ANNOUNCEMENT_PUBLISH' OR job.status<>'RUNNING' OR job.cancellation_requested OR
        job.lease_until<=clock_timestamp() OR job.actor_id<>NEW.actor_id OR content.id IS NULL OR content.status<>'QUEUED' OR
        content.publication_job_id IS DISTINCT FROM job.id OR (content.scheduled_for IS NOT NULL AND NEW.published_at<content.scheduled_for) OR
        NOT EXISTS(SELECT 1 FROM announcement_heads WHERE company_id=NEW.company_id AND id=NEW.announcement_id AND version=NEW.content_version) OR
        NEW.recipient_count<>(SELECT count(*) FROM jsonb_each_text(NEW.recipients)) OR
        (SELECT count(*) FROM jsonb_each_text(NEW.audience_versions))>32 THEN
        RAISE EXCEPTION 'Publication requires a current fenced request and bounded audience' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM jsonb_each_text(NEW.recipients) target
        WHERE NOT EXISTS(SELECT 1 FROM employments e JOIN persons p ON p.id=e.person_id
                WHERE e.company_id=NEW.company_id AND e.id=target.value::uuid AND p.account_id=target.key::uuid)
            OR NOT EXISTS(SELECT 1 FROM company_memberships m
                WHERE m.company_id=NEW.company_id AND m.account_id=target.key::uuid)) THEN
        RAISE EXCEPTION 'Publication recipient references must belong to this company' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER publication_references BEFORE INSERT ON announcement_publications FOR EACH ROW EXECUTE FUNCTION validate_announcement_publication();

-- Validate a complete insert set against one materialized recipient map. Do not
-- detoast a 5,000-recipient document separately for every inbox row.
CREATE FUNCTION validate_inbox_insertions() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (SELECT count(*) FROM inbox_insertions)>5000 OR
        (SELECT count(*) FROM (SELECT DISTINCT company_id,publication_id FROM inbox_insertions) selected)>1 THEN
        RAISE EXCEPTION 'Inbox insertion must be one bounded publication' USING ERRCODE='23514';
    END IF;
    IF EXISTS(
        WITH expected AS MATERIALIZED (
            SELECT p.company_id,p.id,p.announcement_id,p.published_at,r.acknowledgement_required,
                target.key::uuid AS account_id,target.value::uuid AS employment_id
            FROM announcement_publications p JOIN (SELECT DISTINCT company_id,publication_id FROM inbox_insertions) selected
                ON selected.company_id=p.company_id AND selected.publication_id=p.id
            JOIN announcement_revisions r ON r.company_id=p.company_id AND r.id=p.announcement_id AND r.version=p.content_version
            CROSS JOIN LATERAL jsonb_each_text(p.recipients) target
        )
        SELECT 1 FROM inbox_insertions i LEFT JOIN expected e
            ON e.company_id=i.company_id AND e.id=i.publication_id AND e.account_id=i.owner_account_id AND e.employment_id=i.employment_id
        WHERE e.id IS NULL OR i.announcement_id<>e.announcement_id OR i.delivered_at<>e.published_at OR
            i.acknowledgement_required<>e.acknowledgement_required OR i.version<>0 OR i.read_at IS NOT NULL OR i.acknowledged_at IS NOT NULL OR i.withdrawn
    ) THEN RAISE EXCEPTION 'Inbox identity requires its immutable publication snapshot' USING ERRCODE='23514'; END IF;
    RETURN NULL;
END $$;
CREATE TRIGGER inbox_insertions AFTER INSERT ON inbox_items REFERENCING NEW TABLE AS inbox_insertions
    FOR EACH STATEMENT EXECUTE FUNCTION validate_inbox_insertions();

CREATE FUNCTION protect_inbox_item() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (OLD.company_id,OLD.id,OLD.publication_id,OLD.announcement_id,OLD.owner_account_id,OLD.employment_id,OLD.acknowledgement_required,OLD.delivered_at)
        IS DISTINCT FROM (NEW.company_id,NEW.id,NEW.publication_id,NEW.announcement_id,NEW.owner_account_id,NEW.employment_id,NEW.acknowledgement_required,NEW.delivered_at) OR
        OLD.withdrawn OR NEW.version<>OLD.version+1 OR
        (OLD.read_at IS NOT NULL AND NEW.read_at IS DISTINCT FROM OLD.read_at) OR
        (OLD.acknowledged_at IS NOT NULL AND NEW.acknowledged_at IS DISTINCT FROM OLD.acknowledged_at) THEN
        RAISE EXCEPTION 'Inbox identity and acknowledged state are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inbox_state BEFORE UPDATE ON inbox_items FOR EACH ROW EXECUTE FUNCTION protect_inbox_item();
CREATE FUNCTION validate_inbox_withdrawals() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (SELECT count(*) FROM inbox_updates)>5000 THEN
        RAISE EXCEPTION 'Inbox updates must be bounded' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM (SELECT DISTINCT company_id,announcement_id FROM inbox_updates WHERE withdrawn) changed
        LEFT JOIN announcement_heads h ON h.company_id=changed.company_id AND h.id=changed.announcement_id
        LEFT JOIN announcement_revisions r ON r.company_id=h.company_id AND r.id=h.id AND r.version=h.version
        WHERE r.status IS DISTINCT FROM 'ARCHIVED') THEN
        RAISE EXCEPTION 'Inbox withdrawal requires an archived announcement' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE TRIGGER inbox_withdrawals AFTER UPDATE ON inbox_items REFERENCING NEW TABLE AS inbox_updates
    FOR EACH STATEMENT EXECUTE FUNCTION validate_inbox_withdrawals();

CREATE OR REPLACE FUNCTION validate_announcement_revision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE previous announcement_revisions%ROWTYPE; job background_jobs%ROWTYPE; publication announcement_publications%ROWTYPE;
BEGIN
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
    IF NEW.version=0 THEN
        IF NEW.status<>'DRAFT' OR NEW.publication_attempts<>0 THEN RAISE EXCEPTION 'Announcements start as drafts' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO previous FROM announcement_revisions WHERE company_id=NEW.company_id AND id=NEW.id AND version=NEW.version-1;
    IF previous.id IS NULL OR previous.status='ARCHIVED' OR
        ((previous.title,previous.body,previous.audience_kind,previous.target_ids,previous.acknowledgement_required)
            IS DISTINCT FROM (NEW.title,NEW.body,NEW.audience_kind,NEW.target_ids,NEW.acknowledgement_required)
            AND NOT (previous.status='DRAFT' AND NEW.status='DRAFT')) THEN
        RAISE EXCEPTION 'Queued and published content is immutable' USING ERRCODE='23514';
    END IF;
    IF NEW.status='QUEUED' THEN
        SELECT * INTO job FROM background_jobs WHERE id=NEW.publication_job_id AND company_id=NEW.company_id;
        IF previous.status<>'DRAFT' OR NEW.publication_attempts<>previous.publication_attempts+1 OR job.id IS NULL OR
            job.kind<>'ANNOUNCEMENT_PUBLISH' OR job.status<>'QUEUED' OR job.actor_id<>NEW.actor_id OR
            job.scheduled_for IS DISTINCT FROM NEW.scheduled_for OR
            (job.request->>'announcementId') IS DISTINCT FROM NEW.id::text OR
            (job.request->>'contentVersion') IS DISTINCT FROM NEW.version::text THEN
            RAISE EXCEPTION 'Queued publication requires a matching job request' USING ERRCODE='23514';
        END IF;
    ELSE
        IF NEW.publication_attempts<>previous.publication_attempts THEN RAISE EXCEPTION 'Publication attempt count is retained' USING ERRCODE='23514'; END IF;
        IF NEW.status='PUBLISHED' THEN
            SELECT * INTO publication FROM announcement_publications WHERE company_id=NEW.company_id AND id=previous.publication_job_id;
            IF previous.status<>'QUEUED' OR publication.id IS NULL OR publication.announcement_id<>NEW.id OR
                publication.content_version<>previous.version OR publication.published_at IS DISTINCT FROM NEW.published_at OR
                publication.recipient_count<>NEW.recipient_count OR publication.actor_id<>NEW.actor_id THEN
                RAISE EXCEPTION 'Published revision requires complete publication evidence' USING ERRCODE='23514';
            END IF;
        ELSIF NEW.status='DRAFT' THEN
            IF previous.status='QUEUED' THEN
                SELECT * INTO job FROM background_jobs WHERE id=previous.publication_job_id;
                IF job.id IS NULL OR job.status NOT IN ('FAILED','CANCELLED') THEN
                    RAISE EXCEPTION 'Returning to draft requires a stopped publication' USING ERRCODE='23514';
                END IF;
            ELSIF previous.status<>'DRAFT' THEN RAISE EXCEPTION 'Published content cannot return to draft' USING ERRCODE='23514'; END IF;
        ELSIF NEW.status='ARCHIVED' THEN
            IF previous.status='QUEUED' THEN
                SELECT * INTO job FROM background_jobs WHERE id=previous.publication_job_id;
                IF job.id IS NULL OR job.status NOT IN ('FAILED','CANCELLED') THEN
                    RAISE EXCEPTION 'Archival requires a stopped publication' USING ERRCODE='23514';
                END IF;
            END IF;
            IF (previous.publication_job_id,previous.scheduled_for,previous.published_at,previous.recipient_count)
                IS DISTINCT FROM (NEW.publication_job_id,NEW.scheduled_for,NEW.published_at,NEW.recipient_count) THEN
                RAISE EXCEPTION 'Archived publication metadata is retained' USING ERRCODE='23514';
            END IF;
        END IF;
        IF NEW.status='PUBLISHED' AND (previous.publication_job_id,previous.scheduled_for)
            IS DISTINCT FROM (NEW.publication_job_id,NEW.scheduled_for) THEN
            RAISE EXCEPTION 'Publication identity is retained' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION require_complete_announcement_publication() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (SELECT count(*) FROM inbox_items WHERE company_id=NEW.company_id AND publication_id=NEW.id)<>NEW.recipient_count OR
        NOT EXISTS(SELECT 1 FROM background_jobs WHERE id=NEW.id AND company_id=NEW.company_id AND status='SUCCEEDED' AND completed_items=1) OR
        NOT EXISTS(SELECT 1 FROM announcement_revisions WHERE company_id=NEW.company_id AND id=NEW.announcement_id AND version=NEW.content_version+1
            AND status='PUBLISHED' AND publication_job_id=NEW.id AND published_at=NEW.published_at AND recipient_count=NEW.recipient_count) THEN
        RAISE EXCEPTION 'Publication, inbox and fenced completion must commit together' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER complete_announcement_publication AFTER INSERT ON announcement_publications
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_complete_announcement_publication();
