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

Other source metrics, immutable export jobs, and drill-down are subsequent capabilities.

## Validation

The company/group reporting check passed architecture and format checks, server/worker builds, and 61 tests: 10 reporting domain/data, 15 database, and 36 HTTP/PostgreSQL. Coverage includes shared persons, empty companies, effective history, selection bounds, RLS read/write isolation, pooled context cleanup, native/browser build gates, maintenance, module flags, permission and credential revocation while waiting, expired MFA, coherent concurrent snapshots, and cancellation. OpenAPI validates 228 paths and 254 operations. No group browser workflow, device validation, deployment, or group-report performance benchmark is claimed by this backend check.
