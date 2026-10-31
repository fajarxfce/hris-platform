# Agent continuation — HRIS workspace

## Active collaboration implementation — 31 October 2026

The user approved [the full collaboration plan](collaboration/prd.md) and requested
implementation. Branch `feat/communications` starts from `1363abee`. The new
54-group scope is separate from the completed original HRIS audit below.
[Delivery](collaboration/delivery.json) is authoritative for its phases and
evidence; no chat/call/meeting group has been accepted yet. Read the
[architecture](collaboration/architecture.md) and
[threat model](collaboration/threat-model.md) before implementation.

P0 browser/provider checkpoint: six cleanup/diagnostic tests and the actual
Matrix/LiveKit browser scenario pass. [Evidence](measurements/collaboration-web-provider-2026-10-31.json)
pins source hashes and service digests; [the procedure](collaboration/provider-proof.md)
records peer verification, ciphertext inspection, recipient restart, client-side
media-key transfer, decoded audio/video and a wrong-key peer receiving RTP without
decoding. Owned containers/network were removed. The protocol experiment uses a
custom encrypted key event; MatrixRTC/native/device/HRIS admission/capacity remain
pending. Synapse accepts JWT expiry within its 120-second clock tolerance, so the
production broker must enforce strict grant expiry before internal exchange.
Android/web proof now also passes using Matrix Rust Kotlin SDK 26.10.23 in a
separate debug-only emulator APK: each endpoint decrypted the other endpoint’s
message, while both server events remained ciphertext. The combined two-scenario
run took 24.590s with full owned cleanup; 12 driver tests pass. See
[the native evidence](measurements/collaboration-android-web-provider-2026-10-31.json).
The previous browser-only source `9452af1e` also passed hosted CI
[41180880132](https://github.com/fajarxfce/hris-platform/actions/runs/41180880132).
Next: exact native CI, Apple binding verification and protected HRIS integration.
MatrixRTC key distribution and native media remain unverified. No real account
or phone was used.

Continue through P0 provider/native protocol proof, then all P1–P8 gates. Use
isolated fictional provider fixtures. Preserve the running schema-103 HRIS stack,
real Firebase identity, and installed Mobile build 4. Public domains, Apple VoIP
credentials and dedicated capacity/GPU resources are future deployment inputs,
not blockers for local implementation. Keep any unperformed native/capacity
acceptance explicitly unverified. Commit and push each atomic increment; update
this handoff and the delivery ledger. Mobile proves generic code before Starter.

P1 admission policy foundation now exists in `modules/collaboration/domain`:
company/account/native-session/device ownership, live and captured permission
intersection, strict 60-second-or-earlier expiry, MFA and replacement rejection.
Eleven new policy tests and all 35 identity-domain tests pass; architecture checks
pass. `collaboration.chat` is an assignable employee permission without any
membership/template migration. [Admission notes](collaboration/admission.md)
identify the remaining transaction, provisioning, broker, gateway and client
integration. No new endpoint/provider grant is enabled yet.

Platform browser-only CI also passed at `a9100ece` in `41181617114`. Mobile's
Android implementation is `477f9c60`; its corrected hosted runner is at
`eefc4121` after an obsolete Android SDK `tools` package failure. Corrected run
`41182128950` has now passed both actual-provider scenarios, including cleanup,
in 66.016s; Mobile `da43b35c` records its independent APK and source evidence. Apple native
store tests and iOS device-slice compilation passed at Mobile `e210508a` in
`41182072489` (Xcode 16.4 / Swift 6.1.2). Apple proof does not cover provider
interoperability or native media; its SDK requires iOS 16 for later integration.

Schema-104 collaboration registry/data checkpoint: two tables separate retained
company/account device identity from deletable parent-session bindings. Composite
foreign keys prevent account/company substitution; row policies admit only the
owner and restrict writes to the selected company. The repository counts distinct
live parent sessions across owned companies, including provisioning devices, and
excludes revoked/expired/credential-stale parents. Registration and binding roll
back together on failure. No endpoint, provisioning job or broker is enabled yet.

All 28 core database cases, the actual registry integration case, 11 admission
cases, formatting and architecture pass; 21 recovery-tool tests pass. Actual
PostgreSQL dump/restore retains registry facts, constraints and account RLS, so
the reviewed recovery ceiling advances to 104 without a new encryption envelope.
See [registry evidence](measurements/collaboration-device-registry-2026-10-31.json).
The existing live schema-103 stack remains untouched. The new collaboration
control workflow must be checked after push.

A PostgreSQL detail caught by integration tests: `FOR SHARE OF` the immutable
binding table also applies its absent UPDATE row policy and hides readable rows.
The read now locks only the device; the upcoming admission use case must lock the
parent session first to protect bindings against parent cleanup. Keep that order.
For local Docker/JDK checks, `sudo -n runuser -u fajar -g docker -- env
JAVA_HOME=/usr/lib/jvm/java-21-openjdk
PATH=/usr/lib/jvm/java-21-openjdk/bin:/usr/local/bin:/usr/bin ./gradlew ...
--no-daemon` works without changing socket permissions; the older `sudo -u ...
-g docker` form is not permitted by this host's run-as-group rule.

Next: durable provisioning and encrypted provider credential storage;
protected provider broker/gateway; browser-parent admission, native/web adapters
and chat screens. Native registration/admission transactions are now implemented
and tested as recorded below.
Retain all P0–P8 gates and all 54 feature groups as outstanding until accepted.

Company availability now has a separate `COLLABORATION` module, supported by
schema 105 and the existing dashboard policy editor. Disabling it does not disable
`COMMUNICATIONS` (the HRIS Inbox). Existing policy revisions are not rewritten.
Nine module values remain bounded, unique and validated; the dashboard accepts
all nine and shows a localized chat/call/meeting label. Mobile recognizes the key
and retains its unknown-future-module behavior. No chat endpoint is enabled yet.

Local acceptance: nine administration-domain cases, the registry integration case,
82 dashboard cases/type checking and 13 Chromium policy/editor scenarios pass.
The first 29-case database run passed 28 and exposed a fixture that committed a
policy head before its deferred history FK. The fixture now creates both in one
transaction; the corrected migration/actual-restore test and formatting pass.
Combined passing coverage is 29 database cases; do not describe the initial full
run as green. Twenty-one recovery-tool cases pass with reviewed ceiling 105.
The expanded collaboration-control CI subsequently passed both jobs at exact
`120c6d6b` in `41184251989`, including the full schema-105 database rerun.

The schema-104 control checkpoint `959e74f9` passed hosted run `41183444363`.
Mobile's policy mapping has 37 passing workspace cases and a successful dev-debug
assembly (3m54s). That APK is a build check only; the phone remains on release
build 4. No generic Starter port is due for this HRIS module identifier.

Native registration/admission now owns transaction boundaries and lock order.
The five-parent quota is serialized across companies; retry after native refresh
replays without another device/audit/outbox. A parent is locked before its device
binding; registration rechecks admission after writes, and active grants stop at
scheduled policy boundaries. Ten new real PostgreSQL scenarios, one registry
regression, eleven policy cases, formatting and architecture pass (53s). See
[registration evidence](measurements/collaboration-native-registration-2026-10-31.json).
API and worker DI plus the native registration route are now wired, as recorded
below. Native transport supplies the authenticated parent ID/version. Browser
parent bindings and provider provisioning remain unfinished.
Continue into the protected broker/gateway and actual chat; all P0–P8 gates remain.

The native route `POST /api/v1/companies/{companyId}/collaboration/native-device`
now uses verified `NativeIdentity`, company policy and idempotency. The separate
`HRIS_COLLABORATION_ENABLED` switch defaults false in API, worker and Compose;
current acceptance enables it only in fixtures. Registration acknowledges a
provisioning identity, never a usable provider credential. Existing memberships
need an explicit chat grant through normal administration. All seven HTTP cases
now pass, along with 69 existing OpenAPI contract cases, 22 collaboration cases,
formatting, architecture and Compose validation. Shared worker context: 104 passes,
one existing opt-in elapsed-retention skip. See [HTTP evidence](measurements/collaboration-native-http-2026-10-31.json),
which retains the three corrected fixture failures. The earlier registration
checkpoint `4d272499` passed hosted control run `41185015834`; the expanded
HTTP/worker control workflow passed at `292d938c` in `41185906869`. Runtime schema 103 and phone build 4
remain unchanged. Continue with provisioning/credentials/broker, not another
round of already-passing registration checks unless subsequent changes need it.

Schema 106 adds encrypted internal Matrix credential storage, with exact
company/account/device/generation AAD and deterministic provider identities.
Generation-checked rotations cannot race past one another. Revocation clears
secrets while retaining the terminal generation; the final revision is reserved
for revocation/retirement. Six real PostgreSQL scenarios pass, including complete
restored old/current-key envelope verification, corruption, rollback, concurrency
and the revision boundary. All 29 core database, eleven registration/registry,
eleven admission and 22 recovery-tool cases pass. See [storage evidence](measurements/collaboration-provider-storage-2026-10-31.json).
Recovery ceiling 106 uses schema-sensitive SQL so current schema-103 backups do
not query a nonexistent provider table. No new store bean/provisioner is wired yet;
Expanded control CI `41186737908` passed both jobs at exact `ef95326d`,
including the API/worker contexts and schema-106 recovery checks.

Next: bounded Matrix JWT login/transport, durable provisioning/reconciliation,
protected gateway and native/browser client admission. Provider credentials are
internal only. Gateway requests must recheck current HRIS authority; no public
Synapse path may bypass it. Standard native Matrix token refresh now passes the actual protocol proof below.
The HRIS gateway must implement that protocol with its own live admission checks. MatrixRTC,
Apple provider interoperability, native media and all product P0–P8 gates remain.

Native SDK credential renewal now passes against actual Synapse and the browser.
Only the fictional native session has a four-second access lifetime; after expiry,
Matrix Rust SDK refreshes and persists both replacement tokens, retaining the
same device signing key and bidirectional encrypted chat. Both provider scenarios
pass in 30.586s with owned cleanup. [Refresh evidence](measurements/collaboration-native-refresh-2026-10-31.json)
records source/APK hashes, twelve driver checks and Mobile's corrected full root
check (2,002 desktop cases, zero lint errors). Initial Detekt method length failure
is retained; the session callback store is now a separate fixture owner. SDK
`restore_session` must only initialize a client once; use the supported standard
refresh callback, not another restore on an already authenticated client.
This proves the provider mechanism, not HRIS gateway enforcement or product
Keystore storage. Update Mobile CI's fixed Platform ref to this driver before
its refresh fixture runs. The emulator is stopped; the phone was untouched.

The paragraphs below are retained prior-work history. Their old installed build
2 reference is superseded by Mobile's validated release build 4; consult Mobile's
current handoff for its artifact/source and future versionCode >= 5.

Updated 31 October 2026 (UTC). The user requested verification and completion of
all specified features, with every atomic commit pushed and handoffs retained in
Git. The full audit is complete after implementing the discovered gaps and validating every reviewed area.
New work on 31 October: the user requested a full Mobile layout/component review
and fictional sample data for the existing linked employee. [Sample setup](mobile-sample-data-2026-10-31.md)
is verified through normal employee APIs; Mobile's layout review remains active.
Earlier distribution/native acceptance remains valid for its recorded scope. Read `AGENTS.md`, this page and [PRD status](prd-status.md),
then the affected module contract. The [archived history](agent-history-2026-10-29.md)
preserves earlier work; its old next-step statements are not the active queue.

Current runtime: schema 103; API/worker application `2c2a59e3`, dashboard
`1e0522c0`, installed Mobile `devRelease` **1.0.0 / build 2**, built from latest
Mobile source `5ffb7300` with unchanged accepted application trees. The authorized
31 October WireGuard update preserved the signing identity and application data;
the pulled installed APK matches the built release exactly. No UI was launched.
Mobile handoff `bae07a27` records the evidence and the new versionCode floor of 2.
Read [current development runtime](current-development-runtime.md#android-release-installation--31-october-2026).
Use captured container IDs with `docker start` for checkpoint restart; installed
Compose has no `start --no-deps` and `compose start` can rerun migration dependencies.
Earlier pending statements below are retained history, not a new queue.

## Completed feature audit — 30 October 2026

All 61 initial Platform requirements and the eleven detailed module reviews are
accepted, together with Mobile's fifteen-area review and the applicable Starter
foundation. Every confirmed gap/correction has implementation and matching behavior
evidence. [Final Platform CI 41094285531](https://github.com/fajarxfce/hris-platform/actions/runs/41094285531) passed all ten jobs at
`93438f99`, with 3,090 API cases, 104 active worker cases, 1,111 mocked
browser scenarios, 56 actual workflows and successful schema-103 recovery.
[The inventory](measurements/feature-audit-2026-10-30.json) and
[CI report](measurements/workforce-full-ci-2026-10-30.json) retain exact scope.

GitHub's step metadata lagged its successful terminal result. The final job log,
source-matching browser reports and uploaded artifact digest independently verify
all required steps, including cleanup. [The retained observation](measurements/workforce-ci-reporting-lag-2026-10-30.json)
explains the raw pending step fields; no cancellation, rerun or source change was
needed. The independent local 41-case diagnostic passed in 273.489s.

The authorized private API/worker update is complete using verified application
`2c2a59e3` packages after a fresh schema-103 recovery checkpoint. Both services
started and API health returned UP; dashboard HTTP 200 and its exact index bytes
are unchanged. Full migration history, restricted runtime roles, Google/person
bindings, active native session IDs, document revision integrity and record counts
are preserved. SMTP remains disabled. Old images and separate data/key checkpoints
are retained; [rollout evidence](measurements/final-development-rollout-2026-10-30.json)
records their locations and hashes. No production release was performed.

Mobile review `f4f101a1` retains full CI `d170810f`/`41077194465`: 1,994 desktop,
1,676 browser and 104 Apple cases plus complete Xcode/target builds. The normal
private APK with application `3c6db4eb` is already installed; this backend update
required no phone action. Starter review `7605916` retains accepted `77063862`/
`40916187442` and the separately proved distribution upload correction. All 657
identical shared files and 60 differences are reviewed; no generic port remains.
Physical iPhone testing is waived, as requested. Actual Android Google/Home/FCM
acceptance remains recorded; this rollout does not claim another provider test.

The [approved-overtime boundary](workforce-feature-review-2026-10-30.md#approved-overtime-boundary)
is explicit: the original PRD's immutable requested/actual/approved workflow,
attendance adjustments and financial Payroll amendments are implemented. A future
post-approval overtime-fact amendment has no defined product contract and is not
represented as implemented. Public domain/DNS/TLS, signed Apple/APNs deployment,
further tax/bank packs and production operation retain their documented separate
scope. No specified implementation, source CI or owned local test remains pending.

## Retained acceptance history

The following entries preserve earlier increments; the active queue above takes
precedence over their historical pending statements.

Metadata is fully accepted: [final CI and phone evidence](measurements/document-metadata-final-ci-2026-10-30.json)
records four passed Mobile jobs, 1,994 desktop/1,676 browser/104 Apple cases, full
Xcode build and the normal WireGuard APK installed in 5.777s without launching UI.
Installed hash `1a114fbc388c843b45749a8475296f506563493a38bccabd146323ceb87cf791`
is verified directly. Both `documents.02` and `people.03` are closed. Reporting's
seven export policy-completion reproductions now pass full local acceptance:
186 API, eight active worker, 52 domain and 61 repository cases plus quality and
packages (349.481s), then actual private CSV/browser recovery (45.417s with setup).
[Evidence](reporting-assurance-2026-10-30.md) and preserved JARs in
`.work/reporting-assurance-2026-10-30/packages` retain the exact candidate. Its two
leaf corrections are not deployed. Final source CI `41087903927` is now dispatched after
Administration CI `41081153648` passed all ten jobs. Those JARs
contain both corrections and cannot be deployed using Administration-only CI. Continue remaining PRD scope.

Administration correction `3c8fb15a` is committed/pushed and locally accepted.
[Full CI 41081153648](https://github.com/fajarxfce/hris-platform/actions/runs/41081153648)
passed all ten jobs for exact source `3c8fb15a6c2471eef226e5f5c20b0bd8b8ea450a`. Three
private readers and policy saving now check final enrollment/proof assurance;
eleven genuine reproductions pass with one prior ordinary control. Validation
passed 81 API, 104 active worker, eight Administration domain, fourteen data and
two Jobs domain cases, quality and both packages (242.548s). Three actual browser
workflows passed in 65.484s and one actual worker/browser workflow in 60.248s,
including setup. See [correction](administration-assurance-2026-10-30.md),
[five-row/six-section review](administration-feature-review-2026-10-30.md) and
[measurements](measurements/administration-assurance-2026-10-30.json). All five
Administration inventory rows are accepted; current counts are 41/20.
No Administration deployment occurred. Continue Reporting, Payroll and Workforce,
then full Mobile/Starter review while CI runs; do not close the full goal.


Complete every specified Platform/Mobile feature and port demonstrated generic
fixes to Starter. The [full audit](feature-audit-2026-10-30.md) remains active:
Approvals, Communications, Identity, Expenses, Leave, Documents, People and Administration now
have forty-one reviewed initial rows; twenty initial rows await completion/review,
followed by Mobile/Starter. Identity, Approvals, Communications, existing Document behavior, Expenses and Leave
have accepted full-CI evidence. The formerly missing Document category and separate
identity validity now pass complete native and replacement full-CI acceptance.
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
mutable working copies and the versioned correction editor remain in progress.

Mobile `b0f93d67` retains encrypted correction commands using the existing shared
CommandStore/key and exact record/revision checks. Three domain and six actual
SQLite/IndexedDB cases pass; full local checks covered 1,936 desktop and 186 browser
cases in 193.331s. `82519a85` adds explicit intake, admitted local observation, bounded
manual delivery and review. Fourteen new domain scenarios cover original retries,
current ownership/MFA, uncertain outcomes, later headers, acknowledgement and resource
subscriptions. Full root checks, dev APK and Document Chromium suites pass in 180.515s:
1,950 desktop and 200 browser cases, without failures/errors/skips. The first twelve
flow failures were a read-only fixture; it was corrected with a separate denied control.
Both commits are pushed. Mobile Git retains exact cases, sources and APK hashes;
no automatic delivery or current-header replacement is introduced.

Mobile `fbd6a717` and `36ddd5ed` complete encrypted mutable working copies and
admitted open/save/discard/observation, including matching-draft cleanup after
confirmed acknowledgement. Exact local acknowledgement recovery preserves concurrent
edits and the original reviewed header. Full checks passed 1,968 desktop/218 browser
cases and dev APK in 182.812s. `8ca77f19` then fixes a reproduced catalog exposure:
Back from denied details previously showed cached title/file. Per-resource gating
now redacts that record, preserves paging/other rows and permits neutral admitted
recovery. Full checks passed 1,973 desktop/221 browser cases and dev APK in 183.001s;
two screenshots were inspected. All commits are pushed with durable Mobile evidence.
These are HRIS business changes; no generic Starter implementation port is due.

Mobile `832bbc09` and `017aa506` now complete the locally validated metadata editor
and independent retained overview. The editor preserves incomplete input and the
reviewed header, coalesces local writes, freezes confirmed input and reuses original
operations after response loss. Acquired current facts remain separate from old
receipts. Two real command-admission failures and hidden stale-draft feedback were
corrected; its full checks passed 1,980 desktop/223 browser cases plus the dev APK.

The overview reopens local drafts/corrections after published binary rows disappear,
with bounded 20-copy pages, original cursors and neutral resource recovery. Opening
or reloading sends no HTTP or commands. Two reproduced presentation failures showed
that one failed reader could leave another stale private projection visible; both
readers must now be ready/admitted. Full root checks, dev APK and Document browsers
passed in 190.845s: 1,987 desktop/224 browser, zero failures/errors/skips. The actual
storage/controlled-HTTP UI cases pass offline draft recovery and original retry after
later administrative edits; three overview screenshots were inspected. Both commits
are pushed; Mobile Git retains exact cases, source/APK hashes and failed predecessors.
No runtime/phone/provider change or reusable Starter implementation port occurred.

Mobile `3c6db4eb` adds the bounded local metadata summary and direct overview link
in Account Pending actions. Full local checks passed 1,994 desktop/230 Document
browser cases and the dev APK (173.186s). Six domain regressions and the actual
Account workflow retain original command/draft deduplication, scope and disposal.

[Actual Android/API/storage acceptance](native-metadata-acceptance.md) now passes
for that application: two offline process restarts retain an incomplete draft and
then the read-only original uncertain command. Explicit retry confirms the same
operation/input after a later administrative correction. Done clears the matching
working copy and command; all seven pending groups empty. The independent observer
confirms three history revisions, the later version-4 header and unchanged binary
revision. Summary reloads stay 36→36 and 48→48; 78 native requests include exactly
two successful company writes. The fixture passed in 432.451s with complete owned
cleanup. Its earlier ten-minute backend lifetime failure is preserved separately.
See [sanitized measurements](measurements/native-metadata-2026-10-30.json).

Mobile full CI `41071828262` passed Windows/Web and iOS simulator compilation/tests,
but its Linux browser capacity case exceeded Mocha's default two seconds. The
1,000-revision assertions remain intact; a bounded twenty-second module test
configuration passes all 75 Document data browser cases locally in 21.607s.
The complete Apple app and replacement full CI still need terminal verification.

Next: finish Mobile full/Apple CI before accepting `documents.02` or `people.03`,
then the controlled schema-103 API/static-dashboard/Mobile rollout. Physical
iPhone testing is waived. Finish detailed People review, remaining Platform modules
and Mobile/Starter. Keep shared finance/leave evidence free of private identity
numbers. This business capability has no generic Starter port. The full goal remains active.

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


[Detailed People review](people-feature-review-2026-10-30.md) now accepts four initial
rows against source-matched completed CI and all fourteen original sections. The
remaining `people.03` metadata condition awaits replacement Mobile CI `41077194465`
at exact `d170810f03852f46a948f9f4d5dea9ca4104bdbd`. Its predecessor `41071828262`
finished with Web/Windows/Apple success (104 Apple cases and complete Xcode app),
but Linux failed the browser's two-second capacity deadline. The corrected local
75-case browser run passed; do not relabel the original overall failure.

Administration review has started. Older private settings readers check MFA before
repository reads but not after; SaveCompanyClientPolicy similarly needs late
journal/receipt/enrollment review. These are source candidates only: reproduce
them before changing behavior. Public client-policy discovery has a distinct
authentication/recovery contract; do not add sensitive-reader MFA policy blindly.

The private pre-upgrade checkpoint `dev-before-metadata-20261030T150655` succeeded
with isolated verification (19.389s including restart), preserving schema 101 and
current Google/session state. API/dashboard are healthy again. New accepted
API/worker images and dashboard snapshot `~/.local/share/hris-dashboard-dev/1e0522c0`
are staged but not running. Older images have `before-metadata-20261030` tags.
Local `.work/metadata-rollout` retains private checkpoint/build/baseline details.
Continue controlled schema-103 rollout after Mobile CI; no phone installation yet.

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
