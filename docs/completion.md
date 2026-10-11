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
[the mobile application](https://github.com/fajarxfce/hris-mobile/commit/bab8734).
An encrypted company admission also restores the selected workspace offline,
with bounded session/MFA lifetime and live access revalidation on foreground entry.
Company policy controls maintenance, minimum builds and module availability, with
fresh admission before inbox synchronization and command delivery.
The [employee work calendar](https://github.com/fajarxfce/hris-mobile/commit/cffb545)
now shows weekly shifts, roster overrides and days off, with a bounded encrypted
offline snapshot independent of People availability. API 24/API 35 emulator checks
covered week navigation, overnight work, company isolation and offline restoration.
Employee [attendance capture](https://github.com/fajarxfce/hris-mobile/commit/a871170)
now retains encrypted clock-in/out commands before submission, restores offline
captures after process restart and reuses original evidence on retry. API 35
emulator acceptance covered permission recovery, offline delivery and pending
review using simulated location. Home, Inbox, Employees and Account now provide stable mobile navigation; All
services opens its own page. The [monthly attendance view](https://github.com/fajarxfce/hris-mobile/commit/fce9c07)
shows read-only metrics and history with a month filter, while Home retains capture
and its durable queue. Employees has search and department filtering, and Inbox
shows the full account-owned unread total. API 35 checks covered encrypted monthly
offline restoration and explicit queued capture replay; the validated update was
installed on the phone without interacting with its UI.
The [mobile Leave page](https://github.com/fajarxfce/hris-mobile/commit/db842e5)
now provides annual balances, filtered request history and an encrypted offline
view. API 35 acceptance covered process restoration, online recovery, both
languages/themes and large text. Protected reads stay gated across pending and
failed retries; the validated update was installed on the phone without opening it.
Automated UI/API checks and the Android dev build passed. Android 16 device checks
covered password sign-in, company isolation, profiles, inbox reading, offline
content, offline process restoration with company isolation, queued acknowledgement
recovery, company availability, MFA enrollment/verification and recovery-code login,
and device-session revocation. External providers and the employee workflows below
retain their own acceptance work.

## Remaining delivery sequence

| Area | Remaining end-to-end work |
| --- | --- |
| Mobile identity | Native OIDC handoff and external-provider acceptance. Android MFA and device-session workflows are validated; iOS physical acceptance remains separate. |
| Mobile foundation | Additional business replicas and durable commands, background restoration, deep links and push registration. Bounded encrypted company admission, company availability, the employee inbox and HRIS sync client are implemented. |
| People | Scheduled lifecycle execution. Dashboard company transfers and initial employee/account binding are implemented and verified with native employee access. |
| Workforce | Dashboard company-wide exception queues, correction history, overtime and closing; bulk/reset and late corrections; mobile overtime requests and physical location acceptance. Shift templates, company holidays, calendars, weekly scheduling, daily rosters, employee attendance evidence/review/corrections, and mobile work calendars plus online/offline capture are implemented and verified against the real API. |
| Leave | Dashboard request creation, team calendar, entitlement batches and year closing; scheduled accrual/expiry; mobile request details, submission, evidence and cancellation. Mobile balances and filtered request history are implemented. |
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
