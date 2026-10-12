-- Pre-existing fictional accepted revisions for browser retirement acceptance.
-- Applied only by the disposable database owner. Triggers and constraints stay enabled.
-- This fixture represents stored metadata; it does not exercise upload or scanning.
BEGIN;
DO $$
DECLARE
    company uuid := 'b0000000-0000-4000-8000-000000000023';
    person uuid := 'c0000000-0000-4000-8000-000000000023';
    employee uuid := 'd0000000-0000-4000-8000-000000000023';
    document uuid := 'e0000000-0000-4000-8000-000000000023';
    policy uuid := 'f0000000-0000-4000-8000-000000000023';
    operator uuid := 'a0000000-0000-4000-8000-000000000023';
    author uuid;
    revision uuid;
    job uuid;
    chunk uuid;
    attempt uuid;
    sequence integer;
    content bytea := convert_to(E'%PDF-1.4\n%%EOF\n', 'UTF8');
    content_digest text;
    original_at timestamptz := clock_timestamp() - interval '3 days';
    fixture_archive_time timestamptz := clock_timestamp() - interval '2 days';
BEGIN
    IF current_database() <> 'hris_browser' OR current_user <> 'fixture_migrator' THEN
        RAISE EXCEPTION 'Retirement fixture requires its disposable browser database';
    END IF;
    SELECT id INTO STRICT author FROM accounts WHERE email = 'browser-admin@example.invalid';
    IF NOT EXISTS (SELECT 1 FROM accounts WHERE id = operator AND email = 'browser-retirement-operator@example.invalid') THEN
        RAISE EXCEPTION 'Retirement fixture account is missing';
    END IF;
    PERFORM set_config('hris.company_id', company::text, true);
    PERFORM set_config('hris.actor_id', author::text, true);
    content_digest := encode(sha256(content), 'hex');

    INSERT INTO companies(id, code, name, timezone, created_at)
    VALUES (company, 'RETIREMENTFIXTURE', 'Retirement Browser Fixture', 'Asia/Jakarta', original_at);
    INSERT INTO company_memberships(company_id, account_id) VALUES (company, operator);
    INSERT INTO membership_permissions(company_id, account_id, permission)
    VALUES (company, operator, 'company.read'), (company, operator, 'documents.retention');

    INSERT INTO persons(id, owner_company_id, legal_name, nationality, created_at)
    VALUES (person, company, 'Retirement Fixture Employee', 'ID', original_at);
    INSERT INTO person_profile_revisions(person_id, revision, owner_company_id, legal_name, nationality, actor_id, reason, recorded_at)
    VALUES (person, 0, company, 'Retirement Fixture Employee', 'ID', author, 'Initial fixture profile', original_at);
    INSERT INTO employments(company_id, id, person_id, employee_number, created_at)
    VALUES (company, employee, person, 'RETIREMENT-001', original_at);
    INSERT INTO employment_revisions(company_id, employment_id, revision, effective_from, contract_kind, start_date, status, actor_id, reason, recorded_at)
    VALUES (company, employee, 0, original_at::date, 'PERMANENT', original_at::date, 'ACTIVE', author, 'Initial fixture employment', original_at);

    INSERT INTO documents(company_id, id, employment_id, title, classification, created_by, created_at)
    VALUES (company, document, employee, 'Archived fixture record', 'PERSONAL', author, original_at);

    FOR sequence IN 1..2 LOOP
        revision := CASE sequence
            WHEN 1 THEN 'e0000000-0000-4000-8000-000000000031'::uuid
            ELSE 'e0000000-0000-4000-8000-000000000032'::uuid END;
        job := gen_random_uuid();
        chunk := gen_random_uuid();
        attempt := gen_random_uuid();

        INSERT INTO document_revisions(company_id, id, document_id, revision_no, file_name, media_type,
            expected_bytes, expected_sha256, created_by, created_at, expires_at, reason)
        VALUES (company, revision, document, sequence, 'record.pdf', 'application/pdf', octet_length(content),
            content_digest, author, original_at, original_at + interval '1 day', 'Fixture upload');
        UPDATE documents SET revision_count = revision_count + 1, version = version + 1
        WHERE company_id = company AND id = document;

        INSERT INTO document_upload_chunks(company_id, revision_id, id, ordinal, byte_offset, byte_count, sha256, created_by)
        VALUES (company, revision, chunk, 1, 0, octet_length(content), content_digest, author);
        INSERT INTO document_upload_attempts(company_id, id, revision_id, chunk_id, object_key, byte_count, created_at)
        VALUES (company, attempt, revision, chunk, company::text || '/' || revision::text || '/' || attempt::text,
            octet_length(content), original_at);
        UPDATE document_upload_chunks SET status = 'COMMITTED', attempts = 1, current_attempt_id = attempt,
            object_key = company::text || '/' || revision::text || '/' || attempt::text,
            etag = 'fixture-etag', committed_version = 1
        WHERE company_id = company AND revision_id = revision AND id = chunk;
        UPDATE document_revisions SET uploaded_bytes = octet_length(content), version = version + 1
        WHERE company_id = company AND id = revision;

        INSERT INTO background_jobs(id, company_id, actor_id, kind, operation_id, request, authenticated_at,
            credential_version, correlation_id, created_at, status, available_at, attempts,
            completed_items, total_items, finished_at, version)
        VALUES (job, company, author, 'DOCUMENT_VALIDATE', gen_random_uuid(), jsonb_build_object('revisionId', revision),
            original_at, 0, gen_random_uuid(), original_at, 'SUCCEEDED', original_at, 1, 1, 1,
            original_at + interval '1 hour', 2);
        INSERT INTO document_validation_attempts(company_id, revision_id, job_id, attempt, actor_id, created_at, reason)
        VALUES (company, revision, job, 1, author, original_at, 'Fixture validation');
        UPDATE document_revisions SET status = 'VALIDATING', validation_job_id = job, validation_attempts = 1, version = version + 1
        WHERE company_id = company AND id = revision;
        UPDATE document_revisions SET status = 'READY', content_bytes = expected_bytes,
            content_sha256 = expected_sha256, detected_media_type = media_type, scan_clean = true,
            scanner_version = 'fixture-inspection', validated_at = original_at + interval '1 hour', version = version + 1
        WHERE company_id = company AND id = revision;
        UPDATE documents SET current_revision_id = revision, version = version + 1
        WHERE company_id = company AND id = document;
    END LOOP;

    INSERT INTO document_retention_policies(company_id, id, classification, version)
    VALUES (company, policy, 'PERSONAL', 0);
    INSERT INTO document_retention_policy_revisions(company_id, policy_id, version, retention_days, actor_id, recorded_at, reason)
    VALUES (company, policy, 0, 1, author, original_at, 'Fixture retention policy');
    INSERT INTO document_retention_states(company_id, document_id, version) VALUES (company, document, 0);
    INSERT INTO document_retention_changes(company_id, document_id, version, kind, legal_hold,
        archived_at, policy_id, policy_version, retention_days, eligible_at, actor_id, recorded_at, reason)
    VALUES (company, document, 0, 'ARCHIVE', false, fixture_archive_time, policy, 0, 1,
        fixture_archive_time + interval '24 hours', author, fixture_archive_time, 'Pre-existing fictional archive');
END $$;
COMMIT;
