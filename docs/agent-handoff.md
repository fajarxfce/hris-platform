# Agent continuation — HRIS workspace

Updated 9 November 2026, 10:12 UTC. Previous checkpoints remain in
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
| Mobile CI worktree | feat/pinned-inspector-ci-20261109 / e95aead7 | Pushed; public consumer CI underway |
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

Read the [concise Mobile checkpoint at e95aead7](https://github.com/fajarxfce/hris-mobile/blob/e95aead7/docs/agent-handoff.md)
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

The first85→86 upgrade retained account/public keys and six originals before turn
interruption. Follow-up .work/selected-runtime-publication-resumed-native-2026-11-09
completed four120s lifecycle intervals: active sync, zero setup in complete bins,
zero bootstrap/403 and all six old Sent messages/history end retained. Overall gate
still FAILS: one unclassified otherAccess in paired-refresh observation. Its old
observer kept only a count. A separate stale send-script check expected5 events
instead of6 and stopped BEFORE typing/sending. No new Selected86 message exists.
Both failed records are preserved; this is not native/phone acceptance.

Latest COMPLETE STOPPED store is that follow-up's sender at86. The current proof
clones only it, retaining all userdata/encryption files and full AVD; no reset/resend.
Mobile root .work/selected-runtime-auth-trace-native-2026-11-09 is ACTIVE with corrected
six-event preflight and additional passive closed route/time/status/known-access
classification. Tokens/hashes remain volatile; strict gates are unchanged.

Only local heavy job: owned emulator18238/PID795640,2560MiB/two cores, emulator-5562.
Phases38867, passive counter25926 and paired observer91581 run bounded observation.
No compiler overlap. Reap and inspect actual records before another heavy job; turn
interruption may remove session handles. Never kill API/worker JVMs as build cleanup.
No accept-release/phone installation until genuine native success. Earlier native84
failed Home; current source tests and idle observations do not erase that history.
One send sample will not establish p95 or whole communications acceptance.

## Public dependency CI

SDK26.10.23-hris.6 is published in immutable Mobile release438835525; do not replace
its assets. The original inspector still pins unpublishedSDK5 and is frozen, so APK86
uses the exact verified local mirror. The accepted isolated library uses publicSDK6.
Inspector CI42591111996 passes226 portable/Android host and69 Apple simulator cases,
with exact artifact digests/XML audited. Native Android fixture compilation there is
not execution; native Apple Matrix binding and capture overhead remain open.

Mobile CI pins verified inspector742a2d1 in all nine consumer jobs across five workflows.
First run42594287486: Windows and Apple succeed. Apple artifact13664276449 is audited:
1777 simulator tests/195 suites, zero failures/errors/skips, plus Xcode app build.
Web's runner shut down during Wasm optimization; cause not established. Web-only
run42597096435 repeats unchanged compile settings with resource evidence.

Verify first lacked a Material3 POM checksum, then42596014241 lacked Lifecycle POM.
Complete local inventory restores original metadata before review: eight new artifacts,
all independently matched to official bytes and publisher SHA-1; old checksums unchanged.
Strict63401 PASS46.014s, all inputs retained,393-component same-input BOM cache reuse.
The initial insertion failure/strict failure and metadata-alias URL404 are preserved.
Mobilea5d74fa3 records the validated inventory; third verify42598801511 is running.
No distribution workflow dispatched. Port generic CI setup to Starter after Mobile
validation; original app/inspector remain frozen for native86.

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
