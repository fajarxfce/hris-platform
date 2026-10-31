# Native room discovery, joining and state

The fixed native Matrix gateway now supports the reads required to open an HRIS
DIRECT conversation. Room creation remains an HRIS command and fenced worker job.
The gateway never accepts a provider URL, room alias, arbitrary state mutation or
third-party join authority.

| Endpoint | Contract |
| --- | --- |
| `GET /_matrix/client/versions` | Enabled service metadata, wire version `v1.12`, no unstable feature advertisement. |
| `GET /_matrix/client/v3/capabilities` | Current native gateway admission; room version 12, account mutations disabled. |
| `POST /_matrix/client/v3/rooms/{roomId}/join` | Published owned binding and an empty JSON object. |
| `POST /_matrix/client/v3/join/{roomId}` | Same fixed room-ID operation; aliases remain rejected. |
| `GET /_matrix/client/v3/rooms/{roomId}/state` | Mapped full client state events for the retained DIRECT pair. |
| `GET /_matrix/client/v3/rooms/{roomId}/state/{type}` | Content for the fixed create/encryption/history/join/guest/power state types, empty state key only. |
| `GET /_matrix/client/v3/rooms/{roomId}/members` | Complete DIRECT member state, optionally at the caller's sealed sync position. |

Discovery does not authorize Matrix login, registration, room creation, sliding
sync, backup or calls. Client-side account changes remain in HRIS. All responses
are `no-store`; unknown room/state results now use Matrix `404 M_NOT_FOUND`.
Authenticated routes reject GET bodies, unknown/duplicate queries,
native/cookie/URL credentials and nonempty join objects. Join query parameters, including federation routing
selectors, are not exposed by this first adapter.

## Authority and historical membership

A new bounded single-room projection joins existing schemas 110/111 under their
forced RLS. It selects the current account/company, configured Matrix server and
exact room ID. The use case validates the returned ownership and captures the
immutable binding; no new migration or privilege is introduced. Unpublished,
unknown and foreign rooms never reach provider I/O. Both participants retain
their historical room while the peer is inactive; current caller admission is
still mandatory. Future message/key recipients have their separate active policy.

The native operation owner checks account/company/CHAT, MFA, build, parent,
device, gateway head and exact binding before and after provider I/O. Join is
provider-idempotent; a late revoked caller cannot receive a successful response
even if the provider already accepted the join. No SQL transaction spans I/O.
The shared preparation runner also checks captured admission after a failed
resource lookup, so elapsed admission takes precedence over a stale not-found
or storage error. Failed transactions still roll back normally.

`members?at=` accepts an encrypted **sync** cursor for this account, company,
device and Matrix server. The gateway opens it before I/O and sends only the
private provider position. It does not replace a historical position with current
membership. Scope growth does not erase an existing room's retained history;
the provider still enforces its own history visibility at the requested position.
Malformed or foreign-device cursors cannot reach the provider. Raw provider
positions are not accepted as this public parameter.

## Mapping and resource ownership

Room reads/join use the short control transport, separate from the one-per-device
long-poll lease. Joining or loading state must remain possible while sync waits.
Requests use fixed paths, escaped IDs, no redirects, the existing 32 immediate
control permits and five-second/captured-admission deadlines. State/member
responses are bounded to one MiB; join retains the 64-KiB control response limit.

Sync and room reads share the same DIRECT event mapper. It checks room/sender,
creator, v12/federation/encryption metadata and pair membership, while preserving
full versus stripped event framing. State reads reject timeline events and
duplicate type/state-key entries. Members are bounded to the two canonical
participants. Full client events retain their room context; encrypted body bytes
are not decrypted or represented as cryptographic acceptance. A join response
must contain only the exact expected room ID.

## Evidence and continuation

[Measurements](../measurements/collaboration-room-access-2026-10-31.json) retain
source hashes, actual PostgreSQL/HTTP/Synapse coverage and full application graph
checks. Tests cover repeated join, old versus current membership, read ownership,
retained inactive-peer history, wrong room/member responses, invalid cursors,
late lookup expiry and revocation after provider acceptance. An actual HRIS job
publishes a real Synapse room before the gateway joins/reads/syncs it and resolves
member history through its sealed cursor.

Continue with owner/room-bound pagination, encrypted room sends and the remaining
fixed SDK discovery surface, then the actual pinned Android/web client through
HRIS. Durable outbox, logical-message reconciliation, UI and the rest of chat,
calls and meetings are not accepted by these transport tests. The live schema-103
runtime and phone remain unchanged.
