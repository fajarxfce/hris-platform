# Company jobs

Jobs track durable work submitted by business endpoints. There is no public endpoint for arbitrary job definitions. API and worker use the same persisted queue; a browser or mobile connection does not own the worker's lifetime.

## Reads and access

- `GET /api/v1/companies/{companyId}/jobs?size=50`
- `GET /api/v1/companies/{companyId}/jobs/{jobId}`

An active member can read their own jobs. `jobs.read` grants company-wide visibility. `jobs.manage` permits cancellation of another actor's job; it does not implicitly grant read access. Current company, membership, account, credentials, and MFA are checked after pending guards. Available actions use the intersection of the request's original and current permissions and remain hints, not authorization tokens.

The response contains job ID/type, status, progress, attempts, cancellation state, a safe failure code, timestamps, version, and available actions. Request values, checkpoints, credentials, and exception text are excluded. Clients translate kind/status/failure codes; unknown job kinds can use their stable identifier, and unknown failures use a generic localized message.

Lists default to 50 and permit at most 200 rows. A continuation uses both `beforeAt` and `beforeId`, copied exactly from `nextCreatedAt` and `nextId`. Results descend by creation timestamp and ID. Preserve timestamp microseconds; rounding to milliseconds can skip jobs. Null continuation fields indicate exhaustion. Each page is a live SQL snapshot. This endpoint is an operational view, not the commit-ordered mobile change feed.

`FIXED_TOTAL` work succeeds only after completing its declared total. `UPPER_BOUND` work may succeed after exhausting a smaller source. Clients must use status and progress mode rather than assuming every successful job has a 100% count. `scheduledFor` records the original requested schedule; `availableAt` controls eligibility after scheduling, deferral, or cancellation cleanup.

## Cancellation and uncertain responses

`POST /api/v1/companies/{companyId}/jobs/{jobId}/cancel` accepts:

```json
{ "expectedVersion": 3 }
```

The owner or a current `jobs.manage` actor can request cancellation of queued/running work. A first request requires the observed version. Access is checked again on every call. An already requested cancellation returns the current job without advancing cleanup eligibility or adding another audit event; this monotonic operation is naturally idempotent and does not require a separate operation ID.

The response acknowledges `cancellationRequested`, not immediate terminal cancellation. The feature worker owns stopping and cleanup. Completed business work stays committed. A conflict returns `stale_version` or `job_already_finished`; inaccessible jobs use `job_not_found`. Common company/session/MFA failures retain the shared problem contract.

If the response is interrupted or unreadable, the client must not assume rollback or resubmit automatically. Read the job again. A recorded cancellation requires no further command; otherwise, review the refreshed status/actions and explicitly request cancellation with that version. Competing or late cancellation requests still share the same persisted monotonic flag. Do not reuse this reasoning for unrelated commands that require operation receipts.

## Dashboard

`/administration/jobs` displays one page, explicit refresh, URL continuation, and a scoped details panel. A `job` query parameter opens a canonical detail read. Company changes discard the previous company's continuation and selected job. Cancellation uses confirmation and a single in-flight command. An unconfirmed response removes the old details and requires a status read before another command.

The list and detail controllers have separate request lifetimes. Accepted detail observations update a matching visible row without downgrading its version or replacing another company/page. Closing a panel preserves keyboard focus and avoids an unnecessary list request. Each list refresh is an independent live snapshot; cancellation always uses the canonical detail version. There is no polling, growing page cache, or browser worker scheduler. [Dashboard](dashboard.md) describes the shared layout and validation commands.
