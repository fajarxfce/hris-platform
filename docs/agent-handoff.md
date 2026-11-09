# Agent continuation — organization chat

Updated 9 November 2026, 12:37 UTC. Earlier branch checkpoints are preserved in
[Git at fdcd783e](https://github.com/fajarxfce/hris-platform/blob/fdcd783e/docs/agent-handoff.md).
Treat their device versions, running jobs and rollout statements as historical.

## Authorization and boundaries

The user authorized the complete communications plan, continued implementation,
push after every commit and validated phone updates. All 54 acceptance groups remain
open. Mobile proves reusable changes before Starter. Follow AGENTS.md: use cases own
business policy and transactions, repositories coordinate datasources, datasources
perform raw I/O. Preserve existing native IDs, SDK stores, keys and original messages.

Use one heavy local compiler OR emulator. Do not kill the live API/worker JVMs.
The owned emulator uses 2,560 MiB and two cores. Phone `10.77.77.3:5555` is install-only;
announce every installation and never launch/drive its UI. Preserve the Inspector
worktree's unrelated gradlew.bat change. No repeated IAM request: Crashlytics access
is already verified.

## Current repositories and runtime

- This checkout: `/home/fajar/hris/.worktrees/communication-space`, branch
  `feat/communication-space`, accepted original-reference source8d5724e2. V117–138 are present here but remain unshipped.
- Original Platform: `feat/communications` at4d4df31f. Live API/worker remains
  e558a6868399058a2dcdee97acfd0e7fa7f1ef07, schema116.4, readiness control ON.
  Group routing is OFF; there is no live migration in this increment.
- Original Mobile: `feat/chat-send-timing-20261107` ata06b3370 (APK source d8383438). Original Room request
  dispatch4e7be081 and Joined-room reuse are accepted/pushed. APK87 passes packaging,
  lint/signature/immutableSDK6 checks. Actual retained-native87 and phone87 are accepted. One new send took2,066.684ms,
  room acquisition1ms; no join requests across four lifecycle phases. Eight originals
  remain in the latest stopped87 store; this is not phone/p95 latency acceptance.
- Mobile isolated CI: `feat/pinned-inspector-ci-20261109` at951cdbcf. Hosted verify
  42611795210 passed7,950 cases, zero failures/errors/skips/lint. Artifact13672363171
  was hashed/audited, including three APKs and393-component SBOM. Root integration
  57ebc77a passed66 focused cases and is pushed; previous failures remain preserved.
- Inspector rootf8ca3d6 and isolateda6b8236 use verified immutable publicSDK6;
  library CI passes226 portable/Android-host +69 actual Apple simulator cases.
  Native Apple Matrix binding is still open. Starterffd0ac5 is pushed: pinned workflow
  port passes root/Android/web/iOS-source/quality/SBOM,62 fresh actual browser cases
  and fixed1,052 app/49 library inputs. Lint0errors/45 existing advisories. No hosted
  Starter workflow or distribution dispatch occurred.

## Accepted published-room reference

The native published room response now includes the existing immutable original
company/conversation/room/binding time for LEGACY; SPACE returns null. Current native,
group and peer admission stays before/after the read. Invalid provenance is rejected;
no schema, credential, provider mutation or content authority is added.
[Contract](collaboration/published-room-reference.md).

The accepted8d5724e2 increment changed five Kotlin files: public room entity, read use case, native admission
regressions, the existing real LEGACY worker publication expectation, and OpenAPI
field assertions. Pinned ktfmt passes. The new three cases cover exact coordinates
for both participants, contradictory references, cancellation and final denial.
Focused gate71857 is reaped PASS513.312s:266 fresh cases, no failures/errors/skips;
8,637 fixed inputs, format/architecture checks pass. All three new cases are verified
in XML; API/OpenAPI and actual LEGACY worker publication pass. No full worker rerun. `.work/published-room-reference-2026-11-09/check.py focused-1`
is prepared and refuses to start beside the emulator. It runs domain/native data,
actual LEGACY publication, API/OpenAPI and quality checks. Shared DI is unchanged.

Backend71857 is complete and reaped. Mobile native87 emulator8719/phases46256/
counters28538/78071 are all stopped and reaped. Latest complete store is Mobile
`.work/joined-room-entry-native-2026-11-09/sender`, with eight originals. Never resend
Joined87 115837, the seven earlier originals, or Draft66 092041. Phone install33957
is reaped PASS210.86s with exact digest and identity; no UI driving or clearing.

Mobile original-reference5b63b498 is accepted/pushed:49 focused and4,162 fresh full
cases pass, zero failures/errors/skips/lint,4,535 fixed inputs. Full89590 is reaped.
The next encrypted-binding candidate passes focused82526:85 fresh cases including
all14 actual SQLite binding cases, no failures/errors/skips,4,544 fixed app inputs.
The only post-focused source change resolves the new repository in the actual app
DI test. Full45444 is now RUNNING with root/Android/Wasm/iOS source and both new/old
actual IndexedDB storage contracts. Mobile/Inspector source inputs are FROZEN;
do not start another compiler/emulator. No owning acquisition or routing is added.

## Next steps

1. Backend response8d5724e2 and Mobile typed reference5b63b498 are accepted/pushed;
   do not repeat their completed gates. Await/reap Mobile binding full45444.
2. Finish immutable encrypted binding storage, then its owning acquisition under
   current admission. LEGACY must use the exact original company/conversation read
   and match canonical metadata, pair, room, server and binding time. Retained facts
   grant no content/SDK/send authority; no guessed company or replacement original.
3. Continue SDK delivery/history, background ownership, group product routing, rich
   messages/media/stickers/voice, encrypted Drive/iCloud recovery, MatrixRTC native
   calls and LiveKit meetings. `docs/collaboration/prd.md` and delivery.json own scope.

Existing Prepare/Resolve/Replay Room command use cases are already accepted on Mobile;
do not recreate them. Current native group gateway, push, original-history and stable
receipt/fencing contracts are documented in docs/architecture.md and linked evidence.
Their source acceptance does not mean group routing or the full PRD is complete.
