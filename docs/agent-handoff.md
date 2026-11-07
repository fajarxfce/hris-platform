# Agent continuation — HRIS workspace

This is the active checkpoint. [Historical handoff](agent-handoff-history-through-gateway-diagnostics.md)
retains prior entries verbatim. Read the current Mobile handoff before starting builds.
Do not repeat accepted releases, SDK publication or native sends just for evidence.

## Current — 7 November 2026, 01:59UTC

Phone52/sourcefd05 remains installed, original pending report OPEN. User reports
new messages sometimes take20seconds–2minutes. They reopened Bima; passive logs at
01:48/49 match native QueueRetained/Send ApiForbidden with server room_send denial.
A300.022s observer captured FIVE actual requests: original Bima roomHTTP200 once,
a different owned roomHTTP403 four times. Exact read-only room mapping finds Bima
one confirmed eligible device; other peer zero registered devices. Prior speculation
about an old revoked self-device does NOT explain these captured failures. No phone
UI/sends or queue resets. [Evidence](measurements/collaboration-phone52-room-rejection-2026-11-07.json).

Concrete Android scheduling defect: DELIVERY_KEY uses WorkManagerAPPEND_OR_REPLACE;
new requests wait behind an earlier retrying job. Mobile temporary branch376a5096
now has UNCOMMITTED foreground-delivery changes, three common scheduling regressions,
Android lifecycle ownership and bounded collapsed background slots. No new tests,
APK or successful latency claim yet. Do not start another heavy job until Group ends.

ONLY heavy local job84534 is Group publication-preflight contexts-2. Its API suite
passes; FULL worker tests now run. Group source frozen. Domain/data186cases/81.721s
passed. First domain gate had fixture compile errors; first context gate expected
wrong cookie/CSRF failure code, now corrected test only. V117–137 still disabled.

Live API2e75fe1ee88b5766259fc3efd3ab7f21e70d621a/image
3a2ea23b1dd3908419f4c548141dad0a799f5935585f2175d5163d449c0f2524 is healthy;
worker10485916/schema116.1/provider/storage unchanged. First2e75 control42323441241
is now fully audited708cases/113suites/8declaredopt-ins; artifact13555095559 digest
matches GitHub. No redeployment needed for this diagnosis.

Mobilefd05 firstnative42318790943 PASSED437cases/73suites on EACH ABI; original
artifacts13555660087/13555370007 downloaded and digest-verified under Mobile
.work/queue-diagnostics-ci-2026-11-07. Control42318790930 still running.
Firstee control42323799690 remains pending; native42323799725 has started.
Preserve pending first source control before another primary-source promotion.

## Accepted latest work

Main source **940c181c** adds only bounded proof diagnostic parsing above2e75fe1e:
13 native-device,34 HRIS driver and18 worker-driver tests plus architecture pass.
Mobile **376a5096**, pushed on temporaryfeat/chat-recovery-20261107, adds an
instrumentation-owned storage sink. Root check/20 fresh cases/91.471s,4,269 stable
inputs, proof assembly/format/architecture and Detekt0 pass. Exact diagnostic APK
4b62c4999cbde3d78c44e020d493e588d909f494ec307cd059d4d377e9ae197d uses immutableSDK4.

Native investigation83374 passed the ONLY previously failed frozen-draft/priority
worker scenario in350.485s, one test, all original assertions and4,269 Mobile/8,244
Main inputs unchanged. Priority succeeded in one pass; incoming restored176 originals
across11 passes/reopens. The exact fresh emulator5560/PID2936535/startTicks169172211
was stopped and transport absent; original Bima store/phone untouched. This did NOT
reproduce the first x86 CI failure or identify its cause. [Evidence](measurements/collaboration-native-storage-investigation-2026-11-07.json).
That native job is complete; see the current active Group gate above.

Mobile **34d507bc** preserves strictschema3 handover after actual interrupted
credential replacement, without inventing initialized=true or changing private keys.
67 focused and5,512 fresh full cases/513.264s pass; actual SDK two-ABI mechanism
passed132.367s with zero sends. Owning publication/adoption still required.
Mobile primary isee338fb3/source34d507bc; temporary branch376a5096 is ahead. Preserve
firstee control42323799690 and native42323799725 pending runs before another primary
source push. Protocol42323799691 is running. Exact IDs in Mobile work evidence.

## First-source CI

- Main061cac31/control42321745141 accepted/audited:708 cases/113 suites,0failures/
  errors and8 declared opt-in skips(7native SDK/device cases plus elapsed-retention).
  Both jobs passed; artifact13554670271 SHA29267cbfaa01f8ac5e554bd388d39575b34a6da7b247b2ea64e23c7ff12e6180.
  Private audit: .work/gateway-rejection-2026-11-07/remote-ci/summary.json.
- Main2e75fe1e/control42323441241 accepted/audited above; no rerun.
- Mobile15facc91/control42313584270 accepted:4,436 portable/478 suites and1,788 Apple/
  197 suites,0failures/errors/skips. Native42313584318 had one x86 priority storage
  failure out of419 cases; ARM64 passed419. Original failure remains recorded.
- Mobilefd05 control42318790930/native42318790943 running; protocol42318790936 failed
  native SDK image acquisition before tests. Classic14 checks passed. Preserve/audit
  original first runs; never silently replace failed or pending evidence.

## Next implementation

Group worktree /home/fajar/hris/.worktrees/communication-space isfeat/communication-space,
1a315331/docs, program344211f8 unchanged. V117–137 remains unshipped/disabled.
Finish the current admitted provider-publication preflight gate before local irreversible staging.
AdvanceCollaborationSpaceProvisioning already reads actual keys but waits for DRAINING
first, so cannot serve pre-stage verification. Use existing provider repositories,
current root/native/group/client/MFA admission, exact original PROVISIONING target,
canonical keys/fingerprint and before/after revalidation outside network transactions.
Return no bearer, upload, registration, SDK ownership, freeze or ACTIVE state. Never
logout losing same-device provider candidates. Preserve existing same-parent guards;
revoked/changed native parent needs a separate explicit recovery workflow.
Then wire durable Mobile stop/join/target, admitted freeze/drain/adoption and group
routing/background/push. All54 PRD groups remain3in_progress/51planned; calls, meetings,
remaining chat features and backup are unfinished. This is not completion.

Starter /home/fajar/hris/compose-fluent-starter staysb37f8c8/docs/source5fb4eca3;
local490 fresh and Apple138 cases accepted. HRIS-specific recovery belongs in Mobile.
SDK4 release437093395/tagmatrix-sdk-26.10.23-hris.4 is immutable; NEVER republish.

## Persistent constraints

Push each atomic commit, no coauthors. No subagents. One heavy local job at a time;
no unrelated builds/emulators. Announce EACH phone install, explicitserial10.77.77.3:5555
and --install-only; never launch/drive phone UI. Original Bima userdata and ALL sidecars
are under Mobile.work/demo-chat-device-2026-11-05/bima; preserve keys/pending IDs.
Never reset queues, manufacture acknowledgements or replace original pending sends.
Apple physical testing is waived, implementation/compile/runtime acceptance is not.
Follow AGENTS.md layer ownership. Keep current work and evidence in Git.
