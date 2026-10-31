# Direct conversation control

Schema 110 retains a canonical direct conversation for an unordered pair of HRIS
accounts within one company. The two accounts and creator never change. This
identity is durable intent; no provider room, encryption key or message is created
by these endpoints. Group and resource-context rooms have separate membership
lifecycles and must not reuse a direct pair by silently changing its audience.

## Native contract

`POST /api/v1/companies/{companyId}/collaboration/direct-conversations` receives
`{"peerAccountId":"<uuid>"}` and a UUID `Idempotency-Key`. It returns the existing
standard mutation response `{id,version}`. Repeating an operation returns the same
receipt; changing its peer conflicts. Opening the same pair with another operation
or from the opposite account resolves to the same identity. This creation command
has no prior version to observe; immutable identity starts at version zero. Future
mutable provisioning/member commands require their own observed versions.

Native authentication, positive Android/iOS build, both collaboration/provider
flags, current company membership and `collaboration.chat` are required. The peer
is reauthorized on every open, including receipt replay: active account, active
company membership and chat permission. Directory selection conveys no authority.
Self-chat is rejected. Unavailable/missing/foreign peers share one safe error.

`GET` on the collection accepts `after` (UUID) and `limit` (default 30, range 1–50).
`GET /{conversationId}` returns one owned identity or a non-disclosing not-found.
The metadata contains only id, companyId, both account IDs, creator, timestamp and
version. All responses use the native no-store contract. Browser-only transport
and unexpected mutation fields are rejected. Pagination is live UUID navigation,
not a message synchronization cursor or an immutable database snapshot. Clients
must reset it when the account/company changes.

A currently admitted participant can still read retained DM metadata after the
peer is offboarded. Opening it again for new communication requires the peer's
current eligibility. This distinction does not grant access to provider contents;
send/sync/media still require their own conversation admission and reconciliation.
Ordinary session expiry and company departure do not delete the retained pair.

## Transactions and isolation

Command lock order is operation, canonical pair, client policy, company,
membership, ordered account guards, and native parent. The stored pair uses UUID
text order to match PostgreSQL's unsigned byte ordering.
Account guards separately use the identity module's existing Java UUID natural
ordering (signed longs), preserving lock order with account-administration writers.
Pair lock contention retains the existing bounded transaction and lock timeouts. Database uniqueness also rejects duplicate pairs.

Current native access, credential version, parent version, MFA and scheduled
client/module policy are checked after acquiring guards and before completion.
Both account guards protect current peer eligibility. Pair, operation receipt,
audit and outbox commit together. A late denial, journal failure or cancellation
rolls all of them back. The audit carries the identity/version, without names,
emails or message content. No provider I/O occurs inside this transaction.

Forced RLS restricts rows to the selected company and either participant. There
is no runtime UPDATE/DELETE policy; an immutable-row trigger also prevents
privileged accidental reassignment/deletion. No SECURITY DEFINER reader or broad
company-wide audience is needed for this two-account identity. Foreign-key
references retain the identities without binding lifetime to a native session.

## Verification and next integration

See [recorded evidence](../measurements/collaboration-direct-2026-10-31.json).
Actual PostgreSQL exercises opposite-direction races, response loss, invalid
peers, unrelated accounts/companies, paging, late expiry, rollback and cancellation.
The migration test upgrades 109 to 110, restores a real pg_dump into a separate
database, and repeats retained identity/RLS/uniqueness checks. API tests use an
intentionally unavailable private Matrix provider.

Next: durable, fenced provider-room reconciliation, followed by admitted Matrix
key/sync/content routes and client SDK/UI integration. A control receipt is not a
claim that CHAT-01, offline messaging or E2EE delivery is accepted.

The separate [native room setup/status subresource](direct-room-provisioning.md)
now provisions and verifies its Matrix room through the durable worker. Opening
this immutable pair still does not itself create a provider room or admit content.
