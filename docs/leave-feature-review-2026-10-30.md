# Leave requirement review — 30 October 2026

All five initial requirements and eleven original detailed sections have source
and matching behavior review. The earlier [read correction](leave-read-assurance-2026-10-30.md)
closed ten failures; [command/delegation completion](leave-command-assurance-2026-10-30.md)
closes twenty-five more, with eight additional compatibility regressions passing.
Local acceptance covers 485 distinct API/OpenAPI cases across a 471-case broad
run and a 58-case final targeted run, 65 domain cases, quality/packages and nine actual browser workflows, including
three with the durable worker. Full-source CI remains separately tracked.

| Requirement | Implementation and matching behavior |
| --- | --- |
| `leave.01`: types, effective policy, accrual, carry, expiry, requests, cancellations and ledger | Leave policy/request/accounting use cases, forced company scope, immutable history and durable batch/expiry workers. Policy, accrual, closing, carry, request, expiry and schedule suites exercise these paths. |
| `leave.02`: exactly-once movements and eligibility | Half-day ledger and carry subledger constraints pair reservations, consumption, release, refund and expiry with workflow transitions. Concurrent requests, policy/schedule/contract eligibility, evidence, holidays, overnight dates, partial days, carry funding and storage suites verify bounded independent effects. |
| `leave.03`: immutable snapshots, independent cancellation, HR authorship and payroll cutoffs | Submitted policy/schedule/duration and evidence remain immutable. Independent original/current approvers, newly bound beneficiary exclusion, guarded request-management receipts, payroll cutoff and closed-year tests protect retained facts. Corrections use the documented accounting/payroll workflows; original snapshots are never rewritten. |
| `leave.04`: request, ledger, calendar, policy and processing screens | Dashboard controllers/pages cover request creation/actions/evidence, policy history/editor, balances/adjustment, accrual/closing/schedules/expiry history and calendar. 141 matching-source controlled-response Chromium cases and nine fresh actual API/worker/browser workflows pass. Native submission recovery and carried-balance upgrade retain their earlier separate acceptance. |
| `leave.05`: competing funding, replay/cancellation and calendar/cutoff boundaries | Actual PostgreSQL request/ledger/carry/cutoff suites, immutable original receipt tests, delegated completion/account-wait regressions, and actual staged decision/cancellation/withdrawal workflows verify these boundaries. |

The detailed review preserves these limits and ownership rules:

- Policy review retains stable codes, effective revisions and bounded history.
  Admitted contract selections are detached before operation hashing or waits.
- At most three distinct accepted PERSONAL revisions can be attached to the
  same employment/company. Immutable business references retain evidence after
  withdrawal or cancellation. Range/ETag content remains scoped and bounded.
- Independent balance corrections require observed versions; closed years remain
  closed. Reserved/consumed funding is never silently converted to ordinary credit.
- Monthly/annual accrual uses explicit eligibility/frequency and immutable evidence.
  Carry limits and optional validity are configured; no deadline or statutory grant
  is invented. Closing and late refunds settle the appropriate original bucket.
- Company batches freeze at most 5,000 selected employees and original policy/timezone.
  Each step owns one employee transaction and a fenced checkpoint. Attempts and
  recovery are finite; completed outcomes cannot be processed twice.
- Administrative request preparation exposes minimal eligible employment/evidence
  references. Preview revisions bind policy, calendar, balances and carry funding.
  An original receipt precedes recalculation of a later preview.
- Employee discovery/preparation stays under SELF scope independently of broader
  People/Workforce access. Later employment changes do not widen request ownership.
- Entitlement batch preparation uses reviewed versions, explicit cohorts and
  bounded queue admission. A finite schedule has at most twelve reviewed periods;
  recovery preserves future due times and original employee selection.
- Calendar totals/details use charged occupied dates, half-days and current team or
  company scope. Month/date pages are bounded and omit private reasons/evidence.
- [Carry accounting/expiry](leave-carryover.md) retains original funding and immutable
  UTC deadlines, including a derived worker independent of the creator's later
  revocation. [Accrual schedules](leave-accrual-schedules.md) retain fixed period
  authorization, safe worker execution and terminal recovery.

[Measurements](measurements/leave-feature-review-2026-10-30.json) retain matching
Git trees and downloaded full-CI evidence from `8e5dd3db`: 141 controlled-response
Chromium cases, six worker cases, two migration cases and ten Reporting cases.
The existing domain evidence is recorded separately from the fresh 65-case run.
All seven compared trees and 26 existing worker/policy files match; the new read
policy is tested by current API cases and is not called by the worker.

The private schema-101 runtime, accepted Google binding and installed phone are
unchanged. The physical iPhone test remains waived. Thirty-one initial rows are
now reviewed; thirty initial rows, remaining detailed contracts and full Mobile
review remain open. This module acceptance does not close the full goal.
