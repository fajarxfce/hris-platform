CREATE TABLE payroll_amendments (
 company_id uuid NOT NULL,id uuid NOT NULL,first_assessment_id uuid NOT NULL,
 first_month date NOT NULL CHECK(extract(day FROM first_month)=1 AND extract(year FROM first_month) BETWEEN 2024 AND 2100),
 person_id uuid NOT NULL,employment_id uuid NOT NULL,employee_number varchar(64) NOT NULL,employee_name varchar(200) NOT NULL,
 employment_version bigint NOT NULL CHECK(employment_version>=0),version bigint NOT NULL CHECK(version BETWEEN 0 AND 999),
 created_by uuid NOT NULL REFERENCES accounts(id),created_at timestamptz NOT NULL,
 PRIMARY KEY(company_id,id),FOREIGN KEY(company_id,first_assessment_id) REFERENCES payroll_assessments(company_id,id),
 FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
CREATE TABLE payroll_amendment_revisions (
 company_id uuid NOT NULL,amendment_id uuid NOT NULL,revision bigint NOT NULL CHECK(revision BETWEEN 0 AND 999),
 status varchar(20) NOT NULL CHECK(status IN('DRAFT','CALCULATED','CANCELLED')),
 action varchar(24) NOT NULL CHECK(action IN('SAVED','CALCULATED','CALCULATION_FAILED','CANCELLED')),
 terms jsonb NOT NULL CHECK(jsonb_typeof(terms)='object' AND pg_column_size(terms)<=1048576),latest_calculation_id uuid,
 actor_id uuid NOT NULL REFERENCES accounts(id),recorded_at timestamptz NOT NULL,
 reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
 PRIMARY KEY(company_id,amendment_id,revision),FOREIGN KEY(company_id,amendment_id) REFERENCES payroll_amendments(company_id,id)
);
ALTER TABLE payroll_amendments ADD CONSTRAINT payroll_amendment_current_revision FOREIGN KEY(company_id,id,version)
 REFERENCES payroll_amendment_revisions(company_id,amendment_id,revision) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX payroll_amendment_year ON payroll_amendments(company_id,first_month,id);
CREATE INDEX payroll_amendment_employee ON payroll_amendments(company_id,employment_id,first_month,id);

CREATE TABLE payroll_amendment_calculations (
 company_id uuid NOT NULL,id uuid NOT NULL,amendment_id uuid NOT NULL,calculation_number integer NOT NULL CHECK(calculation_number BETWEEN 1 AND 32),
 draft_revision bigint NOT NULL CHECK(draft_revision BETWEEN 0 AND 998),status varchar(16) NOT NULL CHECK(status IN('SUCCEEDED','FAILED')),
 assessment_count integer NOT NULL CHECK(assessment_count BETWEEN 0 AND 12),
 difference_taxable_gross numeric(20,2),difference_withheld numeric(20,2),difference_take_home numeric(20,2),
 difference_employee_deductions numeric(20,2),difference_employer_cost numeric(20,2),
 failure_kind varchar(24),failure_code varchar(120),
 failure_fields jsonb NOT NULL DEFAULT '{}' CHECK(jsonb_typeof(failure_fields)='object' AND pg_column_size(failure_fields)<=65536),
 failure_parameters jsonb NOT NULL DEFAULT '{}' CHECK(jsonb_typeof(failure_parameters)='object' AND pg_column_size(failure_parameters)<=8192),
 actor_id uuid NOT NULL REFERENCES accounts(id),recorded_at timestamptz NOT NULL,reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
 PRIMARY KEY(company_id,id),UNIQUE(company_id,amendment_id,calculation_number),UNIQUE(company_id,amendment_id,id),
 FOREIGN KEY(company_id,amendment_id,draft_revision) REFERENCES payroll_amendment_revisions(company_id,amendment_id,revision),
 CHECK((status='SUCCEEDED' AND assessment_count BETWEEN 1 AND 12 AND failure_kind IS NULL AND failure_code IS NULL
   AND failure_fields='{}' AND failure_parameters='{}' AND difference_taxable_gross IS NOT NULL AND difference_withheld IS NOT NULL
   AND difference_take_home IS NOT NULL AND difference_employee_deductions IS NOT NULL AND difference_employer_cost IS NOT NULL)
  OR (status='FAILED' AND assessment_count=0 AND failure_kind IS NOT NULL AND failure_code IS NOT NULL
   AND failure_kind IN('VALIDATION','CONFLICT','NOT_FOUND','FORBIDDEN','UNAUTHENTICATED','UNAVAILABLE','UNEXPECTED')
   AND failure_code ~ '^[a-z][a-z0-9_]{0,119}$' AND difference_taxable_gross IS NULL AND difference_withheld IS NULL
   AND difference_take_home IS NULL AND difference_employee_deductions IS NULL AND difference_employer_cost IS NULL))
);
ALTER TABLE payroll_amendment_revisions ADD CONSTRAINT payroll_amendment_calculation_owner FOREIGN KEY(company_id,amendment_id,latest_calculation_id)
 REFERENCES payroll_amendment_calculations(company_id,amendment_id,id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE payroll_amendment_results (
 company_id uuid NOT NULL,calculation_id uuid NOT NULL,ordinal integer NOT NULL CHECK(ordinal BETWEEN 1 AND 12),
 assessment_id uuid NOT NULL,assessment_revision bigint NOT NULL CHECK(assessment_revision=0),
 facts jsonb NOT NULL CHECK(jsonb_typeof(facts)='object' AND pg_column_size(facts)<=262144),
 original jsonb NOT NULL CHECK(jsonb_typeof(original)='object' AND pg_column_size(original)<=131072),
 calculation jsonb NOT NULL CHECK(jsonb_typeof(calculation)='object' AND pg_column_size(calculation)<=131072),
 difference_taxable_gross numeric(20,2) NOT NULL,difference_withheld numeric(20,2) NOT NULL,difference_take_home numeric(20,2) NOT NULL,
 difference_employee_deductions numeric(20,2) NOT NULL,difference_employer_cost numeric(20,2) NOT NULL,
 PRIMARY KEY(company_id,calculation_id,ordinal),UNIQUE(company_id,calculation_id,assessment_id),
 FOREIGN KEY(company_id,calculation_id) REFERENCES payroll_amendment_calculations(company_id,id),
 FOREIGN KEY(company_id,assessment_id) REFERENCES payroll_assessments(company_id,id)
);

-- Acquisition and calculation retain a complete published year, including unchanged predecessors.
-- Publication will introduce revised assessment heads; this stage reads immutable revision zero.
CREATE FUNCTION assert_payroll_amendment_basis(p_company uuid,p_person uuid,p_first date,p_first_id uuid,p_terms jsonb)
 RETURNS void LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE sources jsonb:=p_terms->'sourceVersions'; corrections jsonb:=p_terms->'corrections'; item jsonb; assessment payroll_assessments;
 source_count integer; source_calc payroll_run_results; BEGIN
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
    (item->>'revision') IS DISTINCT FROM '0'
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
   SELECT * INTO source_calc FROM payroll_run_results WHERE company_id=p_company AND run_id=assessment.run_id AND ordinal=assessment.ordinal;
   IF item->'compensation'->'tax' IS DISTINCT FROM source_calc.facts->'compensation'->'tax'
    THEN RAISE EXCEPTION 'Remuneration cannot replace verified tax evidence' USING ERRCODE='23514'; END IF;
  END IF;
 END LOOP;
END $$;

CREATE FUNCTION protect_payroll_amendment() RETURNS trigger LANGUAGE plpgsql AS $$
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
  IF previous.revision IS NULL OR previous.status='CANCELLED' OR NEW.version<>OLD.version+1 OR
    (to_jsonb(NEW)-'version') IS DISTINCT FROM (to_jsonb(OLD)-'version')
   THEN RAISE EXCEPTION 'Amendment identity and revision order are protected' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_amendment_protected BEFORE INSERT OR UPDATE OR DELETE ON payroll_amendments FOR EACH ROW EXECUTE FUNCTION protect_payroll_amendment();

CREATE FUNCTION validate_payroll_amendment_revision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE amendment payroll_amendments; previous payroll_amendment_revisions; BEGIN
 SELECT * INTO amendment FROM payroll_amendments WHERE company_id=NEW.company_id AND id=NEW.amendment_id;
 IF amendment.id IS NULL OR amendment.version<>NEW.revision OR NEW.actor_id IS DISTINCT FROM current_actor_id()
  THEN RAISE EXCEPTION 'Amendment revision must match its current header and actor' USING ERRCODE='23514'; END IF;
 IF NEW.revision=999 AND NEW.action<>'CANCELLED'
  THEN RAISE EXCEPTION 'The final amendment revision is reserved for cancellation' USING ERRCODE='23514'; END IF;
 IF NEW.revision=0 THEN
  IF NEW.action<>'SAVED' OR NEW.status<>'DRAFT' OR NEW.latest_calculation_id IS NOT NULL OR NEW.actor_id<>amendment.created_by OR NEW.recorded_at<>amendment.created_at
   THEN RAISE EXCEPTION 'Amendment starts with its original draft' USING ERRCODE='23514'; END IF;
 ELSE
  SELECT * INTO previous FROM payroll_amendment_revisions WHERE company_id=NEW.company_id AND amendment_id=NEW.amendment_id AND revision=NEW.revision-1;
  IF previous.revision IS NULL OR previous.status='CANCELLED' OR
   NOT ((NEW.action='SAVED' AND NEW.status='DRAFT' AND NEW.latest_calculation_id IS NOT DISTINCT FROM previous.latest_calculation_id) OR
    (NEW.action='CANCELLED' AND NEW.status='CANCELLED' AND NEW.terms=previous.terms AND NEW.latest_calculation_id IS NOT DISTINCT FROM previous.latest_calculation_id) OR
    (NEW.action IN('CALCULATED','CALCULATION_FAILED') AND previous.status='DRAFT' AND NEW.terms=previous.terms AND NEW.latest_calculation_id IS NOT NULL AND
     NEW.latest_calculation_id IS DISTINCT FROM previous.latest_calculation_id AND NEW.status=CASE WHEN NEW.action='CALCULATED' THEN 'CALCULATED' ELSE 'DRAFT' END))
   THEN RAISE EXCEPTION 'Amendment revision transition is invalid' USING ERRCODE='23514'; END IF;
 END IF;
 IF NEW.action='SAVED' THEN
  PERFORM assert_payroll_amendment_basis(NEW.company_id,amendment.person_id,amendment.first_month,amendment.first_assessment_id,NEW.terms);
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_amendment_revision_valid BEFORE INSERT ON payroll_amendment_revisions FOR EACH ROW EXECUTE FUNCTION validate_payroll_amendment_revision();
CREATE TRIGGER payroll_amendment_revision_immutable BEFORE UPDATE OR DELETE ON payroll_amendment_revisions FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

CREATE FUNCTION validate_payroll_amendment_calculation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE amendment payroll_amendments; draft payroll_amendment_revisions; outcome payroll_amendment_revisions; previous payroll_amendment_calculations; BEGIN
 SELECT * INTO amendment FROM payroll_amendments WHERE company_id=NEW.company_id AND id=NEW.amendment_id;
 SELECT * INTO draft FROM payroll_amendment_revisions WHERE company_id=NEW.company_id AND amendment_id=NEW.amendment_id AND revision=NEW.draft_revision;
 SELECT * INTO outcome FROM payroll_amendment_revisions WHERE company_id=NEW.company_id AND amendment_id=NEW.amendment_id AND revision=NEW.draft_revision+1;
 SELECT * INTO previous FROM payroll_amendment_calculations WHERE company_id=NEW.company_id AND id=draft.latest_calculation_id;
 IF amendment.id IS NULL OR amendment.version<>NEW.draft_revision+1 OR draft.status IS DISTINCT FROM 'DRAFT' OR
  NEW.calculation_number<>coalesce(previous.calculation_number,0)+1 OR outcome.latest_calculation_id IS DISTINCT FROM NEW.id OR
  outcome.action IS DISTINCT FROM (CASE WHEN NEW.status='SUCCEEDED' THEN 'CALCULATED' ELSE 'CALCULATION_FAILED' END) OR
  (NEW.actor_id,NEW.recorded_at,NEW.reason) IS DISTINCT FROM (outcome.actor_id,outcome.recorded_at,outcome.reason) OR NEW.actor_id IS DISTINCT FROM current_actor_id()
  THEN RAISE EXCEPTION 'Calculation requires its exact current draft and outcome revision' USING ERRCODE='23514'; END IF;
 PERFORM assert_payroll_amendment_basis(NEW.company_id,amendment.person_id,amendment.first_month,amendment.first_assessment_id,draft.terms);
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_amendment_calculation_valid BEFORE INSERT ON payroll_amendment_calculations FOR EACH ROW EXECUTE FUNCTION validate_payroll_amendment_calculation();
CREATE TRIGGER payroll_amendment_calculation_immutable BEFORE UPDATE OR DELETE ON payroll_amendment_calculations FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

CREATE FUNCTION validate_payroll_amendment_result() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE attempt payroll_amendment_calculations; amendment payroll_amendments; draft payroll_amendment_revisions;
 assessment payroll_assessments; original_result payroll_run_results; correction jsonb; BEGIN
 SELECT * INTO attempt FROM payroll_amendment_calculations WHERE company_id=NEW.company_id AND id=NEW.calculation_id;
 SELECT * INTO amendment FROM payroll_amendments WHERE company_id=NEW.company_id AND id=attempt.amendment_id;
 SELECT * INTO draft FROM payroll_amendment_revisions WHERE company_id=NEW.company_id AND amendment_id=attempt.amendment_id AND revision=attempt.draft_revision;
 SELECT * INTO assessment FROM payroll_assessments WHERE company_id=NEW.company_id AND id=NEW.assessment_id;
 SELECT * INTO original_result FROM payroll_run_results WHERE company_id=NEW.company_id AND run_id=assessment.run_id AND ordinal=assessment.ordinal;
 SELECT value INTO correction FROM jsonb_array_elements(draft.terms->'corrections') WHERE value->>'assessmentId'=NEW.assessment_id::text;
 IF attempt.status IS DISTINCT FROM 'SUCCEEDED' OR attempt.actor_id IS DISTINCT FROM current_actor_id() OR assessment.id IS NULL OR
  assessment.person_id IS DISTINCT FROM amendment.person_id OR assessment.tax_month<amendment.first_month OR
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
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_amendment_result_valid BEFORE INSERT ON payroll_amendment_results FOR EACH ROW EXECUTE FUNCTION validate_payroll_amendment_result();
CREATE TRIGGER payroll_amendment_result_immutable BEFORE UPDATE OR DELETE ON payroll_amendment_results FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

CREATE FUNCTION assert_payroll_amendment_calculation(p_company uuid,p_id uuid) RETURNS void LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE attempt payroll_amendment_calculations; amendment payroll_amendments; draft payroll_amendment_revisions; actual integer; expected integer;
 gross numeric; withheld numeric; cash numeric; deductions numeric; cost numeric; BEGIN
 SELECT * INTO attempt FROM payroll_amendment_calculations WHERE company_id=p_company AND id=p_id;
 IF attempt.id IS NULL THEN RAISE EXCEPTION 'Amendment outcome requires its calculation' USING ERRCODE='23514'; END IF;
 SELECT count(*),sum(difference_taxable_gross),sum(difference_withheld),sum(difference_take_home),sum(difference_employee_deductions),sum(difference_employer_cost)
  INTO actual,gross,withheld,cash,deductions,cost FROM payroll_amendment_results WHERE company_id=p_company AND calculation_id=p_id;
 IF actual<>attempt.assessment_count OR (attempt.status='FAILED' AND actual<>0)
  THEN RAISE EXCEPTION 'Amendment result set is incomplete' USING ERRCODE='23514'; END IF;
 IF attempt.status='SUCCEEDED' THEN
  SELECT * INTO amendment FROM payroll_amendments WHERE company_id=p_company AND id=attempt.amendment_id;
  SELECT * INTO draft FROM payroll_amendment_revisions WHERE company_id=p_company AND amendment_id=attempt.amendment_id AND revision=attempt.draft_revision;
  SELECT count(*) INTO expected FROM jsonb_array_elements(draft.terms->'sourceVersions') s
   JOIN payroll_assessments a ON a.company_id=p_company AND a.id=(s.value->>'assessmentId')::uuid WHERE a.tax_month>=amendment.first_month;
  IF actual<>expected OR (gross,withheld,cash,deductions,cost) IS DISTINCT FROM
    (attempt.difference_taxable_gross,attempt.difference_withheld,attempt.difference_take_home,attempt.difference_employee_deductions,attempt.difference_employer_cost) OR
    EXISTS(SELECT 1 FROM (SELECT r.ordinal,row_number() OVER(ORDER BY a.tax_month) expected_ordinal FROM payroll_amendment_results r
     JOIN payroll_assessments a ON a.company_id=r.company_id AND a.id=r.assessment_id WHERE r.company_id=p_company AND r.calculation_id=p_id) ordered WHERE ordinal<>expected_ordinal)
   THEN RAISE EXCEPTION 'Amendment totals and chronological suffix must be complete' USING ERRCODE='23514'; END IF;
 END IF;
END $$;
CREATE FUNCTION check_payroll_amendment_calculation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_TABLE_NAME='payroll_amendment_calculations' THEN PERFORM assert_payroll_amendment_calculation(NEW.company_id,NEW.id);
 ELSIF TG_TABLE_NAME='payroll_amendment_results' THEN PERFORM assert_payroll_amendment_calculation(NEW.company_id,NEW.calculation_id);
 ELSIF NEW.action IN('CALCULATED','CALCULATION_FAILED') THEN PERFORM assert_payroll_amendment_calculation(NEW.company_id,NEW.latest_calculation_id);
 END IF; RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payroll_amendment_outcome_complete AFTER INSERT ON payroll_amendment_revisions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_amendment_calculation();
CREATE CONSTRAINT TRIGGER payroll_amendment_calculation_complete AFTER INSERT ON payroll_amendment_calculations DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_amendment_calculation();
CREATE CONSTRAINT TRIGGER payroll_amendment_results_complete AFTER INSERT ON payroll_amendment_results DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_amendment_calculation();

DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['payroll_amendments','payroll_amendment_revisions','payroll_amendment_calculations','payroll_amendment_results'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
 END LOOP;
END $$;
