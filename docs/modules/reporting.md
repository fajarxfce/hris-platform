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
