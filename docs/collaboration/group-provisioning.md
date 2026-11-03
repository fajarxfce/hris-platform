# Organization device provisioning

The native selection and registration commands commit identity intent, current
binding and journal/receipt. Fresh device registration now also enqueues bounded
provider work atomically. They are unwired. The typed internal Matrix
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

V122 now provides an initially empty organization provisioning queue. It retains
one operational head per group device and the immutable original native parent;
there is no native-session cascading foreign key. Existing background_jobs remains
company-only. Native registration now inserts its initial head; the worker is not
wired yet.

Each request records native version, account credential version, authentication/MFA
timestamps, correlation ID and native client platform/build. Insert/retry requires
the exact current bound parent and a live device. Requests expire within five
minutes and within the parent access/hard deadline. These are intent/provenance
checks, not a future admission grant: a global worker claim does not check current
native/group/client/MFA authority and cannot authorize provider I/O or content.

The worker-only claim function returns at most eight heads, with at most sixteen
live leases globally. A lease lasts at most sixty seconds, clipped to request
expiry. Attempts are capped at eight; an expired final lease becomes a retained
ATTEMPTS_EXHAUSTED outcome without another provider claim. Request expiry becomes
REQUEST_EXPIRED. Each call terminalizes at most eight expired requests and eight
exhausted leases using separate indexed ranges, deduplicating overlap. This is
operational metadata only; the function never changes devices, credentials or keys.

A late worker must match request revision, attempt, owner, token and deadline before
publication. Owned cancellation invalidates an in-flight lease, including after
offboarding or parent purge. Retry advances the request revision after a terminal
outcome, supplies current native proof, resets attempts and retains the prior
terminal outcome while the new request is pending. Heads cannot be deleted or
rebound to a replacement parent. The head retains only the last operational outcome;
native/worker journal integration must preserve required historical business events.

Typed request/head/lease/outcome ports and PostgreSQL adapters are now implemented.
Metadata distinguishes missing, pending and prior terminal outcomes. Completion and
defer compare every typed request proof field, revision, attempt, owner, token and
exact live deadline. Mapping validates timestamp precision, lifecycle coherence and
claim bounds/uniqueness. These ports still require caller-owned current admission.

Native registration now inserts its request atomically with device/binding/receipt/
journal. It re-observes native authority and clips request life to at most60 seconds
and current access/hard/MFA expiry. Replay, including after native refresh, preserves
the original work instead of silently restarting it. Next implement explicit retry
and admitted worker use cases. Retries need their own receipt and current admission;
lost-response replay cannot silently start another attempt.

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

## Validation

[Schema/queue evidence](../measurements/collaboration-space-provisioning-queue-2026-11-03.json):
104 regression cases pass in464.322s with3652 unchanged checked inputs; recovery-tool14
cases pass in0.011s. Actual PostgreSQL covers concurrent claims, batch/global limits,
lease replacement, eight-attempt exhaustion, bounded expiry, fresh retry and archive
restoration. Queue lease/owner fields are internal and must not appear in native
status responses. Native/worker use-case integration and historical business journal
coverage remain outstanding.

[Typed port evidence](../measurements/collaboration-space-provisioning-ports-2026-11-03.json):
17 cases pass in102.360s with3658 unchanged source hashes and quality checks. Actual
PostgreSQL covers every lease proof fence, null/verified MFA, due defer/reclaim, prior
outcomes, owned cleanup and restored worker claims. Failed Result, cancellation and
interruption roll back encrypted credential, device and queue writes together; a
positive transaction commits all three. This is storage atomicity, not acceptance
of worker admission, business journal or provider publication use cases.

[Registration enqueue evidence](../measurements/collaboration-space-registration-queue-2026-11-03.json):
18 cases pass in106.830s with3658 unchanged source hashes and quality checks. Six
actual native command cases include exact proof/deadline, native-refresh replay,
shared quota, retained identities and late admission changes. Failed Result,
cancellation and interruption after device/queue/receipt/journal writes each roll
back the entire registration. The first run required a fixture-only device UPDATE
grant for V122 FOR SHARE; production policy was unchanged.
