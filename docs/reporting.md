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

Group aggregation, other source metrics, immutable export jobs, drill-down, and report screens are subsequent capabilities. A client must not sum `persons` across companies and label it a unique group headcount.
