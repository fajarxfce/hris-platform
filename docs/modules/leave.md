# Leave

Own leave types, effective policy, accrual, carryover, expiry, requests, cancellations, and balance ledger.

Ledger entries record grants, reservations, consumption, expiry, and reversal. Submit reserves; approval consumes; reject/cancel releases exactly once. Eligibility uses employment, working schedule, partial days, overlap, attachment, and available balance.

Snapshot policy and duration on submission. Approved cancellation follows approval. HR can act on behalf with a reason. Closed-payroll changes become adjustments rather than rewriting snapshots.

Screens: requests, balance ledger, team calendar, policies, balance adjustments.

Acceptance: simultaneous requests cannot overspend balance; replay and cancellation cannot double-release; holidays/partial days/overnight shifts and period cutoff behave correctly.

## Implementation status

Effective leave types and the immutable balance ledger are implemented. Policies define paid status, partial-day support, minimum service in calendar months, allowed contract kinds, maximum days per request, and whether evidence is required. Type codes are stable; revisions preserve historical policy.

The ledger represents half-day units internally and decimal day strings at the API. HR adjustments require an independent actor, reason, and observed balance version, reject reductions below available balance, and share an employee guard with balance/history reads. Readers use shared guards; commands hold the exclusive guard. Replay never grants twice. Adjustment of an archived type is allowed for accounting corrections; this does not reactivate it for new requests. Ledger reads follow current team/self/company scope and paginate chronologically.

Requests, staged decisions, withdrawal, and independently approved cancellation are implemented. Submission freezes policy, eligible employment dates, working intervals, duration, and approval routing. Holidays/off days are excluded; unassigned days fail validation. FULL/FIRST_HALF/SECOND_HALF refer to portions of the scheduled shift, including overnight work. Requests can span two balance years. Reservations and active half-day allocations prevent overspending and overlap across leave types.

Approval consumes a reservation only after its final stage. Rejection or withdrawal releases it once. Requesting cancellation retains consumption and occupancy until its independent approval; rejection or withdrawal of that cancellation keeps the original approved leave. Versions, ledger effects, approval transitions, allocation changes, receipts, audit, and outbox commit together. A later roster/policy edit does not change an existing request. History includes previous cancellation attempts.

Request detail exposes server-calculated actions. Current HR/team/self scope or a permitted snapshotted approver/delegate controls access. A delegated approver loses access when the source membership or approval permission is revoked. Summary and history reads are paginated.

## Attachments

Submission accepts up to three distinct `attachmentRevisionIds`. Each revision must already be `READY`, belong to a `PERSONAL` document for the same employment, and remain available in the selected company. The selected policy's `attachmentRequired` flag defaults to false. Missing required evidence and unavailable evidence have stable validation codes; a foreign revision is not identified in the response.

The request freezes document/revision IDs, file name, media type, byte count, and SHA-256. It registers immutable business references in the same transaction as reservations, approval, audit, operation receipt, and the sync invalidation. Deferred database constraints reject missing attachment rows or references. Replaying the same request does not resnapshot a replaced document or a changed policy. Withdrawal and cancellation preserve the original evidence, which continues to prevent retirement of the referenced content.

`GET` and `HEAD /api/v1/companies/{companyId}/leave/requests/{requestId}/attachments/{revisionId}/content` support bounded Range downloads and ETag validation. Access follows current request scope or an independently assigned approver/delegate; it does not require a broad personal-document grant. Each storage read is outside the SQL transaction, verifies content integrity, and rechecks current access before returning bytes. Responses are private and must not be cached outside the account/company partition.

## Entitlements and year closing

A balance has a stable `accountId`, optimistic `version`, and `closed` flag. The immutable ledger is the accounting source; a database projection updates available, reserved, and consumed half-days atomically. A missing account reads as an open zero balance at version zero. It is created by the first movement or an explicit empty-year closing. Direct balance edits and writes to closed years are rejected. Migration backfills existing ledger totals and versions without rewriting history.

An effective policy may configure `accrual` with `frequency`, decimal-string `daysPerPeriod`, and `carryLimitDays`:

- `MANUAL`: zero recurring entitlement; independent adjustments fund the account.
- `MONTHLY`: 0.5–31 days, posted only after a complete eligible calendar month. Every date must satisfy active employment, permitted contract, and minimum service. Partial months require an explicit correction; no implicit proration occurs.
- `ANNUAL`: 0.5–366 days, once per balance year, using the first eligible date in the selected processing month.

Whole-day policies require whole-day entitlement and carry limits. The carry limit is 0–366 days. An absent accrual policy does not grant anything automatically and carries zero days at year closing. The selected policy is effective on the processing month's first day. Posting requires `leave.accrual.post`, independent current identity, observed employment/policy/balance versions, and an idempotency key. The immutable evidence retains the policy revision, eligibility interval, processing month, period key, employment version, actor, and reason. Monthly/annual frequency is fixed after the first posting in an employee/type/year; changing a policy cannot create a second annual allowance or replace an earlier grant. Corrections remain explicit ledger adjustments.

`leave.year.close` closes an ended calendar year under its December 31 policy. Available balance carries up to the configured limit; the rest expires. Reservations and pending request/cancellation reviews block closing. Nonzero carry requires an open destination year and its observed version. The transaction stores closing evidence, exact source/destination ledger movements, the closed account, audit, receipt, and sync invalidations together. Empty balances can close without fabricated zero-value ledger entries. Consumption remains visible in the closed year.

A closed year cannot receive a new leave reservation, grant, adjustment, or cancellation refund. Request detail hides cancellation when its charged year has closed. A correction to a closed period needs a separate adjustment workflow; there is no endpoint that reopens or rewrites the old account. Current company, membership, credential, and employee binding are checked before a mutation or its original receipt is returned.

`LEAVE_BALANCES` synchronization exposes owned account IDs and versions, with `/leave/balances/{id}` as the canonical read. It never sends ledger reasons in the change feed. Clients must refresh the account and discard older late responses. Registering the collection changes the authorized scope fingerprint, so previously issued employee cursors must re-bootstrap.

Company-wide accrual jobs, automatic scheduling, carry-bucket expiry dates, and payroll cutoff integration remain subsequent work. Current expiry occurs at explicit year closing; no expiry date or statutory entitlement is invented by a default policy.
