-- Bound materialized publication inputs and use set joins even before auto-analyze has
-- statistics for a newly imported run. The setting is function-local and automatically
-- restored on success or failure; runtime privileges, RLS, and transaction budgets remain.
CREATE FUNCTION payroll_publication_readiness(p_company uuid,p_run uuid)
RETURNS TABLE(changed integer,duplicates integer,assessed integer,holidays integer)
LANGUAGE sql SECURITY INVOKER SET enable_nestloop=off SET search_path=pg_catalog,public AS $query$
WITH run_scope AS MATERIALIZED (
  SELECT id,company_id,earnings_month FROM payroll_runs WHERE company_id=p_company AND id=p_run LIMIT 1
), target_scope AS MATERIALIZED (
  SELECT t.ordinal,t.employment_id,t.employment_version,t.previous_assessment_id
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
  SELECT DISTINCT ON(a.person_id) a.person_id,a.id FROM payroll_assessments a
  WHERE a.company_id=(SELECT company_id FROM run_scope)
    AND a.person_id=ANY(ARRAY(SELECT person_id FROM employee_scope))
    AND a.tax_month>=date_trunc('year',(SELECT earnings_month FROM run_scope))::date
    AND a.tax_month<(date_trunc('year',(SELECT earnings_month FROM run_scope))+interval '1 year')::date
  ORDER BY a.person_id,a.tax_month DESC
)
SELECT count(*) FILTER(WHERE e.version<>t.employment_version)::integer changed,
  (count(*)-count(DISTINCT e.person_id))::integer duplicates,
  count(*) FILTER(WHERE t.previous_assessment_id IS DISTINCT FROM a.id)::integer assessed,
  count(*) FILTER(WHERE o.holiday_amount>0 AND EXISTS(SELECT 1 FROM payroll_assessments h
    WHERE h.company_id=r.company_id AND h.person_id=e.person_id AND h.holiday_year=o.holiday_year
      AND h.holiday_kind=o.holiday_kind))::integer holidays
FROM run_scope r CROSS JOIN target_scope t
  JOIN employee_scope e ON e.id=t.employment_id
  JOIN outcome_scope o ON o.ordinal=t.ordinal
  LEFT JOIN previous_scope a ON a.person_id=e.person_id;
$query$;

CREATE FUNCTION insert_payroll_assessments(p_company uuid,p_finalization uuid,p_at timestamptz)
RETURNS void LANGUAGE sql SECURITY INVOKER SET enable_nestloop=off SET search_path=pg_catalog,public AS $query$
WITH publication AS MATERIALIZED (
  SELECT f.company_id,f.id,f.run_id,r.earnings_month FROM payroll_finalizations f
    JOIN payroll_runs r ON r.company_id=f.company_id AND r.id=f.run_id
  WHERE f.company_id=p_company AND f.id=p_finalization LIMIT 1
), target_scope AS MATERIALIZED (
  SELECT t.ordinal,t.employment_id FROM payroll_run_targets t
    JOIN publication f ON f.company_id=t.company_id AND f.run_id=t.run_id
  ORDER BY t.ordinal LIMIT 5000
), employee_scope AS MATERIALIZED (
  SELECT e.id,e.person_id FROM employments e
  WHERE e.company_id=(SELECT company_id FROM publication) AND e.id=ANY(ARRAY(SELECT employment_id FROM target_scope))
), outcome_scope AS MATERIALIZED (
  SELECT o.ordinal,
    CASE WHEN (o.calculation->'holiday'->>'amount')::numeric>0 THEN o.facts->'input'->'holidayAllowance'->>'kind' END holiday_kind,
    CASE WHEN (o.calculation->'holiday'->>'amount')::numeric>0 THEN extract(year FROM (o.calculation->'holiday'->>'holidayDate')::date)::integer END holiday_year
  FROM payroll_run_results o JOIN publication f ON f.company_id=o.company_id AND f.run_id=o.run_id
  WHERE o.status='SUCCEEDED' ORDER BY o.ordinal LIMIT 5000
)
INSERT INTO payroll_assessments(company_id,id,finalization_id,run_id,ordinal,employment_id,person_id,tax_month,published_at,holiday_kind,holiday_year)
SELECT f.company_id,gen_random_uuid(),f.id,f.run_id,t.ordinal,t.employment_id,e.person_id,f.earnings_month,p_at,o.holiday_kind,o.holiday_year
FROM publication f CROSS JOIN target_scope t
  JOIN employee_scope e ON e.id=t.employment_id JOIN outcome_scope o ON o.ordinal=t.ordinal
ORDER BY t.ordinal;
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
    SELECT ordinal,employment_id,employment_version,previous_assessment_id FROM payroll_run_targets
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
    SELECT DISTINCT ON(person_id) person_id,id FROM payroll_assessments WHERE company_id=company_key AND run_id<>f.run_id
      AND person_id=ANY(ARRAY(SELECT person_id FROM new_assessments))
      AND tax_month>=date_trunc('year',r.earnings_month)::date
      AND tax_month<(date_trunc('year',r.earnings_month)+interval '1 year')::date
    ORDER BY person_id,tax_month DESC
   )
   SELECT 1 FROM new_assessments n
     LEFT JOIN target_scope t ON t.ordinal=n.ordinal
     LEFT JOIN employee_scope e ON e.id=t.employment_id
     LEFT JOIN outcome_scope o ON o.ordinal=n.ordinal
     LEFT JOIN previous_scope previous ON previous.person_id=e.person_id
  WHERE t.previous_assessment_id IS DISTINCT FROM previous.id OR n.run_id IS DISTINCT FROM f.run_id OR o.status IS DISTINCT FROM 'SUCCEEDED' OR
   n.employment_id IS DISTINCT FROM t.employment_id OR n.person_id IS DISTINCT FROM e.person_id OR e.version<>t.employment_version OR
   n.tax_month IS DISTINCT FROM r.earnings_month OR n.published_at<f.requested_at OR
   n.holiday_kind IS DISTINCT FROM o.holiday_kind OR
   n.holiday_year IS DISTINCT FROM o.holiday_year)
  THEN RAISE EXCEPTION 'Assessment must reference its exact retained result and current employee' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
