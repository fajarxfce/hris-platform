# Internal direct-room adapter

The collaboration module has typed provider operations for resolving its canonical
alias, creating a private direct room, observing bounded state, setting one of five
initial-state types, and inviting the selected peer. The disabled configuration
returns collaboration-unavailable without allocating a provider client.

These operations are internal repository ports. They do not create an HRIS room
binding or open a public Matrix endpoint. A provisioning use case must retain
current company/participant/session authority and its durable ownership fence
before and after provider I/O. Network work must remain outside SQL locks.

## Transport and observation

Login, WHOAMI and room controls share one HTTP datasource and its 32 immediate
permits. Requests never queue behind that limit. All use the operator's fixed
origin with redirects disabled, at most five seconds per exchange, and owned
future cancellation. Room request/response bodies are bounded to 64 KiB. State
responses have at most 256 unique type/state-key pairs; malformed fields, duplicate
JSON properties, trailing data and duplicate state entries are rejected. URI
segments are encoded; a room ID cannot supply a path, query, fragment or origin.
Room-version-12 hash IDs do not require the older server-name suffix.

Only an exact 404 M_NOT_FOUND resolves to a missing alias, and an exact 400
M_ROOM_IN_USE represents creation uncertainty requiring lookup and inspection.
Other failures retain stable safe provider classifications. Mutation acknowledgements
are validated; provider response text, tokens and extra private state never enter
public responses or diagnostics. The observed model retains only the creation
identity, required initial settings, power levels and member states.

## Initial-room policy

A candidate must match the intended immutable company/conversation/DIRECT marker,
creator, explicit room version 12 and disabled federation. Additional creators,
a tombstone or another participant cause a mismatch. The creator must be joined;
the selected peer may be invited/joined or not yet invited. A left/banned peer is
not silently re-invited. Missing initial settings can be filled, but an existing
incompatible encryption, history, join, guest or power setting is not overwritten.

The intended direct-room power levels reserve all state and membership controls
for the creator while allowing ordinary encrypted timeline events. Public HRIS
admission must mediate the creator's operations too: room version 12 gives creators
special authority. Provider power levels alone do not implement HRIS scope.

## First-message delivery and invitation history

Direct rooms use `invited` history visibility. The earlier standalone room-control
experiment used `joined`; that proves creation/repair mechanics but is unsuitable
for the first message to an offline invitee. The pinned matrix-js-sdk 45.0.0
Room.getEncryptionTargetMembers includes invited members only when visibility is
not joined. Invited visibility also limits retrievable history to the invitation
boundary, without making the room public or sharing pre-invitation history.

Clients must observe the room's encryption/member state before sending. An HTTP
create response alone is not proof that the SDK has loaded it. On accepting an
invitation, clients must load history from the invitation boundary, including
messages omitted from the first incremental live timeline window. Key distribution
and recipient durable storage remain separate from provider acceptance.

A peer who has never enrolled any encryption device has no key to receive a first
message. Creating an invitation does not solve this. Product outbox admission must
retain that pending message until an eligible recipient device exists; never mark
it delivered or create participant private keys on the server. Device enrollment,
verification and native/browser interoperability still require their separate
acceptance evidence.

See [adapter evidence](../measurements/collaboration-room-adapter-2026-10-31.json)
for exact checks and corrected failures. No product delivery acceptance is implied.
