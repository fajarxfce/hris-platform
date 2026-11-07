# Native organization room request and status

GET and POST `/api/v1/collaboration/spaces/{spaceId}/direct-conversations/{conversationId}/room`
use root native authentication, the supported client headers and current organization
membership. POST requires `Idempotency-Key`, `deviceId` and `expectedDeviceVersion`.
The group feature remains disabled for product rollout while its provider worker,
recipient access and Mobile SDK integration are unfinished.

Request admission checks current native/security/group/client state, the exact bound
ACTIVE device and live owned provider metadata. Retained devices require completed
original-runtime transfer. Group-room and optional original unsigned-pair guards
precede signed-order shared account/native/device guards. Both canonical participants
and current native/group admission are re-read around dependent acquisition and before
returning. A repository wait cannot extend the captured request deadline.

An initial request creates one immutable intent and private queue entry, or observes
existing work without requeueing it. The intent chooses an exact bound original room
when present, independently preserving its actual creator. Existing unbound original
history requires explicit original recovery; a replacement group room is not created.
Intent, private queue, idempotency receipt and change journal commit together.

For terminal recovery, POST supplies **both** `expectedRequestAccountId` and
`expectedRequestRevision` from the public status. The pair must still identify the
current terminal attempt and its sixty-second retry cooldown must have elapsed.
Either current participant may then continue using its own native/provider proof.
The caller's own private head starts at zero or increments its own previous revision;
it never rewrites the other participant's private proof. Active work cannot be stolen.
Completed bound rooms cannot be retried. Old operation replay returns its original
receipt after fresh admission, even if another participant now owns the public head.
That replay also requires the original native parent/version and actual platform/build
fingerprint. An unchanged JSON body is insufficient after rotation or upgrade; the
operation returns `operation_payload_mismatch` and retains its original uncertainty.
[Original receipt recovery](native-room-receipt-recovery.md) records the actual
database characterization and the remaining explicit recovery requirement.

A mutation receipt identifies the conversation and the observed/created request
revision; that number is not a global cross-participant version. Read status for the
current **account plus revision**, state and published room. Clients must not order
attempts from different accounts by the revision number alone.

The safe status includes operational timing/outcome and public request owner. A
published room exposes room ID, server, origin and publication time. Neither exposes
native authentication, device/provider proof or private lease tokens. The successful
binding must match the status publisher/revision; LEGACY keeps its exact original
creator and room. Reads require both current participants and fresh native admission
after successful or failed aggregate acquisition. Retained metadata alone grants no
HTTP or Matrix content access.

The setup endpoints perform no external provider I/O and grant no content capability.
The bounded worker must still resolve/create/join the SAME alias or original room,
verify actual canonical creator/state under current admission, then atomically publish
binding, request success and journal. Provider results after lost responses or expired
authority must never create replacement history. No SDK sync cursor/key material is
changed by these request/status endpoints.

[V133 evidence](../measurements/collaboration-space-room-peer-recovery-2026-11-04.json):
all103 database plus96 actual API cases PASS1120.114s, full128 worker
cases PASS375.998s with one existing opt-in skip, and all31 retained
publication cases PASS265.182s. Quality checks pass. Prior103
domain and typed5/native5 passing evidence carries only with the recorded source
continuity. Recovery19 passes, MAX133/unknown134. Initial fixture/compilation failures
are resolved and recorded; all test sessions closed. No room worker/SDK rollout claim.
