# Organization room storage

V131 separates a stable room candidate, its bounded provisioning request and the
verified immutable room binding. Conversation creator, first requester and actual
Matrix room creator are distinct. A LEGACY candidate pins the original company,
conversation, room, binding time and actual creator; a SPACE candidate uses the
requester's canonical user. Both exact current canonical participants are required.
An existing original conversation, even without a binding, forbids silent SPACE
replacement. A shared fence against later original creation remains required before
product rollout.

The queue retains its first requester and captures the original native/session
security proof, client, device version and provider generation. Requests last at
most five minutes, attempts at most eight, individual leases at most sixty seconds,
worker claims at most eight and active leases at most sixteen. Explicit terminal
retry advances the revision and may use that same requester's new current device
and native proof; it never changes Matrix creator or loses the previous outcome.
No repository starts a transaction, retries automatically or grants provider access.

Runtime owned reads survive parent cleanup. Only the requester and worker can read
the full request. The participant status view exposes revision, state, attempts,
timestamps and outcome, using a restricted non-login owner with column privileges.
It exposes no native, device or provider proof. Current HTTP admission is a separate
application obligation even when retained metadata is readable.

Publishing requires the exact current request revision, lease, native/security,
device/provider generation and canonical participants. Binding insertion and queue
success must commit together: success requires a matching binding, and a deferred
constraint rejects a binding without its exact successful request at commit. The
binding is immutable. LEGACY publication must retain the exact original room.

Typed repositories validate IDs, finite microsecond timestamps, complete state and
outcome tuples, canonical identities and ownership. Create, enqueue, explicit retry
and publication read back the exact written result inside the caller's transaction.
Bad/missing readback returns a failed Result so TransactionRunner rolls back;
cancellation/interruption propagates. Claim bounds are checked before I/O and claimed
rows must have unique room/token coordinates and the exact requested worker owner.

[Validation evidence](../measurements/collaboration-space-room-storage-2026-11-04.json)
records all 93 database cases passing in 797.756s and all five actual PostgreSQL
repository cases passing in 106.634s, with format/architecture checks. The 8,376
database inputs remain identical in the final 8,385-input typed run; nine typed
files were added afterward. Recovery passes 19 cases and accepts schema131 while
rejecting132. Initial storage/test compilation and typed smart-cast failures are
retained in the evidence; all are resolved. No test session remains running.
Storage alone does not enable room endpoints, provider authority, SDK adoption,
recipient keys, encrypted content, sync or push. No shared DI changed in this stage.
