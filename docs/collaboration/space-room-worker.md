# Organization room preparation worker

The native room request commits a participant-owned queue head before returning.
`ClaimCollaborationSpaceRooms`, `AdvanceCollaborationSpaceRoom` and
`SettleCollaborationSpaceRoomFailure` drive that head without an HR company job.
The claim statement completes before application account/native/device guards.
There is no use-case-to-use-case call or provider work inside a SQL transaction.

## Current authority and provider work

Each attempt retains the requesting participant's own native security proof,
client, device version and provider generation. It locks the conversation, optional
original pair, canonical participant accounts, native parent, device and queue head
in that order. Both participants must remain eligible. Completed LEGACY provenance,
canonical provider identity and the exact claimed attempt must still match.

Room control reserves one `ROOM_PREPARATION` slot among ordinary slots 1–7. Its
deadline is the minimum of the original 30-second attempt budget, queue/lease,
native admission and provider expiry. Slot zero remains available for renewal.
The use case decrypts only the observed own provider generation. It rechecks current
authority and exact slot ownership before and after every provider outcome,
including failures; no call extends the original deadline.

For SPACE, resolve only the immutable intent alias. If absent, create as the current
caller and resolve that same alias once, even after a lost creation response.
A returned creation ID must match the resolved alias. Join with the caller's own
token, observe strict encrypted room state and retain the actual canonical creator.
Only that creator may fill missing initial settings or invitation, followed by a
fresh complete observation. Mismatched rooms and aliases are never replaced.

For LEGACY, join and observe only the exact original room. The original marker,
creator, canonical users and strict room settings must match. There is no new alias,
room creation or repair, and neither original device's SDK namespace or keys changes.

## Publication, retry and cancellation

Binding, exact successful queue head and change journal commit in one transaction,
followed by final admission and exact successful-head/binding readback before commit.
An old lease cannot publish, settle or remove a successor. The observed Matrix
creator and current publishing participant are separate coordinates.

Failure settlement changes only metadata under the exact current lease. Safe failure
enums and bounded backoff preserve the original queue lifetime and eight-attempt
limit. Revocation or malformed room state is terminal. Settlement grants no content
or provider authority. Provider cancellation/interruption propagates; exact finally
release preserves the original exception. An interrupted cleanup leaves a finite
lease for the existing cleanup worker, without logout, renewal or key deletion.

## Process lifecycle

`HRIS_COLLABORATION_SPACE_ROOM_PROVISIONING_ENABLED` defaults to false and requires
the group, collaboration and Matrix flags plus an enabled worker role. Shared module
DI supplies the typed provider adapter, including a disabled-provider implementation.
Actual application context tests cover each missing/disabled capability.

The process defaults to two tasks, permits configured capacity 1–8, and has no waiting
executor queue. Database claims remain globally limited to sixteen running attempts.
A semaphore covers reserved submissions while the executor's physical active count
prevents an interrupted task still cleaning up from freeing effective capacity.
Separate bounded control timers poll and cancel at the earlier lease/request deadline
or 45-second process budget. Duplicate conversations, including different participants,
cannot dispatch together. Different conversations on one device are allowed and still
share its seven provider slots. Shutdown cancels polling and owned tasks/timers; the
same worker instance cannot restart.

## Validation and remaining integration

See [the worker evidence](../measurements/collaboration-space-room-worker-2026-11-04.json)
for actual PostgreSQL, retained-device, Synapse restart, shared API and full worker
results. The runtime test starts native device workers for fictional employees in
two distinct companies and lets the actual scheduled room worker publish one encrypted
room. Both native views and replays retain that room and original provider identities.
These checks do not establish an SDK encrypted-message round trip.

The [receiving projection](space-key-recipients.md) now supplies bounded public-key
candidates. Key query/claim endpoints, room content/sync/push and full Mobile adoption
remain required.
Preserve offline receiving and the original SDK store, passphrase, keys, history and
uncertain sends, including native hard-parent replacement. Group rollout remains
disabled and all communications acceptance groups remain open.
