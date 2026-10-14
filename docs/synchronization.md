# Mobile synchronization

Synchronization provides a bounded, authorized change feed for local-first clients. It is an invalidation protocol: references identify which canonical resources to fetch or remove. It does not expose audit payloads or replace feature commands, idempotency receipts, and optimistic versions.

The supported collections are:

| Collection | Required permission | Audience | Canonical resource |
| --- | --- | --- | --- |
| `EXPENSE_CLAIMS` | `expenses.self.manage`, or explicit selection with `expenses.read` / `expenses.manage` | Current account's employments in the company | `/api/v1/companies/{companyId}/expenses/claims/{id}/record?scope=SELF` |
| `EXPENSE_PAYMENTS` (explicit selection) | `expenses.self.manage`, `expenses.read` or `expenses.manage` | Current account's employments in the company | `/api/v1/companies/{companyId}/expenses/claims/{id}/payment-progress?scope=SELF` |
| `DIRECTORY_EMPLOYEES` (explicit selection) | `people.directory.read` or `people.read` | Public coworkers in the selected company; no personal employment linkage required | `/api/v1/companies/{companyId}/employees/directory/{id}` |
| `DOCUMENT_EVIDENCE` (explicit selection) | `documents.self.read` | Current account's employments; published personal documents and receipts only | `/api/v1/companies/{companyId}/documents/me/evidence/{id}` |
| `LEAVE_REQUESTS` | `leave.self.manage`, or explicit selection with `leave.read` | Current account's employments or immutable request owner | `/api/v1/companies/{companyId}/leave/requests/{id}` |
| `LEAVE_BALANCES` | `leave.self.manage`, or explicit selection with `leave.read` | Current account's employments in the company | `/api/v1/companies/{companyId}/leave/balances/{id}` |
| `OVERTIME_REQUESTS` | `overtime.self.manage`, or explicit selection with `overtime.read`, `overtime.manage` or `workforce.read` | Current account's employments in the company | `/api/v1/companies/{companyId}/workforce/overtime/{id}` |
| `PAYSLIPS` | `payroll.self.read`, or explicit selection with `payroll.read` | Current account's employments in the company | `/api/v1/companies/{companyId}/payroll/payslips/{id}` |
| `PAYROLL_PAYMENTS` | `payroll.self.read`, or explicit selection with `payroll.read` | Current account's employments in the company | `/api/v1/companies/{companyId}/payroll/payslips/{id}/payments` |
| `INBOX` (explicit selection) | `announcements.read` | Original recipient account only | `/api/v1/companies/{companyId}/inbox/{id}` |
| `ATTENDANCE_DAYS` (explicit selection) | `attendance.self.record` or `workforce.read` | Current account's employments in the company | `/api/v1/companies/{companyId}/workforce/attendance/days/{id}` |
| `WORK_SCHEDULES` (explicit selection) | `attendance.self.record` or `workforce.read` | Current account's employments in the company | `/api/v1/companies/{companyId}/workforce/me/schedules/{id}` |
| `ROSTER_DAYS` (explicit selection) | `attendance.self.record` or `workforce.read` | Current account's employments in the company | `/api/v1/companies/{companyId}/workforce/me/roster/{id}` |
| `WORK_HOLIDAYS` (explicit selection) | `attendance.self.record` or `workforce.read` | Company calendar, only while the account owns an employment | `/api/v1/companies/{companyId}/workforce/me/holidays/{id}` |

Balance projections have stable IDs and versions. A year closing can publish several versions of its source balance and a destination update in one transaction; fetching a reference returns the latest committed account. Empty accounts are exposed only after a movement or explicit closing. Adding a collection to the selected projection changes its scope fingerprint and requires bootstrap.

The canonical balance response includes `typeCode` and `typeName`, matching the
balance directory, so clients can hydrate a balance independently of a previously
opened directory page. These descriptive fields come from the current leave type;
the balance version still identifies financial movements and closing state.

All business-request statuses are included. Request cancellation or withdrawal is an `UPSERT` with a new version. Archiving an announcement withdraws its inbox items and produces `DELETE` references. Team/company administration rights do not grant an employee-wide export of private business records. The separately selected coworker directory has its own minimal public fields and directory authority. Document processing/history, approval inboxes, unfinalized payroll, and announcement-management records are not registered sync collections. Fetch those through their existing authorized endpoints; do not infer a change feed from a list cursor.

Document evidence uses the document ID and current header version. Only published
`PERSONAL` and `RECEIPT` metadata appears; `HR_ONLY` and unfinished first uploads
are excluded. A replacement upload advances the header version but retains its
previous accepted revision until publication. Retiring the current revision
clears publication and emits a versioned `DELETE`; ordinary archive retains
readable evidence. Header changes and pending references share their transaction.
Bootstrap directly discovers existing publications, without fabricated historical
deltas. Migration holds preceding header writers until capture is installed.

The canonical record carries employment, classification and immutable revision
metadata. Its owner-only read holds shared people/document/access guards, then
rechecks MFA after acquisition. Administrative document/profile grants do not
substitute for the self read grant. Neither a sync reference nor stored metadata
authorizes file acquisition, export or a business attachment. Those operations
recheck current scope and revision availability independently.

Attendance days have stable projection IDs and versions independent of individual
capture and correction revisions. A capture, review or correction advances the
same employee/date record and captures its invalidation in that business
transaction. Bootstrap includes every day with captured or corrected evidence;
it does not impose a recent-history cutoff. The canonical response contains the
employee ID, projection version and the current daily summary with original
capture schedules, review outcomes and the latest correction. It holds the
shared day and current access guards while acquiring that coherent result.

Empty dates have no attendance record. Scheduled days, holidays and absence
metrics still require the calendar projection; this collection does not invent
an absence or copy the latest schedule over captured evidence. The migration
preserves existing evidence and correction command versions while assigning
projection IDs. It waits for in-flight writers before backfilling, and retains
the schema guards until change capture is installed; a concurrent commit cannot
fall between those steps. Lock acquisition fails after five seconds so an
occupied deployment fails atomically and can be retried. Historical rows need no fabricated delta: bootstrap discovers
them. Subsequent changes remain subject to ordinary after-commit publication.

Calendar replication stores source records, independently of the week currently
displayed. A work-schedule record has one stable ID per employee/effective date
and points to that date's latest immutable assignment revision. Its version is
the assignment revision, so gaps between versions are valid. A later effective
date is another record; changing an earlier date retains its identity. The
original shift snapshots remain embedded in the weekly pattern. Editing a shift
template alone does not change previously assigned schedules.

Roster records have stable IDs and their own versions. A null shift means an
explicit day off; it is not a deleted record. Holidays retain inactive records
as versioned updates, allowing a local calendar to remove the holiday's effect.
Their shared company audience requires at least one owned employment and the
same calendar read grant. It does not expose another employee's rules or roster,
and `company.read` alone does not authorize this employee projection.

Local calendar resolution uses the latest assignment effective on the selected
date, then applies an active holiday, then a roster override. The embedded shift
timezone governs work intervals, overnight shifts and daylight-saving gaps.
These read inputs do not authorize attendance commands or replace the frozen
schedule of already captured evidence. Bootstrap retains all effective dates;
clients bound query/render work separately from stored history.

Payslips reference immutable finalized assessments and have version `0`. Publication captures the entire run's references in one transaction; rollback or cancellation exposes none. The feed carries IDs, never salary amounts or calculation evidence. New collection authorization changes the scope fingerprint, so existing published assessments are discovered through a fresh bootstrap. Explicit selection also admits `payroll.read` for the account's own employments, matching canonical `scope=SELF` reads. Omitted selection still requires `payroll.self.read`; neither mode exposes a colleague's pay through administrative authority.

Payroll payment references use the assessment ID, but belong to a separate collection and monotonically increasing progress version. Preparation, release, cancellation, and reconciliation capture changes atomically. A retry after a confirmed failure retains earlier attempts and advances the same progress resource. Bank destinations and transaction references are excluded from employee progress and sync. Before the first attempt, the canonical progress resource returns an empty version `0`; no payment reference is needed in bootstrap yet. Adding this collection to an existing selection requires a fresh bootstrap.

Expense payments follow the same separate-version contract, using the claim ID.
The canonical response contains `claimId`, `version` and up to twenty `attempts`.
Each preparation or instruction transition advances the progress version once;
receipt replay does not. Claim and approved submission versions remain unchanged.
An empty progress resource has version `0` and requires no bootstrap reference.
Existing attempts are backfilled under a schema guard that waits for preceding
writers and stays held until capture is installed. Their evidence is unchanged;
bootstrap discovers them without fabricated historical changes. Payment rows,
projection heads and pending references roll back together after a failed command.
Explicit finance read grants still select only the account's own employments.
The original `/payments` endpoint retains its list response and `AUTHORIZED`
default. The new `/payment-progress` response defaults to `SELF`.

The canonical Expense record combines the current claim/draft, its latest
submission with current approval and reviews, and a descending activity page.
`historyAfter` is an exclusive claim-version cursor; `historyLimit` defaults to
twenty and accepts 1–20. Older history pages retain the current claim and submission.
Shared claim, people, approval and access guards keep each response coherent.
The response excludes live review actions and cross-claim duplicate-receipt hints;
commands and interactive review retain their own current authorization.

The reference version remains the claim command version. Reassigning its latest
approval publishes another `EXPENSE_CLAIMS` invalidation at that same version;
the embedded approval has its own version. Consume every newly published UPSERT,
even when its claim version matches the stored record. Coalesce repeated IDs
within a pending page, acquire the current record and commit it with the cursor.
Do not deduplicate later feed pages solely by resource ID/version. The original
claim endpoint remains available to clients that only need the draft. Clients
upgrading their local shape to include submission/approval facts must bootstrap
that new local projection so unchanged existing claims also acquire those fields.

Recipient inbox references contain no message text. Publication, read, acknowledgement, and archival capture the corresponding account-owned invalidation in the same transaction as the business change. Failed publication leaves neither inbox nor pending references. An archived item produces `DELETE`, and the canonical endpoint immediately returns `inbox_item_not_found`; physical deletion of its retained record is unnecessary.

An employment ID never grants inbox synchronization. Changing audience groups affects future publication; previously delivered mail stays with its original recipient, subject to current company/read access. New group members do not acquire historic inbox. Existing inbox items are discovered by the first explicit bootstrap. Revocation and scope changes still require replacing the affected local partition.

## Client collection selection

Mobile clients should send an explicit list of collections implemented by that client version, for example `?collections=EXPENSE_CLAIMS,LEAVE_REQUESTS&limit=100`. Both bootstrap and changes accept this optional comma-separated enum set. The effective projection is the intersection of that selection and current self-service permissions. A selection never grants company-wide access. Bootstrap returns the effective `collections` list; absent permissions can narrow it.

Persist the requested selection with the account/company partition and cursor, and send it on every page. Cursors bind the effective selection. Switching to another effective selection, or omitting a selection when the default would include more collections, returns `409 sync_scope_changed`; replace the local partition through bootstrap. Changing order or adding an unauthorized collection does not alter the effective projection. Current permission and ownership changes still invalidate scope independently.

An explicit selection prevents a newly registered server collection from appearing automatically in an older client. A client upgrade that adds support for another collection explicitly changes its selection and replaces the partition. Omitting `collections` selects the original six collections intersected with current permissions. Inbox, Attendance, the three calendar source collections and Expense payments are excluded from that default even when authorized, preserving existing client projections and cursors. Their clients must select these collections explicitly.

Clients with `leave.read` can explicitly request `LEAVE_REQUESTS,LEAVE_BALANCES`
for their own records, matching the existing self-read endpoints. This does not
change omitted-selection feeds or grant team/company exports. `leave.team.read`
alone does not authorize these collections; current membership, ownership, MFA,
and permission checks still apply to every feed and canonical-resource request.

Overtime readers with `overtime.read`, `overtime.manage` or `workforce.read` may
explicitly select `OVERTIME_REQUESTS` for their own employment records. Omitted
selection retains its existing permissions. An approver grant alone cannot
select this collection, and an administrative read grant never widens its
account-owned audience.

Attendance selection also retains account ownership when `workforce.read` allows
broader administrative reads elsewhere. Its canonical endpoint has the same
owner-only scope. Team read/review grants alone do not authorize this collection;
administrative attendance review continues to use its existing endpoints.

An empty set returns `422 invalid_sync_collections`; an unknown enum value returns `400 invalid_request`. A known selection with no authorized collection returns `403 sync_access_denied`. Correct unsupported/empty selections instead of retrying them unchanged. Nonselected records are excluded from snapshot, delta, and pending-publication responses.

## Configuration

Set a dedicated cursor encryption key on every API instance:

```dotenv
HRIS_SYNC_ACTIVE_KEY=v1
HRIS_SYNC_KEYS=v1:<base64-encoded-32-byte-key>
```

Generate the key with `openssl rand -base64 32` and store it in the deployment's secret manager or ignored environment file. Do not reuse the identity key. An absent key disables cursor issuance with `503 sync_unavailable`; it does not generate an ephemeral key. Invalid configured keys stop application startup without logging key material.

For rotation, configure `v1:<old>,v2:<new>` and select `v2` on all API instances. Keep old keys for at least seven days after their last issuance. Removing a key explicitly invalidates its cursors and requires a fresh bootstrap. At most eight keys are accepted. Back up the database and required keys together. After restoring an older database, rotate to a new active key and retire pre-restore cursor keys before reopening sync traffic; a database restore must not make old client positions appear current.

The existing worker publishes and prunes sync metadata every five seconds. It does not need cursor keys. Without a running worker, bootstrap can read current resources, but incremental changes remain pending. Operate the worker with the separate non-bypass `hris_worker_capability`, not migration credentials.

## Bootstrap

Call `GET /api/v1/companies/{companyId}/sync/bootstrap?limit=100`. The limit is 1–200. The response lists the collections authorized for this request and the current resource references:

```json
{
  "collections": ["EXPENSE_CLAIMS"],
  "items": [
    {
      "collection": "EXPENSE_CLAIMS",
      "id": "7cd95cc0-0ca9-4c2d-b0d1-879ec31d8568",
      "version": 2
    }
  ],
  "nextCursor": null,
  "changesCursor": "<opaque-cursor>",
  "serverTime": "2026-10-09T08:00:00Z"
}
```

Follow `nextCursor` on the same bootstrap endpoint until it is null. Only the final page supplies `changesCursor`. Pagination uses collection/resource keys and a fixed initial publication position. Inserts that sort before an earlier page, edits, and deletions during bootstrap are recovered by the subsequent feed. These are current references over multiple short transactions, not a frozen historical dataset.

A bootstrap expires fifteen minutes after its first page and is limited to 1,000 pages. Restart an expired or oversized bootstrap; do not concatenate unrelated page chains. Use an adequate page size. At most 200 employments may belong to one account/company sync scope; an excessive scope fails explicitly instead of silently truncating results.

Build a replacement local partition in staging storage. Hydrate its references through the canonical endpoints using bounded concurrency. Continue from `changesCursor` to reconcile changes made during bootstrap, then replace the old partition atomically. A changed or revoked scope invalidates the entire staging operation. No response authorizes access to a canonical resource beyond that resource's independent live checks.

## Incremental changes

Call `GET /api/v1/companies/{companyId}/sync/changes?cursor=<opaque-cursor>&limit=100`:

```json
{
  "items": [
    {
      "collection": "EXPENSE_CLAIMS",
      "id": "7cd95cc0-0ca9-4c2d-b0d1-879ec31d8568",
      "version": 3,
      "operation": "UPSERT"
    }
  ],
  "cursor": "<next-opaque-cursor>",
  "hasMore": false,
  "pendingPublication": false,
  "pollAfterSeconds": 5,
  "serverTime": "2026-10-09T08:00:05Z"
}
```

Persist each page's references in a local inbox and advance its cursor in the same local transaction. Resolve that inbox through bounded canonical fetches. Never advance a cursor while discarding unprocessed references. Replaying a cursor may repeat changes; applying a reference must be idempotent. A canonical fetch can return a version newer than the feed reference. Keep that newer version and reject older results that arrive later.

`UPSERT` invalidates the cached resource and schedules a current authorized fetch, including when its version equals the last known version. Publication order, rather than a version increase alone, identifies new invalidations. `DELETE` removes the resource, including at an equal version. Process per-resource changes in feed order. Retain the last applied reference version in local sync metadata and cancel obsolete hydration when removing a resource, so a response started before deletion cannot recreate it. Tombstones retain raw identity and owner scope so a deleted database row need not exist for filtering. The current business APIs preserve request history and do not introduce physical-delete commands through sync.

When `hasMore` is true, follow the cursor to finish the captured publication range. New publications are handled in a later range, so a busy company cannot extend one page chain forever. When caught up, wait at least `pollAfterSeconds` before polling. `pendingPublication` indicates authorized source changes still awaiting the worker; it does not authorize a tight loop. Resume promptly on a completed local command, foreground entry, and reconnect, while retaining finite request/retry budgets and cancellation ownership.

An incremental cursor expires after seven days of inactivity. Completing a captured range renews its lifetime; intermediate pages retain the original expiry. Published events are retained for at least eight days. An older client must bootstrap again. Pending changes are never aged out before publication, including transactions that committed late.

## Access and failures

Cursors are encrypted and authenticated, not client-editable offsets. They bind account, company, credential version, company/membership versions, the original/live permission intersection, owned employment IDs, collections, and the database partition epoch. Every page rechecks current scope under short transaction-owned shared guards. Multiple readers may run together; access writers serialize against them.

| Error | Client response |
| --- | --- |
| `401 session_revoked` | Stop requests/outbox and authenticate again. |
| `403 company_access_denied` / `sync_access_denied` | Stop sync, remove inaccessible cached data, and suspend that partition's queued commands. |
| `409 sync_scope_changed` | Discard the previous sync partition and re-bootstrap under current access. Revalidate queued intents before replay. |
| `409 sync_cursor_expired` / `sync_cursor_out_of_range` / `sync_cursor_invalidated` | Discard that cursor and stage a bootstrap replacement. Keep the owned published view until replacement commits. |
| `409 sync_bootstrap_limit` | Restart with a sufficient page size or review the collection size; do not loop with the failing token. |
| `422 invalid_sync_cursor` | Reject a malformed, wrong-account/company, or wrong-phase token; correct the client partition and bootstrap. |
| `422 invalid_sync_limit` / `sync_scope_limit` | Correct the bounded request or account configuration. |
| `503 sync_unavailable` | Cursor keys are not configured. Use bounded backoff and report the service configuration issue. |

Use the [localizable problem contract](mobile-api.md#localizable-errors) for messages. Do not decode cursor contents or use a device timestamp as a conflict/version authority. Never log bearer tokens or cursor query values in application, proxy, analytics, or crash reporting.

Cursor retention, data freshness, local ownership and live authorization have
separate lifetimes. Ordinary cursor or credential expiry does not delete owned
local facts. A confirmed scope/access denial instead gates the affected projection
while cleanup and reauthorization proceed; a storage failure cannot turn that
denial into permission to display retained data. Offline clients cannot learn a
new revocation until they reconnect. Apply explicit account/company isolation,
device protection and logout/removal policy rather than treating a freshness
timestamp as proof of access. See the mobile [read and sync model](https://github.com/fajarxfce/hris-mobile/blob/main/docs/local-first.md)
for current coverage and the remaining collection integrations.

## Publication and resource ownership

Storage triggers copy aggregate IDs, owner IDs, versions, and operation kinds into `mobile_sync_changes` in the same transaction as the business write, receipt, and audit. No permissions, DTO mapping, retries, or application policy run in those triggers.

The worker assigns per-company sequence positions only to committed changes. A transaction-owned advisory guard serializes publishers, and the head and assigned positions commit together. A rolled-back or interrupted publication leaves its source records pending. The queue does not use an allocated sequence number or application timestamp as a public commit-order cursor.

A pass publishes at most 200 records, then prunes at most 500 eligible records in a separate transaction. A competing maintenance pass exits without waiting for the publication guard. Pruning advances a retained-history floor; clients behind that floor reset explicitly. API readers hold the head's shared row guard only for one bounded page, so pruning cannot invalidate an already checked page mid-transaction. The timer has one owned thread and one periodic task, with no task allocated per event. Existing SQL/lock timeouts and interruption boundaries apply.

These limits bound individual work and memory; they are not throughput guarantees. Measure backlog age, publication capacity, query latency, and retention on the target deployment before expanding registered collections or declaring performance targets met.

## Coworker directory projection

`DIRECTORY_EMPLOYEES` is an explicit public company collection. It does not change
omitted collection selections or grant access to private profiles, salaries,
attendance, or another employee's requests. Its canonical response contains only
`id`, `version`, `employeeNumber`, `name`, `positionId`, `position`, `departmentId`
and `department`. Unknown, foreign and withdrawn records return the same scoped
`404 directory_employee_not_found` outcome. Every canonical read and sync page
rechecks current credentials, membership, grants and MFA after waiting; assurance
is checked again before releasing the result, including cursor encoding.

The People worker derives these records from current employment and assignment
facts using the company-local date. Only active/probation employments between
their inclusive start/end dates are published. A future employee's identity is
not published before becoming eligible. The worker schedules the next effective
revision, start, or end boundary; elapsed days do not require a business write to
produce the corresponding change. Cancelled revisions are excluded. A timezone
change requeues the company. Eligibility belongs to the domain policy, separately
from raw SQL acquisition and invalidation capture.

Source transactions enqueue raw employment identities when employment history,
a cancellation, a public name or an assigned unit label changes. A person-name
trigger also captures linked employments after transfers across companies; its
restricted trigger function returns no data and never acquires private profile
fields. Its dedicated non-login, non-bypass role can read only employment IDs,
company IDs and person links, and write the raw refresh queue. API and worker
logins do not inherit that role; the function does not run as the migrator.
Other profile edits do not invalidate this public projection. Existing
employments are queued under migration guards before subsequent writers proceed.

Each fixed-delay worker pass visits at most four companies and divides its
200-record budget among the selected companies. A single busy company can use
the whole batch. Competing workers skip locked rows. Projection writes,
next due instants and raw sync references share one transaction. A source write
waiting on that queue row marks it due after the worker commits, so completion
cannot erase a later invalidation. Source guards are never acquired after queue
rows. Company timezone is guarded; source acquisition uses one MVCC statement.
Cancellation rolls back the entire company batch, and shutdown cancels the owned
timer. These limits bound work, not a measured throughput guarantee.

Identical public fields retain their version and emit no change. A removal clears
the public fields and advances the same record into a tombstone; a later return
advances that identity again. Records retain their own revision, independently of
employment, person or organization command versions. The ordinary after-commit
publisher assigns sync positions. Due projection rows contribute to
`pendingPublication`, including during first acquisition. A client completing a
bootstrap must drain its changes and wait for pending publication within its
finite sync budget before publishing a complete replacement. An unfinished
replacement never hides the previously published owned local directory.

The existing interactive directory remains a live, date-derived API. The new
canonical projection is eventually consistent with source changes while its
worker runs. A stopped projection worker is observable as pending work; neither
an API read nor the mobile client secretly executes worker policy. The mobile
application retains its separate integration and offline acceptance work.
