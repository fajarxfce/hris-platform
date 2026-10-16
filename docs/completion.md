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
The [shared Overtime read restrictions](https://github.com/fajarxfce/hris-mobile/commit/5ddbda710d7c1f2bb2a60784a458042b2017714c)
retain confirmed denials independently of fact cleanup. Overview, detail, forms
and retained commands remain closed until admitted acquisition completes; a newer
denial wins over older pending work. Root checks passed with 1,603 tests and 250
selected Chromium tests. API 35 acceptance verified persisted denial across an
offline cold start and failed retry, recovery of the actual-time form, and another
offline read with unchanged encrypted facts. Android assembly and
[GitHub Actions](https://github.com/fajarxfce/hris-mobile/actions/runs/38943575491)
passed, including production Web, Windows and Apple builds. Phone delivery followed
with the planning-draft update below.
The [Overtime planning drafts](https://github.com/fajarxfce/hris-mobile/commit/e43c2885cb20c8812cfd1ac059f2d688c8f4e8a4)
retain incomplete dates, times, break input and reason in a separate encrypted
working copy. Root checks passed with 1,631 tests, 278 selected Chromium tests and
Android dev assembly. API 35 acceptance restored and edited unfinished input
across offline process restarts, then created exactly one plan after current
schedule review. Confirmation removed only the acknowledged working revision.
The validated APK was installed on the phone without opening it.
The [Overtime action drafts](https://github.com/fajarxfce/hris-mobile/commit/6606b0fddb4d617cdd4fdde9c41fca2b1a8e3817)
now retain actual-time and withdrawal input in separate encrypted request/action
working copies. Root checks passed with 1,664 tests, 311 selected Chromium tests
and Android dev assembly. API 35 acceptance restored both forms after offline
cold starts and retained a subsequent offline edit. Reconnection confirmed
exactly one actual-time change and one withdrawal. Cleanup removed only the
acknowledged draft, while a draft based on the old request version remained
visible for explicit review and deletion. No action drafts or pending commands
remained after canonical confirmation. The validated APK was installed on the
phone with install-only delivery.
The [native payslip pages](https://github.com/fajarxfce/hris-mobile/commit/46d8b2f)
now provide annual history, published amounts, payment progress and English/Indonesian
PDF saving. Every read requests employee SELF scope. API 35 acceptance verified
year filters, native file-dialog cancellation, the exported PDF's SHA-256,
encrypted list/detail restoration after background process death offline, and
explicit online recovery. The validated APK was installed on the phone with
install-only delivery.
The [revised native payslips](https://github.com/fajarxfce/hris-mobile/commit/ec6db47f)
retain assessment revisions and independently versioned payment balances, while
PDF export pins the revision selected before the save dialog. A compatible
acquisition-format upgrade keeps previously stored facts visible until a complete
replacement publishes. Root checks passed with 1,498 desktop/JVM/UI tests, 108
targeted Chromium tests, Android assembly and production Web compilation. API 35
acceptance verified offline upgrade, balance acquisition, another offline process
restart and an Indonesian PDF saved with its revision in the filename. Phone
delivery followed in the retained-revision update below; the emulator retained
its application data.
The [retained payslip navigation](https://github.com/fajarxfce/hris-mobile/commit/7df15e15)
adds previous/next revision selection, an owned encrypted statement archive and
exact-revision acquisition. Already acquired statements remain available offline;
their payment panel keeps the latest stored balance. Archive reads cannot advance
the feed cursor or clear a confirmed denial. Root checks passed with 1,527 tests,
136 targeted Chromium tests, Android assembly and production Web compilation.
API 35 acceptance verified offline upgrade and reconnection with unchanged stored
record hashes. The real development fixture uses revision zero; automated tests
cover historical navigation, immutable archive writes and a pinned historical PDF.
The validated APK was installed on the authorized Android 16 phone after its
source inputs and artifact matched the validation manifest. Delivery was
install-only; no phone UI was opened or operated.
Subsequent API 35 acceptance used an isolated API/PostgreSQL/worker with a real
approved and published amendment. Android acquired both statements, retained the
current payment balance while browsing revision zero, and reopened Home and both
revisions after an offline process restart. All five encrypted record hashes and
both active generations were unchanged through reconnection. Native revision-zero
English and revision-one Indonesian PDFs matched their exact server SHA-256.
The standard APK and phone installation were unchanged; see
[executed mobile validation](https://github.com/fajarxfce/hris-mobile/blob/main/docs/validation.md#android-acceptance-with-published-amendments).
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
The [mobile Leave working copies](https://github.com/fajarxfce/hris-mobile/commit/b32241c6d8e40523e1043d68c8da1bf2d609ca06)
retain dates, type, portion, reason and selected evidence separately from acquired
requests and immutable submissions. Autosave has revision checks, recoverable
acknowledgements and explicit retry/discard. Root checks passed with 1,576 tests;
236 Chromium tests, Android dev assembly and the production Web build passed.
API 35 acceptance restored and edited a draft after an offline cold start and a
73-hour clock advance, then confirmed exactly one half-day request after online
review. Confirmation removed only its matching working revision. The validated
APK was installed on the phone with install-only delivery. Other unfinished
workflow input and shared synchronization status remain separate work.
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
| Mobile foundation | Offline drafts for the remaining workflows, stable ownership for the remaining read projections, consistent sync status, deep links and push registration. Expense, Leave and Overtime planning/actual-time/withdrawal working copies, owned encrypted company restoration, age-independent stored reads and account-owned Inbox/Leave/Overtime/Workforce/Payroll/Expense/Directory/Document catalog/private profile records are implemented; the mobile sync model records each remaining collection. |
| People | Reviewed scheduled offboarding is implemented across the backend, worker and dashboard, including cancellation, retained attempts and terminal-job recovery. [Scheduled onboarding](onboarding-schedules.md) is implemented across the backend, worker and dashboard, with dated template/assignee review and original-command recovery verified against an isolated API/PostgreSQL/worker fixture. Dashboard company transfers and initial employee/account binding are implemented and verified with native employee access. |
| Workforce | Physical location acceptance remains. Development delivery is complete through V90 after compatible Android installation; see [deployment evidence](delivery.md#database-recovery-and-v90-development-delivery). [Android replica acceptance](https://github.com/fajarxfce/hris-mobile/blob/main/docs/validation.md#android-acceptance-with-closed-attendance-adjustments) passed for late attendance corrections: overnight work remained readable after an offline cold start, and reconnecting acquired a later absence while preserving other attendance and roster facts. The [closed-attendance dashboard](dashboard.md#attendance-after-closing) is implemented: all 3,772 unit tests, 18 browser scenarios and an isolated API/PostgreSQL/worker workflow passed. It reviews pending evidence explicitly, retains original closing facts and recovers an exact adjustment after a newer HR correction. Its API support metadata passed 64 related API/PostgreSQL cases. [Amendment attendance source selection](payroll-amendments.md#attendance-source-selection) is implemented in the backend; 241 selected API/PostgreSQL, 114 payroll domain, seven payroll data and 81 worker tests passed. Calculation and publication retain the selected immutable facts through later HR corrections. Dashboard source selection and retained factual calendars are implemented; all 3,752 dashboard unit tests, 83 affected browser scenarios and two isolated API/PostgreSQL/worker workflows passed, including independently approved publication after a newer attendance correction. Explicit attendance source selection for unfinalized payroll inputs is implemented; 173 selected API/PostgreSQL, 108 payroll domain, seven payroll data and 81 worker tests passed. Calculation retains the selected immutable evidence through later corrections. The input dashboard shows current and retained source revisions; 3,735 unit tests, 40 selected browser scenarios and an isolated API/PostgreSQL/worker workflow passed. The [closed attendance adjustment API](attendance-adjustments.md) is implemented, with 140 selected API/PostgreSQL, 61 domain and 81 worker tests passing. It retains original closing facts while appending reviewed employee snapshot revisions; Android replica acceptance and development delivery are complete; physical location acceptance remains separate. The [bulk roster dashboard](dashboard.md#bulk-roster-editing) is implemented, with 3,727 dashboard tests, 20 browser scenarios and an isolated API/PostgreSQL workflow passing. The [bulk roster API](roster-bulk.md) is implemented, with 138 selected API/PostgreSQL, 53 domain and 81 worker tests passing. It reviews a bounded explicit employee/date selection and commits changed cells atomically. [Reviewed roster reset](modules/workforce.md#roster-reset-contract) is implemented in the API, dashboard and mobile sync. API 35 acceptance verified reset, an offline cold start and later reactivation; [client validation](https://github.com/fajarxfce/hris-mobile/blob/main/docs/validation.md#roster-reset-synchronization) is complete. The updated client was installed before development activation through V90. Shift templates, company holidays, calendars, weekly scheduling, daily rosters, employee attendance evidence/review/corrections and their history, company-wide exception review queues, overtime planning with reviewed calendars, directories/details/history, actual-time submission, independent review and withdrawal, dashboard monthly closing/recovery with published employee results, and mobile work calendars, overtime planning/actual time/withdrawal with durable recovery, and online/offline capture are implemented and verified against the real API. |
| Leave | Dashboard validity configuration and native finite-award acceptance are implemented. The editor validates optional 1–12-month deadlines; current/history/processing views retain them. All 3,352 dashboard unit tests, 50 browser scenarios and an isolated API/PostgreSQL/worker workflow passed. Native acceptance verified retained balances during an offline APK upgrade, metadata hydration without new ledger versions, and finite carry after another offline cold start. Mobile carried-balance views and a staged record-format upgrade are implemented; root/Android/Chromium/Web checks passed and the validated APK was installed without phone UI interaction. Optional 1–12-month validity is implemented in the API, including history/snapshots and rejection of fractional integer input; 201 API/PostgreSQL scenarios passed. Dashboard ledger review shows the stored carry breakdown and original calendar deadline; unit, browser and real API/worker integration tests passed. Canonical balance/sync reads now include the stored carry breakdown and frozen deadline, without changing ledger versions solely because time passes; API/PostgreSQL, domain and worker regressions passed. Carry-expiry history and recovery are implemented in the dashboard, with finite visibility-owned polling and original-command recovery. An isolated API/PostgreSQL/worker browser scenario verified a lost recovery response, retained attempts and exactly one ledger debit. Scoped history/detail/recovery APIs are implemented and tested, including frozen labels, bounded outcomes, safe worker metadata, current authority and recovery races. Its durable worker/domain recovery and [accounting, request, adjustment and closing integration](leave-carryover.md) are tested, including original funding, source expiry before rollover, frozen batch timezone, current assurance, cancellation, scope and migration of existing facts. Public closings create tracked awards and accept the optional validity configured in the dashboard. [Accrual schedules](leave-accrual-schedules.md) are implemented across the backend, worker and dashboard, retaining 1–12 reviewed periods, policy snapshots and an explicit employee cohort. API/worker assurance, rollback, capacity and maximum 5,000-employee admission tests are validated. An isolated API/PostgreSQL/worker browser scenario verified original-command recovery, mixed eligibility outcomes, one ledger grant per posting, current progress, company replacement and future due times through cancellation/resume. Company and direct-report calendars are implemented in the API and dashboard, with bounded month/date queries, retained request-navigation context and hidden-read cancellation. Backend scope/MFA races and browser lifecycle/layout tests passed; an isolated API/PostgreSQL browser scenario verified half-day counts, withdrawal and company replacement. Reviewed company accrual/year-closing batches, per-employee outcomes, retained attempts and explicit resume are implemented in the backend, worker and dashboard. Browser acceptance verified original-command recovery, mixed eligibility outcomes and a single year-end carryover against an isolated API/PostgreSQL/worker fixture. Dashboard request creation includes scoped employee/evidence selection, server calculation, preview conflicts and original-command recovery, verified against an isolated API/PostgreSQL instance. Mobile balances, filtered history, request details, submission with published document evidence, and durable withdrawal/cancellation flows are implemented and verified against the development API. |
| Communications | Mobile push hints and navigation. Dashboard audience groups, drafts, bounded named audience selection, publication review, preview, scheduling, cancellation/recovery and archival, and the mobile inbox with durable read/acknowledgement commands are implemented. |
| Identity administration | [Cross-account app sessions](dashboard.md#account-app-sessions) are implemented in the API and dashboard with global authorization, ordered guards, current MFA/recent proof and idempotent revocation. All 3,685 dashboard unit tests, 36 related browser scenarios and two isolated API/PostgreSQL session workflows passed, including lost-response recovery, account replacement and retained independent devices/cookies. All 94 selected API/PostgreSQL, 40 identity domain/data and 81 worker tests passed, including rotation races, cancelled writes and retained independent devices/accounts. [Periodic native-session cleanup](modules/identity.md#native-session-cleanup) is implemented with worker-only access, bounded batches, retained reuse evidence and cancellation; 22 native API, 40 identity domain/data and 81 worker tests passed. [Own-account app sessions](dashboard.md#app-sessions) are implemented in the dashboard, including confirmed revocation, response-loss recovery, cancellation and metadata cleanup. All 3,653 unit tests, 17 related browser scenarios and an isolated API/PostgreSQL workflow passed; the latter verified revoked native credentials, independent devices/accounts and dashboard account replacement. Dashboard SSO binding creation, bounded/exact review and versioned activation are implemented. All 3,624 unit tests, 31 related browser scenarios and an isolated API/PostgreSQL flow verified immutable identity values, original-command recovery, activation versions and cookie/native-session revocation. Dashboard invitations, explicit resends, public acceptance and password recovery are implemented. All 3,595 unit tests, 28 related browser scenarios and an isolated API/PostgreSQL/worker flow verified loopback SMTP, replacement links, activation without grants, lost-response recovery and old cookie/native-session revocation. Global account search/review/access editing, new company assignments, existing membership/grant review/editing and company role creation/editing/archival/history are implemented. Assignments require active accounts and current roles without overwriting existing membership; prior browser/API workflows verified retained role evidence, reactivation, self-edit session revocation and independent native-session company isolation. |
| Reporting | Attendance, leave, overtime, expenses and payroll reports, authorized bounded exports and export history |
| Operations | [Private PostgreSQL backups and isolated restore verification](database-recovery.md) are implemented, including role definitions, migration rehearsal and controlled V90 development delivery. Object-storage/key recovery, integration status, reference-workload measurements and the complete deployment runbook remain. |

Cross-company isolation, lost-response replay, denied/revoked access, offline
recovery, cancellation, late results, and resource disposal are acceptance
conditions for affected changes. Device checks are recorded separately from
builds and automated tests. Production signing, external provider registration,
releases, and live distribution need their actual configuration and authorization;
they cannot be represented as completed by a fixture.
