ALTER TABLE payroll_amendment_revisions DROP CONSTRAINT payroll_amendment_revisions_status_check;
ALTER TABLE payroll_amendment_revisions ADD CHECK(status IN('DRAFT','CALCULATED','IN_REVIEW','APPROVED','REJECTED','CANCELLED'));
ALTER TABLE payroll_amendment_revisions DROP CONSTRAINT payroll_amendment_revisions_action_check;
ALTER TABLE payroll_amendment_revisions ADD CHECK(action IN('SAVED','CALCULATED','CALCULATION_FAILED','SUBMITTED','APPROVED','REJECTED','WITHDRAWN','CANCELLED'));
ALTER TABLE payroll_amendment_revisions ADD COLUMN latest_review_id uuid;

CREATE TABLE payroll_amendment_reviews (
 company_id uuid NOT NULL,id uuid NOT NULL,amendment_id uuid NOT NULL,
 review_number integer NOT NULL CHECK(review_number BETWEEN 1 AND 8),
 amendment_version bigint NOT NULL CHECK(amendment_version BETWEEN 0 AND 987),calculation_id uuid NOT NULL,
 approval_id uuid NOT NULL,reference_date date NOT NULL,timezone varchar(80) NOT NULL,
 exposure numeric(22,2) NOT NULL CHECK(exposure>=0),
 submitted_by uuid NOT NULL REFERENCES accounts(id),submitted_at timestamptz NOT NULL,
 reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
 status varchar(16) NOT NULL CHECK(status IN('PENDING','APPROVED','REJECTED','WITHDRAWN')),
 version bigint NOT NULL CHECK(version BETWEEN 0 AND 9),
 PRIMARY KEY(company_id,id),UNIQUE(company_id,amendment_id,id),UNIQUE(company_id,amendment_id,review_number),UNIQUE(company_id,approval_id),
 FOREIGN KEY(company_id,amendment_id,amendment_version) REFERENCES payroll_amendment_revisions(company_id,amendment_id,revision),
 FOREIGN KEY(company_id,amendment_id,calculation_id) REFERENCES payroll_amendment_calculations(company_id,amendment_id,id),
 FOREIGN KEY(company_id,approval_id) REFERENCES approval_requests(company_id,id)
);
CREATE UNIQUE INDEX payroll_amendment_review_active ON payroll_amendment_reviews(company_id,amendment_id) WHERE status IN('PENDING','APPROVED');
ALTER TABLE payroll_amendment_revisions ADD CONSTRAINT payroll_amendment_review_owner FOREIGN KEY(company_id,amendment_id,latest_review_id)
 REFERENCES payroll_amendment_reviews(company_id,amendment_id,id) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE payroll_amendment_review_changes (
 company_id uuid NOT NULL,review_id uuid NOT NULL,amendment_id uuid NOT NULL,
 revision bigint NOT NULL CHECK(revision BETWEEN 0 AND 9),amendment_revision bigint NOT NULL CHECK(amendment_revision BETWEEN 1 AND 998),
 action varchar(16) NOT NULL CHECK(action IN('SUBMITTED','APPROVED','REJECTED','WITHDRAWN')),
 status varchar(16) NOT NULL CHECK(status IN('PENDING','APPROVED','REJECTED','WITHDRAWN')),
 approval_version bigint NOT NULL CHECK(approval_version>=0),step integer CHECK(step BETWEEN 0 AND 7),
 actor_id uuid NOT NULL REFERENCES accounts(id),deciding_for uuid REFERENCES accounts(id),
 reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),recorded_at timestamptz NOT NULL,
 PRIMARY KEY(company_id,review_id,revision),UNIQUE(company_id,review_id,step),UNIQUE(company_id,amendment_id,amendment_revision),
 FOREIGN KEY(company_id,amendment_id,review_id) REFERENCES payroll_amendment_reviews(company_id,amendment_id,id),
 FOREIGN KEY(company_id,amendment_id,amendment_revision) REFERENCES payroll_amendment_revisions(company_id,amendment_id,revision) DEFERRABLE INITIALLY DEFERRED,
 CHECK((action IN('APPROVED','REJECTED') AND step IS NOT NULL AND deciding_for IS NOT NULL) OR
       (action IN('SUBMITTED','WITHDRAWN') AND step IS NULL AND deciding_for IS NULL))
);
ALTER TABLE payroll_amendment_reviews ADD CONSTRAINT payroll_amendment_review_current_change FOREIGN KEY(company_id,id,version)
 REFERENCES payroll_amendment_review_changes(company_id,review_id,revision) DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION protect_payroll_amendment_review() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE h payroll_amendments; r payroll_amendment_revisions; c payroll_amendment_calculations; a approval_requests;
 last payroll_amendment_reviews; t approval_templates; terms approval_template_revisions;
 beneficiary uuid; makers uuid[]; zone text; amount numeric; result_count integer; BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Amendment reviews are retained' USING ERRCODE='23514'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('payroll:'||NEW.company_id::text,0));
 SELECT * INTO h FROM payroll_amendments WHERE company_id=NEW.company_id AND id=NEW.amendment_id;
 SELECT * INTO r FROM payroll_amendment_revisions WHERE company_id=NEW.company_id AND amendment_id=h.id AND revision=h.version;
 IF h.id IS NULL OR r.revision IS NULL THEN RAISE EXCEPTION 'Amendment review requires its current draft' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' THEN
  SELECT * INTO c FROM payroll_amendment_calculations WHERE company_id=NEW.company_id AND id=NEW.calculation_id AND amendment_id=h.id;
  SELECT * INTO a FROM approval_requests WHERE company_id=NEW.company_id AND id=NEW.approval_id;
  SELECT * INTO last FROM payroll_amendment_reviews WHERE company_id=NEW.company_id AND amendment_id=h.id ORDER BY review_number DESC LIMIT 1;
  SELECT * INTO t FROM approval_templates WHERE company_id=NEW.company_id AND id=a.template_id;
  SELECT * INTO terms FROM approval_template_revisions WHERE company_id=NEW.company_id AND template_id=a.template_id AND revision=a.template_revision;
  SELECT account_id INTO beneficiary FROM persons WHERE id=h.person_id;
  SELECT timezone INTO zone FROM companies WHERE id=NEW.company_id;
  SELECT coalesce(array_agg(DISTINCT actor_id),'{}') INTO makers FROM payroll_amendment_revisions
   WHERE company_id=NEW.company_id AND amendment_id=h.id AND revision<=h.version AND action IN('SAVED','CALCULATED','CALCULATION_FAILED','SUBMITTED');
  SELECT count(*),coalesce(sum(greatest(abs(difference_taxable_gross),abs(difference_withheld),abs(difference_take_home),
    abs(difference_employee_deductions),abs(difference_employer_cost))),0)
   INTO result_count,amount FROM payroll_amendment_results WHERE company_id=NEW.company_id AND calculation_id=NEW.calculation_id;
  IF NEW.version<>0 OR NEW.status<>'PENDING' OR NEW.submitted_by IS DISTINCT FROM current_actor_id() OR
   NEW.submitted_by=beneficiary OR NEW.review_number<>coalesce(last.review_number,0)+1 OR last.status IN('PENDING','APPROVED') OR
   (last.status='REJECTED' AND last.calculation_id=NEW.calculation_id) OR
   r.status<>'CALCULATED' OR h.version<>NEW.amendment_version OR r.latest_calculation_id IS DISTINCT FROM NEW.calculation_id OR
   c.id IS NULL OR c.status<>'SUCCEEDED' OR result_count<>c.assessment_count OR NEW.exposure<>amount OR
   NEW.timezone<>zone OR NEW.reference_date<>(NEW.submitted_at AT TIME ZONE NEW.timezone)::date OR
   a.id IS NULL OR a.kind<>'PAYROLL_AMENDMENT' OR a.resource_id<>NEW.id OR a.author_id<>NEW.submitted_by OR
   a.requester_id IS DISTINCT FROM beneficiary OR a.version<>0 OR a.current_step<>0 OR a.status NOT IN('PENDING','BLOCKED') OR a.submitted_at<>NEW.submitted_at OR
   NOT (a.excluded_account_ids @> makers AND makers @> a.excluded_account_ids) OR
   t.id IS NULL OR t.kind<>'PAYROLL_AMENDMENT' OR NOT t.active OR terms.revision IS NULL OR terms.category IS NOT NULL OR
   terms.effective_from>NEW.reference_date OR terms.minimum_amount>amount OR
   EXISTS(SELECT 1 FROM jsonb_array_elements(terms.stages) s WHERE s->>'assignment'='MANAGER') OR
   EXISTS(SELECT 1 FROM jsonb_array_elements(a.stages) s,LATERAL jsonb_array_elements_text(s->'assignees') party
    WHERE party::uuid=a.author_id OR party::uuid=a.requester_id)
   THEN RAISE EXCEPTION 'Amendment review requires its exact calculated basis and independent approval' USING ERRCODE='23514'; END IF;
  PERFORM assert_payroll_amendment_basis(NEW.company_id,h.person_id,h.first_month,h.first_assessment_id,r.terms);
 ELSE
  IF (to_jsonb(NEW)-ARRAY['status','version']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','version']) OR NEW.version<>OLD.version+1 OR
   r.latest_review_id IS DISTINCT FROM OLD.id OR r.latest_calculation_id IS DISTINCT FROM OLD.calculation_id OR
   r.status<>(CASE WHEN OLD.status='PENDING' THEN 'IN_REVIEW' ELSE 'APPROVED' END) OR
   NOT ((OLD.status='PENDING' AND NEW.status IN('PENDING','APPROVED','REJECTED','WITHDRAWN')) OR (OLD.status='APPROVED' AND NEW.status='WITHDRAWN'))
   THEN RAISE EXCEPTION 'Amendment review basis and lifecycle are protected' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_amendment_review_protected BEFORE INSERT OR UPDATE OR DELETE ON payroll_amendment_reviews FOR EACH ROW EXECUTE FUNCTION protect_payroll_amendment_review();

CREATE FUNCTION validate_payroll_amendment_review_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v payroll_amendment_reviews; h payroll_amendments; a approval_requests; d approval_decisions;
 previous payroll_amendment_review_changes; assignments bigint; beneficiary uuid; BEGIN
 SELECT * INTO v FROM payroll_amendment_reviews WHERE company_id=NEW.company_id AND id=NEW.review_id;
 SELECT * INTO h FROM payroll_amendments WHERE company_id=NEW.company_id AND id=v.amendment_id;
 SELECT * INTO a FROM approval_requests WHERE company_id=NEW.company_id AND id=v.approval_id;
 IF v.id IS NULL OR h.id IS NULL OR a.id IS NULL OR v.amendment_id<>NEW.amendment_id OR NEW.amendment_revision<>h.version+1 OR
  v.version<>NEW.revision OR v.status<>NEW.status OR a.version<>NEW.approval_version OR NEW.actor_id IS DISTINCT FROM current_actor_id()
  THEN RAISE EXCEPTION 'Amendment review change must match its headers and actor' USING ERRCODE='23514'; END IF;
 IF NEW.action='SUBMITTED' THEN
  IF NEW.revision<>0 OR NEW.status<>'PENDING' OR NEW.approval_version<>0 OR NEW.amendment_revision<>v.amendment_version+1 OR
   NEW.actor_id<>v.submitted_by OR NEW.reason<>v.reason OR NEW.recorded_at<>v.submitted_at
   THEN RAISE EXCEPTION 'Amendment submission evidence must match its snapshot' USING ERRCODE='23514'; END IF;
 ELSE
  SELECT * INTO previous FROM payroll_amendment_review_changes WHERE company_id=NEW.company_id AND review_id=v.id AND revision=NEW.revision-1;
  SELECT count(*) INTO assignments FROM approval_assignment_overrides WHERE company_id=NEW.company_id AND request_id=a.id
   AND revision>previous.approval_version AND revision<NEW.approval_version;
  IF previous.revision IS NULL OR NEW.approval_version<=previous.approval_version OR NEW.approval_version-previous.approval_version<>assignments+1
   THEN RAISE EXCEPTION 'Amendment approval versions require complete decision and assignment evidence' USING ERRCODE='23514'; END IF;
  IF NEW.action='WITHDRAWN' THEN
   IF NEW.status<>'WITHDRAWN' OR a.status<>'CANCELLED'
    THEN RAISE EXCEPTION 'Amendment withdrawal must cancel its approval' USING ERRCODE='23514'; END IF;
  ELSE
   SELECT account_id INTO beneficiary FROM persons WHERE id=h.person_id;
   SELECT * INTO d FROM approval_decisions WHERE company_id=NEW.company_id AND request_id=a.id AND step=NEW.step;
   IF NEW.step<>NEW.revision-1 OR d.actor_id IS NULL OR NEW.actor_id=beneficiary OR NEW.deciding_for=beneficiary OR
    (d.actor_id,d.deciding_for,d.reason,d.decided_at,d.decision) IS DISTINCT FROM
     (NEW.actor_id,NEW.deciding_for,NEW.reason,NEW.recorded_at,CASE WHEN NEW.action='APPROVED' THEN 'APPROVE' ELSE 'REJECT' END) OR
    NOT ((NEW.action='APPROVED' AND NEW.status='PENDING' AND a.status IN('PENDING','BLOCKED') AND a.current_step=NEW.step+1) OR
         (NEW.action='APPROVED' AND NEW.status='APPROVED' AND a.status='APPROVED' AND a.current_step=NEW.step) OR
         (NEW.action='REJECTED' AND NEW.status='REJECTED' AND a.status='REJECTED' AND a.current_step=NEW.step))
    THEN RAISE EXCEPTION 'Amendment review requires its exact independent decision' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_amendment_review_change_valid BEFORE INSERT ON payroll_amendment_review_changes FOR EACH ROW EXECUTE FUNCTION validate_payroll_amendment_review_change();
CREATE TRIGGER payroll_amendment_review_change_immutable BEFORE UPDATE OR DELETE ON payroll_amendment_review_changes FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

-- Lifecycle changes and draft revisions are one aggregate commit. Editing requires withdrawal.
CREATE OR REPLACE FUNCTION validate_payroll_amendment_revision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE h payroll_amendments; previous payroll_amendment_revisions; v payroll_amendment_reviews; change payroll_amendment_review_changes; BEGIN
 SELECT * INTO h FROM payroll_amendments WHERE company_id=NEW.company_id AND id=NEW.amendment_id;
 IF h.id IS NULL OR h.version<>NEW.revision OR NEW.actor_id IS DISTINCT FROM current_actor_id()
  THEN RAISE EXCEPTION 'Amendment revision must match its current header and actor' USING ERRCODE='23514'; END IF;
 IF NEW.revision=999 AND NEW.action<>'CANCELLED'
  THEN RAISE EXCEPTION 'The final amendment revision is reserved for cancellation' USING ERRCODE='23514'; END IF;
 IF NEW.revision=0 THEN
  IF NEW.action<>'SAVED' OR NEW.status<>'DRAFT' OR NEW.latest_calculation_id IS NOT NULL OR NEW.latest_review_id IS NOT NULL OR
   NEW.actor_id<>h.created_by OR NEW.recorded_at<>h.created_at
   THEN RAISE EXCEPTION 'Amendment starts with its original draft' USING ERRCODE='23514'; END IF;
 ELSE
  SELECT * INTO previous FROM payroll_amendment_revisions WHERE company_id=NEW.company_id AND amendment_id=NEW.amendment_id AND revision=NEW.revision-1;
  IF previous.revision IS NULL OR previous.status='CANCELLED'
   THEN RAISE EXCEPTION 'Amendment requires its previous live revision' USING ERRCODE='23514'; END IF;
  IF NEW.action IN('SUBMITTED','APPROVED','REJECTED','WITHDRAWN') THEN
   SELECT * INTO v FROM payroll_amendment_reviews WHERE company_id=NEW.company_id AND amendment_id=NEW.amendment_id AND id=NEW.latest_review_id;
   SELECT * INTO change FROM payroll_amendment_review_changes WHERE company_id=NEW.company_id AND review_id=v.id AND amendment_revision=NEW.revision;
   IF v.id IS NULL OR change.revision IS NULL OR change.revision<>v.version OR NEW.terms<>previous.terms OR
    NEW.latest_calculation_id IS DISTINCT FROM previous.latest_calculation_id OR NEW.latest_calculation_id IS DISTINCT FROM v.calculation_id OR
    (NEW.action,NEW.actor_id,NEW.recorded_at,NEW.reason) IS DISTINCT FROM (change.action,change.actor_id,change.recorded_at,change.reason) OR
    NEW.status<>(CASE change.status WHEN 'PENDING' THEN 'IN_REVIEW' WHEN 'WITHDRAWN' THEN 'CALCULATED' ELSE change.status END) OR
    NOT ((NEW.action='SUBMITTED' AND previous.status='CALCULATED' AND v.amendment_version=previous.revision AND
          NEW.latest_review_id IS DISTINCT FROM previous.latest_review_id AND change.revision=0) OR
         (NEW.action IN('APPROVED','REJECTED') AND previous.status='IN_REVIEW' AND NEW.latest_review_id=previous.latest_review_id) OR
         (NEW.action='WITHDRAWN' AND previous.status IN('IN_REVIEW','APPROVED') AND NEW.latest_review_id=previous.latest_review_id))
    THEN RAISE EXCEPTION 'Amendment revision requires its exact review transition' USING ERRCODE='23514'; END IF;
  ELSE
   IF NEW.latest_review_id IS DISTINCT FROM previous.latest_review_id OR
    NOT ((NEW.action='SAVED' AND previous.status IN('DRAFT','CALCULATED','REJECTED') AND NEW.status='DRAFT' AND NEW.latest_calculation_id IS NOT DISTINCT FROM previous.latest_calculation_id) OR
     (NEW.action='CANCELLED' AND previous.status IN('DRAFT','CALCULATED','REJECTED') AND NEW.status='CANCELLED' AND NEW.terms=previous.terms AND NEW.latest_calculation_id IS NOT DISTINCT FROM previous.latest_calculation_id) OR
     (NEW.action IN('CALCULATED','CALCULATION_FAILED') AND previous.status='DRAFT' AND NEW.terms=previous.terms AND NEW.latest_calculation_id IS NOT NULL AND
      NEW.latest_calculation_id IS DISTINCT FROM previous.latest_calculation_id AND NEW.status=(CASE WHEN NEW.action='CALCULATED' THEN 'CALCULATED' ELSE 'DRAFT' END)))
    THEN RAISE EXCEPTION 'Amendment draft transition is invalid' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 IF NEW.action IN('SAVED','SUBMITTED','APPROVED') THEN
  PERFORM assert_payroll_amendment_basis(NEW.company_id,h.person_id,h.first_month,h.first_assessment_id,NEW.terms);
 END IF;
 RETURN NEW;
END $$;

CREATE FUNCTION assert_payroll_amendment_review(p_company uuid,p_review uuid) RETURNS void LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE v payroll_amendment_reviews; h payroll_amendments; r payroll_amendment_revisions; a approval_requests;
 change payroll_amendment_review_changes; retained payroll_amendment_revisions; assignments bigint; decisions bigint; changes bigint; BEGIN
 SELECT * INTO v FROM payroll_amendment_reviews WHERE company_id=p_company AND id=p_review;
 IF v.id IS NULL THEN RAISE EXCEPTION 'Amendment approval requires its retained review' USING ERRCODE='23514'; END IF;
 SELECT * INTO h FROM payroll_amendments WHERE company_id=p_company AND id=v.amendment_id;
 SELECT * INTO r FROM payroll_amendment_revisions WHERE company_id=p_company AND amendment_id=h.id AND revision=h.version;
 SELECT * INTO a FROM approval_requests WHERE company_id=p_company AND id=v.approval_id;
 SELECT * INTO change FROM payroll_amendment_review_changes WHERE company_id=p_company AND review_id=v.id AND revision=v.version;
 SELECT * INTO retained FROM payroll_amendment_revisions WHERE company_id=p_company AND amendment_id=v.amendment_id AND revision=change.amendment_revision;
 SELECT count(*) INTO assignments FROM approval_assignment_overrides WHERE company_id=p_company AND request_id=a.id
  AND revision>change.approval_version AND revision<=a.version;
 SELECT count(*) INTO decisions FROM approval_decisions WHERE company_id=p_company AND request_id=a.id;
 SELECT count(*) INTO changes FROM payroll_amendment_review_changes WHERE company_id=p_company AND review_id=v.id AND action IN('APPROVED','REJECTED');
 IF a.id IS NULL OR a.kind<>'PAYROLL_AMENDMENT' OR a.resource_id<>v.id OR h.id IS NULL OR r.revision IS NULL OR
  change.revision IS NULL OR change.status<>v.status OR retained.revision IS NULL OR retained.latest_review_id IS DISTINCT FROM v.id OR
  (retained.action,retained.actor_id,retained.recorded_at,retained.reason) IS DISTINCT FROM (change.action,change.actor_id,change.recorded_at,change.reason) OR
  retained.status<>(CASE change.status WHEN 'PENDING' THEN 'IN_REVIEW' WHEN 'WITHDRAWN' THEN 'CALCULATED' ELSE change.status END) OR
  retained.latest_calculation_id IS DISTINCT FROM v.calculation_id OR
  change.approval_version>a.version OR a.version-change.approval_version<>assignments OR decisions<>changes OR
  (v.status IN('PENDING','APPROVED') AND (r.latest_review_id IS DISTINCT FROM v.id OR r.latest_calculation_id IS DISTINCT FROM v.calculation_id OR
    r.revision<>change.amendment_revision OR r.status<>(CASE WHEN v.status='PENDING' THEN 'IN_REVIEW' ELSE 'APPROVED' END))) OR
  (v.status='PENDING' AND (a.status NOT IN('PENDING','BLOCKED') OR a.current_step<>v.version)) OR
  (v.status='APPROVED' AND (a.status<>'APPROVED' OR a.current_step<>v.version-1 OR v.version<>jsonb_array_length(a.stages))) OR
  (v.status='REJECTED' AND (a.status<>'REJECTED' OR a.current_step<>v.version-1)) OR
  (v.status='WITHDRAWN' AND a.status<>'CANCELLED')
  THEN RAISE EXCEPTION 'Amendment, review, approval and retained changes must commit together' USING ERRCODE='23514'; END IF;
END $$;

CREATE FUNCTION check_payroll_amendment_review() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE approval approval_requests; BEGIN
 IF TG_TABLE_NAME='payroll_amendment_reviews' THEN PERFORM assert_payroll_amendment_review(NEW.company_id,NEW.id);
 ELSIF TG_TABLE_NAME='payroll_amendment_review_changes' THEN PERFORM assert_payroll_amendment_review(NEW.company_id,NEW.review_id);
 ELSIF TG_TABLE_NAME='payroll_amendment_revisions' THEN
  IF NEW.latest_review_id IS NOT NULL THEN PERFORM assert_payroll_amendment_review(NEW.company_id,NEW.latest_review_id); END IF;
 ELSIF TG_TABLE_NAME='approval_decisions' OR TG_TABLE_NAME='approval_assignment_overrides' THEN
  SELECT * INTO approval FROM approval_requests WHERE company_id=NEW.company_id AND id=NEW.request_id;
  IF approval.kind='PAYROLL_AMENDMENT' THEN PERFORM assert_payroll_amendment_review(NEW.company_id,approval.resource_id); END IF;
 ELSIF NEW.kind='PAYROLL_AMENDMENT' THEN PERFORM assert_payroll_amendment_review(NEW.company_id,NEW.resource_id);
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payroll_amendment_review_complete AFTER INSERT OR UPDATE ON payroll_amendment_reviews DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_amendment_review();
CREATE CONSTRAINT TRIGGER payroll_amendment_review_change_complete AFTER INSERT ON payroll_amendment_review_changes DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_amendment_review();
CREATE CONSTRAINT TRIGGER payroll_amendment_revision_review_complete AFTER INSERT ON payroll_amendment_revisions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_amendment_review();
CREATE CONSTRAINT TRIGGER approval_amendment_complete AFTER INSERT OR UPDATE ON approval_requests DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_amendment_review();
CREATE CONSTRAINT TRIGGER approval_amendment_decision_complete AFTER INSERT ON approval_decisions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_amendment_review();
CREATE CONSTRAINT TRIGGER approval_amendment_assignment_complete AFTER INSERT ON approval_assignment_overrides DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_amendment_review();

CREATE FUNCTION protect_payroll_amendment_decision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a approval_requests; beneficiary uuid; BEGIN
 SELECT * INTO a FROM approval_requests WHERE company_id=NEW.company_id AND id=NEW.request_id;
 IF a.kind='PAYROLL_AMENDMENT' THEN
  SELECT p.account_id INTO beneficiary FROM payroll_amendment_reviews v
   JOIN payroll_amendments h ON h.company_id=v.company_id AND h.id=v.amendment_id JOIN persons p ON p.id=h.person_id
   WHERE v.company_id=NEW.company_id AND v.id=a.resource_id;
  IF NEW.actor_id=ANY(array_remove(ARRAY[a.author_id,a.requester_id,beneficiary],NULL)) OR
   NEW.deciding_for=ANY(array_remove(ARRAY[a.author_id,a.requester_id,beneficiary],NULL))
   THEN RAISE EXCEPTION 'Amendment preparers and beneficiaries cannot decide approval' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_amendment_decision_independent BEFORE INSERT ON approval_decisions FOR EACH ROW EXECUTE FUNCTION protect_payroll_amendment_decision();

DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['payroll_amendment_reviews','payroll_amendment_review_changes'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
 END LOOP;
END $$;
CREATE POLICY payroll_amendment_review_creator ON payroll_amendment_reviews AS RESTRICTIVE FOR INSERT WITH CHECK(submitted_by=current_actor_id());
