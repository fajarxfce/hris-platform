# Organization provider credentials

V121 and `CollaborationSpaceProviderSessionRepository` persist internal Matrix
credentials for the organization device registry. These ports are unwired. Native
admission, provisioning leases, final publication, gateway/room authority and
client SDK ownership still need integration before cross-company chat is accepted.

The credential references the exact space/device/account/native-parent/Matrix-user/
Matrix-device tuple. Its parent UUID is durable provenance, not a cascading native
session foreign key. Purging a login cannot erase a generation or make a retired
credential appear uninitialized. `metadata` returns a retained cleared state;
`credential` decrypts only an exactly matching live observation. Neither lookup
grants current content access. The caller must check current authority and expiry.

New rows start at generation zero. Rotation uses the expected generation and exact
immutable provider/parent coordinates. Terminal generation 1000000 is reserved
for cleanup; live generations end at999999. Device revision has the same terminal
reservation. Retirement requires an already revoked device and cannot precede its
revocation/update. A cleared row cannot be edited, reset or deleted. Foreign-owner
and company-scoped credential reads/writes are denied by RLS.

Owned device metadata and credential metadata remain readable after group
offboarding; only irreversible device revocation and credential clearing can bypass
lost group eligibility. Live writes also require a current bound native parent,
active account, matching account security version and unexpired native hard/access
deadlines. These SQL checks do not replace the caller's client/version/proof/MFA,
group and lease checks before and after provider I/O. Take account/native/device
guards before changing credentials; do not hold SQL locks across provider calls.

## Authenticated storage and recovery

The AES-GCM envelope uses the existing identity keyring with a new purpose:
`hris:matrix-space:v1:<sha256>`. The canonical UTF-8 value joins these fields using
a newline: space, account, group device, native parent, Matrix user, Matrix device,
generation, creation time, update time and token expiry. Times are signed epoch
microseconds. Input timestamps are truncated to PostgreSQL precision before
encryption; reads authenticate the stored precision. A live token has at most a
ten-minute deadline and bounded ASCII content. No token or provider owner enters
entity string diagnostics.

`tool/recovery_set.py` adds this projection only at schema121. Earlier schema
projections are unchanged. V122 adds only provisioning metadata and keeps the V121
encryption projection unchanged; schema123 remains rejected until reviewed. The existing
offline `RecoveryKeyVerifier` already decrypts arbitrary bounded purposes using
the application cipher, so no whitelist or alternate cipher is needed. Keep its
256-byte purpose limit: the canonical context is hashed, not pasted into the AAD.

Actual PostgreSQL tests restore a custom archive with current-key and retained-key
group credentials plus an unchanged legacy company credential. They execute the
production Python recovery projection against the restored database and decrypt
all three envelopes using the application's actual cipher. Tampered ownership,
provider identity, generation or deadlines cannot reinterpret ciphertext. This
is credential/database recovery evidence, not participant E2EE-key backup, Mobile
store adoption or a live environment recovery rollout.

The schema tests also restore purged-parent/revoked/cleared metadata and check its
immutability. The terminal-counter fixture synthesizes revision999999 using the
isolated migration owner with only the corresponding trigger temporarily disabled;
constraints stay enabled. It then uses ordinary protected updates for successful
revocation/clearing and rejection of further advancement.

## Next integration

Owned device lookup (optionally FOR UPDATE) and expected-revision updates are now
implemented. They retain immutable provider/original-parent identity and permit
irreversible cleanup after offboarding or parent purge. Two new actual PostgreSQL
cases exercise activation/revocation competition, stale publication, denied scope,
immutable coordinates and transaction rollback; the15-case [regression](../measurements/collaboration-space-device-mutations-2026-11-03.json)
passes in90.862s with3648 unchanged hashes and quality checks. These ports do not
grant current live admission.

Bounded organization provisioning requests, worker leases and typed ports are now
implemented and tested, including atomic storage rollback across credential, device
and queue. Registration must next commit its request with binding, receipt and
journal. Preparation/publication must use current account/native/device authority,
exact generation/revision and a still-current lease. Keep provider I/O outside SQL
transactions. A lost/late/losing provider candidate expires naturally: **Synapse
logout removes the device's other tokens and E2EE keys**, so it cannot clean up a
losing candidate safely. Retained legacy promotion still needs the explicit
transfer fence described in [group provisioning](group-provisioning.md).

[Validation evidence](../measurements/collaboration-space-provider-credentials-2026-11-03.json):
98 regression cases pass in395.199s with3650 unchanged input hashes; focused12 cases
pass in94.059s, recovery-tool13 in0.011s. Format and architecture checks pass.
