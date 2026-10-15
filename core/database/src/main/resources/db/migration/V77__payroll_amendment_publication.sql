-- Approved correction publication owns a bounded attempt and a fenced worker job.
-- Drain existing publishers before copying holiday ownership and installing capture.
SET LOCAL lock_timeout = '5s';
LOCK TABLE payroll_assessments IN SHARE ROW EXCLUSIVE MODE;

ALTER TABLE payroll_amendment_revisions DROP CONSTRAINT payroll_amendment_revisions_status_check;
ALTER TABLE payroll_amendment_revisions ADD CHECK(status IN('DRAFT','CALCULATED','IN_REVIEW','APPROVED','REJECTED','CANCELLED','PUBLISHING','PUBLISHED'));
ALTER TABLE payroll_amendment_revisions DROP CONSTRAINT payroll_amendment_revisions_action_check;
ALTER TABLE payroll_amendment_revisions ADD CHECK(action IN('SAVED','CALCULATED','CALCULATION_FAILED','SUBMITTED','APPROVED','REJECTED','WITHDRAWN','CANCELLED','PUBLICATION_STARTED','PUBLISHED','PUBLICATION_STOPPED'));
ALTER TABLE payroll_amendment_revisions ADD COLUMN latest_publication_id uuid;
CREATE TABLE payroll_amendment_publications (
 company_id uuid NOT NULL,id uuid NOT NULL,amendment_id uuid NOT NULL,review_id uuid NOT NULL,calculation_id uuid NOT NULL,
 amendment_version bigint NOT NULL CHECK(amendment_version BETWEEN 0 AND 996),
 review_version bigint NOT NULL CHECK(review_version BETWEEN 1 AND 8),approval_version bigint NOT NULL CHECK(approval_version>=1),
 attempt integer NOT NULL CHECK(attempt BETWEEN 1 AND 8),job_id uuid NOT NULL,
 actor_id uuid NOT NULL REFERENCES accounts(id),requested_at timestamptz NOT NULL,
 reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
 status varchar(16) NOT NULL CHECK(status IN('PENDING','PUBLISHED','STOPPED')),
 completed_at timestamptz,completed_by uuid REFERENCES accounts(id),failure_code varchar(120),
 PRIMARY KEY(company_id,id),UNIQUE(company_id,amendment_id,id),UNIQUE(company_id,amendment_id,attempt),UNIQUE(company_id,job_id),
 FOREIGN KEY(company_id,amendment_id,amendment_version) REFERENCES payroll_amendment_revisions(company_id,amendment_id,revision),
 FOREIGN KEY(company_id,amendment_id,review_id) REFERENCES payroll_amendment_reviews(company_id,amendment_id,id),
 FOREIGN KEY(company_id,amendment_id,calculation_id) REFERENCES payroll_amendment_calculations(company_id,amendment_id,id),
 FOREIGN KEY(company_id,job_id) REFERENCES background_jobs(company_id,id),
 CHECK((status='PENDING' AND completed_at IS NULL AND completed_by IS NULL AND failure_code IS NULL) OR
  (status='PUBLISHED' AND completed_at>=requested_at AND completed_at IS NOT NULL AND completed_by IS NOT NULL AND failure_code IS NULL) OR
  (status='STOPPED' AND completed_at>=requested_at AND completed_at IS NOT NULL AND completed_by IS NOT NULL AND failure_code ~ '^[a-z][a-z0-9_]{0,119}$' AND failure_code IS NOT NULL))
);
CREATE UNIQUE INDEX payroll_amendment_publication_active ON payroll_amendment_publications(company_id,amendment_id) WHERE status IN('PENDING','PUBLISHED');
ALTER TABLE payroll_amendment_revisions ADD CONSTRAINT payroll_amendment_publication_owner FOREIGN KEY(company_id,amendment_id,latest_publication_id)
 REFERENCES payroll_amendment_publications(company_id,amendment_id,id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE payroll_amendment_publications ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_amendment_publications FORCE ROW LEVEL SECURITY;
CREATE POLICY company_scope ON payroll_amendment_publications USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY payroll_amendment_publication_creator ON payroll_amendment_publications AS RESTRICTIVE FOR INSERT WITH CHECK(actor_id=current_actor_id());

CREATE FUNCTION protect_payroll_amendment_publication() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE h payroll_amendments; r payroll_amendment_revisions; v payroll_amendment_reviews; a approval_requests;
 c payroll_amendment_calculations; j background_jobs; previous payroll_amendment_publications; beneficiary uuid;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Amendment publication history is retained' USING ERRCODE='23514'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('payroll:'||NEW.company_id::text,0));
 SELECT * INTO h FROM payroll_amendments WHERE company_id=NEW.company_id AND id=NEW.amendment_id;
 SELECT * INTO r FROM payroll_amendment_revisions WHERE company_id=NEW.company_id AND amendment_id=h.id AND revision=h.version;
 SELECT * INTO v FROM payroll_amendment_reviews WHERE company_id=NEW.company_id AND id=NEW.review_id;
 SELECT * INTO a FROM approval_requests WHERE company_id=NEW.company_id AND id=v.approval_id;
 SELECT * INTO c FROM payroll_amendment_calculations WHERE company_id=NEW.company_id AND id=NEW.calculation_id;
 SELECT * INTO j FROM background_jobs WHERE company_id=NEW.company_id AND id=NEW.job_id;
 IF h.id IS NULL OR r.revision IS NULL OR j.id IS NULL OR j.kind<>'PAYROLL_AMENDMENT_PUBLISH' OR j.actor_id<>NEW.actor_id OR
  j.request->>'publicationId' IS DISTINCT FROM NEW.id::text OR j.request->>'amendmentId' IS DISTINCT FROM NEW.amendment_id::text OR
  j.total_items<>1 OR j.progress_mode<>'FIXED_TOTAL'
  THEN RAISE EXCEPTION 'Amendment publication requires its owned job and draft' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' OR NEW.status='PUBLISHED' THEN
  SELECT account_id INTO beneficiary FROM persons WHERE id=h.person_id;
  IF v.id IS NULL OR a.id IS NULL OR c.id IS NULL OR v.amendment_id<>h.id OR v.calculation_id<>NEW.calculation_id OR
   v.status<>'APPROVED' OR v.version<>NEW.review_version OR a.status<>'APPROVED' OR a.version<>NEW.approval_version OR
   a.kind<>'PAYROLL_AMENDMENT' OR a.resource_id<>v.id OR c.amendment_id<>h.id OR c.status<>'SUCCEEDED' OR
   r.latest_review_id IS DISTINCT FROM v.id OR r.latest_calculation_id IS DISTINCT FROM c.id OR
   NEW.actor_id IS DISTINCT FROM current_actor_id() OR NEW.actor_id=beneficiary OR NEW.actor_id=a.requester_id
   THEN RAISE EXCEPTION 'Amendment publication requires its exact approved calculation and independent actor' USING ERRCODE='23514'; END IF;
  PERFORM assert_payroll_amendment_basis(NEW.company_id,h.person_id,h.first_month,h.first_assessment_id,r.terms);
 END IF;
 IF TG_OP='INSERT' THEN
  SELECT * INTO previous FROM payroll_amendment_publications WHERE company_id=NEW.company_id AND amendment_id=h.id ORDER BY attempt DESC LIMIT 1;
  IF r.status<>'APPROVED' OR h.version<>NEW.amendment_version OR NEW.status<>'PENDING' OR NEW.attempt<>coalesce(previous.attempt,0)+1 OR
   (previous.id IS NOT NULL AND previous.status<>'STOPPED') OR j.status<>'QUEUED' OR j.completed_items<>0 OR NEW.requested_at<>j.created_at
   THEN RAISE EXCEPTION 'Publication admission requires its observed approved draft and next attempt' USING ERRCODE='23514'; END IF;
 ELSE
  IF (to_jsonb(NEW)-ARRAY['status','completed_at','completed_by','failure_code']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','completed_at','completed_by','failure_code']) OR
   OLD.status<>'PENDING' OR NEW.status NOT IN('PUBLISHED','STOPPED') OR NEW.completed_by IS DISTINCT FROM current_actor_id() OR
   r.status<>'PUBLISHING' OR h.version<>NEW.amendment_version+1 OR r.latest_publication_id IS DISTINCT FROM NEW.id OR
   r.latest_calculation_id IS DISTINCT FROM NEW.calculation_id OR r.latest_review_id IS DISTINCT FROM NEW.review_id
   THEN RAISE EXCEPTION 'Publication basis is immutable and requires its matching pending amendment' USING ERRCODE='23514'; END IF;
  IF NEW.status='PUBLISHED' THEN
   IF j.status<>'RUNNING' OR j.cancellation_requested OR j.lease_until<=clock_timestamp() OR NEW.completed_by<>NEW.actor_id
    THEN RAISE EXCEPTION 'Publication requires its active uncancelled lease' USING ERRCODE='23514'; END IF;
  ELSIF NOT ((j.status='RUNNING' AND j.lease_until>clock_timestamp() AND NEW.completed_by=NEW.actor_id) OR
             (j.status IN('FAILED','CANCELLED') AND j.completed_items=0 AND j.lease_token IS NULL)) THEN
   RAISE EXCEPTION 'Publication cleanup requires its lease or an observed terminal job' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_amendment_publication_protected BEFORE INSERT OR UPDATE OR DELETE ON payroll_amendment_publications
 FOR EACH ROW EXECUTE FUNCTION protect_payroll_amendment_publication();

CREATE OR REPLACE FUNCTION protect_payroll_amendment() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE assessment payroll_assessments; target payroll_run_targets; previous payroll_amendment_revisions; BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Amendment history is retained' USING ERRCODE='23514'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('payroll:'||NEW.company_id::text,0));
 IF TG_OP='INSERT' THEN
  SELECT * INTO assessment FROM payroll_assessments WHERE company_id=NEW.company_id AND id=NEW.first_assessment_id;
  SELECT * INTO target FROM payroll_run_targets WHERE company_id=NEW.company_id AND run_id=assessment.run_id AND ordinal=assessment.ordinal;
  IF NEW.version<>0 OR NEW.created_by IS DISTINCT FROM current_actor_id() OR assessment.id IS NULL OR
   (NEW.first_month,NEW.person_id,NEW.employment_id) IS DISTINCT FROM (assessment.tax_month,assessment.person_id,assessment.employment_id) OR
   (NEW.employee_number,NEW.employee_name,NEW.employment_version) IS DISTINCT FROM (target.employee_number,target.employee_name,target.employment_version)
   THEN RAISE EXCEPTION 'Amendment requires its exact published assessment' USING ERRCODE='23514'; END IF;
 ELSE
  SELECT * INTO previous FROM payroll_amendment_revisions WHERE company_id=OLD.company_id AND amendment_id=OLD.id AND revision=OLD.version;
  IF previous.revision IS NULL OR previous.status IN('CANCELLED','PUBLISHED') OR NEW.version<>OLD.version+1 OR
    (to_jsonb(NEW)-'version') IS DISTINCT FROM (to_jsonb(OLD)-'version')
   THEN RAISE EXCEPTION 'Amendment identity and revision order are protected' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION validate_payroll_amendment_revision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE h payroll_amendments; previous payroll_amendment_revisions; v payroll_amendment_reviews; change payroll_amendment_review_changes; publication payroll_amendment_publications; BEGIN
 SELECT * INTO h FROM payroll_amendments WHERE company_id=NEW.company_id AND id=NEW.amendment_id;
 IF h.id IS NULL OR h.version<>NEW.revision OR NEW.actor_id IS DISTINCT FROM current_actor_id()
  THEN RAISE EXCEPTION 'Amendment revision must match its current header and actor' USING ERRCODE='23514'; END IF;
 IF NEW.revision=999 AND NEW.action<>'CANCELLED'
  THEN RAISE EXCEPTION 'The final amendment revision is reserved for cancellation' USING ERRCODE='23514'; END IF;
 IF NEW.revision=0 THEN
  IF NEW.action<>'SAVED' OR NEW.status<>'DRAFT' OR NEW.latest_calculation_id IS NOT NULL OR NEW.latest_review_id IS NOT NULL OR NEW.latest_publication_id IS NOT NULL OR
   NEW.actor_id<>h.created_by OR NEW.recorded_at<>h.created_at
   THEN RAISE EXCEPTION 'Amendment starts with its original draft' USING ERRCODE='23514'; END IF;
 ELSE
  SELECT * INTO previous FROM payroll_amendment_revisions WHERE company_id=NEW.company_id AND amendment_id=NEW.amendment_id AND revision=NEW.revision-1;
  IF previous.revision IS NULL OR previous.status IN('CANCELLED','PUBLISHED')
   THEN RAISE EXCEPTION 'Amendment requires its previous live revision' USING ERRCODE='23514'; END IF;
  IF NEW.action IN('PUBLICATION_STARTED','PUBLISHED','PUBLICATION_STOPPED') THEN
   SELECT * INTO publication FROM payroll_amendment_publications WHERE company_id=NEW.company_id AND amendment_id=h.id AND id=NEW.latest_publication_id;
   IF publication.id IS NULL OR NEW.terms<>previous.terms OR NEW.latest_calculation_id IS DISTINCT FROM previous.latest_calculation_id OR
    NEW.latest_calculation_id IS DISTINCT FROM publication.calculation_id OR NEW.latest_review_id IS DISTINCT FROM previous.latest_review_id OR
    NEW.latest_review_id IS DISTINCT FROM publication.review_id OR NEW.reason<>publication.reason OR
    NOT ((NEW.action='PUBLICATION_STARTED' AND previous.status='APPROVED' AND NEW.status='PUBLISHING' AND
          NEW.revision=publication.amendment_version+1 AND publication.status='PENDING' AND NEW.actor_id=publication.actor_id AND
          NEW.recorded_at=publication.requested_at AND NEW.latest_publication_id IS DISTINCT FROM previous.latest_publication_id) OR
         (NEW.action='PUBLISHED' AND previous.status='PUBLISHING' AND NEW.status='PUBLISHED' AND publication.status='PUBLISHED' AND
          NEW.revision=publication.amendment_version+2 AND NEW.latest_publication_id=previous.latest_publication_id AND
          NEW.actor_id=publication.completed_by AND NEW.recorded_at=publication.completed_at) OR
         (NEW.action='PUBLICATION_STOPPED' AND previous.status='PUBLISHING' AND NEW.status='APPROVED' AND publication.status='STOPPED' AND
          NEW.revision=publication.amendment_version+2 AND NEW.latest_publication_id=previous.latest_publication_id AND
          NEW.actor_id=publication.completed_by AND NEW.recorded_at=publication.completed_at))
    THEN RAISE EXCEPTION 'Amendment publication revision must match its retained attempt' USING ERRCODE='23514'; END IF;
  ELSIF NEW.latest_publication_id IS DISTINCT FROM previous.latest_publication_id THEN
   RAISE EXCEPTION 'Publication identity is retained outside its explicit transitions' USING ERRCODE='23514';
  ELSIF NEW.action IN('SUBMITTED','APPROVED','REJECTED','WITHDRAWN') THEN
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

CREATE OR REPLACE FUNCTION assert_payroll_amendment_review(p_company uuid,p_review uuid) RETURNS void LANGUAGE plpgsql SECURITY INVOKER AS $$
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
  (v.status IN('PENDING','APPROVED') AND (r.latest_review_id IS DISTINCT FROM v.id OR r.latest_calculation_id IS DISTINCT FROM v.calculation_id OR r.terms<>retained.terms)) OR
  (v.status='PENDING' AND (r.revision<>change.amendment_revision OR r.status<>'IN_REVIEW')) OR
  (v.status='APPROVED' AND (r.revision<change.amendment_revision OR r.status NOT IN('APPROVED','PUBLISHING','PUBLISHED') OR
   EXISTS(SELECT 1 FROM payroll_amendment_revisions successor WHERE successor.company_id=p_company AND successor.amendment_id=h.id
    AND successor.revision>change.amendment_revision AND successor.revision<=h.version AND
    (successor.action NOT IN('PUBLICATION_STARTED','PUBLISHED','PUBLICATION_STOPPED') OR successor.terms<>retained.terms OR
     successor.latest_calculation_id IS DISTINCT FROM v.calculation_id OR successor.latest_review_id IS DISTINCT FROM v.id)))) OR
  (v.status='PENDING' AND (a.status NOT IN('PENDING','BLOCKED') OR a.current_step<>v.version)) OR
  (v.status='APPROVED' AND (a.status<>'APPROVED' OR a.current_step<>v.version-1 OR v.version<>jsonb_array_length(a.stages))) OR
  (v.status='REJECTED' AND (a.status<>'REJECTED' OR a.current_step<>v.version-1)) OR
  (v.status='WITHDRAWN' AND a.status<>'CANCELLED')
  THEN RAISE EXCEPTION 'Amendment, review, approval and retained changes must commit together' USING ERRCODE='23514'; END IF;
END $$;

-- Every positive assessment revision identifies the specific successful attempt.
ALTER TABLE payroll_assessment_revisions ADD COLUMN amendment_publication_id uuid;
ALTER TABLE payroll_assessment_revisions ADD FOREIGN KEY(company_id,amendment_publication_id)
 REFERENCES payroll_amendment_publications(company_id,id);
ALTER TABLE payroll_assessment_revisions ADD CHECK((revision=0 AND amendment_publication_id IS NULL) OR (revision>0 AND amendment_publication_id IS NOT NULL));
CREATE INDEX payroll_assessment_publication ON payroll_assessment_revisions(company_id,amendment_publication_id) WHERE amendment_publication_id IS NOT NULL;

CREATE OR REPLACE FUNCTION validate_payroll_assessment_revision_set() RETURNS trigger LANGUAGE plpgsql
 SET enable_nestloop=off SET search_path=pg_catalog,public AS $$
DECLARE p payroll_amendment_publications; h payroll_amendments; r payroll_amendment_revisions; j background_jobs; o payroll_amendment_results; n bigint;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM new_assessment_revisions) THEN RETURN NULL; END IF;
 IF (SELECT count(*)>5000 OR count(DISTINCT company_id)<>1 FROM new_assessment_revisions)
  THEN RAISE EXCEPTION 'Assessment revisions require a bounded company publication' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT 1 FROM new_assessment_revisions WHERE revision<>0) THEN
  IF EXISTS(SELECT 1 FROM new_assessment_revisions n LEFT JOIN payroll_assessments a ON a.company_id=n.company_id AND a.id=n.assessment_id
   WHERE n.amendment_calculation_id IS NOT NULL OR n.amendment_publication_id IS NOT NULL OR a.id IS NULL OR n.published_at IS DISTINCT FROM a.published_at)
   THEN RAISE EXCEPTION 'Original assessment revision requires its exact publication' USING ERRCODE='23514'; END IF;
  RETURN NULL;
 END IF;
 SELECT count(*) INTO n FROM new_assessment_revisions;
 IF n>12 OR (SELECT count(DISTINCT amendment_publication_id)<>1 OR bool_or(revision=0 OR amendment_publication_id IS NULL) FROM new_assessment_revisions)
  THEN RAISE EXCEPTION 'An amendment publishes one complete bounded suffix' USING ERRCODE='23514'; END IF;
 SELECT published.* INTO p FROM payroll_amendment_publications published JOIN (SELECT company_id,amendment_publication_id FROM new_assessment_revisions LIMIT 1) first
  ON published.company_id=first.company_id AND published.id=first.amendment_publication_id;
 SELECT * INTO h FROM payroll_amendments WHERE company_id=p.company_id AND id=p.amendment_id;
 SELECT * INTO r FROM payroll_amendment_revisions WHERE company_id=h.company_id AND amendment_id=h.id AND revision=h.version;
 SELECT * INTO j FROM background_jobs WHERE company_id=p.company_id AND id=p.job_id;
 IF p.id IS NULL OR p.status<>'PUBLISHED' OR p.actor_id IS DISTINCT FROM current_actor_id() OR h.version<>p.amendment_version+1 OR
  r.status<>'PUBLISHING' OR r.latest_publication_id IS DISTINCT FROM p.id OR j.status<>'RUNNING' OR j.completed_items<>0 OR
  j.cancellation_requested OR j.lease_until IS NULL OR j.lease_until<=clock_timestamp() OR
  n<>(SELECT count(*) FROM payroll_amendment_results WHERE company_id=p.company_id AND calculation_id=p.calculation_id)
  THEN RAISE EXCEPTION 'Assessment revision requires its approved active publication' USING ERRCODE='23514'; END IF;
 PERFORM assert_payroll_amendment_basis(p.company_id,h.person_id,h.first_month,h.first_assessment_id,r.terms);
 IF EXISTS(SELECT 1 FROM new_assessment_revisions inserted
  LEFT JOIN payroll_amendment_results result ON result.company_id=inserted.company_id AND result.calculation_id=p.calculation_id AND result.assessment_id=inserted.assessment_id
  LEFT JOIN payroll_assessment_heads head ON head.company_id=inserted.company_id AND head.assessment_id=inserted.assessment_id
  WHERE inserted.amendment_calculation_id IS DISTINCT FROM p.calculation_id OR inserted.revision IS DISTINCT FROM result.assessment_revision+1 OR
   inserted.published_at IS DISTINCT FROM p.completed_at OR head.revision IS DISTINCT FROM result.assessment_revision)
  THEN RAISE EXCEPTION 'Amendment revisions require their exact retained results and observed heads' USING ERRCODE='23514'; END IF;
 FOR o IN SELECT * FROM payroll_amendment_results WHERE company_id=p.company_id AND calculation_id=p.calculation_id ORDER BY ordinal LOOP
  PERFORM assert_payroll_amendment_result_history(o);
 END LOOP;
 RETURN NULL;
END $$;

CREATE OR REPLACE FUNCTION protect_payroll_assessment_head() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v payroll_assessment_revisions; p payroll_amendment_publications; j background_jobs;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Assessment heads are retained' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.revision<>0 THEN RAISE EXCEPTION 'Assessment heads start at original revision' USING ERRCODE='23514'; END IF;
 ELSE
  IF (NEW.company_id,NEW.assessment_id) IS DISTINCT FROM (OLD.company_id,OLD.assessment_id) OR NEW.revision<>OLD.revision+1
   THEN RAISE EXCEPTION 'Assessment heads advance one retained revision' USING ERRCODE='23514'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended('payroll:'||NEW.company_id::text,0));
  PERFORM pg_advisory_xact_lock(hashtextextended('payroll-payments:'||NEW.company_id::text,0));
  SELECT * INTO v FROM payroll_assessment_revisions WHERE company_id=NEW.company_id AND assessment_id=NEW.assessment_id AND revision=NEW.revision;
  SELECT * INTO p FROM payroll_amendment_publications WHERE company_id=NEW.company_id AND id=v.amendment_publication_id;
  SELECT * INTO j FROM background_jobs WHERE company_id=NEW.company_id AND id=p.job_id;
  IF p.id IS NULL OR p.status<>'PUBLISHED' OR p.actor_id IS DISTINCT FROM current_actor_id() OR
   j.status<>'RUNNING' OR j.cancellation_requested OR j.lease_until IS NULL OR j.lease_until<=clock_timestamp() OR
   EXISTS(SELECT 1 FROM payroll_payment_items WHERE company_id=NEW.company_id AND assessment_id=NEW.assessment_id AND status IN('PREPARED','PENDING'))
   THEN RAISE EXCEPTION 'A revised assessment requires active publication and resolved payment instructions' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;

CREATE FUNCTION assert_payroll_amendment_publication(p_company uuid,p_id uuid) RETURNS void LANGUAGE plpgsql AS $$
DECLARE p payroll_amendment_publications; h payroll_amendments; admitted payroll_amendment_revisions; terminal payroll_amendment_revisions; j background_jobs; published_count bigint; expected_count bigint;
BEGIN
 SELECT * INTO p FROM payroll_amendment_publications WHERE company_id=p_company AND id=p_id;
 IF p.id IS NULL THEN RAISE EXCEPTION 'Amendment job requires its retained publication' USING ERRCODE='23514'; END IF;
 SELECT * INTO h FROM payroll_amendments WHERE company_id=p_company AND id=p.amendment_id;
 SELECT * INTO admitted FROM payroll_amendment_revisions WHERE company_id=p_company AND amendment_id=p.amendment_id AND revision=p.amendment_version+1;
 SELECT * INTO terminal FROM payroll_amendment_revisions WHERE company_id=p_company AND amendment_id=p.amendment_id AND revision=p.amendment_version+2;
 SELECT * INTO j FROM background_jobs WHERE company_id=p_company AND id=p.job_id;
 SELECT count(*) INTO published_count FROM payroll_assessment_revisions WHERE company_id=p_company AND amendment_publication_id=p_id;
 IF admitted.revision IS NULL OR admitted.action<>'PUBLICATION_STARTED' OR admitted.latest_publication_id IS DISTINCT FROM p_id OR
  j.id IS NULL OR j.request->>'publicationId' IS DISTINCT FROM p_id::text OR j.request->>'amendmentId' IS DISTINCT FROM p.amendment_id::text
  THEN RAISE EXCEPTION 'Publication requires its matching admission and owned job' USING ERRCODE='23514'; END IF;
 IF p.status='PENDING' THEN
  -- Exhausted leases may become terminal in the generic scheduler. An explicit
  -- recovery command clears the pending pointer under current operator authority.
  IF h.version<>admitted.revision OR j.status NOT IN('QUEUED','RUNNING','FAILED','CANCELLED') OR j.completed_items<>0 OR published_count<>0
   THEN RAISE EXCEPTION 'Pending amendment cannot expose a partial publication' USING ERRCODE='23514'; END IF;
 ELSE
  IF terminal.revision IS NULL OR terminal.latest_publication_id IS DISTINCT FROM p.id OR terminal.actor_id IS DISTINCT FROM p.completed_by OR
   terminal.recorded_at IS DISTINCT FROM p.completed_at
   THEN RAISE EXCEPTION 'Publication outcome requires its retained amendment revision' USING ERRCODE='23514'; END IF;
  IF p.status='STOPPED' THEN
   IF terminal.action<>'PUBLICATION_STOPPED' OR j.status NOT IN('FAILED','CANCELLED') OR j.completed_items<>0 OR published_count<>0 OR j.failure_code IS DISTINCT FROM p.failure_code
    THEN RAISE EXCEPTION 'Stopped publication must retain a failed or cancelled job without assessment changes' USING ERRCODE='23514'; END IF;
  ELSE
   SELECT assessment_count INTO expected_count FROM payroll_amendment_calculations WHERE company_id=p_company AND id=p.calculation_id;
   IF terminal.action<>'PUBLISHED' OR h.version<>terminal.revision OR j.status<>'SUCCEEDED' OR j.completed_items<>1 OR j.cancellation_requested OR
    expected_count NOT BETWEEN 1 AND 12 OR published_count<>expected_count OR EXISTS(
     SELECT 1 FROM payroll_amendment_results o LEFT JOIN payroll_assessment_revisions v
      ON v.company_id=o.company_id AND v.assessment_id=o.assessment_id AND v.revision=o.assessment_revision+1
     LEFT JOIN payroll_assessment_heads head ON head.company_id=o.company_id AND head.assessment_id=o.assessment_id
     WHERE o.company_id=p_company AND o.calculation_id=p.calculation_id AND
      (v.amendment_publication_id IS DISTINCT FROM p.id OR v.amendment_calculation_id IS DISTINCT FROM p.calculation_id OR
       v.published_at IS DISTINCT FROM p.completed_at OR head.revision IS NULL OR head.revision<o.assessment_revision+1))
    THEN RAISE EXCEPTION 'All amended assessments, heads, outcome and successful checkpoint must commit together' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
END $$;
CREATE FUNCTION check_payroll_amendment_publication() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE publication uuid;
BEGIN
 IF TG_TABLE_NAME='payroll_amendment_publications' THEN publication:=NEW.id;
 ELSIF TG_TABLE_NAME='background_jobs' THEN publication:=(NEW.request->>'publicationId')::uuid;
 ELSIF TG_TABLE_NAME='payroll_assessment_revisions' THEN publication:=NEW.amendment_publication_id;
 ELSE publication:=NEW.latest_publication_id; END IF;
 IF publication IS NOT NULL THEN PERFORM assert_payroll_amendment_publication(NEW.company_id,publication); END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payroll_amendment_publication_complete AFTER INSERT OR UPDATE ON payroll_amendment_publications
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_amendment_publication();
CREATE CONSTRAINT TRIGGER payroll_amendment_publication_revision_complete AFTER INSERT ON payroll_amendment_revisions
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN(NEW.latest_publication_id IS NOT NULL) EXECUTE FUNCTION check_payroll_amendment_publication();
CREATE CONSTRAINT TRIGGER payroll_amendment_assessments_complete AFTER INSERT ON payroll_assessment_revisions
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN(NEW.amendment_publication_id IS NOT NULL) EXECUTE FUNCTION check_payroll_amendment_publication();
CREATE CONSTRAINT TRIGGER payroll_amendment_job_created AFTER INSERT ON background_jobs
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN(NEW.kind='PAYROLL_AMENDMENT_PUBLISH') EXECUTE FUNCTION check_payroll_amendment_publication();
CREATE CONSTRAINT TRIGGER payroll_amendment_job_complete AFTER UPDATE ON background_jobs
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN(NEW.kind='PAYROLL_AMENDMENT_PUBLISH' AND
  (NEW.completed_items IS DISTINCT FROM OLD.completed_items OR
   (NEW.status IN('SUCCEEDED','FAILED','CANCELLED') AND NEW.status IS DISTINCT FROM OLD.status)))
 EXECUTE FUNCTION check_payroll_amendment_publication();

-- THR uniqueness follows current entitlement; original fields remain retained evidence.
CREATE TABLE payroll_assessment_holidays (
 company_id uuid NOT NULL,assessment_id uuid NOT NULL,revision bigint NOT NULL,person_id uuid NOT NULL,
 holiday_kind varchar(32),holiday_year integer,
 PRIMARY KEY(company_id,assessment_id),
 FOREIGN KEY(company_id,assessment_id,revision) REFERENCES payroll_assessment_revisions(company_id,assessment_id,revision),
 FOREIGN KEY(person_id) REFERENCES persons(id),
 CHECK((holiday_kind IS NULL)=(holiday_year IS NULL)),CHECK(holiday_year IS NULL OR holiday_year BETWEEN 2024 AND 2100),
 CONSTRAINT payroll_current_holiday_unique UNIQUE(company_id,person_id,holiday_year,holiday_kind) DEFERRABLE INITIALLY DEFERRED
);
INSERT INTO payroll_assessment_holidays(company_id,assessment_id,revision,person_id,holiday_kind,holiday_year)
 SELECT company_id,id,0,person_id,holiday_kind,holiday_year FROM payroll_assessments;
DROP INDEX payroll_assessed_holiday;
ALTER TABLE payroll_assessment_holidays ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_assessment_holidays FORCE ROW LEVEL SECURITY;
CREATE POLICY company_scope ON payroll_assessment_holidays USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE FUNCTION protect_payroll_assessment_holiday() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a payroll_assessments; current_revision bigint; facts jsonb; calculation jsonb; expected_kind text; expected_year integer;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Current holiday ownership is retained' USING ERRCODE='23514'; END IF;
 SELECT * INTO a FROM payroll_assessments WHERE company_id=NEW.company_id AND id=NEW.assessment_id;
 SELECT revision INTO current_revision FROM payroll_assessment_heads WHERE company_id=NEW.company_id AND assessment_id=NEW.assessment_id;
 IF a.id IS NULL OR NEW.person_id<>a.person_id OR NEW.revision IS DISTINCT FROM current_revision OR
  (TG_OP='UPDATE' AND ((NEW.company_id,NEW.assessment_id,NEW.person_id) IS DISTINCT FROM (OLD.company_id,OLD.assessment_id,OLD.person_id) OR NEW.revision<>OLD.revision+1))
  THEN RAISE EXCEPTION 'Holiday ownership requires the current retained assessment' USING ERRCODE='23514'; END IF;
 IF NEW.revision=0 THEN expected_kind:=a.holiday_kind;expected_year:=a.holiday_year;
 ELSE
  SELECT e.facts,e.calculation INTO facts,calculation FROM payroll_assessment_evidence e
   WHERE e.company_id=NEW.company_id AND e.assessment_id=NEW.assessment_id AND e.revision=NEW.revision;
  IF (calculation->'holiday'->>'amount')::numeric>0 THEN
   expected_kind:=facts->'input'->'holidayAllowance'->>'kind';
   expected_year:=extract(year FROM (calculation->'holiday'->>'holidayDate')::date)::integer;
  END IF;
 END IF;
 IF (NEW.holiday_kind,NEW.holiday_year) IS DISTINCT FROM (expected_kind,expected_year)
  THEN RAISE EXCEPTION 'Holiday projection must match the current calculation' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_assessment_holiday_protected BEFORE INSERT OR UPDATE OR DELETE ON payroll_assessment_holidays
 FOR EACH ROW EXECUTE FUNCTION protect_payroll_assessment_holiday();
CREATE FUNCTION capture_payroll_assessment_holidays() RETURNS trigger LANGUAGE plpgsql
 SET enable_nestloop=off SET search_path=pg_catalog,public AS $$
BEGIN
 IF TG_OP='INSERT' THEN
  INSERT INTO payroll_assessment_holidays(company_id,assessment_id,revision,person_id,holiday_kind,holiday_year)
   SELECT n.company_id,n.assessment_id,n.revision,a.person_id,a.holiday_kind,a.holiday_year
   FROM new_assessment_heads n JOIN payroll_assessments a ON a.company_id=n.company_id AND a.id=n.assessment_id;
 ELSE
  INSERT INTO payroll_assessment_holidays(company_id,assessment_id,revision,person_id,holiday_kind,holiday_year)
   SELECT n.company_id,n.assessment_id,n.revision,a.person_id,
    CASE WHEN (e.calculation->'holiday'->>'amount')::numeric>0 THEN e.facts->'input'->'holidayAllowance'->>'kind' END,
    CASE WHEN (e.calculation->'holiday'->>'amount')::numeric>0 THEN extract(year FROM (e.calculation->'holiday'->>'holidayDate')::date)::integer END
   FROM new_assessment_heads n JOIN payroll_assessments a ON a.company_id=n.company_id AND a.id=n.assessment_id
    JOIN payroll_assessment_evidence e ON e.company_id=n.company_id AND e.assessment_id=n.assessment_id AND e.revision=n.revision
   ON CONFLICT(company_id,assessment_id) DO UPDATE SET revision=EXCLUDED.revision,holiday_kind=EXCLUDED.holiday_kind,holiday_year=EXCLUDED.holiday_year;
 END IF;
 RETURN NULL;
END $$;
CREATE TRIGGER payroll_assessment_holiday_created AFTER INSERT ON payroll_assessment_heads REFERENCING NEW TABLE AS new_assessment_heads
 FOR EACH STATEMENT EXECUTE FUNCTION capture_payroll_assessment_holidays();
CREATE TRIGGER payroll_assessment_holiday_changed AFTER UPDATE ON payroll_assessment_heads REFERENCING NEW TABLE AS new_assessment_heads
 FOR EACH STATEMENT EXECUTE FUNCTION capture_payroll_assessment_holidays();

CREATE OR REPLACE FUNCTION payroll_publication_readiness(p_company uuid,p_run uuid)
RETURNS TABLE(changed integer,duplicates integer,assessed integer,holidays integer)
LANGUAGE sql SECURITY INVOKER SET enable_nestloop=off SET search_path=pg_catalog,public AS $query$
WITH run_scope AS MATERIALIZED (
  SELECT id,company_id,earnings_month FROM payroll_runs WHERE company_id=p_company AND id=p_run LIMIT 1
), target_scope AS MATERIALIZED (
  SELECT t.ordinal,t.employment_id,t.employment_version,t.previous_assessment_id,t.previous_assessment_revision
  FROM payroll_run_targets t JOIN run_scope r ON r.company_id=t.company_id AND r.id=t.run_id
  ORDER BY t.ordinal LIMIT 5000
), employee_scope AS MATERIALIZED (
  SELECT e.id,e.person_id,e.version FROM employments e
  WHERE e.company_id=(SELECT company_id FROM run_scope) AND e.id=ANY(ARRAY(SELECT employment_id FROM target_scope))
), outcome_scope AS MATERIALIZED (
  SELECT o.ordinal,(o.calculation->'holiday'->>'amount')::numeric holiday_amount,
    extract(year FROM (o.calculation->'holiday'->>'holidayDate')::date)::integer holiday_year,
    o.facts->'input'->'holidayAllowance'->>'kind' holiday_kind
  FROM payroll_run_results o JOIN run_scope r ON r.company_id=o.company_id AND r.id=o.run_id
  ORDER BY o.ordinal LIMIT 5000
), previous_scope AS MATERIALIZED (
  SELECT DISTINCT ON(a.person_id) a.person_id,a.id,h.revision FROM payroll_assessments a
    JOIN payroll_assessment_heads h ON h.company_id=a.company_id AND h.assessment_id=a.id
  WHERE a.company_id=(SELECT company_id FROM run_scope)
    AND a.person_id=ANY(ARRAY(SELECT person_id FROM employee_scope))
    AND a.tax_month>=date_trunc('year',(SELECT earnings_month FROM run_scope))::date
    AND a.tax_month<(date_trunc('year',(SELECT earnings_month FROM run_scope))+interval '1 year')::date
  ORDER BY a.person_id,a.tax_month DESC
)
SELECT count(*) FILTER(WHERE e.version<>t.employment_version)::integer changed,
  (count(*)-count(DISTINCT e.person_id))::integer duplicates,
  count(*) FILTER(WHERE t.previous_assessment_id IS DISTINCT FROM a.id OR t.previous_assessment_revision IS DISTINCT FROM coalesce(a.revision,0))::integer assessed,
  count(*) FILTER(WHERE o.holiday_amount>0 AND EXISTS(SELECT 1 FROM payroll_assessment_holidays h
    WHERE h.company_id=r.company_id AND h.person_id=e.person_id AND h.holiday_year=o.holiday_year
      AND h.holiday_kind=o.holiday_kind))::integer holidays
FROM run_scope r CROSS JOIN target_scope t
  JOIN employee_scope e ON e.id=t.employment_id
  JOIN outcome_scope o ON o.ordinal=t.ordinal
  LEFT JOIN previous_scope a ON a.person_id=e.person_id;
$query$;
