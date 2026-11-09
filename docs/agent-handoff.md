# Agent continuation — HRIS workspace

Updated 9 November 2026, 20:14 UTC. Historical detail remains in
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
recorded. All pushes are reaped; no shared DI or worker activation is accepted yet.

Current Group work is the independent PUSH handoff. The existing fanout is renamed
FanOutCollaborationSpaceReadiness to share current-target admission across channels;
PUSH atomically appends a quiet wake to the existing FCM queue with cursor progress.
HandleCollaborationSpacePush remains the actual delivery/token/retry owner. This
successor is NOT yet accepted. See the Group handoff and private
`.work/space-readiness-push-handoff-2026-11-09` for its latest gate;95714/focused-1 was
running when this note was written. Do not edit frozen Group inputs or start a second
heavy job until it is reaped. Do not reapply old prepared candidates or replay accepted
native/hosted gates. Next: full API/unfiltered worker integration, then Mobile durable
group foreground/background ownership and product routing. No Mobile source,SDK7,
APK88,phone or live rollout; all54 acceptance groups remain open. Group recovery max140
must not be copied to Root/live schema116.4.


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
