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
The encrypted company selection restores its owned local workspace without an
age-based read limit. Saved views remain readable after freshness or credential
expiry; live authorization and operation reviews have separate checks. Foreground
revalidation retains the same workspace, while confirmed denials keep affected
reads gated through failed cleanup and retries. The [mobile read and sync model](https://github.com/fajarxfce/hris-mobile/blob/main/docs/local-first.md)
distinguishes existing snapshots from complete replicas: Inbox, Leave, Overtime, Workforce, Payroll, Expenses, published Documents, the coworker directory and private profile
collections consume change feeds and retain account-owned records
across sign-in, independently of the active request session. A replacement cursor
stages new data without hiding published facts. The coworker directory also supports
full local search, paging and department filters. API 35 acceptance reopened Home and monthly
attendance without connectivity after advancing the emulator clock by 73 hours.
Company policy controls maintenance, minimum builds and module availability, with
fresh admission before inbox synchronization and command delivery.
The [employee calendar and attendance replica](https://github.com/fajarxfce/hris-mobile/commit/d57f496)
retains encrypted attendance days, effective weekly assignments, roster overrides
and holidays independently of the displayed month/week. Local queries resolve
schedule precedence and overnight work without replacing previously acquired dates.
API 35 acceptance covered process restart offline, month/week navigation and a
73-hour clock advance with unchanged stored records. Shared persisted read denials
remain gated until a subsequent admitted acquisition succeeds. The
[action feedback cleanup](https://github.com/fajarxfce/hris-mobile/commit/90d376b)
also separates operation review from the lifetime of readable local data.
The [employee payslip replica](https://github.com/fajarxfce/hris-mobile/commit/5d5ac0d)
retains full owned payslips, indexed annual summaries and independently versioned
payment progress. Year/page/detail navigation queries local storage without
replacing another selection. Explicit two-collection sync publishes records and
cursor atomically, preserves facts during cursor replacement, and retains confirmed
read denials until admitted acquisition. API 35 acceptance covered offline process
restart, year selection, full details and a 73-hour clock advance with unchanged
records. The validated update was installed on the phone without opening it.
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
The [mobile Leave replica](https://github.com/fajarxfce/hris-mobile/commit/24d0d99)
stores balances, full requests and acquired activity pages per record, with indexed
local employment, year and status queries. Older activity acquisition resumes from
a stored per-request cursor without renewing action-review authority. API 35
acceptance covered offline upgrade, change-feed acquisition, local filters and
independent request details after process restart without connectivity. Protected
reads stay gated across pending and failed retries; the validated update was
installed on the phone without opening it. Older-page backfill and legacy detail
compatibility are exercised by automated SQLite and IndexedDB tests.
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
The [mobile Overtime replica](https://github.com/fajarxfce/hris-mobile/commit/9420cd2)
retains full owned requests and acquired activity per record, with local date/status
queries and an incremental request feed. Cursor replacement preserves published
facts while its successor is acquired. API 35 acceptance covered legacy offline
upgrade, real synchronization, independent request details after offline process
restart, and local filters without rewriting ciphertext. Older activity acquisition
and same-account reauthentication are covered by automated storage tests. The
validated update was installed on the phone with install-only delivery.
The backend now exposes an explicit `ATTENDANCE_DAYS` change feed and an owned
canonical daily record. Captures, reviews and corrections advance one projection
version without rewriting original evidence or correction command versions.
The upgrade backfills every existing evidence day and serializes with in-flight
writes. The three calendar source collections now provide effective weekly rules,
daily rosters and company holidays, including embedded shift snapshots. Only owned
employment admits the shared holiday feed; employee-specific sources remain scoped
to the account. The mobile replica consumes these sources for local calendar derivation.
The [native overtime workflows](https://github.com/fajarxfce/hris-mobile/commit/b58caf8)
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
The [Expense record endpoint](modules/expenses.md#employee-reads) supplies a
coherent claim/submission/approval and activity page for local replication.
Approval reassignment publishes a new invalidation even at the same claim version;
payment progress retains its separate collection. Backend tests and read-only dev
checks passed. The [mobile Expense replica](https://github.com/fajarxfce/hris-mobile/commit/d9ae9935fd533dffdf331452473fb69e59a638c3)
now retains complete claims, indexed summaries, independently versioned payments
and acquired activity. Month/status/page selections query local records, and
bounded synchronization commits changed data and cursor atomically. Confirmed
read denials remain authoritative until admitted synchronization succeeds.
Mobile root checks passed with 1,344 JVM/desktop tests, 184 expense tests in Chromium,
Android dev assembly and production Web compilation. API 35 acceptance cold-started
offline after a 73-hour clock advance: four claims, local filters, two claim details,
approval and activity remained readable with unchanged encrypted payloads.
Clock/network restoration and explicit refresh succeeded; the emulator crash buffer
was empty. The validated APK was delivered to the phone with install-only.
The [mobile Expense working copies](https://github.com/fajarxfce/hris-mobile/commit/a4216f79bd2b43e9c00efb80548b5d34552a6b2a)
retain unsubmitted draft fields and unfinished items separately from confirmed
claims and immutable delivery commands. Local autosave uses encrypted storage,
revision checks and recoverable acknowledgements; live server review remains
separate. API 35 acceptance restored incomplete input after an offline process
restart and a 73-hour clock advance, then created and edited exactly one claim
with stable claim/line IDs. The validated APK was installed on the phone with
install-only delivery. Other workflows retain their own offline-draft work.
Automated UI/API checks and the Android dev build passed. Android 16 device checks
covered password sign-in, company isolation, profiles, inbox reading, offline
content, offline process restoration with company isolation, queued acknowledgement
recovery, company availability, MFA enrollment/verification and recovery-code login,
and device-session revocation. External providers and the employee workflows below
retain their own acceptance work.

The backend [coworker directory projection](synchronization.md#coworker-directory-projection)
now provides a company-scoped change feed and canonical public-field records.
A bounded worker handles effective dates, name/assignment changes and tombstones;
private profiles and lifecycle commands remain separate. Migration, API/worker
checks and a scoped development API probe passed. The [mobile directory replica](https://github.com/fajarxfce/hris-mobile/commit/1dd38355)
now stores those records with account/company/audience ownership and atomically
advances the processed cursor. Search includes unvisited local pages, while a
replacement snapshot remains staged until publication catches up. Confirmed
read denials remain separate from ordinary data, credential and cursor age.
Android emulator acceptance covered cold offline restoration, local search and
filters, and another restart after a 73-hour clock advance. The validated APK
was installed on the phone without launching its UI. Private-profile synchronization
is described separately below.

The backend now provides [owned private-profile snapshots](synchronization.md#owned-private-profiles)
through an explicit `SELF_PROFILES` feed and canonical record. Employment, private
person fields and assignment references publish coherently with their own sync
version. The worker captures cross-company person dependencies, schedules effective
changes, and retains unchanged data independently of its age. Current ownership,
grants, MFA and People availability remain separate read gates. Backend migration,
API/worker, cancellation, concurrency and malformed-payload checks passed. The
[mobile private-profile replica](https://github.com/fajarxfce/hris-mobile/commit/70c0661e9890caa84f5ebf8485a4c5934f2eb241)
now stores coherent owned snapshots with an independent content key and atomic
cursor publication. Employment selection queries local records. Confirmed read
denials remain gated through failed cleanup and pending/offline retries. API 35
acceptance verified cold offline profile restoration and another offline restart
after a 73-hour clock advance. The validated APK was subsequently
[installed on the phone](https://github.com/fajarxfce/hris-mobile/blob/d66078bc/docs/validation.md#private-profile-apk-delivery)
with install-only delivery; physical UI interaction was not part of that installation.

The published-document catalog now has an explicit `DOCUMENT_EVIDENCE` change feed
and a canonical metadata endpoint. Both require current self-document authority
and owned employment; private HR files and unfinished first uploads are excluded.
Replacement uploads retain the accepted revision until publication, while
retirement emits a versioned withdrawal. API, worker, database and domain checks
passed. The [mobile catalog replica](https://github.com/fajarxfce/hris-mobile/commit/f06d8eae)
now retains account/company/audience-owned encrypted records, with local
employment, category and page selection. Published facts remain readable during
cursor replacement and after ordinary data or credential expiry. Confirmed read
denials reopen only after admitted acquisition. API 35 acceptance covered cold
offline restoration, a previously unopened category, retained route selection and
another restart after a 73-hour clock advance. File preparation and native export
also passed after reconnecting; the exported digest matched the server. File
downloads and business attachment review retain independent authorization.

Administrative Documents now covers employee folders, document/revision history,
new and replacement uploads, resumable browser delivery, authorized downloads,
validation/retry, explicit cancellation, retention-policy configuration,
archive/restore, legal hold/release, and accepted-content retirement. Independent
operators and immutable lifecycle history were verified against the API/database.
Upload and validation acceptance used private storage and the development worker;
retirement browser acceptance used pre-existing fictional archive metadata in an
owned disposable database. Its historical and current revisions, lost-response
replay and competing hold were exercised without modifying development records.

Expense workflows now include backend self-service discovery and strict ownership,
mobile claim history, account-owned local records, durable local draft authoring, dated categories,
cost centers, published receipt selection/export, submission, cancellation and
withdrawal with durable recovery. The dashboard supports policy administration,
administrative drafts and returned-claim correction, submission/cancellation/
withdrawal, immutable submitted evidence, authorized receipt downloads and
independent review. Finance covers payable selection, reviewed bank instructions,
batch directories/details, CSV worksheets, guarded release/cancellation,
replacement instructions and reconciliation. Browser commands retain their
original operation after response loss; the administrative workflows were checked
against an isolated API/PostgreSQL fixture with independent operators.

## Remaining delivery sequence

| Area | Remaining end-to-end work |
| --- | --- |
| Mobile identity | Native OIDC handoff and external-provider acceptance. Android MFA and device-session workflows are validated; iOS physical acceptance remains separate. |
| Mobile foundation | Offline drafts for the remaining workflows, stable ownership for the remaining read projections, consistent sync status, deep links and push registration. Expense working copies, owned encrypted company restoration, age-independent stored reads and account-owned Inbox/Leave/Overtime/Workforce/Payroll/Expense/Directory/Document catalog/private profile records are implemented; the mobile sync model records each remaining collection. |
| People | Scheduled lifecycle execution. Dashboard company transfers and initial employee/account binding are implemented and verified with native employee access. |
| Workforce | Bulk/reset and late corrections; physical location acceptance. Shift templates, company holidays, calendars, weekly scheduling, daily rosters, employee attendance evidence/review/corrections and their history, company-wide exception review queues, overtime planning with reviewed calendars, directories/details/history, actual-time submission, independent review and withdrawal, dashboard monthly closing/recovery with published employee results, and mobile work calendars, overtime planning/actual time/withdrawal with durable recovery, and online/offline capture are implemented and verified against the real API. |
| Leave | Team calendar, entitlement batches and year closing; scheduled accrual/expiry. Dashboard request creation includes scoped employee/evidence selection, server calculation, preview conflicts and original-command recovery, verified against an isolated API/PostgreSQL instance. Mobile balances, filtered history, request details, submission with published document evidence, and durable withdrawal/cancellation flows are implemented and verified against the development API. |
| Payroll | Dashboard opening tax history, calculation, review, finalization, payslips, payments and amendments. Company policy and employee compensation review, bounded employee discovery, effective month selection, immutable history, independently authorized versioned edits and original-command recovery are implemented and verified against the real API. Period directories, paged participant selection, explicit creation review, roster/history tabs and versioned draft cancellation are implemented and verified against an isolated API/PostgreSQL instance. Monthly input discovery, complete term editing, closed attendance review, immutable history and independent verification are implemented and verified with the real API and worker, including competing revisions, lost responses and revoked access. Native finalized payslips, versioned payment progress, account-owned local records with annual/detail queries, incremental sync and authorized PDF saving are implemented. |
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
