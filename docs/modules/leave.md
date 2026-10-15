# Leave

Own leave types, effective policy, accrual, carryover, expiry, requests, cancellations, and balance ledger.

Ledger entries record grants, reservations, consumption, expiry, and reversal. Submit reserves; approval consumes; reject/cancel releases exactly once. Eligibility uses employment, working schedule, partial days, overlap, attachment, and available balance.

Snapshot policy and duration on submission. Approved cancellation follows approval. HR can act on behalf with a reason. Closed-payroll changes become adjustments rather than rewriting snapshots.

Screens: requests, balance ledger, team calendar, policies, balance adjustments, entitlement processing.

Acceptance: simultaneous requests cannot overspend balance; replay and cancellation cannot double-release; holidays/partial days/overnight shifts and period cutoff behave correctly.

## Implementation status

Effective leave types and the immutable balance ledger are implemented. Policies define paid status, partial-day support, minimum service in calendar months, allowed contract kinds, maximum days per request, and whether evidence is required. Type codes are stable; revisions preserve historical policy.

The ledger represents half-day units internally and decimal day strings at the API. HR adjustments require an independent actor, reason, and observed balance version, reject reductions below available balance, and share an employee guard with balance/history reads. Readers use shared guards; commands hold the exclusive guard. Replay never grants twice. Adjustment of an archived type is allowed for accounting corrections; this does not reactivate it for new requests. Ledger reads follow current team/self/company scope and paginate chronologically.

An employee/year balance directory pages existing accounts by stable type code, including zero and closed accounts. It does not create accounts or reveal administrative policy history. Directory and ledger responses identify the employee and current type labels; a name/number may be absent before a current employment revision exists. Each read rechecks current scope and MFA after its guards. Type labels may change with a later policy revision while ledger movements and submitted evidence remain immutable.

Requests, staged decisions, withdrawal, and independently approved cancellation are implemented. Submission freezes policy, eligible employment dates, working intervals, duration, and approval routing. Holidays/off days are excluded; unassigned days fail validation. FULL/FIRST_HALF/SECOND_HALF refer to portions of the scheduled shift, including overnight work. Requests can span two balance years. Reservations and active half-day allocations prevent overspending and overlap across leave types.

Approval consumes a reservation only after its final stage. Rejection or withdrawal releases it once. Requesting cancellation retains consumption and occupancy until its independent approval; rejection or withdrawal of that cancellation keeps the original approved leave. Versions, ledger effects, approval transitions, allocation changes, receipts, audit, and outbox commit together. A later roster/policy edit does not change an existing request. History includes previous cancellation attempts.

Request detail exposes server-calculated actions. Current HR/team/self scope or a permitted snapshotted approver/delegate controls access. A delegated approver loses access when the source membership or approval permission is revoked. Summary and history reads are paginated.

Request lists and details revalidate current access and session assurance after acquiring their resource/access guards. Detail reads hold the shared approval guard, so a reassignment cannot combine an old workflow version with new assignees or actions. Submission, decisions, withdrawal, and cancellation use the same post-wait assurance check before mutation or receipt replay. Cancellation of pending application work releases transaction-owned guards.

`GET /api/v1/companies/{companyId}/leave/me/requests/{id}` provides the employee
detail view. It accepts only the snapshotted owner or current linked employee
account with self-service/read permission. HR and delegated-approver grants do
not expand this route. It uses the same guarded read and immutable submission
snapshot as administrative details. History defaults to 10 rows, is limited to
50, and advances through `historyAfter` revisions while header metadata stays
current. Foreign or inaccessible requests return `leave_request_not_found`.

The [dashboard request directory and detail review](../dashboard.md#leave-request-review) are implemented, including status/employee filters, submitted schedule and policy, approval/cancellation references, evidence metadata, and bounded history navigation. Company readers enter from navigation; scoped readers enter from employee details, and assigned approvers can follow their inbox's source link. [Guarded browser actions](../dashboard.md#leave-request-actions) support approval, rejection, withdrawal, and cancellation with explicit current-version review and original-command receipt recovery. [Evidence downloads](../dashboard.md#leave-evidence-downloads) are available from both details and action reviews with owned cancellation and current server authorization. The [administrative policy catalog and revision review](../dashboard.md#leave-policy-review) are implemented. [Policy creation and editing](../dashboard.md#leave-policy-editing) preserve observed versions, immutable codes, historical terms, and original receipts. The [employee balance directory and ledger review](../dashboard.md#leave-balance-review) expose current balances, immutable movements, and request links under independent read scope. [Independent balance adjustments](../dashboard.md#leave-balance-adjustments) support initial funding and corrections with current-version review and original-command receipt recovery. [Request creation](../dashboard.md#leave-request-creation) supports scoped employee/evidence selection, server calculation and original-command recovery. [Entitlement processing](../dashboard.md#leave-entitlement-processing) adds reviewed accrual/year-closing commands, per-employee outcomes, attempt history and explicit resume. [Company and team calendars](../dashboard.md#leave-calendar) are implemented. [Accrual schedules](../leave-accrual-schedules.md) include reviewed admission, company history and per-period progress in the dashboard. Original-command recovery and due-time-preserving cancellation/resume are verified against an isolated API/PostgreSQL/worker fixture. Carry-bucket expiry remains separate delivery work.

## Administrative policy review

`GET /leave/policies` returns the latest stored revision of each type, including future and inactive definitions. It requires current `leave.manage`, supports an optional `active` filter, and pages by stable type code with a maximum of 200 rows. This is separate from `/leave/types?asOf=...`, which retains effective-date selection for request preparation. A policy's `version` is its latest saved head; `appliedRevision` identifies the selected definition and can differ in an effective-date response.

`GET /leave/policies/{id}` returns the current head and immutable history with actor, reason, recorded time, and complete policy terms. `historyAfter` selects older revision numbers and `historyLimit` bounds the page to 200. Selecting older history never changes the current head. A shared policy guard keeps both queries coherent while a writer waits. Separate directory/history requests are live reads, rather than a durable snapshot of an entire catalog.

Administrative readers and effective type selection acquire shared policy, company, membership, and account guards in that order. Original/live grants and current MFA are revalidated afterward. Saving through the existing `PUT /leave/types/{id}` keeps its exclusive policy guard and checks current assurance before a mutation or original receipt is returned. Self/team access to effective types does not authorize administrative revision reasons.

## Attachments

Submission accepts up to three distinct `attachmentRevisionIds`. Each revision must already be `READY`, belong to a `PERSONAL` document for the same employment, and remain available in the selected company. The selected policy's `attachmentRequired` flag defaults to false. Missing required evidence and unavailable evidence have stable validation codes; a foreign revision is not identified in the response.

The request freezes document/revision IDs, file name, media type, byte count, and SHA-256. It registers immutable business references in the same transaction as reservations, approval, audit, operation receipt, and the sync invalidation. Deferred database constraints reject missing attachment rows or references. Replaying the same request does not resnapshot a replaced document or a changed policy. Withdrawal and cancellation preserve the original evidence, which continues to prevent retirement of the referenced content.

`GET` and `HEAD /api/v1/companies/{companyId}/leave/requests/{requestId}/attachments/{revisionId}/content` support bounded Range downloads and ETag validation. Access follows current request scope or an independently assigned approver/delegate; it does not require a broad personal-document grant. Each storage read is outside the SQL transaction, verifies content integrity, and rechecks current access before returning bytes. Responses are private and must not be cached outside the account/company partition.

Metadata and content revalidate session assurance after shared resource/access guards. Content rechecks it again after storage returns. Expired MFA therefore blocks the current download even if it was valid when the request started; renewed verification permits a new read.

## Balance adjustment review

Ledger details include `availableActions`. `ADJUST` requires current independent `leave.manage`, an open account/year, and employee/policy definitions applicable by December 31 of that year. Missing accounts can be reviewed at version zero; inactive types remain eligible for accounting corrections. The read evaluates these conditions under its existing resource/access guards and intersects original/current grants. Availability is a review hint; the command independently rechecks access, expected version, amount, reason, and balance limits before committing or replaying a receipt.

## Entitlements and year closing

A balance has a stable `accountId`, optimistic `version`, and `closed` flag. The immutable ledger is the accounting source; a database projection updates available, reserved, and consumed half-days atomically. A missing account reads as an open zero balance at version zero. It is created by the first movement or an explicit empty-year closing. Direct balance edits and writes to closed years are rejected. Migration backfills existing ledger totals and versions without rewriting history.

An effective policy may configure `accrual` with `frequency`, decimal-string `daysPerPeriod`, and `carryLimitDays`:

- `MANUAL`: zero recurring entitlement; independent adjustments fund the account.
- `MONTHLY`: 0.5–31 days, posted only after a complete eligible calendar month. Every date must satisfy active employment, permitted contract, and minimum service. Partial months require an explicit correction; no implicit proration occurs.
- `ANNUAL`: 0.5–366 days, once per balance year, using the first eligible date in the selected processing month.

Whole-day policies require whole-day entitlement and carry limits. The carry limit is 0–366 days. An absent accrual policy does not grant anything automatically and carries zero days at year closing. The selected policy is effective on the processing month's first day. Posting requires `leave.accrual.post`, independent current identity, observed employment/policy/balance versions, and an idempotency key. The immutable evidence retains the policy revision, eligibility interval, processing month, period key, employment version, actor, and reason. Monthly/annual frequency is fixed after the first posting in an employee/type/year; changing a policy cannot create a second annual allowance or replace an earlier grant. Corrections remain explicit ledger adjustments.

`leave.year.close` closes an ended calendar year under its December 31 policy. Available balance carries up to the configured limit; the rest expires. Reservations and pending request/cancellation reviews block closing. Nonzero carry requires an open destination year and its observed version. The transaction stores closing evidence, exact source/destination ledger movements, the closed account, audit, receipt, and sync invalidations together. Empty balances can close without fabricated zero-value ledger entries. Consumption remains visible in the closed year.

A closed year cannot receive a new leave reservation, grant, adjustment, or cancellation refund. Request detail hides cancellation when its charged year has closed. A correction to a closed period needs a separate adjustment workflow; there is no endpoint that reopens or rewrites the old account. Current company, membership, credential, and employee binding are checked before a mutation or its original receipt is returned.

Balance/ledger and entitlement views, independent adjustments, per-employee accrual posting, and year closing also validate current MFA after waiting for their resource and access guards. Expired assurance requires renewal before returning data, committing an effect, or recovering an existing receipt. An earlier successful admission check is insufficient for a later transaction result.

`LEAVE_BALANCES` synchronization exposes owned account IDs and versions, with `/leave/balances/{id}` as the canonical read. It never sends ledger reasons in the change feed. Clients must refresh the account and discard older late responses. Registering the collection changes the authorized scope fingerprint, so previously issued employee cursors must re-bootstrap.

## Company entitlement batches

HR can start a durable accrual or year-closing batch for one leave type and period. The command freezes the policy revision, observed policy head, company timezone, original author, reason, and 1–5,000 employee IDs. An omitted employee set selects the company directory at submission; an explicit set must be wholly available in that company. The same type/kind/period admits one queued or running batch. Queuing never implicitly grants permission or bypasses independent authorship.

Each transaction processes one employee under the current lease, the employee balance guard, policy guard, and current identity/company guards. It records the observed employment/balance versions in the grant or closing evidence. Business evidence, ledger changes, audit/outbox, immutable employee outcome, and job checkpoint commit together. Database constraints reject a checkpoint without its result or a result without its checkpoint. There is one final metadata step after all employee outcomes. Source processing and process retries are bounded; no employee loop lives inside a datasource.

Outcomes distinguish `APPLIED`, `UNCHANGED`, `SKIPPED`, and `FAILED`. Existing awards/closings are referenced without replaying their effects. Ineligibility and an operator's own balance produce a skipped result; closed-year/frequency/pending-review conflicts produce an explicit failed result. Safe error codes and parameters let the client translate each outcome. A successfully completed job means processing finished; clients must still display failed/skipped counts. Technical failures stop the current step and use the shared finite worker retry policy.

A policy head change stops unprocessed work; a new batch explicitly selects the new policy. A stopped or exhausted job can be resumed by its original author with current permission, unchanged policy, observed batch version, reason, and idempotency key. Resume creates a new job attempt and preserves all completed results, including skips/failures. It does not rewind employees or silently change the submission policy. At most eight explicit attempts are retained. To reassess a failed/skipped employee, submit a new batch. A worker that loses its lease cannot publish a late grant or complete an obsolete attempt.

Batch details include the current job state, counts, attempts, and paginated employee outcomes. If process retries are exhausted before feature cleanup, the job can be terminal while the batch header still says `RUNNING`; the job state controls recovery. General job cancellation is acknowledged by the feature worker. Reads require current `leave.read`; a self-service employee cannot browse company batch results. A metadata-only final step can complete already-recorded work after a policy edit; it makes no new balance changes.

Payroll cutoffs are integrated into request submission, decisions, withdrawal, and cancellation, including available cancellation actions. Pending decisions on selected actual dates block a payroll start; active/calculated payroll freezes those absence facts until explicit abandonment. See [cutoff and recovery semantics](../payroll-workflows.md#source-cutoffs).

[Finite accrual schedules](../leave-accrual-schedules.md) are implemented and validated across the backend, worker and dashboard. The [carry accounting, request and adjustment integration](../leave-carryover.md) are implemented and tested; closing, worker and client integration remain before new public awards are tracked. Current public expiry occurs at explicit year closing; no expiry date or statutory entitlement is invented by a default policy.

## Administrative request preparation

`GET /leave/preparation/employees` supplies minimal company employee references
for request entry. It requires current `leave.manage`, supports explicit search
of at most 120 characters and pages by employee number with a maximum of fifty
rows. The response includes the company-local date and timezone. It does not
grant access to general People profiles.

`GET /leave/preparation/employees/{employeeId}/evidence` pages the selected
employee's current published `PERSONAL` revisions. It returns selection metadata
without file contents, hashes, storage keys or inspection details. Other document
classifications and employees are excluded. This capability does not authorize
unsubmitted document downloads or personal-document administration.

`POST /leave/requests/preview` evaluates an administrative date selection with
`leave.manage`. It shares the domain preparation use case and pure submission
policies with `/leave/me/requests/preview`. The employee route explicitly selects
SELF and remains account-owned even if the caller also has administrative grants.
Administrative preview can review applicable historical employment; it does not
require the employee to be working today. Submission rechecks eligibility, scope,
evidence, approval routing and the reviewed content revision before recording a
request. Preview creates no reservation or business receipt.

Discovery takes shared people, document (for evidence), company, membership and
account guards. Preview retains its ledger/schedule/cutoff/policy/people/access
ordering. Original and current permissions must both authorize acquisition;
session assurance is checked again before returning acquired results. Cancellation
releases transaction-owned guards. These routes use Leave company admission and
remain available when unrelated People, Workforce or Documents screens are
disabled. [Browser request creation](../dashboard.md#leave-request-creation) uses
these endpoints for scoped discovery, server calculation and reviewed submission.

## Employee application discovery

`GET /api/v1/companies/{companyId}/leave/me/employments` returns the signed-in
account's minimal employment references, with the company-local `asOf` date and
timezone. It requires `leave.self.manage` or `leave.read`; the latter never expands
this self-service lookup to other accounts. Pages contain at most 50 items, with
an employee-number cursor of at most 32 characters. The Leave module admits this
route independently of People and Workforce availability. Individual balances,
requests and commands retain their own resource authorization.

The use case holds shared people, company, membership and account guards, in that
order, and revalidates original/live permissions, credentials and MFA after
waiting. No profile fields are returned, and no business data is duplicated in a
new datasource or repository.

## Employee request preparation

`POST /leave/me/requests/preview` evaluates an account-owned date selection before
submission. Its use case coordinates the existing ledger, schedule, policy,
employment, cutoff and identity repositories. The same pure policies used by
submission validate the date bounds, employment eligibility, portions, occupancy
and available balance. The policy is selected on the first charged working date;
off days and holidays are listed separately. Overnight dates retain their scheduled
work date. Values use exact half-days and separate balance years.

Preparation requires current `leave.self.manage` and active owned employment.
It remains available when People or Workforce is disabled, subject to Leave's own
company admission. Resource guards precede access guards, and current credentials,
permissions and MFA are rechecked after waiting. No approval, request, reservation,
command receipt or audit event is created by preview. Required evidence is reported
through the selected policy; actual evidence and approval routing are validated by
submission. A preview cannot authorize an offline request or promise a future
balance. Submission retains its independent transaction and original-operation
receipt semantics.

The preview also returns an opaque content `revision`. Clients retain it with the
reviewed dates and send it as `previewRevision` on submission. It covers company,
employee, timezone, effective policy revision, selected/excluded dates, resolved
shift facts and annual ledger versions. Reordering the same selected dates does
not change the revision. It is an optimistic precondition, not an access token.

Submission shares the schedule and policy guards while it holds the employee
ledger and access guards. A changed preview returns `leave_preview_changed`
without reserving days or recording a command receipt. Malformed revisions return
`invalid_leave_preview`. An original committed operation replays its receipt
before evaluating today's preview; its payload includes the original revision.
The field is optional for existing direct-submit consumers, which retain the
same server-side eligibility, authorization and accounting checks. New clients
that show a preview should always send its revision.

## Entitlement batch preparation

Batch type discovery requires the selected accrual/closing permission and returns
bounded current type labels, including archived or future definitions. It does
not grant policy administration or expose revision reasons. Preparation resolves
the selected period's effective policy and its independent current head, derives
the company-local date, and returns an immutable selection of up to 5,000 company
employee IDs. Clients submit that reviewed selection explicitly; later hires do
not silently enter an already reviewed command. Preparation creates no work or
reservation, and admission still validates the IDs and policy version.

Batch detail includes names and employee numbers only for its returned outcome
page, without acquiring private profiles or requiring People administration.
Recovery hints require the original author, current operation permission, an
unchanged policy, a stopped job, remaining attempt capacity and no active
replacement. The command rechecks those conditions independently. Results already
recorded remain immutable when work is resumed.

Interactive batch discovery, preparation, history and detail recheck current MFA
after acquisition. Admission and resume recheck it after queue waits and through
journal/receipt completion. A late failure rolls back the job, batch, targets,
attempt, audit and receipt together. Background workers retain their separate
credential, permission and lease checks. See the [API contracts](../api-conventions.md).


## Team and company calendar

`GET /leave/calendar?scope=TEAM|COMPANY&month=YYYY-MM` returns at most one
summary per charged work date. Omitting the month selects the current month in
the company's timezone. `GET /leave/calendar/entries?scope=...&date=YYYY-MM-DD`
returns a selected date's requests, with a stable request-ID cursor and at most
200 entries (20 by default). Calendar dates are limited to 1900–2200.

Company scope requires `leave.read`. Team scope restricts that permission or
`leave.team.read` to current direct reports whose employment has not ended.
Current reporting lines determine access even when viewing an earlier month.
Self-service and approval permissions alone do not grant a team calendar. Leave
admission is independent of People and Workforce screen availability.

Counts come from occupied dates, not the interval between a request's first and
last dates. Pending and approved requests remain visible, including pending
cancellation until its approval releases occupancy. Rejected and withdrawn
requests do not occupy the calendar. Two half-day requests for one employee count
as two requests and one employee. Portions refer to the submitted shift, including
its overnight work date. Entries retain submitted employee/type labels and omit
reasons, account IDs, attachment metadata and profile fields.

Each aggregate uses one bounded SQL snapshot, with a date index on allocations.
Month summaries and paged entries are independent current reads; they are not a
frozen snapshot across requests. A withdrawn cursor anchor does not prevent
forward pagination. Readers hold shared People and access guards, then recheck
original/current permissions, credentials and MFA. Assurance is checked again
after acquisition. Cancellation releases the transaction and all guards.

The API and dashboard are implemented. The month grid and selected-date pages
retain scope/date/cursor through request history and action review. The controller
cancels superseded or hidden reads and reacquires after workspace verification.
Browser tests cover company replacement, read failures, keyboard navigation,
company-local Today, dark/Indonesian layouts and bounded pages. An isolated real
API/PostgreSQL scenario verified two half-day requests, withdrawal, updated
occupancy and company replacement.
