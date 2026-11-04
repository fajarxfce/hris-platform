# Group Matrix room protocol

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
