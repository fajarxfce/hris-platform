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
The [request creation flow](https://github.com/fajarxfce/hris-mobile/commit/7aa9bcd)
adds effective leave types, date/portion selection, server-reviewed calculations
and durable submission. API 35 checks covered excluded dates, balance reservations,
confirmation with transport blocked, process restoration, explicit delivery and
withdrawal. The validated update was installed on the phone without launching it.
The [document evidence selector](https://github.com/fajarxfce/hris-mobile/commit/c2a9d41)
adds up to three owned, published revisions, including for leave types requiring
evidence. API 35 acceptance verified original revision metadata, balance reservation
and withdrawal. [Document uploads](https://github.com/fajarxfce/hris-mobile/commit/d4cdd58)
now add encrypted local file import, employee-confirmed metadata, resumable delivery,
server inspection status and explicit retry/cancellation. API 35 acceptance uploaded
a PDF through the real API and restored its completion after process restart.
The [published document browser](https://github.com/fajarxfce/hris-mobile/commit/e991035)
adds employment/category filters, authenticated resumable downloads and native
Save copy. API 35 acceptance verified a PDF against its original SHA-256, picker
cancellation and encrypted completion after process restart. Resource access is
rechecked after destination selection and before export completion.
The [mobile overtime view](https://github.com/fajarxfce/hris-mobile/commit/29eed7a)
adds owned requests with month/status filters, planned and actual time, frozen
schedule evidence, approval progress and paged activity. API 35 acceptance covered
overnight dates, encrypted list/detail restoration after process restart offline,
and explicit recovery. The validated update was installed on the phone with
install-only delivery. The [native overtime workflows](https://github.com/fajarxfce/hris-mobile/commit/b58caf8)
now add reviewed-calendar planning, actual-time submission and withdrawal. Immutable
encrypted commands retain original input and operation keys across response loss,
offline process restoration and explicit replay. API 35 acceptance verified a real
plan, actual time, cancelled approval and distinct versioned history entries; the
validated APK was installed on the phone without opening it.
The [native payslip pages](https://github.com/fajarxfce/hris-mobile/commit/46d8b2f)
now provide annual history, published amounts, payment progress and English/Indonesian
PDF saving. Every read requests employee SELF scope. API 35 acceptance verified
year filters, native file-dialog cancellation, the exported PDF's SHA-256,
encrypted list/detail restoration after background process death offline, and
explicit online recovery. The validated APK was installed on the phone with
install-only delivery.
The [native expense pages](https://github.com/fajarxfce/hris-mobile/commit/a213e1a)
now provide owned claim history, month/status filters, draft and submission details,
receipt metadata, approval activity and payment progress. Encrypted list/detail
snapshots retain bounded offline reads. API 35 acceptance verified approval without
payment, both themes, 200% text, offline restoration after background process death
and explicit recovery. The validated APK was installed on the phone with install-only
delivery.
The [native expense editor](https://github.com/fajarxfce/hris-mobile/commit/4503540)
adds draft creation/editing, dated categories, cost centers, published receipt
selection and durable save recovery. API 35 acceptance verified exact amounts,
competing revisions, explicit reload, background input retention and reconnection
without discarding input. Large amounts remain readable at 200% text. The validated
APK was delivered with install-only.
The [native expense actions](https://github.com/fajarxfce/hris-mobile/commit/1388947)
add submission, cancellation and withdrawal with reviewed revisions and retained
command recovery. API 35 acceptance verified offline process restoration, a single
replayed submission, cancelled approval, draft cancellation and versioned history.
Dark appearance and 200% text were inspected; the validated APK was installed on the
phone without opening the app.
The [native receipt export](https://github.com/fajarxfce/hris-mobile/commit/0c880a6)
saves submitted evidence from claim detail through the system file picker. It uses
SELF expense authority independently of document-catalog permissions, bounded
sequential reads and a final digest check. API 35 acceptance verified picker
cancellation/retry, saved bytes against the server hash, large text, dark appearance
and return from Settings. The validated APK was delivered with install-only.
Automated UI/API checks and the Android dev build passed. Android 16 device checks
covered password sign-in, company isolation, profiles, inbox reading, offline
content, offline process restoration with company isolation, queued acknowledgement
recovery, company availability, MFA enrollment/verification and recovery-code login,
and device-session revocation. External providers and the employee workflows below
retain their own acceptance work.

Administrative Documents now covers employee folders, document/revision history,
new and replacement uploads, resumable browser delivery, authorized downloads,
validation/retry, explicit cancellation, retention-policy configuration,
archive/restore, legal hold/release, and accepted-content retirement. Independent
operators and immutable lifecycle history were verified against the API/database.
Upload and validation acceptance used private storage and the development worker;
retirement browser acceptance used pre-existing fictional archive metadata in an
owned disposable database. Its historical and current revisions, lost-response
replay and competing hold were exercised without modifying development records.

## Remaining delivery sequence

| Area | Remaining end-to-end work |
| --- | --- |
| Mobile identity | Native OIDC handoff and external-provider acceptance. Android MFA and device-session workflows are validated; iOS physical acceptance remains separate. |
| Mobile foundation | Additional business replicas and durable commands, background restoration, deep links and push registration. Bounded encrypted company admission, company availability, the employee inbox and HRIS sync client are implemented. |
| People | Scheduled lifecycle execution. Dashboard company transfers and initial employee/account binding are implemented and verified with native employee access. |
| Workforce | Bulk/reset and late corrections; physical location acceptance. Shift templates, company holidays, calendars, weekly scheduling, daily rosters, employee attendance evidence/review/corrections and their history, company-wide exception review queues, overtime planning with reviewed calendars, directories/details/history, actual-time submission, independent review and withdrawal, dashboard monthly closing/recovery with published employee results, and mobile work calendars, overtime planning/actual time/withdrawal with durable recovery, and online/offline capture are implemented and verified against the real API. |
| Leave | Dashboard request creation, team calendar, entitlement batches and year closing; scheduled accrual/expiry. Mobile balances, filtered history, request details, submission with published document evidence, and durable withdrawal/cancellation flows are implemented and verified against the development API. |
| Expenses | Backend own-employment discovery and strictly self-scoped reads, mobile claim history/details, approval/payment progress, bounded offline views, draft authoring, published receipt selection/export, submission, cancellation, withdrawal and durable command recovery are implemented. Dashboard policy administration, claim directories/current details/history, immutable submitted evidence, receipt downloads, independent review decisions, finance batch directories/details, bank outcomes/activity, authorized CSV worksheets, payable selection and reviewed bank preparation, guarded release/cancellation, replacement instructions and reconciliation entry with original-command recovery are implemented. Remaining: dashboard draft authoring, submission, withdrawal and returned-claim correction |
| Payroll | Dashboard policies, compensation, inputs, calculation, review, finalization, payslips, payments and amendments. Native finalized payslips, payment progress, bounded offline views and authorized PDF saving are implemented. |
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
