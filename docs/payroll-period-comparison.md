# Payroll period comparison

The dashboard's **Period comparison** action opens two original, successfully
calculated payroll runs from the same company. Choose an earlier earnings month
and its completed period explicitly; January and February can be reviewed without
rewriting either calculation, tax assessment, payslip or payment record. Later
amendments and payment activity remain in their existing review screens.

The comparison matches immutable targets by employment ID. A transferred or
rehired employee's new employment is distinct. Added/removed employees retain an
absent side, while monetary differences use zero for that absent side. Matched
employees are changed only when taxable gross, tax withheld or take-home pay
differs; a name change alone is not a monetary change. Both original names and
numbers remain available in employee detail alongside links to the retained run
results. All amounts are IDR.

## API and access

`GET /api/v1/companies/{companyId}/payroll/runs/{id}/comparison` accepts:

| Parameter | Contract |
| --- | --- |
| `baselineId` | Required UUID for a distinct run with an earlier earnings month. |
| `after` | Optional preceding employment UUID, ascending PostgreSQL UUID order. |
| `limit` | Default 50; 1–200. The dashboard always requests 50. |
| `changedOnly` | Default false; filters employee rows without changing whole-run totals. |

The response contains minimal baseline/current run references, whole-run totals
and employment coverage, a bounded employee page with exact monetary differences,
the applied filter and observation time. Money is serialized as two-place decimal
strings, calculated with `BigDecimal`, and validated/rendered using integer minor
units in the dashboard. No floating-point conversion is used, including aggregate
totals at 5,000 employees.

Reads require original and current payroll read, calculation or review authority,
active company/membership/account and current session credentials. The use case
takes the shared payroll policy guard before company, membership and account
guards, and checks MFA before and after data acquisition. This serializes against
payroll mutations while preserving the existing run authorization contract.
Company API admission applies as for other payroll endpoints. Finalization-only
authority does not grant comparison access.

Missing or foreign runs return `payroll_run_not_found`. Same-run or reversed-month
comparisons return `invalid_payroll_comparison`. Runs must be `CALCULATED` or
`FINALIZED` with every employee processed successfully and no failed results;
otherwise the read returns `payroll_comparison_unavailable`. The read never
combines partial results into apparently complete totals.

One SQL snapshot joins bounded immutable target/result projections: at most
5,001 targets per run detect a broken 5,000-employee invariant. Summary and page
come from that same snapshot; only the requested page plus one continuation row
crosses the datasource boundary. Repositories validate counts, monetary bounds,
ordering, presence and classification inside the database failure boundary.
No schema migration or additional storage is needed.

## Dashboard ownership

The company-pinned controller first acquires the selected run with one employee
row, then searches a single earlier month with a 20-period page. A baseline is
never selected automatically. Only completed candidate periods offer comparison;
the server separately checks complete success. Employee pages use stable UUID
cursors, retain whole-run totals and offer an explicit return to the first page.

Refresh, filter, route/company replacement, hiding and disposal abort owned
requests and clear old monetary state and employee detail. A late response cannot
publish into another selection. Foreground reacquisition settles with workspace
identity admission, avoiding a duplicate read before the current grants arrive.
Failures clear previous values; there is no automatic retry or background poll.
Employee detail uses the acquired page without another comparison request.
English/Indonesian and dark/narrow layouts use the existing Fluent components and
contained horizontal table scrolling.

## Validation — 29 October 2026

Backend acceptance includes actual independent monthly input verification,
calculation, staged review and baseline finalization; exact three-amount
reconciliation; added and removed employment across cursor pages; failed and
incomplete run refusal; invalid/reversed/foreign scope; permission loss while
waiting for an account guard; and MFA expiry after data acquisition. Counts prove
comparison does not create another run or audit mutation. Repository cases cover
incompatible raw rows, negative withholding, scale/aggregate bounds, ordering,
pagination and cancellation propagation. OpenAPI asserts admission, required
baseline, UUID cursor and decimal-string fields.

Dashboard cases cover exact cents, a one-cent difference above JavaScript's safe
integer range, final run version 25, malformed arithmetic/schema/cursors, replaced
and disposed controllers, and cancelled reads. Chromium exercises period
selection, both retained identities and links, changed-only filtering, company
replacement, hide/foreground admission, revoked grants, invalid monetary
responses, the 50/1 employee page boundary and Indonesian dark layout at 390px.

The separate `integration-worker/payroll-comparison.spec.ts` creates five
independently authenticated fictional actors and uses public commands, actual
PostgreSQL and the real worker. January's independently verified zero opening,
input and approved publication become February's retained tax predecessor; a
salary change produces independently checked baseline/current/difference amounts.
The browser selects those runs, opens employee detail and changes the filter
without company writes. Revoking calculation authority clears the displayed
amounts and rejects the next API read. Its fixture owns and cleans up all sessions,
services and database resources. No storage fault or provider response is used.

Dashboard full check passed 3,997 tests in 352 files (56.38s), TypeScript,
architecture and Biome (two existing CSS specificity warnings). Production build
and both inspected browser layouts passed. Exact outcomes are retained in
[measurements](measurements/payroll-comparison-2026-10-29.json).

See [agent handoff](agent-handoff.md) for final measured outcomes and commands.
This administrative HRIS capability requires no Mobile or Starter implementation.
