# Published room original reference

The native organization room response includes `room.original`: `null` for a new
SPACE room, or the immutable `companyId`, `conversationId`, `roomId`, and `boundAt`
for a LEGACY room. These values come from the existing room intent; no schema or
provider write is added. A client can perform an exact original-conversation lookup
without guessing the selected HR company or treating a Space conversation ID as an
original company conversation ID.

The read use case retains current native session, group, participant and client
admission before and after acquisition. Publication requires matching room intent,
status and binding. LEGACY coordinates must be nonzero, agree with the bound room
and canonical company users, and precede the intent with a valid stored timestamp.
SPACE cannot claim an original. Contradictory metadata is rejected with the existing
`collaboration_room_provisioning_changed` code; no partial reference is published.

The response contains no device credential, native authentication proof or worker
lease. Original coordinates identify retained history; they do not authorize an SDK
client, historical export, message delivery, or a different HR company. Mobile must
still match its owned original room and account pair before retaining the binding.
Current content access and immutable local ownership remain separate decisions.

[Validation](../measurements/collaboration-published-room-reference-2026-11-09.json)
passes266 fresh cases in513.312 seconds, with zero failures/errors/skips and8,637 fixed
inputs. Native admission includes three new regressions for both-participant exact
references, contradictory metadata, cancellation and final denial. The existing actual
LEGACY worker-publication case verifies the precise retained reference. API/OpenAPI and
format/architecture checks pass. Shared DI is unchanged; the full worker application
suite was not rerun. Mobile encrypted binding persistence and product routing follow
separately. V117–138 remain unshipped.
