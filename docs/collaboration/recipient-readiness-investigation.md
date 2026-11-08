# Recipient readiness and repeated delivery

Passive capture of the authorized phone on release 69 matched the actual rejected
room to an active HRIS peer with no collaboration device or public-key publication.
The peer account, membership and chat permission are active. This is not the Bima
sample, whose stopped device still has one eligible confirmed key and a hard-live
parent; short access-token expiry does not remove it.

The server returns valid device/room coordinates from `/transport` without stating
whether the recipient has a confirmed eligible device. The later encrypted room
send rejects the unprepared peer. The raw SDK retains ApiForbidden; the application's
generic API retry resets that original and the worker collapses the outcome into
`client.collaboration_delivery_pending`, requesting another timed pass. Original
messages are not duplicated, but the same unsatisfied operation is attempted again.

A 180.476-second passive phone window observed four transport200 responses and two
encrypted-send403 responses. Server closed logs identify `room_send` and
`collaboration_recipient_unavailable`. The user's inspector count167 is user-reported; its interval was not independently
established and is not equivalent to this capture window. Raw logs, room/account IDs
and query inputs remain private. No phone UI was driven, no message sent and no
account/device/key was modified. [Evidence](../measurements/recipient-readiness-investigation-2026-11-08.json).

The server source is accepted: transport metadata adds explicit recipient readiness
using the same domain policy and existing eligible-device projection as the final
gateway check. Current account/company admission and provider verification remain
separate. Mobile will classify an unready recipient as blocked pending work rather
than a transient network retry, preserving the original command. Independent rooms
must still progress; expiry, revocation, failed reads and late changes must remain
covered. A later relevant trigger may recheck readiness; no timer may replay the
unready room indefinitely. Backend gate passes226 active cases with zero failures/errors and one existing
opt-in skip; actual phone no-loop acceptance is pending.
[Accepted source evidence](../measurements/collaboration-recipient-readiness-2026-11-08.json).

Release70's timeline-only preparation is held until this root fix is validated.
The unrelated lazy-timeline full gate may finish, but it does not fix this loop.
