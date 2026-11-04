# Organization gateway own-key exchange

The fixed group `POST /_matrix/client/v3/keys/upload` operation uses the original
Matrix user/device IDs and separate organization gateway authority. The controller
shares existing strict header, body, stream-size and signed-key parsing. Group
query, claim, room and to-device operations remain unavailable until their explicit
recipient and room authority is implemented. Group availability remains disabled.

## Preparation and provider work

The use case snapshots bounded caller collections before admission. It obtains the
same current native/group/client/MFA, canonical ACTIVE device, live provider generation
and retained completion authority as [self access](space-gateway-access.md). One exact
ordinary request slot owns the original 30-second budget, clipped to the original
native, gateway and provider deadlines; no provider step extends it.

Counts-only uploads create and acknowledge no public-key reservation, including when
an earlier upload is still uncertain. First identity publication validates signed
device keys and commits an immutable [reservation](space-key-publication.md), safe
journal record and request preparation atomically. A different fingerprint is rejected.
A retained LEGACY device must keep its completed original fingerprint. Signed one-time
or fallback keys without supplied device keys require an already acknowledged identity.

After prepare commits, the use case reads the provider's exact own-device keys and
validates their signed fingerprint before uploading anything. It verifies signed
one-time/fallback keys against the preserved identity, uploads the captured values,
and reads the original device keys back. Every provider operation is outside SQL and
has current admission checks before and after both successful and failed results.
Native/provider generation changes, request replacement, cancellation or expiry prevent
publication of a stale result. No company login, device logout or arbitrary provider
path is available to this operation.

## Acknowledgement and uncertain outcomes

Matching signed readback can acknowledge only the exact retained reservation under
current account/native/device/gateway/request guards. Acknowledgement and its journal
commit atomically, with exact stored readback and final authority checks. Competing
uploads of the same identity may observe the one already committed acknowledgement;
they cannot change its fingerprint, original coordinates or reservation time. An
already observed acknowledgement must remain exactly unchanged.

A failed, cancelled or lost upload leaves the committed reservation for recovery.
A counts request never turns it into an acknowledgement. Retrying the original signed
identity reads any already uploaded provider keys and can finish the same reservation.
Lost acknowledgement responses recover through the retained version-1 record. The
private runner preserves the original failure during exact lease release, and cannot
release a successor's token. There is no hidden retry or deletion of client history.

## Validation and next work

[Accepted evidence](../measurements/collaboration-space-key-exchange-2026-11-04.json)
records focused18 PASS 268.626s, final data20/API132 PASS 455.786s and full worker128 PASS
343.98s (one existing retention skip), identical 8,351 final inputs and quality checks.
An acknowledged-metadata comparison and two fencing cases were added after the first
pass; there was no failing gate. Real PostgreSQL covers immutable input, counts, lost
responses, signatures, concurrent publication, revoked access, replaced requests and
transaction failures. Actual pinned Synapse preserves group and retained original
identities across restart. Strict native HTTP and both application contexts pass.

Recipient key availability must include unacknowledged reservations with the V114
confirmation flag, verify all returned keys, and preserve offline receiving independently
of short native/provider token expiry. Key queries, claims and to-device transport need
explicit current peer/room authority. Original-room inventory and native hard-parent
recovery remain required before Mobile group adoption. No end-to-end cross-company
conversation, product APK or rollout is implied by own-key exchange alone.
