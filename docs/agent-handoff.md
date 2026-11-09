# Agent continuation — HRIS workspace

Updated 9 November 2026, 15:34 UTC. Historical detail remains in
[4d4df31f](https://github.com/fajarxfce/hris-platform/blob/4d4df31f/docs/agent-handoff.md).
Use this checkpoint and the linked current Mobile/Group files, not old active-job notes.

## Scope and boundaries

The user authorized the entire communications PRD, long-running implementation,
atomic commits with pushes and validated phone updates. All54 acceptance groups remain
open. Mobile proves reusable foundations before Starter. Follow AGENTS.md layer
ownership and preserve original accounts, native/SDK identities, keys and transactions.

One heavy local compiler OR emulator. Do not kill live API/worker JVMs. The emulator
uses2560MiB/two cores and is stopped. Phone10.77.77.3:5555 is install-only; announce each
installation and never launch, automate, uninstall or clear its app. Crashlytics IAM
is verified; do not request it again. Physical iPhone testing is waived, relevant
Apple runtime, push and calling acceptance remains separate.

## Current work

Original Platform feat/communications contains the minimal reviewed readiness recovery
verifier correction in691f3fc5. The source bound advances116.1→116.4, with exact
fractional-version allowlisting;117+ remains rejected here. The new regression first
reproduced the error, then40 database-recovery/migration-order/recovery-set unit tests
passed. The original encrypted-envelope query is unchanged. No live restore or schema
mutation occurred. [Evidence](measurements/collaboration-readiness-recovery-verifier-2026-11-09.json).
The Group checkout already has the equivalent allowlist with its separate138 bound;
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

The finite Group direct-transport candidate is now applied after exact accepted/base
checks. Eleven Kotlin files include seven SQL and three pure policy cases, HTTP/OpenAPI
and shared DI. Full38552 is the only heavy local job and remains RUNNING. Its first
compile attempt found two fixture errors: the repository override parameter must be
named id, and the Native fixture is not a data class with copy(). Two test-only
corrections are prepared privately and formatted1.774s; do not change its frozen source
until reaped. Full worker is still running under --continue. Preserve this failure;
actual new SQL/API execution remains required after the correction.

Mobile has13 uncompiled transport files/eight methods. Its private original follow-up
now has seven files/ten pure methods for matching and exact journal/archive ACK facts,
formatted0.917s only; no owning delivery implementation or runtime activation. Device
origin stays separate from user origin. Current direct contracts must pass before
applying that follow-up. Latest Mobile checkpoint deb8ba1f is pushed.

## Accepted runtime and device state

Live API/worker remains e558a6868399058a2dcdee97acfd0e7fa7f1ef07, schema116.4,
readiness control ON. Group V117–138 is absent here and remains unshipped; organization
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

1. Reap Group direct-transport full38552, preserve its fixture compile failures, apply
   only the prepared exact test corrections and complete the missing SQL/API checks.
   Validate shared production inputs against the full worker result before acceptance.
   Then validate Mobile transport. Reconciliation fa90ddcc and hosted Mobile42628449460
   are already accepted; do not rerun them.
2. Continue owning original SDK delivery/history, recipient readiness, background
   ownership and group product routing. Room Prepare/Resolve/Replay, exact original
   references and encrypted binding preparation are accepted; do not recreate them.
3. Continue rich message actions/media/stickers/voice, encrypted Drive/iCloud recovery,
   native MatrixRTC call lifecycle, LiveKit meetings and all remaining PRD groups.
   Whole acceptance groups need their product/provider/lifecycle proof, not domain
   tests alone. Current scope is docs/collaboration/prd.md and delivery.json.
