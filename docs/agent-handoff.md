# Agent continuation — organization chat

Updated 9 November 2026, 20:22 UTC. Historical checkpoints are retained in
[bf6e176a](https://github.com/fajarxfce/hris-platform/blob/bf6e176a/docs/agent-handoff.md).
Use this file for current work; do not restart old gates from historical records.

The user authorized the whole communications PRD, long-running work, atomic commits
with pushes and validated phone updates. All54 acceptance groups remain open. Use
Mobile to prove generic foundations before Starter. Preserve original keys, stores,
accounts, native/SDK IDs and message transactions. Follow AGENTS.md layer ownership.

One heavy local compiler OR emulator. Do not kill live API/worker JVMs. The emulator
uses2560MiB/two cores; retained sender87 is stopped, the accepted isolated native-5 is also stopped. Phone10.77.77.3:5555 is install-only; announce
installation and never launch, automate, uninstall or clear it. Crashlytics access
is verified; no repeated IAM request. Physical iPhone testing is waived, relevant
Apple runtime/build and push/call acceptance remains separate.

## Current: both readiness channels accepted; scheduled owners next

The combined FanOutCollaborationSpaceReadiness replaces the Matrix-only class name
and shares the existing full target admission/guard implementation across channels.
Explicit MATRIX/PUSH claims remain independent; PUSH also requires service.pushEnabled.
Matrix transport behavior is retained. PUSH checks exact published immutable binding
coordinates/capture time, then atomically appends a quiet SpacePushQueue hint and
advances its publication cursor. Final target/time checks roll back both mutations.
Handoff completion does not mean FCM acknowledgement. No token is decrypted or new
cloud retry loop introduced in this branch; HandleCollaborationSpacePush retains
current registration, external FCM and exact-token cleanup ownership.

Private `.work/space-readiness-push-handoff-2026-11-09` pins99a0d8c5/schema140:
- focused-1 /95714 REAPED PASS463.060s:256 cases =205 domain +51 PostgreSQL,
  zero failures/errors/skips,8,727 stable source inputs,format and architecture.
- Ten new handoff cases run real ordinary-worker repositories and the existing FCM
  delivery use case; only external transport is fake. They cover atomic rollback,
  cancellation,deadline,late directory loss,missing/unpublished/late/foreign bindings,
  expired Matrix bearer,quiet coalescing,newer wake during FCM lease,native revocation
  before actual delivery and disabled PUSH. Existing Matrix10/queue6 and FCM25 pass.
- accept.py focused-1 PASS checks every current source/report hash and exact cases;
  accepted.json is the tracked measurement. Five current Kotlin files plus the old
  renamed path; no DI/schema/Mobile changes. No failed gate in this increment.
- [Contract](collaboration/space-readiness-push-handoff.md) and
  [evidence](measurements/collaboration-space-readiness-push-handoff-2026-11-09.json).

Next shared CollaborationConfiguration binds the typed queue, combined use case and
existing MatrixSpaceReadinessControlDataSource transport with an explicit disabled
fallback. HttpMatrixClientDataSource already implements that interface; its existing
concrete bean supplies it. Do not change MatrixProviderConfiguration unnecessarily.
Make the opaque lease-only CollaborationReadinessWorker generic rather than duplicate
its lifecycle if inference remains sound; add separately qualified group channel
pools/workers with existing flags plus group rollout enabled. No business retry in
worker adapters. Validate enabled/disabled ordinary-role claims and owned shutdown.

The actual API project is :apps:server:test (apps/server), not apps/api. Validate
native API application contexts plus FULL UNFILTERED :apps:worker:test after shared DI.
The private worker-integration-review.md records exact existing six API classes and
scope. No source for this worker integration exists yet. No active heavy job, SDK7,
APK88,emulator or live rollout. Phone87 remains unchanged;all54 groups remain open.

Root652f67e3 and Mobileb061614a update cross-repo continuation through99a0d8c5 and
identify the then-running95714;those pushes are reaped. This Group checkpoint is newer.

## Accepted Matrix fanout — previous checkpoint

Five new Kotlin files implement the owning Matrix fanout, pure bounded retry and
real PostgreSQL use-case tests. No shared DI, worker loop, provider login/refresh,
new key, Mobile source, APK, emulator or live change. Matrix and PUSH checkpoints
remain separate. Current target capture/acknowledgement uses account -> native ->
device -> gateway -> exact queue guards; I/O runs between transactions. Publisher
settlement follows after leaving target scope. Stable controls retain provider
transaction identity after lost local acknowledgement. The original device/key
policy and V140 constraints remain unchanged.

Private `.work/space-readiness-control-fanout-2026-11-09` pins
bebe31388068c7b647c97663c8b789e2a5e96e7c/schema140:
- focused-1 /9502 REAPED FAILED208.284s:220 cases,one fixture failure,zero errors/skips.
  The expired-token setup attempted expiry before updatedAt; final setup instead
  advances the injected clock after valid rotation. Production source unchanged.
- focused-2 /21572 REAPED PASS215.181s:221 cases =205 domain +16 real PostgreSQL
  repository/use-case cases,zero failures/errors/skips,8,725 stable inputs,quality.
  Ten new use-case cases include a provider writer observed waiting on a real device
  lock until acknowledgement commits; external transport alone is fake.
- accept.py PASS verifies every source/report hash,prior fixture failure and final
  case counts; accepted.json is the tracked measurement. No source edit during gates.
- [Contract](collaboration/space-readiness-control-fanout.md) and
  [evidence](measurements/collaboration-space-readiness-control-fanout-2026-11-09.json).

Next connect independent PUSH readiness to bounded delivery. The existing
HandleCollaborationSpacePush already owns current native registration/token and FCM
outcomes. Prefer transactional handoff to its durable opaque wake queue if it
preserves the publication checkpoint and current-target fencing; do not duplicate
an FCM retry/cleanup owner without a concrete requirement. This is a review of the
initial private design's direct-FCM proposal. Then wire both scheduled owners and
validate full API plus unfiltered worker tests. Mobile group readiness/push consumers
remain absent. Do not replay accepted protocol/Synapse/native/hosted gates. No active
heavy job or new APK; phone87 remains unchanged. All54 acceptance groups remain open.

## Resume current fanout integration

Accepted source:storage7d4d7f1f and typed queuea182ebf5 are committed/pushed;all pushes
are reaped. V140 target acknowledgement4a5dd90f is committed/pushed;all pushes are reaped. No active
heavy job at this checkpoint. Pure/current target admission and Matrix control fanout now pass below. Next shared scheduled worker integration, then Mobile durable ownership. Do not restart the retired confirmation
endpoint,SDK7,old source gates or hosted42656803963. No new APK or live rollout.

Private `.work/space-readiness-fanout-2026-11-09/design.md` records the integration
boundary. Its initial base isa182ebf5;re-pin successor implementation to the V140
commit before applying any candidate. Pure admission and owning Matrix fanout are now accepted below; push and shared worker integration remain next.
No nested transaction owners or hidden actor switch. Claim ends before target account
guards. Exact successful/suppressed cursor advance runs inside the target transaction;
transport deferral leaves it before publisher-only release. Revalidate actual target
identity/keys/native/provider or push registration before and after external I/O.

V140 `.work/space-readiness-target-progress-2026-11-09`:
- red-1 /87113 REAPED EXPECTED FAILURE46.318s:one test. V139 rejects recipient-scope
  progress before the injected late failure;the exact assertion is retained.
- focused-1 /56726 REAPED PASS125.271s:6 actual repository cases,zero failures/errors/
  skips,41 Python migration/recovery cases,format/architecture,8,717 stable inputs.
  Existing repository cases run on V140. Target/current-cursor scope,ordinary API
  denial,late rollback/cancellation and preserved V139 active leases/keys pass.
- verify.py verifies exact source/XML and red regression;V139 bytes stay unchanged.
  Recovery max140 applies ONLY to Group;Root source/live stays116.4.
- [Contract](collaboration/space-readiness-target-progress.md) and
  [measurement](measurements/collaboration-space-readiness-target-progress-2026-11-09.json).
  No shared DI,worker loop,provider or Mobile activation is part of this increment.

## Accepted pure readiness admission; owning fanout next

Three new domain files add current metadata snapshot/admission,pure validation and
seven policy tests. Scope is an opaque wake only. Hard native/group eligibility and
actual original completion remain required;short native/gateway/MFA freshness is not.
Fresh HRISG_ on an original Matrix user stays distinct from retained HRIS_ identity.
Receiver keys use capturedAt,not issuer observation time. Provider credentials must
match the complete admitted state and remain live;no refresh occurs in this policy.

Private `.work/space-readiness-admission-2026-11-09` pins4a5dd90f:
- focused-1 /96709 REAPED PASS30.847s:203 domain cases,including7 new,zero failures/
  errors/skips,8,720 stable inputs,format/architecture. No source change during gate.
- verify.py validates every archived fresh XML and source hash;accepted.json is the
  tracked measurement. No provider/native/phone or worker delivery acceptance claimed.
- [Contract](collaboration/space-readiness-admission.md) and
  [measurement](measurements/collaboration-space-readiness-admission-2026-11-09.json).

Next implement owning Matrix control fanout with existing typed queue and pure
admission,then independent PUSH delivery. Read target repositories under account ->
native -> device -> gateway -> queue guards;I/O outside transactions. Reacquire after
both outcomes and acknowledge exactly that target in the same guarded transaction.
On deferral,leave target scope before publisher-only queue settlement. Bound page8,
claim4,20-second task/60-second lease,eight no-progress attempts,24-hour publication.
Shared DI/worker integration MUST validate full API and unfiltered worker tests.
No active heavy job at this checkpoint;no new APK,SDK7 or live deployment.

## Accepted worker original-key publication

Root cause: AdvanceCollaborationSpaceProvisioning already reads/validates the actual
signed original identity before immutable LEGACY completion, but omitted the V135
group key-publication record. The correction adds that publication and its audit to
the same existing transaction, with timestamps from database completion. Fresh SPACE
devices still publish through their SDK; empty keys/upload stays counts-only. No
extra Mobile endpoint, token refresh, SDK7, migration or readiness relaxation.

The atomic increment contains six Kotlin files:worker use case,repository contract,
shared constructor DI,worker publication tests and two native fixture files.
[Contract](collaboration/space-original-key-publication.md) and
[measurement](measurements/collaboration-space-original-key-publication-2026-11-09.json)
are accepted. Private `.work/space-original-key-publication-2026-11-09`:
- red-1 /30751 REAPED FAILED22.365s:fixture passed DSLContext instead of its datasource.
- red-2 /96766 REAPED EXPECTED FAILURE37.844s:one test,missing group publication.
- focused-1 /97011 REAPED PASS164.148s:11 cases,zero failures/errors/skips,including
  36 write-boundary rollback variants,competing workers and real Synapse continuity.
- source-1 /29647 REAPED PASS31.698s:compile/format/architecture,8,698 stable inputs.
- native-1 /71915 REAPED PASS139.231s:one actual Android SDK6 case,zero failures/errors/
  skips. Worker completion alone publishes before bootstrap;two originals survive
  process loss,exactly two encrypted events,and final offline read. Ten native requests
  retain two bootstrap/two direct reads. No fixture upload or extra Mobile key request.
  Mobile source/APK are unchanged. The isolated emulator is stopped.
- full-1 /62503 REAPED PASS1410.192s:616 fresh cases,zero failures/errors,
  1 existing opt-in skip. Complete domain,seven SQL/provider classes,delivery,
  six native API/OpenAPI classes,FULL unfiltered worker and quality pass.
- Strict accept.py verifies archived fresh XML,all source manifests,unchanged APK,
  original transaction/event identity,durable ACK before projection,and offline read.
  Focused cases are not double-counted with the final suite. No new live/phone release.

## Accepted organization readiness protocol; publication fanout next

The atomic worker correction is committed/pushed as61e8c2f5;62503 is reaped and its
strict audit passes. Mobilefbb89f80 and Rootee755305 link to that616-case plus actual
native proof. No Mobile,SDK,phone or live deployment changed. Do not repeat it.

The next fourteen Kotlin files implement separate group V1 control,exact original/
fresh Matrix identity routing,bounded existing raw HTTP,explicit exclusive company/
group sync framing,and protocol/provider/native HTTP tests. [Contract](collaboration/space-readiness-control.md)
and [measurement](measurements/collaboration-space-readiness-control-2026-11-09.json).
- `.work/space-readiness-control-2026-11-09/apply.py` has ALREADY RUN. Six base hashes
  matched and pinned ktfmt formatted all14 sources. Never reapply prepared candidates.
- focused-1 /21454 REAPED PASS119.967s:216 cases=196 domain,14 provider/projection,
  six selected native API methods. Zero failures/errors/skips;format/architecture pass.
- Actual Synapse wakes the original device's sync,retains unacknowledged replay and
  stable same-device token replacement,and demonstrates possible replay after restart.
  Both company and group cases pass. Public gateway readiness sends remain forbidden.
- Strict accept.py verifies every archived fresh XML and all8,706 unchanged inputs.
  No shared DI constructor changed;previous full worker cases are not recounted.
- No active heavy job remains at this checkpoint. No new emulator or Mobile build.

## Accepted publication storage

V139 captures the existing original-worker/fresh-SDK key journal,shared commit revision
and independent Matrix/PUSH rows atomically. No private key,payload,credential or new
provider/client request. Current target metadata fences both timestamps and receiver
commit revisions; no content authority. [Contract](collaboration/space-readiness-publications.md)
and [measurement](measurements/collaboration-space-readiness-publications-2026-11-09.json).
Private `.work/space-readiness-publication-2026-11-09`:
- focused-1 /97107 REAPED PASS143.603s:6 normal-role SQL cases plus40 Python/quality.
- focused-2 /2437 REAPED PASS335.970s:22 cases=9 SQL,11 activation,2 native API;
  0 failures/errors/skips. Capacity,concurrent claims,stale cursor/lease,restore pass.
- receiver-red /71687 REAPED EXPECTED FAILURE46.865s:1 case. Timestamp-only target
  projection includes a receiver committed later; the exact assertion is retained.
- final-1 /42132 REAPED PASS335.11s:23 cases=10 SQL,11 activation,2 API;
  zero failures/errors/skips,40 Python migration/recovery cases,format/architecture,
  8,708 stable source inputs. New receiver revision fence passes the original regression.
- Strict accept.py checks final source/report hashes,exact cases and the prior failure.
  No shared DI constructor changed; no full-worker re-count or new native emulator.
- Prepared successor files were subsequently applied after7d4d7f1f. Do not reapply
  `space-readiness-publication-2026-11-09/prepared`; it is stale after the fixes below.

## Accepted typed queue repository; current-target fanout next

Eight Kotlin files add domain lease/checkpoint/channel and pure representation policy,
repository contract,raw SQL datasource,strict mappings/repository and actual normal-role
repository tests. No shared DI,new schema,worker/provider loop or Mobile activation.
[Contract](collaboration/space-readiness-queue.md) and
[measurement](measurements/collaboration-space-readiness-queue-2026-11-09.json).
Private `.work/space-readiness-queue-2026-11-09`:
- base/applied.json record7d4d7f1f and the original eight candidate hashes. All were
  ALREADY APPLIED; do not copy candidates over current source.
- focused-1 /94366 REAPED FAILED30.409s:two cross-module smart-cast errors;zero tests.
- focused-2 /32816 REAPED FAILED28.854s:scripted edit changed only the mapper before
  its assertion failed;one repository smart cast remained. Zero tests;no acceptance.
- focused-3 /50419 REAPED PASS80.941s:4 actual repository cases,zero failures/errors/
  skips,8,716 stable inputs,format/architecture. Fourteen real post-write rollback/
  cancellation/interruption variants and malformed actual claims/targets remain
  subcases,not extra JUnit cases. No broad unrelated rerun or full-worker recount.
- verify.py validates exact archived XML,all source hashes,case names and earlier
  compile-only failures. accepted.json is copied to the tracked measurement.
- Root5d75101a and Mobilefcfc8f26 already link the accepted storage7d4d7f1f;both pushes
  are reaped. No Mobile source build or install;source remainsa14eea78/69e639f8.

Next implement current per-target Matrix/PUSH admission and bounded fanout,then Mobile
revision retention and foreground/background ownership. Claim transactions must end
before target account guards. Do not use a space UUID as a company ID. Signals only
schedule existing originals;they never create a message ACK. Reacquire current exact
native/device/identity/publication/provider or push registration before and after
external delivery;do not refresh credentials or create keys inside fanout.

No active heavy job remains at this accepted checkpoint. Source schema140 is unshipped;
group routing OFF,phone87/live e558a686/schema116.4 are unchanged. All54 groups stay open.

RETIRED explicit-confirmation approach:9 Group and22 Mobile unaccepted Kotlin files
are archived in each checkout's `.work/space-original-key-confirmation-2026-11-09/retired-*`.
Do not reapply that directory's `native-prepared` or run its old Mobile focused script.
213 focused/2 HTTP cases passed for that candidate only. Full61608 was deliberately
terminated/reaped after1,231.719s,192 domain cases; it was NOT full acceptance. Its
verified process group is stopped. Those source files were restored before this work.

Mobile hosted42656803963 is complete and accepted at69e639f8:9,137 cases,including
2,499 Apple simulator,zero failures/errors/skips,and successful unsigned Xcode host.
Artifact audits are complete; Mobilea945220c records Apple evidence,8d1a6917 records
this direction. No rerun/reaudit. Actual native Apple Matrix/APNs/calls remain separate.

## Current original-delivery experiment

Mobile69e639f8 source is accepted and pushed. Hosted42656803963 portable job is
accepted with6,638 fresh cases,zero failures/errors/skips; Apple also passes2,499
cases/Xcode. Mobilea945220c records complete hosted acceptance; a14eea78 records native acceptance.

Accepted Group test-only work: NativeSpaceOriginalFixture/DeliveryTest,
NativeOriginalDeliveryTrace, bounded NativeSdkMatrixServer.encryptedRoomEvents and
`tool/collaboration_space_original.py` plus seven ownership tests. Real native commands
and room/provisioning workers adopt two fictional legacy devices and their original
room. The driver stages original app commands, kills only its owned process after a
durable ACK but before Sent projection, then reopens unchanged encrypted stores and
checks offline readback. Failed app data is retained; no employee originals are used.

Private `.work/space-original-delivery-native-2026-11-09`:
- Mobile source89458 passes131.229s/20 cases,4,572 stable inputs,proof APK/lint/quality.
- Group source49471 fails31.843s on a Jackson test JSON map overload; source66177
  passes32.624s after correction. Review fixes nested room fields and adds request counts.
- Final Group source21551 passes32.425s,8,698 stable inputs; compilation/format/architecture.
- Existing driver82 tests and new ownership6 tests pass. No production source changes.
- native-1/2 fail only fresh-emulator setup, before any server/message test; reaped/stopped.
- Native61494/native-3 is REAPED FAILED136.271s:one case fails before delivery on a
  driver checkpoint/terminal-state read race. Inspection of retained app files proves
  two staged originals and unchanged identities; terminal-file inspection itself failed.
  A focused Python regression reproduces the race (7 cases/1 error), then all7 pass
  after re-reading the checkpoint on terminal observation. Original app source unchanged.
- Native5680/native-4 is REAPED FAILED121.262s:one case reaches adopted room, correctly
  reports recipientReady=false because original company publication is not group
  publication. No production readiness guard is weakened. Fixture now uses the actual
  SDK's signed public identity and real group gateway keys/upload after completion.
  No generated/replaced keys or SQL publication injection. This explicitly DOES NOT
  prove automatic Mobile first-key-publication activation; that product gap remains.
- Source1075/source-4 is REAPED PASS32.002s,8,698 stable inputs,compile/format/architecture.
- **Native31328/native-5 REAPED PASS135.232s**, one fresh case,zero failures/errors/skips.
  Two original messages/transactions survive a killed process after durable ACK and
  before projection; provider has exactly two encrypted events. A later process reads
  both Sent records offline before transport. Ten native requests contain exactly two
  bootstrap replays/two direct reads,zero registration/room requests/native failures.
  Both manifests/APK match; all other128 tasks remain up to date (no concurrent compiler).
  The emulator is stopped,successful proof package removed; failed fixture data retained.
  Strict publisher passes. [Contract](collaboration/space-original-delivery-native.md) and
  [measurement](measurements/collaboration-space-original-delivery-native-2026-11-09.json).
- SDK6 x86_64 library exactly matches deterministic NDK28.2 stripping,111204848bytes,
  SHA256ecffbab72fd5dd29d656bc4757ea423bb4bccf0444140af0480684bc32761278.
  Proof APK SHA2561b64c9eed71feebf9cfdebb6e7ec42991512d25e8f5d7a0e0601a7f726d96b7f.

Live e558a686/schema116.4/group routing OFF, phone87 and retained stopped sender87
are unchanged. Native fixture acceptance is bounded above; no new product APK. Next:
group readiness-event consumption/background
ownership/product routing. No acceptance group is complete.

## Current source reconciliation

This checkout `/home/fajar/hris/.worktrees/communication-space`, branch
feat/communication-space, accepts the source merge of Root4d4df31f into group bf6e176a.
It is NOT DEPLOYED. Common ancestor8bad394e;171/85 divergent commits.
Eight conflict paths were manually resolved. Their originals and both parent hashes
are retained in `.work/space-root-reconciliation-2026-11-09`.

[Reconciliation contract](collaboration/root-source-reconciliation.md) describes:

- Both company runtime-transfer and default-key dependencies retained in DI.
- Group push and company readiness worker definitions/guards retained separately.
- Company readiness projection ported into the shared MatrixSyncReader with explicit
  company framing. A new regression excludes old company controls from adopted group sync.
- The fixed default-key read added to the group native gateway and exact-token HTTP
  dispatch. Three new native/SQL cases and one HTTP case cover scope, missing/empty/
  existing metadata, late denial, cancellation, cleanup and provider unavailability.
- Recovery verification now accepts reviewed116.2–4 without changing encrypted envelope
  projection. One expected RED error precedes27 passing Python cases; no live restore.

The full merge is ACCEPTED. Pinned ktfmt0.59 passed65 changed inputs in
3.651s with256MiB heap; three files changed. Focused results are retained separately:

- 49700 REAPED FAILED226.851s:207 domain/data cases passed, then API compilation
 failed on five old directMetadata fixture calls. They now use the merged accountData
 method, whose default remains m.direct. Targeted formatter passed unchanged1.519s;
 its first unsupported --max-width option was rejected before changes.
- 67950 REAPED FAILED83.59s:two HTTP cases ran; the new default-key case returned409
 request_lost because groupNative freezes the application clock before PostgreSQL
 creates its request lease. The new provider-operation test now follows wall time.
 Its503 assertion and all real request lifetime guards remain unchanged.
- 24065 REAPED PASS75.409s:both the new default-key and existing group metadata HTTP
 cases pass; zero failures/errors/skips. Domain/data were cached, not recounted.
- Full33511 is REAPED PASS5,607.828s on8,686 unchanged inputs. It contains1,444 cases:
 138 database,189 domain,762 data,25 delivery,180 API and150 worker. Zero failures/errors;
 the one existing ReportExportElapsedRetentionTest opt-in skip remains explicit. Both
 readiness worker contexts and every new default-key/isolation regression are present.
 Migration/recovery/image/format/architecture checks pass.
 [Accepted evidence](measurements/collaboration-root-source-reconciliation-2026-11-09.json)
 verifies archived fresh XML, exact source hashes and the complete worker command.

Do not substitute bootJar or one worker smoke test for the full shared DI checks.
Full results and earlier failures are audited. No local heavy job remains from this gate;
do not rerun it or infer a live rollout from source acceptance.

Mobile binding preparation final full81742 is REAPED PASS511.375s:4,224 fresh
cases=3,587 desktop+613 Android host+24 exact browser cases, no failures/errors/skips.
Final focused50366 passes26 cases;4,551 source inputs match both gates. Its earlier
coincident-denial/expiry failure and insufficient correction are preserved. See the
[latest Mobile handoff](https://github.com/fajarxfce/hris-mobile/blob/feat/chat-send-timing-20261107/docs/agent-handoff.md)
for accepted source/hosted Apple transitions. No new APK or product activation.

## Accepted state

- Group8d5724e2 exact published original reference is accepted:266 fresh cases,
  native admission/actual LEGACY worker/API/OpenAPI/quality pass. Group V117–140 is
  present only in this worktree and remains unshipped.
- Root Platform feat/communications691f3fc5 (recovery verifier only after merge parent4d4df31f). Live API/worker remains
  e558a6868399058a2dcdee97acfd0e7fa7f1ef07, schema116.4, readiness control ON.
  Organization routing is OFF. This merge does not change live runtime.
- Mobile feat/chat-send-timing-20261107: room dispatch4e7be081, joined-roomd8383438,
  original-reference5b63b498 and encrypted binding storage0f6c843f accepted/pushed.
  Owning binding preparation78076d4e is accepted/pushed; no SDK/worker/UI activation.
- Mobile public CI42611795210 at21101489 passed7,950 cases; artifact13672363171
  digest/XML/APKs/SBOM audited. Root integration57ebc77a passed66 focused cases.
- Inspector rootf8ca3d6/isolateda6b8236 use immutable publicSDK6. CI at742a2d1 passes
  226 portable/Android-host+69 Apple simulator cases; native Apple Matrix binding open.
- Starterffd0ac5 pinned workflow/checksum port accepted: root/Android/Web/iOS source,
  quality/SBOM and62 fresh browser cases. No Starter hosted distribution.

## Phone and retained originals

APK87 is ALREADY INSTALLED fromd8383438. Phone install33957 passed210.86s with exact
installed digest and unchanged first-install identity, no UI driving. Native retained
86→87 proof passed670.401s, four quiet sync phases, no join/setup loop, paired refresh
200×3 and one new send at2,066.684ms. This is one emulator sample, not phone/p95 proof.
All emulator/native jobs are stopped and reaped. No APK88 exists; do not reinstall87.

Latest COMPLETE STOPPED sender store is Mobile
`.work/joined-room-entry-native-2026-11-09/sender`; keep the complete AVD and encryption/
userdata files, plus Bima74's stopped store. Eight originals must never be resent:
Control77 235408, Warm77 235728, Recovery78 005634, Registry82 055959, Parent83 071123,
History84 083059, Selected86 102110, Joined87 115837. Draft66 092041 must never be sent.
Preserve Inspector's unrelated gradlew.bat worktree delta. SDK26.10.23-hris.6 release
438835525 is immutable; do not rebuild or overwrite its assets.

## Next work

1. Direct transport96d2d238 and reconciliationfa90ddcc are accepted/pushed. Never redo
   their apply/merge or accepted source gates. Live runtime/schema remain unchanged.
2. Mobile ab17eadd/69e639f8 are accepted/pushed for direct transport/original delivery;
   full owning source passes4,304 cases with exact source/XML audit. Hosted42656803963
   completed exact69e639f8 with audited portable/Apple success. Group e2c61dad and
   Mobilea14eea78 accept actual native31328. Finish the worker publication correction
   above,then prove the fixture without explicit key uploads.
3. Continue owning original SDK delivery/history, recipient readiness, background
   lifetime and group product routing. Existing Prepare/Resolve/Replay Room commands
   are accepted; do not recreate them or replace uncertain originals.
4. Continue rich actions/media/stickers/voice, encrypted Drive/iCloud recovery,
   MatrixRTC native calls, LiveKit meetings and all remaining PRD scope in
   docs/collaboration/prd.md and delivery.json. No acceptance group is complete.

Mobile hosted collaboration-control42628449460 is complete SUCCESS on exact78076d4e.
Verified artifacts contain9,003 cases, including2,457 Apple simulator, zero failures/
errors/skips; exact21 domain+3 actual storage preparation cases run on each shared target.
Xcode builds the unsigned simulator host. Evidence/handoff7e636567 is pushed. Do not
redispatch or reaudit. Native Apple Matrix delivery/APNs/calling remain separate.

## Accepted direct-transport increment

Twelve Kotlin files implement the current native device/room/recipient read, pure shared
room mapping, native HTTP and DI. [Contract](collaboration/space-direct-transport.md) and
[evidence](measurements/collaboration-space-direct-transport-2026-11-09.json) are accepted.
Full38552 FAILED459.946s after342 domain/worker cases: two fixture compile errors.
Focused34852 FAILED591.639s with302 cases/eight failures: the pre-V122 SQL fixture had no
provisioning head and the HTTP fixture used the wrong room column. Corrections use actual
enqueue/claim/finish plus explicit missing-head rejection and the actual matrix_room_id.

Targeted43271 is REAPED PASS148.630s with nine fresh SQL/HTTP cases, zero failures/errors/
skips,8,693 unchanged inputs. The strict audit accepts452 cases:192 domain,38 SQL,
72 API/OpenAPI,150 full worker; zero failures/errors and one existing elapsed-retention
opt-in skip. Only three SQL/API fixtures changed since full-1; all production, DI,
worker tests and build inputs match. Unchanged domain/room/LEGACY/OpenAPI reports from
focused-2 and worker reports from full-1 are hash-verified and not misreported as fresh.
All new cases and both readiness worker contexts are present. Format/architecture/image
checks pass. All local jobs are reaped; no deployment or group routing enablement.

Private attempts/corrections/audit are under `.work/space-direct-transport-2026-11-09`.
Do not repeat the initial apply or any accepted reconciliation/hosted gate. Mobile contracts ab17eadd/69e639f8 are accepted/pushed with current receipt/CAS/
late-callback ownership. Actual SDK/restart proof, group wake/background ownership and
product routing remain required. See the current Mobile handoff; no deployment/activation.

Original message repository.get already returns archived command facts; its actual
completion storage contract proves reopen/login behavior. No null-to-archive fallback
belongs in ReadCollaborationSpaceOriginalQueue. Keep its missing-record rejection.
