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
  ('a0000000-0000-4000-8000-000000000039', 'browser-payroll-policy-operator@example.invalid', 'Browser Payroll Policy Operator'),
  ('a0000000-0000-4000-8000-000000000040', 'browser-compensation-admin@example.invalid', 'Browser Compensation Admin'),
  ('a0000000-0000-4000-8000-000000000041', 'browser-compensation-operator@example.invalid', 'Browser Compensation Operator'),
  ('a0000000-0000-4000-8000-000000000042', 'browser-payroll-period-admin@example.invalid', 'Browser Payroll Period Admin'),
  ('a0000000-0000-4000-8000-000000000043', 'browser-payroll-period-operator@example.invalid', 'Browser Payroll Period Operator'),
  ('a0000000-0000-4000-8000-000000000044', 'browser-payroll-input-admin@example.invalid', 'Browser Payroll Input Admin'),
  ('a0000000-0000-4000-8000-000000000045', 'browser-payroll-input-preparer@example.invalid', 'Browser Payroll Input Preparer'),
  ('a0000000-0000-4000-8000-000000000046', 'browser-payroll-input-reviewer@example.invalid', 'Browser Payroll Input Reviewer'),
  ('a0000000-0000-4000-8000-000000000047', 'browser-tax-opening-admin@example.invalid', 'Browser Tax Opening Admin'),
  ('a0000000-0000-4000-8000-000000000048', 'browser-tax-opening-preparer@example.invalid', 'Browser Tax Opening Preparer'),
  ('a0000000-0000-4000-8000-000000000049', 'browser-tax-opening-reviewer@example.invalid', 'Browser Tax Opening Reviewer'),
  ('a0000000-0000-4000-8000-000000000050', 'browser-calculation-admin@example.invalid', 'Browser Calculation Admin'),
  ('a0000000-0000-4000-8000-000000000051', 'browser-calculation-preparer@example.invalid', 'Browser Calculation Preparer'),
  ('a0000000-0000-4000-8000-000000000052', 'browser-calculation-reviewer@example.invalid', 'Browser Calculation Reviewer'),
  ('a0000000-0000-4000-8000-000000000053', 'browser-payroll-final-reviewer@example.invalid', 'Browser Payroll Final Reviewer'),
  ('a0000000-0000-4000-8000-000000000054', 'browser-payroll-finalizer@example.invalid', 'Browser Payroll Finalizer'),
  ('a0000000-0000-4000-8000-000000000055', 'browser-offboarding-admin@example.invalid', 'Browser Offboarding Admin'),
  ('a0000000-0000-4000-8000-000000000056', 'browser-offboarding-employee@example.invalid', 'Browser Offboarding Employee'),
  ('a0000000-0000-4000-8000-000000000057', 'browser-onboarding-admin@example.invalid', 'Browser Onboarding Admin'),
  ('a0000000-0000-4000-8000-000000000058', 'browser-onboarding-coordinator@example.invalid', 'Browser Onboarding Coordinator'),
  ('a0000000-0000-4000-8000-000000000059', 'browser-leave-batches-admin@example.invalid', 'Browser Leave Batches Admin'),
  ('a0000000-0000-4000-8000-000000000060', 'browser-leave-calendar-admin@example.invalid', 'Browser Leave Calendar Admin'),
  ('a0000000-0000-4000-8000-000000000061', 'browser-leave-schedule-admin@example.invalid', 'Browser Leave Schedule Admin'),
  ('a0000000-0000-4000-8000-000000000062', 'browser-carry-operator@example.invalid', 'Browser Carry Operator'),
  ('a0000000-0000-4000-8000-000000000063', 'browser-role-admin@example.invalid', 'Browser Role Admin'),
  ('a0000000-0000-4000-8000-000000000064', 'browser-role-member@example.invalid', 'Browser Role Member'),
  ('a0000000-0000-4000-8000-000000000065', 'browser-membership-admin@example.invalid', 'Browser Membership Admin'),
  ('a0000000-0000-4000-8000-000000000066', 'browser-membership-member@example.invalid', 'Browser Membership Member'),
  ('a0000000-0000-4000-8000-000000000067', 'browser-platform-admin@example.invalid', 'Browser Platform Admin'),
  ('a0000000-0000-4000-8000-000000000068', 'browser-platform-member@example.invalid', 'Browser Platform Member'),
  ('a0000000-0000-4000-8000-000000000069', 'browser-assignment-admin@example.invalid', 'Browser Assignment Admin'),
  ('a0000000-0000-4000-8000-000000000070', 'browser-assignment-member@example.invalid', 'Browser Assignment Member'),
  ('a0000000-0000-4000-8000-000000000071', 'browser-invitation-admin@example.invalid', 'Browser Invitation Admin'),
  ('a0000000-0000-4000-8000-000000000072', 'browser-oidc-admin@example.invalid', 'Browser SSO Admin'),
  ('a0000000-0000-4000-8000-000000000073', 'browser-oidc-member@example.invalid', 'Browser SSO Member'),
  ('a0000000-0000-4000-8000-000000000074', 'browser-session-owner@example.invalid', 'Browser Session Owner'),
  ('a0000000-0000-4000-8000-000000000075', 'browser-session-other@example.invalid', 'Browser Other Session Owner'),
  ('a0000000-0000-4000-8000-000000000076', 'browser-session-admin@example.invalid', 'Browser Session Admin'),
  ('a0000000-0000-4000-8000-000000000077', 'browser-managed-session-owner@example.invalid', 'Browser Managed Session Owner'),
  ('a0000000-0000-4000-8000-000000000078', 'browser-managed-session-other@example.invalid', 'Browser Other Managed Session Owner'),
  ('a0000000-0000-4000-8000-000000000079', 'browser-roster-bulk-admin@example.invalid', 'Browser Roster Bulk Admin'),
  ('a0000000-0000-4000-8000-000000000080', 'browser-leave-report-admin@example.invalid', 'Browser Leave Report Admin'),
  ('a0000000-0000-4000-8000-000000000081', 'browser-leave-report-reviewer@example.invalid', 'Browser Leave Report Reviewer'),
  ('a0000000-0000-4000-8000-000000000082', 'browser-expense-report-admin@example.invalid', 'Browser Expense Report Admin'),
  ('a0000000-0000-4000-8000-000000000083', 'browser-expense-report-reviewer@example.invalid', 'Browser Expense Report Reviewer'),
  ('a0000000-0000-4000-8000-000000000084', 'browser-payroll-cost-reader@example.invalid', 'Browser Payroll Cost Reader'),
  ('a0000000-0000-4000-8000-000000000085', 'browser-attendance-closing-reader@example.invalid', 'Browser Attendance Closing Reader'),
  ('a0000000-0000-4000-8000-000000000086', 'browser-report-catalogue-admin@example.invalid', 'Browser Report Catalogue Admin'),
  ('a0000000-0000-4000-8000-000000000087', 'browser-report-catalogue-reader@example.invalid', 'Browser Report Catalogue Reader'),
  ('a0000000-0000-4000-8000-000000000088', 'browser-report-filter-admin@example.invalid', 'Browser Report Filter Admin'),
  ('a0000000-0000-4000-8000-000000000089', 'browser-report-filter-reader@example.invalid', 'Browser Report Filter Reader'),
  ('a0000000-0000-4000-8000-000000000090', 'browser-report-export-admin@example.invalid', 'Browser Report Export Admin'),
  ('a0000000-0000-4000-8000-000000000091', 'browser-report-export-reader@example.invalid', 'Browser Report Export Reader')
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
  ('a0000000-0000-4000-8000-000000000038'),
  ('a0000000-0000-4000-8000-000000000040'),
  ('a0000000-0000-4000-8000-000000000042'),
  ('a0000000-0000-4000-8000-000000000044'),
  ('a0000000-0000-4000-8000-000000000047'),
  ('a0000000-0000-4000-8000-000000000050'),
  ('a0000000-0000-4000-8000-000000000055'),
  ('a0000000-0000-4000-8000-000000000057'),
  ('a0000000-0000-4000-8000-000000000059'),
  ('a0000000-0000-4000-8000-000000000060'),
  ('a0000000-0000-4000-8000-000000000061'),
  ('a0000000-0000-4000-8000-000000000063'),
  ('a0000000-0000-4000-8000-000000000065'),
  ('a0000000-0000-4000-8000-000000000067'),
  ('a0000000-0000-4000-8000-000000000069'),
  ('a0000000-0000-4000-8000-000000000071'),
  ('a0000000-0000-4000-8000-000000000072'),
  ('a0000000-0000-4000-8000-000000000076'),
  ('a0000000-0000-4000-8000-000000000079'),
  ('a0000000-0000-4000-8000-000000000080'),
  ('a0000000-0000-4000-8000-000000000082'),
  ('a0000000-0000-4000-8000-000000000086'),
  ('a0000000-0000-4000-8000-000000000088'),
  ('a0000000-0000-4000-8000-000000000090')
) AS fixture(account_id)
WHERE platform_permissions.account_id = (SELECT id FROM accounts WHERE email = 'browser-admin@example.invalid')
ON CONFLICT DO NOTHING;
COMMIT;
