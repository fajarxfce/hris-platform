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
