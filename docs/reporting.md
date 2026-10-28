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

## Validation

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
