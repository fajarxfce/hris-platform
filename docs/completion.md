# End-to-end completion

The delivery scope includes the backend, administrative dashboard, and the
employee application in [HRIS Mobile](https://github.com/fajarxfce/hris-mobile).
The mobile repository is generated from Compose Fluent Starter and has its own
history. Reusable foundation corrections also return to the starter.

[Delivery records](delivery.md) describe implemented behavior and observed
validation. A generated module or route does not complete a capability. Each
workflow must enforce current scope, handle response loss and cancellation,
provide localized errors, and pass the relevant real API and UI checks.

Mobile native password sign-in, MFA enrollment/verification, recovery acknowledgement,
secure refresh, company selection, current access, product flavors, localizable API
problems, and restrained navigation transitions are connected in
[the mobile application](https://github.com/fajarxfce/hris-mobile/commit/7692ed7).
Automated UI/API checks and the Android dev build passed. Physical device validation
and the employee workflows below remain separate acceptance work.

## Remaining delivery sequence

| Area | Remaining end-to-end work |
| --- | --- |
| Mobile identity | Remote session revocation/management, native OIDC handoff, physical device validation |
| Mobile foundation | Encrypted company/account cache, HRIS sync and durable outbox, availability, background execution, deep links and push registration |
| People | Dashboard account binding and company transfers; scheduled lifecycle execution; mobile self profile and assignments |
| Workforce | Dashboard shifts, schedules, rosters, holidays, attendance reviews/corrections, overtime and closing; bulk/reset and late corrections; mobile calendar, verified/offline attendance and overtime requests |
| Leave | Dashboard request creation, team calendar, entitlement batches and year closing; scheduled accrual/expiry; mobile balances, requests, evidence and cancellation |
| Documents | Dashboard document/upload/processing/retention workflows; mobile scoped resumable upload/download and evidence acquisition |
| Expenses | Dashboard policies, claims, review, payment/reconciliation and corrections; mobile claims, receipts, submission and payment progress |
| Payroll | Dashboard policies, compensation, inputs, calculation, review, finalization, payslips, payments and amendments; mobile finalized payslips and downloads |
| Communications | Dashboard audience groups, drafts, previews, schedules, publication and recovery; mobile inbox, acknowledgement and push hints |
| Identity administration | Dashboard users, roles, memberships, invitations, provider bindings and sessions |
| Reporting | Attendance, leave, overtime, expenses and payroll reports, authorized bounded exports and export history |
| Operations | Integration status, backup/restore verification, reference-workload measurements, deployment and complete run instructions |

Cross-company isolation, lost-response replay, denied/revoked access, offline
recovery, cancellation, late results, and resource disposal are acceptance
conditions for affected changes. Device checks are recorded separately from
builds and automated tests. Production signing, external provider registration,
releases, and live distribution need their actual configuration and authorization;
they cannot be represented as completed by a fixture.
