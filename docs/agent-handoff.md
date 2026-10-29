# Agent continuation — HRIS workspace

Updated 29 October 2026 (UTC). **The overall PRD is still in progress.** The user
asked to continue through the remaining PRD, push each commit, and keep continuation
notes in Git. Read this file first, then the compact [PRD status](prd-status.md),
[completion](completion.md),
[product](product.md), the relevant module contract and each repository's `AGENTS.md`.

## Current result and continuation

Replacement run `40820005213` exposed one intermittent browser-test ambiguity:
the return-navigation assertion matched both the page `h1` and loaded-section
`h2` named **Closing employees**. Dashboard shard 1 had 274 passes/one failure;
the other three dashboard shards passed. Backend/recovery work is still running.
The existing scenario now waits for the returned table and explicitly selects
the level-one page heading. All four closing-report browser cases passed in 10.4s,
with strict TypeScript and Biome; no application implementation changed.

Push this correction and verify its exact head on an owned temporary CI ref so
the active recovery run is not cancelled by branch concurrency. Record the new run
URL here and retain the original failing trace. Do not call either earlier full
run successful; inspect the corrected full matrix and sanitized restore artifact.

First Verify run [40816036721](https://github.com/fajarxfce/hris-platform/actions/runs/40816036721)
passed all four backend and all four dashboard shards at `d7184e83`: 2,731 disjoint
API tests, 94 worker passes (one separately accepted manual retention case skipped),
686 module tests, 3,997 dashboard unit tests, seven native-proxy regressions and
1,098 distinct Chromium scenarios. Repeated worker/module/unit suites are counted
once. Dashboard integration also passed: 40 API/browser cases, 14 worker cases,
one real-storage export case, two mail and nine storage fixture checks.

The actual schema-101 recovery step also passed (104 objects, 2,098,795 bytes,
two encrypted identity records, old/current keys, exact audit/delivery projections
and cleanup), but its artifact-upload step failed: upload-artifact excludes hidden
paths by default. The workflow now opts in only for the exact sanitized
`.work/recovery-set-result-*.json` glob, never the whole private directory. YAML
and result assertions passed locally. The first run concluded failure solely at
that upload. Replacement [40820005213](https://github.com/fajarxfce/hris-platform/actions/runs/40820005213)
is running at pushed `70e5098d`; inspect every job and the downloaded recovery
artifact before recording success. [Sanitized measurements](measurements/ci-2026-10-29.json)
retain both runs and distinguish execution from artifact publication.

[Android Attendance capture recovery](native-attendance-acceptance.md) passed
actual API/PostgreSQL/worker acceptance. One capture without location survived
committed-response loss and offline cold start; summary reload stayed 21 → 21.
Exact replay retained one version-zero event and its original capture time. All six
local summary rows cleared while the server correctly retained pending independent
review, OFFLINE/UNVERIFIED_CAPTURE flags and zero accepted minutes. Across 30 native
requests there were two intended writes. Fixture 4.6m/5.2m with setup; proxy,
TypeScript, Biome, build and architecture passed. All owned resources were cleaned;
location was restored. No Mobile implementation or Starter port applies.

Application artifacts remain integrated `b7f42070` / schema 101. This fixture
follows Leave `ccae6f8f`, Expense `97e33310` and Overtime `578a5031`. Mobile `b0a95391`
records Leave acceptance. Native pending-summary evidence now has an actual
recovery example for all six feature observers, with separate workflow outcomes
and limitations retained; this does not prove every combination or physical device.
No application defect was found in these three newest native cases.

Continue the current CI/PRD reconciliation. Mobile full CI
[40815794384](https://github.com/fajarxfce/hris-mobile/actions/runs/40815794384)
at `b0a95391` passed Linux/Android, Windows and Web but Apple cold compilation
exceeded its combined 15-minute test limit. Mobile `451a2c4d` separates compilation,
test execution and Xcode bounds. Its Apple-only follow-up
[40817592306](https://github.com/fajarxfce/hris-mobile/actions/runs/40817592306)
is running; port that generic CI correction to Starter only after it passes.
Mobile `678783b3` separately corrects English Expense item-count copy, with local
root checks and Android assembly passed. Hosted runs above predate this copy edit.
All native-owned resources are cleaned. No release/distribution was invoked.

The eleven module screen/acceptance lists were reread and source TODO/placeholder
searches found no obvious stub. The [compact review](prd-status.md) maps all eleven
module contracts to actual workflows, the six native cases and external inputs.
Do not call fixture-only acceptance the whole PRD complete.
FCM credentials and Google/physical/iOS setup still need external inputs; do not
repeat the pending Firebase credential question.

## Latest result: real elapsed retention passed

The original checkout's separately owned export-retention acceptance finished
successfully at 03:17:16 UTC on 29 October. JUnit confirms one pass in 3,860.892s;
Gradle completed in 1h4m32s. The real one-hour grace, retained quota/objects,
two physical deletions, lost delete receipt retry and single audited retirement
all passed; live export data and inactive owners were preserved. See
[elapsed retention](export-retention-acceptance.md) and its tracked measurements.
The test is no longer running, so this checkout may be rebuilt.

Pushed feature commits `409abad4` (push delivery/schema 101) and `f757bffd`
(payroll comparison) are integrated into this branch, preserving elapsed result
`1abdbce9`. Runtime/source changes match the validated feature branch; the merge
changes continuation/result documentation only. All test-owned containers are gone.
Mobile documentation reconciliation `42f5e04d` is pushed. Native Overtime committed-response loss and summary/retry are now accepted above.
Provider credentials and physical/iOS cases remain separate.

## Workspace and delivery policy

The workspace is `/home/fajar/hris` with three sibling Git repositories:

| Repository | Branch | Last completed/pushed increment |
| --- | --- | --- |
| `hris-platform` | `feat/firebase-auth` | Integrated `409abad4` + `f757bffd`; elapsed acceptance `1abdbce9` |
| `hris-mobile` | `feat/native-push` | `ad675e57` — native attendance evidence; app fixes `02743a9f` |
| `compose-fluent-starter` | `fix/push-token-lifecycle` | `5bfa9a9` — current CI/native continuation; validated foundation `a16325e` |

The latest increments verify delivery observations, original payroll comparison
and actual elapsed export retirement.
Inspect `git status`, `git log` and
the upstream before continuing. Mobile and Starter were clean at this update.

Mobile was generated from Starter and is the proving ground. Validate reusable
foundation corrections in Mobile before porting them to Starter. HRIS business
features stay in Mobile/Platform. The latest Platform fixture verifies Mobile
registration fixes; no Starter port applies.

The user has authorized continued implementation and a push after every atomic
commit. Do not ask again for routine edits, tests, commits or pushes. Keep the
implementation, relevant tests and documentation together. Preserve unrelated work,
do not add co-author trailers, and do not infer release/live deployment permission
from a Git push. Completing an increment does not complete the PRD.

`/home/fajar/hris/PRD-PROGRESS.md` and `REPO-TRACE.md` are local supplementary notes
outside these Git repositories. The tracked documents linked here are the durable
handoff; a fresh clone must not depend on those local files or previous chat history.

Both [Mobile](https://github.com/fajarxfce/hris-mobile/blob/feat/native-push/docs/agent-handoff.md)
and [Starter](https://github.com/fajarxfce/compose-fluent-starter/blob/fix/push-token-lifecycle/docs/agent-handoff.md)
now have their own tracked entry point, linked from `AGENTS.md` and `README.md`.
Their handoff-only commits were pushed and remote heads verified. Current module
summaries distinguish completed authoring/export/worker work from historical
implementation stages, so an old "planned" paragraph cannot silently become the
next task. These documentation changes require no application test rerun.

## Integrated results: delivery observations and payroll comparison

The separately run [elapsed-time export acceptance](export-retention-acceptance.md)
is complete. Its original checkpoint, final measurement and JUnit result are
tracked; no live classpath must be preserved now. Feature work used the isolated
`/home/fajar/hris/.worktrees/push-delivery` worktree while the one-hour test ran.
Both feature branches are now integrated; no Mobile/Starter business port applies.

[Push delivery](announcement-delivery.md) has current/original permission and
credential/MFA guards, a bounded operational aggregate, a minimal V101 forced-RLS
projection and a localized explicit-refresh dashboard. Raw recipient/session RLS
is unchanged. Upgrade waits for existing dispatch writers, and worker updates and
cleanup capture atomically. History loss never implies successful delivery.

Validation passed 117 API, 94 worker and three repository tests in 4m33s; the
manual elapsed-retention test is the one skipped worker case. Dashboard check
passed 3,974 tests in 349 files (96.93s), TypeScript, Biome (two existing CSS
warnings), architecture and production build. Thirteen distinct Chromium cases
passed across focused runs. The new foreground case found a duplicate request;
visibility now settles with workspace admission before loading. The inspected
Indonesian screenshot fits 390px. Both JARs and architecture/format checks passed.
Real API/PostgreSQL/worker/browser publication, terminal/cleanup display and
unchanged Inbox/read-only audit assertions passed in 45.5s (1.8m including setup).
Terminal/retention states there are controlled SQL fixtures, not FCM proof.
Twenty recovery-tool cases and complete schema-101 recovery passed: 104 objects,
2,098,795 bytes, two encrypted records, old/current keys, exact delivery/audit
projections and retained RLS/trigger privileges. The first recovery fixture tried
a prohibited self-binding; its replacement uses an independent fictional recipient.
Both attempts' owned resources/private configuration were removed.

Commands and supplementary logs in the worktree:
- `./gradlew --no-daemon :modules:communications:data:test :apps:server:test --tests '*Announcement*Test' --tests '*ApiContractHttpTest' :apps:worker:test :apps:server:bootJar :apps:worker:bootJar` → `.work/push-delivery-backend.log`.
- `npm run check` in `apps/dashboard` → `.work/push-delivery-dashboard-check.log`.
- `npx playwright test tests/announcement-delivery.spec.ts tests/announcements.spec.ts tests/announcement-publication.spec.ts`; the final delivery-only run is `.work/push-delivery-browser-rerun.log`.
- `npm run test:integration:worker -- communications.spec.ts` → `.work/push-delivery-integration.log`.
- `python3 tool/check_recovery_set.py` → `.work/push-delivery-recovery-real.log`.
- [Tracked measurements](measurements/announcement-delivery-2026-10-29.json) retain sanitized results; local logs are supplementary.

[Payroll period comparison](payroll-period-comparison.md), `f757bffd`, is integrated
with its parent push delivery feature. No migration beyond V101 or Mobile/Starter
port is needed. Both company payroll runs must have complete successful original results.
One bounded SQL snapshot returns exact whole-run totals, employment matching and
cursor pages. Current/original permissions, policy/resource guards and pre/post
MFA protect disclosure. The UI clears obsolete amounts and owns explicit reads.

Dashboard full check passed 3,997 tests in 352 files (56.38s), TypeScript,
architecture and Biome (two existing CSS specificity warnings). Production build
and both inspected browser layouts passed. Exact outcomes are retained in
[measurements](measurements/payroll-comparison-2026-10-29.json).

Payroll validation: 119 API, 94 worker and 12 data tests passed in 3m52s; the elapsed
manual worker case was skipped by that ordinary suite and later passed separately. Architecture
and Kotlin formatting passed in 21s. Twenty-three distinct Chromium cases passed
across runs, including 50/1 employee pagination and narrow Indonesian layout.
Actual five-actor January approval/publication and February calculation/browser
acceptance passed in 18.5s (42.3s including setup), proving retained tax continuity,
exact deltas, no comparison writes and live grant withdrawal. Owned fixture
sessions/services/database were removed. See the tracked
[measurements](measurements/payroll-comparison-2026-10-29.json).

Commands in the worktree:
- `./gradlew --no-daemon format :modules:payroll:data:test :apps:server:test --tests '*PayrollRun*Test' --tests '*ApiContractHttpTest' :apps:worker:test :apps:server:bootJar :apps:worker:bootJar` → `.work/payroll-comparison-backend.log`.
- `npm run check` in `apps/dashboard` → `.work/payroll-comparison-dashboard-final.log`.
- `npx playwright test tests/payroll-comparison.spec.ts tests/payroll-calculations.spec.ts tests/payroll-reviews.spec.ts`; final comparison-only run → `.work/payroll-comparison-browser-complete.log`.
- `npm run test:integration:worker -- payroll-comparison.spec.ts` → `.work/payroll-comparison-real-browser-visual.log`.
- `./gradlew --no-daemon architectureCheck formatCheck` → `.work/payroll-comparison-architecture.log`.

Continue independent PRD reconciliation after every push. Mobile `42f5e04d` now
corrects its stale local-first next-work list: all nine data-sync registrations,
account-owned directory/profile/catalog scopes and six pending-action observers
exist. Current employee drafts already cover Expense, Leave and Overtime.

The owned Android Overtime fixture described above now closes its original
plan-response-loss/summary combination. Earlier normal delivery and blocked-before-
admission acceptance remain in the Mobile ledger. Continue the explicit Expenses
case; do not repeat the pending credential question or declare the PRD complete.

## Current checkpoint: native upload and inspection review

[Android upload/review recovery](native-upload-acceptance.md) now passes real
API/PostgreSQL/worker/Garage acceptance. A committed one-MiB chunk response was lost;
Android retained the upload through offline restart and replayed the exact command.
Failed inspection review survived a second offline restart. Explicit inspection
retry, cancellation, local deletion and logout passed; six summary rows became
empty before deleting the cancelled file. Counters stayed 32 → 32 and 42 → 42 on
summary reload. The fixture observed 57 requests, nine intended company writes,
four single-attempt chunks and one unpublished cancelled revision, passing in
8.7m/9.2m with setup. Scanner-unavailable jobs were stopped through the real admin
API; no database state or verdict was fabricated. Three proxy regressions, strict
TypeScript, Biome, production build and architecture/format checks passed. All
owned resources were removed. Application code and Starter need no change.

Continue with actual export physical retention/expiry against owned storage and
other remaining product acceptance. Google token exchange, FCM sending/scoped hints
and iOS/physical cases still need their external setup. Do not stop after this
checkpoint or repeat the pending Firebase credential-location question. Native
upload recovery/review needs no Mobile implementation fix or Starter port; record
the paired Mobile validation commit after pushing the fixture.

## Previous checkpoint: actual Firebase native registration

[Android Firebase registration](native-push-acceptance.md) now passes actual SDK
issuance, real API registration, committed-response loss, offline restart,
original-command recovery, explicit token retirement and logout. Mobile `02743a9f`
fixes submillisecond expiry rejection and cleanup of an absent prior session.
The final fixture observed 51 requests, one registration, two identical PUTs,
one disable, final version 1 with no retained token, and no company mutation.
It passed in 9.7m/10.3m including setup, alongside 1,840 Mobile desktop/UI tests,
113 identity Chromium cases, Android assembly and bounded Platform proxy checks.
Owned resources were cleaned. FCM sending/scoped hints still need server
credentials; Google token exchange and iOS/physical acceptance remain separate.

The tracked fixture/procedure is [native push acceptance](native-push-acceptance.md).
It uses the real Firebase SDK and HRIS API/PostgreSQL/worker; it never injects a
provider token. The manual first run found an 855-microsecond mismatch between
server expiry and the client's millisecond family boundary; an upgrade recovered
the existing operation. A fresh database also exposed a retained absent family;
the second upgrade cleared only that exact prior-owner absence before new
registration. The final full case passed, while the initial timed-out run is
retained separately and not counted as a pass. API/worker JARs remain from
`c282fa60`; this increment changes acceptance tooling/docs rather than server DI.

Both native proxy regressions, TypeScript/Biome, production browser build and
root architecture/format checks passed. The 512-event history is complete for the
observed 51 calls; a larger fixture history fails verification. Request/response,
connection/admission and execution bounds are explicit. Cancellation/error paths
close upstream requests, including oversized input and pending streams. Both
proxy files run serially in CI because they own fixed loopback ports. No hosted
CI success is implied. All owned services/private connection files, markers and
forwarding were removed; emulator data retained and connectivity restored. Only
the dedicated emulator was connected, so no phone install is claimed.

Continue independent remaining PRD acceptance after pushing. The question for the
Firebase server credential path is still pending; do not repeat it or equate the
public Android config with an FCM sender credential. Scoped message delivery,
Google identity exchange, iOS/APNs/physical device work and other incomplete
module acceptance remain. These two fixes are HRIS registration contracts, with
no corresponding Starter policy or changed generic SDK source.

## Previous checkpoint: typed account-access audit

[Saved account-access audit](account-access-audit.md) adds
`GET /api/v1/administration/audit-events/{eventId}/account-access` and an owned
Fluent detail section at `/platform/audit`. Both global `audit.read` and
`identity.manage` are required before and after acquisition; the shared account
guard, current credentials and MFA are enforced. V100 backfills/captures only
valid global `account` / `identity.account_access_saved` records into an immutable,
source-validated typed projection. Raw journal RLS and metadata-only reads remain
unchanged. No previous values/version are invented; later edits leave the saved
status, permission codes and reason unchanged.

Dashboard strict mapping matches event/account IDs and bounds Unicode reasons
and unique permission codes. Closing/hiding/replacing scope or grants aborts and
clears private details, with stale-response and explicit-retry coverage. All 3,956
dashboard tests in 347 files passed with architecture/Biome/TypeScript. Both JARs,
architecture/format checks and 206 distinct backend cases passed (97 API,
94 worker, fourteen administration data and one migration).
Eleven distinct focused Chromium cases cover shared metadata and the new panel.
The real three-account API/PostgreSQL/worker/browser workflow passed in 7.9 seconds
(38.6 including setup): independent author, both-grant disclosure, metadata-only
denial, immutable history after another change, and management-grant revocation
with session/UI cleanup. The exact fixture and test corrections are recorded in
the contract; no live provider/deployment claim is implied.

Full schema-100 recovery passed with old/current keys, two encrypted records,
104 objects/2,098,795 bytes and all cleanup checks. The recovery fixture also
restores all six typed fields of one source-captured audit row exactly. Twenty
recovery-tool cases passed. The encryption-family review permits raising the
ceiling from 99 to 100. Relevant supplementary logs use `.work/account-audit-*`.

Continue the remaining PRD after pushing this increment. The user explicitly
rejected stopping at a checkpoint. Other audit source families, real provider
health/delivery and native Google/FCM/iOS acceptance remain. This turn rechecked
FCM server credentials (none in the bounded workspace/config scan; relevant env
variables unset) and asked the user for the credential file path, with no reply
yet. Do not repeat the question or wait when independent work is available.

## Previous checkpoint: native partial-download acceptance

[Android partial-download recovery](native-download-acceptance.md) passed against
actual API/PostgreSQL/worker/Garage with a fictional accepted document inspection.
A one-MiB partial file and Pending actions transfer count survived offline process
restart; summary open/reload made no request (31 → 31). Resume began at byte
1,048,576 and the native saved copy matched all 3,145,841 source bytes/SHA-256.
All six summary rows became empty and sign-out succeeded. The independent fixture
observed 49 requests, four correct ranges and no native company mutation, passing
in 9.7m (10.2 with setup). Proxy concurrency/disposal regression, Biome, TypeScript,
production build and architecture/formatting passed. The unchanged loopback APK
was reused; application source and Starter need no change. Owned resources and
control files were removed, and the emulator stopped with app data retained.

Read the [tracked fixture/procedure](native-download-acceptance.md) for exact
reproduction and source-publication limits. The source file was uploaded via API
and Garage; its accepted inspection metadata was seeded under normal constraints.
This case does not repeat antivirus acceptance. The proxy records only bounded
sanitized metadata and range hashes; a Node regression covers competing admission
and pending-stream cleanup. The sixteen-content-attempt bound is checked before
forwarding. The standalone native-download config owns storage and selects one
manual case; the Inbox config retains its original case selection.

Local evidence is named in the contract. No native/server fixture is active.
The APK was reused from the accepted loopback build, and no application source
changed. Mobile's validation, pending-actions, document contract and handoff record
these results; no Starter port is required.

Next: continue remaining independent PRD work after pushing this increment. Global
metadata audit is complete; detailed source-aware disclosure and provider-health
observations remain separate contracts. Native Pending actions now has real draft,
unconfirmed Inbox-command, partial-download and upload/review recovery evidence; other feature
and other feature outcomes retain their own coverage. Recheck actual provider
configuration before asking for missing external inputs already listed below.

## Previous checkpoint: global platform audit and schema-99 recovery — `adfb7b32`

[Global platform audit](audit.md#platform-history) is implemented through a separate
V99 metadata projection and explicit global `audit.read` grant. Account/MFA guards,
source-bound append-only capture and unchanged journal RLS protect disclosure.
The dashboard supports auditors without companies, bounded search/pagination and
per-account cancellation. Validation passed 250 distinct backend cases including
all 94 worker tests, 3,949 dashboard tests, nine focused Chromium scenarios and a
real API/PostgreSQL/worker/browser workflow (8.5 seconds; 53.9 with setup). The
latter verified an independent grant, another actor's actual journal metadata,
52-row pagination, company-only denial and revoked-session cleanup. Both JARs,
architecture/formatting and twenty recovery-tool cases passed. Full isolated
recovery passed at schema 99 with 104 objects/2,098,795 bytes and old/current keys.
Owned resources were removed and screenshots inspected. Detailed source-aware
audit disclosure and the remaining native/provider work remain separate; this
increment needs no Mobile/Starter port or live deployment.

Read [audit contract and validation](audit.md#global-audit-validation--29-october-2026)
for the exact query, disclosure, permission, lifecycle and regression contracts.
The V99 trigger captures existing/new global metadata atomically without widening
private `audit_entries` reads. Existing accounts receive no implicit grant; global
account access review now lists `audit.read`. New bootstrap administrators include it.
The pure cursor policy shares only navigation fields between concrete global and
company projections. Dashboard responses reject private/foreign scope fields.

Local logs and the reproducible full matrix are linked in that contract. The
first backend matrix passed in 4m3s; subsequent identity/catalogue and OpenAPI
checks corrected fixture expectations without changing application behavior.
The OpenAPI check now runs in the already-enabled API-contract fixture; use the
final contract log for its successful result. The full dashboard run passed in
73.66s. The failed raw Docker-group invocation started no accepted fixture; the
corrected real run cleaned its owned services and connection metadata. No fixture
or emulator is active at this checkpoint.

Next: continue the PRD rather than ending after this commit. The subsequent native
partial-download recovery case is accepted in the current checkpoint above.
Detailed source-aware audit disclosure, provider health and the external Google/
FCM/iOS/device inputs remain their own contracts; no completion is inferred from
this metadata explorer. Continue independent work, update these tracked notes,
and push each atomic commit as already authorized.

## Previous checkpoint: unconfirmed Inbox command on Android — `cf2bb991`

Mobile `c650e0d7` records a successful Android 35/API/PostgreSQL/worker command
workflow. The new tracked [native fixture and protocol](native-acceptance.md)
drops a Mark as read response after commit, holds native transport unavailable,
and independently validates the exact-operation replay. The device retained
**Awaiting confirmation: 1** through an offline force-stop/relaunch, including
the original Retry action. Opening/reloading Pending actions kept the counter at
30 → 30. Restoring transport and retrying produced exactly two forwarded read
commands with equal operation/input hashes, server version 1 and no acknowledgment.
All six summary rows became empty and sign-out succeeded. Total native requests: 42.

The fixture passed in 6.0 minutes (6.4 with setup); the existing Mobile dev source
assembled in 45 seconds using only a loopback endpoint override. TypeScript, Biome
and the fixture's production browser build passed. Owned API/worker/database,
private connection metadata, markers and forwarding were removed; the emulator
was stopped without wiping data. Screenshots were inspected. An initial System UI
warning and native driver activity/nested-navigation targets were corrected during
execution; the app workflow and independent server assertions completed.

Logs: `.work/inbox-native-fixture.log`, `inbox-native-server-result.json` and
`inbox-native-http.json`; Mobile has `.work/inbox-command-*` images/results and
`.work/pending-command-assembly.log`. Source/steps are tracked, while raw fixture
diagnostics stay ignored. There is no native fixture active. Application source
and shared foundations did not change, so this requires no Starter port.

The subsequent global-audit implementation and validation are recorded in the
current checkpoint above. Detailed disclosure remains a separate contract.
Continue the remaining PRD after each commit; do not end the long run at a checkpoint.

## Previous checkpoint: real export storage interruption — `3770b3ff`

The [export workflow](report-exports.md#real-storage-response-loss-and-resumed-output)
now runs through an owned bounded loopback proxy in front of real Garage. The
second successful PUT loses its response after provider storage; further traffic
is refused until an explicit restoration. The worker retains the accepted first
500 rows. Because transient errors retain bounded retries, the owner explicitly
cancels and waits for a terminal job before resuming. Attempt two keeps the first
upload and original expiry. Its committed resume response is also lost and the
same operation/input recovers the same receipt. The final CSV contains precisely
1,201 unique rows, three parts (500/500/201) and 399,053 bytes with matching digests.

The full real API/PostgreSQL/worker/Garage/Chromium case passed in 20.4 seconds
(45.9 including setup); nine storage-fixture/proxy tests, architecture, Biome,
TypeScript and production build passed. The initial expectation that a transient
failure immediately ends the job and an ambiguous browser status selector were
corrected before success. All owned services and private connection/control files
were removed; screenshots were inspected. No application JAR or DI graph changed.
Evidence: [tracked report](measurements/report-export-storage-recovery-2026-10-28.json),
`.work/storage-recovery-browser-3.log`, `.work/storage-fault-tool-final.log`.

Run `npm run test:integration:exports` in `apps/dashboard` with JDK 21/Docker and
both current JARs. Its config enables the optional fault boundary. CI runs the
same case and all nine storage tooling tests. This does not claim physical expiry
or a hosted CI result; uncertain objects retain their existing cleanup reservations.

The subsequent native Pending actions acceptance covers a command whose server
write committed but response was lost, across offline process restoration and
exact-operation recovery; it is recorded above. The loopback dev APK and emulator
data are retained, but the emulator and fixture were stopped. This HRIS-specific
acceptance requires no Starter port.

## Previous checkpoint: running worker reports and schema-98 recovery — `e806caed`

[Integration reports](integrations.md#running-worker-observations) now show four
actual worker-bound flags (SMTP/FCM/storage/scanner), process UUID and first/last
database publication times alongside the existing six API flags. The endpoint is
`GET /api/v1/administration/integrations/workers`; the screen remains
`/platform/integrations`. The worker reporter publishes every 30 seconds, owns its
timer and stops/joins it on shutdown. Its use case serializes pruning/publication
with a registry transaction guard, retains reports for five minutes and caps normal
admission at 64 processes. V98 permits worker-role mutations only, preserves
startup identity/flags and monotonic timestamps, and adds no encrypted family.

The response distinguishes recent (less than 90 seconds), stale and absence at one
database observation time. Future timestamps are stale; if all workers stop,
their reports remain until later publication can prune them. These reports prove
reporter/database publication, not provider delivery or every job loop. Original
and current global `identity.manage`, credentials and pre/post-acquisition MFA
guard reads. The localized browser owns each panel's cancellable request, clears
old results on refresh and discards hidden/revoked/account-obsolete observations.
No provider identifiers, paths or secrets enter the registry or responses.

Completed validation:

- 183 backend tests: 8 Administration domain, 8 data, 73 selected API/PostgreSQL/
  OpenAPI and **all 94 worker tests**, including shared-DI application startup.
  Both JARs, architecture, formatting and 20 recovery/6 deployment/16 workload
  tool checks passed in 3m28s. Log: `.work/worker-observation-final-checks.log`.
- Full dashboard check: **3,940 tests/342 files**, 60.97 seconds, plus architecture,
  Biome and strict TypeScript. Administration contributes 66 cases. Log:
  `.work/worker-dashboard-checks-2.log`.
- Six focused Chromium cases passed (`.work/worker-browser-checks.log`), including
  recent/stale/absent reports, independent API/worker flags, failed refresh,
  localization, permissions and dark 390-pixel layout. Screenshots were inspected.
- Actual API/PostgreSQL/worker/Chromium acceptance passed in 41.2 seconds (1.2m
  including setup). It observed advancing **real** worker publications, bound
  mail/push flags, a separately seeded stale process, company-only denial and
  independent MFA operator revocation. Refresh removed the old view and returned
  to sign-in. The seeded stale row does not claim an actual worker-stop experiment.
  Log: `.work/worker-real-browser-checks.log`; image `.work/worker-reports-real.png`.
- Full recovery-set exercise passed again at **schema 98**: 104 objects/2,098,795
  bytes, two encrypted records, old/current keys, source independence and wrong-
  retired-key rejection. Log: `.work/worker-recovery-set-checks.log`.

Initial attempts found a missing Swagger annotation dependency, an untyped SQL
timestamp parameter and a Jackson test-iteration mistake; the final matrix passes
after their correction. Dashboard lint first encountered ignored `.work` native
fixtures; those local files were formatted, and new non-null-assertion warnings
were removed. Existing two stylesheet warnings and Vite chunk advisory remain.
All owned browser/recovery containers, networks, volumes, private fixture data
and connection files were removed. No acceptance fixture is active at this update.

Reproduce the relevant acceptance after a warranted change (JDK/Docker environment
below):

```sh
./gradlew --no-daemon :modules:administration:domain:test :modules:administration:data:test \
  :apps:worker:test :apps:server:test --tests '*WorkerIntegrationHttpTest' \
  --tests '*IntegrationConfigurationHttpTest' --tests '*ApiContractHttpTest' \
  :apps:server:bootJar :apps:worker:bootJar architectureCheck formatCheck \
  recoverySetCheck deploymentToolCheck workloadToolCheck
python3 tool/check_recovery_set.py
cd apps/dashboard
npm run check
npx playwright test tests/integration-configuration.spec.ts
npx playwright test --config playwright.worker-integration.config.ts \
  integration-worker/worker-integrations.spec.ts
```

Next: reconcile remaining module acceptance against the PRD and continue the
independent missing workflows. Running reporter status is implemented; safe
provider-health observations and real FCM/Google/iOS/device delivery retain their
own contracts and missing inputs listed below. Do not mark the overall PRD done
from these counts. No live deployment occurred or is authorized by a Git push.
This HRIS administration capability requires no Mobile/Starter foundation port.

## Previous checkpoint: deployment review and cutover runbook — `d765f155`

[Deployment](deployment.md) now provides the release/maintenance/checkpoint/
migration/startup/rollback procedure, with a [private record template](deployment-record.md).
It identifies the concrete target inputs still needed and preserves the distinction
between writing a runbook and executing a live release. No live stack, proxy,
credential, route or volume was changed by this increment.

[review_deployment.py](../tool/review_deployment.py) accepts normalized Compose JSON
on stdin (maximum 4 MiB). It emits six API flags, four worker flags, fixed minimum-
wiring checks and safe categories. It checks scanner/storage dependency, shared
runtime flags, the worker FCM read-only existing-file bind, private service bindings
and optional production HTTPS/secure-cookie/development-transport rules. The
`minimumWiringPassed` field is not runtime readiness: process/provider checks are
explicitly false. It never emits provider addresses/IDs, paths, secrets or parser
messages; it does not validate key contents or arbitrary JVM property overrides.

Six deployment-tool regressions passed, including malformed/deep/oversized input
and redaction even when a secret is supplied as an invalid boolean. Three actual
Compose combinations (base, documents, documents plus push) rendered and passed
with fictional values. Root `deploymentToolCheck`, the existing sixteen workload
tests, architecture and formatting passed in 22 seconds. Log:
`.work/deployment-review-checks.log`. Static rendering starts no service and contacts
no provider. No application graph or dashboard/API response changed.

The subsequent running-worker observation contract is recorded above; the static
tool cannot substitute for it. Continue remaining provider/native acceptance and
reconcile module criteria.
Live deployment still requires the actual target, maintained HTTPS origin,
credential custody, reviewed checkpoint/window and explicit release instruction.
Do independent implementation/acceptance work before requesting missing inputs.

## Previous checkpoint: full-signature document workload passed — `ed7a760e`

[measure_documents.py](../tool/measure_documents.py) owns the complete real
API/worker/PostgreSQL/Garage/ClamAV/updater pipeline through
[document_workload_fixture.py](../tool/document_workload_fixture.py). It extends
the existing fixture without changing attendance/payroll defaults. The separate
[bounded transfer/PDF tool](../tool/document_workload_io.py) generates unique
uncompressed PDF comment streams, owns 1–4 transfer tasks and checks all downloaded
bytes. Application JARs needed no source changes; this increment changes the
checked-in daemon's total scan budget and operational tooling/docs.

The first run correctly refused publication of both 100-MiB PDFs because
`MaxScanSize 150M` also counts the original input and extracted streams. Scanner
logs identified `Heuristics.Limits.Exceeded.MaxScanSize`; this was a configuration
failure, not accepted document performance. The finite budget is now 250 MiB,
retaining 105-MiB stream/file limits, two scanner threads, a four-command queue,
60-second scan deadline, encrypted/exceeded-limit alerts, 768-MiB temporary
filesystem and the 3-GiB scanner ceiling.

The corrected full run passed two distinct 100-MiB uploads (7.809 seconds), two
ready revisions (9.376 seconds from validation admission), four complete downloads
(3.841 seconds; all length/SHA-256 checks) and EICAR rejection (2.119 seconds).
SQL found exactly 201 distinct committed chunks, two publications and three
successful validation jobs/audits. The updater acquired and verified main 63,
daily 28167 and bytecode 339: 3,642,936 signatures in total.

All six services shared CPUs 0–3; the driver used 4–7. Memory ceilings totalled
16 GiB: database/API/worker/Garage/scanner/updater 6/2/2/1/3/2 GiB. Peaks including
setup were 240.94/956.05/613.83/170.69/1713.70/673.52 MiB respectively, without OOM
kills. This is still the shared eight-CPU/32-GiB VPS, with private HTTP and the
specified simple PDF corpus. It does not measure simultaneous attendance/payroll,
other document corpora or a later scheduled signature update/reload.

Tracked evidence:

- [Accepted report](measurements/documents-2026-10-28.json)
- [Initial scan-limit failure](measurements/documents-2026-10-28-scan-limit.json)
- [Workload/limits/commands](workload.md#document-upload-inspection-and-download)

Local logs: `.work/document-workload-100.log`,
`.work/document-workload-100-revised.log`, `.work/document-workload-checks.log` and
`.work/document-services-revised.log`. Sixteen workload-tool regressions,
architecture/formatting and the existing real service configuration/health/clean/
EICAR probe passed. All owned containers, two networks and four volumes were
removed after the full run. Its private directory was removed automatically;
the failed-run directory was removed after diagnosis. No workload is active.

The subsequent static deployment review/runbook is recorded above. Running worker
health, remaining native/provider acceptance and module-criteria reconciliation
remain. Update this tracked handoff, completion, delivery and the relevant contract
with each observed result; commit and push each atomic increment.

## Previous checkpoint: payroll workload passed

The executable probe is in [measure_payroll.py](../tool/measure_payroll.py), with
fictional source preparation in [payroll_workload_data.py](../tool/payroll_workload_data.py).
It shares the tested [owned deployment fixture](../tool/workload_fixture.py) and
bounded HTTP client with the attendance measurement.

**Observed:** the two-employee smoke passed. Preparation took 24.885 seconds and
actual calculation 2.395 seconds. Both results succeeded, the amounts reconciled,
and the resource/cleanup checks passed. Evidence on this machine:

- `.work/payroll-workload-smoke.log`
- `.work/payroll-workload-90d6510ddbb64138bb740c0d216c318d.json`

**Full run passed:** the [tracked report](measurements/payroll-2026-10-28.json)
records all 5,000 successful retained results, distinct employees and audits.
Preparation took 90.753 seconds; admission took 1.830 seconds; admission through
observed calculation completion took 85.972 seconds. There were no result failures
or OOM kills, and the run/job finished `CALCULATED`/`SUCCEEDED`. Decimal totals
reconciled. All owned containers, network, data volume and private directory were
removed. No workload is active at this update.

The ten-minute target passed for this documented calculation scenario. The raw
local evidence is `.work/payroll-workload-5000.log` and
`.work/payroll-workload-a092a59411564060b298b5508b672969.json`. Eleven tool tests
passed, including rejection of fast-but-failed/partial outcomes and one-cent
aggregate discrepancies. Architecture and formatting also passed. Inspect retained evidence before repeating a passing run;
rerun when a change or unresolved concern warrants it.

If later work may have started another run, inspect the process/log first:

```sh
pgrep -af '^python3 tool/measure_payroll.py'
tail -n 30 .work/payroll-workload-5000.log
```

The probe first closes actual workforce evidence through the worker. A distinct
preparer and reviewer authenticate/enroll MFA and create/verify one canonical
compensation/input/January tax-opening example through the API. The rest of the
fictional cohort receives those exact terms and contiguous draft/verified history
through SQL, with each input referencing its own actual closing. No constraints
are disabled. The timed phase starts before public payroll admission and ends
when the independent review read observes the completed run. Polling is once per
second; preparation and calculation each have a twenty-minute bound.

This is a homogeneous January resident/gross-pay calculation: fixed earnings,
configured insurance, zero opening balances and explicit paid-day decisions.
It measures calculation admission/worker completion. Human review, finalization,
publication, payments, annual reconciliation and other compensation mixes remain
outside that measurement. The probe never submits bank instructions or contacts
external providers.

## Completed in the latest work

### Attendance workload — `51c4b1b3`

The [tracked report](measurements/attendance-2026-10-28.json) records the complete
5,000-employee/900-second run: 20,000 successful HTTP requests, no errors, exactly
5,000 accepted events, distinct employees, audits and attendance sync heads.
Native refresh/window/clock-in/read p95 values were 9.184/15.676/18.908/93.753 ms.
Scheduled-completion p95 was 138.309 ms and arrival-lag p95 0.207 ms.

Actual API/worker/PostgreSQL shared CPUs 0–3 and memory ceilings totalling 16 GiB;
the driver used 4–7. Individual cgroup peaks including setup were
720.53/478.05/634.94 MiB, without OOM kills. The shared host has eight virtual CPUs
and approximately 32 GiB RAM (AMD EPYC 9V74), so this is a constrained local result,
not an assertion about every dedicated deployment. Private HTTP and the exact
workload exclusions are documented in [workload](workload.md).

A corrected twenty-employee smoke, eight tool tests, architecture and formatting
passed. Initial fixture errors were an excessive capture-window warmup and an
incorrectly seeded schedule shape; both were corrected before the full run. Source
application behavior needed no change. All owned resources and failed-run private
diagnostics were cleaned. Logs: `.work/attendance-workload-5000.log` and
`.work/workload-tool-checks.log`.

### Database/content/key recovery — `a9b4d105`

[Recovery sets](recovery-set.md) capture a stopped API/worker/Garage deployment with
PostgreSQL running. Data and key packs use separate private directories outside
Git. The verifier restores isolated PostgreSQL/Garage copies, checks every object
and decrypts retained identity envelopes with the actual application cipher.
It never starts application processes against restored personnel records.

Two complete real fictional API/worker/PostgreSQL/Garage exercises passed. The
final run verified schema 97, old/current identity keys, two encrypted records and
104 objects totalling 2,098,795 bytes. Later source changes did not alter the
checkpoint; a wrong retired key failed despite a recomputed manifest checksum.
Twenty recovery-tool tests, 21 existing database-tool tests and three JVM cipher
tests passed, alongside API packaging, architecture and formatting. Owned resources
and failed fixture directories were removed. The CI exercise is configured; no
hosted CI success is claimed for these branch pushes.

Logs: `.work/recovery-set-integration-final.log`, `.work/recovery-final-checks.log`,
`.work/recovery-key-verifier.log`. The schema ceiling in `tool/recovery_set.py` is
now 99 after the global-audit repeat exercise recorded above. V99 adds no encrypted
record family; review new persisted encryption contracts before raising it again.

### Native acceptance and integrations

- Mobile `1c1f9104` / Platform `893db26a`: actual Android 35/API/PostgreSQL/worker
  Pending Actions acceptance. An edited leave draft survived offline process
  restart; pristine forms counted zero, local summary refresh made no HTTP request,
  reconnect retained the text and explicit deletion cleared it. No company writes
  occurred. This proves the tested draft path, not every possible pending status.
- Mobile `60456c48` / Platform `839505cf`: Android VIEW/BROWSABLE URLs passed cold
  login/company gating, repeated/resumed navigation, account/company/query rejection,
  offline message restoration and server archival retained after offline restart.
  Read/acknowledgement remained null and no company writes occurred. It does not
  establish FCM delivery, verified HTTPS links, iOS or physical-device acceptance.
- Platform `1f95e0c3`: six API integration startup flags and the localized dashboard
  passed global-permission/MFA and three-account revocation acceptance. The API
  projection acquires no provider identifiers/secrets. Validation included 184
  backend tests, the complete worker graph, 3,922 dashboard tests and seventeen
  distinct Chromium cases. Worker observations were subsequently implemented in
  `e806caed`; provider health remains separate.
- Platform `aaa596a0`: real API/PostgreSQL/worker/private Garage/browser export
  acceptance passed 1,201-row multipart downloads, exact bytes/ranges, immutable
  captured source, owner/source permissions and lost-admission receipt recovery.

Mobile evidence is tracked in its `docs/validation.md` and `docs/deep-links.md`;
Platform contracts/evidence are linked from [completion](completion.md).

## Remaining delivery areas

Use the product/module contracts as the acceptance baseline; this is a continuation
order, not a claim that every module criterion has been exhaustively audited.

1. The documented attendance/payroll/file workloads now have observed local results.
   Retain the distinction between these separate VPS experiments and a dedicated
   reference deployment; mixed workloads and other corpora remain unmeasured.
2. Actual worker integration reports are implemented and accepted in `e806caed`.
   Provider health/delivery remains separate. The static deployment reviewer and
   cutover runbook do not establish those outcomes; isolated recovery does not
   establish a live cutover/RPO/RTO.
3. Native/provider acceptance still includes actual Google identity exchange,
   FCM message delivery/scoped hints and the remaining iOS/physical-device cases. Read the
   current Mobile validation/native-push documents before choosing the next case.
4. Reconcile the remaining module criteria in [completion](completion.md) with
   their individual contracts/evidence. Do not infer completion from routes,
   repository size, a test total, a passing build or an old summary paragraph.

## Firebase state and external inputs

The project is `cmp-fajar`; the user enabled Email/password and Google and wants
both Firebase Authentication and FCM. Android configurations exist locally at
`apps/android/google-services.json` in Mobile and Starter. They are ignored/private;
do not print or commit them. The public Google web client ID is
`415338369394-nop83je85ci1btt097rpl21r6sm96ldv.apps.googleusercontent.com`.

Actual email/password Firebase-to-HRIS native login, HRIS MFA, company selection
and logout were accepted; the temporary Firebase user was removed. Google account
chooser/Back was accepted, but a real Google ID-token exchange was not. The last
known missing external inputs were worker FCM service-account configuration,
iOS Firebase/APNs/device setup and the remaining real-provider/device acceptance.
Recheck local availability before asking for anything already supplied. Public
Firebase Android configuration alone does not grant Firebase project-management
or FCM server credentials. Do not invent unavailable acceptance evidence.

## Commands and environment

Use JDK 21 (`/usr/lib/jvm/java-21-openjdk`). On this VPS Docker access uses the
existing Docker group, for example:

```sh
sudo -n runuser -u fajar -g docker -- env \
  JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  PATH=/usr/lib/jvm/java-21-openjdk/bin:/usr/local/bin:/usr/bin:/bin \
  python3 tool/measure_documents.py --file-mib 100
```

Only rerun after confirming the preceding workload has stopped. Both JARs must
exist at `apps/server/build/libs/apps-server.jar` and
`apps/worker/build/libs/apps-worker.jar`. The application project is `:apps:server`,
not `:apps:api`; the Gradle task is `architectureCheck`, not `checkArchitecture`.

```sh
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  PATH=/usr/lib/jvm/java-21-openjdk/bin:/usr/local/bin:/usr/bin:/bin \
  ./gradlew --no-daemon workloadToolCheck architectureCheck formatCheck
```

Use `--no-daemon` for builds/tests needing the Docker group; an older daemon may
retain different groups. Avoid concurrent heavy builds/workloads on the measurement
CPUs. Docker fixtures track owned resources using the `hris.recovery-run` label:

```sh
docker ps -a --filter label=hris.recovery-run
docker network ls --filter label=hris.recovery-run
docker volume ls --filter label=hris.recovery-run
```

Inspect ownership before cleanup. Failed cleanup deliberately retains private
data needed by a remaining container; never use a global Docker prune. Successful
reports are written only after the probe confirms its own cleanup.

For Mobile builds use `ANDROID_HOME=/home/fajar/Android/Sdk` and
`:apps:android:assembleDevDebug`. The dedicated emulator is AVD `hris-mobile-dev`,
serial `emulator-5560`; preserve its data and never use `-wipe-data`. The latest
acceptance used an isolated API override, not a source change to the standard
WireGuard origin. Physical phone delivery has been install-only: notify before
installing and do not drive phone UI. Read Mobile's own instructions first.

Keep this handoff current in the commit that records the next result. Summarize
failures honestly, name the corrected cause and keep the final validated state
easy to distinguish from historical attempts.
