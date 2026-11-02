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

## Provider checkpoint and dashboard candidate

[Recovery evidence](../measurements/chat-private-provider-recovery-2026-11-02.json)
retains the cold pre-enrollment checkpoint and independent restore. Data is under
~/.local/state/hris-platform/matrix-recovery-data/pilot-20261102T123710; the matching
matrix-recovery-keys path holds separate protected signing/config/operator keys.
The manifest binds source images/contents through the recorded version and hashes;
all retained data/key files match. The database has176 tables at Synapse schema95,
zero users and empty media. Initial checkpoint/restart/clone passes17.085s; review
found its clone role initialization still used an active protected SQL file.
A second clone initialized roles only from archived keys and passes10.473s without
changing the source or immutable checkpoint. Both clones are healthy, retain the
signing key and restricted role, publish no ports and are removed after verification.
This is pre-enrollment recovery, not acceptance of real encrypted-chat recovery.

Dashboard source2124c6fc builds in3.729s into the retained
~/.local/share/hris-dashboard-dev/collaboration-2124c6fc directory (220 files,
3,939,446 bytes); source maps are absent and every file has a recorded hash. It is
not activated yet. Include it in the compatible rollout: the new API vocabulary
contains COLLABORATION, so retaining the older dashboard's strict module parser is
not sufficient. Preserve the old systemd drop-in and immutable1e0522c0 assets.

Concrete guarded operator scripts are supplementary under
.work/chat-private-rollout-2026-11-02: stage-packages.py requires complete exact-head
CI and verifies GitHub artifact ZIP digests/JARs; stage-images.py retains old tags
and checks copied image JARs; checkpoint.py captures fresh quiesced schema103 state;
verify-upgrade.py restores/migrates it in isolation and checks identity/native/
document fingerprints plus original migration history. deploy.py explicitly selects
the migration, compatible images, preserved environments and dashboard activation.
None of those HRIS rollout scripts has executed. enable-pilot.py adds only
collaboration.chat through current MFA/versioned membership commands while retaining
applied role snapshots. Existing module policy needs no change if already enabled.
Only the existing employee/operator memberships are selected; no human message or
provider device is created by granting permission.

## Full CI retry-fixture correction

Full Verify41442782804 failed one worker outage case; seven jobs passed and downstream
integration/recovery did not run. [Evidence](../measurements/chat-provider-retry-2026-11-02.json)
records a provider rejection on the first resumed lease and correct durable requeue,
where the fixture required immediate success. The test now permits at most three
real resumed leases with strict transient-code/no-publication assertions. Full local
worker/static checks pass298.222s (116 active cases, one existing optional skip).
Production behavior is unchanged. A new exact-head full Verify is required; the
old run cannot authorize package staging or rollout. Retarget the prepared scripts
only after the replacement source/run is recorded and accepted.
