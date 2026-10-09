-- Publication retains references to the approved calculation; it does not execute bank payments.
-- Results are read by run/ordinal. A second run-prefix index encouraged repeated scans of
-- all outcomes for point lookups; the primary key covers both indexed pages and individual rows.
DROP INDEX payroll_result_attempt;
ALTER TABLE payroll_runs DROP CONSTRAINT payroll_runs_status_check;
ALTER TABLE payroll_runs ADD CHECK(status IN('PROCESSING','STOPPED','CALCULATED','ABANDONED','FINALIZED'));
ALTER TABLE payroll_runs ADD COLUMN finalization_id uuid;
ALTER TABLE payroll_runs ADD CHECK((status='FINALIZED')=(finalization_id IS NOT NULL));
ALTER TABLE payroll_periods DROP CONSTRAINT payroll_periods_status_check;
ALTER TABLE payroll_periods ADD CHECK(status IN('DRAFT','PROCESSING','CALCULATED','CANCELLED','FINALIZED'));
ALTER TABLE payroll_period_changes DROP CONSTRAINT payroll_period_changes_status_check;
ALTER TABLE payroll_period_changes ADD CHECK(status IN('DRAFT','PROCESSING','CALCULATED','CANCELLED','FINALIZED'));
CREATE TABLE payroll_finalizations (
 company_id uuid NOT NULL,id uuid NOT NULL,run_id uuid NOT NULL,review_id uuid NOT NULL,
 run_version bigint NOT NULL CHECK(run_version BETWEEN 0 AND 23),review_version bigint NOT NULL CHECK(review_version BETWEEN 1 AND 8),
 approval_version bigint NOT NULL CHECK(approval_version>=1),attempt integer NOT NULL CHECK(attempt BETWEEN 1 AND 8),
 job_id uuid NOT NULL,actor_id uuid NOT NULL REFERENCES accounts(id),requested_at timestamptz NOT NULL,
 reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),company_code varchar(40) NOT NULL,company_name varchar(200) NOT NULL,published_at timestamptz,
 published boolean GENERATED ALWAYS AS (published_at IS NOT NULL) STORED,
 PRIMARY KEY(company_id,id),UNIQUE(company_id,id,run_id),UNIQUE(company_id,id,published),UNIQUE(company_id,job_id),UNIQUE(company_id,run_id,attempt),
 FOREIGN KEY(company_id,run_id) REFERENCES payroll_runs(company_id,id),
 FOREIGN KEY(company_id,review_id) REFERENCES payroll_reviews(company_id,id),
 FOREIGN KEY(company_id,job_id) REFERENCES background_jobs(company_id,id),CHECK(published_at IS NULL OR published_at>=requested_at)
);
CREATE UNIQUE INDEX payroll_run_publication ON payroll_finalizations(company_id,run_id) WHERE published_at IS NOT NULL;
ALTER TABLE payroll_runs ADD FOREIGN KEY(company_id,finalization_id,id) REFERENCES payroll_finalizations(company_id,id,run_id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE payroll_assessments (
 company_id uuid NOT NULL,id uuid NOT NULL,finalization_id uuid NOT NULL,run_id uuid NOT NULL,ordinal integer NOT NULL,
 employment_id uuid NOT NULL,person_id uuid NOT NULL,tax_month date NOT NULL,published_at timestamptz NOT NULL,
 holiday_kind varchar(32),holiday_year integer,
 published boolean NOT NULL DEFAULT true CHECK(published),
 PRIMARY KEY(company_id,id),UNIQUE(company_id,run_id,ordinal),UNIQUE(company_id,person_id,tax_month),
 FOREIGN KEY(company_id,finalization_id,run_id) REFERENCES payroll_finalizations(company_id,id,run_id),
 FOREIGN KEY(company_id,finalization_id,published) REFERENCES payroll_finalizations(company_id,id,published) DEFERRABLE INITIALLY DEFERRED,
 FOREIGN KEY(company_id,run_id,ordinal) REFERENCES payroll_run_results(company_id,run_id,ordinal),
 FOREIGN KEY(company_id,run_id,employment_id) REFERENCES payroll_run_targets(company_id,run_id,employment_id),
 CHECK((holiday_kind IS NULL)=(holiday_year IS NULL)),CHECK(holiday_year IS NULL OR holiday_year BETWEEN 2024 AND 2100)
);
CREATE UNIQUE INDEX payroll_assessed_holiday ON payroll_assessments(company_id,person_id,holiday_year,holiday_kind) WHERE holiday_kind IS NOT NULL;
CREATE INDEX payroll_employee_assessment ON payroll_assessments(company_id,employment_id,tax_month,id);
CREATE INDEX payroll_finalization_assessment ON payroll_assessments(company_id,finalization_id);

CREATE OR REPLACE FUNCTION protect_payroll_period() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='INSERT' THEN
  IF NEW.version<>0 OR NEW.status<>'DRAFT' OR NEW.current_run_id IS NOT NULL OR NEW.creation_transaction<>pg_current_xact_id()::text
   THEN RAISE EXCEPTION 'Payroll period must start as a draft' USING ERRCODE='23514'; END IF;
 ELSIF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payroll period history is retained' USING ERRCODE='23514';
 ELSE
  IF (to_jsonb(NEW)-ARRAY['version','status','current_run_id']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['version','status','current_run_id']) OR NEW.version<>OLD.version+1 OR NOT
   ((OLD.status='DRAFT' AND NEW.status IN('CANCELLED','PROCESSING')) OR (OLD.status='PROCESSING' AND NEW.status IN('CALCULATED','DRAFT')) OR (OLD.status='CALCULATED' AND NEW.status IN('DRAFT','FINALIZED')))
   THEN RAISE EXCEPTION 'Payroll period metadata and state transition are protected' USING ERRCODE='23514'; END IF;
 END IF;
 IF (NEW.status IN('DRAFT','CANCELLED') AND NEW.current_run_id IS NOT NULL) OR (NEW.status IN('PROCESSING','CALCULATED','FINALIZED') AND NEW.current_run_id IS NULL)
  OR (TG_OP='UPDATE' AND NEW.status IN('CALCULATED','FINALIZED') AND NEW.current_run_id IS DISTINCT FROM OLD.current_run_id)
  THEN RAISE EXCEPTION 'Payroll period requires its current run' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION protect_payroll_run() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p payroll_periods; result payroll_run_results; BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended('payroll:'||NEW.company_id::text,0));
 IF TG_OP='INSERT' THEN
  SELECT * INTO p FROM payroll_periods WHERE company_id=NEW.company_id AND id=NEW.period_id;
  IF p.id IS NULL OR p.status<>'DRAFT' OR p.current_run_id IS NOT NULL OR NEW.version<>0 OR NEW.status<>'PROCESSING' OR NEW.processed<>0 OR
     NEW.creation_transaction<>pg_current_xact_id()::text OR (NEW.total_employees,NEW.planned_payment_date,NEW.timezone) IS DISTINCT FROM (p.participant_count,p.planned_payment_date,p.timezone) OR
     NEW.run_number<>(SELECT count(*)+1 FROM payroll_runs WHERE company_id=NEW.company_id AND period_id=NEW.period_id) OR
     NOT EXISTS(SELECT 1 FROM work_periods WHERE company_id=NEW.company_id AND month=NEW.earnings_month AND job_id=NEW.work_job_id AND version=NEW.work_period_version AND status='CLOSED' AND timezone=NEW.timezone)
   THEN RAISE EXCEPTION 'Payroll run requires its draft and closed workforce evidence' USING ERRCODE='23514'; END IF;
 ELSIF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payroll run history is retained' USING ERRCODE='23514';
 ELSE
  IF (to_jsonb(NEW)-ARRAY['status','job_id','version','processed','succeeded','failed','last_result','finalization_id']) IS DISTINCT FROM
     (to_jsonb(OLD)-ARRAY['status','job_id','version','processed','succeeded','failed','last_result','finalization_id']) OR OLD.status IN('ABANDONED','FINALIZED')
   THEN RAISE EXCEPTION 'Payroll run inputs are immutable' USING ERRCODE='23514'; END IF;
  IF NEW.processed<>OLD.processed THEN
   SELECT * INTO result FROM payroll_run_results WHERE company_id=NEW.company_id AND run_id=NEW.id AND ordinal=NEW.processed;
   IF OLD.status<>'PROCESSING' OR NEW.status<>OLD.status OR NEW.job_id<>OLD.job_id OR NEW.version<>OLD.version OR NEW.processed<>OLD.processed+1 OR result.ordinal IS NULL OR
      NEW.succeeded<>OLD.succeeded+(CASE WHEN result.status='SUCCEEDED' THEN 1 ELSE 0 END) OR NEW.failed<>OLD.failed+(CASE WHEN result.status='FAILED' THEN 1 ELSE 0 END)
    THEN RAISE EXCEPTION 'Payroll progress requires exactly its next retained result' USING ERRCODE='23514'; END IF;
  ELSE
   IF NEW.succeeded<>OLD.succeeded OR NEW.failed<>OLD.failed OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'Payroll lifecycle must preserve progress' USING ERRCODE='23514'; END IF;
   IF NEW.job_id=OLD.job_id THEN
    IF NOT ((OLD.status='PROCESSING' AND NEW.status IN('STOPPED','CALCULATED','ABANDONED')) OR (OLD.status IN('STOPPED','CALCULATED') AND NEW.status='ABANDONED') OR (OLD.status='CALCULATED' AND NEW.status='FINALIZED'))
     THEN RAISE EXCEPTION 'Invalid payroll run transition' USING ERRCODE='23514'; END IF;
   ELSIF NEW.status<>'PROCESSING' OR OLD.status NOT IN('PROCESSING','STOPPED') OR NOT EXISTS(SELECT 1 FROM background_jobs WHERE company_id=OLD.company_id AND id=OLD.job_id AND status IN('FAILED','CANCELLED'))
    THEN RAISE EXCEPTION 'Only terminal payroll jobs can resume' USING ERRCODE='23514'; END IF;
  END IF;
 END IF; RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION check_payroll_run_checkpoint() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r payroll_runs; j background_jobs; a payroll_run_attempts; p payroll_periods; run_key uuid; BEGIN
 IF TG_TABLE_NAME='payroll_runs' THEN run_key:=NEW.id;
 ELSIF TG_TABLE_NAME='background_jobs' THEN run_key:=(NEW.request->>'runId')::uuid;
 ELSIF TG_TABLE_NAME='payroll_periods' THEN
  SELECT * INTO p FROM payroll_periods WHERE company_id=NEW.company_id AND id=NEW.id;
  run_key:=p.current_run_id;
  IF run_key IS NULL THEN
   IF EXISTS(SELECT 1 FROM payroll_runs WHERE company_id=NEW.company_id AND period_id=NEW.id AND status<>'ABANDONED')
    THEN RAISE EXCEPTION 'Active payroll run cannot be detached' USING ERRCODE='23514'; END IF;
   RETURN NULL;
  END IF;
 ELSE run_key:=NEW.run_id; END IF;
 SELECT * INTO r FROM payroll_runs WHERE company_id=NEW.company_id AND id=run_key;
 IF r.id IS NULL THEN RAISE EXCEPTION 'Payroll checkpoint requires its run' USING ERRCODE='23514'; END IF;
 SELECT * INTO p FROM payroll_periods WHERE company_id=r.company_id AND id=r.period_id;
 SELECT * INTO j FROM background_jobs WHERE company_id=r.company_id AND id=r.job_id;
 SELECT * INTO a FROM payroll_run_attempts WHERE company_id=r.company_id AND run_id=r.id AND job_id=r.job_id;
 IF a.job_id IS NULL OR r.processed<>a.base_completed+j.completed_items-(CASE WHEN j.status='SUCCEEDED' THEN 1 ELSE 0 END) OR
  (r.status IN('CALCULATED','FINALIZED') AND (r.processed<>r.total_employees OR j.status<>'SUCCEEDED' OR p.status<>r.status OR p.current_run_id IS DISTINCT FROM r.id)) OR
  (r.status IN('PROCESSING','STOPPED') AND (p.status<>'PROCESSING' OR p.current_run_id IS DISTINCT FROM r.id)) OR
  (r.status='STOPPED' AND j.status NOT IN('FAILED','CANCELLED')) OR
  (r.status='ABANDONED' AND (p.current_run_id=r.id OR j.status NOT IN('FAILED','CANCELLED','SUCCEEDED'))) OR
  (j.status='SUCCEEDED' AND r.status NOT IN('CALCULATED','ABANDONED','FINALIZED'))
  THEN RAISE EXCEPTION 'Payroll checkpoint, period and retained outcomes must agree' USING ERRCODE='23514'; END IF;
 IF TG_TABLE_NAME='payroll_run_attempts' THEN
  IF NEW.job_id<>r.job_id THEN RAISE EXCEPTION 'Payroll attempt must bind its current run' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='payroll_run_results' THEN
  IF NEW.ordinal>r.processed THEN RAISE EXCEPTION 'Payroll result must advance progress' USING ERRCODE='23514'; END IF;
 END IF;
 IF TG_TABLE_NAME='payroll_runs' AND TG_OP='INSERT' THEN
  IF r.total_employees<>(SELECT count(*) FROM payroll_run_targets WHERE company_id=r.company_id AND run_id=r.id)
   THEN RAISE EXCEPTION 'Payroll target snapshot must be complete' USING ERRCODE='23514'; END IF;
  IF EXISTS(SELECT 1 FROM leave_requests l JOIN payroll_run_targets t ON t.company_id=l.company_id AND t.employment_id=l.employment_id
    WHERE t.company_id=r.company_id AND t.run_id=r.id AND l.status IN('PENDING','CANCELLATION_PENDING') AND
      EXISTS(SELECT 1 FROM jsonb_array_elements(l.days) d WHERE date_trunc('month',(d->>'workDate')::date)::date=r.earnings_month))
   THEN RAISE EXCEPTION 'Pending leave must be resolved before payroll cutoff' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE OR REPLACE FUNCTION assert_payroll_review(p_company uuid,p_review uuid) RETURNS void LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE v payroll_reviews; r payroll_runs; a approval_requests; BEGIN
 SELECT * INTO v FROM payroll_reviews WHERE company_id=p_company AND id=p_review;
 IF v.id IS NULL THEN RAISE EXCEPTION 'Payroll approval requires its review snapshot' USING ERRCODE='23514'; END IF;
 SELECT * INTO r FROM payroll_runs WHERE company_id=p_company AND id=v.run_id;
 SELECT * INTO a FROM approval_requests WHERE company_id=p_company AND id=v.approval_id;
 IF a.id IS NULL OR a.resource_id<>v.id OR a.kind<>'PAYROLL' OR
   (v.status='PENDING' AND (r.status<>'CALCULATED' OR r.version<>v.run_version)) OR
   (v.status='APPROVED' AND NOT ((r.status='CALCULATED' AND r.version=v.run_version) OR
    (r.status='FINALIZED' AND r.version=v.run_version+1 AND EXISTS(SELECT 1 FROM payroll_finalizations f WHERE f.company_id=p_company AND f.id=r.finalization_id AND f.review_id=v.id AND f.published_at IS NOT NULL)))) OR
   (v.status='PENDING' AND a.status NOT IN('PENDING','BLOCKED')) OR (v.status='APPROVED' AND a.status<>'APPROVED') OR
   (v.status='REJECTED' AND a.status<>'REJECTED') OR (v.status='WITHDRAWN' AND a.status<>'CANCELLED') OR
   NOT EXISTS(SELECT 1 FROM payroll_review_changes WHERE company_id=p_company AND review_id=v.id AND revision=v.version AND status=v.status)
  THEN RAISE EXCEPTION 'Payroll review, approval and retained evidence must agree' USING ERRCODE='23514'; END IF;
END $$;

CREATE FUNCTION protect_payroll_finalization() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r payroll_runs; v payroll_reviews; a approval_requests; j background_jobs; previous payroll_finalizations; BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payroll finalization history is retained' USING ERRCODE='23514'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('payroll:'||NEW.company_id::text,0));
 SELECT * INTO r FROM payroll_runs WHERE company_id=NEW.company_id AND id=NEW.run_id;
 SELECT * INTO v FROM payroll_reviews WHERE company_id=NEW.company_id AND id=NEW.review_id;
 SELECT * INTO a FROM approval_requests WHERE company_id=NEW.company_id AND id=v.approval_id;
 SELECT * INTO j FROM background_jobs WHERE company_id=NEW.company_id AND id=NEW.job_id;
 IF r.id IS NULL OR v.id IS NULL OR a.id IS NULL OR j.id IS NULL OR r.status<>'CALCULATED' OR r.version<>NEW.run_version OR
    r.succeeded<>r.total_employees OR r.failed<>0 OR v.run_id<>r.id OR v.run_version<>r.version OR
    v.version<>NEW.review_version OR v.status<>'APPROVED' OR a.version<>NEW.approval_version OR a.status<>'APPROVED' OR
    j.kind<>'PAYROLL_FINALIZE' OR j.actor_id<>NEW.actor_id OR j.request->>'finalizationId' IS DISTINCT FROM NEW.id::text OR
    j.request->>'runId' IS DISTINCT FROM r.id::text OR j.total_items<>1 OR j.progress_mode<>'FIXED_TOTAL' OR NEW.actor_id IS DISTINCT FROM current_actor_id()
  THEN RAISE EXCEPTION 'Finalization requires exact approved payroll and its owned job' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' THEN
  SELECT * INTO previous FROM payroll_finalizations WHERE company_id=NEW.company_id AND run_id=NEW.run_id ORDER BY attempt DESC LIMIT 1;
  IF NOT EXISTS(SELECT 1 FROM companies WHERE id=NEW.company_id AND code=NEW.company_code AND name=NEW.company_name) OR NEW.published_at IS NOT NULL OR NEW.attempt<>coalesce(previous.attempt,0)+1 OR j.status<>'QUEUED' OR j.completed_items<>0 OR NEW.requested_at<>j.created_at OR
     (previous.id IS NOT NULL AND EXISTS(SELECT 1 FROM background_jobs WHERE company_id=NEW.company_id AND id=previous.job_id AND status NOT IN('FAILED','CANCELLED')))
   THEN RAISE EXCEPTION 'Only terminal unpublished finalizations can be retried' USING ERRCODE='23514'; END IF;
 ELSE
  IF (to_jsonb(NEW)-ARRAY['published_at','published']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['published_at','published']) OR OLD.published_at IS NOT NULL OR NEW.published_at IS NULL OR
     j.status<>'RUNNING' OR j.cancellation_requested OR j.lease_until<=clock_timestamp()
   THEN RAISE EXCEPTION 'Publication requires its active lease and is immutable' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_finalization_protected BEFORE INSERT OR UPDATE OR DELETE ON payroll_finalizations FOR EACH ROW EXECUTE FUNCTION protect_payroll_finalization();
-- Validate the statement as a set. No per-employee scan of every published row.
CREATE FUNCTION validate_payroll_assessments() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE company_key uuid; finalization_key uuid; f payroll_finalizations; r payroll_runs; j background_jobs; BEGIN
 IF NOT EXISTS(SELECT 1 FROM new_assessments) THEN RETURN NULL; END IF;
 IF (SELECT count(*)>5000 OR count(DISTINCT(company_id,finalization_id))<>1 FROM new_assessments)
  THEN RAISE EXCEPTION 'Assessment publication is one bounded finalization' USING ERRCODE='23514'; END IF;
 SELECT company_id,finalization_id INTO company_key,finalization_key FROM new_assessments LIMIT 1;
 SELECT * INTO f FROM payroll_finalizations WHERE company_id=company_key AND id=finalization_key;
 SELECT * INTO r FROM payroll_runs WHERE company_id=company_key AND id=f.run_id;
 SELECT * INTO j FROM background_jobs WHERE company_id=company_key AND id=f.job_id;
 IF f.id IS NULL OR f.published_at IS NOT NULL OR f.actor_id IS DISTINCT FROM current_actor_id() OR
    j.status IS DISTINCT FROM 'RUNNING' OR j.cancellation_requested OR j.lease_until<=clock_timestamp() OR
    r.status IS DISTINCT FROM 'CALCULATED' OR r.version<>f.run_version
  THEN RAISE EXCEPTION 'Assessments require their unchanged run and leased publication' USING ERRCODE='23514'; END IF;
 -- Correlated primary-key lookups prevent a transition-table join from comparing every pair
 -- of employees under RLS, including immediately after a large result import/restore.
 IF EXISTS(SELECT 1 FROM new_assessments n
   LEFT JOIN LATERAL(SELECT t.employment_id,t.employment_version FROM payroll_run_targets t
      WHERE t.company_id=n.company_id AND t.run_id=n.run_id AND t.ordinal=n.ordinal LIMIT 1) t ON true
   LEFT JOIN LATERAL(SELECT e.person_id,e.version FROM employments e
      WHERE e.company_id=n.company_id AND e.id=t.employment_id LIMIT 1) e ON true
   LEFT JOIN LATERAL(SELECT o.status,o.facts,o.calculation FROM payroll_run_results o
      WHERE o.company_id=n.company_id AND o.run_id=n.run_id AND o.ordinal=n.ordinal LIMIT 1) o ON true
  WHERE n.run_id IS DISTINCT FROM f.run_id OR o.status IS DISTINCT FROM 'SUCCEEDED' OR
   n.employment_id IS DISTINCT FROM t.employment_id OR n.person_id IS DISTINCT FROM e.person_id OR e.version<>t.employment_version OR
   n.tax_month IS DISTINCT FROM r.earnings_month OR n.published_at<f.requested_at OR
   n.holiday_kind IS DISTINCT FROM CASE WHEN (o.calculation->'holiday'->>'amount')::numeric>0 THEN o.facts->'input'->'holidayAllowance'->>'kind' END OR
   n.holiday_year IS DISTINCT FROM CASE WHEN (o.calculation->'holiday'->>'amount')::numeric>0 THEN extract(year FROM (o.calculation->'holiday'->>'holidayDate')::date)::integer END)
  THEN RAISE EXCEPTION 'Assessment must reference its exact retained result and current employee' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE TRIGGER payroll_assessments_valid AFTER INSERT ON payroll_assessments REFERENCING NEW TABLE AS new_assessments FOR EACH STATEMENT EXECUTE FUNCTION validate_payroll_assessments();
CREATE TRIGGER payroll_assessments_immutable BEFORE UPDATE OR DELETE ON payroll_assessments FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE FUNCTION assert_payroll_finalization(p_company uuid,p_id uuid) RETURNS void LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE f payroll_finalizations; r payroll_runs; v payroll_reviews; a approval_requests; j background_jobs; BEGIN
 SELECT * INTO f FROM payroll_finalizations WHERE company_id=p_company AND id=p_id;
 IF f.id IS NULL THEN RAISE EXCEPTION 'Payroll publication requires its finalization' USING ERRCODE='23514'; END IF;
 SELECT * INTO j FROM background_jobs WHERE company_id=p_company AND id=f.job_id;
 SELECT * INTO r FROM payroll_runs WHERE company_id=p_company AND id=f.run_id;
 IF f.published_at IS NULL THEN
  IF j.status='SUCCEEDED' OR j.completed_items<>0 OR r.finalization_id=f.id OR EXISTS(SELECT 1 FROM payroll_assessments WHERE company_id=p_company AND finalization_id=f.id)
   THEN RAISE EXCEPTION 'Unpublished finalization cannot expose assessments' USING ERRCODE='23514'; END IF;
 ELSE
  SELECT * INTO v FROM payroll_reviews WHERE company_id=p_company AND id=f.review_id;
  SELECT * INTO a FROM approval_requests WHERE company_id=p_company AND id=v.approval_id;
  IF r.status<>'FINALIZED' OR r.finalization_id IS DISTINCT FROM f.id OR r.version<>f.run_version+1 OR r.failed<>0 OR r.succeeded<>r.total_employees OR
     v.status<>'APPROVED' OR v.version<>f.review_version OR a.status<>'APPROVED' OR a.version<>f.approval_version OR
     j.status<>'SUCCEEDED' OR j.completed_items<>1 OR j.cancellation_requested OR
     r.total_employees<>(SELECT count(*) FROM payroll_assessments WHERE company_id=p_company AND finalization_id=f.id) OR
     EXISTS(SELECT 1 FROM payroll_assessments WHERE company_id=p_company AND finalization_id=f.id AND published_at<>f.published_at)
   THEN RAISE EXCEPTION 'Payroll publication, approved results and job must commit together' USING ERRCODE='23514'; END IF;
  PERFORM assert_payroll_review(p_company,v.id);
 END IF;
END $$;
CREATE FUNCTION check_payroll_finalization() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_TABLE_NAME='payroll_finalizations' THEN PERFORM assert_payroll_finalization(NEW.company_id,NEW.id);
 ELSIF TG_TABLE_NAME='payroll_runs' THEN
  IF NEW.finalization_id IS NOT NULL THEN PERFORM assert_payroll_finalization(NEW.company_id,NEW.finalization_id); END IF;
 ELSE PERFORM assert_payroll_finalization(NEW.company_id,(NEW.request->>'finalizationId')::uuid); END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payroll_publication_complete AFTER INSERT OR UPDATE ON payroll_finalizations DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_finalization();
CREATE CONSTRAINT TRIGGER payroll_final_run_complete AFTER UPDATE ON payroll_runs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_finalization();
CREATE CONSTRAINT TRIGGER payroll_final_job_created AFTER INSERT ON background_jobs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
 WHEN(NEW.kind='PAYROLL_FINALIZE') EXECUTE FUNCTION check_payroll_finalization();
CREATE CONSTRAINT TRIGGER payroll_final_job_complete AFTER UPDATE ON background_jobs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
 WHEN(NEW.kind='PAYROLL_FINALIZE' AND (NEW.completed_items IS DISTINCT FROM OLD.completed_items OR NEW.status='SUCCEEDED')) EXECUTE FUNCTION check_payroll_finalization();
CREATE FUNCTION guard_finalizing_payroll_review() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.status='WITHDRAWN' AND EXISTS(SELECT 1 FROM payroll_finalizations f JOIN background_jobs j ON j.company_id=f.company_id AND j.id=f.job_id
    WHERE f.company_id=NEW.company_id AND f.review_id=NEW.id AND (f.published_at IS NOT NULL OR j.status IN('QUEUED','RUNNING')))
  THEN RAISE EXCEPTION 'Active finalization must stop before withdrawing payroll review' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_review_finalization_guard BEFORE UPDATE ON payroll_reviews FOR EACH ROW EXECUTE FUNCTION guard_finalizing_payroll_review();
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['payroll_finalizations','payroll_assessments'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
 END LOOP;
END $$;
