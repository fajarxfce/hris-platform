# Native organization room request and status

This is an unexposed application layer on V131/V132. RequestNativeCollaborationSpaceRoom
and ReadNativeCollaborationSpaceRoom coordinate repositories; no provider I/O, HTTP
route, shared DI or worker adapter is installed by this checkpoint.

Request admission requires current root native authentication, supported client,
current group, exact bound ACTIVE device and live provider metadata. Retained devices
must have completed their original-runtime transfer. Both canonical participants
are acquired again after shared account/native guards; current native/group admission
is repeated after peer acquisition and immediately before returning either outcome.
The optional original unsigned-pair advisory guard precedes account/native/device
guards and uses the same key as original conversation creation.

First intent selects an exact bound original room when available, preserving its
actual creator independently of the requester. Existing unbound original history
requires explicit recovery. Otherwise one immutable group intent is created. Queue,
operation receipt and change journal commit together. Native/provider deadlines do
not slide during repository waits. Failed results, cancellation and interruption
roll back all writes. Initial/replayed requests do not requeue active work.

Safe peer status and published room projections omit native/device/provider proof
and private lease tokens. Reads require both current participants and fresh native
admission after successful or failed aggregate acquisition. Owned retained metadata
alone does not authorize a read.

## Recovery limitation before rollout

The current V131 queue belongs to its first requester. Only that requester can
explicitly retry a terminal unbound request, using its observed revision and the
60-second cooldown. If that requester is offline, the peer cannot yet recover it.
This is not accepted product behavior and the group feature remains disabled.

The next implementation must let the other currently admitted participant recover
the same stable alias with its own native/provider proof. Do not expose or reuse
the first participant's private queue. A lost provider response may have created
the room already: SPACE binding must retain the observed canonical participant
creator, while LEGACY remains pinned to its exact original room and creator.
Immutable history, current admission around external outcomes, exact lease fences
and binding/success/journal atomicity must survive competing workers and restarts.

## Evidence

[Measurement](../measurements/collaboration-native-space-room-2026-11-04.json):
41 PostgreSQL cases pass in316.012s on8,393 unchanged inputs with format/architecture:
storage5, native5 and complete worker-publication31. The latter includes two new
retained-device/opposite-requester cases; it is not the worker application suite.
All103 domain cases passed in an earlier mixed run; all production and domain inputs
are identical to the final data gate. Three initial failing runs and their fixes
are recorded. There is no new API/full-worker/SDK acceptance in this checkpoint.
