-- Ordinary payroll must retain both the assessment identity and the exact revision
-- used for tax continuity. Original payroll and bank evidence stay immutable.
-- Block target writers before assessment insertion while seeding every original.
LOCK TABLE payroll_run_targets IN ACCESS EXCLUSIVE MODE;
LOCK TABLE payroll_assessments IN SHARE ROW EXCLUSIVE MODE;

CREATE TABLE payroll_assessment_revisions (
 company_id uuid NOT NULL,assessment_id uuid NOT NULL,
 revision bigint NOT NULL CHECK(revision>=0 AND revision<9223372036854775807),
 amendment_calculation_id uuid,published_at timestamptz NOT NULL,
 PRIMARY KEY(company_id,assessment_id,revision),
 FOREIGN KEY(company_id,assessment_id) REFERENCES payroll_assessments(company_id,id),
 FOREIGN KEY(company_id,amendment_calculation_id,assessment_id)
  REFERENCES payroll_amendment_results(company_id,calculation_id,assessment_id),
 CHECK((revision=0 AND amendment_calculation_id IS NULL) OR (revision>0 AND amendment_calculation_id IS NOT NULL))
);
CREATE TABLE payroll_assessment_heads (
 company_id uuid NOT NULL,assessment_id uuid NOT NULL,revision bigint NOT NULL CHECK(revision>=0 AND revision<9223372036854775807),
 PRIMARY KEY(company_id,assessment_id),
 FOREIGN KEY(company_id,assessment_id) REFERENCES payroll_assessments(company_id,id),
 CONSTRAINT payroll_assessment_current_revision FOREIGN KEY(company_id,assessment_id,revision) REFERENCES payroll_assessment_revisions(company_id,assessment_id,revision)
  DEFERRABLE INITIALLY DEFERRED
);

INSERT INTO payroll_assessment_revisions(company_id,assessment_id,revision,published_at)
 SELECT company_id,id,0,published_at FROM payroll_assessments;
INSERT INTO payroll_assessment_heads(company_id,assessment_id,revision)
 SELECT company_id,id,0 FROM payroll_assessments;
-- Validate queued backfill references before later table DDL while retaining
-- the runtime's initially deferred foreign key and company RLS policy.
SET CONSTRAINTS payroll_assessment_current_revision IMMEDIATE;
SET CONSTRAINTS payroll_assessment_current_revision DEFERRED;
ALTER TABLE payroll_assessments ADD CONSTRAINT payroll_assessment_has_head FOREIGN KEY(company_id,id)
 REFERENCES payroll_assessment_heads(company_id,assessment_id) DEFERRABLE INITIALLY DEFERRED;

-- DEFAULT adds the historical revision without rewriting any retained target.
-- A target without a predecessor keeps the neutral zero value, never a made-up ID.
ALTER TABLE payroll_run_targets ADD COLUMN previous_assessment_revision bigint NOT NULL DEFAULT 0
 CHECK(previous_assessment_revision>=0 AND previous_assessment_revision<9223372036854775807
  AND (previous_assessment_id IS NOT NULL OR previous_assessment_revision=0));
ALTER TABLE payroll_run_targets ADD CONSTRAINT payroll_target_assessment_revision
 FOREIGN KEY(company_id,previous_assessment_id,previous_assessment_revision)
 REFERENCES payroll_assessment_revisions(company_id,assessment_id,revision);

CREATE FUNCTION validate_payroll_assessment_revision_set() RETURNS trigger LANGUAGE plpgsql
 SET enable_nestloop=off SET search_path=pg_catalog,public AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM new_assessment_revisions) THEN RETURN NULL; END IF;
 IF (SELECT count(*)>5000 OR count(DISTINCT company_id)<>1 FROM new_assessment_revisions)
  THEN RAISE EXCEPTION 'Assessment revisions require a bounded company publication' USING ERRCODE='23514'; END IF;
 -- Revised publication has its own independent approval and job transaction. Until
 -- that workflow is installed, no retained calculation can be promoted by a raw write.
 IF EXISTS(SELECT 1 FROM new_assessment_revisions n
    LEFT JOIN payroll_assessments a ON a.company_id=n.company_id AND a.id=n.assessment_id
    WHERE n.revision<>0 OR n.amendment_calculation_id IS NOT NULL OR a.id IS NULL
     OR n.published_at IS DISTINCT FROM a.published_at)
  THEN RAISE EXCEPTION 'Assessment revision requires its authorized publication' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE TRIGGER payroll_assessment_revision_valid AFTER INSERT ON payroll_assessment_revisions
 REFERENCING NEW TABLE AS new_assessment_revisions FOR EACH STATEMENT EXECUTE FUNCTION validate_payroll_assessment_revision_set();
CREATE TRIGGER payroll_assessment_revision_immutable BEFORE UPDATE OR DELETE ON payroll_assessment_revisions
 FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

CREATE FUNCTION protect_payroll_assessment_head() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Assessment heads are retained' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.revision<>0 THEN RAISE EXCEPTION 'Assessment heads start at original revision' USING ERRCODE='23514'; END IF;
 ELSE
  IF (NEW.company_id,NEW.assessment_id) IS DISTINCT FROM (OLD.company_id,OLD.assessment_id)
    OR NEW.revision<>OLD.revision+1
   THEN RAISE EXCEPTION 'Assessment heads advance one retained revision' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_assessment_head_protected BEFORE INSERT OR UPDATE OR DELETE ON payroll_assessment_heads
 FOR EACH ROW EXECUTE FUNCTION protect_payroll_assessment_head();

CREATE FUNCTION capture_original_payroll_assessment_revisions() RETURNS trigger LANGUAGE plpgsql
 SET enable_nestloop=off SET search_path=pg_catalog,public AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM new_assessments) THEN RETURN NULL; END IF;
 INSERT INTO payroll_assessment_revisions(company_id,assessment_id,revision,published_at)
  SELECT company_id,id,0,published_at FROM new_assessments;
 INSERT INTO payroll_assessment_heads(company_id,assessment_id,revision)
  SELECT company_id,id,0 FROM new_assessments;
 RETURN NULL;
END $$;
CREATE TRIGGER payroll_assessment_original_revision AFTER INSERT ON payroll_assessments
 REFERENCING NEW TABLE AS new_assessments FOR EACH STATEMENT EXECUTE FUNCTION capture_original_payroll_assessment_revisions();

DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['payroll_assessment_revisions','payroll_assessment_heads'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
 END LOOP;
END $$;


-- Capture and validate exact predecessors at admission, calculation and publication.
CREATE OR REPLACE FUNCTION validate_payroll_run_target() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r payroll_runs; BEGIN
 SELECT * INTO r FROM payroll_runs WHERE company_id=NEW.company_id AND id=NEW.run_id;
 IF r.id IS NULL OR r.creation_transaction<>pg_current_xact_id()::text OR r.version<>0 OR r.status<>'PROCESSING' OR NEW.ordinal>r.total_employees
  THEN RAISE EXCEPTION 'Payroll target must be inserted with its run' USING ERRCODE='23514'; END IF;
 IF NEW.employment_version IS DISTINCT FROM (SELECT version FROM employments WHERE company_id=NEW.company_id AND id=NEW.employment_id)
  THEN RAISE EXCEPTION 'Payroll target requires its current employment version' USING ERRCODE='23514'; END IF;
 IF NEW.compensation_revision IS DISTINCT FROM (SELECT revision FROM employee_compensation_revisions WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND effective_from<=r.earnings_month ORDER BY effective_from DESC,revision DESC LIMIT 1)
  THEN RAISE EXCEPTION 'Payroll target requires its effective compensation revision' USING ERRCODE='23514'; END IF;
 IF NEW.input_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM payroll_inputs WHERE company_id=NEW.company_id AND id=NEW.input_id AND employment_id=NEW.employment_id AND earnings_month=r.earnings_month AND version=NEW.input_revision)
  THEN RAISE EXCEPTION 'Payroll target requires its current monthly input' USING ERRCODE='23514'; END IF;
 IF NEW.tax_opening_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM payroll_tax_openings WHERE company_id=NEW.company_id AND id=NEW.tax_opening_id AND employment_id=NEW.employment_id AND tax_year=extract(year FROM r.earnings_month)::integer AND version=NEW.tax_opening_revision)
  THEN RAISE EXCEPTION 'Payroll target requires its current opening history' USING ERRCODE='23514'; END IF;
 IF NEW.previous_assessment_id IS DISTINCT FROM (
   SELECT a.id FROM payroll_assessments a
    WHERE a.company_id=NEW.company_id AND a.person_id=(SELECT person_id FROM employments WHERE company_id=NEW.company_id AND id=NEW.employment_id)
      AND a.tax_month>=date_trunc('year',r.earnings_month)::date AND a.tax_month<(date_trunc('year',r.earnings_month)+interval '1 year')::date
    ORDER BY a.tax_month DESC LIMIT 1)
  THEN RAISE EXCEPTION 'Payroll target requires its latest published tax reference' USING ERRCODE='23514'; END IF;
 IF NEW.previous_assessment_revision IS DISTINCT FROM coalesce((
   SELECT revision FROM payroll_assessment_heads WHERE company_id=NEW.company_id AND assessment_id=NEW.previous_assessment_id),0)
  THEN RAISE EXCEPTION 'Payroll target requires the current assessment revision' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION validate_payroll_tax_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE t payroll_run_targets; r payroll_runs; opening payroll_tax_opening_revisions;
 a payroll_assessments; p payroll_run_targets; basis_facts jsonb; basis_calculation jsonb; h jsonb; prior_input jsonb; prior_result jsonb; BEGIN
 IF NEW.status<>'SUCCEEDED' THEN RETURN NEW; END IF;
 SELECT * INTO t FROM payroll_run_targets WHERE company_id=NEW.company_id AND run_id=NEW.run_id AND ordinal=NEW.ordinal;
 SELECT * INTO r FROM payroll_runs WHERE company_id=NEW.company_id AND id=NEW.run_id;
 h:=NEW.facts->'taxHistory';
 IF t.previous_assessment_id IS NULL THEN
  SELECT * INTO opening FROM payroll_tax_opening_revisions WHERE company_id=t.company_id AND opening_id=t.tax_opening_id AND revision=t.tax_opening_revision;
  IF h IS DISTINCT FROM opening.terms THEN
   RAISE EXCEPTION 'Initial tax history must retain its verified opening terms' USING ERRCODE='23514'; END IF;
 ELSE
  SELECT * INTO a FROM payroll_assessments WHERE company_id=t.company_id AND id=t.previous_assessment_id;
  SELECT * INTO p FROM payroll_run_targets WHERE company_id=a.company_id AND run_id=a.run_id AND ordinal=a.ordinal;
  SELECT CASE WHEN v.revision=0 THEN o.facts ELSE corrected.facts END,
    CASE WHEN v.revision=0 THEN o.calculation ELSE corrected.calculation END INTO basis_facts,basis_calculation
   FROM payroll_assessment_revisions v
    JOIN payroll_run_results o ON o.company_id=a.company_id AND o.run_id=a.run_id AND o.ordinal=a.ordinal
    LEFT JOIN payroll_amendment_results corrected ON corrected.company_id=v.company_id
      AND corrected.calculation_id=v.amendment_calculation_id AND corrected.assessment_id=v.assessment_id
   WHERE v.company_id=a.company_id AND v.assessment_id=a.id AND v.revision=t.previous_assessment_revision;
  prior_input:=basis_calculation->'taxInput';prior_result:=basis_calculation->'tax';
  IF a.id IS NULL OR basis_facts IS NULL OR basis_calculation IS NULL OR a.employment_id<>t.employment_id OR a.tax_month<>(r.earnings_month-interval '1 month')::date OR
     extract(year FROM a.tax_month)<>extract(year FROM r.earnings_month) OR
     p.tax_opening_id IS DISTINCT FROM t.tax_opening_id OR p.tax_opening_revision IS DISTINCT FROM t.tax_opening_revision OR
     (prior_input->>'finalPeriod')::boolean IS DISTINCT FROM false OR
     ((NEW.facts->'compensation'->'tax')-ARRAY['verifiedOn','verificationReference','residenceCountry']) IS DISTINCT FROM
       ((basis_facts->'compensation'->'tax')-ARRAY['verifiedOn','verificationReference','residenceCountry']) OR
     h->>'reference' IS DISTINCT FROM ('assessment:'||a.id::text||CASE WHEN t.previous_assessment_revision=0 THEN '' ELSE ':'||t.previous_assessment_revision::text END) OR (h->>'throughMonth')::integer IS DISTINCT FROM extract(month FROM a.tax_month)::integer OR
     h->>'residency' IS DISTINCT FROM prior_input->>'residency' OR h->>'ptkp' IS DISTINCT FROM prior_input->>'ptkp'
   THEN RAISE EXCEPTION 'Tax continuity requires the same employment opening registration and previous month' USING ERRCODE='23514'; END IF;
  IF (h->'history'->>'taxableGross')::numeric IS DISTINCT FROM (prior_input->'history'->>'taxableGross')::numeric+(prior_result->>'taxableGross')::numeric OR
     (h->'history'->>'retirementContributions')::numeric IS DISTINCT FROM (prior_input->'history'->>'retirementContributions')::numeric+(prior_input->>'retirementContributions')::numeric OR
     (h->'history'->>'qualifiedDonations')::numeric IS DISTINCT FROM (prior_input->'history'->>'qualifiedDonations')::numeric+(prior_input->>'qualifiedDonations')::numeric OR
     (h->'history'->>'withheld')::numeric IS DISTINCT FROM (prior_input->'history'->>'withheld')::numeric+(prior_result->>'withheld')::numeric OR
     (h->'history'->>'employmentMonths')::integer IS DISTINCT FROM (prior_input->'history'->>'employmentMonths')::integer+1 OR
     (h->'history'->>'previousEmployerNet')::numeric IS DISTINCT FROM (prior_input->'history'->>'previousEmployerNet')::numeric OR
     (h->'history'->>'previousEmployerWithheld')::numeric IS DISTINCT FROM (prior_input->'history'->>'previousEmployerWithheld')::numeric
   THEN RAISE EXCEPTION 'Tax continuity must retain its exact assessed totals and prior employer credits' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;

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
  count(*) FILTER(WHERE o.holiday_amount>0 AND EXISTS(SELECT 1 FROM payroll_assessments h
    WHERE h.company_id=r.company_id AND h.person_id=e.person_id AND h.holiday_year=o.holiday_year
      AND h.holiday_kind=o.holiday_kind))::integer holidays
FROM run_scope r CROSS JOIN target_scope t
  JOIN employee_scope e ON e.id=t.employment_id
  JOIN outcome_scope o ON o.ordinal=t.ordinal
  LEFT JOIN previous_scope a ON a.person_id=e.person_id;
$query$;

CREATE OR REPLACE FUNCTION validate_payroll_assessments() RETURNS trigger LANGUAGE plpgsql SET enable_nestloop=off SET search_path=pg_catalog,public AS $$
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
 -- Capture each bounded run scope once. RLS and cold statistics can otherwise select a
 -- company-prefix index for each employee/ordinal and repeatedly scan the entire run.
 IF EXISTS(WITH target_scope AS MATERIALIZED (
    SELECT ordinal,employment_id,employment_version,previous_assessment_id,previous_assessment_revision FROM payroll_run_targets
    WHERE company_id=company_key AND run_id=f.run_id ORDER BY ordinal LIMIT 5000
   ), employee_scope AS MATERIALIZED (
    SELECT id,person_id,version FROM employments WHERE company_id=company_key
      AND id=ANY(ARRAY(SELECT employment_id FROM target_scope))
   ), outcome_scope AS MATERIALIZED (
    SELECT ordinal,status,
      CASE WHEN (calculation->'holiday'->>'amount')::numeric>0 THEN facts->'input'->'holidayAllowance'->>'kind' END holiday_kind,
      CASE WHEN (calculation->'holiday'->>'amount')::numeric>0 THEN extract(year FROM (calculation->'holiday'->>'holidayDate')::date)::integer END holiday_year
    FROM payroll_run_results WHERE company_id=company_key AND run_id=f.run_id ORDER BY ordinal LIMIT 5000
   ), previous_scope AS MATERIALIZED (
    SELECT DISTINCT ON(a.person_id) a.person_id,a.id,h.revision FROM payroll_assessments a
      JOIN payroll_assessment_heads h ON h.company_id=a.company_id AND h.assessment_id=a.id
    WHERE a.company_id=company_key AND a.run_id<>f.run_id
      AND a.person_id=ANY(ARRAY(SELECT person_id FROM new_assessments))
      AND a.tax_month>=date_trunc('year',r.earnings_month)::date
      AND a.tax_month<(date_trunc('year',r.earnings_month)+interval '1 year')::date
    ORDER BY a.person_id,a.tax_month DESC
   )
   SELECT 1 FROM new_assessments n
     LEFT JOIN target_scope t ON t.ordinal=n.ordinal
     LEFT JOIN employee_scope e ON e.id=t.employment_id
     LEFT JOIN outcome_scope o ON o.ordinal=n.ordinal
     LEFT JOIN previous_scope previous ON previous.person_id=e.person_id
  WHERE t.previous_assessment_id IS DISTINCT FROM previous.id OR t.previous_assessment_revision IS DISTINCT FROM coalesce(previous.revision,0) OR n.run_id IS DISTINCT FROM f.run_id OR o.status IS DISTINCT FROM 'SUCCEEDED' OR
   n.employment_id IS DISTINCT FROM t.employment_id OR n.person_id IS DISTINCT FROM e.person_id OR e.version<>t.employment_version OR
   n.tax_month IS DISTINCT FROM r.earnings_month OR n.published_at<f.requested_at OR
   n.holiday_kind IS DISTINCT FROM o.holiday_kind OR
   n.holiday_year IS DISTINCT FROM o.holiday_year)
  THEN RAISE EXCEPTION 'Assessment must reference its exact retained result and current employee' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
