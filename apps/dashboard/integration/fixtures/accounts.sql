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
COMMIT;
