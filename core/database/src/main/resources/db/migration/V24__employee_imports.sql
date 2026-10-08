ALTER TABLE background_jobs ADD CONSTRAINT background_jobs_company_identity UNIQUE(company_id,id);
CREATE TABLE employee_imports (
    company_id uuid NOT NULL REFERENCES companies(id),id uuid NOT NULL,file_name varchar(120) NOT NULL,source_hash varchar(64) NOT NULL CHECK(source_hash~'^[0-9a-f]{64}$'),row_count integer NOT NULL CHECK(row_count BETWEEN 1 AND 5000),
    status varchar(16) NOT NULL CHECK(status IN ('PREVIEWING','REVIEW','IMPORTING','COMPLETED','STOPPED','CANCELLED')),job_id uuid NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK(version>=0),created_by uuid NOT NULL REFERENCES accounts(id),created_at timestamptz NOT NULL,reason varchar(1000) NOT NULL,
    PRIMARY KEY(company_id,id),UNIQUE(company_id,job_id),FOREIGN KEY(company_id,job_id) REFERENCES background_jobs(company_id,id)
);
CREATE TABLE employee_import_rows (
    company_id uuid NOT NULL,import_id uuid NOT NULL,row_number integer NOT NULL CHECK(row_number BETWEEN 1 AND 5000),
    employee_number varchar(1024) NOT NULL,legal_name varchar(1024) NOT NULL,
    proposed jsonb CHECK(proposed IS NULL OR octet_length(proposed::text)<=8192),parse_issues jsonb NOT NULL CHECK(octet_length(parse_issues::text)<=4096),
    status varchar(16) NOT NULL CHECK(status IN ('PENDING','READY','INVALID','APPLIED','REJECTED')),issues jsonb NOT NULL CHECK(octet_length(issues::text)<=4096),created_employment_id uuid,
    PRIMARY KEY(company_id,import_id,row_number),FOREIGN KEY(company_id,import_id) REFERENCES employee_imports(company_id,id),
    FOREIGN KEY(company_id,created_employment_id) REFERENCES employments(company_id,id),
    CHECK((status='APPLIED' AND created_employment_id IS NOT NULL AND created_employment_id=(proposed->>'id')::uuid) OR (status<>'APPLIED' AND created_employment_id IS NULL)),
    CHECK(status NOT IN ('READY','APPLIED') OR (proposed IS NOT NULL AND parse_issues='{}'::jsonb AND issues='{}'::jsonb))
);
CREATE INDEX employee_import_pending ON employee_import_rows(company_id,import_id,status,row_number);
CREATE TABLE employee_import_attempts (
    company_id uuid NOT NULL,import_id uuid NOT NULL,job_id uuid NOT NULL,phase varchar(16) NOT NULL CHECK(phase IN ('PREVIEW','APPLY')),actor_id uuid NOT NULL REFERENCES accounts(id),created_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,import_id,job_id),FOREIGN KEY(company_id,import_id) REFERENCES employee_imports(company_id,id),FOREIGN KEY(company_id,job_id) REFERENCES background_jobs(company_id,id)
);
CREATE FUNCTION protect_employee_import() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Employee imports cannot be deleted' USING ERRCODE='23514'; END IF;
    IF (to_jsonb(NEW)-ARRAY['status','job_id','version']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','job_id','version']) OR NEW.version<>OLD.version+1 OR OLD.status IN ('COMPLETED','CANCELLED') THEN
        RAISE EXCEPTION 'Invalid employee import transition' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER employee_import_guard BEFORE UPDATE OR DELETE ON employee_imports FOR EACH ROW EXECUTE FUNCTION protect_employee_import();
CREATE FUNCTION protect_employee_import_row() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Employee import rows cannot be deleted' USING ERRCODE='23514'; END IF;
    IF (to_jsonb(NEW)-ARRAY['status','issues','created_employment_id']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','issues','created_employment_id']) OR
        NOT((OLD.status='PENDING' AND NEW.status IN ('READY','INVALID')) OR (OLD.status='READY' AND NEW.status IN ('APPLIED','REJECTED'))) THEN
        RAISE EXCEPTION 'Invalid employee import row transition' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER employee_import_row_guard BEFORE UPDATE OR DELETE ON employee_import_rows FOR EACH ROW EXECUTE FUNCTION protect_employee_import_row();
CREATE TRIGGER immutable_employee_import_attempt BEFORE UPDATE OR DELETE ON employee_import_attempts FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['employee_imports','employee_import_rows','employee_import_attempts'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
END $$;
CREATE POLICY employee_import_actor ON employee_imports AS RESTRICTIVE FOR INSERT WITH CHECK(created_by=current_actor_id());
CREATE POLICY employee_import_attempt_actor ON employee_import_attempts AS RESTRICTIVE FOR INSERT WITH CHECK(actor_id=current_actor_id());
