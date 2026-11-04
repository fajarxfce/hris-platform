# Organization recipient key candidates

V135 supplies a separate read-only projection of eligible receiving devices. Its
non-login owner has only the exact source columns needed for public identity and
eligibility. Runtime roles cannot assume it. No provider token, native access/refresh
hash, gateway replay, private key bytes or HR profile is available through that role.
The view has a security barrier and does not change ordinary private-table RLS.

A root-scoped caller must have current group eligibility and a canonical identity.
Own devices need no conversation. A peer candidate additionally requires a verified
immutable room binding for exactly those canonical participants. A directory entry,
an unbound conversation or a provider-visible room is insufficient. Selected HR
company scope does not grant this projection or another company's HR resources.

Each receiver must have current group eligibility, an ACTIVE/nonrevoked device,
unchanged canonical user/device identity, matching public-key reservation and live
binding to its original native parent. The parent must be unrevoked, within its hard
lifetime and match current account security version. Completed LEGACY transfer and
its original fingerprint remain mandatory. Stored Matrix user and device IDs are
retained; a group device UUID cannot be substituted for its original Matrix device ID.

Receiving is independent of short native access, gateway and provider-token expiry.
Offline devices keep receiving while their underlying device/native authority remains
valid. V121 provider retirement requires device revocation, already excluded by the
ACTIVE predicate. Native hard expiry, revocation, security change, lost eligibility
or parent purge removes receiving eligibility without deleting original key/device
provenance or local history.

Version-zero reservations appear with `publicationConfirmed=false`, preserving the
provider pre-acknowledgement device-list race handled by V114. Query consumers must
verify any returned signed identity against its pinned fingerprint. Unconfirmed keys
may still be absent upstream; confirmed keys must not disappear. Candidate discovery
never acknowledges a reservation. Claim/send consumers require their own confirmed
recipient and current room rules before they consume keys or deliver ciphertext.

The repository captures a bounded account set before I/O, accepts at most 100 accounts
and reads at most 501 rows to detect overflow. It permits at most 500 devices and five
per account. It rejects foreign scope/account, malformed canonical names, invalid
fingerprints/time, expired readback, duplicate group devices and duplicate Matrix
user/device coordinates. It never silently truncates recipients. Raw datasource I/O
remains separate from typed mapping; cancellation/interruption propagate through the
shared database error boundary.

Validation includes actual ordinary-role PostgreSQL scope and column privileges,
unbound/bound cross-company pairs, pending/confirmed keys, naturally expired provider
credentials, short access expiry, current revocation and hard expiry, actual completed
LEGACY transfer with original inactive company, and archive restore retaining key
provenance. The typed path has actual PostgreSQL coverage plus bounded/malformed
acquisition and cancellation tests. Exact results and source continuity are recorded in the
[acceptance measurement](../measurements/collaboration-space-key-recipients-2026-11-04.json).

This storage does not expose a Matrix key endpoint. Gateway queries/claims/to-device
must still lock the required accounts before native/device guards and revalidate
current caller, selected recipient set and room authority around every provider
outcome. Recipient key signatures, content/sync/push and full Mobile SDK adoption
remain separate required work; group rollout stays disabled.
