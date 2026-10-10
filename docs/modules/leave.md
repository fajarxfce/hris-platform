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

Request lists and details revalidate current access and session assurance after acquiring their resource/access guards. Detail reads hold the shared approval guard, so a reassignment cannot combine an old workflow version with new assignees or actions. Submission, decisions, withdrawal, and cancellation use the same post-wait assurance check before mutation or receipt replay. Cancellation of pending application work releases transaction-owned guards.

The [dashboard request directory and detail review](../dashboard.md#leave-request-review) are implemented, including status/employee filters, submitted schedule and policy, approval/cancellation references, evidence metadata, and bounded history navigation. Company readers enter from navigation; scoped readers enter from employee details, and assigned approvers can follow their inbox's source link. [Guarded browser actions](../dashboard.md#leave-request-actions) support approval, rejection, withdrawal, and cancellation with explicit current-version review and original-command receipt recovery. [Evidence downloads](../dashboard.md#leave-evidence-downloads) are available from both details and action reviews with owned cancellation and current server authorization. Submission, policies, and balance administration remain subsequent dashboard capabilities.

## Attachments

Submission accepts up to three distinct `attachmentRevisionIds`. Each revision must already be `READY`, belong to a `PERSONAL` document for the same employment, and remain available in the selected company. The selected policy's `attachmentRequired` flag defaults to false. Missing required evidence and unavailable evidence have stable validation codes; a foreign revision is not identified in the response.

The request freezes document/revision IDs, file name, media type, byte count, and SHA-256. It registers immutable business references in the same transaction as reservations, approval, audit, operation receipt, and the sync invalidation. Deferred database constraints reject missing attachment rows or references. Replaying the same request does not resnapshot a replaced document or a changed policy. Withdrawal and cancellation preserve the original evidence, which continues to prevent retirement of the referenced content.

`GET` and `HEAD /api/v1/companies/{companyId}/leave/requests/{requestId}/attachments/{revisionId}/content` support bounded Range downloads and ETag validation. Access follows current request scope or an independently assigned approver/delegate; it does not require a broad personal-document grant. Each storage read is outside the SQL transaction, verifies content integrity, and rechecks current access before returning bytes. Responses are private and must not be cached outside the account/company partition.

Metadata and content revalidate session assurance after shared resource/access guards. Content rechecks it again after storage returns. Expired MFA therefore blocks the current download even if it was valid when the request started; renewed verification permits a new read.

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

## Company entitlement batches

HR can start a durable accrual or year-closing batch for one leave type and period. The command freezes the policy revision, observed policy head, company timezone, original author, reason, and 1–5,000 employee IDs. An omitted employee set selects the company directory at submission; an explicit set must be wholly available in that company. The same type/kind/period admits one queued or running batch. Queuing never implicitly grants permission or bypasses independent authorship.

Each transaction processes one employee under the current lease, the employee balance guard, policy guard, and current identity/company guards. It records the observed employment/balance versions in the grant or closing evidence. Business evidence, ledger changes, audit/outbox, immutable employee outcome, and job checkpoint commit together. Database constraints reject a checkpoint without its result or a result without its checkpoint. There is one final metadata step after all employee outcomes. Source processing and process retries are bounded; no employee loop lives inside a datasource.

Outcomes distinguish `APPLIED`, `UNCHANGED`, `SKIPPED`, and `FAILED`. Existing awards/closings are referenced without replaying their effects. Ineligibility and an operator's own balance produce a skipped result; closed-year/frequency/pending-review conflicts produce an explicit failed result. Safe error codes and parameters let the client translate each outcome. A successfully completed job means processing finished; clients must still display failed/skipped counts. Technical failures stop the current step and use the shared finite worker retry policy.

A policy head change stops unprocessed work; a new batch explicitly selects the new policy. A stopped or exhausted job can be resumed by its original author with current permission, unchanged policy, observed batch version, reason, and idempotency key. Resume creates a new job attempt and preserves all completed results, including skips/failures. It does not rewind employees or silently change the submission policy. At most eight explicit attempts are retained. To reassess a failed/skipped employee, submit a new batch. A worker that loses its lease cannot publish a late grant or complete an obsolete attempt.

Batch details include the current job state, counts, attempts, and paginated employee outcomes. If process retries are exhausted before feature cleanup, the job can be terminal while the batch header still says `RUNNING`; the job state controls recovery. General job cancellation is acknowledged by the feature worker. Reads require current `leave.read`; a self-service employee cannot browse company batch results. A metadata-only final step can complete already-recorded work after a policy edit; it makes no new balance changes.

Payroll cutoffs are integrated into request submission, decisions, withdrawal, and cancellation, including available cancellation actions. Pending decisions on selected actual dates block a payroll start; active/calculated payroll freezes those absence facts until explicit abandonment. See [cutoff and recovery semantics](../payroll-workflows.md#source-cutoffs).

Automatic scheduling and carry-bucket expiry dates remain subsequent work. Current expiry occurs at explicit year closing; no expiry date or statutory entitlement is invented by a default policy.
