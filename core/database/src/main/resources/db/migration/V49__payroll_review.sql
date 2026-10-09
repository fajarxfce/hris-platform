CREATE TABLE payroll_reviews (
 company_id uuid NOT NULL,id uuid NOT NULL,run_id uuid NOT NULL,review_number integer NOT NULL CHECK(review_number BETWEEN 1 AND 8),
 run_version bigint NOT NULL CHECK(run_version BETWEEN 0 AND 24),approval_id uuid NOT NULL,reference_date date NOT NULL,
 employee_count integer NOT NULL CHECK(employee_count BETWEEN 1 AND 5000),
 taxable_gross numeric(20,2) NOT NULL CHECK(taxable_gross BETWEEN 0 AND 999999999999999999.99),
 withheld numeric(20,2) NOT NULL CHECK(withheld BETWEEN -999999999999999999.99 AND 999999999999999999.99),
 take_home numeric(20,2) NOT NULL CHECK(take_home BETWEEN 0 AND 999999999999999999.99),
 submitted_by uuid NOT NULL REFERENCES accounts(id),submitted_at timestamptz NOT NULL,
 reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
 status varchar(16) NOT NULL CHECK(status IN('PENDING','APPROVED','REJECTED','WITHDRAWN')),version bigint NOT NULL CHECK(version BETWEEN 0 AND 9),
 PRIMARY KEY(company_id,id),UNIQUE(company_id,run_id,review_number),UNIQUE(company_id,approval_id),
 FOREIGN KEY(company_id,run_id) REFERENCES payroll_runs(company_id,id),
 FOREIGN KEY(company_id,approval_id) REFERENCES approval_requests(company_id,id)
);
CREATE UNIQUE INDEX payroll_review_active ON payroll_reviews(company_id,run_id) WHERE status IN('PENDING','APPROVED');
CREATE TABLE payroll_review_changes (
 company_id uuid NOT NULL,review_id uuid NOT NULL,revision bigint NOT NULL CHECK(revision BETWEEN 0 AND 9),
 action varchar(16) NOT NULL CHECK(action IN('SUBMITTED','APPROVED','REJECTED','WITHDRAWN')),
 status varchar(16) NOT NULL CHECK(status IN('PENDING','APPROVED','REJECTED','WITHDRAWN')),
 approval_version bigint NOT NULL CHECK(approval_version>=0),step integer CHECK(step BETWEEN 0 AND 7),
 actor_id uuid NOT NULL REFERENCES accounts(id),deciding_for uuid REFERENCES accounts(id),reason varchar(1000) NOT NULL,recorded_at timestamptz NOT NULL,
 PRIMARY KEY(company_id,review_id,revision),UNIQUE(company_id,review_id,step),FOREIGN KEY(company_id,review_id) REFERENCES payroll_reviews(company_id,id),
 CHECK((action IN('APPROVED','REJECTED') AND step IS NOT NULL AND deciding_for IS NOT NULL) OR (action IN('SUBMITTED','WITHDRAWN') AND step IS NULL AND deciding_for IS NULL))
);
ALTER TABLE payroll_reviews ADD FOREIGN KEY(company_id,id,version) REFERENCES payroll_review_changes(company_id,review_id,revision) DEFERRABLE INITIALLY DEFERRED;
CREATE FUNCTION protect_payroll_review() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r payroll_runs; a approval_requests; total_count integer; gross numeric; tax numeric; cash numeric; last payroll_reviews; BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payroll review history is retained' USING ERRCODE='23514'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('payroll:'||NEW.company_id::text,0));
 SELECT * INTO r FROM payroll_runs WHERE company_id=NEW.company_id AND id=NEW.run_id;
 IF r.id IS NULL OR r.status<>'CALCULATED' OR r.version<>NEW.run_version
  THEN RAISE EXCEPTION 'Payroll review requires its unchanged calculated run' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' THEN
  SELECT * INTO a FROM approval_requests WHERE company_id=NEW.company_id AND id=NEW.approval_id;
  SELECT * INTO last FROM payroll_reviews WHERE company_id=NEW.company_id AND run_id=NEW.run_id ORDER BY review_number DESC LIMIT 1;
  SELECT count(*),coalesce(sum(taxable_gross),0),coalesce(sum(withheld),0),coalesce(sum(take_home),0)
   INTO total_count,gross,tax,cash FROM payroll_run_results WHERE company_id=NEW.company_id AND run_id=NEW.run_id AND status='SUCCEEDED';
  IF NEW.version<>0 OR NEW.status<>'PENDING' OR NEW.review_number<>coalesce(last.review_number,0)+1 OR last.status IN('PENDING','APPROVED','REJECTED') OR
    r.failed<>0 OR r.succeeded<>r.total_employees OR (NEW.employee_count,NEW.taxable_gross,NEW.withheld,NEW.take_home) IS DISTINCT FROM (total_count,gross,tax,cash) OR NEW.employee_count<>r.total_employees OR
    a.id IS NULL OR a.kind<>'PAYROLL' OR a.resource_id<>NEW.id OR a.author_id<>NEW.submitted_by OR a.requester_id IS NOT NULL OR a.version<>0 OR
    a.status NOT IN('PENDING','BLOCKED') OR a.submitted_at<>NEW.submitted_at OR NOT a.excluded_account_ids @> ARRAY[r.actor_id] OR
    NEW.reference_date<>(NEW.submitted_at AT TIME ZONE r.timezone)::date
   THEN RAISE EXCEPTION 'Payroll review requires complete results and its independent approval snapshot' USING ERRCODE='23514'; END IF;
 ELSIF (to_jsonb(NEW)-ARRAY['status','version']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','version']) OR NEW.version<>OLD.version+1 OR
    NOT ((OLD.status='PENDING' AND NEW.status IN('PENDING','APPROVED','REJECTED','WITHDRAWN')) OR (OLD.status='APPROVED' AND NEW.status='WITHDRAWN'))
  THEN RAISE EXCEPTION 'Payroll review metadata and lifecycle are protected' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_review_protected BEFORE INSERT OR UPDATE OR DELETE ON payroll_reviews FOR EACH ROW EXECUTE FUNCTION protect_payroll_review();
CREATE FUNCTION validate_payroll_review_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r payroll_reviews; a approval_requests; d approval_decisions; BEGIN
 SELECT * INTO r FROM payroll_reviews WHERE company_id=NEW.company_id AND id=NEW.review_id;
 SELECT * INTO a FROM approval_requests WHERE company_id=NEW.company_id AND id=r.approval_id;
 IF r.id IS NULL OR r.version<>NEW.revision OR r.status<>NEW.status OR a.version<>NEW.approval_version OR NEW.actor_id IS DISTINCT FROM current_actor_id()
  THEN RAISE EXCEPTION 'Payroll review change must match its header and actor' USING ERRCODE='23514'; END IF;
 IF NEW.action='SUBMITTED' THEN
  IF NEW.revision<>0 OR NEW.status<>'PENDING' OR NEW.approval_version<>0 OR NEW.actor_id<>r.submitted_by OR NEW.reason<>r.reason OR NEW.recorded_at<>r.submitted_at
   THEN RAISE EXCEPTION 'Payroll submission evidence must match its original snapshot' USING ERRCODE='23514'; END IF;
 ELSIF NEW.action='WITHDRAWN' THEN
  IF NEW.revision=0 OR NEW.status<>'WITHDRAWN' OR a.status<>'CANCELLED' OR length(trim(NEW.reason))=0
   THEN RAISE EXCEPTION 'Payroll withdrawal must cancel its approval' USING ERRCODE='23514'; END IF;
 ELSE
  SELECT * INTO d FROM approval_decisions WHERE company_id=NEW.company_id AND request_id=a.id AND step=NEW.step;
  IF NEW.revision=0 OR d.actor_id IS NULL OR (d.actor_id,d.deciding_for,d.reason,d.decided_at,d.decision) IS DISTINCT FROM
    (NEW.actor_id,NEW.deciding_for,NEW.reason,NEW.recorded_at,CASE WHEN NEW.action='APPROVED' THEN 'APPROVE' ELSE 'REJECT' END) OR
    NOT ((NEW.action='APPROVED' AND NEW.status='PENDING' AND a.status IN('PENDING','BLOCKED') AND a.current_step=NEW.step+1) OR
      (NEW.action='APPROVED' AND NEW.status='APPROVED' AND a.status='APPROVED' AND a.current_step=NEW.step) OR
      (NEW.action='REJECTED' AND NEW.status='REJECTED' AND a.status='REJECTED' AND a.current_step=NEW.step))
   THEN RAISE EXCEPTION 'Payroll review requires its exact independent decision' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_review_change_scope BEFORE INSERT ON payroll_review_changes FOR EACH ROW EXECUTE FUNCTION validate_payroll_review_change();
CREATE FUNCTION assert_payroll_review(p_company uuid,p_review uuid) RETURNS void LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE v payroll_reviews; r payroll_runs; a approval_requests; BEGIN
 SELECT * INTO v FROM payroll_reviews WHERE company_id=p_company AND id=p_review;
 IF v.id IS NULL THEN RAISE EXCEPTION 'Payroll approval requires its review snapshot' USING ERRCODE='23514'; END IF;
 SELECT * INTO r FROM payroll_runs WHERE company_id=p_company AND id=v.run_id;
 SELECT * INTO a FROM approval_requests WHERE company_id=p_company AND id=v.approval_id;
 IF a.id IS NULL OR a.resource_id<>v.id OR a.kind<>'PAYROLL' OR
   (v.status IN('PENDING','APPROVED') AND (r.status<>'CALCULATED' OR r.version<>v.run_version)) OR
   (v.status='PENDING' AND a.status NOT IN('PENDING','BLOCKED')) OR (v.status='APPROVED' AND a.status<>'APPROVED') OR
   (v.status='REJECTED' AND a.status<>'REJECTED') OR (v.status='WITHDRAWN' AND a.status<>'CANCELLED') OR
   NOT EXISTS(SELECT 1 FROM payroll_review_changes WHERE company_id=p_company AND review_id=v.id AND revision=v.version AND status=v.status)
  THEN RAISE EXCEPTION 'Payroll review, approval and retained evidence must agree' USING ERRCODE='23514'; END IF;
END $$;
CREATE FUNCTION check_payroll_review() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_TABLE_NAME='payroll_reviews' THEN PERFORM assert_payroll_review(NEW.company_id,NEW.id);
 ELSIF TG_TABLE_NAME='payroll_review_changes' THEN PERFORM assert_payroll_review(NEW.company_id,NEW.review_id);
 ELSIF NEW.kind='PAYROLL' THEN PERFORM assert_payroll_review(NEW.company_id,NEW.resource_id); END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payroll_review_complete AFTER INSERT OR UPDATE ON payroll_reviews DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_review();
CREATE CONSTRAINT TRIGGER payroll_review_change_complete AFTER INSERT ON payroll_review_changes DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_review();
CREATE CONSTRAINT TRIGGER approval_payroll_complete AFTER INSERT OR UPDATE ON approval_requests DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_review();
CREATE FUNCTION guard_reviewed_payroll_run() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.status='ABANDONED' AND EXISTS(SELECT 1 FROM payroll_reviews WHERE company_id=NEW.company_id AND run_id=NEW.id AND status IN('PENDING','APPROVED'))
  THEN RAISE EXCEPTION 'Payroll review must be explicitly withdrawn before abandonment' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_run_review_guard BEFORE UPDATE ON payroll_runs FOR EACH ROW EXECUTE FUNCTION guard_reviewed_payroll_run();
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['payroll_reviews','payroll_review_changes'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
 END LOOP;
END $$;
CREATE POLICY payroll_review_creator ON payroll_reviews AS RESTRICTIVE FOR INSERT WITH CHECK(submitted_by=current_actor_id());
CREATE TRIGGER payroll_review_history_immutable BEFORE UPDATE OR DELETE ON payroll_review_changes FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
