# Organization device provisioning

The native selection and registration commands currently commit identity intent,
current binding and journal/receipt. They are unwired. The typed internal Matrix
candidate port is implemented and tested against an owned Synapse process; it does
not supply a public login endpoint or activate an HRIS group device.

`CollaborationSpaceMatrixLogin` carries UUID coordinates from admitted canonical
and device records. It derives a new `hrisg_<space>_<account>` user, or retains the
canonical legacy company's `hris_<company>_<account>` user. A new device has a
`HRISG_<group-device>` ID; an explicit retained device keeps its original `HRIS` ID.
Unrelated retained/company coordinates are invalid. JWTs remain capped at60 seconds
and provider tokens at10 minutes. The existing company/room admission policies are
unchanged; supporting provider formatting is not permission for a group exchange.

[Evidence](../measurements/collaboration-space-matrix-login-2026-11-03.json): 28 cases
pass in53.772s with3637 unchanged hashes and quality checks. Actual old/new/retained
identities, key publication/readback and restart preserve original public keys.
The old transport/logout regression still passes. This is not proof of Mobile SDK
private-key/store continuity or cross-company HRIS messaging.

## Required worker and persistence contract

Group provisioning needs an explicit organization queue. Existing background_jobs
requires a real HR company; using a synthetic company or space UUID would violate
its authority and foreign keys. A feature-owned bounded lease/fence queue can follow
the existing communication push-worker ownership pattern. Native registration must
insert its request atomically with device/binding/receipt/journal. Retry requests
must preserve history and fence older attempts; they require current native proof.

A worker claim transaction acquires only queue metadata and commits before native
admission. Preparation/publication then take account, native parent, device and
queue-head guards in that order, followed by credential access. Native registration
already holds account/parent guards before enqueueing: taking the queue first and
then waiting for the account would introduce a lock inversion. Verify current
group/client/MFA/proof/canonical identity and capture a short observation with
expected device and credential generation. The
provider call runs without SQL locks. Publication rechecks the entire authority,
lease token, attempt and generation before atomically storing the credential,
activating the device and completing the request. Bound queue size, active workers,
attempts, lease durations, query windows and cleanup; retain failed outcomes.

V121 and the internal [group credential repository](group-credentials.md) now provide
a distinct authenticated purpose, immutable native/provider ownership and exact
generation/expiry binding. Terminal generation and device revision1000000 are
reserved for irreversible cleanup. Real current/retained-key envelopes and the
production recovery projection survive an actual PostgreSQL archive restore.
The native/worker publication flow is still unwired; these persistence checks do
not grant current group/content access or permit reusing a retired credential.

## Retained devices and runtime transfer

Synapse logout deletes all tokens and keys for a device, including a newer winning
login. A losing or cancelled candidate must expire within its bounded lifetime;
do not call logout to clean it up. Participant E2EE private keys stay on the client.

Retained-device promotion requires an explicit transfer/mapping fence that stops
competing old/new runtime ownership without replacing Matrix user/device/key IDs.
Current registration requires an active/unretired source at completion. Do not
retire that source and then bypass this rule implicitly; completed transfer needs
its own reviewed admission and immutable key/store references. Recheck old gateway
requests in flight when authority changes. A public fingerprint is not private-key
possession: the client must reopen and verify the exact retained SDK store.

Preserve pending message transaction identities and original owners. Inspect the
existing MatrixTransactionInteropTest and durable worker recovery before redirecting
pending commands; provider token rotation and request replay affect deduplication.
Legacy inbox/outbox/SDK data must remain explicit until the new mapping is accepted.
The complete change includes room/gateway admission, Mobile store and owner lifetime,
drafts, incoming/outgoing work, push/deep links and reads. Accept only after the
cross-company end-to-end scenario in identity-migration.md succeeds.
