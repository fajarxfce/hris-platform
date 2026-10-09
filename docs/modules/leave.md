# Leave

Own leave types, effective policy, accrual, carryover, expiry, requests, cancellations, and balance ledger.

Ledger entries record grants, reservations, consumption, expiry, and reversal. Submit reserves; approval consumes; reject/cancel releases exactly once. Eligibility uses employment, working schedule, partial days, overlap, attachment, and available balance.

Snapshot policy and duration on submission. Approved cancellation follows approval. HR can act on behalf with a reason. Closed-payroll changes become adjustments rather than rewriting snapshots.

Screens: requests, balance ledger, team calendar, policies, balance adjustments.

Acceptance: simultaneous requests cannot overspend balance; replay and cancellation cannot double-release; holidays/partial days/overnight shifts and period cutoff behave correctly.

## Implementation status

Effective leave types and the immutable balance ledger are implemented. Policies define paid status, partial-day support, minimum service in calendar months, allowed contract kinds, maximum days per request, and whether evidence is required. Type codes are stable; revisions preserve historical policy.

The ledger represents half-day units internally and decimal day strings at the API. HR adjustments require an independent actor and reason, reject reductions below available balance, and share an employee lock with balance/history reads. Replay never grants twice. Adjustment of an archived type is allowed for accounting corrections; this does not reactivate it for new requests. Ledger reads follow current team/self/company scope and paginate chronologically.

Requests, staged decisions, withdrawal, and independently approved cancellation are implemented. Submission freezes policy, eligible employment dates, working intervals, duration, and approval routing. Holidays/off days are excluded; unassigned days fail validation. FULL/FIRST_HALF/SECOND_HALF refer to portions of the scheduled shift, including overnight work. Requests can span two balance years. Reservations and active half-day allocations prevent overspending and overlap across leave types.

Approval consumes a reservation only after its final stage. Rejection or withdrawal releases it once. Requesting cancellation retains consumption and occupancy until its independent approval; rejection or withdrawal of that cancellation keeps the original approved leave. Versions, ledger effects, approval transitions, allocation changes, receipts, audit, and outbox commit together. A later roster/policy edit does not change an existing request. History includes previous cancellation attempts.

Request detail exposes server-calculated actions. Current HR/team/self scope or a permitted snapshotted approver/delegate controls access. A delegated approver loses access when the source membership or approval permission is revoked. Summary and history reads are paginated.

## Attachments

Submission accepts up to three distinct `attachmentRevisionIds`. Each revision must already be `READY`, belong to a `PERSONAL` document for the same employment, and remain available in the selected company. The selected policy's `attachmentRequired` flag defaults to false. Missing required evidence and unavailable evidence have stable validation codes; a foreign revision is not identified in the response.

The request freezes document/revision IDs, file name, media type, byte count, and SHA-256. It registers immutable business references in the same transaction as reservations, approval, audit, operation receipt, and the sync invalidation. Deferred database constraints reject missing attachment rows or references. Replaying the same request does not resnapshot a replaced document or a changed policy. Withdrawal and cancellation preserve the original evidence, which continues to prevent retirement of the referenced content.

`GET` and `HEAD /api/v1/companies/{companyId}/leave/requests/{requestId}/attachments/{revisionId}/content` support bounded Range downloads and ETag validation. Access follows current request scope or an independently assigned approver/delegate; it does not require a broad personal-document grant. Each storage read is outside the SQL transaction, verifies content integrity, and rechecks current access before returning bytes. Responses are private and must not be cached outside the account/company partition.

Scheduled accrual, carryover/expiry, and payroll cutoff integration remain planned.
