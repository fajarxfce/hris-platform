-- Fictional retained carry and a failed initial attempt, before the worker starts.
-- Optional validity is still internal; the test exercises public recovery, not admission.
-- Every trigger and constraint remains enabled in this disposable database.
BEGIN;
DO $$
DECLARE
    company uuid := 'b0000000-0000-4000-8000-000000000062';
    person uuid := 'c0000000-0000-4000-8000-000000000062';
    employee uuid := 'd0000000-0000-4000-8000-000000000062';
    closing uuid := 'e0000000-0000-4000-8000-000000000062';
    expiry uuid := 'e0000000-0000-4000-8000-000000000063';
    leave_type uuid := 'f0000000-0000-4000-8000-000000000062';
    job uuid := 'f0000000-0000-4000-8000-000000000063';
    operator uuid := 'a0000000-0000-4000-8000-000000000062';
    carry_year integer := extract(year FROM current_date)::integer - 1;
    original_at timestamptz;
    posted_at timestamptz;
    deadline timestamptz;
    policy jsonb;
BEGIN
    IF current_database() <> 'hris_browser' OR current_user <> 'fixture_migrator' THEN
        RAISE EXCEPTION 'Carry fixture requires its disposable browser database';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM accounts WHERE id = operator AND email = 'browser-carry-operator@example.invalid') THEN
        RAISE EXCEPTION 'Carry fixture account is missing';
    END IF;
    PERFORM set_config('hris.company_id', company::text, true);
    PERFORM set_config('hris.actor_id', operator::text, true);
    original_at := make_date(carry_year - 1, 1, 1)::timestamp AT TIME ZONE 'Asia/Jakarta';
    posted_at := make_date(carry_year, 1, 5)::timestamp AT TIME ZONE 'Asia/Jakarta';
    deadline := make_date(carry_year, 4, 1)::timestamp AT TIME ZONE 'Asia/Jakarta';
    policy := jsonb_build_object(
        'name', 'Retained annual leave', 'paid', true, 'allowPartialDays', true,
        'minServiceMonths', 0, 'allowedContracts', jsonb_build_array('PERMANENT'),
        'maxRequestDays', 12, 'attachmentRequired', false,
        'accrual', jsonb_build_object('frequency', 'ANNUAL', 'halfDaysPerPeriod', 24,
            'carryLimitHalfDays', 6, 'carryValidityMonths', 3));

    INSERT INTO companies(id, code, name, timezone, created_at)
    VALUES (company, 'CARRYFIXTURE', 'Carry Browser Fixture', 'Asia/Jakarta', original_at);
    INSERT INTO company_memberships(company_id, account_id) VALUES (company, operator);
    INSERT INTO membership_permissions(company_id, account_id, permission)
    VALUES (company, operator, 'company.read'), (company, operator, 'leave.read'),
        (company, operator, 'leave.year.close'), (company, operator, 'jobs.read');
    INSERT INTO persons(id, owner_company_id, legal_name, nationality, created_at)
    VALUES (person, company, 'Carry Fixture Employee', 'ID', original_at);
    INSERT INTO person_profile_revisions(person_id, revision, owner_company_id, legal_name, nationality, actor_id, reason, recorded_at)
    VALUES (person, 0, company, 'Carry Fixture Employee', 'ID', operator, 'Initial fixture profile', original_at);
    INSERT INTO employments(company_id, id, person_id, employee_number, created_at)
    VALUES (company, employee, person, 'CARRY-001', original_at);
    INSERT INTO employment_revisions(company_id, employment_id, revision, effective_from, contract_kind, start_date, status, actor_id, reason, recorded_at)
    VALUES (company, employee, 0, original_at::date, 'PERMANENT', original_at::date, 'ACTIVE', operator, 'Initial fixture employment', original_at);
    INSERT INTO leave_types(company_id, id, code) VALUES(company, leave_type, 'ANNUAL');
    INSERT INTO leave_type_revisions(company_id, type_id, revision, effective_from, details, active, actor_id, reason, recorded_at)
    VALUES (company, leave_type, 0, original_at::date, policy, true, operator, 'Original carry policy', original_at);

    INSERT INTO leave_ledger(company_id, id, employment_id, type_id, balance_year, kind, source_id,
        available_delta, reserved_delta, consumed_delta, actor_id, recorded_at, reason)
    VALUES (company, gen_random_uuid(), employee, leave_type, carry_year - 1, 'ADJUSTMENT', gen_random_uuid(),
        6, 0, 0, operator, original_at, 'Fixture opening credit');
    INSERT INTO leave_year_closings(company_id, id, employment_id, type_id, balance_year, source_version,
        destination_version, available_half_days, consumed_half_days, carry_half_days, expire_half_days,
        policy_revision, policy_snapshot, actor_id, recorded_at, reason, carry_tracking, company_timezone)
    VALUES (company, closing, employee, leave_type, carry_year - 1, 1, 0, 6, 0, 6, 0, 0,
        jsonb_build_object('typeId', leave_type, 'code', 'ANNUAL', 'revision', 0, 'policy', policy),
        operator, posted_at, 'Year-end carry fixture', true, 'Asia/Jakarta');
    INSERT INTO leave_carry_buckets(company_id, id, employment_id, type_id, balance_year, initial_half_days,
        available_half_days, company_timezone, validity_months, valid_through, expires_at)
    VALUES (company, closing, employee, leave_type, carry_year, 6, 6,
        'Asia/Jakarta', 3, make_date(carry_year, 3, 31), deadline);
    INSERT INTO background_jobs(id, company_id, actor_id, kind, operation_id, request, authenticated_at,
        credential_version, correlation_id, created_at, total_items, scheduled_for, available_at)
    VALUES (job, company, operator, 'LEAVE_CARRY_EXPIRY', gen_random_uuid(), jsonb_build_object('expiryId', expiry),
        posted_at, 0, gen_random_uuid(), posted_at, 2, deadline, deadline);
    INSERT INTO leave_carry_expiries(company_id, id, source_kind, closing_id, employment_id, type_id,
        balance_year, company_timezone, valid_through, expires_at, actor_id, created_at, reason, total_employees, job_id)
    VALUES (company, expiry, 'CLOSING', closing, employee, leave_type, carry_year, 'Asia/Jakarta',
        make_date(carry_year, 3, 31), deadline, operator, posted_at, 'Year-end carry fixture', 1, job);
    INSERT INTO leave_carry_expiry_attempts(company_id, expiry_id, job_id, attempt, base_completed, started_at, reason)
    VALUES(company, expiry, job, 1, 0, posted_at, 'Year-end carry fixture');
    INSERT INTO leave_ledger(company_id, id, employment_id, type_id, balance_year, kind, source_id,
        available_delta, reserved_delta, consumed_delta, actor_id, recorded_at, reason)
    VALUES (company, gen_random_uuid(), employee, leave_type, carry_year - 1, 'CARRY_OUT', closing,
        -6, 0, 0, operator, posted_at, 'Year-end carry fixture'),
        (company, gen_random_uuid(), employee, leave_type, carry_year, 'CARRY_IN', closing,
        6, 0, 0, operator, posted_at, 'Year-end carry fixture');
    UPDATE leave_accounts SET closing_id = closing, version = version + 1
    WHERE company_id = company AND employment_id = employee AND type_id = leave_type AND leave_accounts.balance_year = carry_year - 1;
    UPDATE background_jobs SET status = 'FAILED', attempts = 5, finished_at = deadline + interval '1 hour',
        failure_code = 'job_attempts_exhausted', version = version + 1
    WHERE id = job AND company_id = company;
END $$;
COMMIT;
