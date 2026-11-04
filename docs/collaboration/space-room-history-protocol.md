# Organization room history protocol

`CollaborationSpaceMatrixRoomHistoryRepository` provides bounded messages, context
and single-event reads with a distinct `CollaborationSpaceHistoryOwner`. The owner
retains the group device ID and original Matrix user/device coordinates; optional
LEGACY company/device coordinates keep their original meaning. A group UUID is never
substituted for a company UUID. These are provider protocol contracts, not current
native, device or room authority. Runtime group dispatch and admission remain required.

The pure coordinate policy checks the exact bound room, participant, server, creator
and canonical original device namespace. A LEGACY device can read a newly created
SPACE room while retaining its original HRIS_ identity. Only an exact LEGACY room
with matching original company/device metadata can expose the old company cursor
namespace. The eventual use case must prove completed transfer and current admission
before constructing this metadata; possession of the metadata grants no access.

## Cursors and retained history

New cursors use the separate `collaboration.space.room.history.v1` encryption purpose,
bound to space, account, group device, server, original Matrix user, original Matrix
device and room. Company/group, account, device, room and server replay fail. Provider
positions remain private, bounded to 1,024 printable ASCII characters; opaque input
cursors remain bounded to 4,096.

Opening first tries the group namespace. Only an INVALID cursor with an exact eligible
LEGACY room/device tuple may try the original company namespace once. Missing crypto
keys retain reset-required classification and do not trigger fallback. Cancellation
and interruption propagate. The codec does not perform retries, logout, rekey or SDK
store deletion. All newly sealed positions use the group namespace. If a page start
equals the input position, it may return that exact input cursor, including a valid
bridged cursor, to preserve stable pagination.

## Shared bounded projection

Company and group repositories share one private protocol reader and cursor crypto
primitives. Repositories keep acquisition and mapping inside `safeMatrixProviderCall`;
they do not call one another. Explicit original participant/current/creator Matrix
users frame the existing direct-room event mapper. Foreign room/sender, plaintext,
invalid state and duplicate IDs are rejected; unsafe provider fields are omitted.
Original ciphertext, session/device IDs, event IDs and permitted own transaction IDs
remain unchanged.

Both `from` and `to` are opened before provider I/O. Raw start/end/from are compared
before randomized encryption: an empty stalled page omits `end`, a nonempty stalled
page fails, and an empty advancing visibility gap remains pageable. Context requires
the exact target and bounds the combined before/after events. Event reads require
the exact event ID. Existing HTTP transport limits and membership-loading filters
remain in force.

All 139 tests pass in 76.378 s: 122 domain and 17 provider/cursor/
mapping cases, including actual company and group/LEGACY Synapse history. Quality
checks pass on 8,446 unchanged inputs. See the [gate evidence](../measurements/collaboration-space-room-history-protocol-2026-11-04.json).

Actual provider tests use opaque Megolm fixtures. They do not prove SDK encryption,
decryption or completed-device runtime admission. The next increment must wire fixed
group messages/context/event operations with current caller admission and immutable
owned room checks before and after every provider outcome. Peer receiving eligibility
must not erase owned history. Group sync has separate room-set/filter semantics and
must not blindly reuse this history bridge. Metadata, push, full Mobile SDK adoption
and native hard-parent recovery remain required; group rollout remains disabled.
