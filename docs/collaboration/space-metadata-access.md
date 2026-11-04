# Current native group room metadata and receipts

The native group gateway now exposes managed `m.direct` hints, room join, state,
membership and public/private read receipts for the complete original/published room
inventory. These routes retain the current native session, group device, gateway and
provider admission rules, including completed migration proof for retained devices.
They neither create a conversation nor grant new-send authority to an offboarded peer.

Direct hints use the exact peer Matrix identity. An original company identity and a
new group identity remain distinct; exact adoption of an original room does not add a
duplicate. Reading or confirming hints requires the caller's own Matrix user. A PUT
only confirms the currently managed hints; it cannot replace server ownership. The
complete bounded room scope is rechecked before releasing either result.

Room operations pin the original or published immutable tuple. Current authority and
that tuple are checked around both provider outcomes and after the final operation.
The shared provider reader checks room identifiers, original creator, member pairs,
bounded state arrays, duplicate state keys and allowed event shapes. Both company and
group adapters reuse this protocol; each validates its own domain frame before I/O.

Membership `at` positions are authenticated outside SQL with the group sync cursor
codec. Only its private provider position reaches Matrix. An authenticated retained
company cursor requires a fresh group sync (`collaboration_sync_reset_required`);
it must not silently substitute current membership for a requested historical view.

Read receipts accept only `m.read` and `m.read.private`. Before posting a receipt,
the gateway obtains and maps the exact event through the same owned-history protocol
as the event endpoint. It checks current admission between that proof and the write,
and after the write, within one original deadline. Revocation after proof prevents
the receipt; revocation during provider work withholds the acknowledgment. A provider
receipt success alone does not prove another device received or decrypted a message.

No network or cursor crypto runs under the SQL transaction. Cancellation/interruption
propagate, finite request leases are released precisely and deadlines never extend.
Strict existing HTTP parsing dispatches group tokens to these operations. Shared DI
is validated in both API and full worker application contexts.

PostgreSQL tests cover exact current ownership, managed hints, cursor isolation,
event proof, metadata races around both provider outcomes, revocation, offboarding,
deadline expiry and resource cleanup. Existing original migration cases additionally
exercise original metadata and receipts before adoption and after parent purge.
Actual Synapse tests preserve the original creator, compare membership before/after
joining, restart the provider and verify public/private receipt projection. API tests
exercise the real controller, SQL and strict input boundary using a private provider
fixture. These tests do not establish SDK Olm/Megolm decryption or product acceptance.

Group push integration, actual SDK queued-key authentication/decryption, Mobile store
ownership transition and native hard-parent recovery remain required. Group rollout
stays disabled and all 54 PRD acceptance groups remain open. Live schema116 and normal
phone release28 are unchanged; this backend foundation does not produce a product APK.

See [accepted gate evidence](../measurements/collaboration-space-metadata-access-2026-11-04.json).
