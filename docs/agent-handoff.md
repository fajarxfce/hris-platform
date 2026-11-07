# Agent continuation — HRIS Platform organization chat

Read [architecture](architecture.md), [product](product.md), [delivery](delivery.md)
and the linked contracts. Earlier checkpoints are retained verbatim in
[history through native room fencing](agent-handoff-history-through-room-fence-2026-11-07.md).
Their running-job and next-step statements are historical; use this current queue.

## Current mobile priority — 7 November 2026, 16:12 UTC

Mobile recovery and primary both include c760c6c7: Room preparation is accepted
(128 focused cases; 3,829 full cases/462 suites, zero failures/errors/skips/lint).
Atomic handoff98ef15a6 and owning resolution0d10c395 remain accepted. No new APK.
First c760 control42387719940 has started; native42387719927 is still pending.
Preserve that first native run before the next primary promotion. First0d control
42383612502 succeeded (artifact audit pending); native42383612567 still runs.
Firstfe control42380244144/native42380243983 are fully audited: Apple2,064/233,
portable4,995/552, each native551/87, exact immutableSDK4 and cleanup verified.
[Current Mobile handoff](https://github.com/fajarxfce/hris-mobile/blob/feat/chat-recovery-20261107/docs/agent-handoff.md).

User's additive priorities are pending-message previews in the chat list, retained
drafts, roughly10-second send acknowledgement, new bubbles appearing late/under the
composer, and a Chucker-like CROSS-PLATFORM inspector including Matrix Rust traffic.
If custom inspector code is needed, create its own reusable Git repository. Existing
Chucker/Ktor-only interception does not capture Rust HTTP. Library/adapter evaluation
continues; no inspector/repository has been implemented yet. These requests do not
cancel the remaining communications/54-group PRD work.

The user explicitly authorized sending real test chats ourselves. One message was
sent from the ORIGINAL Bima emulator to Karyawan Demo using the exact phone release53
APK at15:49:26.104527UTC. UI polling first observed pending5.534s and Sent7.799s;
these are polling bounds, not exact ACK times. Actual video shows a transient duplicate
outgoing bubble before convergence. Do not attribute all concurrent HTTP calls or
endpoint_unavailable403 logs to this send. Original device/keys/IDs/data are retained;
no reset, cloned SDK store or phone UI driving. Emulator5562/PID295446 was stopped
at15:56:48UTC. Its inactive backup is EVIDENCE ONLY and must never be restored after
this send. Private records: Mobile .work/phone53-self-send-2026-11-07; do not publish
raw chat/logs. New ADB connect31740 at15:41UTC timed out and was reaped.

Mobile's ELEVEN-file pending-preview increment is uncommitted/FROZEN. ObserveInbox
shares one bounded local command observer, combines latest original sends with owned
history/drafts, retains existing denial/cancellation gates, and exposes compact clock
icons. Focused74307 passed50 cases/266.430s on4,412 unchanged inputs, zero failures/
errors/skips; three synthetic layout captures were opened and reviewed. Sole heavy
job95262 now runs .work/chat-inbox-pending-2026-11-07/full-1. Reap and inspect it before
editing those sources. No compiler/emulator overlap. Accept, commit and push this
increment, then fix the real echo/viewport issue and deliver a validated build54.

Live API2e75fe1e/worker10485916/schema116.1 remain unchanged. Group native dcdaf220,
HTTP cf885a1c and V138 c6937843 remain accepted; V117–138 are undeployed and organization
routing disabled. Starterb11437a is unchanged. After these user-visible fixes continue
original Room dispatch, published binding/projection, original SDK delivery/history,
native-parent recovery, product routing and all54 groups (3 in progress/51 planned/
none complete). Push every atomic commit and announce each install-only phone update.

## Accepted work — 7 November 2026

V138 storage c6937843 is accepted/pushed, but not deployed. It retains immutable
actor/original-operation evidence and stable receipt/fence exclusion under the exact
original advisory lock. Seven migration cases and seventeen writer cases at138 pass;
26 recovery checks retain unchanged encrypted envelopes. [Storage](collaboration/room-request-fence-storage.md).

The fourteen-file native integration is now locally accepted: raw datasource, typed
repository/fingerprint mapping, owning `FenceNativeCollaborationSpaceRoomRequest`,
current-admitted stable-writer rejection and shared DI. Focused49421 passed195 cases
in319.052s:164 domain,27 actual transaction and4 mapping cases. API/FULL worker71315
passed241 cases in540.839s, no failures/errors and one existing opt-in retention
skip. Both gates had8,635 unchanged inputs and quality passed. All local jobs are
reaped. [Native contract and measurements](collaboration/native-room-request-fence.md).

The fence uses the current parent/group/pair, not the original device/provider.
Only a still-admitted writer gets typed fenced rejection; unavailable old devices
still fail their existing admission. Four real concurrent writer/fence commit and
rollback tests, parent purge/replay, wrong scope, denial ordering and late rollback
pass. Neither fence nor setup receipt grants SDK authority or message acknowledgement.

## Next work

1. SIX Group HTTP files are now locally accepted. Corrected42852/http-2 passed99 cases
   in202.31s,8,637 stable inputs, zero failures/errors/skips, format/architecture.
   First57183/http-1 retained one fixture expectation failure (actual unselected-provider
   rejection is peer_unavailable). No production admission change. Actual refreshed/new/
   purged-parent resolution, exact Recorded/Fenced echo, original-key header, no-store,
   bounds/OpenAPI/disabled graph pass. [HTTP contract](collaboration/native-room-request-fence-http.md).
   All Group jobs are reaped; no shared module DI change after dcdaf220 FULL worker proof.
2. Mobile owning resolution0d10c395 is accepted/pushed to recovery and primary.
   Focused91/219.787s and full3,755/460/525.629s on4,405 unchanged inputs pass with zero
   failures/errors/skips/lint; root/Android host/quality/shared Wasm and64 explicitly
   verified Browser cases. It captures exact original results under current ownership,
   keeps send attempts stable and preserves denial through cancellation. [Contract](https://github.com/fajarxfce/hris-mobile/blob/0d10c395/docs/collaboration-space-room-request-resolution.md).
   Prior transportfe3992f1 and durable storagefc0034ed remain accepted. Atomic handoff
   source98ef15a6 is now accepted/pushed on recovery: eleven source/test files retain
   exact original ciphertext and authenticated predecessor/successor links using the
   existing raw archiveAndInsert. Focused40440 passed102 cases/216.321s; full45951
   passed3,777 fresh cases/460 suites/511.466s on4,406 unchanged inputs, zero failures/
   errors/skips/lint. Root, Android host/release lint, quality/shared Wasm and75 actual
   Browser cases passed. Desktop SQLite/Browser IndexedDB execute the30 storage and8
   policy cases. All jobs reaped. No owning successor, SDK/product caller or APK yet.
   [Atomic storage contract](https://github.com/fajarxfce/hris-mobile/blob/98ef15a6/docs/collaboration-space-room-successor.md).
3. After the current Mobile priority above, implement original Room dispatch after accepted preparationc760c6c7.
   A null receipt, timeout, denial or changed native parent never authorizes replacement.
   Continue Room projection/recovery, original SDK delivery/history, native-parent
   recovery, product routing and all54 PRD groups:3 in progress/51 planned/none complete.
4. Push every atomic implementation/test/doc commit. Routine work and pushes are
   authorized. Validate reusable foundation changes in Mobile before any Starter port.

## Earlier repository and CI checkpoint (superseded above)

Accepted Mobile source0d10c395fb0c637d27344d037d63961a1af9bd80 is on both recovery and
primary; recovery source is98ef15a6. First control42383612502/native42383612567
are PENDING behind running firstfe control42380244144/native42380243983. Preserve both
new first runs before another primary promotion; recovery work may continue.
First Open source31e control42376025279/native42376025306 are fully audited SUCCESS:
Apple2,056/232, portable4,979/550, each native543/86, zero failures/errors/skips. Actual
25 Open/3 storage cases per Apple/Desktop/Browser, immutableSDK4 hashes/keys/IDs and
cleanup verified. Original queue20.779s x86/74.454s ARM sends zero; separate six HRIS
scenarios retain exact[1,3,3,176,176,176] sends and cleanup. No provider reruns. Earlier
1d/3b3/79/fb/e8 remain accepted; this older CI does not replace new fence/owning source CI.
[Mobile continuation](https://github.com/fajarxfce/hris-mobile/blob/feat/chat-recovery-20261107/docs/agent-handoff.md).
Starterb11437a remains unchanged; no HRIS policy port. Main1005340a is a docs checkpoint.

## Runtime, phone and original data

Live API2e75fe1ee88b5766259fc3efd3ab7f21e70d621a, worker10485916, schema116.1 remain.
V117–138 are unshipped and organization routing is disabled. Preserve running services,
Google binding, original stores and message transaction IDs. No new phone APK is added.

Phone10.77.77.3:5555 remains on release53, source08545820c0b58f46d784971e71ceb207dd628c0f,
UID10962 retained. APK SHA994d161ade3455745157d0793475b5091d67e968af909bda7a282dce163152c6.
Latency is OPEN; latest bounded ADB retry11:41UTC timed out. No more ADB retries this
turn and no repeated feedback question. Fresh14:00UTC audit confirms existing Bima keys;
14:04UTC still admits one confirmed receiving device despite expired interactive access;
he need not remain online for sender server acceptance. That does not prove the cause
or fix for the physical phone's20s–2min delay. Use the owned emulator for interaction.
Announce each authorized install, explicit serial and --install-only; never drive phone UI.

Preserve Mobile `.work/demo-chat-device-2026-11-05/bima/userdata.img`, all sidecars,
private keys, original pending transaction IDs and failed native fixtures. Immutable
SDK4 tagmatrix-sdk-26.10.23-hris.4 / release437093395 must not be rebuilt or republished.
AAR SHA9b6806de02b7d40b8487b54a51b192c114a38c68c6dfe35c68faf50d3b6bfd21.
Full native hashes and inventory ownership are in Mobile's current handoff.

Use one heavy job at a time; no compiler/emulator overlap. JDK21, one Gradle worker,
Docker wrapper from AGENTS, source hashes frozen within each gate. Reap jobs before
editing their inputs. Physical iPhone testing is waived; relevant Apple CI still applies.
