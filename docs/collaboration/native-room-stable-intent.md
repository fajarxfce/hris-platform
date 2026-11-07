# Stable native room request intent

Room request idempotency now has an explicit internal stable format. Its operation
namespace is `collaboration.space_room_setup.v2`; the fingerprint contains exactly:
space, conversation, device, expected device version, expected request revision or
`initial`, and expected request account or `initial`. Actor/scope/operation UUID remain
part of the existing operation repository key. Actual native parent/version and client
build still determine current admission and are retained in the original queued request;
they do not rewrite this business fingerprint after token rotation or an upgrade.

The existing `collaboration.space_room_setup` namespace, field order and native-proof
fingerprint are unchanged. Existing native HTTP requests still use that original format.
The stable format is explicit; neither protocol reads the other's receipt, including
when operation UUIDs coincide. Both may retain their own receipt for one existing room
without duplicating its provider intent or provisioning queue.

`CollaborationSpaceRoomRequestIntent` is the new immutable request descriptor. It differs
from `CollaborationSpaceRoomIntent`, which already denotes a provider room candidate.
A sealed receipt descriptor and typed observation let the existing owning reader share
its current native/group/participant admission, operation/room/account/parent locks,
deadline, failure handling and cancellation across both protocols. The repository,
provider and DI constructors do not change. Legacy HTTP output remains concretely typed.

A null receipt is still uncertain. The original may later commit, and a subsequent read
may recover it. There is no automatic new command, provider grant, old-parent revival,
acknowledgement or handoff fence. Current denial overrides a changed-payload lookup.

[Accepted source and validation](../measurements/collaboration-space-room-stable-intent-2026-11-07.json):
181 fresh tests in213.656s on8,622 unchanged inputs, zero failures/errors/skips, delivery
compilation and format/architecture passed. This includes164 domain and17 actual database
cases. New cases cover a first write after rotation, replay after another rotation/build
upgrade, exact retained private request proof, protocol isolation, uncertain absence,
current denial and both-protocol cancellation/competing uncommitted writers. First
compilation failed due to a test preparation name replacement; the original provider
preparation call was restored. No production authentication or provider policy changed.

Next expose explicit native `/room/requests` and read-only `/room/requests/receipts/read`
routes, with strict input/output and real refresh/write/replay/replacement-parent HTTP
acceptance. Then implement durable Mobile intent commands and finite owning recovery.
Absence fencing, original SDK delivery/history, parent recovery and product routing remain
unfinished. V117-137 remains disabled/unshipped; no migration or new phone build is included.
