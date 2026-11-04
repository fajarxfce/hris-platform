# Organization device-key query and claim

The fixed Matrix key query and claim routes dispatch group gateway tokens to
`AccessNativeCollaborationSpaceMatrixDevice`. Header/body constraints remain shared
with the company gateway. Native HRIS tokens, cookies, provider tokens or URL credentials
do not substitute for an opaque group gateway token. Company key policy stays separate.

Group requests capture at most 100 canonical user names and five explicit devices per
user. Queries may ask for all eligible devices using an empty list; claims require
explicit `signed_curve25519` device selections. The current space's `hrisg` names and
retained `hris` names are accepted only for the configured server. Several names may
identify one account; each requested alias remains separate and can return keys only
for exactly the stored canonical user and Matrix device ID. Group UUIDs never replace
original legacy device IDs. Unknown, ineligible and unbound aliases return empty maps.

## Admission and observations

The private admitted runner locks the signed-UUID-sorted union of caller and requested
accounts before native parent, device, gateway and exact request-slot guards. It reads
the V135 recipient projection once per checkpoint as a bounded batch. The projection
supplies only eligible current canonical own or verified-room peers, independently of
short receiving-side access/provider expiry. Native hard authority remains required.
There is no per-peer repository traversal or use-case-to-use-case call.

Each selected observation pins group/account/device, stored Matrix coordinates,
fingerprint, hard receive deadline and publication confirmation. It is re-read around
every provider outcome together with caller/native/device/provider/gateway/slot
admission. Recipient checks run between two caller checks in the same transaction.
Provider I/O stays outside SQL transactions. A changed selected set returns 503 with
`collaboration_recipients_changed`, letting the SDK retry without caching an obsolete
empty result. Caller revocation and request deadline remain dominant. No operation
extends the original 30-second/admission/provider/slot deadline.

The exact request slot is released in `finally`; cancellation/interruption propagates.
An interrupted thread leaves only its existing finite lease for normal cleanup. The
private extra-recipient callback is absent for self/own-upload operations, preserving
their existing admission and publication behavior.

## Signed results

Queries include version-zero reservations so an SDK can discover a key after provider
publication but before publisher acknowledgement. Missing unconfirmed identities may
be empty; confirmed identities may not disappear. Returned users/devices must match
the explicit eligible selection. Device signatures and fingerprints must match their
pinned original identity. Querying never acknowledges publication.

Claims use confirmed recipients only. The gateway queries and verifies pinned device
identities before consuming a signed one-time or fallback key, with a fresh checkpoint
between the two calls. Each device may return at most one signed key. Missing/exhausted
keys remain empty; mismatched signatures, extra devices and unsigned algorithms fail.
Response maps, algorithms, keys and signatures are detached before validation and
before post-provider callbacks, so later provider collection mutation cannot replace
an admitted response.

## Validation boundary

Required evidence includes pure bounded/alias/signature mapping, ordinary PostgreSQL
cross-company and offline receiving, current authority changes after both outcomes,
publication races, cancellation/exact cleanup, completed retained identities, actual
Synapse query/claim/restart, real HTTP dispatch and the complete worker graph. See the
[accepted gate results](../measurements/collaboration-space-recipient-key-exchange-2026-11-04.json) and unchanged source hashes.

This is device-key exchange. To-device envelopes, group room content/sync/push and
full Mobile SDK adoption remain required. Preserve the original SDK namespace,
passphrase, keys/history and uncertain sends, including native hard-parent recovery.
Group rollout remains disabled and communications acceptance groups remain open.
