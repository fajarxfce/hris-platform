# Current organization direct transport

One native GET at
`/api/v1/collaboration/spaces/{spaceId}/direct-conversations/{conversationId}/transport`
returns the already published immutable pair/room, this native parent's active device,
its public Matrix identifiers, and explicit recipient readiness. It does not provision,
join, query Matrix, decrypt credentials, select an identity or retry setup.

The owning domain use case acquires the room guard, exact immutable conversation,
sorted account guards and native-parent guard. It validates current native/client/
space and both participants before and after acquisition, including failed reads.
Malformed pair/device/gateway ownership cannot become a transport observation. The
observation budget is30 seconds, inside the existing transaction/statement/lock limits.
HTTP is a native-session adapter; shared DI constructs the use case. The repository
and datasource boundaries remain unchanged.

The ten public response fields are `spaceId`, `accountId`, `nativeSessionId`,
`nativeSessionVersion`, `conversation`, `device`, `matrixUserId`, `matrixDeviceId`,
`room` and `recipientReady`. The published room keeps the exact original company/
conversation reference when LEGACY. Device origin and selected-user origin remain
distinct: a new crypto device can retain a selected original Matrix user.

Receiving readiness uses the existing V135 bounded public-key projection. A confirmed
receiver remains eligible after short access/provider-token expiry while the hard
receiving deadline, revocation, security and membership guards remain enforced.
An empty or merely reserved receiving-device set is explicitly false. This endpoint
creates no keys, receiving records, leases or provider state. Expired/foreign/duplicate
public facts are rejected, not converted into readiness.

The existing native room read shares only a pure room-view mapping policy. Both use
cases coordinate repositories directly; neither invokes the other. Current transport
metadata is not retained sending authority. Mobile still needs an admitted logical
runtime, exact original physical writer, complete original command matching and
receipt recovery before a bounded resume. Readiness changes need their own group wake
owner; company readiness controls remain excluded from group sync.

## Accepted source validation

The twelve-file increment follows reconciliation fa90ddcc. [Accepted evidence](../measurements/collaboration-space-direct-transport-2026-11-09.json)
contains452 cases:192 domain,38 actual SQL,72 API/OpenAPI and150 complete worker cases.
There are zero failures/errors and one existing elapsed-retention opt-in skip.
The final targeted43271 run passes148.630s with nine fresh SQL/HTTP cases and unchanged
8,693 source inputs. Format, architecture and image-pin checks pass.

Full38552 initially ran342 domain/worker cases but SQL/API compilation failed on a
misnamed override parameter and Native.copy assumption. Focused34852 then ran302
cases with eight failures: the SQL fixture predates provisioning heads, and the new
HTTP query used room_id instead of matrix_room_id. The SQL correction uses actual
enqueue/worker claim/finish and retains explicit missing-head rejection. No production
policy, SQL schema, DI or worker input changed to resolve these fixture failures.

Fresh final reports replace those failed cases. Unchanged domain,30 room, actual
retained LEGACY publication/direct transport and70 OpenAPI cases are retained from
focused-2; the full worker result is retained from full-1. The audit compares all
source hashes and archived XML: only three SQL/API fixture files changed after the
full worker gate. No failed/cached case is counted as new success.

Coverage includes short receiver access expiry, exact old physical/device identities,
immutable pair/room metadata, current denial after successful or failed acquisition,
offboarding, wrong credentials, cancellation/interruption, transaction cleanup and
native HTTP no-store/public response shape. All earlier failures remain recorded.

Live e558a686/schema116.4 and organization routing remain unchanged. Mobile transport,
original queue ownership, readiness wake and product adoption are separate next work.
No phone, APNs, calling or meeting acceptance follows from this contract.
