# Organization room-history access

The fixed group gateway now dispatches `GET /rooms/{roomId}/messages`,
`GET /rooms/{roomId}/context/{eventId}` and `GET /rooms/{roomId}/event/{eventId}` under
the existing `/_matrix/client/v3` prefix. Strict read parsing, opaque group credentials
and `Cache-Control: no-store` remain mandatory. Native/provider tokens, duplicate
authorization, URL credentials, unsupported filters, duplicate parameters and invalid
page bounds cannot substitute for valid group access. Company and group operations
share page/event-ID shape validation while retaining separate ownership and cursors.

## Current caller and retained room

The fixed group runner admits the current native parent, account, device, gateway,
provider credential and exact finite request lease. For LEGACY devices it verifies
completed transfer, original parent/device/key provenance, provisioning outcome and
provider generation before constructing the history owner. Protocol metadata supplied
by a caller does not replace that proof.

The use case reads the exact V131 bound room under participant root RLS, validates
immutable intent/binding metadata and matches the original caller Matrix user/device.
History uses caller guards; an immutable owned room does not require current receiving
authority from its peer. Short peer expiry or offboarding does not erase owned history.
New-message sending still requires current confirmed receiving devices. Reads do not
require a new own-key publication or perform an unrelated signed-key query.

The original owner and bound room are pinned and checked around every provider outcome,
including failure. Changed owned metadata returns the established room-changed error;
expired admission returns the established retryable admission-changed error. Current
native revocation takes precedence over a provider response. The original 30-second
budget and finite slot lease never extend. Provider acquisition, cursor crypto and
projection run outside SQL; cancellation/interruption propagate and exact request
cleanup remains bounded.

## Original SDK positions

The [history protocol](space-room-history-protocol.md) now receives its metadata from
current group admission. Completed original devices can open the exact old company
history cursor for a retained LEGACY room. Returned continuation cursors use the group
namespace, and a terminal page omits `end`. New SPACE rooms use group cursors even when
their participants retain old Matrix identities. The gateway never passes raw provider
positions, deletes the SDK store, changes the original passphrase/keys or synthesizes
delivered/read receipts. Existing event/context reads can support client reanchoring
without replacing local history.

See the [accepted gate evidence](../measurements/collaboration-space-room-history-access-2026-11-04.json), including original failed
fixtures, corrected focused acceptance and final source continuity.

These PostgreSQL/provider/HTTP checks do not complete full Mobile SDK adoption or valid
Megolm decryption. Group sync, metadata, push and native hard-parent recovery remain
required, as do the broader chat/call/meeting acceptance groups. Group rollout remains
disabled. A backend access increment alone does not justify installing an unchanged
mobile product build.

Before enabling group sync, retain separately proven original owned-room inventory
or require a complete original-room adoption barrier. Filtering sync to newly bound
group rooms alone could omit original queued to-device keys while advancing the
provider cursor. V129/V130 keep original inventory and actual creator metadata;
those rows must remain explicit rather than fabricated group bindings. The current
history routes require published group room bindings. Broader retained-history
fallback and sync admission remain separate integration work.
