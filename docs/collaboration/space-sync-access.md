# Current native group sync

`AccessNativeCollaborationSpaceMatrixDevice.sync` admits one fixed Matrix sync operation
under the existing native/group gateway/device/provider and completed-retained-device
rules. It collects the bounded published inventory plus all owned original rooms for the
canonical original user. Failed or oversized acquisition never falls back to a partial
scope. New-send recipient eligibility and key publication do not erase owned history.

The full owner and complete original/published room tuples are captured and checked before
and after provider work. Both provider outcomes are withheld if current authority or the
scope changed. Cursor open/seal and provider long polling run outside SQL. A final current
checkpoint follows cursor sealing, so revocation during crypto work cannot release an
apparently valid continuation. Cancellation/interruption retain their behavior and release
only the exact request lease. The original deadline is never extended.

Same-scope encrypted positions resume with the original provider since/filter. A changed
scope initializes a fresh bounded sync; an authenticated original-company cursor does so
only for the exact retained identity. A fresh HRISG device on an original user can read
its owned original rooms but cannot inherit the previous device's cursor or key queue.
Sync never replaces the SDK crypto store or rewrites Matrix user/device identity.

The standard `/_matrix/client/v3/sync` endpoint dispatches valid group gateway tokens to
this use case. Existing strict query/header/body parsing and no-store responses remain
in force. New group sync ports bind in the owning module with an explicit disabled
provider fallback. Both API and worker contexts must validate the shared DI graph.

Native PostgreSQL tests exercise empty/growing/stable scopes, exact metadata changes,
revocation around successful/failed provider calls and crypto open/seal, capacity and
acquisition failure, cancellation, deadline expiry and peer offboarding. Existing original
history cases now also exercise native sync over original-only inventory, retained cursor
initialization, fresh-device isolation, cancellation and denial after native revocation.
Actual HTTP/SQL tests cover expanded scopes, encrypted history framing, another device's
cursor, peer offboarding, strict request authority and late native revocation. The private
HTTP provider is an explicit fixture; actual Synapse protocol evidence is separate.

This remains a backend foundation. Group room metadata/join/receipt and push integration,
actual SDK queued-key authentication/decryption and ownership transition, hard-parent
recovery and the remaining communications PRD are unfinished. Group rollout stays disabled;
all 54 PRD acceptance groups remain open. Live schema116 and normal phone release28 stay
unchanged; no unchanged or proof APK is installed on the user's phone.

See [accepted gate evidence](../measurements/collaboration-space-sync-access-2026-11-04.json).
