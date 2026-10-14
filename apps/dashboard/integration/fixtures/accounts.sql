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
  ('a0000000-0000-4000-8000-000000000026', 'browser-overtime-employee@example.invalid', 'Browser Overtime Employee'),
  ('a0000000-0000-4000-8000-000000000027', 'browser-closing-admin@example.invalid', 'Browser Closing Admin'),
  ('a0000000-0000-4000-8000-000000000028', 'browser-closing-employee@example.invalid', 'Browser Closing Employee'),
  ('a0000000-0000-4000-8000-000000000029', 'browser-expense-admin@example.invalid', 'Browser Expense Admin'),
  ('a0000000-0000-4000-8000-000000000030', 'browser-expense-claim-admin@example.invalid', 'Browser Expense Claim Admin'),
  ('a0000000-0000-4000-8000-000000000031', 'browser-expense-claim-reviewer@example.invalid', 'Browser Expense Claim Reviewer'),
  ('a0000000-0000-4000-8000-000000000032', 'browser-expense-payment-admin@example.invalid', 'Browser Expense Payment Admin'),
  ('a0000000-0000-4000-8000-000000000033', 'browser-expense-payment-reviewer@example.invalid', 'Browser Expense Payment Reviewer'),
  ('a0000000-0000-4000-8000-000000000034', 'browser-expense-payment-maker@example.invalid', 'Browser Expense Payment Maker'),
  ('a0000000-0000-4000-8000-000000000035', 'browser-expense-payment-checker@example.invalid', 'Browser Expense Payment Checker'),
  ('a0000000-0000-4000-8000-000000000036', 'browser-leave-creation-admin@example.invalid', 'Browser Leave Creation Admin'),
  ('a0000000-0000-4000-8000-000000000037', 'browser-leave-creation-reviewer@example.invalid', 'Browser Leave Creation Reviewer'),
  ('a0000000-0000-4000-8000-000000000038', 'browser-payroll-policy-admin@example.invalid', 'Browser Payroll Policy Admin'),
  ('a0000000-0000-4000-8000-000000000039', 'browser-payroll-policy-operator@example.invalid', 'Browser Payroll Policy Operator')
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
  ('a0000000-0000-4000-8000-000000000024'),
  ('a0000000-0000-4000-8000-000000000027'),
  ('a0000000-0000-4000-8000-000000000029'),
  ('a0000000-0000-4000-8000-000000000030'),
  ('a0000000-0000-4000-8000-000000000032'),
  ('a0000000-0000-4000-8000-000000000036'),
  ('a0000000-0000-4000-8000-000000000038')
) AS fixture(account_id)
WHERE platform_permissions.account_id = (SELECT id FROM accounts WHERE email = 'browser-admin@example.invalid')
ON CONFLICT DO NOTHING;
COMMIT;
