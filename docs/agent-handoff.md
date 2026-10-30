# Agent continuation — HRIS workspace

Updated 30 October 2026 (UTC). The user requested verification and completion of
all specified features, with every atomic commit pushed and handoffs retained in
Git. The full audit is active after finding missing announcement email delivery.
Earlier distribution/native acceptance remains valid for its recorded scope. Read `AGENTS.md`, this page and [PRD status](prd-status.md),
then the affected module contract. The [archived history](agent-history-2026-10-29.md)
preserves earlier work; its old next-step statements are not the active queue.

## Active goal — full feature completion audit

Complete every specified Platform/Mobile feature and port demonstrated generic
fixes to Starter. The [full audit](feature-audit-2026-10-30.md) remains active:
Approvals, Communications, Identity, Expenses, Leave and four Document rows now
have thirty reviewed initial rows; thirty-one initial rows await completion/review,
followed by Mobile/Starter. Identity, Approvals, Communications, existing Document behavior, Expenses and Leave
have accepted full-CI evidence. The explicit Document category subrequirement has
been reopened; see the confirmed metadata gap below.
Do not mark the goal complete from these increments or earlier green CI.

Announcement email source `8014d733` passed all ten jobs in
[CI 41005223374](https://github.com/fajarxfce/hris-platform/actions/runs/41005223374):
four backend, four dashboard, dashboard integration and recovery. Its optional
selection, atomic queue, SMTP delivery, administrative observations and recovery
have [recorded acceptance](measurements/announcement-email-2026-10-30.json).

[Approval history](approval-history.md) is committed/pushed at `bbb99b48`, with
118 API/OpenAPI cases, 104 worker cases, 11 then-current domain cases, 4,012 dashboard
unit cases, 14 distinct Chromium scenarios and two real API/browser workflows.
One manual one-hour worker test remains covered by its earlier independent run.
The interrupted combined execution was replaced by successful separate API/worker
runs; [measurements](measurements/approval-history-2026-10-30.json) retain the details.

The approval correction completes assurance on seven older readers.
Ten actual acquisition-expiry reproductions failed at `bbb99b48` and then passed.
Final acceptance passed 60 approval API cases, 13 domain cases, architecture,
formatting, both packages and two actual API/browser workflows. Inbox completion
uses current-stage eligibility; retained request/history scope remains distinct.
See [correction](approval-read-assurance-2026-10-30.md) and
[module requirement review](approvals-feature-review-2026-10-30.md).
The correction is committed/pushed at `fcfdaf26`. [Full CI 41009137720](https://github.com/fajarxfce/hris-platform/actions/runs/41009137720)
passed all ten jobs for exact source `fcfdaf26a48718a80bc79468bdbdb3b4cbeae11c`.

[Communications read completion](communications-read-assurance-2026-10-30.md)
corrects thirteen reproduced MFA-expiry variants across ten older readers. All
106 API, 13 domain and three repository tests passed, along with quality/package
checks and actual API/worker/browser/SMTP acceptance (37.1s; 1.0m including setup).
The [Communications requirement review](communications-feature-review-2026-10-30.md)
locally accepts all five rows and nine detailed sections, including optional email.
The correction is committed/pushed at `737d39a7`. Final source `8e5dd3db` now passed all ten CI jobs, including the Document command correction.

[Identity](identity-feature-review-2026-10-30.md) accepts all five initial rows and
eight detailed sections against unchanged source trees and full CI `8014d733`:
190 API, 35 domain, 22 data and 17 worker cases, plus 95 Chromium scenarios
(including eight actual API/browser workflows). No new implementation was needed.

[Document read completion](document-read-assurance-2026-10-30.md) now passes all
20 reproductions, 223 API, 18 domain and nine data cases, quality/packages and four
actual browser workflows (42.4s). Seventeen readers retain final MFA checks,
including preparation and post-storage completion. No DI/schema change occurred.

[Document command completion](document-command-assurance-2026-10-30.md) corrects
eight commands. Nineteen actual baseline cases failed; inventory receipt replay
was the twentieth, already-correct control. All twenty focused cases and final
243 API, 18 domain, nine data, quality/packages and four actual browser workflows
passed (42.5s). The [Documents requirement review](documents-feature-review-2026-10-30.md)
locally accepts all five rows and ten original sections. Exact source `8e5dd3db49b2568917c44432574f58ce1eae6dae` passed all ten jobs in
[CI 41013958569](https://github.com/fajarxfce/hris-platform/actions/runs/41013958569).
This includes Communications and both Document increments; it does not include
the later Expense correction.

[Expenses](expenses-feature-review-2026-10-30.md) now locally accepts all six initial
rows and nine detailed sections. Its correction closes 29 failed cases: fifteen
payment-enrollment, three receipt-delegation, five mutable-input and six decision-
delegation cases. Three ordinary receipt-scope controls already passed; three added
delegator-account wait regressions now pass. Broad acceptance passed 329 API,
20 Expense domain and 115 Reporting cases, quality/packages (590s). Final decision
acceptance passed 62 API, 20 domain and quality/packages (145s), for 335 distinct
API cases across both selections. Four final real browser workflows passed in
1.2 minutes: claim authoring/review, payment preparation/release/reconciliation,
category policy and reporting. The [measurements](measurements/expense-assurance-2026-10-30.json)
retain both selections and package hashes. No runtime or phone update occurred.

Exact Expense source `5b60aba3fca4fe7ae388f57a08d6a9135b858739` completed
[CI 41018107365](https://github.com/fajarxfce/hris-platform/actions/runs/41018107365):
nine jobs passed; dashboard integration failed its five-minute worker-step budget
after all fourteen scenarios passed in 5.2 minutes. Later storage/export checks
were skipped. [Timeout evidence](measurements/ci-integration-timeout-2026-10-30.json)
records the outcome. The workflow now permits eight minutes for this step and
twenty for the job, retaining bounded test timeouts and zero retries. Replacement CI `41023645590` now passes all ten jobs, including unchanged Expenses.
No full-source acceptance is claimed from this failed run.

[Leave read completion](leave-read-assurance-2026-10-30.md) corrects eight readers.
Ten genuine reproduction failures now pass. Final acceptance passed 437 API/OpenAPI,
65 domain cases, quality/packages (822s), and six actual browser
workflows (51.5s including setup). No DI/schema/additional query change occurred.
Storage buffers are cleared and expired preparation starts no object read.

[Leave command/delegation completion](leave-command-assurance-2026-10-30.md) now
locally accepts all five rows and eleven original sections. Twenty-five actual
failed cases were corrected; five prior controls and four new account-wait
regressions remain distinct. Acceptance covers 485 distinct API/OpenAPI cases across a 471-case broad
run (857s) and 58-case final targeted run (115s),
plus 65 domain cases and quality/packages, six actual browser workflows (50.4s)
and three actual worker/browser workflows (53.4s), including setup. Nine leaf
use cases and one pure read policy changed; no schema/DI/transport change occurred.

Decision receipts select their original immutable pre-decision revision with a
one-item history page, preserving the correct approval through later cancellations.
They still require current independent assignment and exclude newly bound
beneficiaries. Read grants are reacquired after sorted actor/delegator account
locks. Request-management receipts retain current scope and final MFA. Do not
remove these bounded acquisitions as a query-count optimization.

Leave correction `70b92e59` is committed/pushed. Exact source `70b92e59e7b41bdafe9f9d1c4b9d5106412777f5`
passed all ten jobs in [CI 41023645590](https://github.com/fajarxfce/hris-platform/actions/runs/41023645590), including normal browser, worker,
private exports and recovery. [Evidence](measurements/leave-full-ci-2026-10-30.json)
retains exact outcomes and matching Expense tree. This CI excludes later People
work. Continue the full feature goal; accepted increments do not close it.

[Document category/identity metadata](document-identity-metadata-gap-2026-10-30.md)
is a newly confirmed implementation gap in `documents.02` and `people.03`. The
earlier Documents review omitted category; that row is reopened. Upload expiry
is not passport/permit validity, and Payroll tax residency is separate. The
current inventory is thirty reviewed and thirty-one unverified rows. People
command/Organization acceptance is complete. Backend/dashboard metadata is
locally validated; finish native projection/flows and recovery before accepting
the requirements.

[People read completion](people-read-assurance-2026-10-30.md) passes twenty-eight
actual reproductions across twenty-four readers, including both transfer-review
company scopes. Final acceptance passed 427 API/OpenAPI, 50 domain and fourteen
data cases, quality/packages (1124s), three actual browser workflows (36.5s)
and two actual worker/browser workflows (37.3s), with setup. No new query, DI
or schema change occurred. All five People initial rows remain unverified.

People reader correction `b0b7c21c` is committed/pushed. The later
[command/Organization correction](people-command-assurance-2026-10-30.md) now
passes fifty reproduced failures and four prior controls. All 54 focused API,
52 domain and fourteen data cases passed in 142s. The broad API selection passed
497 cases; its parallel worker run had two old enrollment-fixture failures.
After fixing only that fixture, all 104 ordinary worker cases passed with one
separately accepted manual test skipped; quality/packages passed in 226s.
Production/package hashes match the broad API run. Final actual browser workflows
passed in 36.1s (three normal) and 36.8s (two worker), including setup.
Both API and worker contexts are validated. Correction `158b81ac` is committed and
pushed. Exact source `158b81acce89f637dbf715bb573fffb7b99b92c9` passed all ten jobs in
[CI 41032184925](https://github.com/fajarxfce/hris-platform/actions/runs/41032184925),
including both People increments, normal/worker browser, private exports and recovery.
[Terminal evidence](measurements/people-full-ci-2026-10-30.json) retains every job and
step. It excludes the later V103 document metadata implementation.

Document business metadata backend and dashboard are now implemented and locally
validated; see the [contract and evidence](document-business-metadata.md). V103,
24 focused API cases and migration acceptance pass. Broad plus final corrected
suites cover 689 distinct API cases, 104 ordinary worker cases (one retained manual
skip), 27 database, 18 domain and nine data cases, with unchanged production/package
hashes. Dashboard passes 4,054 unit cases, 25 mocked and five distinct actual browser
workflows. Exact failed attempts and fixture-only corrections are retained in
[measurements](measurements/document-business-metadata-2026-10-30.json).

Metadata increment `27e7f77e` is committed/pushed. Exact source
`27e7f77e722da9e8ac2bde47836353ef92dcc3b3` completed
[CI 41038369597](https://github.com/fajarxfce/hris-platform/actions/runs/41038369597):
nine jobs passed; recovery failed because its reviewed schema ceiling remained 102.
The correction now seeds three metadata snapshots through the real API and verifies
all restored typed values, company RLS, history immutability and deferred head/history
consistency. Actual schema-103 recovery passes in 57.356s, with 104 objects and both
identity key generations; 42 Python tool tests pass. API/worker JAR hashes match the
accepted implementation. [Evidence](measurements/document-metadata-recovery-2026-10-30.json)
retains every original CI job and step. Correction `292a30f1` is pushed. [Replacement CI 41043798660](https://github.com/fajarxfce/hris-platform/actions/runs/41043798660)
passed all ten jobs for exact `292a30f14498b2eee97a9255f22fe645639658e5`, including
normal/worker browser integration, private exports and schema-103 recovery. Platform Git
retains every terminal job/step and the downloaded recovery summary: 104 objects,
2,098,795 bytes, restored metadata/history/RLS/constraints and completed cleanup.
The original run remains recorded as a failure; no CI run is pending for this source.

Mobile `7f352cfd` and `41fe79f9` are pushed: owned metadata format-2 refresh retains
old facts and admission gates; initial upload freezes category/identity/printed
validity in the encrypted intent. Old omitted payloads keep their original operation.
Root checks pass 1,878 desktop and 131 Document Chromium cases; the APK builds.
The new form also fixes validation feedback hidden above a long form. Mobile Git
retains each earlier fixture failure, the reproduction and source/APK measurements.

Mobile `e2690fd6` now recovers first definite upload rejections without reimporting
file bytes. Two genuine failures now pass; uncertain/unknown commands stay pinned.
Explicit reconfirmation uses an exact-file compare-and-set and new operation IDs.
All 1,888 desktop and 140 Document Chromium cases, root checks and the dev APK pass
(189.354s). Mobile Git retains cancellation/race/capability and actual encrypted
storage evidence. No phone or live API change occurred.

Mobile `3ce42fed` adds validated header/history/correction transport; `966e883c`
adds encrypted metadata/history and per-resource read admission. Both are pushed.
Current root checks, dev APK and Document browser suites pass in 184.621s: 1,912
desktop and 164 browser cases. Actual SQLite/IndexedDB tests retain 1,000 immutable
revisions and verify exact-page reads, restart, scope, lost keys, cancellation,
competing writes and pending/failed admission storage. These changes reuse existing
storage contracts and require no Starter implementation port.

Mobile `f282b027` adds admitted reads and the assembled detail/history route. Thirteen
domain and two UI cases cover ownership, finite paging, legacy support, cancellation,
offline history and denial recovery. Two reproduced publication failures now retain
an earlier denial when company policy expires during local storage. Root quality,
dev APK and Document browser suites pass in 197.437s: 1,927 desktop and 177 browser
cases, all without failures/errors/skips. Mobile Git retains exact cases, source/APK
hashes and earlier compilation/method-length corrections. This commit is pushed;
versioned corrections and durable original-command recovery remain in progress.

Next: Mobile observed-version corrections with
durable original-command recovery. Keep shared finance/leave evidence free of
private identity numbers. Verify
Android, Apple and owned emulator behavior before accepting `documents.02` or
`people.03`; physical iPhone testing is waived. Finish the detailed People review,
then remaining Platform modules and Mobile/Starter. This business capability has
no generic Starter port. The full goal remains active.

CompleteOffboarding and explicit onboarding/offboarding schedules already have
final checks; preserve their behavior. Client policy is admission-only: do not add
final availability rejection for already admitted work. Complete full People
requirements/evidence before accepting its rows; then continue the other modules
and Mobile/Starter.

Input copies are bounded before validation. Receipt reads retain scope/expiry and
ordered actor/delegator guards; one additional identity acquisition per short
receipt transaction is intentional. No DI/schema change or generic client port.

Downloaded
full-CI reports are supplementary local evidence in
`.work/feature-audit-2026-10-30/ci-8014d733`; parsed backend and browser indexes
are in `ci-suite-index.json` and `ci-browser-index.json` beside that directory.
The [Identity measurement](measurements/identity-feature-review-2026-10-30.json)
retains its selected evidence in Git. Gradle artifacts contain HTML, not XML;
Playwright HTML embeds the report ZIP with its verified source metadata.
Keep each requirement's matching evidence in the inventory. No reusable client
correction is due from these server/dashboard changes.

The **private development API still uses schema 101**, SMTP disabled. Preserve its
accepted Google binding and installed phone session. The dashboard service uses
`~/.config/systemd/user/hris-dashboard-dev.service.d/20-static-assets.conf`, pointing
Vite preview at `~/.local/share/hris-dashboard-dev/f91a9bca`. This baseline is compatible
with that API and independent of `apps/dashboard/dist`, which tests rewrite. Both
endpoints returned HTTP 200 and its index matched that snapshot. Future controlled
dev rollout must update both API and the explicit static asset path. No live mail,
phone interaction, production deployment or runtime rollout occurred in this audit.

## Current user direction — 29 October 2026

The user explicitly waived physical iPhone testing for this completion pass
("iphone gkpp gk ditest juga") and requested continued implementation through
completion, with each atomic commit pushed. Do not ask for an iPhone again or
keep that test as a completion blocker. Simulator/contract tests and Xcode builds
remain required; do not claim actual iOS sign-in or APNs delivery from them.
Configurable HTTPS admission/native associations, the public browser handoff and
the independently verified Starter port are complete.
The controlled domain remains a deployment setting, not a reason to leave the
configurable implementation unfinished. Preserve the accepted Google binding,
phone data and running private development stack.

## Native distribution audit — accepted

The audit fixes selected release API precedence over per-environment project
properties, optional app-link configuration through signing and packaging, and
31 missing SBOM POM verification entries. The original artifact hashes remain
unchanged. Android checks the normal test configuration before compiling the
selected release; Xcode forwards the selected backend/API explicitly. The 15
portable files retain Starter's application IDs, demo/remote choice and three-job
CI. HRIS's remote-only policy, build-version UI fixtures and explicit dev-network
XML attributes remain in Mobile.

Mobile source `3dc81d00` passed all 4 jobs in full
[CI 40909156570](https://github.com/fajarxfce/hris-mobile/actions/runs/40909156570). Downloaded Apple reports
confirm 104 tests in 28 suites, zero failures/errors/skips;
compilation/tests/Xcode took 791s/122s/1295s. The complete Xcode app, native/domain
artifacts and explicit selected distribution API/backend assertions passed.

Starter port `77063862` passed all 3 jobs in full
[CI 40916187442](https://github.com/fajarxfce/compose-fluent-starter/actions/runs/40916187442). Downloaded Apple reports
confirm 84 tests in 24 suites, zero failures/errors/skips;
compilation/tests/Xcode took 724s/81s/744s. The complete Xcode app, native/domain
artifacts and explicit selected distribution API/backend assertions passed.

Actual local signed Android APK/AAB/SBOM exercises passed in 215.572s on Mobile
and 136.647s on Starter. Both verify the selected release API and native manifest
in APK and AAB, actual signatures, retained domain files and checksums. Mobile
passed 1,860 desktop cases; Starter passed 326 desktop cases in 112 suites. Both
passed 17 distribution regressions. Normal dev builds and production SBOMs were
restored afterward (39s Mobile, 23s Starter), with fixture API/host values absent;
Starter's normal backend remains demo. Every disposable signing key was removed.

A workflow-only follow-up at Mobile `d7e445a8` and Starter `4c794e7` retains
`.well-known` files in the manual artifact upload. The pinned action's actual
`@actions/glob` 0.5.0 selection reproduced five files with both domain statements
missing on each signed fixture; the correction selects all seven and every
checksum matches. Both workflows include hidden files only in the packaged
artifact directory. This separate proof does not invoke upload; application/native
code and ordinary CI definitions remain at the full-CI sources above.

No CI, local check or reusable port remains pending for this audit. No distribution
workflow, upload or phone installation was performed; the installed Mobile APK
remains `72341ef6`. The private server and accepted Google binding are preserved.
Real signed Apple IPA, APNs, public domain/DNS/TLS activation, physical GPS and
production operation retain their separate target requirements. Physical iPhone testing was
explicitly waived; simulator and unsigned Xcode proof does not imply device delivery.

See [distribution evidence](measurements/distribution-2026-10-29.json).

## Previous implementation checkpoint — HTTPS links

The last implementation queue is closed: the [public HTTPS handoff](mobile-app-links.md)
passed 4002 dashboard unit cases and 14 related Chromium cases at `d21a1346`.
Native associations and bounded ASCII URL admission are implemented in Mobile and
ported to Starter after the recorded source proof. Actual Android scope/offline/
archive acceptance passed with 28 requests, no company writes and unchanged
read/ack state. [Measurements](measurements/app-links-2026-10-29.json) retain exact
sources, previous failures and all final verification.

Final Mobile source `72341ef6` passed all four jobs in full
[40895095518](https://github.com/fajarxfce/hris-mobile/actions/runs/40895095518).
Downloaded Apple reports confirm 104 cases in 28 suites, zero failures/errors/skips
and the complete Xcode app; compilation/tests/Xcode took 701s/77s/1040s.
Starter port `738db36` independently passed all three jobs in full
[40895798634](https://github.com/fajarxfce/compose-fluent-starter/actions/runs/40895798634),
with 84 Apple cases in 24 downloaded suites, no failures/errors/skips and Xcode;
compilation/tests/Xcode took 561s/84s/703s. Generated Android/Apple association
checks passed, and the Xcode/Apple artifact success markers were inspected.
No CI or owned local acceptance remains active from this increment.

The authorized phone has application `72341ef6`, installed with `--install-only`.
APK SHA-256 `5b3754d5097eb0a535d1a98cff5bc37e508ea12a008eec897895d9289797fe4d`,
32,872,857 bytes. Its registered certificate/private WireGuard API match, and
fixture values are absent from runtime code and the binary manifest. First-install
time was unchanged; no phone UI was driven. Actual Google/Home acceptance remains
attributed to the earlier `678783b3` user-operated APK. The private API/dashboard
still returned HTTP 200; preserve the Google binding, keys, volumes and running stack.

The implementation queue is complete for this development pass. Physical iPhone
testing was explicitly waived. Public domain/DNS/TLS association, actual APNs,
physical GPS and production deployment remain separate configuration/target
observations; no result for those is inferred from builds or fixtures. Default
native builds do not enable an unconfigured HTTPS host. Reopen implementation for
an observed failure or a documented requirement; do not recreate accepted modules
from old next-step paragraphs.

The user subsequently requested a usable server for the installed phone and
authorized continuing development startup. A new persistent private `hris-dev`
stack is running at schema 101; API health returned HTTP 200 from the VPS and
phone through WireGuard. Dashboard build/user service, private Garage/scanner,
separate database roles and development configuration review passed. The current
Android signing SHA-1 was registered in Firebase after exact-app verification.
Fictional company/employee provisioning is complete through public workflows:
operator MFA, captured worker invitation, employee role, independent person link,
schedule, demo Leave/Expense policies and four approval kinds. The same phone APK
passed Firebase email/password, company/Home and downloaded-document UI on the
owned emulator. The PDF passed real storage/scanner/worker publication and identical
download. A coordinated private recovery checkpoint passed schema/key/object checks;
host and phone health, browser login and document download passed after restart.
See [current runtime ownership, private paths and next actions](current-development-runtime.md)
and [sanitized evidence](measurements/development-runtime-2026-10-29.json).
The user tried Google and received the expected unlinked-account rejection.
Exact Firebase lookup then confirmed `google.com` and verified email. The old
fictional password binding was deactivated before adding the confirmed Google UID,
preserving V16's one-active-subject-per-issuer rule. The user retried and confirmed
Home; account-session metadata independently returned one new native session
created at `2026-10-29T10:24:50.263372Z`. Actual Android Google → Firebase → HRIS →
company/Home is accepted. Email and the binding receipt remain private. See
[Google evidence](measurements/native-google-2026-10-29.json). A newer private checkpoint
`dev-google-20261029T103103` now includes the accepted binding/session and passed
isolated restoration; API/dashboard and the same Google session passed after restart.
The initial Java-17 verifier failure and corrected JDK-21 run are retained in the
current-runtime record. Firebase Management
also supplied matching public iOS configs for all three HRIS flavors. Mobile's
iOS password/bundle loading and Google system-browser PKCE/code exchange passed
full CI `40858176601` at `43cd8ec6`: all four jobs, 97 native cases and Xcode.
Only then was the Google foundation ported to Starter `e0c6071`; its local checks
and all three jobs in full run `40864341588` passed. Downloaded Apple reports retain
77 native cases in 22 suites, no failures/errors/skips and the complete Xcode app.
The preceding password-only
Starter port `8a75de5` independently passed 62 native cases/Xcode in `40861505322`.
APNs/device acceptance remains. Leave
this persistent stack running; statements below about cleaned fixtures refer to
the earlier disposable acceptance runs, not this development environment.

The user supplied the Firebase service-account path. It is installed privately at
`/home/fajar/.config/hris/fcm-service-account.json` (directory 700, file 600), outside
Git. **The sender credential question is resolved; do not ask for it again.**
[Actual FCM acceptance](native-fcm-acceptance.md) now passed on Android 35 with
Mobile `678783b3` and Platform runtime `b7f42070` / schema 101: two real publications,
two observed generic OS notifications (foreground/background), correct notification
navigation, unchanged unread/unacknowledged version-zero Inbox rows and no native
company mutation across 55 requests. Disable/logout removed the server token/hash.
Fixture 6.2m/6.6m with setup; [measurements](measurements/native-fcm-2026-10-29.json)
retain the two preceding orchestration/observer timeouts and exact proof. Five new
configuration tests, 12 related fixture checks, seven proxy regressions, TypeScript,
Biome, build and architecture passed locally. All owned resources are cleaned.
This increment adds an opt-in fixture, not a deployment or application change;
there is no new generic Starter port. Actual Android Google exchange is accepted above; iOS/physical cases remain.

Corrected full Verify [40820912608](https://github.com/fajarxfce/hris-platform/actions/runs/40820912608)
passed all ten jobs at `9345c19bad4a219ecdb9ac247f944b2959c4642a`. All four backend and all
four dashboard shards passed. Downloaded reports confirm 2,731 disjoint API tests,
686 shared module tests and 94 worker passes; one opt-in elapsed-retention case
is skipped by ordinary worker runs and was separately accepted below. Dashboard
passed 3,997 unit tests in 352 files, seven native-proxy regressions and 1,098 distinct
Chromium cases. Repeated suites are counted once.

Schema-101 recovery and report upload also passed on this exact head. The downloaded
sanitized summary verifies 104 objects, 2,098,795 bytes, two encrypted records,
old/current keys, original-source preservation, typed audit/delivery projections
and cleanup. Actual integration passed 40 API/browser cases (4.9m), 14 worker cases
(4.5m), one storage export case (47.8s), two mail and nine storage fixture checks.
Those Platform runs and the newer Starter run are complete. Logs and downloaded summaries
were inspected; [tracked measurements](measurements/ci-2026-10-29.json) retain results.

The two preceding failed runs remain in [measurements](measurements/ci-2026-10-29.json).
Run `40816036721` passed tests/restore but excluded the hidden `.work` report during
upload; `70e5098d` enables only the exact sanitized summary glob. Run `40820005213`
passed backend/restore/upload but one browser locator matched both page and section
headings. `9345c19b` waits for the returned table and selects the level-one heading;
its four local Chromium cases and the corrected full dashboard matrix passed.

The final run used owned temporary ref `ci/closing-report-40820005213` so the
previous recovery run could finish without cancellation. That ref was removed
after verification; the tested commit remains on `feat/firebase-auth`. The newer
FCM acceptance fixture has separate local proof above. No PR, release or live
deployment was created.

Mobile's original full CI passed Linux/Android, Web and Windows but Apple cold
compilation exceeded its combined deadline. Workflow correction `451a2c4d` passed
[40817592306](https://github.com/fajarxfce/hris-mobile/actions/runs/40817592306):
27 simulator tests, complete unsigned Xcode build and retained reports. Only then
was it ported to Starter `1817e45`, whose independent
[40820482699](https://github.com/fajarxfce/compose-fluent-starter/actions/runs/40820482699)
also passed 27 tests, Xcode and reports. No generic port remains from this increment.
Mobile copy-only `678783b3` corrects English Expense item-count wording; local root
checks and Android assembly passed with 1,840 desktop cases in the report set.
Its hosted CI and previous native recovery used earlier application `02743a9f`.

## Workspace and delivery policy

| Repository | Working branch | Accepted source boundary |
| --- | --- | --- |
| `hris-platform` | `feat/firebase-auth` | Runtime integration `b7f42070` / schema 101; full CI `9345c19b`; later real-FCM fixture accepted locally |
| `hris-mobile` | `feat/native-push` | Final application `72341ef6`: all four jobs in `40895095518`, 104 Apple cases/Xcode; installed on phone with data preserved |
| `compose-fluent-starter` | `fix/push-token-lifecycle` | HTTPS port `738db36`: all three jobs in `40895798634`, 84 Apple cases/Xcode; local 326 desktop/11 browser and configured artifact checks |

The repositories are siblings under `/home/fajar/hris`. Inspect Git status, history
and upstream before editing; preserve unrelated work. Mobile was generated from
Starter and is the proving ground. Prove reusable changes there before porting to
Starter; HRIS policy, APIs, routes and configuration remain in the consuming repos.
Read the [Mobile handoff](https://github.com/fajarxfce/hris-mobile/blob/feat/native-push/docs/agent-handoff.md)
and [Starter handoff](https://github.com/fajarxfce/compose-fluent-starter/blob/fix/push-token-lifecycle/docs/agent-handoff.md).

Routine implementation, validation, atomic commits and pushes are already authorized.
Do not ask again or add co-author trailers. A Git push does not authorize releases
or a live cutover. Tracked contracts/measurements are the durable handoff; local
`.work`, `PRD-PROGRESS.md`, `REPO-TRACE.md` and chat are supplementary.

## Accepted product evidence

Use the [eleven-module map](prd-status.md#implementation-and-acceptance-map) and
[delivery ledger](completion.md#detailed-delivery-evidence-by-area) for the exact
implemented scope. Do not recreate completed features from an old future-work paragraph.

- All nine Mobile company data-sync registrations and six pending-action observers
  are implemented. [Native recovery examples](prd-status.md#native-pending-action-coverage)
  cover Inbox, Attendance, Overtime, submitted Leave, saved Expenses and document
  transfers/inspection review, with exact replay and server invariants. They do not
  prove every state combination or physical target.
- [Push delivery observations](announcement-delivery.md), `409abad4`, and
  [original payroll comparison](payroll-period-comparison.md), `f757bffd`, are
  integrated at `b7f42070`, with actual API/worker/browser and schema-101 recovery
  evidence. Neither administrative feature needs a Mobile/Starter business port.
- [Elapsed export retention](export-retention-acceptance.md), `1abdbce9`, passed
  its unchanged physical one-hour grace, lost delete receipt retry and one audited
  retirement. JUnit: one pass, 3,860.892s; Gradle 1h4m32s, compiled `ecb17627` / schema
  100. It completed at 03:17:16 UTC; no running test/classpath must be preserved.
- [Workload evidence](workload.md) covers 20,000 attendance requests, a constrained
  5,000-employee payroll calculation and full-signature document scanning with two
  100-MiB PDFs, checked downloads and EICAR rejection. These are distinct measured
  fixtures, not a claim about an untested production environment or mixed workload.

All native-owned fixture services, containers, control files and forwarding are
cleaned. The dedicated emulator is stopped with app data retained and location
restored. Do not rerun accepted expensive workloads without a new failure or
requirement. New behavior gaps still require implementation and relevant acceptance.

## Remaining delivery areas

The preceding Apple provider ports are accepted. Configurable HTTPS admission and
native associations are implemented in Mobile `d36a1bc9`; full CI `40887772486`
passed all four jobs and 103 native cases/Xcode. Its Starter port and final parser correction are accepted by the complete CI
runs above; no implementation or test remains active from this increment. The public dashboard handoff
and actual Android scoped/offline/archive case passed. The user waived physical
iPhone testing for this completion pass. Deployment-specific evidence stays
separate from these implementation results.

| Deployment observation | Configuration/execution still needed |
| --- | --- |
| Public HTTPS association | A controlled domain, its DNS/TLS and generated public association files served without redirects. Configurable native implementation already exists; do not implement it again. |
| Apple physical provider/push | Optional later device signing and APNs setup; actual device testing is waived for this completion pass. Simulator/Xcode proof is recorded independently. |
| Physical location and production | Authorized device/target environment and target-specific execution. Emulator/no-location review and isolated recovery retain their measured boundaries. |

Only the domain question remains pending; do not repeat it while waiting, and do
not request an iPhone again. Mobile's [optional iOS procedure](https://github.com/fajarxfce/hris-mobile/blob/feat/native-push/docs/ios-acceptance.md)
is a future procedure, not an executed test. Preserve the accepted Android Google
binding/session and the running private development stack.

Do not invent provider credentials, bank formats, tax rules or new scope to close
these rows. The initial expense bank submission is manual. New workload corpora or
rule packs need an explicit requirement, not inference from historical prose.

## Firebase state and external inputs

The user selected both FCM and Firebase Authentication in project `cmp-fajar` and
enabled Email/password and Google. Ignored local Android config exists at
`apps/android/google-services.json` in Mobile and Starter; do not print/commit it.
The public Google web client ID is
`415338369394-nop83je85ci1btt097rpl21r6sm96ldv.apps.googleusercontent.com`.

Actual Firebase email/password → HRIS session/MFA/company/logout passed; the temporary
provider user was removed. Actual SDK FCM token issuance and HRIS device registration
also passed. The supplied server credential now passed real foreground/background
FCM delivery and notification navigation on the owned emulator. Public Android
configuration remains separate from the sender credential; no private key belongs
in chat or Git. Scope replacement/duplicate/revoked hints retain their separate
automated coverage; this actual provider case used the correct selected account/company.

## Commands and environment

Use JDK 21 at `/usr/lib/jvm/java-21-openjdk`. Docker group membership on this VPS
requires the existing wrapper for new owned fixtures and backend tests, for example:

```sh
sudo -n runuser -u fajar -g docker -- env \
  JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  PATH=/usr/lib/jvm/java-21-openjdk/bin:/usr/local/bin:/usr/bin:/bin \
  ./gradlew --no-daemon check :apps:server:bootJar :apps:worker:bootJar
```

Use relevant focused targets for a scoped change; root tasks are `architectureCheck`
and `formatCheck`. The project is `:apps:server`, not `:apps:api`. JAR paths are
`apps/server/build/libs/apps-server.jar` and `apps/worker/build/libs/apps-worker.jar`.
Use `--no-daemon` when Docker group ownership matters. Avoid concurrent heavy local
builds/workloads on measurement CPUs. Follow the particular fixture's owned cleanup;
never use global Docker prune or remove retained data belonging to a live fixture.
Successful recovery summaries are emitted only after their owned cleanup passes.

Dashboard commands run in `apps/dashboard` with Node 24. Verify a development branch
with `gh workflow run ci.yml --ref feat/firebase-auth`; inspect exact commit, all
jobs and actual artifacts. Branch concurrency cancels older runs on that same ref.
Mobile/Starter handoffs have copyable JDK/SDK/Chromium environment commands.

Use only AVD `hris-mobile-dev`, serial `emulator-5560`, for routine native work;
never wipe its data. The acceptance APK uses a local fixture API override, so do
not distribute it as a normal configured build. Phone delivery follows Mobile's
`AGENTS.md`: notify before installation, use install-only and do not drive phone UI.

Keep current status, exact proof, limitations and next work in each accepted commit,
then push and verify upstream. Preserve historical failures in their measurement
record without leaving obsolete instructions in this active queue.
