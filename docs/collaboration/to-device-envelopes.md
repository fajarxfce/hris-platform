# Native encrypted to-device envelopes

`PUT /_matrix/client/v3/sendToDevice/m.room.encrypted/{transactionId}` accepts an
opaque native HRIS gateway bearer. This fixed route sends Olm ciphertext to
explicit, currently eligible self/direct devices. It does not expose a generic
provider proxy or plaintext `m.room_key`, secret or verification-event routes.

The transaction ID is 1–128 ASCII letters, digits or `._~-`. Strict JSON accepts
only `messages`, with 1–100 canonical selected-company/server users and 1–5
explicit HRIS device IDs each. Each envelope has algorithm
`m.olm.v1.curve25519-aes-sha2`, `sender_key`, and exactly one `ciphertext` entry
whose key is the recipient Curve25519 public key and value contains `body` and
integral `type` (0 or 1). Optional `org.matrix.msgid` is preserved, bounded to
128 printable ASCII characters. It is distinct from the product's encrypted
`org.hris.message_id` used for durable message reconciliation.

Ciphertext is canonical padded or unpadded standard Base64, at most 65,536
characters per envelope and 524,288 characters per request. Public Curve25519
keys decode to exactly 32 bytes. Request framing is bounded at one MiB, with the
same two-second streamed-read check, duplicate/trailing-input rejection and
single-header bearer rules as the key endpoints. The latter retain their
separate 64-KiB request bound and parser. Null/unknown fields and URL credentials
are rejected.

The use case requires a confirmed own-device publication and an exact eligible
recipient set. It verifies the sender's self-signed device identity against the
retained fingerprint, then does the same for each recipient using an explicit
provider key query. Envelope sender and recipient Curve25519 keys must match
those validated identities. A stale device/key cannot be replaced silently.
Inaccessible, missing or unpublished targets reject the whole submission with
`collaboration_recipient_unavailable`; no partial discard is reported as success.
Short access/MFA freshness of a still-eligible offline peer does not remove it.

Current caller admission, own publication and recipient eligibility are checked
before and after every provider exchange. Policy/company/member/account guards,
then native parent/device/gateway guards, retain their existing order. No SQL
transaction spans provider HTTP I/O or cryptographic verification. A late current
denial withholds even a provider success/failure response. The provider may have
already queued ciphertext; the gateway cannot retract that external effect.
Recipient sync must independently enforce current admission before disclosure.

The adapter preserves the original transaction ID and ciphertext, uses only the
fixed encrypted PUT route and accepts only an empty JSON object as success.
Provider request bytes are bounded at one MiB; the response stays at 64 KiB.
The existing 32 immediate short-control permits, five-second exchange timeout,
shorter captured admission deadline, fixed origin and cancellation remain owned.
There is no automatic retry, new transaction allocation or provider logout.

## Replay and delivery meaning

Actual pinned Synapse 1.164.1 retains one queued envelope across immediate retry
and same-device bearer rotation. After the recipient acknowledges its sync cursor,
a provider restart allows the same transaction to enqueue the same envelope again.
This differs from room-send transaction retention. The pinned
[send-to-device servlet](https://github.com/element-hq/synapse/blob/v1.164.1/synapse/rest/client/sendtodevice.py)
uses its process-local
[HTTP transaction cache](https://github.com/element-hq/synapse/blob/v1.164.1/synapse/rest/client/transactions.py):
entries last at least 30 and at most 60 minutes between cleanup passes, and are
lost on restart. Do not promise indefinite deduplication from the provider.

HTTP 200 with `{}` and `Cache-Control: no-store` means the provider accepted the
ciphertext for the admitted targets. It does not establish valid Olm plaintext,
recipient decryption, durable recipient storage or read acknowledgement. The
actual transport fixture intentionally uses opaque fictional bytes. Pinned SDK
interop through the complete HRIS gateway must verify replay handling, encrypted
store/process recovery and delivery acknowledgement after durable acquisition.
Scoped sync/history, native/browser client ownership, groups/context, verification
and backups retain their separate implementation and acceptance gates.
