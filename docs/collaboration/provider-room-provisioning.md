# Matrix room creation and recovery evidence

The two actual-provider scenarios in
[recorded evidence](../measurements/collaboration-room-interop-2026-10-31.json)
pass against pinned Synapse 1.164.1, using explicit room version 12. These tests
establish provider control behavior. They do not accept HRIS room publication,
SDK E2EE delivery, groups or context-room membership.

The original control fixture below uses joined history. The current
[direct-room adapter](matrix-room-adapter.md) uses **invited** history for first-message
delivery to an already enrolled offline invitee; do not infer client encryption
behavior from the earlier control-only fixture.

## First login and canonical identity

A private, non-federated room can invite a deterministic local HRIS Matrix user
before that user has logged in or registered a device. Its later JWT login can
join the retained invitation. Repeating join succeeds. No temporary peer device
or server-generated participant encryption key is needed for this invitation.

Use a deterministic company/conversation alias to recover a lost create response.
A second createRoom returns HTTP 400 `M_ROOM_IN_USE`; alias resolution returns
the original room, including after a provider restart. Treat this as a lookup
mechanism, not an atomic transaction or a content-access capability.

The creation event can retain an immutable HRIS marker containing company,
conversation and kind. The tested room explicitly disables federation and sets
Megolm encryption, invite-only joining, `joined` history visibility and forbidden
guest access. Its peer has power level zero and cannot invite another account or
change encryption. Version 12 gives the creator special authority; HRIS must also
gate the creator's state/member operations. Provider power levels alone do not
implement company or resource-context policy.

## Partial creation is observable

A deliberately malformed initial power-level state produces HTTP 400 after the
alias and creation/member events have already persisted. The room lacks encryption
state. Retrying createRoom returns `M_ROOM_IN_USE` and leaves the incomplete room.
A successful alias lookup therefore cannot cause publication to clients.

The correct creator can read the immutable marker and repair this owned partial
room by setting expected power levels, encryption, join rule, history visibility
and guest policy. The experiment verifies every resulting state value. It does
not downgrade or repair an unrelated room. The implementation must reject a wrong
creator, marker, additional creator or incompatible existing encryption; it must
also reconcile and verify the exact admitted member set.

Inspection of the same pinned provider's `RoomCreationHandler.create_room` shows
alias association before `_send_events_for_new_room`, which separately persists
the creation/join before the remaining initial state. The observed failure and
repair are exercised against the running provider, not inferred only from source.

## Required integration

Persist HRIS provisioning intent and fences before provider I/O, release SQL
locks for every network exchange, and recheck current parent/company/member/device
admission and ownership before publishing the room binding. A lease timeout or
lost response is uncertainty, not authorization to publish an arbitrary alias.
Use bounded retry and retain one authoritative binding per conversation. Verify
all required initial state and intended participants before making content routes
available. Retain explicit non-retryable mismatch outcomes for operator review.

Public clients continue to receive only HRIS gateway credentials. No state,
room, sync, key or admin route becomes available merely because this proof passes.
