# Native Matrix gateway credential storage

Schema 109 stores one HRIS gateway credential head per company/account/device.
The public access and refresh values are separate 256-bit opaque credentials;
they are neither native HRIS tokens nor internal Synapse tokens. Only their
SHA-256 hashes and one encrypted lost-response response are persisted. Participant
E2EE private keys never enter this store.

The retained identity includes the native parent, platform, client build and
monotonic version. New heads require the exact active device and parent binding.
The parent may later be purged without erasing the credential generation. Version
one million is reserved for retirement after irreversible device revocation;
SQL rejects deletion, resurrection, foreign bindings and stale compare-and-set.

Refresh atomically replaces both hashes and remembers only the immediately prior
refresh hash. Racing rotations produce one winner. That previous hash can replay
only the winning generation's encrypted response; another rotation removes it.
A losing or older request never revokes the winning device or deletes its keys.
Explicit bootstrap can update the client build while retaining the same Matrix
device. Its operation identifier selects only the current bootstrap response.

The response cache has a maximum 60-second lifetime; access lifetime is bounded
at 15 minutes and the hard deadline by both the parent and 31 days. Admission use
cases must enforce their shorter operational defaults, replay deadline, current
native access/MFA/company/permission/client policy, and admission again after any
external I/O. A token lookup only identifies a record. It runs in a separate
transaction using a transaction-local hash capability; it never authorizes a
provider request by itself. Native and browser parent admission remain distinct.

AES-256-GCM uses the existing separately retained identity keyring. Its context is
`hris:matrix-gateway:v1:` followed by the SHA-256 of canonical colon-separated
company/account/device/head/parent/platform/build/version, creation/rotation/hard/
access deadlines in Unix microseconds, both token hashes, replay kind, bootstrap
operation or previous refresh hash, and replay deadline. Every field is delimiter
safe and microsecond normalized. The context hash satisfies the existing 256-byte
AAD limit. Recovery selects this projection only for schema 109 or later; actual
PostgreSQL dump/restore verifies both old and current keys and the independent
Python/SQL projection against the Kotlin encryption context.

Expired cache cleanup locks at most 100 rows with SKIP LOCKED. It erases encrypted
response, prior hash, kind and operation without changing the active token hashes,
head version or device. The expired deadline stays as nonsecret metadata so the
restricted maintenance role can see the updated row under PostgreSQL RLS. A new
rotation replaces that deadline and hides its live replay from the maintenance
role. The trigger forbids changing other facts during this cleanup and takes no
reverse device lock. Application clock drift cannot clear a cache before the
database clock says it has expired.

This increment supplies storage and recovery, not a publicly usable Matrix
endpoint. Bootstrap/refresh admission, the protected bounded provider gateway,
maintenance scheduling, SDK wiring, product chat and browser parents still need
implementation. No provider bearer is returned by these repositories.
