# Native HTTP resolution of original room requests

`POST /api/v1/collaboration/spaces/{spaceId}/direct-conversations/{conversationId}/room/requests/fence`
resolves one original stable v2 setup request under current native authentication.
The required `Idempotency-Key` is the **original operation ID**, not a fresh recovery
operation. It must equal `operationId` in the body. Current native proof and client
build remain in authentication/headers; they are not added to the stable descriptor.

The body requires `operationId`, `deviceId` and `expectedDeviceVersion` (0–999999).
`expectedRequestAccountId` and `expectedRequestRevision` are either both absent/null
or both present, with revision0–999999. The complete stable descriptor includes the
space and canonical conversation from the path. Unknown or removed original devices
do not prevent the owning fence operation; they do not gain writer authority.

The no-store response always contains `accountId`, the complete seven-field
`original` descriptor, `state`, `receipt` and `fence`:

- `RECORDED`: the exact original setup receipt is present and `fence` is null.
- `FENCED`: `receipt` is null and `fence` contains its immutable `id` and microsecond
  `createdAt`. The same original descriptor recovers exactly the same evidence.

Exactly one result is present. Neither result acknowledges an SDK message or grants
room readiness, content access, provider delivery or a successor. The controller
maps input and the owning use-case result only. [Native transaction admission](native-room-request-fence.md)
retains original-operation serialization, current group/participant checks, immutable
evidence and rollback. Existing recorded/queued work is never relabelled as unexecuted.

The result survives real native-token refresh, client upgrade and replacement of the
original native parent. Current peer or native denial still takes precedence over
payload conflicts and retained evidence. A still-admitted stable writer gets
`collaboration_room_request_fenced`; a caller without provider identity/device
admission keeps the existing rejection instead. Legacy v1 operations stay independent.

HTTP input retains the API's existing1MiB request ceiling; there is no newly claimed
4KiB server request limit. The fixed typed response is measured below4KiB. Missing or
malformed required input, mismatched mutation header and descriptor bounds are rejected
before fence writes. The default-disabled API remains unavailable. OpenAPI lists the
native-only authentication, operation key, required fields, enum and exact result shapes.

## Validation and continuation

The corrected HTTP gate passes99 cases in202.31 seconds with8,637
unchanged source inputs, zero failures/errors/skips, format and architecture passed.
It covers actual native refresh, original-parent purge, exact replay, Recorded/Fenced
exclusion, current denial, no queue/fake receipt writes, bounds/no-store, OpenAPI and
both enabled/default-disabled API graphs. The existing recorded-operation test also
checks an admitted writer's explicit fenced rejection.

The first run had98 passes and one fixture expectation failure in179.991s: a writer
without participant provider identities correctly returned `collaboration_peer_unavailable`
before generic device admission. Only that test expectation/comment changed; the original
failure is retained. [Exact source hashes and reports](../measurements/collaboration-room-request-fence-http-2026-11-07.json).
The shared module DI did not change in this HTTP slice; the accepted dcdaf220 API/FULL
worker gate remains applicable.

Mobile typed transport, durable exact evidence and atomic successor linkage, Room
projection and product routing remain required. V138 is undeployed; organization
routing remains disabled, live schema116.1/phone53 remain, and phone latency is open.
