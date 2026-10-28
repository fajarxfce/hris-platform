# Reporting

Own read models, metric definitions, scoped queries, asynchronous exports, and export retention.

Reports cover headcount/employments, attendance exceptions, leave, overtime, expenses, and payroll costs. Group reports only aggregate authorized companies. Distinguish unique persons from employment count.

Historical reports use corresponding snapshots. Run large exports in bounded batches and recheck access on download. Saved filters never grant access. Reporting cannot mutate operational data.

Screens: role overview, report catalogue, filters, drill-down, export history.

Acceptance: totals reconcile, row/resource isolation, historical definitions, export interruption, bounded memory, and expired export access.

Operational attendance exception aggregates are implemented in the backend with
report/source permissions, a 31-day window, coherent totals/day buckets and current
MFA checks. They reconcile with the workforce exception directory; missing evidence
does not imply absence, and the live exception state does not replace closing
snapshots. The dashboard applies company-bound date filters and drills into the
authorized exception directory; real API/browser acceptance covers review and
correction reconciliation. See the exact
[report definitions and validation](../reporting.md#operational-attendance-exceptions).

Company headcount is implemented with effective-date status/contract counts, a separate distinct-person total, current source/report permissions, a single bounded aggregate response, and a responsive dashboard report. Group headcount selects up to 32 independently authorized companies and computes the distinct global person count in the same SQL snapshot as company buckets. Its explicit SELECT scope adds no mutation permission; access, MFA, and client policy are rechecked after ordered guards. The dashboard supports applied company/date filters, company breakdowns, and cancellation of obsolete group reads. [Reporting API](../reporting.md) documents the exact definitions and historical limits. Other metrics and exports remain planned.

Operational leave reporting and its scoped request drill-down are implemented with
one to 366 submitted work dates, current status buckets, distinct employment counts
and exact half-day quantities. Frozen request days retain policy/calendar history;
released allocations do not erase rejected/cancelled requests. Both reads recheck
report/source grants and MFA, with bounded RLS projections and scoped continuations.
V91 adds the date-envelope index. The backend passed 25 reporting tests, 90 selected
API/PostgreSQL/OpenAPI tests and 81 worker tests. Its dashboard passed 3,793 tests,
19 Chromium scenarios and real API/browser withdrawal/reconciliation, including
preserved report and calendar return links. See
[leave definitions and evidence](../reporting.md#operational-leave).

Operational overtime has an annual bounded API with optional current status,
exact requested/actual/approved minute totals, submitted-actual counts, distinct
employments and complete clipped month buckets. It retains overnight work dates
and immutable requested/submitted facts. Report grants combine with canonical
company-wide overtime access and current MFA. One RLS query reconciles totals and
months; month scopes match the existing request directory. Backend checks passed
31 reporting, 61 workforce, 84 API/PostgreSQL/OpenAPI and 81 worker tests. Its
dashboard and scoped month navigation passed 3,801 tests, 12 report browser scenarios
and real API/PostgreSQL reconciliation through approval, rejection and withdrawal. See [overtime definitions](../reporting.md#operational-overtime).

Operational expense reporting now reads only the latest submitted claim lines,
using their transaction dates and current claim status. Exact decimal IDR amounts
and claim/line/distinct-employment counts reconcile with its scoped claim pages.
Unsubmitted drafts are excluded; later unsent edits retain prior submitted evidence,
and resubmission replaces the selected facts without counting them twice. Payments
retain a separate definition. V92 indexes transaction dates. The backend passed
42 reporting, 20 expense domain, 153 API/PostgreSQL/OpenAPI and 81 worker tests.
Its dashboard and exact-submission navigation passed 3,813 tests, 13 distinct
Chromium scenarios and an isolated API/PostgreSQL correction/resubmission workflow.
Validated report return context retains company, dates, status and page through
review. See [expense definitions](../reporting.md#operational-expenses).

Published payroll costs now aggregate each assessment's latest published immutable
revision across one to twelve earnings months. Exact employer cost, gross,
deductions, signed withholding and take-home reconcile with published payslips;
repeated amendments count once and payment results retain a separate definition.
Current report and company-wide payroll access, credentials and MFA protect the
bounded projection. Backend checks passed 49 reporting, 114 payroll domain,
101 distinct API/PostgreSQL/OpenAPI and 81 worker tests. The dashboard passed
3,823 tests, fourteen distinct Chromium scenarios and a real API/PostgreSQL/worker
workflow through original publication, payment, amendment and returns. Exact month
navigation retains report filters through browser return. Historical selection,
saved filters and exports remain open. See [payroll cost definitions](../reporting.md#published-payroll-costs).

Historical attendance closing aggregates now retain the published original facts
beside the latest applied employee corrections across one to twelve months. Partial
and cancelled attempts are excluded, successive adjustments count once, and empty
published closings keep their identity. Original/current grants and MFA protect the
bounded SQL projection. Validation passed 56 reporting tests and 146 distinct
API/PostgreSQL/OpenAPI cases across focused runs, plus architecture, formatting
and API packaging. Dashboard, revision-aware drill-down and exports remain in
progress. See [closing definitions and evidence](../reporting.md#historical-attendance-closings).

The historical attendance report now has a matching bounded employee page with
exact original closing and selected adjustment references. Ascending employment
UUID continuations retain company/month scope through later corrections and hires.
The immutable initial employment date excludes future hires captured by the worker
before either aggregation or pagination; all twenty-five closing API cases pass.
Original/current counts reconcile with the aggregate while retained revisions stay
readable. Validation passed 61 reporting and 83 API/PostgreSQL/OpenAPI cases, plus
architecture, formatting and API packaging. Dashboard and exports remain open. See
[employee closing drill-down](../reporting.md#employee-closing-drill-down).

Historical attendance closings now have dashboard and exact-revision drill-down
acceptance. Applied month/company filters, complete publication state, original and
latest-corrected comparisons, and bounded employee pages retain their report return
context through the selected and original snapshots. Validation passed 3,835
dashboard tests, twenty-two distinct Chromium cases and a real API/PostgreSQL/worker
workflow with an independent MFA reader, failed/retried closing, future-hire
exclusion and successive corrections. Catalogue/saved filters and exports remain
open. See [closing dashboard evidence](../reporting.md#attendance-closing-dashboard).

The role-aware report catalogue now has backend, dashboard and real API/browser
acceptance. Seven stable definitions reuse their report source policies, intersect
current grants after access guards, and retain current MFA. The bounded response
contains metadata only. Localized discovery clears stale data on refresh, failure
and account/company replacement. Validation passed 63 reporting tests, 70 selected
API/PostgreSQL/OpenAPI cases, 3,841 dashboard tests, fifteen distinct Chromium
scenarios and an isolated independent-reader revocation workflow. Saved filters,
bounded asynchronous exports and export history remain open. See
[the catalogue contract and evidence](../reporting.md#report-catalogue).

Personal saved-filter APIs now retain bounded names and typed main-report selections
per account/company, with owner RLS, observed versions and original-command receipts.
Deleting a filter clears its definition while retaining the version identity; source
revocation still permits owner cleanup and never authorizes a report. V93 introduces
the protected storage. Validation passed 72 reporting tests, 21 database/migration
tests and 87 distinct API/PostgreSQL/OpenAPI cases, plus packaging and architecture/
format checks. Dashboard authoring/recovery, exports and export history remain open.
See [personal saved filters](../reporting.md#personal-saved-filters).

The dashboard now implements personal saved-filter directories, typed authoring for
all seven report kinds, persisted-selection navigation and confirmed deletion.
Original command recovery survives response loss and in-place MFA; unknown group
selections remain intact. All 3,872 dashboard tests, 25 distinct Chromium cases and
an isolated two-account API/PostgreSQL/browser workflow passed. Exports, retention
and export history remain open. See
[saved-filter dashboard behavior and evidence](../reporting.md#saved-filter-dashboard).

Export selection and bounded typed CSV encoding now share the canonical report
parameter policy. The encoder reports the exact complete-row prefix accepted within
one MiB, preserves decimal values and protects spreadsheet text. Eight new domain
cases and saved-filter regressions passed; 80 reporting and 26 API/PostgreSQL cases
were validated across focused runs. Queued execution, snapshots, private artifacts,
retention/download and history still remain open. See
[export contracts and remaining delivery](../report-exports.md).

Export capture now has a separate company-anchored transaction port and V94 SELECT
scope for the existing five report input tables. The requesting company retains its
write scope; selected source companies gain no mutation rights. Validation passed
24 database, 11 headcount API and 82 worker tests with both application contexts,
packaging and architecture/format checks. Durable snapshots, queued execution,
publication, expiry/download and export history remain open. See
[source transaction boundaries](../report-exports.md#source-transaction-boundary).

V95 now retains owned export snapshot headers and immutable numbered rows, rejecting
partial commits and enforcing 100,000-row/100-MiB quotas. Reads consume a prefix of
at most 500 rows and one MiB of cell JSON; expired rows retire in bounded batches.
Across focused runs, 89 reporting, 24 database, 48 server/PostgreSQL and 82 worker
tests passed with packaging and architecture/format checks. Report-specific capture,
queued execution, artifacts, authorized downloads and export history remain open.
See [snapshot storage and validation](../report-exports.md#retained-snapshots).

Known layouts now cover all seven summaries and the three existing detail datasets.
Pure summary conversion retains global distinct persons and exact units; detail
capture shares source SQL with live readers and admits a complete bounded snapshot
in one query. Validation passed 99 reporting and 41 server/PostgreSQL cases; the
six-case worker context and both packages remained up to date. Architecture and
formatting passed. Queued execution, private artifacts, authorized downloads,
scheduled retention and export history remain open. See
[report-specific capture](../report-exports.md#report-layouts-and-source-capture).

V96 now retains owned export execution state and immutable upload/part evidence.
Exact snapshot attachment, version fencing, finite quotas, explicit recovery and
complete publication are enforced in persistence. Retirement retains metadata and
waits for actual expiry plus row/object-cleanup evidence. Across focused runs,
109 reporting, 24 database, 21 server/PostgreSQL and 82 worker tests passed, with
both packages and architecture/format checks. Public admission, worker/provider
orchestration, downloads, scheduled retention and history UI remain open. See
[execution records](../report-exports.md#execution-records-and-private-parts).


Idempotent admission/cancel/resume use cases and the registered `REPORT_EXPORT`
worker now connect those records to all seven summary and three detail captures.
Rendering freezes facts, consumes bounded CSV prefixes, reserves cleanup before
private provider writes, and fences acceptance/publication with current source
access and job ownership. Recovery retains accepted parts and original expiry.
Public adapters and scheduled retirement are recorded below; dashboard delivery
remains open. See [worker execution](../report-exports.md#admission-and-worker-execution).

Execution validation passed 226 distinct tests across focused runs: 111 Reporting,
two core-job, 29 API-context/PostgreSQL and 84 worker cases. Both packages and
architecture/format checks passed. The complete worker graph was exercised after
adding Reporting; storage providers remain controlled fixtures pending real S3 and
browser acceptance. Detailed boundaries and failed-run corrections are recorded in
[export validation](../report-exports.md#validation-and-remaining-delivery).

Public export commands, private history/detail and authorized CSV downloads now
expose the retained lifecycle. Range, ETag and HEAD preserve current authority and
verify each private part before releasing its bytes. Owned status and cancellation
remain available after source/availability loss while retaining owner, credential,
report permission and MFA checks. V97 schedules bounded row retirement, waits for
private-object cleanup and preserves metadata; future formats and inactive owners
do not block cleanup. Validation passed 338 distinct tests across focused runs,
including seven export HTTP and six retention worker cases, the full 90-test worker
graph, both packages and architecture/format checks. The dashboard and real S3/browser
acceptance remain open. See [public contracts](../report-exports.md#owned-history-and-authorized-downloads)
and [retirement](../report-exports.md#scheduled-retirement).

The export dashboard now starts from applied report filters, reviews summary/detail
selection and shows owned paged history/progress. Confirmed cancellation/recovery
retain observed versions; uncertain outcomes preserve their original command through
MFA. Bounded private CSV download, buffer disposal, current scope, hidden-workspace
cancellation and localized narrow views are covered. All 3,910 dashboard tests and
26 distinct Chromium scenarios passed across focused runs, including exact CSV
contents and eight new export scenarios. Architecture, lint, TypeScript and build
passed; see the [dashboard workflow](../report-exports.md#dashboard-workflow).

Real API/PostgreSQL/worker/Garage-S3/Chromium acceptance now covers lost-response
admission recovery, current owner/source access and complete CSV downloads.
Headcount remains unchanged after a new employment; 1,201 leave-detail rows form
three private parts with exact hashes, unique identities and cross-object ranges.
The historical load is fictional database provisioning plus one API-created request;
withdrawing that request leaves the file unchanged. Five storage-fixture cleanup
tests, architecture checks, TypeScript, Biome and production build passed. Local
services and configuration files were disposed. Provider interruption/physical
expiry remain covered by controlled backend tests. See
[real storage acceptance](../report-exports.md#real-private-storage-browser-acceptance).
