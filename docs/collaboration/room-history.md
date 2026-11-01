# Native DIRECT room history

The gateway now exposes these fixed reads after current native admission and
exact retained account/company/server/room ownership:

| Route under `/_matrix/client/v3/rooms/{roomId}` | Accepted parameters |
| --- | --- |
| `GET /messages` | `dir=b|f`, optional `from`/`to`, `limit=1..100` (default 10), optional membership `filter` |
| `GET /context/{eventId}` | `limit=0..100` (default 10), optional membership `filter` |
| `GET /event/{eventId}` | None |

All reads are no-store. Duplicate/unknown query parameters, request bodies,
URL credentials, malformed event IDs and excessive ranges are rejected. There
is no arbitrary provider proxy or event/sender filter. Current admission and
the immutable room binding are checked before and after external acquisition;
late denial withholds results even after a successful provider response.
Ordinary expiry and peer offboarding do not delete retained owned history.

The pinned native SDK requests lazy member loading when opening an event-focused
timeline. Both paged and context reads accept a JSON `filter` of at most 256
characters containing only boolean `lazy_load_members` and
`include_redundant_members`. Redundant members require lazy loading. An absent
filter or `{}` retains ordinary behavior. The gateway validates this typed
selection and forwards it to the provider; it does not silently discard it.
Duplicate JSON keys, trailing tokens, unknown fields and nonboolean values fail
before provider I/O. Event/sender filtering would require its own cursor contract
and remains unsupported.

## Positions and progress

Sync timeline `prev_batch`, page `start`/`end`, and context `start`/`end` use
AES-GCM positions bound to company, account, device, Matrix server and room.
The existing server keyring encrypts them with a distinct
`collaboration.room.history.v1` purpose. Raw provider positions never leave the
adapter. The public envelope is bounded to 4,096 ASCII characters; the private
position to 1,024. No age expiry is imposed on owned history positions.

Room positions and sync `next_batch` are different purposes. Member `at` still
accepts a same-owner sync position, as specified in [room access](room-access.md).
Tampering or another owner/room fails before history I/O. Retired encryption
keys return `collaboration_history_reset_required`; clients must reacquire a
position without deleting the existing store. Rotation retaining the old key
continues to work. These wire contracts do not establish SDK reset recovery or
provider database rollback recovery.

The adapter compares private positions before randomized encryption. An empty
page with no progress omits `end`; a nonempty repeated position fails safely.
An empty page that advances retains its continuation, because visibility gaps
must not truncate history. When `start` equals the requested private position,
the original opaque `from` is reused. Context positions can paginate in both
directions. No server retry loop is introduced.

## Mapping and resources

History shares the fixed DIRECT event mapper with sync and state reads: exact
room, canonical pair, creator, v12/federation and encryption constraints.
Single-event/context reads verify the requested event ID. Repeated timeline
IDs, duplicate state keys, non-state events in state, foreign events, plaintext
messages and malformed arrays fail without partial output. Unknown top-level
provider fields are not forwarded. This gateway handles ciphertext; actual
SDK authentication/decryption is a separate integration test.

The existing 32-slot control transport owns each request independently of sync
polling. Requests use fixed escaped paths and the shorter of the configured
five-second timeout and captured admission deadline. History responses are
bounded to eight MiB, event arrays to the requested limit and state to 256.
Control JSON now also bounds nesting to 64, strings to one MiB and numeric
lexemes to 32 characters, in addition to duplicate/trailing-token rejection.
Cancellation and acquisition errors release the permit; there is no wait queue.

[Encrypted room sending](room-messages.md) is implemented. Next connect the actual
pinned SDK through HRIS, followed by durable outbox and product UI. This does not accept
CHAT-01–15, call/meeting delivery, capacity or a platform rollout.

[Validation](../measurements/collaboration-room-history-2026-10-31.json) retains
24 source hashes, the fixture-only initial 429 failure and corrected 59s focused
checks. Full 426s regression passes 34 domain, 194 data, 159 selected API and 112
worker cases (one existing opt-in skip), format/architecture and both DI graphs.
No migration, live service or phone update occurred.

The [original-window proof](../measurements/collaboration-original-window-2026-11-01.json)
now exercises the pinned Android SDK's actual context request through HRIS with
member-loading flags, original decoding and reopened encrypted local history.
It retains the initial unsupported-filter failure and the passing 77s corrected
run. This known-anchor snapshot does not yet establish application history paging.
The [common acquisition proof](../measurements/collaboration-history-acquisition-2026-11-01.json)
additionally verifies current application admission before and after the SDK
context read, followed by product-owned encrypted history storage and reopening.

## Mobile head discovery

[Validation and actual SDK evidence](../measurements/collaboration-history-head-2026-11-01.json).

Android's owned runtime uses the same admitted `/messages` endpoint with `dir=b`
and `limit=1` to select an opaque anchor when the common acquisition has none.
It borrows the application's public transport and reads the current SDK session;
the existing SDK remains the crypto/sync/delivery owner. Caller/parent cancellation
stops the request, while the public pool survives runtime disposal. The mobile
reader bounds responses to 256 KiB and rejects foreign/malformed heads. It neither
advances provider positions nor clears local history on an empty visible result.

The real SDK fixture now requests this head and checks that it equals the latest
encrypted reply before using the common history acquisition. Server trace assertions
require exactly one unpositioned `dir=b&limit=1` read, with HRIS device/conversation/
room admission before and after acquisition. This adds no production backend route,
provider store or migration. Ordinary Matrix token rejection remains a failed live
operation; HRIS control admission establishes company read restrictions.
