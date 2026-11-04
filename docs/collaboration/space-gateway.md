# Organization gateway credentials

V126 introduces one retained credential head per organization device. Its table,
domain types, opaque token prefixes and authenticated replay context are distinct
from the existing company gateway. It does not create credentials during migration,
exchange a provider bearer or move a Mobile SDK store. Native HTTP issuance and
refresh dispatch are implemented separately below.
[Validation](../measurements/collaboration-space-gateway-storage-2026-11-04.json) passes 19 focused cases and 181 broader regression cases on identical inputs, plus 16 recovery-tool cases and format/architecture checks. Native issuance use cases are separately validated below. Current gateway request/provider/room authority and Mobile adoption remain unfinished.

## Native issuance use cases

`BootstrapNativeCollaborationSpaceGateway` and `RefreshNativeCollaborationSpaceGateway`
now require current native/account/group/client/MFA, canonical identity, exact active
bound device and live provider metadata. A retained device also requires its immutable
completed-transfer, original retired-key/credential and succeeded-queue provenance.
The operation budget is 30 seconds. Application defaults are 300-second access,
30-second encrypted replay and a 10-second rotation cooldown; the native hard deadline
and current authority remain mandatory. Bootstrap requires an operation UUID and
observed device/gateway versions; refresh reacquires the locked head after anonymous
lookup and can recover only the latest committed response. Neither decrypts provider
bearers or performs network I/O.

[Issuance evidence](../measurements/collaboration-space-gateway-issuance-2026-11-04.json)
records 14 focused and 154 broader domain/data cases, including 15 new acceptance
methods after adding actual retained-device refresh under the real cooldown. Receipt,
journal and credential writes roll back after failed final admission or malformed
returned tokens. Current and previous tokens require the same live checks; refreshing
an expired access token is allowed only inside the remaining native/provider/hard
lifetimes. The original completed runtime stays DRAINING and its keys/retired facts
remain unchanged.

## Native transport and recovery

`POST /api/v1/collaboration/spaces/{spaceId}/native-device/bootstrap` requires the
current native bearer, Android/iOS client headers and `Idempotency-Key`. Its separate
request contains `deviceId`, required `expectedDeviceVersion` (0–999999), and nullable
`expectedGatewayVersion` (0–999998; absent for the first issuance). A conflicting
operation or observed version cannot rotate the head. Responses are private/no-store
and credential-bearing objects redact their string representation.

Bootstrap returns original Matrix user/device IDs together with space, account,
group-device and native-parent IDs and the opaque session ID/version/token pair and
access/hard deadlines. The use case captures that envelope under the same transaction
guards on issuance and replay. The controller neither derives a retained Matrix device
ID from a new group UUID nor performs a follow-up identity lookup.

Native group device status adds nullable owned gateway metadata: ID, version, client
build, access/hard deadlines and revocation time. It exposes no hashes, replay payload
or provider credential. Metadata retains expired deadlines; current native/group
admission still gates the server read. A mismatched owner, parent, platform or invalid
chronology fails instead of appearing absent. Provisioning provenance remains required
for the current device status; imported pre-queue fixtures do not invent it.

After the bounded bootstrap replay expires, read status and persist a new operation
with its observed gateway version. The same old operation cannot silently issue a new
pair. An expired credential replay returns the specific
`collaboration_space_gateway_authentication_required` code (HTTP 401); it does not
itself revoke the native parent or authorize deletion of owned local history. Status
must reacquire current native/group admission before a new bootstrap operation.
Standard `POST /_matrix/client/v3/refresh` dispatches a correctly shaped
`hris_mxg_r_` credential to the group use case. Legacy refresh remains separate. Both
responses retain Matrix token field names and no-store behavior. The existing strict
2048-byte parser rejects duplicate/extra fields, trailing JSON and excessive nesting,
with bounded reading and cancellation propagation.

The owning configuration binds group storage, bootstrap, refresh and status. Group
availability remains disabled by default. This does not grant group sync/send, provider
renewal or room access, and no Mobile product caller is enabled. Current transport
validation is recorded separately from the earlier storage and issuance gates.

[Transport evidence](../measurements/collaboration-space-gateway-http-2026-11-04.json)
records 93 domain/33 actual data cases, 92 API/OpenAPI cases (158.550s) and full
worker127 (356.150s; 126 active and one existing retention skip), with identical
final inputs and format/architecture checks. Preliminary test-only corrections are
retained. Lost-response and revoked-access cases exercise actual elapsed replay and
cooldown under PostgreSQL's wall-clock publication guard.

Before enabling group use, connect the [bounded group request storage](space-request-lifecycle.md)
to current request admission and provider renewal without reopening original company runtime slots. Expired group replay cleanup has
a typed storage port but still needs its own use case and worker scheduling; the
existing replay job handles only company gateway heads.

## Ownership and authority

The immutable coordinates are space, account, group device, original native parent,
platform, gateway ID, creation time and hard deadline. The device foreign key keeps
the original native provenance after the live native session and binding are purged.
Company IDs never substitute for space IDs in this contract.

A root account transaction can read its retained metadata after expiry or loss of
chat eligibility. An anonymous root transaction can identify one head using the
hash of a correctly shaped `hris_mxg_a_` or `hris_mxg_r_` token. The lookup uses a
separate transaction-local setting and confers no HR company access. Identification
alone is insufficient for request admission. Issuance rechecks current account, native session, organization policy, device and
provider metadata before replay publication. Gateway request exchange must additionally
implement its own current admission and room authority.

Writes require the exact active group device and bound, current native parent;
the account security version must still match. New credentials cannot outlive the
parent. Callers own the transaction and acquire guards in account, native session,
device, gateway order. Provider I/O must not occur while these database guards are
held. The raw datasource does not make application admission decisions.

## Rotation and recoverable responses

Initial issuance uses version zero and records its bootstrap operation UUID.
Rotations compare the prior version and refresh hash, advance exactly once, and
retain the hard deadline. Refresh rotation retains the client build and the exact
previous refresh hash. A bootstrap rotation records its new operation UUID. The
database reserves version 1,000,000 for terminal retirement.

Only hashes of access and refresh tokens are stored as searchable columns. A
bounded encrypted response contains the pair so a lost response can be recovered
without creating another generation. Its authenticated context includes all scope
coordinates, version, platform/build, four session timestamps, both hashes, replay
kind, operation UUID or previous refresh hash, and replay deadline. The separate
`hris:matrix-space-gateway:v1:` purpose prevents replay across the company and group
formats. Timestamps use canonical microseconds and the bounded context is SHA-256
hashed before passing to the identity encryption adapter.

The database allows at most 31 days of hard lifetime, 15 minutes of access lifetime,
60 seconds of encrypted replay and 4,096 ciphertext characters. These are storage
ceilings; application policy may choose shorter lifetimes. The repository validates
raw coordinates and phase invariants inside the same failure boundary as acquisition.
It verifies exact stored metadata and encrypted replay after a write before returning
tokens. Failed results must roll back through `TransactionRunner`; cancellation and
interruption propagate without an implicit retry.

Reading retained metadata or decrypting a stored response is not evidence that its
deadline is current. Issuance checks response expiry and all current admission at its final publication
point, including recovery of a prior operation.

## Retirement and recovery

Terminal retirement requires the matching revoked group device, preserves the last
issued facts, advances the revision and clears token hashes and replay material.
It remains possible after offboarding or native-session purge. Rows cannot be deleted
or revived. The bounded worker cleanup selects at most 100 expired replay rows with
`SKIP LOCKED`; it removes only encrypted replay material and retains the expired
deadline and every logical credential fact.

The recovery tool recognizes schema 126 and the distinct group authenticated purpose,
alongside the unchanged legacy envelope formats. Unknown future schemas are rejected.
Recovery acceptance must exercise the generated SQL projection against actual
encrypted bootstrap and refresh rows, key rotation and restored PostgreSQL data.

The next increment must implement current group gateway request admission, provider renewal and room authority,
then join these to the durable Mobile handover. An accepted storage adapter alone
does not satisfy cross-company chat or authorize freezing the old client runtime.
