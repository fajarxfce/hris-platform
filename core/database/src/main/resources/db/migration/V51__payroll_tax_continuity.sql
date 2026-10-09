-- Every subsequent same-year run captures an immutable person/month predecessor.
-- Existing initial assessments retain their original null predecessor.
ALTER TABLE payroll_run_targets ADD COLUMN previous_assessment_id uuid;
ALTER TABLE payroll_run_targets ADD FOREIGN KEY(company_id,previous_assessment_id) REFERENCES payroll_assessments(company_id,id);

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
 RETURN NEW;
END $$;

-- Bind the retained tax-history snapshot to its exact verified opening or immediately prior assessment.
-- This verifies immutable source arithmetic; the calculator still owns tax/earning rules.
CREATE FUNCTION validate_payroll_tax_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE t payroll_run_targets; r payroll_runs; opening payroll_tax_opening_revisions;
 a payroll_assessments; p payroll_run_targets; o payroll_run_results; h jsonb; prior_input jsonb; prior_result jsonb; BEGIN
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
  SELECT * INTO o FROM payroll_run_results WHERE company_id=a.company_id AND run_id=a.run_id AND ordinal=a.ordinal;
  prior_input:=o.calculation->'taxInput';prior_result:=o.calculation->'tax';
  IF a.id IS NULL OR a.employment_id<>t.employment_id OR a.tax_month<>(r.earnings_month-interval '1 month')::date OR
     extract(year FROM a.tax_month)<>extract(year FROM r.earnings_month) OR
     p.tax_opening_id IS DISTINCT FROM t.tax_opening_id OR p.tax_opening_revision IS DISTINCT FROM t.tax_opening_revision OR
     (prior_input->>'finalPeriod')::boolean IS DISTINCT FROM false OR
     ((NEW.facts->'compensation'->'tax')-ARRAY['verifiedOn','verificationReference','residenceCountry']) IS DISTINCT FROM
       ((o.facts->'compensation'->'tax')-ARRAY['verifiedOn','verificationReference','residenceCountry']) OR
     h->>'reference' IS DISTINCT FROM ('assessment:'||a.id::text) OR (h->>'throughMonth')::integer IS DISTINCT FROM extract(month FROM a.tax_month)::integer OR
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
CREATE TRIGGER payroll_result_tax_history BEFORE INSERT ON payroll_run_results FOR EACH ROW EXECUTE FUNCTION validate_payroll_tax_history();

CREATE OR REPLACE FUNCTION validate_payroll_assessments() RETURNS trigger LANGUAGE plpgsql AS $$
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
   LEFT JOIN LATERAL(SELECT t.employment_id,t.employment_version,t.previous_assessment_id FROM payroll_run_targets t
      WHERE t.company_id=n.company_id AND t.run_id=n.run_id AND t.ordinal=n.ordinal LIMIT 1) t ON true
   LEFT JOIN LATERAL(SELECT e.person_id,e.version FROM employments e
      WHERE e.company_id=n.company_id AND e.id=t.employment_id LIMIT 1) e ON true
   LEFT JOIN LATERAL(SELECT o.status,o.facts,o.calculation FROM payroll_run_results o
      WHERE o.company_id=n.company_id AND o.run_id=n.run_id AND o.ordinal=n.ordinal LIMIT 1) o ON true
   LEFT JOIN LATERAL(SELECT a.id FROM payroll_assessments a WHERE a.company_id=n.company_id AND a.person_id=e.person_id AND a.run_id<>n.run_id
      AND a.tax_month>=date_trunc('year',r.earnings_month)::date AND a.tax_month<(date_trunc('year',r.earnings_month)+interval '1 year')::date
      ORDER BY a.tax_month DESC LIMIT 1) previous ON true
  WHERE t.previous_assessment_id IS DISTINCT FROM previous.id OR n.run_id IS DISTINCT FROM f.run_id OR o.status IS DISTINCT FROM 'SUCCEEDED' OR
   n.employment_id IS DISTINCT FROM t.employment_id OR n.person_id IS DISTINCT FROM e.person_id OR e.version<>t.employment_version OR
   n.tax_month IS DISTINCT FROM r.earnings_month OR n.published_at<f.requested_at OR
   n.holiday_kind IS DISTINCT FROM CASE WHEN (o.calculation->'holiday'->>'amount')::numeric>0 THEN o.facts->'input'->'holidayAllowance'->>'kind' END OR
   n.holiday_year IS DISTINCT FROM CASE WHEN (o.calculation->'holiday'->>'amount')::numeric>0 THEN extract(year FROM (o.calculation->'holiday'->>'holidayDate')::date)::integer END)
  THEN RAISE EXCEPTION 'Assessment must reference its exact retained result and current employee' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
