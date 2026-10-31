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

Next implementation work is the registration/admission use cases, provisioning
intent/outbox, protected session broker and provider gateway. The adapter must
read current company/member/account/session/device authority under the established
lock order. It must apply company maintenance/module/build admission, protect
encrypted provider credentials, and deny direct provider login/admin/federation
access. A live Matrix request cannot be admitted solely by an earlier bootstrap
or by eventual cleanup. Native/browser session replacement must retain owned
local facts while pausing denied network operations.
