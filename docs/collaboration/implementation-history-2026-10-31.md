# Collaboration implementation checkpoint history — 31 October 2026

These are chronological checkpoint notes retained for evidence. Their old next-step
statements are superseded by [the current handoff](../agent-handoff.md) and
[delivery](delivery.json). Each measurement describes only its tested source.

## Active collaboration implementation — 31 October 2026

The user approved [the full collaboration plan](prd.md) and requested
implementation. Branch `feat/communications` starts from `1363abee`. The new
54-group scope is separate from the completed original HRIS audit below.
[Delivery](delivery.json) is authoritative for its phases and
evidence; no chat/call/meeting group has been accepted yet. Read the
[architecture](architecture.md) and
[threat model](threat-model.md) before implementation.

P0 browser/provider checkpoint: six cleanup/diagnostic tests and the actual
Matrix/LiveKit browser scenario pass. [Evidence](../measurements/collaboration-web-provider-2026-10-31.json)
pins source hashes and service digests; [the procedure](provider-proof.md)
records peer verification, ciphertext inspection, recipient restart, client-side
media-key transfer, decoded audio/video and a wrong-key peer receiving RTP without
decoding. Owned containers/network were removed. The protocol experiment uses a
custom encrypted key event; MatrixRTC/native/device/HRIS admission/capacity remain
pending. Synapse accepts JWT expiry within its 120-second clock tolerance, so the
production broker must enforce strict grant expiry before internal exchange.
Android/web proof now also passes using Matrix Rust Kotlin SDK 26.10.23 in a
separate debug-only emulator APK: each endpoint decrypted the other endpoint’s
message, while both server events remained ciphertext. The combined two-scenario
run took 24.590s with full owned cleanup; 12 driver tests pass. See
[the native evidence](../measurements/collaboration-android-web-provider-2026-10-31.json).
The previous browser-only source `9452af1e` also passed hosted CI
[41180880132](https://github.com/fajarxfce/hris-platform/actions/runs/41180880132).
Next: exact native CI, Apple binding verification and protected HRIS integration.
MatrixRTC key distribution and native media remain unverified. No real account
or phone was used.

Continue through P0 provider/native protocol proof, then all P1–P8 gates. Use
isolated fictional provider fixtures. Preserve the running schema-103 HRIS stack,
real Firebase identity, and installed Mobile build 4. Public domains, Apple VoIP
credentials and dedicated capacity/GPU resources are future deployment inputs,
not blockers for local implementation. Keep any unperformed native/capacity
acceptance explicitly unverified. Commit and push each atomic increment; update
this handoff and the delivery ledger. Mobile proves generic code before Starter.

P1 admission policy foundation now exists in `modules/collaboration/domain`:
company/account/native-session/device ownership, live and captured permission
intersection, strict 60-second-or-earlier expiry, MFA and replacement rejection.
Eleven new policy tests and all 35 identity-domain tests pass; architecture checks
pass. `collaboration.chat` is an assignable employee permission without any
membership/template migration. [Admission notes](admission.md)
identify the remaining transaction, provisioning, broker, gateway and client
integration. No new endpoint/provider grant is enabled yet.

Platform browser-only CI also passed at `a9100ece` in `41181617114`. Mobile's
Android implementation is `477f9c60`; its corrected hosted runner is at
`eefc4121` after an obsolete Android SDK `tools` package failure. Corrected run
`41182128950` has now passed both actual-provider scenarios, including cleanup,
in 66.016s; Mobile `da43b35c` records its independent APK and source evidence. Apple native
store tests and iOS device-slice compilation passed at Mobile `e210508a` in
`41182072489` (Xcode 16.4 / Swift 6.1.2). Apple proof does not cover provider
interoperability or native media; its SDK requires iOS 16 for later integration.

Schema-104 collaboration registry/data checkpoint: two tables separate retained
company/account device identity from deletable parent-session bindings. Composite
foreign keys prevent account/company substitution; row policies admit only the
owner and restrict writes to the selected company. The repository counts distinct
live parent sessions across owned companies, including provisioning devices, and
excludes revoked/expired/credential-stale parents. Registration and binding roll
back together on failure. No endpoint, provisioning job or broker is enabled yet.

All 28 core database cases, the actual registry integration case, 11 admission
cases, formatting and architecture pass; 21 recovery-tool tests pass. Actual
PostgreSQL dump/restore retains registry facts, constraints and account RLS, so
the reviewed recovery ceiling advances to 104 without a new encryption envelope.
See [registry evidence](../measurements/collaboration-device-registry-2026-10-31.json).
The existing live schema-103 stack remains untouched. The new collaboration
control workflow must be checked after push.

A PostgreSQL detail caught by integration tests: `FOR SHARE OF` the immutable
binding table also applies its absent UPDATE row policy and hides readable rows.
The read now locks only the device; the upcoming admission use case must lock the
parent session first to protect bindings against parent cleanup. Keep that order.
For local Docker/JDK checks, `sudo -n runuser -u fajar -g docker -- env
JAVA_HOME=/usr/lib/jvm/java-21-openjdk
PATH=/usr/lib/jvm/java-21-openjdk/bin:/usr/local/bin:/usr/bin ./gradlew ...
--no-daemon` works without changing socket permissions; the older `sudo -u ...
-g docker` form is not permitted by this host's run-as-group rule.

Next: durable provisioning and encrypted provider credential storage;
protected provider broker/gateway; browser-parent admission, native/web adapters
and chat screens. Native registration/admission transactions are now implemented
and tested as recorded below.
Retain all P0–P8 gates and all 54 feature groups as outstanding until accepted.

Company availability now has a separate `COLLABORATION` module, supported by
schema 105 and the existing dashboard policy editor. Disabling it does not disable
`COMMUNICATIONS` (the HRIS Inbox). Existing policy revisions are not rewritten.
Nine module values remain bounded, unique and validated; the dashboard accepts
all nine and shows a localized chat/call/meeting label. Mobile recognizes the key
and retains its unknown-future-module behavior. No chat endpoint is enabled yet.

Local acceptance: nine administration-domain cases, the registry integration case,
82 dashboard cases/type checking and 13 Chromium policy/editor scenarios pass.
The first 29-case database run passed 28 and exposed a fixture that committed a
policy head before its deferred history FK. The fixture now creates both in one
transaction; the corrected migration/actual-restore test and formatting pass.
Combined passing coverage is 29 database cases; do not describe the initial full
run as green. Twenty-one recovery-tool cases pass with reviewed ceiling 105.
The expanded collaboration-control CI subsequently passed both jobs at exact
`120c6d6b` in `41184251989`, including the full schema-105 database rerun.

The schema-104 control checkpoint `959e74f9` passed hosted run `41183444363`.
Mobile's policy mapping has 37 passing workspace cases and a successful dev-debug
assembly (3m54s). That APK is a build check only; the phone remains on release
build 4. No generic Starter port is due for this HRIS module identifier.

Native registration/admission now owns transaction boundaries and lock order.
The five-parent quota is serialized across companies; retry after native refresh
replays without another device/audit/outbox. A parent is locked before its device
binding; registration rechecks admission after writes, and active grants stop at
scheduled policy boundaries. Ten new real PostgreSQL scenarios, one registry
regression, eleven policy cases, formatting and architecture pass (53s). See
[registration evidence](../measurements/collaboration-native-registration-2026-10-31.json).
API and worker DI plus the native registration route are now wired, as recorded
below. Native transport supplies the authenticated parent ID/version. Browser
parent bindings and provider provisioning remain unfinished.
Continue into the protected broker/gateway and actual chat; all P0–P8 gates remain.

The native route `POST /api/v1/companies/{companyId}/collaboration/native-device`
now uses verified `NativeIdentity`, company policy and idempotency. The separate
`HRIS_COLLABORATION_ENABLED` switch defaults false in API, worker and Compose;
current acceptance enables it only in fixtures. Registration acknowledges a
provisioning identity, never a usable provider credential. Existing memberships
need an explicit chat grant through normal administration. All seven HTTP cases
now pass, along with 69 existing OpenAPI contract cases, 22 collaboration cases,
formatting, architecture and Compose validation. Shared worker context: 104 passes,
one existing opt-in elapsed-retention skip. See [HTTP evidence](../measurements/collaboration-native-http-2026-10-31.json),
which retains the three corrected fixture failures. The earlier registration
checkpoint `4d272499` passed hosted control run `41185015834`; the expanded
HTTP/worker control workflow passed at `292d938c` in `41185906869`. Runtime schema 103 and phone build 4
remain unchanged. Continue with provisioning/credentials/broker, not another
round of already-passing registration checks unless subsequent changes need it.

Schema 106 adds encrypted internal Matrix credential storage, with exact
company/account/device/generation AAD and deterministic provider identities.
Generation-checked rotations cannot race past one another. Revocation clears
secrets while retaining the terminal generation; the final revision is reserved
for revocation/retirement. Six real PostgreSQL scenarios pass, including complete
restored old/current-key envelope verification, corruption, rollback, concurrency
and the revision boundary. All 29 core database, eleven registration/registry,
eleven admission and 22 recovery-tool cases pass. See [storage evidence](../measurements/collaboration-provider-storage-2026-10-31.json).
Recovery ceiling 106 uses schema-sensitive SQL so current schema-103 backups do
not query a nonexistent provider table. No new store bean/provisioner is wired yet;
Expanded control CI `41186737908` passed both jobs at exact `ef95326d`,
including the API/worker contexts and schema-106 recovery checks.

Next: bounded Matrix JWT login/transport, durable provisioning/reconciliation,
protected gateway and native/browser client admission. Provider credentials are
internal only. Gateway requests must recheck current HRIS authority; no public
Synapse path may bypass it. Standard native Matrix token refresh now passes the actual protocol proof below.
The HRIS gateway must implement that protocol with its own live admission checks. MatrixRTC,
Apple provider interoperability, native media and all product P0–P8 gates remain.

Native SDK credential renewal now passes against actual Synapse and the browser.
Only the fictional native session has a four-second access lifetime; after expiry,
Matrix Rust SDK refreshes and persists both replacement tokens, retaining the
same device signing key and bidirectional encrypted chat. Both provider scenarios
pass in 30.586s with owned cleanup. [Refresh evidence](../measurements/collaboration-native-refresh-2026-10-31.json)
records source/APK hashes, twelve driver checks and Mobile's corrected full root
check (2,002 desktop cases, zero lint errors). Initial Detekt method length failure
is retained; the session callback store is now a separate fixture owner. SDK
`restore_session` must only initialize a client once; use the supported standard
refresh callback, not another restore on an already authenticated client.
This proves the provider mechanism, not HRIS gateway enforcement or product
Keystore storage. Update Mobile CI's fixed Platform ref to this driver before
its refresh fixture runs. The emulator is stopped; the phone was untouched.

Internal Matrix transport now exists and passes eight HTTP boundary cases plus
one real pinned Synapse scenario (35s): deterministic JWT identity, bounded
nonrefreshable credentials, replacement login with retained signed public keys,
expiry and terminal deletion. [Transport evidence](../measurements/collaboration-matrix-transport-2026-10-31.json)
retains initial missing-dependency/redundant-cast/formatting failures and the
corrected passing result. No transport DI/provisioning/gateway is enabled yet.

Critical correction: Synapse `/logout` and its module invalidation API delete the
whole device and E2EE keys, even with an expired bearer. Never call them to clean
up a losing or late login; that can destroy the winner. Internal candidates require
finite expiry (at most ten minutes), stay private and expire if not published.
Only permanent HRIS device revocation permits `logoutDevice`. Next add persisted
provider expiry and durable provisioning/fences, then protected gateway and chat.
Do not repeat or implement the earlier tentative exact-token logout approach.

Mobile native refresh CI `41187875527` passed at `d799e5cf`, using Platform
`1db0f9fe`: both scenarios in 54.657s with cleanup; Mobile `aeb0f6b5` retains the
independent source/APK result. All product P0–P8 gates still apply.

Schema 107 now persists bounded provider expiry and includes its Unix microseconds
in the encrypted AAD. Legacy schema-106 NULL deadlines preserve their original
AAD and remain readable for cleanup/recovery but cannot authorize live provider
use. New inserts/rotations require a finite deadline after the write and within
ten minutes. Terminal retirement preserves the deadline. Use the named lifetime
policy only for freshness; it never replaces HRIS admission or deletes local facts.

Eight provider-storage scenarios, eleven admission cases, 29 core database cases,
formatting and architecture pass in 2m27s; 22 recovery-tool cases pass. The actual
106→107 migration carries a legacy ciphertext, and dump/restore verifies the full
three-envelope projection with old/current encryption keys. Independent deadline
tampering fails authentication. [Expiry evidence](../measurements/collaboration-provider-expiry-2026-10-31.json)
records source and limits. No DI/live deployment occurred; runtime stays schema 103.
Continue durable provisioning/fenced activation and gateway integration next.
Provider transport CI `41188537446` at `d78f285d` failed a fixture cleanup
race: shutdown interrupted a handler just as its released latch woke, causing
a false server assertion. The fixture now drains its executor with a five-second
bound, keeping cancellation/timeout assertions intact. All eight transport cases
and formatting pass locally (21s); the measurement retains the failed hosted run.
Check the corrected control CI after push. The schema-107 run `41188884369` at
`3a0135d2` also predates this cleanup correction; retain its eventual outcome.

Schema 108 now queues native device provisioning atomically with registration.
`COLLABORATION_PROVISION` uses the existing bounded jobs infrastructure; a scoped
monotonic pointer ties it to company/account/device/native parent. Duplicate
registration and native refresh retain one device/job; old pending identities
without a pointer enqueue once. Full 100-job company queues and post-write expiry
roll back all seven row groups. Platform metadata now comes back with the device
and contradictory Android/iOS registration/admission is rejected.

Twelve registration scenarios plus the registry regression, eleven admission
cases, 76 HTTP/contract cases and full worker context pass in 4m31s (104 worker
passes, its unchanged opt-in retention skip). All 29 database and two jobs-domain
cases passed the schema-108 selection; 22 recovery and 14 dashboard/type checks
pass. Actual dump/restore retains the job request/pointer, owner/company isolation
and pointer after parent purge. [Queue evidence](../measurements/collaboration-provisioning-registration-2026-10-31.json)
retains the initial policy-fixture constructor mistake and corrected result.

The worker deliberately leases only registered task kinds; no provisioning task
exists yet. Continue its prepare → private Matrix I/O → reauthorization/fenced
activation transactions, then explicit bounded retry and protected SDK gateway.
No provider credential is published by registration; the device remains pending.
Use the existing lease token/DB deadlines; never call Matrix logout to compensate
a losing candidate. Permanent revocation alone permits device logout. The API
and worker DI graph has been exercised; live schema 103 and phone build 4 remain.

Hosted schema-107 run `41188884369` passed at `3a0135d2`. The subsequent cleanup
correction also passed full control CI `41189071278` at `b2bc286a`, including real
provider transport, storage, API and worker contexts. The earlier failed provider
fixture run remains in its evidence. Check the new queue commit's CI after push.

Native activation use cases now exist: `AdvanceNativeCollaborationProvisioning`
and `AbortCollaborationProvisioning`, with generation-checked registry activation.
Preparation and publication use separate guarded transactions around private
provider I/O. Publication repeats parent/MFA/company/build/permission, lease,
pointer, device version and provider-generation checks; credential/device/job/
audit/outbox commit together. Late results never call logout. Only an obsolete
observation is eligible for bounded job retry; a confirmed access denial is final.

Real PostgreSQL scenarios exercise winning/losing workers, released SQL guards,
permission/MFA/policy changes during I/O, native refresh, post-write rollback,
wrong provider identities, cancellation and completed-job replay. Initial data
selection: 18 registration/activation + one registry case, eleven admission cases,
49s. The subsequent retry-observation refinement passed the new policy-boundary
case in 35s. [Activation evidence](../measurements/collaboration-provisioning-activation-2026-10-31.json)
retains exact selections; do not describe this controlled provider as an actual
Synapse worker run. The task adapter, provider configuration and their DI/context
acceptance are next, followed by explicit bounded retry, gateway and SDK/chat.
No service is enabled or deployed. Schema-108 queue CI `41190119482` passed all
jobs at `5a1dc9a9`; record this new use-case commit's full CI after push.


## Historical snapshot before the fixed identity gateway

This snapshot is retained for traceability. Its next steps are superseded by the
current agent handoff.

The user approved [the full collaboration plan](collaboration/prd.md) and requested
implementation, atomic commits, a push after every commit, and continued work
through completion. All three repositories use `feat/communications`. Platform
started at `1363abee`, Mobile at `7e12251a`, Starter at `44d164c`.
[Delivery](collaboration/delivery.json) tracks all 54 feature groups and P0–P8;
**no product feature group is accepted yet**. Continue implementation; completing
an infrastructure checkpoint does not complete chat/calls/meetings.

Read the [architecture](collaboration/architecture.md),
[threat model](collaboration/threat-model.md), [admission contracts](collaboration/admission.md)
and [checkpoint history](collaboration/implementation-history-2026-10-31.md).
The latter retains earlier measurements and corrected failures without making
its old next-step paragraphs the current queue.

Current implementation:

- P0: actual Matrix/LiveKit browser E2EE exchange/media and wrong-key rejection;
  actual Matrix Rust Android ↔ browser encrypted exchange and standard SDK
  refresh preserving the signing key. Mobile `d799e5cf` passed
  hosted refresh run `41187875527`; `aeb0f6b5` records the artifact. Apple binding/
  encrypted-store checks and iOS compilation passed, but actual Apple provider
  interoperability and native media remain unverified. The media experiment's
  custom encrypted key event is **not MatrixRTC protocol acceptance**.
- P1 schemas 104–108: retained company/account/device identity plus native parent
  binding, independent company availability, encrypted bounded provider credential
  storage with authenticated expiry/generation, and an atomic durable provisioning
  queue. `collaboration.chat` is an explicit membership permission; never rewrite
  existing membership or role snapshots to grant it implicitly.
- Native registration HTTP is `POST /api/v1/companies/{companyId}/collaboration/native-device`.
  It requires native transport, client headers and idempotency, records a pending
  device and queues work atomically. Five distinct live native parents count
  across owned companies. Ordinary access expiry retains the device/owned history.
- Activation prepares under current HRIS authority and its job lease, releases
  SQL guards for private Matrix I/O, then repeats authority/version/deadline/fence
  checks before committing credential, active device, progress, completion,
  audit/outbox together. Stale workers cannot overwrite a winner. Abort stops
  only the owned job. The exact activation source `4600a294` passed both hosted
  control jobs in [41191206775](https://github.com/fajarxfce/hris-platform/actions/runs/41191206775),
  including full API/worker contexts and final scheduled-policy case.
- Provider settings, application-owned HTTP client, encrypted repository and
  `CollaborationProvisioningTask` are now wired. Both `HRIS_COLLABORATION_ENABLED`
  and `HRIS_MATRIX_ENABLED` must be true. Missing identity keyring/signing file or
  invalid settings fail startup; disabled provider allocates no client/task.
  The actual worker fixture exercises PostgreSQL claiming, persistent Spring
  Batch, pinned Synapse and outage/recovery. The final combined check passed in
  4m28s: four settings cases, 77 API cases and 109 worker cases (108 pass, one
  unchanged opt-in retention skip), formatting and architecture. Compose syntax
  also passes. [Worker evidence](measurements/collaboration-provider-worker-2026-10-31.json)
  retains both corrected fixture failures and precise scope. Exact `d387ab67`
  passed complete hosted control [41192185771](https://github.com/fajarxfce/hris-platform/actions/runs/41192185771).
- Native setup status and explicit retry are implemented on the registration route
  (GET and POST `/retry`). Status exposes only owned safe metadata. Retry checks
  native/company/permission/MFA/client authority, observed device/pointer versions,
  terminal failed/cancelled job, a one-minute enqueue bound and queue capacity.
  It creates one new job and increments the pointer atomically with receipt/audit/
  outbox; the device identity/version does not change. Receipt ID is the new job,
  not a new device. Replay after native refresh returns the same job. Twenty-four
  PostgreSQL data cases and three new HTTP cases pass (51s), including raced retry,
  rollback and actual restore of the old failed/new queued job with pointer v1.
  Full contexts pass in 4m22s: 80 API and 108 worker passes plus the one existing
  opt-in retention skip; eleven unchanged admission cases, formatting/architecture
  also pass. [Retry evidence](measurements/collaboration-native-retry-2026-10-31.json)
  retains the corrected replay-flag fixture assertion.

Schema 109 now supplies [native gateway credential storage](collaboration/gateway-sessions.md):
one retained head/device, opaque gateway-token hashes and one authenticated encrypted
bootstrap/refresh response cache. Concurrent rotation, losing response replay,
explicit bootstrap/build change, RLS lookup isolation, bounded SKIP LOCKED cache
cleanup and real old/current-key restore pass nine PostgreSQL cases. Public bootstrap/refresh is
wired as described below; the protected provider proxy remains pending. Cache cleanup retains its
expired nonsecret deadline for PostgreSQL post-update RLS visibility. See
[gateway evidence](measurements/collaboration-gateway-storage-2026-10-31.json) for
validation (nine gateway, 29 database, 23 recovery cases; format/architecture;
final JVM selection 2m35s) and the corrected timestamp-binding/RLS failures. The earlier status/
retry source `b938d48c` passed every job/step in hosted control
[41193061956](https://github.com/fajarxfce/hris-platform/actions/runs/41193061956).

Native gateway bootstrap and standard refresh use cases now enforce current HRIS
parent/company/chat/MFA/build authority, observed bootstrap versions and bounded
idempotent replay. Gateway access lasts five minutes, replay 30 seconds, rotation
at least ten seconds apart. A stale request cannot revoke the winner's device.
Receipts contain no secrets; issuance/receipt/audit/outbox roll back on final
admission failure. The initial selection passed 43 PostgreSQL and eleven domain
cases in 79s. Final single-instant response validation passed 34 PostgreSQL and
thirteen domain cases in 45s, formatting/architecture. [Admission evidence](measurements/collaboration-gateway-admission-2026-10-31.json)
records scope. HTTP/DI/status/cache cleanup are now implemented as described below;
protected provider requests and SDK integration remain next.

The gateway HTTP/worker increment now exposes native POST `/bootstrap` and
GET setup gateway metadata, plus POST `/_matrix/client/v3/refresh` with standard
Matrix reply/errors. The separate stateless security chain keeps native/cookie
authority out of Matrix refresh and denies every other Matrix/Synapse route.
JSON bodies are bounded to 2 KiB with strict duplicate/trailing/depth checks.
The worker clears one batch of at most 100 expired response caches every five
seconds, with owned timer, cancellation, rollback and shutdown. It can drain
caches after collaboration is disabled. [HTTP/worker evidence](measurements/collaboration-gateway-http-2026-10-31.json)
records eight focused API/five worker passes (62s), then 84 API, 34 data, fourteen
domain and 111 worker cases (110 pass, one unchanged retention skip), quality
checks (4m34s) and Compose syntax. No provider content proxy is exposed yet.

Exact storage `16ab4991` and admission `5a666758` passed every hosted control job/
step in [41195040771](https://github.com/fajarxfce/hris-platform/actions/runs/41195040771)
and [41195557470](https://github.com/fajarxfce/hris-platform/actions/runs/41195557470).
The [transaction-retention gate](collaboration/provider-transaction-retention.md)
records a pinned-source finding: Synapse deletes message transaction mappings
older than 24 hours on a five-minute cleanup loop. Actual same-device bearer
replacement and aged-mapping retry are now proved below; durable uncertain-send
reconciliation remains required before offline messaging acceptance. Do not assume unlimited provider
deduplication or change a local transaction ID on refresh/restart.

The [actual transaction experiment](measurements/collaboration-transaction-interop-2026-10-31.json)
now proves same-device bearer replacement returns the same event while the mapping
exists. Fixture aging + exact cleanup predicate + Synapse restart permits a second
event for the same transaction. Both appear in room history; the origin device
sees their transaction IDs, an invited peer does not. No real 24-hour wait or
scheduled-timer acceptance is claimed. Two provider cases pass in 40s (including
the retained token-expiry/logout regression); the final peer extension passes in
29s. Formatting/architecture pass. Native bindings expose `sendWithExtraContent`
and queue listeners; the next native proof must establish encrypted logical-ID
round trips/queue ownership before implementing durable recipient coalescing.

Actual Android SDK queue/extra-content proof now passes with the existing browser
scenario in 29.090s. Both directions preserve encrypted `org.hris.message_id`,
paused SDK sending and local-to-remote transaction identity survive standard
refresh with the same device key. [Evidence](measurements/collaboration-native-logical-queue-2026-10-31.json)
retains source/APK hashes and two corrected synchronous room-queue binding failures.
The asynchronous client-level queue API passes. Mobile full root/debug/lint checks
pass in 52s (2,002 desktop cases, no lint errors). Durable process recovery and
recipient logical-ID coalescing remain pending; no product group is accepted.
Exact HTTP/worker source `c2e72935` passed every hosted job/step in
[41196507714](https://github.com/fajarxfce/hris-platform/actions/runs/41196507714).

Critical provider behavior: Synapse logout deletes the **whole device**, every
bearer and its E2EE keys, even when called with an expired bearer. Never use it
to clean up a losing/late login. Such credentials stay private/unpublished and
expire within ten minutes. Logout is permitted only after irreversible HRIS
device revocation. Synapse's JWT expiry tolerance is 120 seconds; HRIS must apply
its own strict admission deadline before and after exchange. Provider ports stay
private; a future client gateway must not expose raw provider credentials or
allow public login/admin/federation bypass.

Next implementation:

1. Inspect transaction experiment `be810f1c` hosted control `41197100773`; its
   database step passed and full contexts are still running. HTTP/worker, storage
   and admission hosted results are accepted above.
2. Protected Matrix request path with current admission before/after provider I/O,
   bounded/cancelled transport, internal credential renewal and room/context scope.
   Same-device transaction/aged retry behavior is proved; use the retained limit
   when implementing SDK outbox reconciliation.
3. Browser-parent admission, native/web SDK adapters, owned crypto stores and
   durable chat UI/outbox reconciliation. Restore an authenticated Matrix SDK
   client only once; rotate through standard refresh. Retain owned offline facts
   after ordinary expiry. A public refresh endpoint alone is not a usable SDK
   content gateway.
4. All remaining P0 native protocol gates and P1–P8 groups: rich/context messaging,
   backup, native calls, web/mobile meetings, AI, recovery and measured capacity.
   Do not mark the full plan complete after infrastructure checkpoints.

Keep the live stack at schema **103**, API/worker application **2c2a59e3**, dashboard
**1e0522c0**, and phone release **build 4**. No deployment or phone action occurred
in these collaboration checkpoints. Dedicated emulator `hris-mobile-dev` / 5560
is stopped, with data retained. Use fictional owned provider fixtures; never
real employee messages. Notify before any later phone installation, install only,
do not drive phone UI, preserve signer/data and use versionCode >= 5.

Physical iPhone testing is waived; simulator/compilation/provider proof remain
separate gates. Public DNS/TLS, Apple VoIP credentials and dedicated capacity/GPU
resources are later deployment inputs, not blockers for local implementation.
Mobile proves reusable product foundations before Starter receives them. No
Starter port is due for HRIS-specific backend modules or isolated proof fixtures.

JDK 21 is `/usr/lib/jvm/java-21-openjdk`; use the Docker-authorized test wrapper
in the commands section below. Shared DI changes require actual API and the full
`:apps:worker:test`, not JAR assembly. Avoid overlapping heavy local builds.
Secrets remain outside Git in private configuration. Update this handoff and
measurements with exact results, then push every atomic commit.

The paragraphs below are retained prior-work history. Their old installed build
2 reference is superseded by Mobile's validated release build 4; consult Mobile's
current handoff for its artifact/source and future versionCode >= 5.

Updated 31 October 2026 (UTC). The user requested verification and completion of
all specified features, with every atomic commit pushed and handoffs retained in
Git. The full audit is complete after implementing the discovered gaps and validating every reviewed area.
New work on 31 October: the user requested a full Mobile layout/component review
and fictional sample data for the existing linked employee. [Sample setup](mobile-sample-data-2026-10-31.md)
is verified through normal employee APIs; Mobile's layout review remains active.
Earlier distribution/native acceptance remains valid for its recorded scope. Read `AGENTS.md`, this page and [PRD status](prd-status.md),
then the affected module contract. The [archived history](agent-history-2026-10-29.md)
preserves earlier work; its old next-step statements are not the active queue.

Current runtime: schema 103; API/worker application `2c2a59e3`, dashboard
`1e0522c0`, installed Mobile `devRelease` **1.0.0 / build 2**, built from latest
Mobile source `5ffb7300` with unchanged accepted application trees. The authorized
31 October WireGuard update preserved the signing identity and application data;
the pulled installed APK matches the built release exactly. No UI was launched.
Mobile handoff `bae07a27` records the evidence and the new versionCode floor of 2.
Read [current development runtime](current-development-runtime.md#android-release-installation--31-october-2026).
Use captured container IDs with `docker start` for checkpoint restart; installed
Compose has no `start --no-deps` and `compose start` can rerun migration dependencies.
Earlier pending statements below are retained history, not a new queue.

