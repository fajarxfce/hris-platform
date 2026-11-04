# Organization encrypted room messages

The fixed `PUT /_matrix/client/v3/rooms/{roomId}/send/m.room.encrypted/{transactionId}`
route dispatches an opaque group gateway credential to the group use case. Existing
company authority, strict HTTP parsing, bounded ciphertext validation and the provider
message repository remain separate or shared only where their contracts are scope-free.
Native or provider tokens, duplicate authorization and URL tokens cannot substitute
for group gateway authority. No plaintext or arbitrary event type is accepted.

## Exact owned room and current receiving authority

The new typed `boundRoom` lookup reads the existing immutable V131 intent and binding
under participant root RLS. It selects the exact space, account, provider server and
room ID, with a two-row bound even though the database already enforces uniqueness.
The repository maps raw rows and failures; the pure policy validates the original
account pair, Matrix users, creator, publisher, revision, lease and timestamps. SPACE
and LEGACY metadata retain their distinct meanings. A missing room is not found;
malformed or inconsistent owned metadata fails closed. No new migration or broad
public view is needed.

An owned lookup discovers account guard IDs only. The admitted runner still checks
the current caller before returning either lookup outcome, and it never claims a
provider request slot for a failed lookup. Sorted account guards precede native,
device, gateway and request guards. The exact room is reread under those guards,
and its caller Matrix user must equal the admitted device's original identity.

Sending requires a confirmed current peer selected through the V135 projection and
a confirmed own-key publication. Short receiving-side native access or provider token
expiry permits offline delivery while hard authority remains current. Missing peer
devices, unconfirmed publication, offboarding, hard expiry and revocation do not grant
new-send authority. Owned room metadata can survive peer offboarding; future history
reads must not confuse that retention with permission to send a new message.

## Original ciphertext and bounded execution

The gateway pins the complete owned room, own publication and selected receiving
devices around every external outcome. It reads, detaches and verifies the signed
sender device keys inside the provider operation. Optional `device_id` and `sender_key`
must match that original device and verified Curve25519 identity. SDK payloads that
omit these optional fields remain valid. It never derives a replacement device ID
from a group UUID.

The existing provider repository receives the original room ID, transaction ID,
ciphertext and Megolm session ID. All provider I/O runs outside SQL transactions.
Current admission and the exact pinned observations are checked after success and
failure; changed recipients return a retryable failure rather than an obsolete event
ID. The original request budget and finite slot lease do not extend. Cancellation and
interruption propagate, and cleanup releases only the exact owned request.

A remote provider may accept ciphertext just before access changes. Withholding the
late result cannot undo that acceptance. The returned event ID means server acceptance;
it is not a delivered or read receipt. No automatic retry, HRIS message receipt table,
rekey, logout or SDK store replacement is introduced.

## Validation and remaining work

See the [accepted gate results](../measurements/collaboration-space-room-messages-2026-11-04.json) and recorded source continuity.
Actual Synapse returned the same room event ID across retry and provider restart;
the peer history contained one original ciphertext/session/sender event.

Required evidence includes pure metadata policy, ordinary PostgreSQL RLS and current
admission, offline peers, exact ciphertext retry, both remote outcomes, cancellation,
completed LEGACY devices in old and new rooms, strict actual HTTP dispatch, actual
Synapse room-event retry through provider restart and the full worker DI graph.
Provider transaction behavior must be measured separately from the process-local
to-device transaction cache. Opaque Megolm fixtures prove transport, not SDK encryption,
decryption or device-change session rotation.

The [group history routes](space-room-history-access.md) now retain owned reads under
current admission. Group sync, metadata, push, native hard-parent recovery and full
Mobile SDK adoption remain required. Original store, passphrase, keys, history and uncertain
sends must survive that adoption. Group rollout remains disabled and chat product
acceptance stays open; this backend increment does not justify reinstalling an
unchanged mobile build.
