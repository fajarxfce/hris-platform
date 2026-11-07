# Agent continuation — HRIS Platform organization chat

Read [architecture](architecture.md), [product](product.md), [delivery](delivery.md)
and the linked contracts. Earlier checkpoints are retained verbatim in
[history through native room fencing](agent-handoff-history-through-room-fence-2026-11-07.md).
Their running-job and next-step statements are historical; use this current queue.

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

1. SIX Group HTTP candidates are prepared under `.work/room-request-fence-http-2026-11-07`.
   They are NOT applied/tested. Verify baseline hashes before applying; add the typed
   `/room/requests/fence` route with original `Idempotency-Key` and exact full echo.
   The candidates already include real HTTP refresh/replacement/purge, Recorded/Fenced,
   current denial, input bounds, no-store/OpenAPI/default-disabled tests. Run its
   `check.py http-1` after formatting. Shared module DI is unchanged by this next slice;
   retain this completed FULL worker evidence and validate API contexts again.
2. ELEVEN Mobile transport/domain/test candidates are isolated under Mobile
   `.work/space-room-request-fence-2026-11-07`; NOT applied/tested. Accept Group HTTP first,
   verify baselines, then test typed exact evidence and original idempotency across auth
   refresh. HTTP503 maps to Service. No durable fence storage/successor linkage exists.
3. Implement durable exact fence capture before atomic original/successor linkage.
   A null receipt, timeout, denial or changed native parent never authorizes replacement.
   Continue Room projection/recovery, original SDK delivery/history, native-parent
   recovery, product routing and all54 PRD groups:3 in progress/51 planned/none complete.
4. Push every atomic implementation/test/doc commit. Routine work and pushes are
   authorized. Validate reusable foundation changes in Mobile before any Starter port.

## Other repositories and CI

Mobile recovery `feat/chat-recovery-20261107` is c119792e; primary
`feat/communications` is31e618cc89e2ca0cce38bcb62ccd95b8533c88c0, including Open335e3914.
Its full local gate passed3,669 fresh cases/452 suites505.461s,4,393 stable inputs,
zero failures/errors/skips/lint. Original Open uses normalized persisted marks for
exact CAS, captures outcomes and materializes canonical metadata before archival.
[Mobile continuation](https://github.com/fajarxfce/hris-mobile/blob/feat/chat-recovery-20261107/docs/agent-handoff.md).

First31e control42376025279/native42376025306 remain PENDING behind first1d9493df
control42372520573/native42372520624, which both started. At13:38UTC1d portable passed;
Apple and both Android jobs were still running. Preserve both first31e runs before
another primary promotion. Recovery branch work can continue. First3b3 control/native
are fully audited SUCCESS: Apple2,001/228, portable4,869/542 and each native543/86,
SDK4 keys/IDs and cleanup verified. Do not rerun providers merely to audit artifacts.
Starterb11437a is unchanged; no HRIS policy port. Main checkpoint2a1e4a0b is docs only.

## Runtime, phone and original data

Live API2e75fe1ee88b5766259fc3efd3ab7f21e70d621a, worker10485916, schema116.1 remain.
V117–138 are unshipped and organization routing is disabled. Preserve running services,
Google binding, original stores and message transaction IDs. No new phone APK is added.

Phone10.77.77.3:5555 remains on release53, source08545820c0b58f46d784971e71ceb207dd628c0f,
UID10962 retained. APK SHA994d161ade3455745157d0793475b5091d67e968af909bda7a282dce163152c6.
Latency is OPEN; latest bounded ADB retry11:41UTC timed out. No more ADB retries this
turn and no repeated feedback question. Bima's06:42UTC audit confirmed existing keys;
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
