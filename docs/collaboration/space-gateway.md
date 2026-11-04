# Organization gateway credentials

V126 introduces one retained credential head per organization device. Its table,
domain types, opaque token prefixes and authenticated replay context are distinct
from the existing company gateway. It does not create credentials during migration,
enable an HTTP route, exchange a provider bearer or move a Mobile SDK store.
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

These use cases have no HTTP or DI binding yet. Before transport, bootstrap must return
original Matrix user/device IDs from the same guarded observation as its tokens.
A retained Matrix device ID cannot be derived from the new group UUID. Native group
status also needs safe gateway revision/deadline metadata so a lost initial response
can recover after the encrypted replay expires. Then validate strict transport/OpenAPI
and both API/full-worker contexts before a Mobile caller is enabled.

## Ownership and authority

The immutable coordinates are space, account, group device, original native parent,
platform, gateway ID, creation time and hard deadline. The device foreign key keeps
the original native provenance after the live native session and binding are purged.
Company IDs never substitute for space IDs in this contract.

A root account transaction can read its retained metadata after expiry or loss of
chat eligibility. An anonymous root transaction can identify one head using the
hash of a correctly shaped `hris_mxg_a_` or `hris_mxg_r_` token. The lookup uses a
separate transaction-local setting and confers no HR company access. Identification
alone is insufficient for request admission. The future use cases must acquire and
recheck current account, native session, organization policy, device, provider and
room authority before exchange or replay publication.

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
deadline is current. The future use case must check response expiry and all current
admission at its final publication point, including recovery of a prior operation.

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

The next increment must implement coherent native transport, current group gateway request admission and room authority,
then join these to the durable Mobile handover. An accepted storage adapter alone
does not satisfy cross-company chat or authorize freezing the old client runtime.
