# Group Matrix room protocol

## Current recovery contract

Either admitted participant may recover the immutable SPACE intent's stable alias
using its own provider credentials. Typed `join` validates the candidate and requires
exact `room_id` readback. It grants no authority by itself. After joining, recovered
room assessment requires the caller's joined membership and retains the actual
`m.room.create` sender, which must be one of the exact two canonical participants.
The strict room policy is oriented to that creator. A different participant cannot
repair missing initial settings or invitation; it gets a stable conflict instead.
Healthy recovered state has no pending repair before binding publication.

[Current evidence](../measurements/collaboration-space-peer-provider-2026-11-04.json):
105 domain, five repository and four actual Synapse cases pass in 83.55 seconds with
quality checks and 8,398 unchanged inputs. The first participant creates but loses
the response; the second resolves and joins the same room with its own credentials.
Actual creator, same alias and both original signed device keys survive restart.
These are protocol/key checks; encrypted SDK-message acceptance remains separate.

[V131–V133 storage](space-room-lifecycle.md), [V132 routing](original-pair-routing.md)
and [native HTTP](native-space-room.md) now pin both participants, exact original or
SPACE intent and atomic binding/success. The room worker is still required to admit
current authority around every provider outcome. V127 has no room-control slot kind;
add a bounded kind sharing slots 1..7 without fabricating gateway coordinates or using
renewal slot zero. Recipient content/sync/push and Mobile integration remain disabled.
Earlier sections below record the original adapter and verification checkpoints.

Group rooms carry an explicit organization space, conversation, creator/peer account
IDs and both canonical Matrix user IDs. User IDs may retain different original
companies or use the new organization namespace. The provider adapter never derives
one participant's identity from another participant's HR company or replaces keys.
Shape validation is separate from current account/device/room authorization.

The provider alias is `hrisg_dm_<space UUID without hyphens>_<conversation UUID without
hyphens>`. Room creation uses an immutable `org.hris.space_conversation` marker in
`m.room.create`, containing `space_id`, `conversation_id` and `kind: DIRECT`. This
namespace is distinct from original company aliases and creation markers. The same
conversation UUID must not create or resolve a company room by substituting space
for company. A resolved or created room ID is validated before returning the candidate.

Creation requests room version 12, private visibility, disabled federation, Megolm
encryption, invite-only joining, invited history visibility, forbidden guests and
the existing restricted direct-room power settings. Only the explicit canonical peer
is invited. A peer may not yet have logged into the provider; an invitation is not
proof of an available receiving key or delivered encrypted message.

## Verification and uncertain outcomes

After resolution/creation, the owning use case must observe actual provider state and
verify creator, immutable marker, version/federation, no additional creators or
tombstone, exact encryption/settings/power and only the two canonical members. The
creator must have joined; the peer may be invited or joined. Unknown participants,
ban/leave states or mismatched existing settings are rejected. Only absent initial
settings and an absent peer invitation can be proposed for repair on an otherwise
matching room. No automatic repair or retry runs inside the adapter.

Creation can succeed even when its response is lost. Retry first resolves the same
stable alias and verifies the existing candidate instead of generating a new room.
Alias-in-use is an explicit provider result. A mismatched candidate is retained and
rejected; it is never overwritten, silently adopted or replaced with a second alias.
Room observation rejects duplicate `(type, state key)`, oversized or malformed state.
Acquisition and mapping share the technical provider failure boundary; cancellation
and interruption propagate. The raw HTTP client owns bounded exchanges and the caller
supplies the original deadline. There is no database or use-case policy in the port.

## Integration boundary

The typed group request, marker, observation and policy are separate from company
types. The repository reuses only raw `MatrixRoomDataSource`, fixed protocol settings
and technical error mapping; it does not invoke another repository. No new DI,
controller, SQL schema, worker queue or product caller is introduced by this adapter.

Next, a group-owned request/publication lifecycle must pin the first creator and exact
canonical users, admit current native/group/device/request authority before and after
outside-SQL work, and publish one immutable verified binding. Generic `background_jobs`
still requires an HR company, so space IDs cannot be used there. Follow the bounded
group provisioning pattern with separate room ownership. Recipient keys, content,
sync and push require that durable room grant before gateway access.

Original room adoption remains separate. Start from the exact owned original inventory,
require both selected canonical users to match its original participants, and verify
actual original marker/encryption/membership. Group creation policy does not convert
old markers into new markers or re-encrypt historical events. Keep other historical
identities and local SDK namespaces explicit.

[Accepted evidence](../measurements/collaboration-space-room-provider-2026-11-04.json)
records96 domain cases passing in the initial41.723s run and final6 data cases passing
in50.407s with quality gates and8,371 unchanged inputs. Four initial data failures were
corrected only in test fixtures: explicit JSON child iteration and current provider
endpoint after restart. Production/domain sources stayed unchanged. Actual Synapse
covers cross-company retained identities, creation before peer login, join, duplicate/
lost responses, foreign membership, restart and original signed-key readback. These
are provider control/key checks, not client SDK message or Mobile adoption acceptance.

## Exact original lookup and verification

The conversation repository now has a bounded original company/unsigned-pair lookup.
It reads at most two rows and requires at most one exact owned match; foreign scope,
ambiguous or malformed readback fails. A missing row, unbound conversation and bound
room remain different outcomes. An unbound original can still have a pending or
uncertain old room job; this lookup never authorizes a replacement room.

`prepareCollaborationSpaceLegacyRoom` checks the group pair against both exact original
canonical Matrix users, original metadata/times and actual V130 binding creator. Its
output retains original company, conversation and room coordinates. It conveys no
native/group authority. `assessCollaborationSpaceLegacyRoom` applies original immutable
marker/creator/member/encryption rules and requires every setting/invite to be present.
Historical repair suggestions are rejected. A caller must still pin these coordinates
and recheck current native/device/group/peer authority before and after provider work,
then atomically publish a durable group binding. Other legacy producers and unbound
original room jobs need a real creation fence before rollout.

[Verification evidence](../measurements/collaboration-space-legacy-room-policy-2026-11-04.json)
records 110 passing cases in 144.409s: all 99 domain cases, eight actual PostgreSQL
repository cases and three actual Synapse cases, plus quality checks on 8,374 unchanged
inputs. The original canonical peer joins using its retained user/device after group
login; original creator, room state and both signed keys survive restart. No provider
logout, creator impersonation, key replacement or new group alias occurs. These are
protocol/public-key checks; no native room grant, SDK message or Mobile rollout follows.

## Durable storage foundation

[V131 storage](space-room-lifecycle.md) now pins the immutable candidate, original
creator/coordinates, bounded request and binding. Binding and queue success commit
together. It does not invoke this provider or admit native/group access. The owning
use case and worker still need current authority around each provider outcome and
a shared fence against later original conversation creation before product rollout.
