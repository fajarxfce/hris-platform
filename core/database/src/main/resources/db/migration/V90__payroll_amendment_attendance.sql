-- Attendance corrections retain their Workforce identity through amendment review
-- and revised publication. Existing proposals and published facts are unchanged.

CREATE OR REPLACE FUNCTION assert_payroll_amendment_basis(p_company uuid,p_person uuid,p_first date,p_first_id uuid,p_terms jsonb)
 RETURNS void LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE sources jsonb:=p_terms->'sourceVersions'; corrections jsonb:=p_terms->'corrections'; item jsonb; assessment payroll_assessments;
 source_count integer; source_calc payroll_assessment_evidence; selected_work attendance_adjustments; BEGIN
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
    (nullif(item->'compensation','null') IS NULL AND nullif(item->'input','null') IS NULL AND item->>'workAdjustmentId' IS NULL) OR
    (nullif(item->'compensation','null') IS NOT NULL AND jsonb_typeof(item->'compensation')<>'object') OR
    (nullif(item->'input','null') IS NOT NULL AND jsonb_typeof(item->'input')<>'object')
   THEN RAISE EXCEPTION 'Amendment corrections must belong to the affected suffix' USING ERRCODE='23514'; END IF;
  IF item->>'workAdjustmentId' IS NOT NULL THEN
   SELECT w.* INTO selected_work FROM attendance_adjustments w
    JOIN payroll_runs r ON r.company_id=w.company_id AND r.work_job_id=w.job_id
    WHERE w.company_id=p_company AND w.id=(item->>'workAdjustmentId')::uuid
     AND w.employment_id=assessment.employment_id AND r.id=assessment.run_id;
   IF selected_work.id IS NULL OR selected_work.payload->>'month' IS DISTINCT FROM to_char(assessment.tax_month,'YYYY-MM') OR
    selected_work.payload->>'employeeId' IS DISTINCT FROM assessment.employment_id::text
    THEN RAISE EXCEPTION 'Amendment attendance requires its exact owned closing evidence' USING ERRCODE='23514'; END IF;
  END IF;
  IF nullif(item->'compensation','null') IS NOT NULL THEN
   SELECT e.* INTO source_calc FROM payroll_assessment_evidence e
    JOIN payroll_assessment_heads h ON h.company_id=e.company_id AND h.assessment_id=e.assessment_id AND h.revision=e.revision
    WHERE e.company_id=p_company AND e.assessment_id=assessment.id;
   IF item->'compensation'->'tax' IS DISTINCT FROM source_calc.facts->'compensation'->'tax'
    THEN RAISE EXCEPTION 'Remuneration cannot replace verified tax evidence' USING ERRCODE='23514'; END IF;
  END IF;
 END LOOP;
END $$;

CREATE OR REPLACE FUNCTION validate_payroll_amendment_result() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE attempt payroll_amendment_calculations; amendment payroll_amendments; draft payroll_amendment_revisions;
 assessment payroll_assessments; original_result payroll_assessment_evidence; correction jsonb; selected_work attendance_adjustments; expected_days jsonb; expected_work_id uuid; BEGIN
 SELECT * INTO attempt FROM payroll_amendment_calculations WHERE company_id=NEW.company_id AND id=NEW.calculation_id;
 SELECT * INTO amendment FROM payroll_amendments WHERE company_id=NEW.company_id AND id=attempt.amendment_id;
 SELECT * INTO draft FROM payroll_amendment_revisions WHERE company_id=NEW.company_id AND amendment_id=attempt.amendment_id AND revision=attempt.draft_revision;
 SELECT * INTO assessment FROM payroll_assessments WHERE company_id=NEW.company_id AND id=NEW.assessment_id;
 SELECT * INTO original_result FROM payroll_assessment_evidence WHERE company_id=NEW.company_id
  AND assessment_id=assessment.id AND revision=NEW.assessment_revision;
 SELECT value INTO correction FROM jsonb_array_elements(draft.terms->'corrections') WHERE value->>'assessmentId'=NEW.assessment_id::text;
 expected_work_id:=coalesce((correction->>'workAdjustmentId')::uuid,(original_result.facts->>'workAdjustmentId')::uuid);
 expected_days:=original_result.facts->'workDays';
 IF correction->>'workAdjustmentId' IS NOT NULL THEN
  SELECT w.* INTO selected_work FROM attendance_adjustments w
   JOIN payroll_runs r ON r.company_id=w.company_id AND r.work_job_id=w.job_id
   WHERE w.company_id=NEW.company_id AND w.id=(correction->>'workAdjustmentId')::uuid
    AND w.employment_id=assessment.employment_id AND r.id=assessment.run_id;
  IF selected_work.id IS NULL OR selected_work.payload->>'month' IS DISTINCT FROM to_char(assessment.tax_month,'YYYY-MM') OR
   selected_work.payload->>'employeeId' IS DISTINCT FROM assessment.employment_id::text OR
   jsonb_typeof(selected_work.payload->'days') IS DISTINCT FROM 'array' OR
   jsonb_array_length(selected_work.payload->'days')<>extract(day FROM (assessment.tax_month+interval '1 month'-interval '1 day'))::integer
   THEN RAISE EXCEPTION 'Amended work facts require their complete owned attendance revision' USING ERRCODE='23514'; END IF;
  SELECT jsonb_agg(jsonb_build_object(
    'date',d.value->'workDate','schedule',d.value->'schedule'->'kind','attendance',d.value->'fact',
    'officialHoliday',nullif(d.value->'schedule'->'holidayId','null') IS NOT NULL,
    'overtime',coalesce((SELECT jsonb_agg(jsonb_build_object('requestId',o.value->'requestId',
       'revision',o.value->'revision','minutes',o.value->'approvedMinutes') ORDER BY o.ordinality)
       FROM jsonb_array_elements(coalesce(d.value->'overtime','[]'::jsonb)) WITH ORDINALITY o),'[]'::jsonb)
   ) ORDER BY d.ordinality) INTO expected_days
   FROM jsonb_array_elements(selected_work.payload->'days') WITH ORDINALITY d;
 END IF;
 IF attempt.status IS DISTINCT FROM 'SUCCEEDED' OR attempt.actor_id IS DISTINCT FROM current_actor_id() OR assessment.id IS NULL OR
  original_result.assessment_id IS NULL OR assessment.person_id IS DISTINCT FROM amendment.person_id OR assessment.tax_month<amendment.first_month OR
  NOT EXISTS(SELECT 1 FROM jsonb_array_elements(draft.terms->'sourceVersions') WHERE value->>'assessmentId'=NEW.assessment_id::text AND value->>'revision'=NEW.assessment_revision::text) OR
  NEW.original IS DISTINCT FROM original_result.calculation OR NEW.facts->>'month' IS DISTINCT FROM to_char(assessment.tax_month,'YYYY-MM') OR
  NEW.calculation->'taxInput'->>'month' IS DISTINCT FROM to_char(assessment.tax_month,'YYYY-MM') OR
  (NEW.facts-ARRAY['compensation','input','taxHistory','workDays','workAdjustmentId']) IS DISTINCT FROM
   (original_result.facts-ARRAY['compensation','input','taxHistory','workDays','workAdjustmentId']) OR
  (NEW.facts->>'workAdjustmentId')::uuid IS DISTINCT FROM expected_work_id OR
  NEW.facts->'workDays' IS DISTINCT FROM expected_days OR
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

-- A new draft or calculation must select the latest employee snapshot under the
-- month guard. Later review/publication keeps the exact immutable selection.
CREATE FUNCTION validate_payroll_amendment_attendance_selection() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE item record; latest_id uuid; BEGIN
 IF NEW.action NOT IN('SAVED','CALCULATED','CALCULATION_FAILED') THEN RETURN NEW; END IF;
 IF jsonb_typeof(NEW.terms->'corrections') IS DISTINCT FROM 'array' OR
  jsonb_array_length(NEW.terms->'corrections') NOT BETWEEN 1 AND 12
  THEN RAISE EXCEPTION 'Amendment attendance selections must be bounded' USING ERRCODE='23514'; END IF;
 FOR item IN
  SELECT (c.value->>'workAdjustmentId')::uuid adjustment_id,a.employment_id,r.work_job_id,a.tax_month
   FROM jsonb_array_elements(NEW.terms->'corrections') c
   LEFT JOIN payroll_assessments a ON a.company_id=NEW.company_id AND a.id=(c.value->>'assessmentId')::uuid
   LEFT JOIN payroll_runs r ON r.company_id=a.company_id AND r.id=a.run_id
   WHERE c.value->>'workAdjustmentId' IS NOT NULL ORDER BY a.tax_month,a.id
 LOOP
  PERFORM 1 FROM work_periods p WHERE p.company_id=NEW.company_id AND p.job_id=item.work_job_id FOR SHARE;
  SELECT w.id INTO latest_id FROM attendance_adjustments w WHERE w.company_id=NEW.company_id
   AND w.job_id=item.work_job_id AND w.employment_id=item.employment_id ORDER BY w.revision DESC LIMIT 1;
  IF latest_id IS NULL OR latest_id<>item.adjustment_id
   THEN RAISE EXCEPTION 'New amendment work selection must match current attendance' USING ERRCODE='23514'; END IF;
 END LOOP;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_amendment_attendance_current BEFORE INSERT ON payroll_amendment_revisions
 FOR EACH ROW EXECUTE FUNCTION validate_payroll_amendment_attendance_selection();
