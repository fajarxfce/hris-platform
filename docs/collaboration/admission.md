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
