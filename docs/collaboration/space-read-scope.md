# Organization original and published room read scope

A group gateway must include original owned rooms before advancing a Matrix sync
position. A published group-room inventory alone can omit an original room and its
pending peer key frames. This pure snapshot keeps both sources explicit; current
native/device/provider admission and actual sync integration are separate work.

## Identity and ownership

The owner has exact space/account/device/server/Matrix-user/Matrix-device coordinates.
A fresh HRISG_ device may use either its canonical group user or an already selected
original @hris_company_account user. A retained HRIS_ device requires the exact
original company/device tuple. Empty inventories still validate owner coordinates.
The original company is parsed only from the canonical original user, never from the
selected HR company or by substituting a group UUID. Metadata is not admission.

Original rows must match the space, original company, participant account and server.
Both participants and the conversation ID are nonzero, participants are ordered,
version is zero, the conversation and actual binding creators are participants, and
stored timestamps are finite canonical microseconds in creation/binding/current order.
The original binding creator can differ from the original conversation creator.
Owned history does not require the peer's current company membership or selected alias.

## Complete union

Each source is bounded to 10,000 rows; callers use the existing 10,001-row overfetch
ports to detect incomplete acquisition. Duplicate conversation or provider room IDs
inside a source are rejected. Group rows retain existing group-wide canonical-user
consistency, while an original peer can have a different historical alias in another
room. The caller's own canonical Matrix user must match every room.

A shared provider room ID must have an exact LEGACY group reference: original company,
conversation, participant pair, room ID, binding time and actual creator agree. Both
original and published metadata remain in the snapshot, with one room in the union.
Missing original metadata, mismatched references and SPACE-origin overlaps fail.
Unadopted originals remain explicitly original; no group request, lease or publication
fields are invented. The unique union is sorted by provider room ID, bounded to 10,000,
and never silently truncated. A deterministic SHA-256 digest pins the owner and every
immutable tuple from both sources. Reordered input yields the same snapshot; changed
metadata changes the digest. Current peer credentials are not part of history scope.

## Validation and remaining work

See [accepted gate evidence](../measurements/collaboration-space-read-scope-2026-11-04.json).

The six domain cases exercise empty-owner validation, fresh/retained canonical identity,
original ownership and time validation, different old/current peer aliases, exact
adoption and metadata conflicts, input detachment/digest stability, duplicates, and
10,000-room union capacity with adoption deduplication. Existing history cursor,
projection, actual Synapse and native admission regressions run with the new helper.
No SDK decryption, queued key delivery or current merged sync dispatch is proven here.

Next connect original history framing and cursor handling under the same seven group
coordinates, then admitted original history lookup and complete group sync. Original
company cursor fallback remains exclusive to the exact completed retained device.
Prove actual SDK historical peer-key lookup/decryption before adoption, followed by
metadata/push, Mobile SDK ownership transition and native hard-parent recovery.
Group rollout stays disabled; all 54 PRD acceptance groups remain open.
