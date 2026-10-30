# Reporting requirement review

The five initial Reporting requirements and both original detailed sections have
implementation and behavior mappings below. Review found a seven-case export
policy-completion defect, now corrected with [focused evidence](reporting-assurance-2026-10-30.md).
Broader acceptance now passes 186 API, eight active worker, 52 domain and 61
repository cases plus actual private-storage browser recovery. All five inventory rows are accepted after complete source CI `41087903927`
passed its ten jobs. Workforce remains separately gated.

| Initial requirement | Implementation and verification |
| --- | --- |
| `reporting.01` | Reporting owns typed aggregates, versioned definitions, catalogue, private saved filters, immutable export snapshots, fenced execution and scheduled retention. Summary/detail repository and source-capture tests verify their separate contracts. Export HTTP/execution/storage and worker suites cover admission, capture, parts, recovery and retirement. |
| `reporting.02` | Seven summary definitions cover company/group headcount, operational attendance exceptions, leave, overtime, expenses, published payroll costs and historical attendance closings. Their API suites reconcile actual source transitions and totals. Group headcount counts distinct persons across selected companies rather than summing company persons or employments; ordered independent source authority and the bounded SELECT scope prevent cross-company disclosure. |
| `reporting.03` | Payroll uses published immutable revisions; closing reports retain original published facts and latest applied adjustments. Operational reports explicitly retain their current-state definitions. Export capture seals one coherent source acquisition into immutable owner-scoped rows, then renders bounded pages/parts. Current source authority and expiry protect preparation and every content range. Saved selections are parameters, never grants. Reporting writes only its own filter/export/snapshot/queue/audit state; selected source scope adds no operational mutation. |
| `reporting.04` | Role-aware catalogue, applied report filters, company/group summaries, exact-revision drill-down, saved-filter authoring and export history/detail/commands are routed dashboard features. Controlled-response tests cover hidden/disposed ownership, account/company replacement, revocation, original command recovery and preserved return filters. Actual browser scenarios cover catalogue revocation, saved filters and private CSV execution through real API/worker/Garage. |
| `reporting.05` | Metric-specific API tests reconcile exact counts, minutes, half-day units and decimal IDR against canonical sources. RLS and source-capture tests reject foreign owners/companies, unsealed/gapped capture and oversize rows/bytes. Export tests cover interruption, lease loss, response loss, quota, original recovery and expired access. The separately executed one-hour retention acceptance preserves immutable deadlines, object grace, one retirement audit and exact live control content. |

## Metric and lifecycle coverage

| Surface | Definition and evidence |
| --- | --- |
| Headcount | Effective-date status/contract buckets distinguish employment and unique-person totals. `HeadcountReportHttpTest`, access tests and `GroupHeadcountHttpTest` cover company-date history, shared persons, exactly selected company buckets, original/current grants, client gates, one SQL snapshot and interruption. |
| Attendance exceptions | A 31-day live exception aggregate reconciles total/day buckets with the Workforce exception directory. Missing evidence is not recorded absence. Report access and browser correction scenarios preserve this distinction. |
| Leave | Submitted frozen work dates, current status and exact half-day units remain distinct from released allocations. Aggregate/detail/source-capture tests reconcile rejected/cancelled requests and scope-bound pagination without rewriting the submitted calendar. |
| Overtime | Bounded annual date/status selection retains requested, submitted actual and approved minute totals, including overnight work dates. API/access tests and canonical request navigation reconcile changes without counting missing actuals as submitted. |
| Expenses | Latest submitted lines retain original transaction dates, labels and submission identity. Unsent edits do not replace submitted facts; resubmission counts once. Exact IDR totals remain separate from payment facts. Expense report/payment/source-capture tests exercise these definitions. |
| Payroll costs | Latest published assessment revisions count once across selected earnings months; signed withholding and employer/gross/deduction/take-home totals reconcile with immutable payslips. Payment status is separate. API/access and actual worker publication/amendment evidence cover the definition. |
| Attendance closings | Only published attempts count; empty published closings retain identity. Original facts and latest applied employee corrections remain separate. Detail rows retain exact closing/adjustment references and exclude future hires. Aggregate/detail/source-capture tests reconcile those selections. |
| Catalogue and filters | Discovery intersects current report/source permissions. Filters are owner/company-scoped, canonical and versioned, with original receipts and deletion tombstones. Filter deletion remains possible after source permission loss and never authorizes a report. Input selections are copied before waits. |
| Capture and rendering | Seven summaries and three supported detail layouts retain definition, evaluation time and original expiry. Coherent SQL capture and deferred sealing reject incomplete rows. Rendering advances only by the accepted complete-row prefix, bounded by 500 rows/one MiB per part and 100 MiB overall. CSV preserves exact decimals and escapes spreadsheet text. These limits are not a new process-memory benchmark. |
| Commands and downloads | Server-generated resource IDs, original operations, observed versions, quotas and fenced leases prevent duplicate work and replacement of accepted parts. Current enrollment/proof is checked after source-policy acquisition as well as before it. Owner history/cancel stays distinct from authorized content; a rejected preparation reads no object. |
| Retention | Database-clock expiry schedules bounded row removal and later physical object cleanup. Original metadata and quota remain until all reserved objects/receipts retire. The [elapsed acceptance](export-retention-acceptance.md) passed an unchanged real hour plus lost-delete recovery; ordinary tests explicitly skip that opt-in case. |

The original `Current delivery` section summarizes the completed surfaces; the
`Implementation and acceptance record` section preserves their history. Earlier
entries saying a later-delivered surface was open are historical, not fresh gaps.
This review retains those boundaries and checks the newly changed export paths.

[Final local measurements](measurements/reporting-assurance-2026-10-30.json)
provide the exact case selection and actual browser results. Matching full source
CI now accepts all five Reporting rows. Final Workforce CI and the combined private
runtime update remain the active workspace gates.

Final [CI evidence](measurements/reporting-payroll-full-ci-2026-10-30.json) confirms
3,063 disjoint API cases, 104 active worker cases, 1,111 distinct mocked browser
scenarios, 56 actual browser workflows and successful schema-103 restoration.
Repeated worker/module suites across shards are counted once. No deployment is
implied by this CI acceptance.
