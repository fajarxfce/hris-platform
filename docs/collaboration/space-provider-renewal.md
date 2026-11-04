# Native organization provider renewal

A long-idle client can have an expired Matrix provider head while its HRIS native
session and owned local history remain recoverable. After renewing the native HRIS
session as needed, call `POST /api/v1/collaboration/spaces/{spaceId}/native-device/provider/renew`
with native authorization, client headers, `Idempotency-Key`, `deviceId` and the
observed `expectedDeviceVersion`. Then acquire current group gateway credentials.
The group feature remains disabled until room authority and Mobile adoption are ready.

The response contains only `deviceId` and `providerGeneration`, with `private, no-store`.
Provider generation is distinct from the device version. No provider token, request
lease token or private key enters the response. Current group bootstrap still requires
a live provider head; ordinary credential expiry does not authorize deleting SDK keys,
local history, drafts or pending commands.

## Current admission and identity

The command owns operation, account, native-parent and device guards in that order.
It requires current account/security, group eligibility, client policy, MFA and an
exact ACTIVE bound device under the immutable canonical Matrix identity. Retained
LEGACY devices additionally require the exact immutable completed transfer, original
retirement and successful provisioning provenance. Original native provenance cannot
be rebound by this command. Only this explicit renewal permits an expired provider
head; a missing, cleared, foreign or incoherent head remains denied.

A provider with more than60 seconds remaining records a no-op receipt without network
I/O. Exact receipts can recover the recorded generation while it remains live and all
current admission still holds. `collaboration_space_provider_renewal_required` means
the receipt's provider head expired: reacquire status and submit a fresh operation.
A later provider generation or changed device gives `collaboration_space_provider_changed`.

## Bounded candidate publication

A required renewal claims the reserved V127 slot0 for at most the original30-second
request budget, clipped to current admission. The seven ordinary gateway slots remain
available. A busy slot returns `collaboration_space_provider_busy` and a two-second
retry hint; the server performs no implicit retry. Each invocation performs at most
one login and, for a retained device, one signed key readback.

The prepare transaction commits before provider I/O. Login uses the canonical user's
legacy company only when selected, and the retained device's original identifier only
when the device is LEGACY. The command never derives a retained Matrix device identity
from its group UUID. Signed readback must match the recorded retained fingerprint.
This proves provider publication continuity; it does not prove local private-key
possession or replace Mobile handover preflight.

Before I/O, after both successful and failed provider results, and before publication,
the command reacquires current admission and its exact original request lease. A
replaced lease, native refresh during the operation, offboarding or late callback
cannot publish. Provider candidates must match the original identity, contain a
bounded printable token and retain more than60 seconds but at most10 minutes lifetime.

Encrypted rotation uses the observed generation and preserves original creation time.
Rotation, receipt and safe journal/outbox commit atomically. Final provider readback,
current admission, request ownership and candidate lifetime are checked after writes;
failed results and cancellation roll back the transaction. Exact finally-release
cannot remove a successor. An interrupted thread may leave only the finite lease for
worker cleanup. Losing Matrix candidates expire without logout, because Matrix logout
would also delete retained E2EE keys.

## Integration and validation

The module binds request storage and renewal, and native HTTP exposes the versioned
command. The existing owned gateway cleanup timer also invokes group maintenance.
One group transaction clears at most100 expired replay caches and100 expired request
leases, using the existing ordered SKIP LOCKED ports. Failure or interruption rolls
back both group batches. The earlier company cache pass remains independent; there
is no new timer, unbounded drain loop or per-session work queue. Stop cancels in-flight
work and shuts down the shared owner. Cleanup preserves retained identities and
current access/refresh hashes. It removes replay kind/operation/previous-refresh
metadata together with ciphertext while retaining the replay deadline.
Actual PostgreSQL tests cover expiry, competing candidates, no-op/replay, parent
refresh, late results, replaced leases, rollback and cancellation. Retained-device
tests additionally exercise signed keys and an isolated real Synapse restart. Exact
accepted runs and any preliminary failures belong in the accompanying measurement.
API and full worker application contexts are required for the new shared DI graph.

Fixed group [self access](space-gateway-access.md) now checks current provider and
gateway/request ownership without calling this renewal use case. Group room/content
exchange, member/key authority and Mobile orchestration remain separate work. This command alone enables no product group runtime or rollout.

[Accepted measurement](../measurements/collaboration-space-provider-renewal-2026-11-04.json)
records exact source/report hashes, focused and broader outcomes, actual retained
provider restart evidence, final HTTP/full-worker gates and preliminary corrections.
