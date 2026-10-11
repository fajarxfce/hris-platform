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
secure refresh, device-session management and remote logout, company selection, current access, product flavors, localizable API
problems, restrained navigation transitions, self-profile/assignment reads, and an
encrypted, synchronized employee inbox with durable read/acknowledgement actions are connected in
[the mobile application](https://github.com/fajarxfce/hris-mobile/commit/3d4048b).
Automated UI/API checks and the Android dev build passed. Android 16 device checks
covered password sign-in, company isolation, profiles, inbox reading, offline
content, process restoration, and queued acknowledgement recovery. Native MFA,
external providers and the employee workflows below retain their own acceptance work.

## Remaining delivery sequence

| Area | Remaining end-to-end work |
| --- | --- |
| Mobile identity | Native OIDC handoff; physical MFA and device-session workflow validation |
| Mobile foundation | Offline company admission, additional business replicas and durable outbox, availability, background restoration, deep links and push registration. The encrypted inbox and bounded HRIS sync client are implemented. |
| People | Dashboard company transfers; scheduled lifecycle execution. Initial employee/account binding is implemented and verified with native self-profile access. |
| Workforce | Dashboard shifts, schedules, rosters, holidays, attendance reviews/corrections, overtime and closing; bulk/reset and late corrections; mobile calendar, verified/offline attendance and overtime requests |
| Leave | Dashboard request creation, team calendar, entitlement batches and year closing; scheduled accrual/expiry; mobile balances, requests, evidence and cancellation |
| Documents | Dashboard document/upload/processing/retention workflows; mobile scoped resumable upload/download and evidence acquisition |
| Expenses | Dashboard policies, claims, review, payment/reconciliation and corrections; mobile claims, receipts, submission and payment progress |
| Payroll | Dashboard policies, compensation, inputs, calculation, review, finalization, payslips, payments and amendments; mobile finalized payslips and downloads |
| Communications | Mobile push hints and navigation. Dashboard audience groups, drafts, bounded named audience selection, publication review, preview, scheduling, cancellation/recovery and archival, and the mobile inbox with durable read/acknowledgement commands are implemented. |
| Identity administration | Dashboard platform users, role/membership changes, invitations, provider bindings and sessions; company membership and grant review is implemented |
| Reporting | Attendance, leave, overtime, expenses and payroll reports, authorized bounded exports and export history |
| Operations | Integration status, backup/restore verification, reference-workload measurements, deployment and complete run instructions |

Cross-company isolation, lost-response replay, denied/revoked access, offline
recovery, cancellation, late results, and resource disposal are acceptance
conditions for affected changes. Device checks are recorded separately from
builds and automated tests. Production signing, external provider registration,
releases, and live distribution need their actual configuration and authorization;
they cannot be represented as completed by a fixture.
