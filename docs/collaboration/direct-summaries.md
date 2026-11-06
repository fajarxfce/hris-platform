# Owned direct-conversation summaries

The conversation list can request counterpart labels without scanning the contact
directory or making a separate request for every row. These are admitted live
metadata reads; owned offline projections and content synchronization are separate.

`GET /api/v1/companies/{companyId}/collaboration/direct-conversations/summaries`
accepts `after` (UUID) and `limit` (default 30, range 1–50). It returns the standard
`{items,nextCursor}` page. Each item is `{conversation,peer}`: `conversation` keeps
the existing immutable seven-field identity and `peer` contains only `accountId`
and `displayName`. `GET /{conversationId}/summary` returns one such item, or the
same non-disclosing not-found result for missing and unowned identities. Existing
identity-only collection/detail endpoints remain compatible.

Both endpoints reuse the native Android/iOS admission guards, current company,
chat permission, client policy and native parent checks. Admission is checked
again after acquisition. Responses use `Cache-Control: no-store`; browser-session
transport and anonymous callers cannot read them.

One database statement selects the bounded conversation page and joins each
counterpart by primary key. The statement selects the conversation columns and
account display name, not email, credentials or private profiles. It explicitly
filters the calling account and selected company, in addition to forced RLS.
Pagination overfetches at most one row (51 total) and uses the existing UUID order.
Acquisition and mapping share the database failure boundary. The repository
validates ownership again while mapping and never publishes a partial page.

The label is the counterpart's current account display name. It can change
without rewriting or versioning immutable conversation identity. An admitted
participant retains this label after the counterpart is offboarded, as part of
owned conversation metadata. It does not claim that the counterpart is active or
eligible for new messages; opening/sending still reauthorizes independently.
No schema, provider metadata, DI dependency or worker graph changes are needed.

These bounds are not a 5,000-employee capacity measurement. Mobile adapters and
product conversation-list/offline acceptance remain separate work.

[Verification](../measurements/collaboration-direct-summaries-2026-11-01.json)
records 25 database and nine HTTP cases (75s), followed by the final HTTP fixture
resource-ownership check (49s). The initial cookie-fixture failure is retained.

## Incremental metadata follow-up

The current endpoint is **not** a change feed. Mobile34e68b12 removes identical
local writes and partial-preview flicker; it does not reduce unchanged HTTP pages
to deltas. The user's changed-only synchronization requirement remains open.

Implementation must keep immutable direct identity (`version=0`, V110's mutation
trigger) separate from mutable peer-label projection revisions. Do not repurpose
conversation version or infer changes from a UUID page or wall-clock timestamp.
Existing `mobile_sync_heads`/`mobile_sync_changes` and their worker publisher assign
positions after business transactions commit; inspect V39 and subsequent collection
migrations before reusing that raw publication mechanism. A native collaboration
reader must retain current native parent, build, company, permission and account
guards; access to a generic company sync cursor is not native chat admission.

The follow-up needs bounded initial snapshot and incremental pages, a captured
publication boundary, resumable opaque owner-scoped cursors, atomic local facts plus
cursor publication, no-change responses, deletion markers and explicit expired-cursor
recovery. Tests must cover late transaction commits, duplicate/uncertain page replies,
peer-name changes, snapshot/delta overlap, removals, revoked admission and foreign
owner/company cursors. Offboarding a counterpart alone must not erase previously
owned metadata or pretend they remain eligible to receive new messages.

Group migrations V117–137 exist on the separate communication-space branch and have
not shipped. Coordinate their numbering/integration before adding the metadata
projection migration. No new schema, delta endpoint or production rollout is claimed
by these continuation notes.

V116.1 now provides [validated raw capture and projection storage](direct-summary-sync.md).
Worker execution, the admitted delta reader and Mobile integration remain pending;
this additive version precedes the unshipped group range without renumbering it.
