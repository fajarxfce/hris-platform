# Reporting API

Reports expose explicit definitions and company-scoped projections. `reports.read` is required together with the corresponding company-wide source permission. Self/team permissions do not authorize company totals. Client policy gates reporting requests separately from authorization.

## Headcount

`GET /api/v1/companies/{companyId}/reports/headcount?asOf=2026-10-01`

Requires current `reports.read` and `people.read`. Dates range from 1900 through 2100. The response contains `companyId`, `asOf`, `evaluatedAt`, `definitionVersion: "headcount.v1"`, and integer counts:

| Field | Definition |
| --- | --- |
| `employments` | Eligible employment records on the selected date. |
| `persons` | Distinct persons attached to those records; never the sum of per-bucket distinct counts. |
| `active`, `probation`, `suspended` | Employment counts by effective status; together they equal `employments`. |
| `permanent`, `fixedTerm` | Employment counts by effective contract kind; together they equal `employments`. |

For each employment, select the latest effective date on or before `asOf`, then the highest non-cancelled revision at that date. Include statuses `ACTIVE`, `PROBATION`, and `SUSPENDED` when the start date has arrived and the inclusive end date has not passed. `ENDED` records are excluded. Suspension still counts as employment; attendance eligibility is a different definition. Missing buckets and an empty company return zero.

This is effective history as currently recorded. A later backdated correction can change an earlier date's report. A future date projects currently scheduled revisions. `evaluatedAt` identifies evaluation time; it does not turn a report into an immutable payroll or historical publication snapshot. Financial/closed-period reports must consume their own finalized evidence.

The report returns aggregates only, without names, account identities, or employee profiles. One SQL snapshot computes all buckets and the distinct total. The use case holds shared company/membership/account guards and rechecks original versus live permission and credential state before reading. Employment changes can proceed independently: a committed change is included wholly in a subsequent snapshot. This read does not need the people mutation guard or a directory repository. Cancellation propagates and releases transaction resources.

The result is bounded to fixed scalar counts. The database query uses effective-date indexes, executes under normal company RLS and transaction timeouts, and does not materialize an employee directory in application memory. `invalid_report_date` is a stable localizable validation code. Technical exceptions use the existing sanitized database failure contract. Responses are `no-store`; any client cache must remain account/company scoped.

The dashboard exposes this report at `/reports/headcount` with an applied date filter, localized counts, and request ownership scoped to account/company. See [dashboard](dashboard.md) for browser behavior and checks.

## Group headcount

`GET /api/v1/reports/headcount?companies={firstId},{secondId}&asOf=2026-10-01`

Select one to 32 distinct company IDs. The global route does not grant platform administrators implicit access: every selected company requires active membership and both `reports.read` and `people.read`. Invalid or duplicate selections return `invalid_report_companies`. Missing access or unavailable client policy fails the entire request; companies are never silently omitted from totals.

The response contains `asOf`, `evaluatedAt`, `definitionVersion`, `totals`, and an ordered `companies` list of `{companyId, counts}`. Each count object uses the definition above. Employment/status/contract totals equal the sum of company buckets. `totals.persons` is a database distinct count across the selected companies, so one person employed by two companies counts once globally and once in each company. Empty selected companies are present with zero counts. Names and profile data are not returned.

The use case captures company grants, then acquires ordered policy, company, membership, and account guards. It rechecks those grants, active access, credential version, and MFA assurance after waiting. Client build, `REPORTING` availability, and maintenance are evaluated for every company. The authenticated transport supplies whether the caller is native; query parameters cannot override it. Paired `X-HRIS-Client-Platform` and `X-HRIS-Client-Build` headers follow the [client policy contract](client-policy.md). Availability failures include a safe `companyId` parameter so a mobile client can identify the blocked selection and translate the existing code.

An explicit transaction port adds bounded SELECT scope only to employment history and client policy inputs. It does not add mutation permissions, expose person profiles, or bypass RLS. One SQL statement produces the complete group snapshot, with cancellation and the standard finite database timeouts. Ordinary and cross-company mutation transactions clear this extra read scope. Cache identity must include the account, sorted company selection, date, and definition version; replace the complete group result atomically and discard it when access is revoked.

## Operational attendance exceptions

`GET /api/v1/companies/{companyId}/reports/attendance-exceptions?from=2026-09-01&until=2026-10-01`

Requires both current `reports.read` and company-wide `workforce.read`, plus the
`REPORTING` client-policy gate. Self/team grants do not authorize aggregates. The
inclusive date window is one to 31 days, within 1900–2100. Invalid windows return
`invalid_report_range` with `maximumDays: "31"`.

The response contains `companyId`, `from`, `until`, `evaluatedAt`,
`definitionVersion: "attendance-exceptions.v1"`, `totals` and an ascending `days`
array of `{date, counts}`. Every requested date appears, including empty dates.
Each count object has these integer fields:

| Field | Definition |
| --- | --- |
| `observedDays` | Employment/date pairs with at least one captured event, including rejected events. |
| `affectedDays` | Employment/date pairs with pending evidence, incomplete accepted clocks, or both. |
| `affectedEmployments` | Distinct employments among those affected pairs. Across the whole window this is not the sum of per-date values. |
| `pendingDays` | Employment/date pairs with at least one initially pending event still lacking a review. |
| `incompleteDays` | Employment/date pairs with exactly one accepted clock-in XOR exactly one accepted clock-out, and no approved attendance correction. |
| `pendingEvents` | Count of initially pending events with no review. |

The definitions reconcile with the existing workforce exception directory at the
same committed source state. A pair can be both pending and incomplete; it counts
once in `affectedDays`. A correction resolves raw-clock incompleteness but does not
hide subsequently received pending evidence. A review supersedes the event's
initial decision. Dates without captured evidence do not count as observed or
absent; this report does not infer missed shifts, lateness or payroll deductions.

This is the current operational exception state for the selected work dates.
Later reviews, evidence or corrections can change earlier-date counts. It is not
an immutable closing/payroll snapshot or an as-recorded-at historical query.
Closed-period financial reporting must use its own published work/payroll evidence.

One SQL statement computes the grand total and per-date buckets under normal RLS
and finite transaction/query timeouts. It returns at most 32 aggregate rows and
does not materialize employee profiles or raw evidence in application memory. The
mapper verifies cardinality and counter reconciliation before publishing the whole
result. Company, membership and account guards protect live authorization; original
and current grants, credential version and MFA are checked after lock acquisition,
and MFA is checked again after the read. Concurrent evidence/review commits cannot
mix daily and total snapshots. Cancellation releases transaction resources, and
errors remain sanitized. Responses are `no-store`.

The dashboard at `/reports/attendance-exceptions` applies company-bound URL date
filters, renders the six totals and daily table, and opens the existing workforce
exception directory for the selected date/company. Its date action stays visible
on narrow screens. Partial filter edits do not fetch until applied; browser history
restores applied filters. Company/account replacement and disposal cancel owned
requests and ignore late responses. A refresh removes prior values, and a denial
or failed retry cannot expose them. Client mapping checks scope, definition,
complete dates, safe integer bounds and reconciliation before publishing a report.
No report payload enters browser persistence or a shared query cache.

Other source metrics, historical closing reports and immutable export jobs remain
subsequent capabilities.

## Operational leave

`GET /api/v1/companies/{companyId}/reports/leave?from=2026-01-01&until=2026-12-31`

Requires both current `reports.read` and company-wide `leave.read`, with the
`REPORTING` client-policy gate. Team/self leave grants are insufficient. The
inclusive window is one to 366 days, within 1900–2200, allowing a complete leap
year. Invalid ranges return `invalid_report_range` with `maximumDays: "366"`.

The report selects the immutable requested **working dates** inside that window,
not submission dates or the whole envelope between a request's first and last day.
Scheduled off days and holidays excluded at submission remain excluded. Later
policy, roster or employment-label edits cannot rewrite the submitted facts. A
request crossing the report boundary contributes only its included portions.

The response has `companyId`, `from`, `until`, `evaluatedAt`,
`definitionVersion: "leave.v1"`, `totals`, and all five ordered `statuses` entries
(`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, `CANCELLATION_PENDING`). An empty
status has zero counts. Each count object contains:

| Field | Definition |
| --- | --- |
| `requests` | Distinct requests with at least one submitted working date in the window. |
| `employments` | Distinct employment records among those requests, not unique persons. The grand total is not the sum across statuses. |
| `days` | Exact decimal string: each full portion contributes `1`, each half portion `0.5`, clipped to the report window. |

Statuses are current. Approved leave awaiting cancellation approval stays separate
as `CANCELLATION_PENDING`; its ledger consumption remains until approval completes.
Rejected and cancelled requests remain in this report even after allocations are
released. Therefore the grand total describes submitted request history, **not**
active occupancy, consumed balance, entitlement, payroll expense, or a closing
snapshot. Totals for requests and days reconcile with the status buckets; employment
counts use an independent distinct total. Subsequent decisions can move requests
between buckets without changing their frozen dates or overall requested days.

`GET /api/v1/companies/{companyId}/reports/leave/requests?from=2026-01-01&until=2026-12-31&status=APPROVED&limit=50`

This drill-down uses the same source definition and authorization. Optional `status`
selects one bucket; omission selects all statuses. `limit` is 1–200 (default 50).
The response repeats the company/window/status, evaluation time and definition,
with `items` and `nextCursor`. Items contain the request ID, employment ID, submitted
employee/type labels, current status/version, submission time, clipped `days`, and
`requestDays` for the complete submitted request. Reasons, attachments, private
profiles and approval assignments are not acquired by this projection. Authorized
clients can open the existing leave-request detail separately.

Requests sort by immutable submission time then UUID, both descending. The bounded
opaque `after` token retains company, dates, status and that ordering key. Reusing it
for another filter returns `invalid_page`. A last-row status change does not break
continuation because no current-row lookup is needed. Each page is a fresh read;
counts/pages may change after new submissions or decisions. Refresh from the first
page to reconcile a later state. This is not an immutable export cursor.

One SQL snapshot produces each aggregate or page under normal RLS. The shared raw
projection expands only overlapping request snapshots and filters their actual
working dates; a company/date-envelope index supports candidate selection. At most
six aggregate rows or 201 drill-down rows reach application mapping. Counts, units,
status completeness, ordering, duplicates and continuation progress are checked
inside the database failure boundary. Query/lock/transaction timeouts remain finite.
Company, membership and account guards retain current scope, original/live grants,
credential version and MFA checks after waiting; assurance is checked again after
acquisition. Cancellation unwinds the read and failures stay sanitized. Responses
are `no-store`. Reporting does not mutate operational leave.

The dashboard exposes `/reports/leave` and `/reports/leave/requests`. The default
window runs from the active company's current year start through its local date.
Applied URL dates remain separate from form edits; browser history restores them.
Status rows and the all-requests action open the exact company/window drill-down.
Request pages retain status and bounded continuations, with first/next controls.
The detail, action completion and history routes preserve validated report return
context; a foreign company or invalid cursor cannot import that selection.

Counts and decimal days are mapped and reconciled before publication. Exact day
formatting avoids floating-point conversion even beyond JavaScript's safe integer
precision. The UI distinguishes clipped days from the full request. Replacement,
refresh, disposal and authorization failures clear the owned result and cancel or
ignore obsolete reads. No report data enters persistent storage or a shared cache.
Status and request actions occupy the first table column on narrow screens. These
operational reports do not close historical reporting, financial metrics or exports.

## Operational overtime

`GET /api/v1/companies/{companyId}/reports/overtime?from=2026-01-01&until=2026-12-31&status=APPROVED`

Requires `reports.read` and company-wide overtime access (`overtime.read`,
`overtime.manage` or `workforce.read`), with the `REPORTING` client-policy gate.
Self/team and approval-only permissions do not authorize company aggregates.
The inclusive window is one to 366 work dates within 2000–2100. Invalid windows
return `invalid_report_range` with `maximumDays: "366"`, `minimumYear: "2000"`
and `maximumYear: "2100"`. Optional `status` selects the current `PLANNED`,
`PENDING`, `APPROVED`, `REJECTED` or `WITHDRAWN` state; omission includes all.

The response repeats `companyId`, `from`, `until`, optional `status`, `evaluatedAt`
and `definitionVersion: "overtime.v1"`. `totals` and each ordered `months` entry
contain the following exact integer counts. Every calendar month intersecting the
window appears, including empty months, with `month` (`YYYY-MM`), clipped inclusive
`from`/`until` dates and `counts`. At most 13 month buckets are returned.

| Field | Definition |
| --- | --- |
| `requests` | Requests whose frozen work date falls in the selected window and current status. |
| `employments` | Distinct employment records among those requests. The overall total is independent of monthly distinct counts. |
| `actualRequests` | Requests with submitted actual times, including subsequently rejected or withdrawn actuals. |
| `requestedMinutes` | Net requested elapsed minutes, after the frozen requested break. |
| `actualMinutes` | Net submitted actual elapsed minutes, after the frozen actual break. Unsubmitted plans contribute zero. |
| `approvedMinutes` | Net actual minutes with completed approval. Partial staged approval contributes zero. |

An overnight request belongs wholly to its original work date and month; it is
not split at midnight. Requested and submitted actual times, breaks and work dates
retain their stored facts after schedule/holiday changes. Rejected or withdrawn
requests remain in selected history, including any already submitted actuals.
These are operational quantities, not payable hours, payroll amounts or immutable
closing snapshots. Later decisions can change current status and approved totals.

A month can be reconciled through the existing overtime directory using its exact
company, clipped `from`/`until` and optional `status`. Its normal resource grants,
pagination and detail/actions remain authoritative. Each subsequent directory page
is a fresh read; it is not pinned to the report's database snapshot.

One SQL statement groups the total and months under normal company RLS and finite
query/transaction timeouts, using the existing company/work-date index. At most
14 raw aggregate rows reach mapping. Mapping validates cardinality, complete month
bounds, state-consistent quantities, exact additive reconciliation and distinct
employment bounds. Shared company, membership and account guards retain original
and live authority, credentials and MFA after waits; assurance is checked again
after acquisition. Concurrent decisions cannot mix total and monthly snapshots.
Cancellation releases resources, failures are sanitized and responses are `no-store`.
No new database migration or operational write is required.

The backend passed 31 reporting domain/data tests, 61 workforce domain tests,
84 selected HTTP/PostgreSQL/OpenAPI tests and all 81 worker tests on 28 October
2026. Coverage includes every status, actual/plan withdrawal, overnight work dates,
frozen calendar facts, staged approval, receipt replay, monthly directory
reconciliation, empty partial months and leap windows, current source/report
permissions, RLS, client availability, revoked grants/credentials while waiting,
MFA expiry before/after reading, cancellation, sanitized recovery and a concurrent
approval committed during a held report snapshot. Architecture and formatting
passed. OpenAPI contains 353 paths in 2,039,264 bytes. Logs:
`.work/overtime-report-unit.log`, `.work/overtime-report-validation.log`.
The dashboard at `/reports/overtime` defaults to the active company's year-to-date
window. Date/status form edits wait for Apply; company-bound URL parameters retain
applied filters through Back/Forward. Six totals and complete monthly rows show
exact net minutes and independent employment counts. The first column opens the
existing overtime directory with the same company, clipped month and status.
Empty months retain zero counts with disabled actions. Pending refreshes, disposal,
company/account replacement and access failures remove old values and cancel or
ignore obsolete reads. Response mapping validates scope, version, complete buckets,
state-consistent quantities and exact reconciliation before publication. No report
payload enters browser persistence or a shared cache.

Dashboard validation passed all 3,801 unit/UI tests in 319 files, architecture,
Biome and TypeScript, production build, and 12 distinct report Chromium scenarios
covering overtime, attendance and leave. The four overtime cases passed in the final
rerun after correcting a test expectation for the directory's explicit `limit=50`.
A focused 33-test reporting/localization run also passed after separating invalid
report-status copy from the directory's shorter date-limit message. English/Indonesian
and desktop/390px layouts were inspected.

The existing real API/PostgreSQL overtime browser workflow now also verifies annual
report totals, pending-status month navigation and browser return, then independently
approves/rejects or withdraws requests through the existing guarded UI. It preserves
original calendar facts, recovers lost plan/actual/decision/withdrawal receipts and
replays original receipts without duplicate totals. Four requests for one employment
reconcile to 540 requested, 375 actual and 135 approved minutes; the two withdrawn
requests separately retain 240 requested and 120 actual minutes. Explicit refresh
keeps that status selection. The owned fixture stopped its API and removed its
database. Existing bundle/CSS warnings do not establish a performance result.

Logs: `.work/overtime-dashboard-check.log`, `.work/overtime-dashboard-final-unit.log`,
`.work/overtime-dashboard-browser.log`, `.work/overtime-dashboard-final-browser.log`
and `.work/overtime-dashboard-integration.log`. Screenshots:
`.work/dashboard-overtime-report-real.png` and `.work/dashboard-overtime-report-mobile.png`.

## Operational expenses

`GET /api/v1/companies/{companyId}/reports/expenses?from=2026-01-01&until=2026-12-31`

Requires `reports.read` together with company-wide `expenses.read` or
`expenses.manage`, plus the `REPORTING` client-policy gate. Self/team, assigned
reviewer and payment-only authority do not authorize company totals. The inclusive
window is one to 366 transaction dates within 1900–2200. Invalid windows return
`invalid_report_range` with `maximumDays: "366"`.

Each claim contributes only the immutable lines from its **latest submission**,
filtered by their original `occurredOn` transaction date. Unsubmitted drafts are
excluded. After a return or withdrawal, later draft edits do not replace the
submitted facts until a new submission succeeds. Resubmission replaces that
claim's selected submission instead of adding another copy of its expenses.
A line outside the window contributes nothing, even if its claim has another
line inside. Claim creation, submission timestamps and current category settings
do not substitute for these transaction dates.

The response contains `companyId`, `from`, `until`, `evaluatedAt`,
`definitionVersion: "expenses.v1"`, `currency: "IDR"`, `totals` and all six current
claim `statuses`: `DRAFT`, `PENDING`, `RETURNED`, `APPROVED`, `REJECTED`, `CANCELLED`.
Empty statuses have zero counts. Each count object contains:

| Field | Definition |
| --- | --- |
| `claims` | Distinct claims with at least one latest-submitted line in the window. |
| `employments` | Distinct employment records associated with those claims, independently counted for the total. |
| `lines` | Selected transaction lines from those submissions. |
| `amount` | Exact decimal IDR string summed from the selected immutable lines. Aggregate amounts may exceed a single claim's limit. |

`DRAFT` includes previously submitted claims that are now being corrected;
unsubmitted drafts still remain excluded. Return/rejection/cancellation preserves
submitted history. A staged approval remains `PENDING` until every stage completes.
The report describes submitted expense amounts at current claim status, not
cash paid, outstanding settlement, recognized accounting expense or an immutable
historical publication. Payment reservations, partial bank results and settlement
have their own definitions. Later submissions or decisions can change old-date
report results; `evaluatedAt` does not freeze them.

`GET /api/v1/companies/{companyId}/reports/expenses/claims?from=2026-01-01&until=2026-12-31&status=APPROVED&limit=50`

The drill-down uses exactly the same latest-submission and transaction-date
projection. It accepts optional current `status`, `limit` 1–200 (default 50), and
an opaque `after` cursor. The response repeats company, window, status, currency,
evaluation time and definition, then supplies `items` and `nextCursor`. Each item
has claim `id`, exact `submissionId`/`submissionNumber`, employment ID and submitted
employee labels/title, current claim `status`/`version`, `submittedAt`, clipped
`lines`/`amount`, and the full frozen `submissionAmount`. Private profiles, draft
changes, reasons, receipts and payment destinations are not acquired by this view.
Authorized clients can open the identified immutable submission separately.

Ordering is descending submission time and claim UUID. The bounded cursor retains
the report definition, company, date window, status and ordering key. It does not
look up the previous row, so a status change does not invalidate continuation.
Each page is a fresh read: a resubmitted claim can move forward in the ordering or
leave the selected dates. Refresh from the first page to reconcile the latest
state. These are not immutable export cursors. The ordinary expense directory
filters claim creation dates; it cannot replace this transaction-date drill-down.

One SQL projection joins the latest submission to its frozen draft lines and
submitted-line references, with coherent claim aggregates and total/status buckets.
V92 adds a company/transaction-date index to support source selection. Normal RLS
and finite query/transaction timeouts apply. At most seven aggregate rows or 201
page rows reach application mapping. Mapping checks counts, exact cent quantities,
claim limits, distinct-employment bounds, reconciliation, duplicate/order/cursor
progress and frozen reference metadata inside the sanitized database boundary.
Shared company, membership and account guards retain original/live report/source
permissions, credentials and MFA after waits, with assurance checked again after
acquisition. Cancellation unwinds resources. Responses are `no-store`; reporting
performs no operational mutation.

On 28 October 2026, this backend passed 42 reporting domain/data/delivery tests,
20 expense domain tests, 153 distinct selected API/PostgreSQL/OpenAPI tests and all
81 worker tests. The first API run passed 146 cases; the final 13-case run repeated
seven report cases and added payment coverage. It verified exact cents and totals
above the individual claim limit, complete statuses, distinct employments, date
gaps, excluded unsubmitted drafts, retained facts through category/draft edits,
replacement on resubmission (including reuse of one draft revision), staged review,
withdrawal/cancellation, stable continuation after status changes, source/report
authority, RLS, client availability, revocation during admission, MFA expiry before
and after acquisition, cancellation, sanitized recovery and concurrent decision
serialization. Real payment preparation/release, partial success, confirmed failure,
retry reservation and receipt replay leave the submitted totals unchanged.

Architecture and formatting passed. OpenAPI remains bounded at 2,051,708 bytes with
355 paths. The worker application suite validated the shared expense-domain change
and V92 migration. Logs: `.work/expense-report-unit.log`,
`.work/expense-report-validation.log`, `.work/expense-report-followup.log` and
`.work/expense-report-final-build.log`.

### Expense dashboard

`/reports/expenses` applies company-bound transaction dates and displays claim,
employment, line and exact IDR totals with all six current status buckets.
`/reports/expenses/claims` retains those dates and optional status, with 50-row
first/next navigation. It shows submitted employee/title labels, submission number,
lines in the period, the selected amount and the full submission amount separately.
Rows open the exact `submissionId`; the existing claim link remains available.
A validated same-company report return link preserves dates, status and page through
submission tabs, evidence selection, review actions and their result. Invalid
origins cannot introduce an arbitrary return URL or another company's cursor.
Transaction-date report filters do not become claim-directory creation dates.

The lazy routes own their requests per account/company/window. Refresh, replaced
filters and disposal clear old data and cancel pending work; obsolete successes
and failures cannot restore it. Mapping validates scope, definition, safe counters,
exact cent reconciliation, claim limits, order and cursor progress before publishing
frozen data. Decimal display shares a core formatter with Expenses without floating
point conversion. Forms retain edits until Apply; a company replacement resets to
its local year-to-date range and removes the prior status/page. No report data is
persisted or polled.

Dashboard acceptance on 28 October 2026 passed all 3,813 tests in 323 files,
architecture/Biome/TypeScript checks and the production build. Thirteen distinct
Chromium scenarios passed (five report scenarios and eight existing expense
scenarios), including localized amounts, 390px dark layout, first/next pages,
failed refresh/retry, source grants, invalid annual windows, company replacement,
and return to page two when the exact submission is unavailable. The final five
report scenarios were confirmed together after an earlier runner returned SIGTERM
after reporting all five passed.

An isolated API/PostgreSQL browser workflow enrolled separate administrator and
reviewer accounts, excluded an unsubmitted draft, clipped three submitted lines
from 223.56 IDR to 123.46 IDR within the window, and returned the claim through the
review UI while preserving the report filter. Saving a corrected draft retained
123.46 IDR and the original submitted title. Resubmitting replaced the selected
facts with one line/claim at 200.02 IDR and opened submission two; submission one
remained immutable at 223.56 IDR. The fixture cleaned up its API and database.
Logs: `.work/expense-dashboard-check.log`, `.work/expense-dashboard-browser.log`,
`.work/expense-dashboard-browser-confirmed.log` and
`.work/expense-dashboard-integration.log`. Desktop/light and narrow/Indonesian/dark
screenshots were inspected in `.work/dashboard-expense-report-real.png` and
`.work/dashboard-expense-report-mobile.png`. Existing CSS specificity and build
chunk-size warnings remain; this is no performance measurement.


## Published payroll costs

`GET /api/v1/companies/{companyId}/reports/payroll-costs?from=2026-01&until=2026-12`

Requires `reports.read` and the canonical company-wide `payroll.read` scope, plus
`REPORTING` client availability. Self payslip access, compensation/policy management,
amendment authority and payment permissions do not independently authorize these
company totals. Both parameters are earnings/tax months (`YYYY-MM`). The inclusive
window is one to twelve months within 2024–2100; it can cross a year boundary.
Invalid windows return `invalid_report_month_range` with `maximumMonths: "12"`,
`minimumYear: "2024"` and `maximumYear: "2100"`.

Each published person/month assessment contributes exactly one current published
revision. The query joins the assessment head to its immutable revision evidence;
it never adds original and amended results together. Draft/calculated/reviewed runs,
unpublished finalization jobs and unissued amendment calculations contribute nothing.
A later compensation, policy or workforce change cannot replace published facts.
An approved, successfully published amendment replaces the selected revision for
its affected months. This is the current published view, not an arbitrary historical
as-of statement. Separate exports or historical selections must pin their revisions.

The response contains company, month bounds, `evaluatedAt`,
`definitionVersion: "payroll-costs.v1"`, `currency: "IDR"`, `totals`, and an ordered
`months` array with every selected month, including zero months. Each count object
contains:

| Field | Definition |
| --- | --- |
| `assessments` | Published person/month assessments in the selection. |
| `employments` | Distinct employment records, counted independently for the overall total. |
| `revisedAssessments` | Selected assessments whose published revision is greater than zero; an assessment amended repeatedly still counts once. |
| `employerCost` | Sum of the stored monthly employer-cost result. |
| `taxableGross` | Sum of the stored taxable gross, independently from employer cost and cash pay. |
| `employeeDeductions` | Stored retirement, donation and other net deductions, excluding withholding. |
| `withheld` | Signed stored withholding, including negative final-period tax refunds. |
| `takeHome` | Stored nonnegative employee take-home amounts. |

All five money fields are exact decimal strings with two fractional digits. The
report performs no new salary or tax calculation. The published payroll policy's
employer cost includes cash earnings, taxable noncash amounts, tax/deduction
allowances and the retained employer old-age/pension contributions. Net pay, tax
and cost remain separate measures; a refund can make take-home exceed employer
cost. Bank reservations, release, reconciliation and confirmed recoveries do not
change these published amounts.

One company-scoped SQL snapshot returns at most thirteen aggregate rows. It reads
scalar calculation amounts from the security-invoker assessment-evidence view;
private profiles, frozen salary inputs, labels and payment destinations do not
enter the report payload. The existing `payroll_payslip_month` index supports month
selection; this increment adds no migration. Normal RLS and finite transaction/query
timeouts apply. Mapping rejects missing amounts, invalid decimal/count bounds,
duplicate or out-of-window months, inconsistent employee counts and non-reconciling
totals. Cancellation and technical mapping failures stay inside the existing safe
database boundary. Company, membership and account guards retain original/live
report and source authority, credentials and MFA before and after acquisition.

A month can be reconciled with the existing `/payroll/payslips` directory using
that same month for `from` and `until`. Its summaries retain each exact published
revision for detail review. These are independent live reads; a publication between
reads can change the selected latest revision. Responses are `no-store`.

Backend validation passed 49 reporting domain/data/delivery tests, 114 payroll
domain tests, 101 distinct selected API/PostgreSQL/OpenAPI tests, and all 81 worker
tests. The API selection includes the eleven new cost-report scenarios, existing
payslip/source assurance, amendment publication and 57 contract cases. Coverage
reconciles four published months against exact payslip revisions, including a
December tax refund, two successive amendments, unchanged amounts through bank
settlement and publication receipt replay, exclusion before publication, later
compensation changes, zero months, RLS, independent source/report grants, module
availability, revocation, MFA expiry, cancellation, recovery and a coherent read
during concurrent publication. Data checks also retain cents beyond the binary
floating-point safe integer range and reject inconsistent aggregates.

The final four-case API rerun passed after correcting test fixtures for explicit
payroll grants, Jackson array iteration, future-effective compensation and the
existing 422 validation status. Production access and frozen-period policy remain
unchanged. Architecture, formatting and both application boot JAR builds passed.
OpenAPI has 356 paths and is
2,057,966 bytes, within its 2 MiB bound. Logs are retained locally in
`.work/payroll-cost-{unit,validation,api,api-final,api-followup,build}.log`.
The dashboard is described below; historical/export acceptance remains open.
No performance or live deployment claim is made.

### Payroll cost dashboard

`/reports/payroll-costs` uses company-bound earnings-month filters with a maximum
of twelve months. The initial selection is the company's local year through the
current month; replacing company resets it. The page shows published and revised
payslip counts, distinct employment records, employer cost, taxable gross, employee
deductions, signed withholding and take-home pay. Complete month rows retain zero
buckets. A nonempty row opens the company payslip directory for that exact month
with `scope=AUTHORIZED`; browser Back restores the applied report range. These
remain independent live reads, so a new publication may change the latest facts.

Navigation and load policy require both report and canonical company payroll read
grants. DTO/repository mapping checks company/range/definition/currency, bounded
ordered months, safe integer counts, distinct employment bounds and exact decimal
reconciliation. BigInt cents preserve refunds and sums beyond binary float precision;
the shared core IDR formatter now retains negative sub-rupiah signs. Frozen results
are detached from transport values. Each mounted controller owns and cancels its
read, clears data on refresh/disposal/denial and rejects obsolete responses. There
is no report polling or shared report cache.

All 3,823 tests in 326 dashboard files passed, together with architecture, Biome,
TypeScript and production build checks. Fourteen distinct Chromium scenarios
passed across focused runs: five cost-report cases and nine existing payslip cases.
The final five-case run passed in eight seconds after correcting a fixture's
expectation for the directory's explicit empty search query. Tests cover annual
bounds, current source grants, exact signed amounts, malformed aggregates,
cancellation, late results, denial/retry, applied filters and browser history,
company replacement/logout, zero months and narrow Indonesian/dark layout.

The real API/PostgreSQL/worker calculation/publication/payment workflow also passed
in 1.7 minutes including fixture startup. A separate MFA-enrolled report reader
verified zero before publication, exact reconciliation against all five amounts in
the published statement, unchanged totals after bank payment, and one revised
assessment after independent amendment publication and confirmed return workflows.
The latest report reconciled with revision one while the original statement stayed
unchanged. Monthly directory/detail navigation and browser return retained the
applied range. The desktop English/light and 390px Indonesian/dark screenshots
were inspected; actions remain in the first table column and overflow stays inside
the table. Existing CSS-specificity and Vite chunk warnings remain. This does not
claim a performance benchmark or deployment.
Local logs: `.work/payroll-cost-dashboard-{check,browser,browser-final,integration}.log`.
Historical closing reports, saved filters and exports remain separate open work.

## Validation

On 28 October 2026, leave reporting passed 25 reporting domain/data/delivery tests,
90 selected HTTP/PostgreSQL/OpenAPI tests and the 81-test worker suite. Architecture
and formatting checks passed. The new coverage exercises exact half-day output,
all statuses, partial windows and gaps, excluded off days, frozen policy/roster
facts, cancellation refunds, distinct employments, date/page/cursor bounds, matching
aggregate and drill-down semantics, RLS and source grants, REPORTING availability,
revoked credentials/permissions after waits, MFA expiry before/after acquisition,
read cancellation, sanitized failure recovery, and concurrent decision serialization.
The real PostgreSQL continuation test caught a missing timestamp parameter cast;
the corrected query passes after the previous row changes status. OpenAPI remains
bounded at 2,032,863 bytes with 352 paths. Evidence is in
`.work/leave-report-unit.log`, `.work/leave-report-validation.log` and
`.work/leave-report-format.log`. The complete worker suite ran after migration V91
and remained up to date in the final targeted rerun. Dashboard/browser acceptance
for this report subsequently passed the checks below.

The leave dashboard passed all 3,793 unit/UI tests across 317 files, architecture,
Biome and TypeScript checks, and 19 Chromium scenarios covering leave reports,
existing attendance reports and leave calendar return flows. A real API/PostgreSQL
fixture completed MFA, created two half-day requests for one employment, reconciled
the report and scoped rows, withdrew a request through the detail/action UI, returned
to the original pending filter, selected the cancelled request, and verified that
requested totals stayed unchanged while status buckets moved. The existing real
calendar withdrawal/return scenario also passed. Both completed in the same owned
fixture, which removed its API process and database. The report layout was inspected
at desktop and 390px widths. A focused follow-up also confirmed retained page cursors
and rejected incompatible status changes in return navigation.

Logs: `.work/leave-dashboard-check.log`, `.work/leave-dashboard-browser.log`,
`.work/leave-dashboard-integration.log`, `.work/leave-origin-validation.log`.
Screenshots: `.work/dashboard-leave-report-real.png` and
`.work/dashboard-leave-report-mobile.png`. Production build passed; existing large
bundle/CSS warnings do not establish a performance result.


On 28 October 2026, operational attendance reporting passed 15 reporting-domain/data
tests and 83 selected HTTP/PostgreSQL/OpenAPI tests. Sixteen new regressions cover
counter reconciliation, overlapping kinds, distinct employment counts, empty dates,
corrections and late evidence, date bounds, row isolation, source/report grants,
reporting availability, pending revocation, MFA expiry before/after the query,
concurrent review snapshots, cancellation and sanitized failure recovery.
Architecture and formatting passed; the 81-test worker suite remained up to date
because its classpath did not change. Logs are `.work/attendance-report-validation.log`
and `.work/attendance-report-format.log`. Dashboard and export acceptance are still
active work; these backend checks do not claim historical closing-report coverage.

The dashboard subsequently passed its full 3,781-test suite, architecture and
TypeScript checks, 11 attendance/headcount Chromium scenarios and production build.
All four attendance scenarios passed again after moving drill-down to the date
column for narrow screens. The real API/PostgreSQL browser fixture completed MFA,
captured offline evidence, opened the report and drilled into the same company/day,
recovered a lost committed review response, reconciled overlapping exception kinds,
and retained raw evidence through corrections and late-event rejection. Report counts
followed those committed changes. The owned fixture stopped and removed its database.
Evidence is in `.work/attendance-dashboard-check.log`,
`.work/attendance-dashboard-browser.log`, `.work/attendance-dashboard-final-browser.log`
and `.work/attendance-dashboard-integration.log`; desktop/mobile screenshots are
`.work/dashboard-attendance-report-real.png` and `.work/dashboard-attendance-report-mobile.png`.

Dependency audit found advisories in Zod 4.6.5 and the test-only jsdom dependency
undici 8.11.2. Zod is now pinned to 4.6.7 and the lockfile resolves undici 8.12.3;
these were the only changed package versions. The complete and runtime-only audits
reported zero known vulnerabilities after the update. The dashboard/browser checks
above used those versions.

The company/group reporting check passed architecture and format checks, server/worker builds, and 61 tests: 10 reporting domain/data, 15 database, and 36 HTTP/PostgreSQL. Coverage includes shared persons, empty companies, effective history, selection bounds, RLS read/write isolation, pooled context cleanup, native/browser build gates, maintenance, module flags, permission and credential revocation while waiting, expired MFA, coherent concurrent snapshots, and cancellation. OpenAPI validates 228 paths and 254 operations. No group browser workflow, device validation, deployment, or group-report performance benchmark is claimed by this backend check.
