# Shared approval history

The approval request screen links to retained decisions and approver changes.
History is available to the current request author/beneficiary, an approval
administrator, or a currently eligible direct/delegated approver. It uses the same
company/resource boundary as request review; a former assignee does not retain
access just because their account appears in an old record.

`GET /api/v1/companies/{companyId}/approvals/{id}/history` returns:

- `request`: the current shared approval snapshot, including its observed `version`.
- `decisions`: at most eight actual decisions, ordered by workflow step. Each
  contains `step`, `actorId`, `decidingFor`, `decision`, `reason` and `decidedAt`.
- `reassignments`: `{items, nextCursor}`. Each item contains immutable `revision`,
  `step`, `assignees`, `actorId`, `reason` and `recordedAt`.

Steps are zero-based on the API and one-based in the UI. Decisions retain the
actual actor and delegated account. Reassignment revisions follow request versions,
so gaps are valid when decisions also advanced the request. Current assignments
never substitute for old decision evidence. Account UUIDs identify the persisted
actors; the API does not invent historical names or expose email addresses.
Cancellation state does not imply a recorded cancellation instant. Business-specific
leave, expense, overtime and payroll histories retain their separate events.

The reassignment page accepts `limit` 1–200 (default 50), an exclusive positive
revision `after`, and `expectedVersion` from the preceding response. Continuation
requires that version. A request that changes between pages returns HTTP 409
`approval_changed`; refresh without either cursor/version starts a current first
page. Invalid bounds return HTTP 422 `invalid_approval_history_query`. Revision and
version query values fit JavaScript safe integers. A bounded extra row determines
whether another page exists; no count query or growing client page cache is used.

The use case owns a read transaction and shared approval, company, membership and
account guards. Request metadata, retained decisions and reassignment rows remain
coherent while writers wait. Original and current permissions are intersected.
Session assurance and delegated eligibility are checked after the final history
acquisition, including expiration during I/O. Cancellation propagates, releases the
transaction, and never becomes an empty successful page. Reading creates no audit,
operation receipt, decision or business mutation.

The existing V4 immutable tables and forced company RLS provide storage. There is
no migration, backfill, new secret family or retention change for this reader.
Acquisition and mapping share the database failure boundary; query ranges and
response dimensions are finite. The API and worker use the same module DI entry.

The dashboard retains one 50-row page, with first/next and explicit refresh actions.
A changed request or lost access clears the prior facts. Leaving, hiding or
replacing the account/company cancels outstanding reads and fences late results.
Foreground admission reacquires the current first page. The request's inbox return
context survives opening history. English and Indonesian labels, company timezone,
plain escaped reasons and horizontally contained tables use existing UI components.

Local verification is recorded in [measurements](measurements/approval-history-2026-10-30.json).
See [audit baseline](approval-history-audit-2026-10-30.md) and the
[full feature audit](feature-audit-2026-10-30.md). This addition does not close the
remaining module/Mobile review or imply any change to the private development stack.
