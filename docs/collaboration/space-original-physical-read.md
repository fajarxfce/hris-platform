# Native original-room metadata read

A retained Matrix original can receive a room hint before its Company conversation has a
published Space binding. `POST /api/v1/collaboration/spaces/{spaceId}/legacy-rooms/read`
accepts exactly `companyId` and `matrixRoomId`, then returns the existing original Company
conversation and immutable room metadata. The Company coordinate comes from the original
registration. The provider server comes from configuration; room locators grant no authority.

`ReadNativeCollaborationSpaceDirect.legacyRoom` owns the transaction, current Space/native
admission and the existing account/session locks. Its repository uses the bounded original
room lookup. Acquired space, Company, participant, server and room coordinates must match.
Native admission surrounds successful, absent and failed acquisition; current denial wins.
A result acquired outside the same 30-second operation budget, including backward clock
movement, is rejected. Cancellation/interruption propagate. Existing logical reads retain
their contract and do not inherit this operation-specific deadline.

This read creates no publication, Space alias, provider request, device, job, receipt or
wake. Owned original metadata remains distinct from current peer/provider authority. An
unpublished Company conversation must retain its original typed namespace on Mobile;
this endpoint does not authorize sending, claiming keys or projecting it as a Space room.

Validation accepts 491 passed cases and the one unchanged explicitly opt-in elapsed-hour
report-export skip. The 205 domain, 61 data, 75 HTTP and 151 scheduled worker cases cover
the real API/worker DI graph. Eight new data cases cover scope, configured server, current
denial, missing/failed/late acquisition, budgets and cancellation. One new HTTP case uses
both participants, a cross-company original, outsider/invalid/native-revoked requests and
unchanged publication/device/job counts, with the provider endpoint unavailable. The first
run exposed a shared fixture room ID; the corrected isolated ID passed all 75 HTTP cases.
All previously selected case IDs remain. No schema or live deployment changed.

[Exact evidence](../measurements/collaboration-space-original-physical-read-2026-11-11.json).
The Mobile metadata consumer, typed original routing, inventory, key recovery and raw batch
activation remain required before native end-to-end acceptance.
