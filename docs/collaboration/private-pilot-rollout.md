# Private collaboration pilot rollout

The isolated provider services are now running and verified as described below;
HRIS integration and migration remain pending. The existing private API/worker
use2c2a59e3 and schema103; the candidate uses2124c6fc
and schema116. Both Collaboration control jobs41437984098 pass (652 active JVM
cases in uploaded reports, six opt-in native/elapsed cases skipped). Full ten-job
Verify41442782804 is in progress. Preserve its exact source and audit the artifacts.
[Preparation evidence](../measurements/chat-private-pilot-preparation-2026-11-02.json).

1. Stage a persistent internal Synapse1.164.1 with separate PostgreSQL18.6,
   pinned image digests, UTF8/C collation, bounded resources/logs and an internal
   Docker network. No provider/database host ports, federation, open registration,
   password login, URL previews or public callback. An operator-owned stable server
   name binds future Matrix identities; preserve it and the signing/DB keys.
2. Keep protected config/JWT files outside Git and separate from active dev.env.
   API/worker use the same HS256 secret and issuer/audience as Synapse. Runtime
   UID10001 receives only read-only private files; original host credentials remain
   protected. Synapse nonrefreshable tokens must expire within10min. Clients use the
   existing HRIS WireGuard gateway, never a provider URL. Do not enable chat push
   callback, pusher or outgoing FCM flags before notification/actions acceptance.
3. Preserve all existing Compose documents/WireGuard/push overlays, identity/sync
   keyrings, FCM configuration, disabled SMTP and running unrelated services. Start
   only the new isolated provider services during preparation. Check internal
   versions/health, denied unauthenticated access, persistence/restart and no host
   exposure without creating a real employee identity or sending a human message.
4. After full candidate acceptance, stage exact verified API/worker JARs and image
   hashes. Take a fresh coordinated database/storage/separate-key checkpoint while
   existing API/worker/Garage are quiesced. Capture their container IDs first and
   restart those IDs directly; Compose start can execute migration dependencies.
5. Restore and migrate that checkpoint103→116 in an isolated clone before the live
   migration. Compare Google/person bindings, active native session IDs, document
   revisions/counts, roles, constraints and the original migration-history prefix.
   Retain old image tags and complete checkpoints. Never automatically downgrade
   the live database. Select the migration window explicitly; deploy compatible
   API/worker with no dependency-driven surprise migration, then verify health,
   non-root runtime roles, data invariants and provider connectivity.
6. Enable the existing company's collaboration module and employee grants through
   authorized HRIS administration APIs, with observed versions and retained MFA.
   Do not seed grants in SQL, reset Google/native identities or rerun invitations.
   Foreground/offline chat may be piloted before push notifications. Build Mobile
   release16+ with the existing private API/signing identity after its checks;
   notify before installing to explicit10.77.77.3:5555 with --install-only. Do not
   launch or drive the user's phone UI. Physical iPhone acceptance is waived;
   hosted simulator/full app compilation remains required.

Provider startup/configuration, schema recovery, application integration, actual
native SDK behavior and phone acceptance are distinct. This private pilot cannot
accept load/latency, public rollout, rich chat, calls or meetings. All54 PRD feature
groups remain open. Continue notification read/mute/privacy, durable display/actions,
encrypted inline reply and remaining P2–P8 after the usable foreground pilot.

## Isolated provider started — 2 November 2026

[Evidence](../measurements/chat-private-provider-2026-11-02.json): Synapse1.164.1
and PostgreSQL18.6 run as hris-dev matrix/matrix-database on the internal
hris-dev_matrix network, with no host ports. HRIS API/worker are not attached yet.
Staged configuration is private at ~/.config/hris/matrix-pilot/compose.matrix.yml;
active dev.env and all existing service containers are unchanged. Matrix data is
at ~/.local/share/hris-matrix-dev/data and hris-dev_matrix-postgres-data. Keep these,
private keys/config and stable matrix.hris.internal identity; never regenerate on
continuation. Runtime files are read-only UID10001 (PostgreSQL UID70), with separate
protected originals. The Synapse role matrix is neither superuser nor BYPASSRLS,
CREATEROLE or CREATEDB; its database is UTF8/C. Tracked bounded logging is mounted.

Preparation validates the rendered overlay preserves existing environments/mounts/
ports. Startup passes14.993s; verification and explicit owned restart pass11.274s.
Versions returns200, unauthenticated whoami401, federation404, JWT is enabled and
password/registration disabled. Both provider services restart healthy with their
signing key/database preserved, zero users and no messages created. Existing HRIS
API remains healthy. This is not end-to-end chat or full recovery acceptance.
The staged overlay explicitly keeps all three chat push flags false.

Next: complete full Verify package/recovery acceptance, fresh coordinated HRIS
checkpoint and isolated migration, then compatible API/worker activation. Matrix
backup must retain its separate database, data/signing key and protected config;
the older HRIS recovery-set command does not include these new provider stores.
