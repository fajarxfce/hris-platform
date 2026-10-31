# Native Matrix device-key upload

The fixed public `POST /_matrix/client/v3/keys/upload` now accepts the pinned
Matrix key shape through an opaque HRIS gateway bearer. It never exposes the
private provider bearer. The existing WHOAMI operation shares the private
admission/renewal runner in `AccessNativeCollaborationMatrixDevice`; these are
explicit operations, not a caller-configurable proxy or use-case chaining.

## Input and identity

Tokens belong only in one Authorization header. URL parameters, duplicate
headers, unknown JSON fields, duplicate members, trailing input and explicit
null key objects are rejected. JSON is bounded to 64 KiB, depth eight, strings
512 and a two-second streamed-read budget. Device identity contains the current
Matrix user and device, the two supported Olm/Megolm algorithms, exact Ed25519
and Curve25519 public keys and the device self signature. At most 100 signed
one-time keys and one signed fallback key are accepted. Plain unsigned keys and
unknown algorithms remain unavailable. Counts-only `{}` is valid and creates no
key-publication identity.

Public keys/signatures use canonical padded or unpadded base64 with exact 32/64
byte lengths. Ed25519 verifies Matrix canonical JSON using the standard JCA
implementation; independently signed OpenSSL/Python public vectors exercise the
encoding. Missing `fallback` and explicit false stay distinct signed objects;
fallback keys require true. No participant private key is generated or stored
by the HRIS server. Inputs are detached before waits. Diagnostics and result
`toString` do not contain the key payload.

The retained fingerprint is SHA-256 over UTF-8
`org.hris.matrix.device-keys.v1` + NUL + user ID + NUL + device ID + NUL,
followed by the raw 32 Ed25519 bytes and 32 Curve25519 bytes. Algorithm/signature
ordering and equivalent base64 spelling do not change identity. Supported
algorithms and signatures are still validated before computing that fingerprint.
This fingerprint does not replace client cross-signing or verified-device trust.

## Publication and failure

The use case reacquires current company, member, account, native parent, MFA,
client policy/build, active device and gateway head in the established guard
order. The key guard follows these guards. A valid first device identity reserves
schema 112's immutable fingerprint with its journal in one transaction. A pending
reservation needs the same full identity for signed-key uploads; a published
identity can upload additional signed one-time keys without repeating its device
object. Counts-only requests neither reserve nor confirm publication.

Every provider request runs outside SQL, with current authority checked both
before and after, including failures. Internal credential renewal retains its
generation fence and never logs out a losing candidate. The shared short-control
transport has 32 immediate permits, a five-second request bound and 64-KiB
response bound. A full upload performs exact own-device query, upload, then
readback; an optional private credential renewal precedes those three exchanges.
All share the captured admission/provider/gateway deadline. No hidden retry or
provider compensation occurs.

The preflight refuses a conflicting pre-existing provider identity. One-time and
fallback signatures must validate against the retained identity before upload.
Readback must validate the same identity before the final transaction confirms
publication and records its journal. The final transaction repeats admission and
the exact reservation fence; failure/interruption rolls back only that local
publication. A valid reservation may remain pending after invalid signed-key
input, provider failure, lost response or revoked admission. Retrying the same
identity can reconcile the actual provider result. Competing first uploads cannot
reserve different keys for one device. Native refresh invalidates a captured
exchange temporarily while preserving its identity for a newly admitted retry.

Actual Synapse 1.164.1 accepts replacement device keys through the same bearer.
HRIS therefore enforces immutability before calling it. An external provider
administrator is not part of the HRIS SQL transaction: publication records a
verified readback, not a distributed transaction or permanent provider-state
promise. Future recipient queries must check the retained fingerprint again.
A conflicting identity returns `collaboration_device_key_recovery_required`;
never clear the client's crypto store merely to retry that error.

## Evidence and remaining integration

[Measurement](../measurements/collaboration-device-key-upload-2026-10-31.json)
retains independent signature vectors, actual Synapse retry/private-bearer/
restart behavior, provider replacement reproduction, current-authority races,
real PostgreSQL rollback, HTTP bounds and both runtime contexts. Signature shape,
provider transport and own-device publication are distinct from SDK/content
acceptance. The actual HTTP fixture accepts chunked replay and rejects an upload
result after native revocation, with its pending identity retained.

Public peer query/claim, to-device messages, classic/native sync and room/content
routes remain closed. Browser-parent admission, client crypto ownership/recovery
and recipient delivery still need integration. Recipient key eligibility must
allow an admitted employee's enrolled offline device; short access-token freshness
is not device revocation or a reason to discard owned local history. A peer with
no eligible published device needs a pending product outbox, not a self-only
message reported as delivered. MatrixRTC, backup and verified-device UX retain
their separate acceptance gates.
