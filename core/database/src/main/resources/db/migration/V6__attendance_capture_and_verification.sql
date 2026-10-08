CREATE TABLE attendance_capture_windows (
    company_id uuid NOT NULL, id uuid NOT NULL, employment_id uuid NOT NULL,
    account_id uuid NOT NULL REFERENCES accounts(id), device_id uuid NOT NULL,
    issued_at timestamptz NOT NULL, expires_at timestamptz NOT NULL, consumed_by uuid,
    PRIMARY KEY(company_id,id), FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    CHECK(expires_at>issued_at)
);
CREATE INDEX attendance_window_rate ON attendance_capture_windows(company_id,employment_id,issued_at);
CREATE TABLE attendance_events (
    company_id uuid NOT NULL,id uuid NOT NULL,employment_id uuid NOT NULL,account_id uuid NOT NULL REFERENCES accounts(id),
    work_date date NOT NULL,kind varchar(16) NOT NULL CHECK(kind IN ('CLOCK_IN','CLOCK_OUT')),
    captured_at timestamptz NOT NULL,received_at timestamptz NOT NULL,device_id uuid NOT NULL,window_id uuid,
    offline boolean NOT NULL,latitude double precision,longitude double precision,accuracy_meters double precision,mocked boolean NOT NULL,
    schedule jsonb NOT NULL,initial_status varchar(16) NOT NULL CHECK(initial_status IN ('ACCEPTED','PENDING')),issues text[] NOT NULL,
    PRIMARY KEY(company_id,id),UNIQUE(company_id,window_id),
    CHECK (NOT offline OR initial_status='PENDING'),CHECK (initial_status='PENDING' OR window_id IS NOT NULL),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    FOREIGN KEY(company_id,window_id) REFERENCES attendance_capture_windows(company_id,id),
    CHECK ((latitude IS NULL AND longitude IS NULL AND accuracy_meters IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL AND accuracy_meters IS NOT NULL)),
    CHECK (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180 AND accuracy_meters BETWEEN 0 AND 100000)
);
CREATE INDEX attendance_employee_days ON attendance_events(company_id,employment_id,work_date,received_at,id);
CREATE INDEX attendance_pending ON attendance_events(company_id,work_date,id) WHERE initial_status='PENDING';
ALTER TABLE attendance_capture_windows ADD FOREIGN KEY(company_id,consumed_by) REFERENCES attendance_events(company_id,id);
CREATE TABLE attendance_reviews (
    company_id uuid NOT NULL,event_id uuid NOT NULL,actor_id uuid NOT NULL REFERENCES accounts(id),
    decision varchar(16) NOT NULL CHECK(decision IN ('ACCEPT','REJECT')),reviewed_at timestamptz NOT NULL,reason varchar(1000) NOT NULL,
    PRIMARY KEY(company_id,event_id),FOREIGN KEY(company_id,event_id) REFERENCES attendance_events(company_id,id)
);
CREATE FUNCTION protect_capture_window() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (to_jsonb(NEW)-'consumed_by') IS DISTINCT FROM (to_jsonb(OLD)-'consumed_by') OR OLD.consumed_by IS NOT NULL OR NEW.consumed_by IS NULL THEN
        RAISE EXCEPTION 'Capture windows can only be consumed once' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_window BEFORE UPDATE ON attendance_capture_windows FOR EACH ROW EXECUTE FUNCTION protect_capture_window();
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['attendance_capture_windows','attendance_events','attendance_reviews'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
    FOREACH t IN ARRAY ARRAY['attendance_events','attendance_reviews'] LOOP
        EXECUTE format('CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',t);
    END LOOP;
END $$;
