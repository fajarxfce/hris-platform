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
  ('a0000000-0000-4000-8000-000000000008', 'browser-binding-employee@example.invalid', 'Browser Binding Employee')
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
  ('a0000000-0000-4000-8000-000000000007')
) AS fixture(account_id)
WHERE platform_permissions.account_id = (SELECT id FROM accounts WHERE email = 'browser-admin@example.invalid')
ON CONFLICT DO NOTHING;
COMMIT;
