# Retained device public-key identity

Schema 112 reserves one public-key fingerprint per HRIS company/account/device
before a provider upload can write keys. An uncertain or cancelled upload may
already have succeeded at Matrix; its next attempt must retain that identity.
A different identity requires an explicitly enrolled/recovered device. Ordinary
session expiry, parent cleanup and revocation retain this historical metadata.

The row starts at version zero with a reservation time. Its only transition is
to version one with the first confirmed publication time. Company, account,
device, fingerprint and reservation time are immutable. Both writes require an
active owned device. Forced RLS restricts reads and writes to the selected company
and owner, including runtime roles granted broad table privileges. There is no
peer projection, new privileged lookup, delete grant or parent-session foreign key.
A trigger also rejects mutation/deletion through the migration owner.

The repository exposes an explicit per-device transaction guard after the native
parent/device/gateway guards, a scoped read, reservation and exact-identity
publication. Competing initial uploads must acquire that guard before inspecting
or reserving a candidate. Publication compares the reservation identity and time
at version zero. No SQL guard may remain held across a provider request.

This storage does not establish current HRIS admission, verify a signature, or
trust a device for E2EE. The gateway use case must bound/validate the typed upload,
compute the fingerprint from authenticated public identity, recheck current
company/session/device authority around each provider exchange, and confirm the
same public identity by readback before publication. It must refuse a conflicting
pre-existing Matrix device key instead of overwriting it. No private key belongs
in this table or on the server. Cross-signing, verified recovery and visible key
changes remain client requirements.

Five actual PostgreSQL cases cover owner/company isolation, wrong/inactive/revoked
devices, immutable identity, competing reservation guards, failed-result and
interruption rollback, retained uncertain reservation, ordinary parent cleanup,
and real dump/restore of both pending and published rows. The fixture upgrades
111 to 112 and checks existing account snapshots are unchanged. Recovery accepts
schema 112 and rejects future schema 113; there is no new secret envelope format.
See [measurement](../measurements/collaboration-device-key-storage-2026-10-31.json).

The [own-device key gateway](device-key-upload.md) now applies these fences.
Remaining content and SDK integration follow separately; a reservation or
publication row alone never grants room access or establishes recipient delivery.
