# Organization encrypted to-device transport

`PUT /_matrix/client/v3/sendToDevice/m.room.encrypted/{transactionId}` dispatches a
valid opaque group gateway token to the fixed group device use case. The existing
strict bounded HTTP parser, provider datasource and safe repository mapping are
reused. Native HRIS tokens, provider tokens, duplicate authorization and URL token
parameters cannot substitute for a group gateway credential. Company request shape
and authority remain separate.

## Captured frames and recipients

The pure group wrapper shares envelope validation with company transport while using
the separate group device selection policy. It captures original `HRIS_` and `HRISG_`
device IDs, transaction IDs and optional `org.matrix.msgid` without rewriting them.
Requests contain 1–100 users and 1–5 explicit devices each; each encoded body is at
most 65,536 characters and the total is at most 524,288. Olm types 0 and 1, canonical
base64 key/body encodings and existing transaction/message ID limits remain enforced.
Captured maps are detached from caller collections. Plaintext, wildcards and arbitrary
Matrix event types are not supported by this route.

Every requested alias and device must match a confirmed current recipient from the
[scoped projection](space-key-recipients.md). A missing, pending, unbound or ineligible
recipient rejects the entire request before provider I/O; the gateway never silently
drops a requested target. Short receiving-side native access or provider expiry does
not remove an otherwise eligible offline device. Native hard expiry, revocation and
purge do. Completed LEGACY identities retain their original device IDs and keys.

The sender also needs a coherent confirmed own-key publication. Missing own keys
require publication; an unacknowledged own reservation returns a retryable error.
The original publication and exact recipient selection are pinned for revalidation.

## Admission and provider operations

The fixed operation reuses sorted account guards before native/device/gateway/request
guards and one bounded recipient read per checkpoint. Current caller admission, exact
own publication and exact selected recipients are checked around each provider
outcome, including failures. A changed recipient set returns HTTP 503 for retry.
The original request budget and finite slot lease never extend. All provider calls
run outside SQL transactions; cancellation/interruption propagates and the exact
owned request is released in `finally` (or expires if the thread is interrupted).

Before sending, the gateway reads and detaches the sender's signed device identity
inside the provider operation, then verifies its original publication fingerprint.
It queries, detaches and verifies every selected recipient's signed identity through
the [key-exchange policy](space-recipient-key-exchange.md). Each envelope's sender and
recipient Curve25519 bytes must match these verified identities. Equivalent canonical
padded/unpadded base64 encodings identify the same key. Provider collection mutation
after verification cannot replace the retained identities or captured ciphertext.

Only then does the existing provider repository send the original transaction and
frames. Success is returned only after current admission is rechecked. The remote
provider may already have accepted a frame before a concurrent revocation; withholding
an obsolete success response does not undo that acceptance.

## Retry and acceptance boundary

No new HRIS receipt table, message ID, automatic retry, rekey or logout is introduced.
The original Matrix user/device/transaction namespace remains authoritative. Actual
Synapse transaction caching deduplicates repeat requests while its process lives,
but provider restart can enqueue the same opaque frame again. Preserve the same
ciphertext and message ID across an uncertain send. SDK duplicate handling and valid
Olm decryption remain required product acceptance; provider acceptance is not a
recipient-delivered or read receipt. An HRIS receipt alone would not make an external
provider call atomic across a crash.

Validation must cover bounded policy, cross-company/offline PostgreSQL admission,
whole-request rejection, both curve keys, signed identity pinning, mutable collections,
revocation on provider success and failure, cancellation, original LEGACY migration,
actual Synapse restart behavior, strict HTTP dispatch and the full worker DI graph.
See the [accepted gate results](../measurements/collaboration-space-to-device-2026-11-04.json) and source continuity.

The [group room-message route](space-room-messages.md) now retains exact room and
transaction identity under current receiving authority. Group history/sync/metadata/
push, full Mobile SDK adoption and native hard-parent recovery remain required. Group rollout stays disabled; this transport alone does not satisfy
chat product acceptance or justify installing an unchanged mobile build.
