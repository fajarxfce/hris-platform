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

## Admitted classic endpoint

`GET /_matrix/client/v3/sync` now accepts one opaque gateway bearer and bounded
`since`, `timeout` (0–30,000 ms), `full_state` and `set_presence` parameters. Unknown
or duplicate parameters, URL credentials and GET bodies are rejected. Client
filters are not exposed yet. Presence is not published by this first transport.
The response is `no-store`; only the encrypted HRIS cursor becomes `next_batch`.

The existing native operation owner captures current account/company/CHAT,
membership, MFA, minimum build, native parent, device and gateway head under the
normal guards. It reads the immutable room projection in that transaction, then
rechecks admission and the exact scope before and after provider I/O, including
after sealing the new cursor. No transaction or SQL lock spans the network wait.
Late revocation, expiry, scope growth or gateway replacement withholds the batch
and cursor, even when the provider returned an error. A retry keeps the client's
previous acknowledged position.

Scope digest changes reacquire an initial provider sync without `since`; using
only `full_state` misses older invitations. Explicit `full_state=true` is passed
only when requested; it bypasses Synapse long polling. This reset changes neither
the encrypted client store nor its durable deduplication records. See the
[actual provider behavior](sync-scope-recovery.md).

## Provider mapping and ownership

Each Matrix user has a fixed filter shape (20-event timelines, full membership,
no provider account-data/presence/ephemeral observation). The internal own-user
filter POST is deduplicated by pinned Synapse even across scope changes. Avoiding
room lists in persistent provider filters prevents retaining a growing copy of
the entire list for every newly created DM. This does not grant room authority:
HRIS independently filters **join, invite and leave** buckets against its exact
captured bindings, before mapping any room events. Other buckets are omitted.

Initial/full-state joined rooms acquire the actual immutable `m.room.create`
event from a fixed room-state read when it is absent from state and timeline.
Only that verified creation event is inserted into the state base; mutable
current state is not merged ahead of the older timeline. Sender/room/creator,
v12 and federation/encryption settings are checked. Ordinary encrypted timeline
events retain their opaque ciphertext; plaintext messages and foreign room
senders fail the batch. Stripped invitation state remains separate.

To-device delivery accepts encrypted envelopes from self or retained DIRECT
participants. The SDK must authenticate/decrypt them; filtering is not Olm
acceptance. Future key distribution remains subject to current recipient policy.
Device-list changes are intersected with those users; bounded own key/fallback
counts are retained. Provider account data/presence are excluded, and `m.direct`
is derived from HRIS bindings. Ephemeral receipts/typing and further account-data,
verification, deletion and backup flows still require their own implementation.
The limited room-history `prev_batch` remains an opaque provider pagination value;
no public history endpoint accepts it yet. That future endpoint must authorize
the exact room independently and bind its pagination to the owner/room.

Long polling has a separate 64-slot pool, with at most one active operation per
Matrix user/device; it does not consume the 32 short control slots. Acquisition
never queues. Each leased connection permits one in-flight fixed request and owns
its future, body subscription and close/release. Interruption and shutdown cancel
pending I/O; double close is harmless. Polling reserves response time inside the
captured admission and has a 35-second maximum network budget. Sync bodies are
bounded to 8 MiB, state reads to 1 MiB and filter responses to 64 KiB. Existing
control/key operation limits remain unchanged. JSON depth/string/number limits,
duplicate detection, no redirects and identity content encoding remain enforced.
These are finite resource limits, not a 5,000-user capacity result.

The synchronous servlet cannot promise immediate detection of a disconnected
mobile HTTP request. Provider work remains bounded by its deadline; direct
interruption/shutdown cancellation is tested. Actual Android/web SDK owner
cancellation, long-poll capacity, client scope-reset ingestion, durable outbox,
provider rollback recovery remain acceptance gates. Discovery,
[room join/state/member reads](room-access.md) are now implemented. History
[pagination and context/event reads](room-history.md) now seal per-room positions.
Encrypted sends and product UI must be connected before accepting chat.

[Validation](../measurements/collaboration-sync-foundation-2026-10-31.json):
domain, affected PostgreSQL storage and cursor crypto tests, format and architecture
passed in 22s. Shared DI was not changed in that foundation. The separate
[gateway evidence](../measurements/collaboration-sync-gateway-2026-10-31.json)
records subsequent provider/HTTP/race/termination tests and API/worker DI.
