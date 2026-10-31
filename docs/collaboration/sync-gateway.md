# Native classic sync gateway

The current foundation reads retained DIRECT bindings in one bounded statement
through existing forced RLS. The requesting account must be a participant in the
selected company. No migration, privilege expansion or peer-active filter is
added: peer offboarding and native-parent cleanup do not erase historical rooms.
Current caller admission remains the responsibility of the sync use case.

The pure scope policy detaches and deterministically sorts rooms, rejects foreign
ownership/server, invalid IDs and duplicate conversations/rooms, and hashes the
complete ownership/binding set. A 10,001-row overfetch rejects more than 10,000
rooms explicitly; it never silently drops a room. This bound is not a capacity
measurement. The append-only property of schemas 110/111 applies only to DIRECT;
groups, context membership and leave/restore need their own policy.

The cursor repository seals the scope digest, private provider position and
provider filter ID using the existing identity AES-GCM keyring with a distinct
purpose bound to company, account, stable collaboration device and Matrix server.
It contains no public gateway bearer/generation, so normal credential refresh
does not invalidate a position. Provider since is bounded to 1,024 ASCII
characters, filter ID to 128 and encrypted cursor to 4,096. Fresh nonces prevent
deterministic ciphertext. No position age deletes history.

Malformed or tampered positions and wrong owners return
`invalid_collaboration_sync_cursor`. A retired envelope key returns
`collaboration_sync_reset_required`; it does not clear the SDK store or silently
reinterpret unauthenticated data. A client must reacquire an initial position
while retaining its local encrypted history and deduplication records. Old keys
remain readable while retained during key rotation. Provider database rollback
requires explicit initial-position recovery; restoration acceptance is pending.

The public sync endpoint, owner-bounded long polling, filter registration and
immutable creation-state hydration are the next implementation step. Follow the
[actual scope recovery behavior](sync-scope-recovery.md): filter every response
bucket, reset provider since when scope grows, and obtain actual immutable create
metadata rather than inventing events or merging mutable current state ahead of
an old timeline. Native SDK, durable offline outbox and large-scope acceptance
remain required before accepting chat.

[Validation](../measurements/collaboration-sync-foundation-2026-10-31.json):
domain, affected PostgreSQL storage and cursor crypto tests, format and architecture
passed in 22s. Shared DI was not changed in this foundation.
