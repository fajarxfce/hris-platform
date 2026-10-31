# HRIS collaboration admission

The collaboration domain now defines a device bound to **company, account and
HRIS native session**, plus a short-lived admission observation. These types
contain neither participant private keys nor provider credentials. They are
internal policy inputs, not a public bearer-token contract.

The policy checks both captured and current chat permissions, current account,
company and membership, credential version, parent session version/revocation,
parent access expiry, device state/version and exact ownership. Provisioning,
revoked, missing and future-created devices cannot acquire a grant. Voluntary
and required MFA keep their existing requirements. `collaboration.chat` is an
assignable employee permission; no migration or runtime operation rewrites an
existing membership or role-template snapshot.

An observation expires after at most **60 seconds**, and earlier if the parent
session, access token or MFA proof expires. The exchange policy rejects the exact
deadline, clock reversal and replacement company/account/device/session/credential
versions. Synapse's 120-second JWT clock tolerance does not extend that deadline.
External provider I/O must be followed by reacquisition of live admission before
any result is exposed. An observation alone never proves current authorization.

Eleven policy tests cover ownership, changed permissions, disabled access,
pending/revoked devices, parent rotation/expiry, MFA deadlines, provider clock
tolerance and late results from replaced devices. All 35 identity-domain tests
also pass; architecture checks pass. These checks establish the policy boundary,
not the delivery of a usable chat feature.

Schema 104 and the transaction-backed registry repository now retain device
identity independently from parent-session bindings. Composite foreign keys
protect company/account ownership. Session purge removes the binding while
retaining device identity; a missing live parent can never admit a provider
request. Only the owner reads these records, with writes additionally restricted
to the current company. Owned cross-company metadata supports a global count of
distinct live parent sessions. A failed binding insert rolls back device creation.

Actual integration tests cover the distinct-parent count, pending devices,
cross-account rejection, revoked parents, refresh expiry versus parent expiry,
credential rotation and rollback. All 28 database cases pass, including actual
`pg_dump`/`pg_restore` with restored facts, row policies and immutable transitions.
The recovery schema ceiling is 104; no new encryption envelope exists yet. See
[recorded evidence](../measurements/collaboration-device-registry-2026-10-31.json).

Next implementation work is durable provider provisioning, encrypted provider
credentials, API wiring, protected session broker and provider gateway. The adapter must
read current company/member/account/session/device authority under the established
lock order. It must apply company maintenance/module/build admission, protect
encrypted provider credentials, and deny direct provider login/admin/federation
access. A live Matrix request cannot be admitted solely by an earlier bootstrap
or by eventual cleanup. Native/browser session replacement must retain owned
local facts while pausing denied network operations.

## Company availability

Schema 105 introduces the `COLLABORATION` policy key, independent of
`COMMUNICATIONS`/Inbox. The dashboard's existing policy editor and read view show
“Chat, calls and meetings” / “Chat, panggilan, dan rapat”. Current revisions keep
their recorded values; administrators use a normal new revision to disable the
new module. This availability switch does not enable an absent provider service
or bypass chat permission, session or device admission.

The module vocabulary permits at most nine unique, known, non-null values. Web
parsing uses that vocabulary's length, while Mobile preserves its bounded handling
of additive unknown keys. The migration's actual backup/restore test checks both
the new function and unchanged historical revisions; recovery ceiling 105 adds no
new encrypted-data contract. Settings behavior passes 82 unit and 13 browser
cases; the native policy change passes 37 workspace cases and Android assembly.

## Native registration and admission transactions

`RegisterNativeCollaborationDevice` now owns idempotent registration. Its lock
order is operation → client policy → company → members → account → native parent
→ device. The exclusive account guard serializes a global five-parent quota with
native creation/refresh/revocation. Registering the same parent in another company
keeps a separate company identity without consuming a sixth slot. Provisioning
rows count; expired access does not release a still-refreshable parent's slot.

A duplicate operation returns the original receipt after fresh authorization.
Its fingerprint excludes the parent version, allowing a lost-response retry after
a legitimate token refresh. A new operation on an existing binding records only
a receipt. Device creation, binding, receipt, audit and outbox commit together;
permission, access expiry and company availability are checked again after writes.
Revoked devices cannot be reactivated through registration.

`AcquireNativeCollaborationAdmission` locks the native parent before reading its
binding, rechecks live company/session/permission/MFA/device authority, and clamps
the deadline to the next company policy/maintenance boundary. If a scheduled
successor becomes effective during lookup, the observation is rejected for retry.
Native identity/version come from authenticated transport; neither is accepted as
client authority. Browser metadata cannot bypass native build policy.

Ten actual PostgreSQL scenarios plus the registry regression and eleven domain
cases pass, including competing registrations, concurrent replay, account-wide
quota, revocation during waiting, post-write rollback and policy timing. See
[registration evidence](../measurements/collaboration-native-registration-2026-10-31.json).
The schema-105 control CI also passed both jobs in
[41184251989](https://github.com/fajarxfce/hris-platform/actions/runs/41184251989).
Browser-parent admission, provider credentials, provisioning and actual chat
delivery remain required.

## Native HTTP control

`POST /api/v1/companies/{companyId}/collaboration/native-device` accepts an
`Idempotency-Key` UUID and `{ "platform": "ANDROID", "label": "My phone" }`.
The authenticated native bearer supplies parent identity/version. Cookie sessions
cannot use this route, and unknown body fields cannot replace server authority.
The company path, current `collaboration.chat` permission, client-build headers and
company availability are independently checked. A successful receipt contains
only device `id` and `version`; it is neither a Matrix credential nor evidence that
asynchronous provisioning has completed.

The collaboration configuration now composes the same domain/data use cases in
the API and worker. `HRIS_COLLABORATION_ENABLED` defaults to false, producing
`collaboration_unavailable` before registration writes. The current HTTP fixture
enables it only for isolated control-path acceptance. Enabling this switch alone
does not configure Matrix or deliver chat. Existing membership grants still need
an explicit normal permission update; the switch never assigns access.

Seven HTTP scenarios pass with real bearer/cookie middleware and PostgreSQL. They
cover explicit membership grant, cookie/malformed-token rejection, immutable
parent selection, unknown fields/idempotency, replay, revocation, independent
Inbox/collaboration availability, minimum builds and disabled deployment. All 69
existing OpenAPI contract cases pass. The shared worker context passes 104 cases;
its existing opt-in one-hour retention case is skipped. Formatting, architecture
and Compose configuration pass. [HTTP evidence](../measurements/collaboration-native-http-2026-10-31.json)
retains the initial fixture permission, Jackson-array mapping and HTTP-status
expectation failures; production permission checks and 422 validation status were
preserved. No live stack or phone was changed.

## Internal Matrix credential storage

Schema 106 and `EncryptedCollaborationProviderSessionRepository` retain internal
Matrix credentials using the existing AES-256-GCM identity keyring. Persisted AAD
is `hris:matrix:{company}:{account}:{device}:{generation}`. SQL fixes the Matrix
user namespace to the company/account and the Matrix device ID to its HRIS device.
These are provider access tokens; this table contains no participant E2EE keys.
The domain value redacts its string representation. Public controllers never
return this internal value.

Credential rotation uses an observed generation. A revoked device's secret may
be cleared while retaining its terminal generation and provider identity; deletion
and reactivation are forbidden, preventing delayed cleanup from acting on a new
generation zero. The final device/credential revision is reserved for irreversible
revocation/retirement. Callers must lock account/device before credential rows and
own the current policy and transaction. The repository itself grants no access.

Six PostgreSQL scenarios cover concurrent rotation, identity/generation tampering,
rollback, retirement and revision exhaustion. Actual `pg_dump`/`pg_restore` retains
both old-key and current-key envelopes, and the complete restored Matrix envelope
projection decrypts using the retained keyring. Recovery query selection preserves
older schemas without the new table; 22 recovery-tool cases pass. All 29 database
cases, eleven registration/registry regressions, eleven admission cases, formatting
and architecture also pass. [Storage evidence](../measurements/collaboration-provider-storage-2026-10-31.json)
records these boundaries. The store is not wired to provider I/O/provisioning yet;
registration remains pending. No provider credential or live runtime was changed.

## Bounded internal provider transport

`InternalCollaborationMatrixRepository` coordinates JWT signing and fixed-origin
HTTP sources outside SQL transactions. HS256 claims carry exact company/account
subject, issuer/audience and at most 60 seconds of HRIS observation. The adapter
checks the strict deadline before sending; Synapse's JWT tolerance grants no extra
HRIS authority. The client cannot choose another URL or follow a redirect.
Requests include body deadlines, cancellation and 64-KiB response limits; JSON
duplicate/trailing data, invalid credentials and unexpected encodings fail closed.

Internal login asks for nonrefreshable provider credentials and requires an
`expires_in_ms` value of 1–600,000, without a refresh token. Deployment must set
Synapse `nonrefreshable_access_token_lifetime: 10m`. The candidate expiry is
conservatively measured from request start, and its bearer is never a public
response. A later use case must recheck HRIS authority, expected provider identity
and its job/generation fence before storing it. Schema 107 now persists that expiry as described below; transport still awaits
provisioning/gateway wiring.

**Synapse logout is device deletion.** The pinned provider's
[`LogoutRestServlet`](https://github.com/element-hq/synapse/blob/v1.164.1/synapse/rest/client/logout.py)
calls `delete_devices` for a device-bound token. Its module API's
`invalidate_access_token` does the same. Do not use either to compensate a losing
login or a late result: even an expired bearer can delete the winning replacement
credential and E2EE device keys. Losing tokens stay private and expire; bounded
job attempts prevent unbounded orphan creation. `logoutDevice` is explicitly a
terminal operation, permitted only after irreversible HRIS device revocation.

Eight transport cases and a real Synapse scenario pass (35s), with formatting and
architecture checks. The provider scenario uses five-second credentials, actual
signed public device keys, replacement login, expiry, key query and permanent
logout. It verifies both retained keys on ordinary expiry and removed keys on
logout. [Transport evidence](../measurements/collaboration-matrix-transport-2026-10-31.json)
records the source/image and corrected compiler/formatting failures. The transport
is not yet wired to API/worker DI; live runtime and phone remain unchanged.

## Persisted provider deadlines

Schema 107 adds `token_expires_at`. A legacy schema-106 bearer retains its unknown
NULL deadline for recovery or terminal retirement. It cannot satisfy
`collaborationProviderCredentialIsCurrent`; a successful bounded rotation is
required before live use. Reads retain expired/legacy metadata so cleanup does
not confuse freshness with ownership. New inserts/rotations require a finite
deadline after the write and at most ten minutes later. Retirement preserves
that recorded deadline while clearing the bearer.

For known deadlines the encryption purpose appends
`:expires:{unixMicroseconds}` to the schema-106 identity/generation AAD. The
repository normalizes to PostgreSQL microsecond precision before encryption and
storage. Changing the deadline independently of the ciphertext fails
authentication. NULL deadlines preserve the original AAD, so schema-106 backups
remain readable. The recovery tool selects the matching SQL projection by
schema; schemas 59–105 never reference Matrix tables and 106 never references the
new column.

The expiry storage fixture migrates a real legacy ciphertext through 106→107,
then exercises bounded writes, rotation, unknown/expired/future freshness,
expiry tampering, concurrency, rollback and terminal retirement. Actual dump/
restore verifies the complete three-envelope projection: one legacy deadline
and two known deadlines, across old/current encryption keys. Provisioning and
public gateway integration remain the next work.

All eight credential scenarios, eleven admission cases, 29 core database cases,
formatting and architecture passed in 2m27s. Twenty-two recovery-tool cases pass.
[Expiry evidence](../measurements/collaboration-provider-expiry-2026-10-31.json)
records the tested source and schema-107 restore. The live database remains 103.

## Durable native provisioning queue

Schema 108 links each device to its current `COLLABORATION_PROVISION` job. The
immutable job request carries the account/company, parent session, device,
platform and optional observed client build. The pointer has its own monotonic
version. Its row policy admits only the selected company and owner; constraints
reject foreign job/device/parent substitutions, deletion, and replacement of
an unfinished or successful job. Parent-session purging removes its ordinary
binding but retains this provisioning fence.

Registration now creates device, parent binding, durable job, pointer, operation
receipt, audit and outbox in one transaction. It takes the existing bounded
company job-queue guard after device/account guards. A full 100-job queue or lost
post-write authorization rolls all new rows back. Duplicate operations and
registration after ordinary native refresh reuse the same device/job. A retained
pending device without a pointer can enqueue once under fresh authority without
replacing its identity. Existing failed jobs are not silently reset by receipt
replay; an explicit bounded retry use case remains to be implemented.

The device projection now retains its immutable platform. Registration and
admission reject contradictory Android/iOS metadata before queueing or returning
a grant. The dashboard translates the new job kind with its existing job UI.
API and worker DI share the queue repository and registration dependencies.

Actual tests cover concurrent enrollment/replay, queue capacity, seven-table
rollback, legacy pending enrollment, platform mismatch and PostgreSQL dump/restore
of the immutable request and pointer, including restored owner/company isolation
and retained identity after parent purge. Worker execution, terminal activation,
provider admission/gateway and product chat remain the next implementation. The
current worker only leases its registered task kinds and does not yet execute the
new provisioning kind. No provider or live runtime is enabled by this increment.

Final admission/registration selection: 13 data cases, eleven domain cases, 76
HTTP/contract cases, and the full worker context (104 pass, one existing opt-in
elapsed-retention skip) pass in 4m31s. The unchanged schema-108 selection passed
all 29 database and two jobs-domain cases earlier (2m35s); 22 recovery cases and
14 dashboard cases/type checking also pass. [Queue evidence](../measurements/collaboration-provisioning-registration-2026-10-31.json)
retains the corrected fixture constructor failure and tested sources.

## Fenced native activation

`AdvanceNativeCollaborationProvisioning` prepares under the job lease, client
policy, company/member/account, native-parent, device/pointer and credential
guards. It reads current parent assurance and intersects the captured chat grant
with live HRIS authority. The immutable job's parent/device/platform/build must
match the bound identity. Provider I/O happens after that transaction releases
its guards. The result then passes the same live checks, original grant deadline,
lease token, pointer, device version and provider generation before publication.

The final transaction encrypts the bounded internal credential, activates the
device, completes job progress/status and records audit/outbox together. A late
response cannot publish after another worker wins. No failure path calls Matrix
logout; unpublished candidates expire behind the private provider network. A
current stored credential may be reused; unknown/expired legacy credentials
require bounded replacement. Native refresh or an obsolete scheduled-policy
observation returns a retryable observation failure; actual permission, MFA or
module denial stays final under the existing eight-attempt worker policy.

`AbortCollaborationProvisioning` stops only the owned lease, recording failure or
cancellation without deleting the device or its E2EE identity. Explicit retry
and permanent device revocation remain separate forthcoming commands.

Nineteen data cases (18 registration/activation and one registry regression) and
eleven admission cases passed in 49s. The later retry-observation refinement
passed its new policy-boundary case in 35s, with formatting and architecture.
[Activation evidence](../measurements/collaboration-provisioning-activation-2026-10-31.json)
records the two selections and their limits. These tests use real PostgreSQL and
a controlled provider repository; actual provider transport is separately
verified. Next wire provider configuration, the worker adapter and their actual
application contexts; the use cases are not yet registered as a running task.
