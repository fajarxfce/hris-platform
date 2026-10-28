"""Fictional payroll source preparation for an owned workload fixture."""

from datetime import datetime, timezone
import json
import time
import uuid

from measure_attendance import Api, WorkloadError, code


class Actor:
    def __init__(self, api, email, password):
        self.api = api
        self.credentials = api.call("/auth/native/login", method="POST", body={
            "email": email, "password": password, "deviceName": "Fictional payroll workload"})["credentials"]
        enrollment = api.call("/auth/native/mfa/enrollment", method="POST", token=self.credentials["accessToken"])
        self.secret = enrollment["secret"]
        self.credentials = api.call("/auth/native/mfa/enrollment/confirm", method="POST", token=self.credentials["accessToken"],
            body={"operationId": enrollment["operationId"], "code": code(self.secret)})["credentials"]
        self.proof_at = time.monotonic()

    def call(self, path, *, method="GET", body=None):
        deadline = datetime.fromisoformat(self.credentials["accessExpiresAt"].replace("Z", "+00:00"))
        if (deadline - datetime.now(timezone.utc)).total_seconds() < 60:
            self.credentials = self.api.call("/auth/native/refresh", method="POST", body={"refreshToken": self.credentials["refreshToken"]})
        if method != "GET" and time.monotonic() - self.proof_at > 240:
            self.credentials = self.api.call("/auth/native/mfa/verify", method="POST", token=self.credentials["accessToken"], body={"code": code(self.secret)})["credentials"]
            self.proof_at = time.monotonic()
        return self.api.call(path, method=method, body=body, token=self.credentials["accessToken"])


def compensation_terms(year):
    return {
        "basicSalary": "10000000.25", "fixedEarnings": [{"code": "MEAL", "name": "Meal allowance", "amount": "500000.75", "taxable": True}],
        "treatment": "GROSS", "tax": {"residency": "RESIDENT", "ptkp": "TK0", "residenceCountry": "ID",
            "subjectiveFrom": None, "subjectiveUntil": None, "verifiedOn": f"{year}-01-01", "verificationReference": "Fictional registration"},
        "insuranceWage": "10000000.25", "insurancePrograms": ["HEALTH", "OLD_AGE", "PENSION", "ACCIDENT", "DEATH"],
        "insuranceExemptionReason": None, "accidentRisk": "LOW", "additionalHealthDependents": 0,
        "additionalRetirementContribution": "100000.25", "qualifiedDonation": "0", "otherNetDeduction": "0",
        "payBasis": {"overtimeRuleId": "ID-PP35-2021-v1", "holidayAllowanceRuleId": "ID-PERMENAKER6-2016-v1",
            "proration": "CALENDAR_DAYS", "workWeek": "FIVE_DAYS", "shortestWorkDay": None, "overtimeEligibility": "ELIGIBLE",
            "overtimeExemptionReference": None, "regularNonFixedWage": "0", "serviceMonthConvention": "COMPLETE_MONTHS",
            "rounding": "HALF_UP", "reviewReference": "Fictional earning conventions"},
    }


def prepare_payroll(fixture, count):
    api = Api(fixture.endpoint)
    try:
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            try:
                api.call("/auth/native/firebase")
                if fixture.sql("SELECT count(*) FROM accounts a JOIN platform_permissions p ON p.account_id=a.id WHERE a.email='workload-admin@example.invalid' AND p.permission='identity.manage'") == "1":
                    break
            except OSError:
                pass
            time.sleep(.5)
        else:
            raise WorkloadError("Payroll fixture API readiness timed out")
        admin = Actor(api, "workload-admin@example.invalid", fixture.bootstrap_password)
        company = admin.call("/companies", method="POST", body={"code": "PAYLOAD", "name": "Fictional Payroll Workload", "timezone": "UTC"})["id"]
        base = f"/companies/{company}"
        operator_ids = [str(uuid.uuid4()) for _ in range(2)]
        for account, role, permissions in (
            (operator_ids[0], "preparer", ["company.read", "payroll.calculate", "payroll.policy.manage", "payroll.compensation.manage"]),
            (operator_ids[1], "reviewer", ["company.read", "payroll.review"]),
        ):
            fixture.sql(f"INSERT INTO accounts(id,email,display_name,password_hash) SELECT '{account}','payroll-{role}@example.invalid','Fictional payroll {role}',password_hash FROM accounts WHERE email='workload-admin@example.invalid';")
            admin.call(base + "/members/" + account, method="PUT", body={"active": True, "permissions": permissions, "reason": "Independent fictional workload roles"})
        preparer = Actor(api, "payroll-preparer@example.invalid", fixture.bootstrap_password)
        reviewer = Actor(api, "payroll-reviewer@example.invalid", fixture.bootstrap_password)
        now = datetime.now(timezone.utc)
        year = now.year if now.month > 1 else now.year - 1
        month = f"{year}-01"
        employees = [str(uuid.uuid4()) for _ in range(count)]
        first = employees[0]
        admin.call(base + "/employees", method="POST", body={"id": first, "employeeNumber": "PAY-00001",
            "person": {"id": str(uuid.uuid4()), "legalName": "Fictional Payroll Employee 1", "nationality": "ID"},
            "terms": {"effectiveFrom": month + "-01", "startDate": month + "-01", "status": "ACTIVE", "contract": "PERMANENT"},
            "reason": "Fictional payroll workload template"})
        rows = "\n".join(f"{n}\t{employee}\t{uuid.uuid4()}" for n, employee in enumerate(employees[1:], 2))
        actor = fixture.sql("SELECT id FROM accounts WHERE email='workload-admin@example.invalid'")
        fixture.sql(f"""BEGIN;
SET LOCAL hris.company_id='{company}'; SET LOCAL hris.actor_id='{actor}';
CREATE TEMP TABLE payroll_load_people(n integer,employee uuid,person uuid) ON COMMIT DROP;
COPY payroll_load_people FROM STDIN;
{rows}
\\.
INSERT INTO persons(id,owner_company_id,legal_name,nationality) SELECT person,'{company}','Fictional Payroll Employee '||n,'ID' FROM payroll_load_people;
INSERT INTO person_profile_revisions(person_id,revision,owner_company_id,legal_name,nationality,actor_id,reason)
SELECT person,0,'{company}','Fictional Payroll Employee '||n,'ID','{actor}','Fictional workload setup' FROM payroll_load_people;
INSERT INTO employments(company_id,id,person_id,employee_number) SELECT '{company}',employee,person,'PAY-'||lpad(n::text,5,'0') FROM payroll_load_people;
INSERT INTO employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,actor_id,reason)
SELECT '{company}',employee,0,'{month}-01','PERMANENT','{month}-01','ACTIVE','{actor}','Fictional workload setup' FROM payroll_load_people;
COMMIT;
ANALYZE;
""")
        fixture.start_worker()
        before = admin.call(base + "/workforce/periods/" + month)
        admin.call(base + "/workforce/periods/" + month + "/close", method="POST", body={
            "expectedVersion": (before.get("period") or {}).get("version", 0), "reason": "Close fictional payroll workload evidence"})
        deadline, progress = time.monotonic() + 1200, time.monotonic() + 30
        while time.monotonic() < deadline:
            work = admin.call(base + "/workforce/periods/" + month)
            state = (work.get("period") or {}).get("status")
            if state == "CLOSED":
                break
            if state not in ("PROCESSING", "OPEN"):
                raise WorkloadError("Payroll workload attendance closing did not finish")
            if time.monotonic() >= progress:
                print(json.dumps({"preparation": "attendanceClosing", "employees": count}), flush=True)
                progress = time.monotonic() + 30
            time.sleep(1)
        else:
            raise WorkloadError("Payroll attendance preparation exceeded twenty minutes")
        preparer.call(base + "/payroll/policy", method="PUT", body={"expectedVersion": None,
            "effectiveFrom": month, "effectiveUntil": f"{year}-12", "incomeTaxRuleId": "ID-PP58-PMK168-2024-v1", "insuranceRuleId": "ID-BPJS-PU-v1",
            "minimumMonthlyWage": "4000000", "healthWageCap": "12000000", "pensionWageCap": "10000000", "contributionRounding": "HALF_UP",
            "reviewReferences": ["https://example.invalid/fictional-policy"], "reason": "Fictional workload policy"})
        preparer.call(base + f"/payroll/employees/{first}/compensation", method="PUT", body={"expectedVersion": None,
            "expectedEmploymentVersion": 0, "effectiveFrom": month, "terms": compensation_terms(year), "reason": "Fictional workload compensation"})
        inputs = base + f"/payroll/employees/{first}/inputs/{month}"
        review = preparer.call(inputs + "/review")
        preparer.call(inputs, method="PUT", body={"expectedVersion": None, "expectedEmploymentVersion": 0,
            "workJobId": review["source"]["jobId"], "expectedWorkPeriodVersion": review["source"]["version"],
            "terms": {"earnings": [], "deductions": [], "nonCashTaxable": "0", "scheduledMonthUnits": None,
                "dayResolutions": [{"workDate": f"{month}-{day:02}", "portion": "FULL", "disposition": "PAID", "reference": "Fictional paid day"} for day in range(1, 32)],
                "holidayAllowance": None, "reviewReference": "Fictional closed attendance review"}, "reason": "Fictional monthly input"})
        reviewer.call(inputs + "/verify", method="POST", body={"expectedVersion": 0, "reason": "Independent fictional input review"})
        opening = base + f"/payroll/employees/{first}/tax-openings/{year}"
        preparer.call(opening, method="PUT", body={"expectedVersion": None, "expectedEmploymentVersion": 0,
            "terms": {"throughMonth": 0, "residency": "RESIDENT", "ptkp": "TK0", "reference": "Fictional zero January opening",
                "history": {"taxableGross": "0", "retirementContributions": "0", "qualifiedDonations": "0", "withheld": "0",
                            "employmentMonths": 0, "previousEmployerNet": "0", "previousEmployerWithheld": "0"}}, "reason": "Fictional initial history"})
        reviewer.call(opening + "/verify", method="POST", body={"expectedVersion": 0, "reason": "Independent fictional opening review"})
        clone_sources(fixture, company, first, month, year, operator_ids)
        period = str(uuid.uuid4())
        preparer.call(base + "/payroll/periods", method="POST", body={"id": period, "earningsMonth": month,
            "plannedPaymentDate": month + "-31", "employeeIds": employees, "reason": "Fictional complete payroll workload cohort"})
        return api, preparer, reviewer, company, period, month, review["source"]["version"]
    except BaseException:
        api.close()
        raise


def clone_sources(fixture, company, first, month, year, operators):
    # Preserve real API-created terms and both immutable draft/verified revisions.
    # Every copied input references that employee's actual worker-produced closing.
    fixture.sql(f"""BEGIN;
SET LOCAL hris.company_id='{company}'; SET LOCAL hris.actor_id='{operators[0]}';
CREATE TEMP TABLE payroll_load_sources ON COMMIT DROP AS
SELECT e.id AS employee,e.employee_number,p.legal_name,gen_random_uuid() AS input_id,gen_random_uuid() AS opening_id
FROM employments e JOIN persons p ON p.id=e.person_id WHERE e.company_id='{company}' AND e.id<>'{first}';
INSERT INTO employee_compensations(company_id,employment_id,version) SELECT '{company}',employee,0 FROM payroll_load_sources;
INSERT INTO employee_compensation_revisions(company_id,employment_id,revision,effective_from,employee_number,employee_name,terms,actor_id,reason)
SELECT '{company}',s.employee,0,t.effective_from,s.employee_number,s.legal_name,t.terms,t.actor_id,'Fictional template copy'
FROM payroll_load_sources s CROSS JOIN employee_compensation_revisions t WHERE t.company_id='{company}' AND t.employment_id='{first}' AND t.revision=0;
INSERT INTO payroll_inputs(company_id,id,employment_id,earnings_month,version) SELECT '{company}',input_id,employee,'{month}-01',0 FROM payroll_load_sources;
INSERT INTO payroll_input_revisions(company_id,input_id,revision,employment_id,earnings_month,work_job_id,work_period_version,employment_version,status,terms,prepared_by,verified_by,actor_id,reason,recorded_at)
SELECT '{company}',s.input_id,0,s.employee,t.earnings_month,t.work_job_id,t.work_period_version,0,'DRAFT',t.terms,t.prepared_by,NULL,t.prepared_by,'Fictional template copy',now()
FROM payroll_load_sources s CROSS JOIN payroll_input_revisions t WHERE t.company_id='{company}' AND t.employment_id='{first}' AND t.earnings_month='{month}-01' AND t.revision=0;
UPDATE payroll_inputs SET version=1 WHERE company_id='{company}' AND id IN(SELECT input_id FROM payroll_load_sources);
INSERT INTO payroll_input_revisions(company_id,input_id,revision,employment_id,earnings_month,work_job_id,work_period_version,employment_version,status,terms,prepared_by,verified_by,actor_id,reason,recorded_at)
SELECT '{company}',s.input_id,1,s.employee,t.earnings_month,t.work_job_id,t.work_period_version,0,'VERIFIED',t.terms,t.prepared_by,t.verified_by,t.verified_by,'Fictional verified template copy',now()
FROM payroll_load_sources s CROSS JOIN payroll_input_revisions t WHERE t.company_id='{company}' AND t.employment_id='{first}' AND t.earnings_month='{month}-01' AND t.revision=1;
INSERT INTO payroll_tax_openings(company_id,id,employment_id,tax_year,version) SELECT '{company}',opening_id,employee,{year},0 FROM payroll_load_sources;
INSERT INTO payroll_tax_opening_revisions(company_id,opening_id,revision,status,terms,prepared_by,verified_by,actor_id,reason,recorded_at)
SELECT '{company}',s.opening_id,0,'DRAFT',t.terms,t.prepared_by,NULL,t.prepared_by,'Fictional template copy',now()
FROM payroll_load_sources s CROSS JOIN payroll_tax_opening_revisions t JOIN payroll_tax_openings h ON h.company_id=t.company_id AND h.id=t.opening_id
WHERE h.company_id='{company}' AND h.employment_id='{first}' AND h.tax_year={year} AND t.revision=0;
UPDATE payroll_tax_openings SET version=1 WHERE company_id='{company}' AND id IN(SELECT opening_id FROM payroll_load_sources);
INSERT INTO payroll_tax_opening_revisions(company_id,opening_id,revision,status,terms,prepared_by,verified_by,actor_id,reason,recorded_at)
SELECT '{company}',s.opening_id,1,'VERIFIED',t.terms,t.prepared_by,t.verified_by,t.verified_by,'Fictional verified template copy',now()
FROM payroll_load_sources s CROSS JOIN payroll_tax_opening_revisions t JOIN payroll_tax_openings h ON h.company_id=t.company_id AND h.id=t.opening_id
WHERE h.company_id='{company}' AND h.employment_id='{first}' AND h.tax_year={year} AND t.revision=1;
COMMIT;
ANALYZE;
""")
