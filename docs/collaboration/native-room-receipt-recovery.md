# Original room request receipt recovery

The existing room setup operation fingerprint includes space/conversation, original
native parent ID/version, device/version, optional observed request account/revision
and the actual client platform/build. Direct-conversation opening differs: its
fingerprint includes only the space and peer. Native token rotation increments the
native version, so an unchanged room JSON body can no longer replay a previously
committed operation after rotation or upgrade.

[Database characterization](../measurements/collaboration-space-room-replay-proof-2026-11-07.json)
passes all six native room cases in 118.329 seconds on 8,613 unchanged inputs, with
format/architecture and no failures/errors/skips. After recording the request, a
different build and then a genuinely advanced native-session version both produce
`operation_payload_mismatch`. Current room status remains readable. The stale native
version is denied, while original request fields and every counted business table
remain unchanged. This is accepted current behavior, not a recovery implementation.

Mobile `e8fc7844` adds bounded native conversation and room transport. Its HTTP tests
preserve original bodies and idempotency keys, but that alone cannot recover an
already committed fingerprint under changed authentication. Do not interpret a
mocked credential replay as such proof. Keep the original uncertainty and ID.

## Accepted internal acquisition

`ReadNativeCollaborationSpaceRoomReceipt` now reads the actor-owned original receipt
with the complete immutable operation descriptor. The request writer and reader
share a pure operation-key policy with the unchanged persisted field order and two
`initial` sentinels. Its metadata includes the original native version and actual
platform/build; these values never replace current authentication or client headers.

The use case acquires operation then room then ordered account then current native
guards. It checks current security, organization and both participants before returning
either a receipt, absence or lookup failure. The captured thirty-second deadline is
checked after waits and dependent acquisitions, including rollback of clock time.
Provider availability, old device existence and original-parent liveness do not grant
or prevent this owned metadata read. No provider or device operation is performed.

[Acceptance](../measurements/collaboration-space-room-receipt-read-2026-11-07.json):
175 domain/database cases pass in197.492s,8,617 unchanged inputs, no failures/errors/
skips and format/architecture passed. Thirteen actual PostgreSQL cases cover original
rotation, current minimum build upgrade, old-parent revocation/purge, account isolation,
corrupted receipts, cancellation, late denial/deadline and a concurrent uncommitted
writer. The original11-case reader pass173.240s is retained separately. Missing receipt
still permits a delayed original to commit; it is explicitly not a rejection or fence.
All sessions are reaped. No HTTP route or application DI binding is added yet.

## Next integration

Expose the accepted read of an **original** operation receipt under current native,
security, organization and participant admission. Retain and validate the complete
original descriptor, then compare the exact original fingerprint in the actor-owned
receipt store. Return only public original-request/receipt metadata. Do not revive
the original native parent, spoof an old client build, rewrite the stored request,
refresh provider credentials, requeue work or grant room/content permission.

The owning use case must preserve operation/room/account/native lock order and its
captured finite deadline. Recheck current authority on both successful and failed
acquisition before publishing a result. Parent replacement or cleanup must not erase
account-owned original receipt facts. A successful recovery read does not reopen a
denied content projection or authorize the retained SDK queue.

An absent receipt is still uncertain: a delayed original may not have entered its
transaction yet. Do not silently replace the operation or treat absence as rejection.
Explicit handoff of an unresolved original needs late-original fencing and durable
retention of the uncertainty before a fresh operation can be prepared. Keep setup
operation recovery distinct from immutable encrypted-message transaction identity.

Mobile durable commands must retain original native version, actual platform/build
and complete body as well as operation/space/peer. Preparation and recovery belong
in use cases coordinating repositories; raw receipt lookup remains datasource I/O.
V117–137 stays disabled until these paths, original delivery and product admission
are accepted. The internal acquisition adds a domain reader and shares the unchanged
writer fingerprint; it introduces no migration or provider behavior change.

## Native HTTP integration

`POST /api/v1/collaboration/spaces/{spaceId}/direct-conversations/{conversationId}/room/receipts/read`
is an authenticated metadata read. It requires no `Idempotency-Key` and creates no
operation receipt, room request or provider work. The actual native bearer and
`X-HRIS-Client-*` headers identify current admission. The body retains the ORIGINAL
operation ID, native parent ID/version, device/version, platform/build and paired
optional request account/revision. The route supplies original space/conversation.

The response contains current `accountId`, the complete original descriptor and a
nullable `{id, version}` receipt. Current participant removal or policy denial still
wins. The response uses `Cache-Control: no-store`; it contains no credentials or
provider grant. UUID/type validation and the shared 1 MiB native JSON limit apply.
Versions are 0..999999, build is positive, original platform is Android/iOS and the
retry tuple is either complete or absent. OpenAPI marks this POST as non-mutating
for idempotency metadata. Existing missing-bearer POST protection returns403; a
revoked native bearer returns401.

The actual API tests recover the same receipt under a newer build and a replacement
parent after original-parent revocation, prove private actor scope and unchanged
receipt/queue counts, and cover absence, invalid input, policy/peer denial, request
size and disabled configuration. Shared DI requires both actual API and FULL worker
contexts. [HTTP/context acceptance](../measurements/collaboration-space-room-receipt-http-2026-11-07.json)
passed in504.080s on8,619 unchanged inputs:238 cases, zero failures/errors and one
preexisting elapsed-retention skip. Format and architecture passed. The first gate
failed one incorrect expected401; production authentication was unchanged.

## Initial dispatch proof is still a prerequisite

Mobile's native credential/session model and the server's native token response do
not expose the native session version. Authentication can rotate a bearer while
preserving a POST body; the existing room writer hashes the actual authenticated
version and client build. A locally assumed pre-send version is therefore not a
valid durable original descriptor. The explicit reader only handles a COMPLETE,
correct original proof; it does not solve initial proof capture.

Before a durable owning caller, characterize real native refresh followed by the
first room write. Introduce an explicit stable intent contract/versioned namespace
or authoritative retained operation descriptor, preserving existing fingerprints
and receipts. Do not decode opaque tokens, guess versions, weaken current admission
or silently replace an uncertain original. No new endpoint or group schema is live.
