# Native DIRECT encrypted sending

`PUT /_matrix/client/v3/rooms/{roomId}/send/m.room.encrypted/{transactionId}`
accepts a fixed Megolm envelope and returns one `event_id`. Plaintext and arbitrary
event types remain closed. The transaction ID is 1–128 characters from
`A-Z a-z 0-9 . _ ~ -`. JSON is at most 64 KiB with duplicate, trailing-token,
depth and string limits; reading the request has a two-second budget. Query
parameters, URL credentials, extra body fields and ambiguous authorization fail.

Accepted content is `algorithm=m.megolm.v1.aes-sha2`, canonical base64 ciphertext
and a 32-byte base64 `session_id`. Ciphertext is bounded to 60,000 characters to
leave provider-envelope room within Matrix's 65,536-byte event limit. Optional
legacy `sender_key` and `device_id` must match the pinned sender device. Replies,
edits and reactions need their own verified relation contract before those
additional fields are admitted. This first route carries basic encrypted content.

## Authority and delivery

The gateway resolves only an exact retained account/company/server/room binding.
That preliminary read identifies account guards; even its failure is followed
by current caller admission before publication. Policy, company and membership
guards precede account guards in stable order. The captured room's two accounts
must be among the acquired guards.

Unlike history reads, sending requires a currently admitted peer with at least
one eligible confirmed recipient device. The existing recipient projection
excludes removed memberships, revoked/hard-expired parents, security-version
changes and revoked/unconfirmed devices. Ordinary access-token expiry and old
MFA do not exclude an otherwise admitted offline recipient. The sender needs
its confirmed key publication; a fresh provider read must still match that pin.

The exact room, sender publication, recipient set and live native admission are
rechecked before and after every provider exchange. Added/removed recipients or
late denial withhold the outcome. A provider write can already have occurred
before that final check: HRIS and Synapse do not share a transaction. No SQL
guard remains held across network I/O. A failed lookup that expires admission
cannot hide the current denial behind its earlier resource error.

The bounded existing control transport sends one escaped fixed request, without
automatic retry. The returned object must contain only a valid event ID. A
caller retry retains the same transaction ID. Actual pinned Synapse deduplicates
that ID across same-device bearer replacement only while the provider mapping
exists; [retention evidence](provider-transaction-retention.md) documents its
finite lifetime. Durable client logical IDs, alias/tombstone reconciliation and
process-restart outbox remain required. Server acceptance is not recipient durable
delivery or reading. HRIS never authenticates/decrypts the opaque message body.

The next integration connects the pinned Android SDK to this actual gateway,
then owned stores/outbox and product UI. Backend/provider fixtures alone do not
accept chat, calling or meetings. The physical phone and live services remain
unchanged.

[Validation](../measurements/collaboration-room-messages-2026-10-31.json) retains
18 source hashes and the 52s focused run. Full 641s coverage had two existing
provider/container startup/restart timeouts under observed memory pressure;
serial 68s reruns of both classes passed without source changes. Combined source
coverage is 36 domain, 201 data, 160 selected API and 112 worker cases (one existing
opt-in skip), format/architecture and both DI graphs. The initial failures remain
recorded; the full invocation itself is not labeled successful.
