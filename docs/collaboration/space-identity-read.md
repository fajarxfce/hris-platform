# Reading the selected organization identity

`GET /api/v1/collaboration/spaces/{spaceId}/identity` returns the account's already
selected provider-user intent under current native/group/client admission. It
returns `{identity: null}` when no selection is stored, including when an eligible
legacy preference exists. Only the separate explicit POST selects an identity.
The GET has no operation key, receipt, audit/outbox write, device registration,
provisioning work, provider I/O or credential issuance.

A retained identity is immutable account/space metadata, not a device or provider
access grant. The native read is independent of selected HR company. Its shared
account guard serializes against identity/account changes, its exact native parent
is locked, and current membership/security/client admission is reacquired after
the coherent selection snapshot for either result. A received access failure,
wall rollback, the original 30-second wall deadline or thread interruption cannot
become a successful late read. Existing PostgreSQL transaction/statement/lock/idle
timeouts still bound database work; no nested transaction or retry loop is added.

Only the retained branch of the existing pure identity policy is evaluated. It
validates account/space/version, original provider user and current configured
realm; a changed realm remains an explicit identity-migration conflict. An absent
selection never exposes a proposed preferred user as if it were already selected.
Expired/retired legacy devices do not erase the account's retained user identity;
this read alone cannot reactivate or adopt any such device.

The private, no-store HTTP projection contains spaceId, accountId, serverName,
matrixUserId, origin, legacyCompanyId, version and createdAt. It excludes the
preferred original device ID, native proof, keys, tokens and request/worker state.
Several devices can use one Matrix user: Mobile must match the selected user to
its actual local SDK identity, not assume the preference's device is this phone.
The legacy company ID does not grant HR access there. Provider metadata must
never select a new HTTP origin or override application endpoint configuration.

Five new PostgreSQL cases and one new HTTP case pass, with the existing native
transport guard extended to GET identity. Focused domain/PG: 16 cases /
55.207 s; actual API context: 18 cases / 105.251 s.
Full worker: 137 passed, 1 existing opt-in elapsed-retention skip /
375.238 s. All final gates preserve the same 8,506 Git-enumerated
build/application inputs; format and architecture pass. All sessions closed.
[Evidence](../measurements/collaboration-space-identity-read-2026-11-05.json).

Next: implement Mobile transport and finite current-parent acquisition for this
metadata, then durable registration/retry/bootstrap commands that retain original
SDK user/device/key provenance. Local SDK identity port 5860290e is available on
an already opened Android runtime; unavailable/null observations never establish
that old stores are absent. Closed-store inspection, explicit hard-parent recovery,
new SPACE inventory, current group runtime ownership, UI and typed push/background
replies remain outstanding. No live deployment, provider/FCM, phone installation,
Apple runtime or performance acceptance is implied. All 54 PRD groups stay open;
live backend8b40/schema116 and phone release29/source084662c5 are unchanged.
