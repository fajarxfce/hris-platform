# End-to-end completion

[Android upload/review recovery](native-upload-acceptance.md) now passes real
API/PostgreSQL/worker/Garage acceptance. A committed one-MiB chunk response was lost;
Android retained the upload through offline restart and replayed the exact command.
Failed inspection review survived a second offline restart. Explicit inspection
retry, cancellation, local deletion and logout passed; six summary rows became
empty before deleting the cancelled file. Counters stayed 32 → 32 and 42 → 42 on
summary reload. The fixture observed 57 requests, nine intended company writes,
four single-attempt chunks and one unpublished cancelled revision, passing in
8.7m/9.2m with setup. Scanner-unavailable jobs were stopped through the real admin
API; no database state or verdict was fabricated. Three proxy regressions, strict
TypeScript, Biome, production build and architecture/format checks passed. All
owned resources were removed. Application code and Starter need no change.

[Android Firebase registration](native-push-acceptance.md) now passes actual SDK
issuance, real API registration, committed-response loss, offline restart,
original-command recovery, explicit token retirement and logout. Mobile `02743a9f`
fixes submillisecond expiry rejection and cleanup of an absent prior session.
The final fixture observed 51 requests, one registration, two identical PUTs,
one disable, final version 1 with no retained token, and no company mutation.
It passed in 9.7m/10.3m including setup, alongside 1,840 Mobile desktop/UI tests,
113 identity Chromium cases, Android assembly and bounded Platform proxy checks.
Owned resources were cleaned. FCM sending/scoped hints still need server
credentials; Google token exchange and iOS/physical acceptance remain separate.

[Saved account-access audit](account-access-audit.md) now discloses typed historical
status, global permissions and reason through a separate V100 immutable projection.
Reads require both global `audit.read` and `identity.manage`, current credentials
and pre/post-acquisition MFA. The dashboard owns/cancels each detail independently.
Real API/PostgreSQL/worker/Chromium acceptance passed in 7.9 seconds (38.6 with
setup), proving independent-author disclosure, metadata-only denial, preservation
after a later edit and revoked-session cleanup. Dashboard checks passed 3,956 tests;
full worker/API, architecture/formatting, focused Chromium and recovery checks
validate the change. Schema-100 recovery includes exact typed audit fields,
104 objects/2,098,795 bytes and retained old/current keys. Other audit source
families and remaining provider/device acceptance continue separately.

[Android partial-download recovery](native-download-acceptance.md) passed against
actual API/PostgreSQL/worker/Garage with a fictional accepted document inspection.
A one-MiB partial file and Pending actions transfer count survived offline process
restart; summary open/reload made no request (31 → 31). Resume began at byte
1,048,576 and the native saved copy matched all 3,145,841 source bytes/SHA-256.
All six summary rows became empty and sign-out succeeded. The independent fixture
observed 49 requests, four correct ranges and no native company mutation, passing
in 9.7m (10.2 with setup). Proxy concurrency/disposal regression, Biome, TypeScript,
production build and architecture/formatting passed. The unchanged loopback APK
was reused; application source and Starter need no change. Owned resources and
control files were removed, and the emulator stopped with app data retained.

[Global platform audit](audit.md#platform-history) is implemented through a separate
V99 metadata projection and explicit global `audit.read` grant. Account/MFA guards,
source-bound append-only capture and unchanged journal RLS protect disclosure.
The dashboard supports auditors without companies, bounded search/pagination and
per-account cancellation. Validation passed 250 distinct backend cases including
all 94 worker tests, 3,949 dashboard tests, nine focused Chromium scenarios and a
real API/PostgreSQL/worker/browser workflow (8.5 seconds; 53.9 with setup). The
latter verified an independent grant, another actor's actual journal metadata,
52-row pagination, company-only denial and revoked-session cleanup. Both JARs,
architecture/formatting and twenty recovery-tool cases passed. Full isolated
recovery passed at schema 99 with 104 objects/2,098,795 bytes and old/current keys.
Owned resources were removed and screenshots inspected. The newer account-access detail contract is linked above; other audit families
and the remaining native/provider work remain separate; this
increment needs no Mobile/Starter port or live deployment.


For current branches, active work, executable commands and the next continuation
steps across all three repositories, see [agent handoff](agent-handoff.md).

The delivery scope includes the backend, administrative dashboard, and the
employee application in [HRIS Mobile](https://github.com/fajarxfce/hris-mobile).
The mobile repository is generated from Compose Fluent Starter and has its own
history. Reusable foundation corrections also return to the starter.

**The overall product PRD remains in progress.** Completed increments below do
not close the remaining delivery sequence or substitute automated checks for
live API/device/provider acceptance. The requirements in [product](product.md)
and its module documents remain the acceptance baseline.

[Delivery records](delivery.md) describe implemented behavior and observed
validation. A generated module or route does not complete a capability. Each
workflow must enforce current scope, handle response loss and cancellation,
provide localized errors, and pass the relevant real API and UI checks.

The [attendance workload](workload.md) now has a complete local 5,000-employee,
900-second measurement on four shared server CPUs with 16 GiB of summed container
limits. All 20,000 real requests succeeded; the slowest endpoint p95 was 93.753 ms.
SQL confirmed 5,000 accepted events, employees, audits and synchronization heads,
with no OOM kills and all fixture resources removed. The host is a shared eight-CPU/
32-GiB VPS, so this is a constrained local result with explicit limits. The subsequent
homogeneous January payroll calculation also passed all 5,000 results in 85.972
seconds, with reconciled decimal totals and no OOM kills. Full-signature document
acceptance also passed two 100-MiB uploads/validations, four checked downloads and
EICAR rejection after correcting the total scan budget to 250 MiB. Scanner peak
was 1,713.70 MiB with no OOM kills. Other corpora, simultaneous mixed workloads and
dedicated deployment measurements remain separate work.

The [deployment/cutover runbook](deployment.md) now specifies artifact retention,
HTTPS/configuration review, final checkpoints, migration gates, worker startup,
canaries and rollback decisions. Its operator tool reviews the rendered API/worker
flags without emitting secrets; six regressions and three real Compose variants
passed. This adds a concrete procedure and static review, not live worker health,
provider delivery, target-specific cutover authorization or measured RPO/RTO.

[Database/content/key recovery sets](recovery-set.md) now pass an isolated real
API/worker/PostgreSQL/Garage exercise, repeated at schema 99: 104 objects (2,098,795 bytes),
two retained encrypted records using old/current keys, and a second restore after
source storage changes. An incorrect retired key fails even with recomputed
manifest metadata. Twenty recovery-tool tests, the existing 21 database-tool tests,
three application-cipher tests, architecture and formatting passed. Temporary
resources and private fixture data were removed. Live cutover, deployment/provider
configuration and reference-workload measurements remain open.

Platform integration configuration review now exposes six API startup flags without
provider identifiers or secrets. Global authority, current credentials and MFA
protect both the endpoint and the localized dashboard; company-only administration
is insufficient. Backend checks passed 184 tests including the full worker graph;
all 3,922 dashboard tests and seventeen distinct Chromium cases passed across runs.
Real API/PostgreSQL/browser acceptance verified three independent MFA accounts
through permission revocation and removal of the old observation. Actual worker
reports now add four immutable bound flags and database publication times, with
90-second recency, five-minute pruning and a 64-process bound. A later increment
passed 183 backend tests including the full 94-test worker graph, all 3,940 dashboard
tests, six focused browser cases and real worker/browser acceptance. The latter
observed advancing publications and independent-account revocation; all fixtures
were removed. These reports do not prove provider health/delivery or every job loop. See
[integration configuration](integrations.md).

Report export commands, owned history/status, authorized CSV Range/ETag downloads
and scheduled retention now expose the backend lifecycle. The worker preserves
captured facts, accepted parts and expiry across recovery; download checks current
authority and part integrity, and bounded cleanup waits for physical objects before
retirement. Validation passed 338 distinct tests across focused runs, including
both application contexts and the full 90-test worker graph; both packages and
architecture/format checks passed.
The dashboard now covers applied-filter review, private history/progress, confirmed
cancellation/recovery and bounded CSV download. All 3,910 dashboard tests and 26
distinct Chromium cases passed across focused runs, including exact file contents
and original-command recovery through MFA. An isolated real API/PostgreSQL/worker/
Garage-S3/Chromium workflow also passed lost-admission recovery, private owner/source
access, immutable source capture, three-part 1,201-row CSV download, exact hashes
and cross-object byte ranges. Its historical load is fictional fixture data;
provider interruption/physical-expiry checks use the earlier controlled tests.
See [export contracts and evidence](report-exports.md).

Historical attendance closings now have dashboard and exact-revision drill-down
acceptance. Applied month/company filters, complete publication state, original and
latest-corrected comparisons, and bounded employee pages retain their report return
context through the selected and original snapshots. Validation passed 3,835
dashboard tests, twenty-two distinct Chromium cases and a real API/PostgreSQL/worker
workflow with an independent MFA reader, failed/retried closing, future-hire
exclusion and successive corrections. Catalogue/saved filters and exports remain
open. See [closing dashboard evidence](reporting.md#attendance-closing-dashboard).

The historical attendance report now has a matching bounded employee page with
exact original closing and selected adjustment references. Ascending employment
UUID continuations retain company/month scope through later corrections and hires.
The immutable initial employment date excludes future hires captured by the worker
before either aggregation or pagination; all twenty-five closing API cases pass.
Original/current counts reconcile with the aggregate while retained revisions stay
readable. Validation passed 61 reporting and 83 API/PostgreSQL/OpenAPI cases, plus
architecture, formatting and API packaging. Dashboard and exports remain open. See
[employee closing drill-down](reporting.md#employee-closing-drill-down).

Historical attendance closing aggregates now retain the published original facts
beside the latest applied employee corrections across one to twelve months. Partial
and cancelled attempts are excluded, successive adjustments count once, and empty
published closings keep their identity. Original/current grants and MFA protect the
bounded SQL projection. Validation passed 56 reporting tests and 146 distinct
API/PostgreSQL/OpenAPI cases across focused runs, plus architecture, formatting
and API packaging. Dashboard, revision-aware drill-down and exports remain in
progress. See [closing definitions and evidence](reporting.md#historical-attendance-closings).

The published payroll cost API now selects one current published revision per
assessment and reconciles complete monthly buckets and distinct employment totals.
Its exact IDR fields preserve final-period refunds and avoid amendment duplication.
Backend validation passed 49 reporting, 114 payroll domain, 101 distinct
API/PostgreSQL/OpenAPI and 81 worker tests. Dashboard acceptance passed 3,823 tests,
fourteen distinct Chromium scenarios and a real API/PostgreSQL/worker workflow
through publication, payment, amendment and returns, preserving original evidence
and applied month navigation. Historical selections and exports remain in progress. See [payroll cost definitions](reporting.md#published-payroll-costs).

The operational expense report and scoped claim page now use latest-submitted
transaction lines with exact decimal amounts, current status and independent
employment counts. They exclude unsubmitted drafts and avoid double-counting
resubmissions. Backend acceptance passed 42 reporting, 20 expense domain,
153 API/PostgreSQL/OpenAPI and 81 worker tests, including real payment invariance.
Dashboard acceptance passed 3,813 tests, 13 distinct Chromium scenarios and a real
API/PostgreSQL correction/resubmission workflow with exact-submission navigation
and preserved report return filters. See [expense definitions](reporting.md#operational-expenses).

The operational overtime report API now supplies exact requested, actual and
approved minutes, submitted-actual counts and distinct employments over complete
month buckets, with optional current status and a bounded annual window. It passed
31 reporting, 61 workforce, 84 API/PostgreSQL/OpenAPI and 81 worker tests. The dashboard
and month drill-down passed 3,801 unit/UI tests, 12 report browser scenarios and real
API/PostgreSQL approval/rejection/withdrawal reconciliation. See [definitions and evidence](reporting.md#operational-overtime).

The operational leave report and matching request drill-down now read frozen
working dates within an annual window and preserve exact half-day quantities.
Current status buckets include rejected/cancelled history separately from active
leave; report/source grants and MFA protect both projections. Validation passed
25 reporting tests, 90 API/PostgreSQL/OpenAPI tests and 81 worker tests, including
pagination after status changes and concurrent decision serialization. Its dashboard
passed 3,793 unit/UI tests, 19 Chromium scenarios and two real API/browser workflows
through withdrawal, status reconciliation and preserved report/calendar return links.
Historical financial reports and exports remain open. See [reporting](reporting.md#operational-leave).

The operational attendance exception report now supplies coherent per-date and
overall counts with current report/source access and MFA checks. It passed 15
reporting-domain/data and 83 HTTP/PostgreSQL/OpenAPI tests, plus architecture and
format checks. Its dashboard and date drill-down passed 3,781 dashboard tests,
11 Chromium scenarios and a real API/PostgreSQL workflow through review and
correction reconciliation. Historical closing reports, other metrics and exports
remain active work; see [reporting](reporting.md).

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
The [shared Inbox read restrictions](https://github.com/fajarxfce/hris-mobile/commit/25819040f7d992559ca1e73567cdc0697afbdd8f)
now cover list, detail, unread counts and retained actions. Root checks passed
with 1,679 workspace tests, four build-convention tests and 67 selected Chromium
tests. API 35 acceptance retained the same encrypted message and denial across an
offline cold start and failed retry, then recovered access and confirmed a read
action through the real API/PostgreSQL/worker fixture. The validated WireGuard
APK was installed on the phone with install-only delivery.
The [company data status page](https://github.com/fajarxfce/hris-mobile/commit/b1b8b81fdb4e38f1b513781826945fff2d54ddde)
now shows completed synchronization times for all nine datasets, with individual
refresh, bounded combined refresh and cancellation. Status comes from published
local checkpoints and follows each feature's existing read restrictions. Root
checks passed with 1,706 workspace tests and four convention tests; 892 selected
Chromium tests and Android assembly passed. API 35 acceptance verified offline
process restoration, unchanged encrypted data after offline refresh, retained MFA
restrictions and admitted recovery. The validated APK was installed on the phone
with install-only delivery.

The local Mobile increment `85fa6cb9` adds the shared pending-action summary for
Inbox, Attendance, Overtime, Leave, Expenses and Documents. It reads owned local
drafts/commands/transfers without initiating delivery. Root checks passed with
1,724 desktop tests, 253 selected Chromium tests and Android assembly. Real API
and Android 35 emulator acceptance at Mobile `9a557dcc` and Platform `1f95e0c3`
now cover pristine form exclusion, an edited Leave draft, summary open/reload
without HTTP, offline process restoration, retained input after reconnect and
explicit deletion. All 53 observed native requests contained no company write;
the owned API/PostgreSQL/worker fixture also passed its independent assertion.
See the [native evidence](https://github.com/fajarxfce/hris-mobile/blob/feat/native-push/docs/validation.md#pending-actions-on-android).
Other workflow command states and physical acceptance remain separate. Its generic database
observation changes were ported and validated in starter `8707dec`.

Mobile `6c200387` implements HRIS URL navigation, including account/company-scoped
Inbox resources, pending ownership across first login, and resumed-host navigation.
It rejects other-account/company targets and never marks a message read implicitly.
Root checks passed with 1,732 desktop tests, 17 application Chromium tests and
Android assembly. Starter `29081c5` carries the generic ownership/lifecycle port,
with 286 desktop tests, three application Chromium tests and Android assembly.
Android 35 URL acceptance at Mobile `1c1f9104` and Platform `893db26a` now passes
actual browsable intent entry through login/company selection, repeated links,
account/company/query rejection, background resume and offline message restoration.
An independent native API session verified unchanged read/acknowledgement times;
no other-company request or native company write occurred. Archiving the message
removed content through sync and kept it unavailable after an offline cold launch.
See [native URL evidence](https://github.com/fajarxfce/hris-mobile/blob/feat/native-push/docs/validation.md#android-url-navigation).
Provider delivery, verified HTTPS associations, iOS and physical acceptance remain
separate work.

Mobile `f98a7884` adds session-owned native push registration and retained SDK
retirement; `d941a544` mounts explicit opt-in, disable and recovery in Settings.
Mobile `ea2c58b7` receives bounded data-only Inbox hints, persists encrypted
account-owned queues, synchronizes canonical Inbox state and applies current
admission before a generic local notification. Attempt evidence survives account
changes and uncertain OS delivery; reception cannot choose display text or bypass
resource-link admission. Root checks passed with 1,788 desktop/UI tests, 113 related
Chromium tests and configured Android assembly. The reusable notification path
fix `b77d991f` is ported as starter `7562e67`, with 291 desktop tests, eight Chromium
tests and Android assembly. All these commits are pushed.

The requested Firebase project `cmp-fajar` has local ignored Android configuration
for all HRIS/starter flavors. Configuration processing proves a matching build,
not token issuance or provider delivery. Mobile `5a3fb6ee` adds bounded background
registration renewal and retained retries, validated with 1,797 desktop/UI tests,
106 related Chromium tests and configured Android assembly. Worker composition
`23d17e77` addresses duplicate Koin metadata during native framework linking;
cross-platform CI evidence remains tracked in Mobile. Starter `7562e67` passed
Linux, Windows and Apple in run `40637280819`.

The [Firebase Authentication exchange](firebase-auth.md) now accepts verified
email/password and Google identities through approved HRIS issuer/UID bindings.
It preserves HRIS MFA, credential revocation, native idempotency and cookie
isolation. All 76 selected API/PostgreSQL tests, 57 identity domain/data tests and
81 worker tests passed; OpenAPI generation/validation and architecture checks
also passed. Real Firebase login and Mobile SDK/UI integration remain open.

The user enabled Email/password and Google; public Firebase configuration now
returns HTTP 200, and the public Google web client ID was discovered and validated
in all three Android HRIS flavor configurations. Worker FCM credentials and
iOS/APNs configuration remain unavailable. Physical FCM/APNs delivery and iOS
acceptance remain open. See Mobile's
[native push contract](https://github.com/fajarxfce/hris-mobile/blob/feat/native-push/docs/native-push.md)
for boundaries and validation evidence.

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
| Mobile foundation | Reconcile any remaining draft/read-projection gaps against module acceptance; complete scoped provider hint acceptance after the accepted registration/recovery workflow, plus iOS/physical URL acceptance. Android application URLs passed real API/emulator acceptance, including unread-state preservation and archival across offline process restoration. Real API/Android acceptance of the pending-action summary passed for pristine and edited Leave drafts, offline restoration, reconnect and deletion. A subsequent actual Inbox command case retained awaiting confirmation through offline process restart, recovered the same operation/input after a lost response and left server version 1; a subsequent partial-download case retained one transfer through offline restart, resumed from one MiB and saved exact verified bytes; upload/inspection-review now also passes offline restarts, exact chunk replay, explicit retry and cancellation; other feature command outcomes retain separate validation. Company data status and bounded explicit synchronization cover nine datasets. Expense, Leave and Overtime planning/actual-time/withdrawal working copies, owned encrypted company restoration, age-independent stored reads and account-owned Inbox/Leave/Overtime/Workforce/Payroll/Expense/Directory/Document catalog/private profile records are implemented; the mobile sync model records each remaining collection. |
| People | Reviewed scheduled offboarding is implemented across the backend, worker and dashboard, including cancellation, retained attempts and terminal-job recovery. [Scheduled onboarding](onboarding-schedules.md) is implemented across the backend, worker and dashboard, with dated template/assignee review and original-command recovery verified against an isolated API/PostgreSQL/worker fixture. Dashboard company transfers and initial employee/account binding are implemented and verified with native employee access. |
| Workforce | Physical location acceptance remains. Development delivery is complete through V90 after compatible Android installation; see [deployment evidence](delivery.md#database-recovery-and-v90-development-delivery). [Android replica acceptance](https://github.com/fajarxfce/hris-mobile/blob/main/docs/validation.md#android-acceptance-with-closed-attendance-adjustments) passed for late attendance corrections: overnight work remained readable after an offline cold start, and reconnecting acquired a later absence while preserving other attendance and roster facts. The [closed-attendance dashboard](dashboard.md#attendance-after-closing) is implemented: all 3,772 unit tests, 18 browser scenarios and an isolated API/PostgreSQL/worker workflow passed. It reviews pending evidence explicitly, retains original closing facts and recovers an exact adjustment after a newer HR correction. Its API support metadata passed 64 related API/PostgreSQL cases. [Amendment attendance source selection](payroll-amendments.md#attendance-source-selection) is implemented in the backend; 241 selected API/PostgreSQL, 114 payroll domain, seven payroll data and 81 worker tests passed. Calculation and publication retain the selected immutable facts through later HR corrections. Dashboard source selection and retained factual calendars are implemented; all 3,752 dashboard unit tests, 83 affected browser scenarios and two isolated API/PostgreSQL/worker workflows passed, including independently approved publication after a newer attendance correction. Explicit attendance source selection for unfinalized payroll inputs is implemented; 173 selected API/PostgreSQL, 108 payroll domain, seven payroll data and 81 worker tests passed. Calculation retains the selected immutable evidence through later corrections. The input dashboard shows current and retained source revisions; 3,735 unit tests, 40 selected browser scenarios and an isolated API/PostgreSQL/worker workflow passed. The [closed attendance adjustment API](attendance-adjustments.md) is implemented, with 140 selected API/PostgreSQL, 61 domain and 81 worker tests passing. It retains original closing facts while appending reviewed employee snapshot revisions; Android replica acceptance and development delivery are complete; physical location acceptance remains separate. The [bulk roster dashboard](dashboard.md#bulk-roster-editing) is implemented, with 3,727 dashboard tests, 20 browser scenarios and an isolated API/PostgreSQL workflow passing. The [bulk roster API](roster-bulk.md) is implemented, with 138 selected API/PostgreSQL, 53 domain and 81 worker tests passing. It reviews a bounded explicit employee/date selection and commits changed cells atomically. [Reviewed roster reset](modules/workforce.md#roster-reset-contract) is implemented in the API, dashboard and mobile sync. API 35 acceptance verified reset, an offline cold start and later reactivation; [client validation](https://github.com/fajarxfce/hris-mobile/blob/main/docs/validation.md#roster-reset-synchronization) is complete. The updated client was installed before development activation through V90. Shift templates, company holidays, calendars, weekly scheduling, daily rosters, employee attendance evidence/review/corrections and their history, company-wide exception review queues, overtime planning with reviewed calendars, directories/details/history, actual-time submission, independent review and withdrawal, dashboard monthly closing/recovery with published employee results, and mobile work calendars, overtime planning/actual time/withdrawal with durable recovery, and online/offline capture are implemented and verified against the real API. |
| Leave | Dashboard validity configuration and native finite-award acceptance are implemented. The editor validates optional 1–12-month deadlines; current/history/processing views retain them. All 3,352 dashboard unit tests, 50 browser scenarios and an isolated API/PostgreSQL/worker workflow passed. Native acceptance verified retained balances during an offline APK upgrade, metadata hydration without new ledger versions, and finite carry after another offline cold start. Mobile carried-balance views and a staged record-format upgrade are implemented; root/Android/Chromium/Web checks passed and the validated APK was installed without phone UI interaction. Optional 1–12-month validity is implemented in the API, including history/snapshots and rejection of fractional integer input; 201 API/PostgreSQL scenarios passed. Dashboard ledger review shows the stored carry breakdown and original calendar deadline; unit, browser and real API/worker integration tests passed. Canonical balance/sync reads now include the stored carry breakdown and frozen deadline, without changing ledger versions solely because time passes; API/PostgreSQL, domain and worker regressions passed. Carry-expiry history and recovery are implemented in the dashboard, with finite visibility-owned polling and original-command recovery. An isolated API/PostgreSQL/worker browser scenario verified a lost recovery response, retained attempts and exactly one ledger debit. Scoped history/detail/recovery APIs are implemented and tested, including frozen labels, bounded outcomes, safe worker metadata, current authority and recovery races. Its durable worker/domain recovery and [accounting, request, adjustment and closing integration](leave-carryover.md) are tested, including original funding, source expiry before rollover, frozen batch timezone, current assurance, cancellation, scope and migration of existing facts. Public closings create tracked awards and accept the optional validity configured in the dashboard. [Accrual schedules](leave-accrual-schedules.md) are implemented across the backend, worker and dashboard, retaining 1–12 reviewed periods, policy snapshots and an explicit employee cohort. API/worker assurance, rollback, capacity and maximum 5,000-employee admission tests are validated. An isolated API/PostgreSQL/worker browser scenario verified original-command recovery, mixed eligibility outcomes, one ledger grant per posting, current progress, company replacement and future due times through cancellation/resume. Company and direct-report calendars are implemented in the API and dashboard, with bounded month/date queries, retained request-navigation context and hidden-read cancellation. Backend scope/MFA races and browser lifecycle/layout tests passed; an isolated API/PostgreSQL browser scenario verified half-day counts, withdrawal and company replacement. Reviewed company accrual/year-closing batches, per-employee outcomes, retained attempts and explicit resume are implemented in the backend, worker and dashboard. Browser acceptance verified original-command recovery, mixed eligibility outcomes and a single year-end carryover against an isolated API/PostgreSQL/worker fixture. Dashboard request creation includes scoped employee/evidence selection, server calculation, preview conflicts and original-command recovery, verified against an isolated API/PostgreSQL instance. Mobile balances, filtered history, request details, submission with published document evidence, and durable withdrawal/cancellation flows are implemented and verified against the development API. |
| Communications | Actual token registration/recovery/retirement now passes; scoped FCM hint delivery and iOS/physical URL acceptance remain. HRIS URL routing is implemented in Mobile `6c200387`; real API/Android URL acceptance now passes login/company gating, scope rejection, repeated/resumed navigation, unchanged read state and archival across offline process restoration. Dashboard audience groups, drafts, bounded named audience selection, publication review, preview, scheduling, cancellation/recovery and archival, and the mobile inbox with durable read/acknowledgement commands are implemented. |
| Identity administration | [Cross-account app sessions](dashboard.md#account-app-sessions) are implemented in the API and dashboard with global authorization, ordered guards, current MFA/recent proof and idempotent revocation. All 3,685 dashboard unit tests, 36 related browser scenarios and two isolated API/PostgreSQL session workflows passed, including lost-response recovery, account replacement and retained independent devices/cookies. All 94 selected API/PostgreSQL, 40 identity domain/data and 81 worker tests passed, including rotation races, cancelled writes and retained independent devices/accounts. [Periodic native-session cleanup](modules/identity.md#native-session-cleanup) is implemented with worker-only access, bounded batches, retained reuse evidence and cancellation; 22 native API, 40 identity domain/data and 81 worker tests passed. [Own-account app sessions](dashboard.md#app-sessions) are implemented in the dashboard, including confirmed revocation, response-loss recovery, cancellation and metadata cleanup. All 3,653 unit tests, 17 related browser scenarios and an isolated API/PostgreSQL workflow passed; the latter verified revoked native credentials, independent devices/accounts and dashboard account replacement. Dashboard SSO binding creation, bounded/exact review and versioned activation are implemented. All 3,624 unit tests, 31 related browser scenarios and an isolated API/PostgreSQL flow verified immutable identity values, original-command recovery, activation versions and cookie/native-session revocation. Dashboard invitations, explicit resends, public acceptance and password recovery are implemented. All 3,595 unit tests, 28 related browser scenarios and an isolated API/PostgreSQL/worker flow verified loopback SMTP, replacement links, activation without grants, lost-response recovery and old cookie/native-session revocation. Global account search/review/access editing, new company assignments, existing membership/grant review/editing and company role creation/editing/archival/history are implemented. Assignments require active accounts and current roles without overwriting existing membership; prior browser/API workflows verified retained role evidence, reactivation, self-edit session revocation and independent native-session company isolation. |
| Reporting | The [export backend lifecycle](report-exports.md) is implemented and validated with controlled storage: commands, bounded worker execution, owned history, authorized CSV downloads and retention. Dashboard export review/history/progress/cancel/recovery/download has 3,910 dashboard and 26 distinct Chromium checks across focused runs. Real API/PostgreSQL/worker/Garage-S3/browser acceptance passed private owner/source checks, lost-admission recovery and exact 1,201-row multipart downloads; real storage response loss now passes cancellation/resume with retained parts, original expiry and exact final output; physical expiry retains controlled-provider checks. Personal saved filters have API/dashboard acceptance, owner/company isolation, typed authoring, confirmed deletion and original-command recovery through MFA and lost responses. All 3,872 dashboard tests, 25 distinct browser cases and an independent-owner API/PostgreSQL workflow passed. The report catalogue has scoped API/dashboard and independent-reader revocation acceptance. Historical attendance closings have API/dashboard and exact-revision acceptance. Operational headcount, attendance exceptions, leave, overtime and expense reports, plus published payroll costs, have API/dashboard acceptance; see [reporting](reporting.md). |
| Operations | [Global audit](audit.md#platform-history) now has a separate immutable metadata projection, platform grant, current MFA/account guards and browser acceptance of independent access/revocation. [Saved account-access details](account-access-audit.md) now have both-grant authorization and real historical-disclosure acceptance; other source families remain metadata-only. [Workload measurements](workload.md) passed 20,000 attendance requests and a 5,000-employee payroll calculation in 85.972 seconds on the constrained local fixture; full-signature file measurement passed two 100-MiB PDFs, four checked downloads and EICAR rejection, correcting the total scan budget to 250 MiB. Other corpora, mixed workloads and dedicated-deployment measurements remain separate. [Recovery sets](recovery-set.md) passed real isolated schema-100 restoration including exact typed audit fields with old/current keys. [Integration review](integrations.md) includes six API flags and bounded actual worker publications, global authority/MFA and real revocation acceptance. [Deployment guidance](deployment.md) and the static Compose reviewer are implemented. Provider health/delivery and a target-specific authorized execution remain. |

Cross-company isolation, lost-response replay, denied/revoked access, offline
recovery, cancellation, late results, and resource disposal are acceptance
conditions for affected changes. Device checks are recorded separately from
builds and automated tests. Production signing, external provider registration,
releases, and live distribution need their actual configuration and authorization;
they cannot be represented as completed by a fixture.
