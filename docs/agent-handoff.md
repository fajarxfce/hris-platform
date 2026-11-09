# Agent continuation — HRIS workspace

Updated 9 November 2026, 09:52 UTC. Previous checkpoints remain in
[Git at 1c0cb482](https://github.com/fajarxfce/hris-platform/blob/1c0cb482/docs/agent-handoff.md).
Use this file for current state; older active-job instructions are historical.

## Objective and authorization

The user authorized the full [communications PRD](collaboration/prd.md), continued
implementation, atomic commits with a push after each accepted change, Git handoffs,
and validated Android phone updates. All 54 feature groups remain open (3 in progress,
51 planned). Chat complaints refine the active work; they do not cancel calls/meetings.
Physical iPhone interaction is waived; source and platform acceptance remain distinct.

Mobile is the proving ground. Reusable foundations return to Starter after proof;
HRIS policy stays in the HRIS repositories. Follow AGENTS.md and architecture.md.
Do not put orchestration/retry policy in controllers, ViewModels or datasources.

## Current repositories and live runtime

| Repository | Branch/source | State |
| --- | --- | --- |
| Platform original | feat/communications / 1c0cb482 | Documentation checkpoint; runtime unchanged |
| Platform group worktree | feat/communication-space / fdcd783e | Separate unshipped organization chat |
| Mobile original | feat/chat-send-timing-20261107 / 2293ff05 | Accepted selected-runtime source; native86 proof running |
| Mobile CI worktree | feat/pinned-inspector-ci-20261109 / acc0e79d | Pushed; public consumer CI underway |
| Inspector original | feat/matrix-rust-bindings-20261107 / 2b849217 | Frozen with Mobile native proof |
| Inspector CI worktree | feat/sdk6-distribution-20261109 / a6b8236 | Published SDK consumer validation accepted |
| Starter | feat/network-inspector-20261107 / 6157db1 | Generic worker/identity ports accepted and pushed |

Active backend source is e558a6868399058a2dcdee97acfd0e7fa7f1ef07; schema116.4,
readiness control ON, FCM enabled. V117–138 and organization routing are NOT deployed.
The older readiness live-result.json describes its original OFF setting, not the
later accepted ON toggle. Do not deploy the group branch or modify gateway/session
settings during the current Mobile proof. Crashlytics access is already verified.
Never print the Firebase service-account file or native credentials.

## Mobile work in progress

Read the [concise Mobile checkpoint at acc0e79d](https://github.com/fajarxfce/hris-mobile/blob/acc0e79d/docs/agent-handoff.md)
and its current local checkout before starting another heavy job. It records exact
source checks, artifacts, active sessions, retained stores and failure evidence.

2293ff05 preserves an available background SDK client for the fresh admitted selected
application owner. Two old-code regressions fail; all31 registry cases pass after the
fix. Focused620 and full693 fresh cases pass separately; overlapping reused cases are
not added. No new authority, timer, retry or SDK-opening policy is introduced.

APK86 is built/verified: nondebug devRelease, same dev signer and exact publishedSDK6,
123465103 bytes, SHA2564c53315ff2f1f29bc67f572d6f63527622200ad0c6b638ea65746386e03afc73.
Initial packaging ran out of2GiB heap; preserved retry with3GiB/one worker passed88.189s.
No86 phone install yet; phone remains83. ADB is available, but phone interaction is
install-only after notification and actual native acceptance.

The first85→86 emulator run retained account, public keys and SIX original messages.
Home/room and old-message observations passed; interruption stopped its resumed-idle
phase. No new send, reset or full acceptance. The current proof clones that latest
complete86 store and checks the already installed APK without repeating installation.
Root: Mobile .work/selected-runtime-publication-resumed-native-2026-11-09.
It tests four complete idle/lifecycle intervals and exactly one new Selected86 sample
at the end. Preserve every original and inspect new-send.json before continuation.
Do not roll back to an older store, recreate keys or resubmit messages.

Only one local heavy job runs: owned emulator92677/PID645928,2560MiB/two cores,
serial emulator-5562. Phases47713 and passive counter36194/paired-refresh71793 own
bounded observation. Reap jobs and inspect stored evidence before edits or a compiler.
Turn interruption may remove session handles; actual process/ownership files then
remain authoritative. Never kill live API/worker JVMs as build cleanup.

Native84's failed Home gate (26 metadata reads and121.661s sync gap) remains a failure.
Diagnostic85 did not reproduce it. Current86 captures the opening-contention path with
continued Home synchronization; final idle/credential/send acceptance remains pending.
Startup cost, phone latency and p95 are not declared solved by one emulator sample.

## Public dependency CI

SDK26.10.23-hris.6 is published in immutable Mobile release438835525; do not replace
its assets. The original inspector still pins unpublishedSDK5 and is frozen, so APK86
uses the exact verified local mirror. The accepted isolated library uses publicSDK6.
Inspector CI42591111996 passes226 portable/Android host and69 Apple simulator cases,
with exact artifact digests/XML audited. Native Android fixture compilation there is
not execution; native Apple Matrix binding and capture overhead remain open.

Mobile CI pins verified inspector742a2d1 in all nine consumer jobs across five workflows.
First run42594287486: Windows succeeds; Apple/web continue; verify failed before tests
because Android SBOM resolution lacked one Material3 POM checksum. b1630963 adds only
the official Maven Central/publisher-SHA1-verified POM hash. Corrected verify run
42596014241 is active. Do not relax verification or dispatch distribution workflows
merely to make checks green. Port generic CI setup to Starter after Mobile validation.

## Next implementation

1. Finish/review current native86; notify and install the exact accepted APK with
   --install-only. Record exact checksum, identity preservation and remaining limits.
2. Finish public application CI, retaining original failures and verifying actual
   reports. Integrate the verified inspector/consumer setup after Mobile inputs unfreeze,
   then port generic changes to Starter.
3. Resume organization chat. Backend work is in .worktrees/communication-space,
   including V117–138; the original Platform checkout does not contain those migrations.
   Mobile has accepted original Room preparation and resolution. Next are bounded
   dispatch, durable published binding, exact LEGACY metadata validation, original SDK
   delivery/history, group workers and product routing. Preserve original transaction
   IDs and keys; a missing receipt never authorizes replacement.
4. Continue rich messages, actions/media/stickers/voice notes, encrypted backup,
   native MatrixRTC calls and LiveKit meetings under delivery.json. No whole PRD group
   becomes accepted from a domain test or provider experiment alone.

Keep this checkpoint current and concise. Implementation measurements and immutable
Git history retain detail; do not append obsolete active-job instructions here.
