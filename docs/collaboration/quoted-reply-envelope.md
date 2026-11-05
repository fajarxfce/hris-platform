# Encrypted quoted reply envelope

The native Matrix send gateway accepts one optional reply reference alongside the
existing Megolm fields. Its exact shape is `m.relates_to` → `m.in_reply_to` →
`event_id`. The ID must start with `$`, contain 2–255 printable ASCII characters,
and have no spaces. Missing relation retains the original plain-message behavior.
Explicit null, unsupported relation families, extra nested fields and quoted plaintext are
rejected before provider access. The domain validates the same bound independently.

The reference is opaque metadata, not an access grant. Sending does not fetch the
target, grant access to its room or assert that the event exists. Existing native
session, room, participant and sender-key admission remains mandatory. Mobile must
resolve previews only through its admitted local conversation and show an unavailable
quote when the target is absent or deleted. The separate
[replacement envelope](message-edit-envelope.md) also supports exact `m.replace`;
it cannot be mixed with the quoted-reply outer relation.

The pinned Android SDK 26.10.23 (Rust commit
`8de215a0aa0026afa5754ae5f05a6d168d1a1d60`) copies `m.relates_to` from plaintext to
the encrypted envelope. The planned Mobile path retains `sendWithExtraContent`,
its paused durable send queue and the app's original logical ID. The reply reference
must participate in that content fingerprint. No online original-event lookup is
required to construct an offline reply. Quoted body text will not be duplicated
into durable draft/outbox records.

Validation: 158 fresh cases, zero failures/errors, one existing opt-in elapsed-retention
worker case skipped; 427.516 seconds, 8,211 unchanged source inputs. Includes domain
bounds, strict delivery parsing, exact adapter mapping, real Synapse transaction
deduplication and same-device token rotation, native HTTP/PG admission, full worker
tests, formatting and architecture. Peer history retains the exact reply relation
and ciphertext; revoked participants cannot send the reply. [Evidence](../measurements/collaboration-reply-envelope-2026-11-05.json).

The Synapse fixture sends a synthetic encrypted envelope; this is not proof of actual
SDK reply encryption/decryption or of the product composer. Mobile draft/queue/history/UI,
actual native reply exchange and offline recovery are the next work. No live deployment
or acceptance-group closure. Group branch port and its separate tests are also pending.
