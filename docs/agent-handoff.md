# Agent continuation — HRIS workspace

Updated 10 November 2026, 04:22 UTC. Historical detail remains in
[4d4df31f](https://github.com/fajarxfce/hris-platform/blob/4d4df31f/docs/agent-handoff.md).
Use this checkpoint and the linked current Mobile/Group files, not old active-job notes.

## Scope and boundaries

The user authorized the entire communications PRD, long-running implementation,
atomic commits with pushes and validated phone updates. All54 acceptance groups remain
open. Mobile proves reusable foundations before Starter. Follow AGENTS.md layer
ownership and preserve original accounts, native/SDK identities, keys and transactions.

One heavy local compiler OR emulator. Do not kill live API/worker JVMs. The emulator
uses2560MiB/two cores. Both the retained sender and isolated proof AVD are stopped. Phone10.77.77.3:5555 is install-only; announce each
installation and never launch, automate, uninstall or clear its app. Crashlytics IAM
is verified; do not request it again. Physical iPhone testing is waived, relevant
Apple runtime, push and calling acceptance remains separate.

## Current Mobile integration — 10 November, 04:22 UTC

Mobile[d89e6b5d](https://github.com/fajarxfce/hris-mobile/commit/d89e6b5dcb0c0463916628ecd10a9bae946e5a1a) is committed/pushed
(79200 reaped): new immutable room bindings atomically retain fresh recovery work,
then the owning use case schedules after exact readback. Lost responses, schedule
failure/timeouts/cancellation preserve committed data/work. Existing local reads and
identical retention create no wake or scheduler request. No backend source/schema/DI
change. [Contract](https://github.com/fajarxfce/hris-mobile/blob/d89e6b5dcb0c0463916628ecd10a9bae946e5a1a/docs/collaboration-space-binding-producer-wake.md).

Focused62815 passes87 fresh cases/372.923s. Full77826 passes682.233s with9,294 validated
results=7,301 fresh+1,993 exact reused,zero failure/error/skip. All4,656 Mobile/library
inputs and current/archive XML match. Native46744 passes193.275s,two actual SDK cases:
original transactions,callback loss,process death,group control/claim,pusher and idle
checks remain; fresh binding/retained-read wake assertions pass. Original10native/
11Matrix;group25native/17Matrix,one pusher,zero errors or extra idle direct requests.
Exact unchanged backend source-2 compile reused; no compiler during2560MiB/two-core
emulator,now stopped. The current Mobile full gate below is the only local heavy job. Proof APK150470250bytes
SHAde897799fc7e0eb4c8751915aa4a02a7a1a7fd14b83447426dd8a0556322a686.

Generic Mobile7a6fe534 control42749839268 is running. Producer control42753380451
pinsd89e6b5dcb0c0463916628ecd10a9bae946e5a1a and is pending behind it; do not supersede
that pending run. Expected generic7,276 portable+2,709 Apple; producer7,292+2,717.
Receipt42745403043 is fully accepted9,798 cases (7,100 portable+2,698 Apple),16 Swift
checks and unsigned host; public evidence is in Mobile7b032633. Pusher42739907129 is
fully accepted9,744+16Swift/unsignedhost. Do not rerun accepted jobs.

Current uncommitted Mobile command+wake foundation follows pushedd89e6b5d/7b032633;
checkpoint a86ae506 is pushed. Raw CommandStore.insertBatchAndWake adds atomic new
commands/work; active/archive replay never creates another wake. The shared request
is renamed WorkWakeRequest without a compatibility alias or schema change. Thirteen
new shared cases per platform pass in focused99243:202 fresh database cases (102
SQLite+100 Chromium),zero failures/errors/skips,126.140s,Apple source/quality pass.
All4,660 source/library inputs match; manifest SHA
ed60ebcb63b731cfbe898ae0f8e08f22edbc735352e0b7af3895ad3232cc74a1.
Full44596 is running on that frozen source,one compiler/no emulator. Initial private
runner20937 failed configuration with zero tests; configuration-only correction is
retained. No new SPACE command producer,phone build or backend change yet. See
Mobile .work/command-commit-wake-2026-11-10 and current handoff before starting jobs.

Starter remains clean HEADffd0ac5; nine-file core patch passes a dry apply check,
no source/build change. Port after actual generic Apple acceptance. Read Mobile's
handoff/private .work/space-binding-producer-wake-2026-11-10 before starting a job.

Next SPACE sends need explicit account/space intent and durable command namespace,
atomic command+wake insertion and complete mixed old/new ledger reconciliation under
one physical SDK writer. Never reinterpret companyId as spaceId,ignore unmatched
originals or move durable commands into disposable replicas. Continue incoming/older
history,notifications/replies,rich media,backup,calls and meetings. All54 groups open.
Phone remains87; last ADB6953 timed out15s around03:37. Release88 is uninstalled;
no phone UI/data or live change. Live e558a686/schema116.4,groupOFF remains.

## Accepted: original receipt recovery; continue producer wakes

Mobile[76a143b6](https://github.com/fajarxfce/hris-mobile/blob/76a143b6/docs/collaboration-original-lost-callback.md)
and Group[401c6d56](https://github.com/fajarxfce/hris-platform/blob/401c6d56/docs/collaboration/space-original-receipt-recovery-native.md)
are committed/pushed (85826/7722 reaped). The original SDK removes a sent transaction
before its asynchronous app receipt callback. Preserved failure data had two app
commands,one receipt and zero SDK originals. The owning use case now recovers only
disappeared pending originals from exact local history or one bounded original SDK
window. Same sender/logical ID/content hash/text,physical device/runtime,keys and
transactions; no new message or fabricated ACK. Ordinary queued sends never read history.

Focused89708 passes70 cases/318.638s. Full50072 validates6,338 cases=4,521 fresh+
1,817 exact reused/578.408s,zero failures/errors/skips,4,651 stable Mobile inputs.
Required root/Android host/release/lint/Wasm+iOS/browser/proof/quality pass. Group
source22157 passes25.556s,8,734 stable inputs;15 Python cases pass0.006s.
Native93669 is REAPED PASS208.523s,two cases. Exact hard-death boundary has one app
receipt after both SDK originals disappear. Recovery yields two receipts/Sent from
exactly two provider events;544ms in fictional fixture only. Later offline reads pass.
Group case retains control/dedup/claim/pusher/process-recovery assertions:25 native,
17 Matrix requests,one pusher,no401/403/server errors,zero additional idle direct reads.
Source/APK hashes unchanged,no compiler during2560MiB/two-core emulator,now stopped.

First successor native-1 failed265.004s after original case passed: shared driver
waited for original-only first-ACK before relaying group listening/control. Each
scenario now owns its interruption checkpoint; same Mobile APK passes without weaker
assertions. All failed userdata remains preserved. No SDK artifact,backend product,
DI,schema,live,phone or Starter change. All54 PRD acceptance groups remain open.

Current receipt/generic hosted runs, producer work and phone status are recorded
above; do not repeat the old source/native gates. Live remains e558a686/schema116.4,
readinessON/groupOFF; GroupV140 is not deployed. All 54 acceptance groups remain open.

## Accepted local demand — historical build notes below

Mobile[6a8b4f61](https://github.com/fajarxfce/hris-mobile/blob/6a8b4f61/docs/collaboration-space-recovery-demand.md)
and Group[7ae66f89](https://github.com/fajarxfce/hris-platform/blob/7ae66f89/docs/collaboration/space-recovery-demand.md)
are accepted/pushed;5984/82129 reaped. The new owning read checks complete local SDK
inventory plus commands,ACK journal and immutable completions before skipping per-room
transport. It never sends,settles,projects or retires. Pending/changed facts remain work;
local ownership,denial and runtime guards remain. Global device sync is unchanged.

Focused89418 passes123 cases/170.44s. Full48682 passes483.891s:4,437 fresh cases plus
20 exact reused proof results,4,457 validated,zero failures/errors/skips. Required root,
Android host/release/lint,shared Wasm+iOS source/browser/proof/quality pass.4,633 matching
Mobile inputs and all source/library/XML hashes are verified. Source34803 passes30.759s,
8,733 backend inputs,format/architecture. Only one backend test changed.
Native77040 passes203.279s:2 actual cases. Original scenario2 events/10 native requests.
Group2 events,24 native/16 Matrix requests,zero401/403/server errors. Exactly2 required
direct transport reads;an explicitly requested fresh idle sweep adds no direct read.
Original ACK/process loss/transaction/dedup/offline checks remain. Manual scheduler,
later offline reads after reconnect;no OS timing/phone/p95 claims. Emulator stopped.
First focused candidate failed one new8-page fixture assertion against4-page turns;
corrected test verifies both turns,without changing production behavior.

Release88 and hosted states are superseded by the current checkpoint above.
All historical local gates here are completed.

## Original-key worker publication accepted

Mobilea14eea78 and Groupe2c61dad accept the prior original-delivery native fixture,
which used an explicit current-gateway upload of the unchanged signed public key.
Review found the owning correction: AdvanceCollaborationSpaceProvisioning already
verifies that actual original identity,so it must publish the group key record in
the same transaction as LEGACY completion. No extra Mobile endpoint,token-refresh
request,SDK7,private-key replacement or new schema is needed. The temporary explicit
confirmation approach is retired and archived; do not restore its private candidates.

Group67def551 records the replacement regression and current work. Red96766 fails
on the missing record after a fixture-only compile correction. Focused97011 passes
11 cases/164.148s:rollback at every write/readback/audit boundary,competing workers,
late admission and actual Synapse continuity. Source29647 passes31.698s.

Native71915 passes139.231s with the old fixture upload removed. The worker confirms
original publication before bootstrap; two original messages survive a process kill
after durable ACK and before Sent projection,produce exactly two encrypted provider
events,and remain readable offline in a later process. Ten native requests retain
two bootstrap/two direct reads. Mobile source and accepted SDK6 APK are unchanged.
The isolated2560MiB/two-core emulator is stopped; phone87 remains untouched.

Group62503/full-1 is REAPED PASS1410.192s:616 fresh cases=192 domain,75 SQL/
provider,25 delivery,174 native API/OpenAPI and FULL worker150. Zero failures/errors;
one existing elapsed-retention opt-in skip. Format/architecture and strict archived
XML/source/native/APK audit pass. [Group61e8c2f5](https://github.com/fajarxfce/hris-platform/blob/61e8c2f5d62b0f7f6406972df7191e4b32638a5c/docs/collaboration/space-original-key-publication.md) is committed/pushed.
No Mobile,SDK,live schema,runtime or phone source change was required for this fix.

Group [41c52145](https://github.com/fajarxfce/hris-platform/blob/41c52145/docs/collaboration/space-readiness-control.md)
accepts the separate readiness protocol/projection:216 cases,including actual Synapse
wake,replay,same-device bearer change and restart. Group [7d4d7f1f](https://github.com/fajarxfce/hris-platform/blob/7d4d7f1f/docs/collaboration/space-readiness-publications.md)
now accepts V139 atomic publication/revision and independent Matrix/PUSH checkpoints.
Final42132 passes335.110s:23 JVM cases,zero failures/errors/skips,plus40 Python
migration/recovery cases. Normal-role concurrency,capacity,stale leases,rollback and
actual dump/restore pass. Receiver commit-order regression first fails as expected,
then passes with the revision fence. Both original worker and fresh SDK upload capture
exactly two channels. Strict source/XML audit passes;8,708 stable inputs,no shared DI change.

Group queuea182ebf5, target acknowledgement4a5dd90f and pure admissionbebe3138
are accepted/pushed. V140 preserves the original V139 leases and allows exact target
progress under its own current guards; publisher-only settlement remains separate.
The latest accepted [99a0d8c5](https://github.com/fajarxfce/hris-platform/blob/99a0d8c5/docs/collaboration/space-readiness-control-fanout.md)
adds bounded Matrix fanout. Final21572 passes221 cases in215.181s:205 domain and16
real PostgreSQL queue/use-case cases,zero failures/errors/skips,8,725 stable inputs,
format/architecture. Actual provider rotation waits on target guards until ACK commits.
Prior9502 had one invalid expired-token fixture among220 cases; it is corrected and
recorded. Its pushes are reaped; the later worker integration below supersedes its scope.

Group [806601d5](https://github.com/fajarxfce/hris-platform/blob/806601d5/docs/collaboration/space-readiness-push-handoff.md)
accepts independent PUSH handoff through the existing durable FCM queue. Final95714
passes256 cases/463.060s,zero failures/errors/skips,8,727 stable sources. Current
binding checks,quiet coalescing,newer wakes during FCM leases,atomic rollback and
post-handoff native revocation are tested with normal-worker PostgreSQL repositories.
The existing HandleCollaborationSpacePush remains the sole cloud delivery owner.

Group [d4246a08](https://github.com/fajarxfce/hris-platform/blob/d4246a08/docs/collaboration/space-readiness-workers.md)
is committed/pushed;89491 is reaped. Shared DI and separate scheduled MATRIX/PUSH
owners are now accepted. Focused98743 passes8 cases/101.486s. Full56826 is REAPED
PASS775.848s:325 cases=174 across six API context/contract classes +151 FULL
UNFILTERED worker cases,zero failures/errors,one existing opt-in retention skip.
Both gates retain8,729 identical source inputs;exact XML and complete worker set
checks pass. One new context case opens ten feature-flag configurations,uses actual
normal-role queue claims and verifies pool termination. No actual cloud delivery.

Mobile [da248173](https://github.com/fajarxfce/hris-mobile/blob/da248173/docs/collaboration-space-readiness-receipts.md)
is committed/pushed;93828 is reaped. Eleven Kotlin inputs implement strict group V1
parsing and isolated encrypted revision receipts with SQLite/IndexedDB/Apple Room
contracts and real app DI. Focused81916 passes24 cases/244.810s. Full50781 is REAPED
PASS588.489s:4,284 fresh cases=3,641 desktop+621 Android host+22 forced browser,
zero failures/errors/skips. Required root check,Android release/lint,shared Wasm/iOS
source and quality pass.4,582 source inputs and Inspector bytes stay unchanged.

Fresh [hosted42697887863](https://github.com/fajarxfce/hris-mobile/actions/runs/42697887863)
is pinned to exactda2481732e51132fac7249fc31d732f037c2222c. Portable is now accepted:
6,662 cases (3,089 desktop+621 Android host+2,952 browser),zero failures/errors/skips;
full previous report set plus24 new protocol/storage cases verified. Audit7525 is
reaped. Job129434510765,artifact13710243749,3,296,341 bytes,GitHub digest and ZIP CRC
verified. SHA25619ce5254277f1c2c03502ea6b22fb4043b5fe7443d78fe5b8c783b69cd3aa471;
XML manifest3539a401dad1b6aa535c06a4a81799ac6ebe600fa55ffbf5e2f6f6c4e3955288.
Apple is now accepted:2,511 simulator cases,zero failures/errors/skips,and unsigned
Xcode host success. Audit30032 is reaped. Job129434510161,artifact13714460959,
492,864 bytes,SHA2561fd597c818a46793c0fa337902b0c67c764ff958f4a943da5f45b3a73f7f6595;
XML manifest60f3f42d9121c45e5839da2cecc6a4b58dbeabad60a6be00f122a3ee17834590.
GitHub digests,ZIP CRCs,every XML/report count and exact prior full sets plus36 total
protocol/storage cases are verified. Combined9,173 cases. Mobiledd15a4fd retains
[hosted evidence](https://github.com/fajarxfce/hris-mobile/blob/dd15a4fd/docs/measurements/collaboration-space-readiness-protocol-hosted-2026-11-09.json).
No remote CI remains active. Do not rerun these unchanged gates or attribute this
protocol evidence to newer native/wake/inventory sources. Native Apple Matrix stays open.

Mobile [a877c088](https://github.com/fajarxfce/hris-mobile/blob/a877c088/docs/collaboration-space-readiness-listener.md)
is also committed/pushed;68495 is reaped. This successor routes original native
clients to group controls using immutable logical source and actual physical sender,
with a separate bounded stream and one subscription handle lifetime. Focused30283
passes84 cases/198.331s;full45533 is REAPED PASS630.629s:4,297 cases=3,645 desktop,
626 Android host,26 browser,zero failures/errors/skips. Required root/platform/quality
pass,4,584 stable sources and exact XML/library audit. Protocol hosted evidence is
for the earlier immutable source,not these successor bytes. No actual provider
callback measurement,app collector,wake ACK,foreground sync activation or phone build.

Mobile [01544c26](https://github.com/fajarxfce/hris-mobile/blob/01544c26/docs/collaboration-space-readiness-wake.md)
is committed/pushed;18367 is reaped. Its independent organization wake key retains
exact generations before scheduling, and the owning use case guards current local
parent/runtime around encrypted receipt persistence and schedule handoff. Full70424
is REAPED PASS593.165s:4,338 fresh cases=3,670 desktop+626 Android host+42 browser,
zero failures/errors/skips. Required root/platform/quality checks pass;focused/full
retain identical4,593 source inputs and unchanged Inspector bytes. All XML audited.
No collector or actual group worker is bound;this is not product activation.

Mobile [eba05a9c](https://github.com/fajarxfce/hris-mobile/blob/eba05a9c/docs/collaboration-space-room-inventory.md)
is committed/pushed;38798 is reaped. Bounded encrypted room inventory now preserves
account/generation/revision cursors,authenticates both immutable indexes,advances over
empty projected pages and reports concurrent appends. Focused51195 passes25 cases;
full11069 REAPED PASS575.815s:4,328 fresh cases=3,679 desktop+626 Android host
+23 browser,zero failures/errors/skips. Required root/platform/quality checks and exact source/library/XML audit
pass with4,595 unchanged source inputs. No original binding rewrite or schema change.

[CI42707758241](https://github.com/fajarxfce/hris-mobile/actions/runs/42707758241)
is fully accepted on immutable eba05a9c253aa7fe7f0cf816b49c128b4f3003a6.
Portable6,743 +Apple simulator2,549 =9,292 cases,zero failures/errors/skips;
unsigned Xcode host succeeds. Audits75963/66359 are reaped. GitHub SHA256,ZIP CRC,
every XML and the full previous report set plus119 cases pass. Mobile495dd81b
records [complete evidence](https://github.com/fajarxfce/hris-mobile/blob/495dd81b/docs/measurements/collaboration-space-inventory-hosted-2026-11-09.json)
and is pushed;31316 reaped. This source covers cumulative listener,wake and inventory,
not later sync/progress/worker or native Apple Matrix/APNs/calling behavior.

Mobile [b51e70d8](https://github.com/fajarxfce/hris-mobile/blob/b51e70d8/docs/collaboration-space-device-sync.md)
is committed/pushed;9895 reaped. Exact original group sync passes focused85631:
37 cases/152.816s and full51820:4,340 fresh cases/526.554s,zero failures/errors/skips.
Full root,Android release/lint,shared Wasm/iOS source,browser and quality pass.
Both manifests contain4,599 identical source inputs;current source/library/XML audits
pass. The initial missing mapper import and app DI fixture failure are recorded.
Original parent-owned sync reuses its cursor;no store/key/queue is recreated.

Mobile [8bc952fe](https://github.com/fajarxfce/hris-mobile/blob/8bc952fe/docs/collaboration-space-recovery-progress.md)
is accepted/pushed;57676 reaped. Independent encrypted account/native-family/space
page checkpoints pass focused31161:28 cases/244.986s and full35632:4,376 fresh cases/
597.698s. Zero failures/errors/skips;required root/Android release/lint/shared Wasm/iOS
source/browser/quality pass. Both4,611-input manifests and all source/library/XML
checks agree. Claimed raw pages survive interruption;only a clean exhausted sweep
can return its exact wake. No schema,SDK,phone or live change.

Mobile [efa700bb](https://github.com/fajarxfce/hris-mobile/blob/efa700bb/docs/collaboration-space-recovery-worker.md)
is accepted/pushed;12097 reaped. Its group task uses independent durable wake/claims,
current native-parent cancellation,original adoption/sync/delivery and exact completion.
One processor/one waiter,four raw pages per90-second turn. Faults retain backoff.
Focused85180 passes97 cases/85.225s. Final89627 passes253.361s with4,439 validated
cases:2,489 rerun and1,950 passing reports reused from first-full83939. All240 reused
XML hashes and production inputs match;only CI and the old startup test changed.
That formerly failing suite reran successfully. Zero failures/errors/skips. Required
root,Android release/lint,shared Wasm/iOS source and quality pass;4,627 stable inputs
match focused/full. Initial fixture/quality and startup-expectation failures are retained
in the Mobile measurement;no checks suppressed. No new product APK/phone/live rollout.

[CI42721024887](https://github.com/fajarxfce/hris-mobile/actions/runs/42721024887) runs
on immutable efa700bbb5a2b7a75af392afb5add8b642eb2ba0;dispatch99990 reaped. Expected
6,940 portable cases accepted;2,628 Apple cases expected and unaccepted. It covers sync/progress/worker and expanded
app filters,not the following native proof. Earlier42707758241 is fully accepted.

Native proof is accepted and pushed: Mobile78145d78 and Group450fd51d.
[Mobile contract](https://github.com/fajarxfce/hris-mobile/blob/78145d78/docs/collaboration-space-recovery-native.md)
and [backend contract](https://github.com/fajarxfce/hris-platform/blob/450fd51d/docs/collaboration/space-recovery-native.md)
retain exact source, APK, SDK and XML evidence. Native98504 passes196.262s:2 actual
cases,zero failures/errors/skips. Each preserves exactly2 provider events. Group control
flows through durable receipt/wake and actual registered task; process death after
ACK/before Sent retains the claim and original transactions. Recovery needs no new
hint/FCM; later offline Sent reads pass. The captured real signal is explicitly replayed
into its consumer to test dedup. The fixture manually invokes the registered task.

Group scenario has22 native and16 Matrix requests,zero401/403/server errors;3 encrypted
HTTP sends include original transaction replay and create only2 events. Whole-scenario
counts are not per-message latency. Original scenario retains2 events/10 native requests.
Source/APK hashes match;no compiler during emulator;emulator stopped. Failed native34069
had no eligible control target because its fixture created the room after publication.
Only fixture ordering changed;failed userdata remains retained.

Mobile final73682 passes68.001s,required root/Android-host/quality checks. No tests rerun
in this final gate;exact current/archived XML validates4,439 predecessor and20 proof
cases with unchanged production bytes. Focused6044 ran20 proof cases/131.926s.
Group89294 source/format/architecture passes34.055s;11 driver cases pass0.005s.
Strict cross-source/APK/SDK/XML audit passes4,630 Mobile and8,733 Group inputs.
Pushes15951/83711 are REAPED. Prior Platformfb1beb63 push62231 is also confirmed
complete by clean origin-tracking status;do not restart it.

Portable hosted42721024887 is accepted6,940 cases=3,242 desktop+629 Android host+
3,069 browser,zero failures/errors/skips. Audit71793 is reaped;GitHub digest,ZIP CRC,
full previous testcase set and new cases verified. Artifact13720089441,3,385,094 bytes,
SHA2ea99a2369cd5ff6a6af84dc10a68d103900f464750d83b3b75385614f34ee7b.
Apple job remains running/unaccepted. This run pins efa700bb,not the later proof edits.

Next: complete SDK+app-ledger idle checks before redundant room transport, durable
producer wakes after final reads, group FCM/product routing, new SPACE enqueue, rich
messaging/calls/meetings. No local heavy job or emulator remains. No product88/phone
or live rollout;phone87,SDK6,live e558a686/schema116.4 and groupOFF are unchanged.
All54 PRD acceptance groups remain open. Group recovery max140 must not be copied
into the original/live max116.4 verifier without its own integration.


Hosted42656803963 is fully accepted on69e639f8:9,137 cases,including2,499 Apple
simulator,zero failures/errors/skips,and unsigned Xcode host success. Mobilea945220c
records audited artifacts. No redispatch is needed. Native Apple Matrix/APNs/calls
remain separate. All54 PRD groups stay open; no product APK88 or live rollout.
Phone87,retained sender87,live e558a686/schema116.4 and group routing OFF remain.

## Current work

Original Platform feat/communications contains the minimal reviewed readiness recovery
verifier correction in691f3fc5. The source bound advances116.1→116.4, with exact
fractional-version allowlisting;117+ remains rejected here. The new regression first
reproduced the error, then40 database-recovery/migration-order/recovery-set unit tests
passed. The original encrypted-envelope query is unchanged. No live restore or schema
mutation occurred. [Evidence](measurements/collaboration-readiness-recovery-verifier-2026-11-09.json).
The Group checkout already has the equivalent allowlist with its separate139 bound;
do not replace that bound with this branch's116.4 limit.

Group `/home/fajar/hris/.worktrees/communication-space`, feat/communication-space,
accepts merge fa90ddcc of Root4d4df31f into bf6e176a (base8bad394e). It is committed
and pushed;34015 is reaped. Eight textual conflicts preserve company runtime/readiness/
default-key fixes and group behavior. It is NOT DEPLOYED. Focused49700 passed207 domain/data cases but failed
API test compilation: five group-only fixture calls retained a renamed method. Those
calls now use the merged accountData fixture with the same m.direct default.
Focused67950 then failed one HTTP case because its frozen fixture clock preceded the
actual PostgreSQL request-lease start. Only that real-provider fixture now follows
system time; admission and the expected503 response remain unchanged. Corrected24065
passes both HTTP cases in75.409s. Full33511 is REAPED PASS5,607.828s on8,686 unchanged
inputs:1,444 fresh cases, zero failures/errors and one existing elapsed-retention opt-in
skip. Database138/domain189/data762/delivery25/API180/FULL worker150 and quality/
migration/recovery checks pass. Archived fresh XML, both readiness worker contexts,
all new default-key/isolation regressions and exact hashes are audited in Group
`docs/measurements/collaboration-root-source-reconciliation-2026-11-09.json`. Do not
reset, repeat or redeploy this accepted source merge.

Mobile feat/chat-send-timing-20261107 now has accepted/pushed78076d4e: owning published
room binding preparation. It first reads owned immutable local metadata; only absence
performs bounded live acquisition and exact readback. Final focused50366 passes26;
full81742 passes4,224 fresh cases (3,587 desktop/613 Android host/24 actual browser),
zero failures/errors/skips,4,551 stable application/library inputs. Root/release/Wasm/
iOS-source/quality pass. Original denial/expiry regressions remain recorded. No SDK/
worker/UI activation or new APK follows from this change. Hosted collaboration-control
[42628449460](https://github.com/fajarxfce/hris-mobile/actions/runs/42628449460) is now
SUCCESS on exact78076d4e. Both artifact digests/ZIP CRC/XML are verified:9,003 cases,
including2,457 Apple simulator and the exact21 domain+3 actual database preparation
cases on each shared target; zero failures/errors/skips. Xcode builds the unsigned
simulator host. Evidence and continuation are committed/pushed in Mobile7e636567.
This is separate from APNs/calling, native SDK6 Matrix delivery and signed distribution.
The [latest Mobile handoff](https://github.com/fajarxfce/hris-mobile/blob/feat/chat-send-timing-20261107/docs/agent-handoff.md)
is authoritative for subsequent transitions; do not redispatch this accepted CI run.

Group direct transport is ACCEPTED/COMMITTED/PUSHED as96d2d238 (85313 reaped).
The12 Kotlin files expose one current native device/room/recipient GET. Verified
combined evidence covers452 cases:192 domain,38 SQL,72 API/OpenAPI,150 complete worker;
zero failures/errors and one existing elapsed-retention opt-in skip. Full38552 and
focused34852 preserved compile/fixture failures. Final43271 passes nine fresh cases
in148.630s on8,693 unchanged inputs. Only three SQL/API fixtures changed since the
complete worker run; exact source/XML comparison retains unchanged broader proof.
Actual retained LEGACY publication proves exact original room/user/physical device.
No production policy/schema/DI was changed to resolve test fixture errors. See Group
`docs/measurements/collaboration-space-direct-transport-2026-11-09.json`; do not rerun.

Mobile direct transport ab17eadd is accepted/pushed:23 focused/4,237 full cases,
zero failures/errors/skips. Owning original delivery69e639f8 is accepted/pushed:
56 focused/4,304 full cases (3,629 desktop/621 Android host/54 browser), zero failures/
errors/skips;4,571 unchanged inputs/library and exact source/XML audit. Two new review
regressions first reproduced inventory-order rejection and unbounded post-ACK storage;
corrected source preserves original IDs, late ACK journals and absent-original uncertainty.
Source does not activate group UI/worker/SDK delivery or deploy the backend.

The former Mobile4649 compilation/native-proof work is complete; its failed first
attempt and corrected source/native/hosted gates are retained in the current Mobile
handoff. Do not restart it. Current work follows the accepted worker publication above.
SDK6 remains immutable; all original stores/messages remain preserved.

## Accepted runtime and device state

Live API/worker remains e558a6868399058a2dcdee97acfd0e7fa7f1ef07, schema116.4,
readiness control ON. Group V117–139 is absent here and remains unshipped; organization
routing is OFF. None of this source reconciliation changes live runtime.

APK87 from Mobile d8383438 is ALREADY INSTALLED. Install33957 passed210.86s with exact
digest and unchanged first-install identity; no phone UI driving. SHA256
43373c6c4e6335149138630e7c2c9d6daa05edc1f76729ff01dae1023916ce8b.
No APK88 exists; do not reinstall87. Retained native86→87 proof passes670.401s with
four quiet sync phases, zero setup/rejected traffic, no room_join and paired refresh
200×3. One new Joined87 send took2,066.684ms; this is one emulator sample, not phone/p95.

Latest COMPLETE STOPPED sender store is Mobile
`.work/joined-room-entry-native-2026-11-09/sender`. Preserve the whole AVD and all four
userdata/encryption files, plus stopped Bima74. Eight originals must NEVER be resent:
Control77 235408, Warm77 235728, Recovery78 005634, Registry82 055959, Parent83 071123,
History84 083059, Selected86 102110, Joined87 115837. Draft66 092041 must NEVER be sent.

Mobile public inspector CI42611795210 passed7,950 cases with audited artifacts;
Root integration57ebc77a is accepted. Inspector rootf8ca3d6/isolateda6b8236 use public
immutable SDK26.10.23-hris.6 (release438835525); never rebuild/overwrite those assets.
Inspector742a2d1 CI passed226 portable/host+69 Apple simulator cases. Native Apple
Matrix bindings remain separate. Preserve isolated Inspector's unrelated gradlew.bat
change. Starterffd0ac5's generic pinned workflow/checksum port passes root/Android/
Web/iOS-source/quality/SBOM and62 fresh browser tests; no Starter hosted distribution.

## Next work

1. Continue Mobile's current native experiment and source4649, then the actual isolated
   Group server/SDK/restart proof. Track hosted42656803963 at exact69e639f8 and audit
   artifacts when complete. Accepted prior source gates must not be repeated.
2. Continue owning original SDK delivery/history, recipient readiness, background
   ownership and group product routing. Room Prepare/Resolve/Replay, exact original
   references and encrypted binding preparation are accepted; do not recreate them.
3. Continue rich message actions/media/stickers/voice, encrypted Drive/iCloud recovery,
   native MatrixRTC call lifecycle, LiveKit meetings and all remaining PRD groups.
   Whole acceptance groups need their product/provider/lifecycle proof, not domain
   tests alone. Current scope is docs/collaboration/prd.md and delivery.json.
