-- Fictional accounts for independent browser scenarios in the owned disposable database.
-- Copy only the bootstrap fixture's known password hash; MFA and companies stay independent.
BEGIN;
INSERT INTO accounts(id, email, display_name, password_hash)
SELECT 'a0000000-0000-4000-8000-000000000001',
       'browser-approval-admin@example.invalid', 'Browser Approval Admin', password_hash
FROM accounts WHERE email = 'browser-admin@example.invalid'
ON CONFLICT DO NOTHING;

INSERT INTO platform_permissions(account_id, permission)
SELECT 'a0000000-0000-4000-8000-000000000001', permission
FROM platform_permissions
WHERE account_id = (SELECT id FROM accounts WHERE email = 'browser-admin@example.invalid')
ON CONFLICT DO NOTHING;

INSERT INTO accounts(id, email, display_name)
VALUES ('a0000000-0000-4000-8000-000000000002', 'browser-delegate@example.invalid', 'Browser Delegate')
ON CONFLICT DO NOTHING;

INSERT INTO accounts(id, email, display_name, password_hash)
SELECT fixture.id::uuid, fixture.email, fixture.name, bootstrap.password_hash
FROM accounts bootstrap
CROSS JOIN (VALUES
  ('a0000000-0000-4000-8000-000000000003', 'browser-leave-admin@example.invalid', 'Browser Leave Admin'),
  ('a0000000-0000-4000-8000-000000000004', 'browser-leave-reviewer@example.invalid', 'Browser Leave Reviewer'),
  ('a0000000-0000-4000-8000-000000000005', 'browser-policy-admin@example.invalid', 'Browser Policy Admin'),
  ('a0000000-0000-4000-8000-000000000006', 'browser-balance-admin@example.invalid', 'Browser Balance Admin'),
  ('a0000000-0000-4000-8000-000000000007', 'browser-binding-admin@example.invalid', 'Browser Binding Admin'),
  ('a0000000-0000-4000-8000-000000000008', 'browser-binding-employee@example.invalid', 'Browser Binding Employee'),
  ('a0000000-0000-4000-8000-000000000009', 'browser-transfer-admin@example.invalid', 'Browser Transfer Admin'),
  ('a0000000-0000-4000-8000-000000000010', 'browser-transfer-employee@example.invalid', 'Browser Transfer Employee'),
  ('a0000000-0000-4000-8000-000000000011', 'browser-workforce-admin@example.invalid', 'Browser Workforce Admin'),
  ('a0000000-0000-4000-8000-000000000012', 'browser-holiday-admin@example.invalid', 'Browser Holiday Admin'),
  ('a0000000-0000-4000-8000-000000000013', 'browser-calendar-admin@example.invalid', 'Browser Calendar Admin'),
  ('a0000000-0000-4000-8000-000000000014', 'browser-attendance-admin@example.invalid', 'Browser Attendance Admin'),
  ('a0000000-0000-4000-8000-000000000015', 'browser-attendance-employee@example.invalid', 'Browser Attendance Employee'),
  ('a0000000-0000-4000-8000-000000000016', 'browser-import-admin@example.invalid', 'Browser Import Admin'),
  ('a0000000-0000-4000-8000-000000000017', 'browser-client-policy-admin@example.invalid', 'Browser Client Policy Admin'),
  ('a0000000-0000-4000-8000-000000000018', 'browser-document-admin@example.invalid', 'Browser Document Admin'),
  ('a0000000-0000-4000-8000-000000000019', 'browser-retention-admin@example.invalid', 'Browser Retention Admin'),
  ('a0000000-0000-4000-8000-000000000020', 'browser-retention-operator@example.invalid', 'Browser Retention Operator'),
  ('a0000000-0000-4000-8000-000000000021', 'browser-document-lifecycle-admin@example.invalid', 'Browser Document Lifecycle Admin'),
  ('a0000000-0000-4000-8000-000000000022', 'browser-document-lifecycle-operator@example.invalid', 'Browser Document Lifecycle Operator'),
  ('a0000000-0000-4000-8000-000000000023', 'browser-retirement-operator@example.invalid', 'Browser Retirement Operator'),
  ('a0000000-0000-4000-8000-000000000024', 'browser-overtime-admin@example.invalid', 'Browser Overtime Admin'),
  ('a0000000-0000-4000-8000-000000000025', 'browser-overtime-reviewer@example.invalid', 'Browser Overtime Reviewer'),
  ('a0000000-0000-4000-8000-000000000026', 'browser-overtime-employee@example.invalid', 'Browser Overtime Employee')
) AS fixture(id, email, name)
WHERE bootstrap.email = 'browser-admin@example.invalid'
ON CONFLICT DO NOTHING;

INSERT INTO platform_permissions(account_id, permission)
SELECT fixture.account_id::uuid, permission
FROM platform_permissions
CROSS JOIN (VALUES
  ('a0000000-0000-4000-8000-000000000003'),
  ('a0000000-0000-4000-8000-000000000005'),
  ('a0000000-0000-4000-8000-000000000006'),
  ('a0000000-0000-4000-8000-000000000007'),
  ('a0000000-0000-4000-8000-000000000009'),
  ('a0000000-0000-4000-8000-000000000011'),
  ('a0000000-0000-4000-8000-000000000012'),
  ('a0000000-0000-4000-8000-000000000013'),
  ('a0000000-0000-4000-8000-000000000014'),
  ('a0000000-0000-4000-8000-000000000016'),
  ('a0000000-0000-4000-8000-000000000017'),
  ('a0000000-0000-4000-8000-000000000018'),
  ('a0000000-0000-4000-8000-000000000019'),
  ('a0000000-0000-4000-8000-000000000021'),
  ('a0000000-0000-4000-8000-000000000024')
) AS fixture(account_id)
WHERE platform_permissions.account_id = (SELECT id FROM accounts WHERE email = 'browser-admin@example.invalid')
ON CONFLICT DO NOTHING;
COMMIT;
