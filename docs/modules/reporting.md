# Reporting

Own read models, metric definitions, scoped queries, asynchronous exports, and export retention.

Reports cover headcount/employments, attendance exceptions, leave, overtime, expenses, and payroll costs. Group reports only aggregate authorized companies. Distinguish unique persons from employment count.

Historical reports use corresponding snapshots. Run large exports in bounded batches and recheck access on download. Saved filters never grant access. Reporting cannot mutate operational data.

Screens: role overview, report catalogue, filters, drill-down, export history.

Acceptance: totals reconcile, row/resource isolation, historical definitions, export interruption, bounded memory, and expired export access.

Company headcount is implemented with effective-date status/contract counts, a separate distinct-person total, current source/report permissions, a single bounded aggregate response, and a responsive dashboard report. Group headcount selects up to 32 independently authorized companies and computes the distinct global person count in the same SQL snapshot as company buckets. Its explicit SELECT scope adds no mutation permission; access, MFA, and client policy are rechecked after ordered guards. [Reporting API](../reporting.md) documents the exact definitions and historical limits. Other metrics, exports, and the group browser workflow remain planned.
