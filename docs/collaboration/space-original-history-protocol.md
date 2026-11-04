# Original room history through the group protocol

An admitted group device can retain a canonical original Matrix user while its owned
original room has no published group binding. The history port now has explicit
original-metadata overloads for messages, event context and single-event reads. It
uses the existing shared bounded acquisition/projection reader and never fabricates
group lease, request or publication metadata.

## Identity and cursors

Original framing verifies canonical space/account/user/device and original company,
participants, server, version, stored time ordering and actual binding creator. Provider
framing rejects a binding beyond the request deadline; the native use case separately
must establish current time and admission before dispatch. Original conversation and
binding creators remain distinct. Only original room members appear in projected
content and creation state uses the actual original binding creator.

Cursors use the same seven group coordinates (space, account, group device, server,
Matrix user, Matrix device, provider room ID) before and after exact group adoption.
An original-room cursor therefore continues on the published-room path and vice versa,
without replacing provider positions or SDK storage. Immutable metadata provenance is
checked at cursor construction; it does not itself authorize current native access.

Only an exact completed retained HRIS_ device can bridge its original company/device
cursor. A fresh HRISG_ device on the same original user has isolated cursors. The common
opener attempts the original namespace only after an INVALID group cursor. Missing
crypto keys remain RESET_REQUIRED; encryption failures remain UNAVAILABLE; cancellation
and interruption propagate without falling back. Company cursors cannot open group
cursors and changed devices or provider rooms cannot replay them.

## Provider projection

Messages retain private-position progress checks before randomized cursor encryption.
An empty repeated position ends history; a nonempty repeated position fails. Context
retains combined bounds, target matching and duplicate rejection; single-event reads
retain exact event identity. Original ciphertext, sender and device IDs remain intact.
Foreign/plaintext content and malformed original metadata are rejected, and cursors
are opened before provider I/O. Existing company and published-group behavior shares
the same reader and error mapping.

See [accepted gate evidence](../measurements/collaboration-space-original-history-protocol-2026-11-04.json).

The four new protocol cases cover adoption cursor continuity, fresh/retained isolation,
missing-key and cancellation classification, original creator/content across all three
read shapes, malformed ownership before I/O, progress termination and provider failure.
Actual Synapse legacy history alternates original/group framing after restart, resumes
an old company cursor and reads from a fresh device before/after adoption. These are
opaque transport proofs, not SDK Megolm decryption or a completed native fallback route.

Next connect exact original lookup only after successful absent group lookup, revalidate
both outcomes under current native admission, and validate actual API/worker contexts.
Then complete group sync, historical peer-key authentication/decryption, metadata/push,
Mobile adoption and native hard-parent recovery. Group rollout remains disabled; all
54 PRD acceptance groups remain open.
