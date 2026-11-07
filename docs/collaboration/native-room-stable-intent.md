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

## Native HTTP

`POST /api/v1/collaboration/spaces/{spaceId}/direct-conversations/{conversationId}/room/requests`
uses stablev2 intent and requires `Idempotency-Key`. Its body retains device/version and
the complete optional observed request account/revision. Actual native credentials and
client headers provide current admission. No native version is guessed or supplied.

`POST .../room/requests/receipts/read` is read-only, without an idempotency header. It
accepts the operation UUID and same immutable body; responds with current actor account,
complete original seven-field intent and nullable `{id, version}` receipt. No credentials
or current content authority are returned. Original native-proof routes are unchanged.

[HTTP acceptance](../measurements/collaboration-space-room-stable-http-2026-11-07.json)
passed97 cases in192.018s on8,624 unchanged inputs with zero failures/errors/skips and
format/architecture passed. Actual tests perform two native refreshes with real11-second
cooldowns, preserve the first receipt after an app upgrade, recover through a new parent
after old-parent revocation, and retain one queue/receipt. Peer/legacy isolation, uncertain
absence, current denial dominance, invalid input/size bounds, required headers, OpenAPI
and disabled routes also pass. DI constructors remain unchanged; this gate validates API
contexts, with prior full worker graph acceptance retained from4e1b5b90.

Next finish Mobile stable intent transport, durable commands and finite owning recovery.
Absence fencing, original SDK delivery/history, parent recovery and product routing remain
unfinished. V117-137 remains disabled/unshipped; no migration or new phone build is included.
