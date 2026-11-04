# Organization device public-key identity

V128 reserves a group's device public-key fingerprint before provider upload and
acknowledges only an exact, signed provider readback. It stores public metadata;
private keys remain in the original client SDK store. The table and typed repository
are separate from company publications. Storage alone enables no group key endpoint or recipient projection. The subsequent
[own-key exchange](space-key-exchange.md) supplies the fixed upload integration.

## Retained identity and transactions

A reservation pins the organization space, account, device, original native parent,
original Matrix user/device IDs, lowercase SHA-256 fingerprint and reservation time.
Version 0 has no publication time; the only update is version 0 to version 1 with a
publication time at or after reservation. Replacement, reset and deletion are forbidden.
There is no foreign key to the transient native session: purging that parent must not
erase an uncertain provider upload or silently authorize replacing its keys.

Root account ownership permits reading retained metadata after offboarding or parent
purge. Company scope, anonymous callers, other accounts and worker maintenance receive
no additional access. Writes independently require current group eligibility, an
ACTIVE original device and current bound native security/access. SQL rechecks actual
time after waiting for the device row. A retained LEGACY device also requires its
immutable completed-transfer record and exactly the already retained fingerprint.

The use case owns account/native/device/gateway/request guards, transaction boundaries
and current provider admission. These metadata writes do not grant request authority.
Repositories validate finite microsecond metadata and exact scope on acquisition;
reservation and acknowledgement require exact post-write readback within the same safe
failure boundary. A failed result, cancellation or interruption rolls the caller's
transaction back. A compare-and-set miss has the stable conflict code
`collaboration_space_device_keys_changed`; there is no automatic retry or reset.

Reservation must commit before external upload. An upload that fails or whose response
is lost leaves version 0 for recovery; a new request first reads that owned identity.
Acknowledgement requires a matching signed provider proof and current exact request
admission, then commits under the same guards. A lost acknowledgement response is
recovered by reading version 1. No upload or logout is part of the storage adapter.

## Gateway integration and required recipient authority

The fixed own-key gateway operation snapshots bounded Matrix input before admission.
A counts-only request creates or acknowledges no reservation. First publication needs
validated signed device keys; signed one-time/fallback keys without an identity need
an already published identity. Read existing provider keys before upload and reject
any replacement. Validate signatures against the preserved identity, read back after
upload, and acknowledge only after current admission and exact fingerprint checks.

Matrix device-list events can precede HRIS acknowledgement. The future group recipient
projection must preserve V114's behavior: include committed version-0 reservations with
an explicit publication-confirmed flag, verify every returned provider key against the
reserved fingerprint, and permit absent provider keys only while unconfirmed. Do not
let the SDK cache an empty confirmed device list during that race.

Sender gateway authority and recipient availability are distinct. An offline receiver
must not need a live short native access token or provider bearer simply for its public
keys to remain available. Receiving requires current group eligibility/security and
retained device binding under a documented lifetime. Native hard-parent replacement
still needs explicit ownership recovery. Room/peer authority must bound key queries,
claims and to-device traffic; directory membership alone grants none of these.

The existing recovery envelope projection is unchanged at schema 128 because no new
encrypted value is stored. Database archive recovery preserves immutable reservations,
acknowledgements and their RLS/trigger rules. Unknown future schema 129 remains rejected.

[Accepted evidence](../measurements/collaboration-space-key-storage-2026-11-04.json)
records all 82 database plus 6 typed/retained-provider cases passing in 792.52s,
8,350 unchanged inputs, format/architecture and 18 recovery cases. The initial missing
fixture-role write grant and its correction are retained. Actual concurrency, expiry
after a PostgreSQL row-lock wait, rollback, native purge, archive restore and original
signed keys through Synapse restart pass. Product group rollout, Mobile adoption and
all communications acceptance groups remain open.
