# Leave

Own leave types, effective policy, accrual, carryover, expiry, requests, cancellations, and balance ledger.

Ledger entries record grants, reservations, consumption, expiry, and reversal. Submit reserves; approval consumes; reject/cancel releases exactly once. Eligibility uses employment, working schedule, partial days, overlap, attachment, and available balance.

Snapshot policy and duration on submission. Approved cancellation follows approval. HR can act on behalf with a reason. Closed-payroll changes become adjustments rather than rewriting snapshots.

Screens: requests, balance ledger, team calendar, policies, balance adjustments.

Acceptance: simultaneous requests cannot overspend balance; replay and cancellation cannot double-release; holidays/partial days/overnight shifts and period cutoff behave correctly.

## Implementation status

Effective leave types and the immutable balance ledger are implemented. Policies define paid status, partial-day support, minimum service in calendar months, allowed contract kinds, and maximum days per request. Type codes are stable; revisions preserve historical policy.

The ledger represents half-day units internally and decimal day strings at the API. HR adjustments require an independent actor and reason, reject reductions below available balance, and share an employee lock with balance/history reads. Replay never grants twice. Adjustment of an archived type is allowed for accounting corrections; this does not reactivate it for new requests. Ledger reads follow current team/self/company scope and paginate chronologically.

Requests, staged decisions, withdrawal, and independently approved cancellation are implemented. Submission freezes policy, eligible employment dates, working intervals, duration, and approval routing. Holidays/off days are excluded; unassigned days fail validation. FULL/FIRST_HALF/SECOND_HALF refer to portions of the scheduled shift, including overnight work. Requests can span two balance years. Reservations and active half-day allocations prevent overspending and overlap across leave types.

Approval consumes a reservation only after its final stage. Rejection or withdrawal releases it once. Requesting cancellation retains consumption and occupancy until its independent approval; rejection or withdrawal of that cancellation keeps the original approved leave. Versions, ledger effects, approval transitions, allocation changes, receipts, audit, and outbox commit together. A later roster/policy edit does not change an existing request. History includes previous cancellation attempts.

Request detail exposes server-calculated actions. Current HR/team/self scope or a permitted snapshotted approver/delegate controls access. A delegated approver loses access when the source membership or approval permission is revoked. Summary and history reads are paginated.

Scheduled accrual, carryover/expiry, attachments, and payroll cutoff integration remain planned.
