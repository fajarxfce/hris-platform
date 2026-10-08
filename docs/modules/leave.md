# Leave

Own leave types, effective policy, accrual, carryover, expiry, requests, cancellations, and balance ledger.

Ledger entries record grants, reservations, consumption, expiry, and reversal. Submit reserves; approval consumes; reject/cancel releases exactly once. Eligibility uses employment, working schedule, partial days, overlap, attachment, and available balance.

Snapshot policy and duration on submission. Approved cancellation follows approval. HR can act on behalf with a reason. Closed-payroll changes become adjustments rather than rewriting snapshots.

Screens: requests, balance ledger, team calendar, policies, balance adjustments.

Acceptance: simultaneous requests cannot overspend balance; replay and cancellation cannot double-release; holidays/partial days/overnight shifts and period cutoff behave correctly.

## Implementation status

Effective leave types and the immutable balance ledger are implemented. Policies define paid status, partial-day support, minimum service in calendar months, allowed contract kinds, and maximum days per request. Type codes are stable; revisions preserve historical policy.

The ledger represents half-day units internally and decimal day strings at the API. HR adjustments require an independent actor and reason, reject reductions below available balance, and share an employee lock with balance/history reads. Replay never grants twice. Adjustment of an archived type is allowed for accounting corrections; this does not reactivate it for new requests. Ledger reads follow current team/self/company scope and paginate chronologically.

Request reservations, approval/cancellation effects, scheduled accrual, carryover/expiry, attachments, and payroll cutoff integration remain planned.
