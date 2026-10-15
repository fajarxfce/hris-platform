-- Preserve original assessments and retained calculations while making corrected
-- tax continuity explicit. Historical rows are not rewritten or republished.
ALTER TABLE payroll_amendment_results DROP CONSTRAINT payroll_amendment_results_assessment_revision_check;
ALTER TABLE payroll_amendment_results ADD CHECK(assessment_revision>=0 AND assessment_revision<9223372036854775806);
ALTER TABLE payroll_amendment_results ADD COLUMN previous_assessment_revision bigint NOT NULL DEFAULT 0
 CHECK(previous_assessment_revision>=0 AND previous_assessment_revision<9223372036854775807);
ALTER TABLE payroll_amendment_results ADD CONSTRAINT payroll_amendment_result_source_revision
 FOREIGN KEY(company_id,assessment_id,assessment_revision) REFERENCES payroll_assessment_revisions(company_id,assessment_id,revision);

-- The caller must select an explicit retained revision (or join its current head).
-- SECURITY INVOKER keeps all underlying company policies active for runtime roles.
CREATE VIEW payroll_assessment_evidence WITH (security_invoker=true) AS
 SELECT a.company_id,a.id assessment_id,v.revision,v.published_at,
  CASE WHEN v.revision=0 THEN original.facts ELSE corrected.facts END facts,
  CASE WHEN v.revision=0 THEN original.calculation ELSE corrected.calculation END calculation,
  CASE WHEN v.revision=0 THEN t.previous_assessment_revision ELSE corrected.previous_assessment_revision END previous_assessment_revision
 FROM payroll_assessments a
  JOIN payroll_assessment_revisions v ON v.company_id=a.company_id AND v.assessment_id=a.id
  JOIN payroll_run_targets t ON t.company_id=a.company_id AND t.run_id=a.run_id AND t.ordinal=a.ordinal
  JOIN payroll_run_results original ON original.company_id=a.company_id AND original.run_id=a.run_id AND original.ordinal=a.ordinal
  LEFT JOIN payroll_amendment_results corrected ON corrected.company_id=v.company_id
   AND corrected.calculation_id=v.amendment_calculation_id AND corrected.assessment_id=v.assessment_id;

CREATE OR REPLACE FUNCTION assert_payroll_amendment_basis(p_company uuid,p_person uuid,p_first date,p_first_id uuid,p_terms jsonb)
 RETURNS void LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE sources jsonb:=p_terms->'sourceVersions'; corrections jsonb:=p_terms->'corrections'; item jsonb; assessment payroll_assessments;
 source_count integer; source_calc payroll_assessment_evidence; BEGIN
 IF jsonb_typeof(sources) IS DISTINCT FROM 'array' OR jsonb_typeof(corrections) IS DISTINCT FROM 'array'
  THEN RAISE EXCEPTION 'Amendment references and corrections must be arrays' USING ERRCODE='23514'; END IF;
 IF jsonb_array_length(sources) NOT BETWEEN 1 AND 12 OR jsonb_array_length(corrections) NOT BETWEEN 1 AND 12 OR
  (SELECT count(DISTINCT value->>'assessmentId') FROM jsonb_array_elements(sources))<>jsonb_array_length(sources) OR
  (SELECT count(DISTINCT value->>'assessmentId') FROM jsonb_array_elements(corrections))<>jsonb_array_length(corrections) OR
  NOT EXISTS(SELECT 1 FROM jsonb_array_elements(corrections) WHERE value->>'assessmentId'=p_first_id::text)
  THEN RAISE EXCEPTION 'Amendment references must be distinct and include the first correction' USING ERRCODE='23514'; END IF;
 SELECT count(*) INTO source_count FROM payroll_assessments WHERE company_id=p_company AND person_id=p_person
  AND tax_month>=date_trunc('year',p_first)::date AND tax_month<(date_trunc('year',p_first)+interval '1 year')::date;
 IF source_count<>jsonb_array_length(sources)
  THEN RAISE EXCEPTION 'Amendment requires its complete observed year' USING ERRCODE='23514'; END IF;
 FOR item IN SELECT value FROM jsonb_array_elements(sources) LOOP
  SELECT * INTO assessment FROM payroll_assessments WHERE company_id=p_company AND id=(item->>'assessmentId')::uuid;
  IF assessment.id IS NULL OR assessment.person_id<>p_person OR extract(year FROM assessment.tax_month)<>extract(year FROM p_first) OR
    (item->>'revision')::bigint IS DISTINCT FROM (SELECT revision FROM payroll_assessment_heads WHERE company_id=p_company AND assessment_id=assessment.id)
   THEN RAISE EXCEPTION 'Amendment source scope or revision does not match' USING ERRCODE='23514'; END IF;
 END LOOP;
 FOR item IN SELECT value FROM jsonb_array_elements(corrections) LOOP
  SELECT * INTO assessment FROM payroll_assessments WHERE company_id=p_company AND id=(item->>'assessmentId')::uuid;
  IF assessment.id IS NULL OR assessment.person_id<>p_person OR assessment.tax_month<p_first OR
    NOT EXISTS(SELECT 1 FROM jsonb_array_elements(sources) WHERE value->>'assessmentId'=assessment.id::text) OR
    (nullif(item->'compensation','null') IS NULL AND nullif(item->'input','null') IS NULL) OR
    (nullif(item->'compensation','null') IS NOT NULL AND jsonb_typeof(item->'compensation')<>'object') OR
    (nullif(item->'input','null') IS NOT NULL AND jsonb_typeof(item->'input')<>'object')
   THEN RAISE EXCEPTION 'Amendment corrections must belong to the affected suffix' USING ERRCODE='23514'; END IF;
  IF nullif(item->'compensation','null') IS NOT NULL THEN
   SELECT e.* INTO source_calc FROM payroll_assessment_evidence e
    JOIN payroll_assessment_heads h ON h.company_id=e.company_id AND h.assessment_id=e.assessment_id AND h.revision=e.revision
    WHERE e.company_id=p_company AND e.assessment_id=assessment.id;
   IF item->'compensation'->'tax' IS DISTINCT FROM source_calc.facts->'compensation'->'tax'
    THEN RAISE EXCEPTION 'Remuneration cannot replace verified tax evidence' USING ERRCODE='23514'; END IF;
  END IF;
 END LOOP;
END $$;

-- Validate the amounts and exact predecessor revision that this calculation will
-- publish. Retained legacy calculations are not rewritten by this migration.
CREATE FUNCTION assert_payroll_amendment_result_history(p_result payroll_amendment_results)
 RETURNS void LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE attempt payroll_amendment_calculations; amendment payroll_amendments; draft payroll_amendment_revisions;
 assessment payroll_assessments; t payroll_run_targets; previous payroll_assessments; previous_target payroll_run_targets;
 opening payroll_tax_opening_revisions; corrected payroll_amendment_results; retained payroll_assessment_evidence;
 basis_facts jsonb; basis_calculation jsonb; expected_revision bigint; h jsonb; prior_input jsonb; prior_result jsonb;
BEGIN
 SELECT * INTO attempt FROM payroll_amendment_calculations WHERE company_id=p_result.company_id AND id=p_result.calculation_id;
 SELECT * INTO amendment FROM payroll_amendments WHERE company_id=p_result.company_id AND id=attempt.amendment_id;
 SELECT * INTO draft FROM payroll_amendment_revisions WHERE company_id=p_result.company_id AND amendment_id=attempt.amendment_id AND revision=attempt.draft_revision;
 SELECT * INTO assessment FROM payroll_assessments WHERE company_id=p_result.company_id AND id=p_result.assessment_id;
 SELECT * INTO t FROM payroll_run_targets WHERE company_id=p_result.company_id AND run_id=assessment.run_id AND ordinal=assessment.ordinal;
 h:=p_result.facts->'taxHistory';
 IF attempt.id IS NULL OR amendment.id IS NULL OR draft.revision IS NULL OR assessment.id IS NULL OR t.ordinal IS NULL OR
  p_result.calculation->'taxInput'->'history' IS DISTINCT FROM h->'history' OR
  p_result.calculation->'taxInput'->>'residency' IS DISTINCT FROM h->>'residency' OR
  p_result.calculation->'taxInput'->>'ptkp' IS DISTINCT FROM h->>'ptkp'
  THEN RAISE EXCEPTION 'Amended tax history requires its retained calculation and source' USING ERRCODE='23514'; END IF;
 IF t.previous_assessment_id IS NULL THEN
  SELECT * INTO opening FROM payroll_tax_opening_revisions WHERE company_id=t.company_id AND opening_id=t.tax_opening_id AND revision=t.tax_opening_revision;
  IF opening.revision IS NULL OR p_result.previous_assessment_revision<>0 OR h IS DISTINCT FROM opening.terms
   THEN RAISE EXCEPTION 'Amended initial history requires its verified opening' USING ERRCODE='23514'; END IF;
 ELSE
  SELECT * INTO previous FROM payroll_assessments WHERE company_id=t.company_id AND id=t.previous_assessment_id;
  SELECT * INTO previous_target FROM payroll_run_targets WHERE company_id=t.company_id AND run_id=previous.run_id AND ordinal=previous.ordinal;
  IF previous.tax_month>=amendment.first_month THEN
   SELECT * INTO corrected FROM payroll_amendment_results WHERE company_id=p_result.company_id
    AND calculation_id=p_result.calculation_id AND assessment_id=previous.id AND ordinal=p_result.ordinal-1;
   basis_facts:=corrected.facts;basis_calculation:=corrected.calculation;expected_revision:=corrected.assessment_revision+1;
  ELSE
   SELECT (value->>'revision')::bigint INTO expected_revision FROM jsonb_array_elements(draft.terms->'sourceVersions')
    WHERE value->>'assessmentId'=previous.id::text;
   SELECT * INTO retained FROM payroll_assessment_evidence WHERE company_id=t.company_id AND assessment_id=previous.id AND revision=expected_revision;
   basis_facts:=retained.facts;basis_calculation:=retained.calculation;
  END IF;
  prior_input:=basis_calculation->'taxInput';prior_result:=basis_calculation->'tax';
  IF previous.id IS NULL OR basis_facts IS NULL OR basis_calculation IS NULL OR expected_revision IS NULL OR
   p_result.previous_assessment_revision<>expected_revision OR previous.employment_id<>t.employment_id OR
   previous.tax_month<>(assessment.tax_month-interval '1 month')::date OR extract(year FROM previous.tax_month)<>extract(year FROM assessment.tax_month) OR
   previous_target.tax_opening_id IS DISTINCT FROM t.tax_opening_id OR previous_target.tax_opening_revision IS DISTINCT FROM t.tax_opening_revision OR
   (prior_input->>'finalPeriod')::boolean IS DISTINCT FROM false OR
   ((p_result.facts->'compensation'->'tax')-ARRAY['verifiedOn','verificationReference','residenceCountry']) IS DISTINCT FROM
    ((basis_facts->'compensation'->'tax')-ARRAY['verifiedOn','verificationReference','residenceCountry']) OR
   h->>'reference' IS DISTINCT FROM ('assessment:'||previous.id::text||CASE WHEN expected_revision=0 THEN '' ELSE ':'||expected_revision::text END) OR
   (h->>'throughMonth')::integer IS DISTINCT FROM extract(month FROM previous.tax_month)::integer OR
   h->>'residency' IS DISTINCT FROM prior_input->>'residency' OR h->>'ptkp' IS DISTINCT FROM prior_input->>'ptkp'
   THEN RAISE EXCEPTION 'Amended tax history must name its exact predecessor revision' USING ERRCODE='23514'; END IF;
  IF (h->'history'->>'taxableGross')::numeric IS DISTINCT FROM (prior_input->'history'->>'taxableGross')::numeric+(prior_result->>'taxableGross')::numeric OR
   (h->'history'->>'retirementContributions')::numeric IS DISTINCT FROM (prior_input->'history'->>'retirementContributions')::numeric+(prior_input->>'retirementContributions')::numeric OR
   (h->'history'->>'qualifiedDonations')::numeric IS DISTINCT FROM (prior_input->'history'->>'qualifiedDonations')::numeric+(prior_input->>'qualifiedDonations')::numeric OR
   (h->'history'->>'withheld')::numeric IS DISTINCT FROM (prior_input->'history'->>'withheld')::numeric+(prior_result->>'withheld')::numeric OR
   (h->'history'->>'employmentMonths')::integer IS DISTINCT FROM (prior_input->'history'->>'employmentMonths')::integer+1 OR
   (h->'history'->>'previousEmployerNet')::numeric IS DISTINCT FROM (prior_input->'history'->>'previousEmployerNet')::numeric OR
   (h->'history'->>'previousEmployerWithheld')::numeric IS DISTINCT FROM (prior_input->'history'->>'previousEmployerWithheld')::numeric
   THEN RAISE EXCEPTION 'Amended tax totals must retain the exact predecessor amounts and credits' USING ERRCODE='23514'; END IF;
 END IF;
END $$;

CREATE OR REPLACE FUNCTION validate_payroll_amendment_result() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE attempt payroll_amendment_calculations; amendment payroll_amendments; draft payroll_amendment_revisions;
 assessment payroll_assessments; original_result payroll_assessment_evidence; correction jsonb; BEGIN
 SELECT * INTO attempt FROM payroll_amendment_calculations WHERE company_id=NEW.company_id AND id=NEW.calculation_id;
 SELECT * INTO amendment FROM payroll_amendments WHERE company_id=NEW.company_id AND id=attempt.amendment_id;
 SELECT * INTO draft FROM payroll_amendment_revisions WHERE company_id=NEW.company_id AND amendment_id=attempt.amendment_id AND revision=attempt.draft_revision;
 SELECT * INTO assessment FROM payroll_assessments WHERE company_id=NEW.company_id AND id=NEW.assessment_id;
 SELECT * INTO original_result FROM payroll_assessment_evidence WHERE company_id=NEW.company_id
  AND assessment_id=assessment.id AND revision=NEW.assessment_revision;
 SELECT value INTO correction FROM jsonb_array_elements(draft.terms->'corrections') WHERE value->>'assessmentId'=NEW.assessment_id::text;
 IF attempt.status IS DISTINCT FROM 'SUCCEEDED' OR attempt.actor_id IS DISTINCT FROM current_actor_id() OR assessment.id IS NULL OR
  original_result.assessment_id IS NULL OR assessment.person_id IS DISTINCT FROM amendment.person_id OR assessment.tax_month<amendment.first_month OR
  NOT EXISTS(SELECT 1 FROM jsonb_array_elements(draft.terms->'sourceVersions') WHERE value->>'assessmentId'=NEW.assessment_id::text AND value->>'revision'=NEW.assessment_revision::text) OR
  NEW.original IS DISTINCT FROM original_result.calculation OR NEW.facts->>'month' IS DISTINCT FROM to_char(assessment.tax_month,'YYYY-MM') OR
  NEW.calculation->'taxInput'->>'month' IS DISTINCT FROM to_char(assessment.tax_month,'YYYY-MM') OR
  (NEW.facts-ARRAY['compensation','input','taxHistory']) IS DISTINCT FROM (original_result.facts-ARRAY['compensation','input','taxHistory']) OR
  NEW.facts->'compensation' IS DISTINCT FROM coalesce(nullif(correction->'compensation','null'),original_result.facts->'compensation') OR
  NEW.facts->'input' IS DISTINCT FROM coalesce(nullif(correction->'input','null'),original_result.facts->'input') OR
  (NEW.difference_taxable_gross,NEW.difference_withheld,NEW.difference_take_home,NEW.difference_employee_deductions,NEW.difference_employer_cost) IS DISTINCT FROM
   ((NEW.calculation->'tax'->>'taxableGross')::numeric-(NEW.original->'tax'->>'taxableGross')::numeric,
    (NEW.calculation->'tax'->>'withheld')::numeric-(NEW.original->'tax'->>'withheld')::numeric,
    (NEW.calculation->'tax'->>'takeHome')::numeric-(NEW.original->'tax'->>'takeHome')::numeric,
    (NEW.calculation->>'employeeDeductions')::numeric-(NEW.original->>'employeeDeductions')::numeric,
    (NEW.calculation->>'employerCost')::numeric-(NEW.original->>'employerCost')::numeric)
  THEN RAISE EXCEPTION 'Amendment result must preserve its source and exact calculated difference' USING ERRCODE='23514'; END IF;
 PERFORM assert_payroll_amendment_result_history(NEW);
 RETURN NEW;
END $$;
