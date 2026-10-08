CREATE TABLE lifecycle_templates (
    company_id uuid NOT NULL REFERENCES companies(id),id uuid NOT NULL,
    code varchar(32) NOT NULL,name varchar(120) NOT NULL,kind varchar(16) NOT NULL CHECK(kind IN ('ONBOARDING','OFFBOARDING')),
    active boolean NOT NULL,version bigint NOT NULL CHECK(version>=0),tasks jsonb NOT NULL CHECK(jsonb_typeof(tasks)='array' AND jsonb_array_length(tasks) BETWEEN 1 AND 64 AND octet_length(tasks::text)<=32768),
    PRIMARY KEY(company_id,id),UNIQUE(company_id,code)
);
CREATE TABLE lifecycle_template_revisions (
    company_id uuid NOT NULL,id uuid NOT NULL,revision bigint NOT NULL,name varchar(120) NOT NULL,active boolean NOT NULL,tasks jsonb NOT NULL,
    actor_id uuid NOT NULL REFERENCES accounts(id),reason varchar(1000) NOT NULL,recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,id,revision),FOREIGN KEY(company_id,id) REFERENCES lifecycle_templates(company_id,id)
);
CREATE TABLE lifecycle_cases (
    company_id uuid NOT NULL,id uuid NOT NULL,employment_id uuid NOT NULL,kind varchar(16) NOT NULL CHECK(kind IN ('ONBOARDING','OFFBOARDING')),
    target_date date NOT NULL,template_id uuid NOT NULL,template_version bigint NOT NULL,template_name varchar(120) NOT NULL,
    status varchar(16) NOT NULL CHECK(status IN ('OPEN','COMPLETED','CANCELLED')),version bigint NOT NULL CHECK(version>=0),
    created_by uuid NOT NULL REFERENCES accounts(id),created_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,id),FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    FOREIGN KEY(company_id,template_id,template_version) REFERENCES lifecycle_template_revisions(company_id,id,revision)
);
CREATE UNIQUE INDEX lifecycle_open_case ON lifecycle_cases(company_id,employment_id,kind) WHERE status='OPEN';
CREATE TABLE lifecycle_tasks (
    company_id uuid NOT NULL,case_id uuid NOT NULL,key varchar(48) NOT NULL,title varchar(160) NOT NULL,required boolean NOT NULL,due_date date NOT NULL,
    assignee_id uuid REFERENCES accounts(id),status varchar(16) NOT NULL CHECK(status IN ('PENDING','DONE','WAIVED')),
    completed_by uuid REFERENCES accounts(id),completed_at timestamptz,
    PRIMARY KEY(company_id,case_id,key),FOREIGN KEY(company_id,case_id) REFERENCES lifecycle_cases(company_id,id),
    CHECK(NOT(required AND status='WAIVED')),
    CHECK((status='PENDING' AND completed_by IS NULL AND completed_at IS NULL) OR (status<>'PENDING' AND completed_by IS NOT NULL AND completed_at IS NOT NULL))
);
CREATE INDEX lifecycle_assigned_task ON lifecycle_tasks(company_id,assignee_id,case_id,key) WHERE status='PENDING';
CREATE INDEX lifecycle_employee_cases ON lifecycle_cases(company_id,employment_id,id);
CREATE TABLE lifecycle_events (
    company_id uuid NOT NULL,case_id uuid NOT NULL,version bigint NOT NULL CHECK(version>=0),task_key varchar(48),action varchar(32) NOT NULL CHECK(action IN ('CREATED','ASSIGNED','TASK_PENDING','TASK_DONE','TASK_WAIVED','CANCELLED','COMPLETED')),
    assignee_id uuid REFERENCES accounts(id),actor_id uuid NOT NULL REFERENCES accounts(id),reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),recorded_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,case_id,version),FOREIGN KEY(company_id,case_id) REFERENCES lifecycle_cases(company_id,id)
);
CREATE FUNCTION protect_lifecycle_template() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Lifecycle templates cannot be deleted' USING ERRCODE='23514'; END IF;
    IF (NEW.company_id,NEW.id,NEW.code,NEW.kind) IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.code,OLD.kind) OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Invalid lifecycle template revision' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lifecycle_template_guard BEFORE UPDATE OR DELETE ON lifecycle_templates FOR EACH ROW EXECUTE FUNCTION protect_lifecycle_template();
CREATE FUNCTION protect_lifecycle_case() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Lifecycle cases cannot be deleted' USING ERRCODE='23514'; END IF;
    IF (to_jsonb(NEW)-ARRAY['status','version']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','version']) OR OLD.status<>'OPEN' OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Invalid lifecycle case transition' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lifecycle_case_guard BEFORE UPDATE OR DELETE ON lifecycle_cases FOR EACH ROW EXECUTE FUNCTION protect_lifecycle_case();
CREATE FUNCTION protect_lifecycle_task() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE case_status text; BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Lifecycle tasks cannot be deleted' USING ERRCODE='23514'; END IF;
    SELECT status INTO case_status FROM lifecycle_cases WHERE company_id=NEW.company_id AND id=NEW.case_id FOR UPDATE;
    IF case_status IS DISTINCT FROM 'OPEN' THEN RAISE EXCEPTION 'Lifecycle case is closed' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' AND (to_jsonb(NEW)-ARRAY['assignee_id','status','completed_by','completed_at']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['assignee_id','status','completed_by','completed_at']) THEN
        RAISE EXCEPTION 'Lifecycle task definition is immutable' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lifecycle_task_guard BEFORE INSERT OR UPDATE OR DELETE ON lifecycle_tasks FOR EACH ROW EXECUTE FUNCTION protect_lifecycle_task();
CREATE TRIGGER immutable_lifecycle_template_revision BEFORE UPDATE OR DELETE ON lifecycle_template_revisions FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TRIGGER immutable_lifecycle_event BEFORE UPDATE OR DELETE ON lifecycle_events FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['lifecycle_templates','lifecycle_template_revisions','lifecycle_cases','lifecycle_tasks','lifecycle_events'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
END $$;

CREATE UNIQUE INDEX lifecycle_completed_offboarding ON lifecycle_cases(company_id,employment_id) WHERE kind='OFFBOARDING' AND status='COMPLETED';
CREATE POLICY lifecycle_template_actor ON lifecycle_template_revisions AS RESTRICTIVE FOR INSERT WITH CHECK(actor_id=current_actor_id());
CREATE POLICY lifecycle_event_actor ON lifecycle_events AS RESTRICTIVE FOR INSERT WITH CHECK(actor_id=current_actor_id());
CREATE POLICY lifecycle_creator ON lifecycle_cases AS RESTRICTIVE FOR INSERT WITH CHECK(created_by=current_actor_id());
