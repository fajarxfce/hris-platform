# Mobile synchronization

Synchronization provides a bounded, authorized change feed for local-first clients. It is an invalidation protocol: references identify which canonical resources to fetch or remove. It does not expose audit payloads or replace feature commands, idempotency receipts, and optimistic versions.

The first supported collections are:

| Collection | Required permission | Audience | Canonical resource |
| --- | --- | --- | --- |
| `EXPENSE_CLAIMS` | `expenses.self.manage` | Current account's employments in the company | `/api/v1/companies/{companyId}/expenses/claims/{id}` |
| `LEAVE_REQUESTS` | `leave.self.manage` | Current account's employments or immutable request owner | `/api/v1/companies/{companyId}/leave/requests/{id}` |

All request statuses are included. A cancellation or withdrawal is an `UPSERT` with a new version, not a deletion. Team/company administration rights do not grant an employee-wide export. Documents, attendance, balances, schedules, approval inboxes, payroll, and communication are not registered sync collections yet. Fetch those through their existing authorized endpoints; do not infer a change feed from a list cursor.

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

`UPSERT` invalidates the cached resource and schedules a current authorized fetch. `DELETE` removes the resource, including when its version equals the last known version. Process per-resource changes in feed order. Tombstones retain raw identity and owner scope so a deleted database row need not exist for filtering. The current business APIs preserve request history and do not introduce physical-delete commands through sync.

When `hasMore` is true, follow the cursor to finish the captured publication range. New publications are handled in a later range, so a busy company cannot extend one page chain forever. When caught up, wait at least `pollAfterSeconds` before polling. `pendingPublication` indicates authorized source changes still awaiting the worker; it does not authorize a tight loop. Resume promptly on a completed local command, foreground entry, and reconnect, while retaining finite request/retry budgets and cancellation ownership.

An incremental cursor expires after seven days of inactivity. Completing a captured range renews its lifetime; intermediate pages retain the original expiry. Published events are retained for at least eight days. An older client must bootstrap again. Pending changes are never aged out before publication, including transactions that committed late.

## Access and failures

Cursors are encrypted and authenticated, not client-editable offsets. They bind account, company, credential version, company/membership versions, the original/live permission intersection, owned employment IDs, collections, and the database partition epoch. Every page rechecks current scope under short transaction-owned shared guards. Multiple readers may run together; access writers serialize against them.

| Error | Client response |
| --- | --- |
| `401 session_revoked` | Stop requests/outbox and authenticate again. |
| `403 company_access_denied` / `sync_access_denied` | Stop sync, remove inaccessible cached data, and suspend that partition's queued commands. |
| `409 sync_scope_changed` | Discard the previous sync partition and re-bootstrap under current access. Revalidate queued intents before replay. |
| `409 sync_cursor_expired` / `sync_cursor_out_of_range` / `sync_cursor_invalidated` | Discard that cursor and replace the local snapshot through bootstrap. |
| `409 sync_bootstrap_limit` | Restart with a sufficient page size or review the collection size; do not loop with the failing token. |
| `422 invalid_sync_cursor` | Reject a malformed, wrong-account/company, or wrong-phase token; correct the client partition and bootstrap. |
| `422 invalid_sync_limit` / `sync_scope_limit` | Correct the bounded request or account configuration. |
| `503 sync_unavailable` | Cursor keys are not configured. Use bounded backoff and report the service configuration issue. |

Use the [localizable problem contract](mobile-api.md#localizable-errors) for messages. Do not decode cursor contents or use a device timestamp as a conflict/version authority. Never log bearer tokens or cursor query values in application, proxy, analytics, or crash reporting. Offline revocation cannot erase data on a disconnected device; enforce the documented local cache expiry and device policy.

## Publication and resource ownership

Storage triggers copy aggregate IDs, owner IDs, versions, and operation kinds into `mobile_sync_changes` in the same transaction as the business write, receipt, and audit. No permissions, DTO mapping, retries, or application policy run in those triggers.

The worker assigns per-company sequence positions only to committed changes. A transaction-owned advisory guard serializes publishers, and the head and assigned positions commit together. A rolled-back or interrupted publication leaves its source records pending. The queue does not use an allocated sequence number or application timestamp as a public commit-order cursor.

A pass publishes at most 200 records, then prunes at most 500 eligible records in a separate transaction. A competing maintenance pass exits without waiting for the publication guard. Pruning advances a retained-history floor; clients behind that floor reset explicitly. API readers hold the head's shared row guard only for one bounded page, so pruning cannot invalidate an already checked page mid-transaction. The timer has one owned thread and one periodic task, with no task allocated per event. Existing SQL/lock timeouts and interruption boundaries apply.

These limits bound individual work and memory; they are not throughput guarantees. Measure backlog age, publication capacity, query latency, and retention on the target deployment before expanding registered collections or declaring performance targets met.
