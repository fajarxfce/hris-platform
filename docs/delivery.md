# Delivery and validation

## Status

End-to-end scope and remaining work are tracked in [completion](completion.md),
including the separate [HRIS Mobile](https://github.com/fajarxfce/hris-mobile) repository.

- Repository and product/architecture specification: initialized.
- Backend foundation: implemented Gradle conventions, PostgreSQL migrations/jOOQ generation, transaction/error boundaries, company RLS, immutable audit/outbox storage, correlation IDs, CI, and local container configuration. Business capabilities and job processing are tracked below.
- Mobile API: native sessions, atomic command receipts, optimistic versions, and consistent localizable JSON problems are implemented. Owned expense/leave-request/leave-balance/overtime/payslip/payment-progress and account-owned inbox bootstrap/incremental synchronization, explicit client collection selection, encrypted scope-bound cursors, bounded post-commit publication, retention, and worker lifecycle are implemented. Inbox requires explicit selection; the omitted-selection projection remains compatible with earlier clients. Other collections and a mobile client integration remain subsequent work.
- API description: generated OpenAPI 3.1, exact registered-route coverage, filter authentication contracts, safe localizable problems, binary negotiation metadata, and CI artifacts are implemented. Runtime documentation is optional and authenticated, with a single canonical locale and bounded cache.
- Identity: password sign-in, persistent cookie sessions, CSRF/session rotation, bootstrap, current-company permissions, revocation checks, credential-race handling, shared sign-in limits, and bounded password verification implemented. Company member administration is implemented, including versioned grants/revocation and sensitive-grant restrictions. Authenticator MFA, recovery codes, live credential revocation, and recent-authentication gates are implemented. Direct native password sign-in, pending MFA sessions, native authenticator enrollment, access/refresh rotation, reuse detection, session management, and native MFA renewal are implemented. Configured OIDC sign-in and account bindings are implemented. Configurable company role templates, versioned assignment snapshots, and global account activation/permission administration are implemented. Local invitations, one-use password recovery, and durable SMTP delivery are implemented.
- Organization and people: company setup, organization units with bounded name/code search and coherent parent details, employee onboarding, scoped directories, and effective employment revisions implemented. Restricted profile editing, immutable history, and cancellation of scheduled employment revisions are implemented. Atomic cross-company transfer with source membership revocation is implemented. Versioned lifecycle checklists, task assignment/history, and explicit offboarding with access revocation are implemented. CSV preview/apply jobs, field errors, partial outcomes, cancellation, and explicit recovery are implemented. Independently authorized initial person/account binding and historical account identity are implemented. Scheduled lifecycle execution remains planned.
- Approvals: effective templates, immutable snapshots, assignment/delegation, inbox, optimistic decision storage, and pure transitions implemented. Bounded policy/delegation administration, live-access revalidation, and serialization of revocation/reassignment with decisions are implemented. Leave submission, staged decisions, withdrawal, and cancellation apply atomic business consequences. Expense submission, staged decisions, return-for-correction, and withdrawal are integrated. Overtime and payroll review also use the shared boundary, including retained maker exclusions.
- Workforce: versioned shifts, weekly schedules, roster overrides, holidays, and scoped calendar reads implemented. Attendance capture and independent verification are implemented. Versioned HR corrections are implemented. Durable monthly factual closing and explicit recovery are implemented. Planned/actual overtime, independent staged review, withdrawal, immutable evidence, closing gates/snapshots, and employee synchronization are implemented. Late adjustments and bulk/reset workflows remain planned.
- Leave: effective policies, independent balance adjustments, immutable scoped ledger, snapshotted requests, staged approval, withdrawal, and cancellation implemented. Required/optional validated attachments, immutable business references, and scoped content downloads are implemented. Versioned ledger projections, independent monthly/annual entitlement posting, immutable evidence, explicit year closing with bounded carryover/expiry, closed-year guards, and owned balance sync are implemented. Durable company accrual/year-closing batches, per-employee outcomes, cancellation, and bounded recovery are implemented. Payroll cutoff integration is implemented; automatic scheduling remains planned.
- Documents: the bounded private S3 adapter, Garage compatibility, fenced temporary-object cleanup, and authorized resumable upload/status/cancellation are implemented. Bounded ClamAV/Tika inspection, fenced validation jobs, publication, and explicit recovery are implemented. Authorized bounded Range/ETag downloads and optional private Garage/ClamAV deployment configuration are implemented. Bounded storage inventory jobs, explicit resume, and conservative orphan reconciliation are implemented. Immutable business evidence references and enforced expense/leave registration are implemented. Explicit versioned retention policies, archival/restoration, legal hold, and guarded retirement of unreferenced accepted revisions are implemented. Physical deletion uses the existing cleanup worker; immutable metadata and business references remain retained.
- Expenses: effective category policies, immutable history, scoped reads, and independent policy administration are implemented. Versioned claim drafts, bounded lines/receipt references, current self/team/company scope, cancellation, and draft history are implemented. Immutable evidence submission, maker snapshots, approval routing, exact reviewer scope, and withdrawal are implemented. Independent staged review, current duplicate acknowledgement, rejection, and return/resubmission are implemented. Scoped receipt downloads, independent payment preparation/release, bounded instruction export, per-item reconciliation, and scoped claim payment progress are implemented. Bank-specific formats and settlement corrections remain planned.
- Payroll: dated company policy, independent compensation administration, immutable history, scoped reads, and atomic versioned commands are implemented. The pure calculation library includes reviewed TER tables, resident annual reconciliation, domestic PPh 26, bounded gross-up/net treatment, configured social insurance contributions, standard overtime, explicit proration, and THR. Reviewed earning conventions are retained in compensation history. Explicit opening tax history and independent verification are implemented. Period drafts/cancellation, immutable participant snapshots, exact workforce references, independently verified monthly input history, and pure monthly composition are implemented. Durable per-employee calculation, immutable source/results, source cutoffs, and bounded recovery are implemented. Staged aggregate review, immutable decisions, explicit withdrawal, and bounded resubmission are implemented. Immutable, independently approved finalization, bounded publication jobs, and retained person/month assessments are implemented. Finalized same-year tax-history chaining, exact predecessor references, and cross-month THR protection are implemented. Immutable employee/company payslip JSON reads, bounded month pagination, and owned mobile synchronization are implemented. Independent payment preparation/release, deterministic CSV export, confirmed reconciliation, company-scoped cross-module bank references, and owned payment-progress sync are implemented. English/Indonesian PDF downloads use the finalized snapshot, current access checks, embedded fonts, bounded rendering, and the shared Range/ETag transport. Amendments and browser workflows remain planned.
- Communications: versioned drafts/groups, immutable revisions, bounded summary/detail queries, and atomic replayable commands are implemented. Durable optional schedules, publication-time audience snapshots, fenced all-or-nothing inbox publication, explicit cancellation/recovery/archival, and account-owned versioned read/acknowledgement are implemented. Current access and native bearer commands are enforced. Explicit account-owned inbox synchronization, including versioned read/acknowledgement updates and archival tombstones, is implemented. A versioned current-audience preview is implemented. Native session-owned, encrypted, versioned push registration/disable is implemented. The optional FCM transport and durable inbox dispatch are implemented, including live access/session checks, bounded checkpoints/retries, token retirement, expiry/retention, and owned worker lifecycle. Dashboard announcement directories, current/revision review, and publication-job navigation are implemented. Audience editing and publication commands remain in progress.
- Reporting: company and group headcount with effective-date status/contract breakdowns, distinct persons, and current source/report access is implemented. Group reads independently authorize up to 32 companies and enforce client availability and MFA. Other metrics and exports remain planned. Administration has versioned company client policy, module availability, minimum client builds, and scheduled maintenance with authorized recovery. Bounded company audit metadata search, exact filters, immutable cursor navigation, and current access checks are implemented. Company-scoped job queries/cancellation, durable optional execution schedules, bounded PostgreSQL leasing, and isolated Spring Batch metadata are implemented. The shared outbound SMTP boundary, identity invitation/recovery delivery, Spring Batch worker execution, and workforce closing/recovery are implemented. Other operational controls remain planned.
- Azure-style dashboard: password/SSO entry, session bootstrap, authenticator enrollment/challenge, recovery-code acknowledgement, in-place account verification with freshly authorized form retention, current-company selection, an authorized overview, organization directories, parent details, creation and versioned editing, protected form departures and explicit recovery of uncertain saves, scoped employee directories, employment overview/history and dated revision editing with coherent assignment references, scheduled revision cancellation with guarded review and receipt recovery, employee creation with bounded assignment lookup and explicit receipt recovery, separate personal profiles with versioned editing and immutable history, lifecycle template catalogs and versioned checklist editing, scoped case/checklist/history views, assigned-task queues, versioned task changes and assignment, company/group headcount reports, audit metadata search/details, effective/configured client policy with immutable revision navigation, versioned editing and availability recovery, and scoped job monitoring/cancellation are implemented. Shared Fluent components, responsive light/dark layouts, English/Indonesian messages, bounded requests, owned cancellation, and company/account cache cleanup are implemented. Lifecycle case initiation from employee details, cancellation, and onboarding/offboarding completion with guarded review are implemented. Employee CSV selection, template download, preview submission/recovery, directories, summary counts, row proposals/outcomes, attempt review, and guarded apply/resume/cancel actions are implemented. The current approval inbox, scoped request/stage review, and effective template administration with ordered stages, approver lookup, immutable revision review, and versioned save recovery are implemented. Owned delegation lists, retained details, and versioned delegation editing with explicit company timezone handling are implemented. Guarded current-stage reassignment, maker exclusions, explicit conflict review, and original-command receipt recovery are implemented. Scoped leave request directories, submitted schedule/policy review, approval/cancellation references, evidence metadata, and paginated history are implemented. Guarded leave approval, rejection, withdrawal, and cancellation reviews with receipt recovery are implemented. Request-scoped evidence downloads from detail and action reviews include bounded binary acquisition, cancellation, access revocation, and MFA recovery. Administrative leave policy catalogs, current definitions, immutable revision review, and versioned policy creation/editing with guarded receipt recovery are implemented. Scoped employee/year balance directories, current balances, paginated ledger review, selected movement evidence, and related-request navigation are implemented. Independent balance adjustments include initial funding, signed half-day corrections, current-version review, original receipt recovery, and protected drafts. Other business resource workflows remain planned.
- Compose employee application: HRIS Mobile uses production HTTP engines for native password sign-in, MFA enrollment/verification, recovery acknowledgement, secure refresh, current account/company access, company selection, self-profile and assignment reads, native device-session management, and remote logout. Localizable API problems, product flavors, retained tab state, reduced motion, and account/workspace navigation ownership are integrated. The encrypted inbox reader and bounded HRIS change-feed client are connected. Local checks and builds, real native API probes, and Linux/Windows/Apple CI passed; detailed evidence is recorded below. Physical-device validation, native OIDC, offline company admission, durable commands, push, and remaining employee workflows are in progress.

Implementation status must reflect executable behavior and observed checks, not scaffolding or empty routes.

## Sequence

1. Repository, license, specifications, architecture rules, and initial contracts.
2. Gradle conventions, Spring Boot, PostgreSQL/Flyway/jOOQ, transactions/errors, audit/outbox, tests, and local deployment.
3. Authentication, company isolation/permissions, organization, people, history/import.
4. Approval policy, roster/attendance/offline verification/overtime, and leave ledger.
5. Resumable documents, claim policy, and payment reconciliation.
6. Payroll rule packs, durable calculation, review/finalization, payslips, payment exports/adjustments.
7. Communication, reporting, flags/availability, and operational controls.
8. Dashboard shell and module workflows.
9. Compose integration against stable API contracts.

## Gates

Use domain tests, architecture checks, PostgreSQL integration tests, transport contracts, job/retry/recovery tests, and UI E2E. Reproduce races with controlled synchronization, not sleeps. Include double submission, late results, lost response, access revocation, overnight/DST calendars, and transaction rollback.

Payroll acceptance includes sourced statutory golden cases, rule-version selection, deterministic rounding, restart checkpoints, concurrent finalization, historical immutability, and no duplicate payment export. Configured rules do not establish legal validation until those fixtures are reviewed.

Performance targets on a 4-vCPU/16-GiB reference deployment: interactive API p95 below one second during the attendance scenario; a 5,000-employee payroll below ten minutes; bounded file/worker memory. Record actual workload, measurements, and limitations. These are targets, not current results.

Commits are atomic and include relevant tests/docs. No co-author trailers. Push increments; releases and production deployment require explicit instructions. Documentation and demo data must not contain secrets or real employee data.

## Observed validation

Backend foundation: Gradle check and bootJar passed; six PostgreSQL integration tests passed, covering pooled company isolation, denied writes, typed/raw SQL failure mapping, failed-result rollback, cancellation rollback, and immutable audit records. Compose configuration and actionlint passed. No business module, deployment, or performance benchmark is claimed by these checks.

Identity password/session slice: full Gradle check and bootJar passed. Four domain tests and four HTTP/PostgreSQL tests passed alongside the six database tests (14 total). HTTP tests verify persisted sessions, session/CSRF rotation, logout, company isolation, and membership revocation. No MFA, OIDC, or native-token validation is claimed yet.

Company setup slice: full Gradle check and bootJar passed (17 tests total). Three additional HTTP/PostgreSQL tests verify concurrent create/replay, payload mismatch, no automatic payroll grant, stale updates, validation, and rollback without consuming failed operation IDs. Test application contexts close after each suite.

Organization/people slice: full Gradle check and bootJar passed (25 tests total). Five additional HTTP/PostgreSQL tests cover history immutability/as-of reads, stale edits, replay after reference archival, cross-company reference rejection, concurrent employee numbers without orphan people, organization cycles, and team/self resource scope. Three pure policy tests cover reporting cycles across future effective boundaries. Shared HTTP fixtures own bounded clients and close application contexts between database lifetimes.

Approval/access slice: full Gradle check and bootJar passed (36 tests total). Six policy tests cover blocked stages, author/beneficiary exclusion, delegation expiry and source-access revocation, ambiguous policy selection, and bounded monetary input. Five HTTP/PostgreSQL tests cover submitted snapshots, cross-company reads, competing decisions with one audit, idempotent reassignment, delegation ownership/revocation, and denied sensitive self grants. The inbox uses typed jOOQ timestamp parameters. Business ledger/payroll consequences are not claimed by these tests.

Calendar slice: full Gradle check and bootJar passed (46 tests total). Six pure tests cover overnight anchoring, DST gap/overlap, calendar precedence, future revisions, and location/duration validation. Four HTTP/PostgreSQL tests cover frozen shift snapshots, effective assignments, immutable history, holiday/roster precedence and replay, stale/cross-company shift references, range bounds, and employee scope. No attendance or payroll behavior is claimed by these checks.

Current-team access fix: full Gradle check and bootJar passed (47 tests total). The additional HTTP/PostgreSQL test advances a controlled clock across midnight in Asia/Jakarta and verifies that former managers lose historical employee/calendar access while current managers retain it. Combined team/self directory grants are covered.

Attendance capture slice: full Gradle check and bootJar passed (56 tests total). Six policy tests and three HTTP/PostgreSQL tests cover offline/expired proof, GPS validation, overnight totals, frozen schedules, lost-response replay, proof ownership/reuse, self-review denial, competing reviewers, rollback without consuming proof, immutable events, and live access revocation. Closing and payroll integration are not claimed by these tests.

Attendance correction slice: full Gradle check and bootJar passed (60 tests total). Two policy and two HTTP/PostgreSQL tests cover independent corrections, absence, pending-evidence gates, immutable history, optimistic concurrent corrections, original receipt replay, self-correction denial, and later punches that cannot replace corrected facts.

Leave balance foundation: full Gradle check and bootJar passed (65 tests total). Two policy and three HTTP/PostgreSQL tests cover effective policy revisions, stable type codes, immutable history, half-day input bounds, competing reductions without overspending, adjustment replay, chronological pagination, current team access, self-adjustment denial, and archived-type corrections. Request reservations are a subsequent slice.

Leave request workflow: full Gradle check and bootJar passed (79 tests total). Six additional pure leave tests, two approval-access tests, and six HTTP/PostgreSQL tests cover charged working days, future employment eligibility, half-day overlap across types, two-year reservations, competing submissions/decisions, immutable snapshots, staged approval, independent cancellation, current delegated grants, and rollback of the complete decision after a deliberately failed allocation cleanup. PostgreSQL generated projections are excluded from the BEFORE-trigger comparison while their source snapshot stays immutable.

Request/database boundaries: full Gradle check and bootJar passed (85 tests total). New tests verify declared/chunked JSON size limits without database writes, malformed path/query/header responses, safe mapper diagnostics, bounded PostgreSQL queries, timeout settings cleared across reused connections, and rollback/propagation of late interruption. No throughput or memory benchmark is claimed.

Password resource controls: full Gradle check and bootJar passed (90 tests total). Additional tests cover shared concurrent attempt budgets, failed-attempt accounting, bounded expiry cleanup, backward clock movement, missing-account parity, window recovery, saturated hashing, cancellation/failure permit cleanup, and preserved server-session login. Per-account/origin budgets are enabled by default and enforced in their dedicated integration suite.

Authenticator MFA: full Gradle check and bootJar passed (101 tests total). Additional tests cover RFC 6238 vectors, account-bound encryption and key rotation, mandatory and voluntary MFA, enrollment replay and expiry, session/CSRF rotation, concurrent one-use authenticator and recovery codes, recent-authentication limits, committed failed-attempt budgets, missing-key refusal, and complete rollback after a deliberately failed audit insert. API interception also protects handlers without an Actor parameter. Compose configuration validation passed; no live deployment is claimed.

Native session slice: full Gradle check and bootJar passed (109 tests total). Eight HTTP/PostgreSQL tests cover exchange replay, cookie/bearer separation, malformed or duplicate authorization headers, concurrent refresh with equal/different operation IDs, committed reuse revocation, complete refresh rollback after a failed audit insert, credential expiry and revocation, account ownership, native MFA renewal, and bounded session issuance/rotation. No device integration or performance benchmark is claimed.

Durable job foundation: full Gradle check and bootJar passed (113 tests total). Four additional HTTP/PostgreSQL tests cover worker-only leasing, queue and business RLS separation, restricted Spring Batch metadata, competing leases with company/global limits, stale ownership and transactional rollback, immutable completed updates, versioned cancellation, and audited exhaustion after repeated crashes. Retry/exhaustion policy is owned by a domain use case. The Spring Batch executor, process lifecycle, feature scheduling, and feature-specific recovery are not claimed by these checks.

Monthly closing and worker execution: full Gradle check and both application bootJar builds passed (127 tests total). Six new HTTP/PostgreSQL tests cover closing replay, competing finalizers, calendar gates, current/prior-attempt late evidence, cancellation, expired-lease rollback, failed final audit rollback, immutable snapshots, scoped reads, and explicit recovery. Three policy tests cover malformed calendars, pending/incomplete evidence, frozen schedules, and corrected versus missing attendance. Five worker/PostgreSQL tests exercise real Spring Batch metadata, revoked initiating accounts, non-progress termination, shutdown/deferral, and late cancellation after thread detachment. Checked interruption is explicitly propagated through the Batch tasklet so it cannot silently repeat a stopped operation. Compose configuration, shell syntax, and actionlint passed. No live deployment or throughput benchmark is claimed.

OIDC account sign-in: full Gradle check and both application bootJar builds passed (137 tests total). Seven HTTP/PostgreSQL tests and three transport tests cover PKCE, session/CSRF rotation, state/nonce/signature/issuer/audience/expiry rejection, ignored provider role/MFA claims, minimal persisted sessions, one-use callbacks, account binding revocation/replay, global receipt isolation, competing updates, complete audit-failure rollback, and bounded response/callback capacity. Compose configuration and documentation links passed. The protocol was exercised against an owned local test issuer; no live identity provider or browser dashboard integration is claimed.

Personal profile management: full Gradle check and both application bootJar builds passed (141 tests total). Four HTTP/PostgreSQL tests cover sensitive-profile versus directory access, owning-company edits, shared-person RLS, employee self reads, independent profile/employment versions, historical pagination/immutability, concurrent saves, receipt replay/mismatch, validation, and complete rollback after failed audit insertion. Documentation links passed. No employee dashboard or cross-company transfer workflow is claimed by these checks.

Company role templates: full Gradle check and both application bootJar builds passed (146 tests total). Five additional HTTP/PostgreSQL tests cover frozen permission sources, explicit reapplication, archive-safe receipt replay, stale selections, company scope, sensitive/self-grant denial, sensitive reactivation, competing role codes, immutable revisions/applications, assignment rollback after failed audit, and bounded definition counts/pagination. The backend exposes its assignable permission catalog. No role-management dashboard is claimed by these checks.

Outbound mail boundary: full Gradle check and both application bootJar builds passed (152 tests total). Six SMTP/unit tests cover stable message IDs, single recipients, connection cleanup, bounded stalled-provider reads, required TLS, envelope/UTF-8 size limits, header/group rejection, transient versus permanent SMTP failures, secret-free representations, and direct/nested cancellation and late interruption. Existing backend tests remained up to date where inputs were unchanged. Documentation links passed. Tests used an owned loopback SMTP fixture; no real email was sent, and no invitation/recovery consumer or production delivery is claimed yet.

Global account administration: full Gradle check and both application bootJar builds passed (157 tests total). Five new HTTP/PostgreSQL tests cover restricted credential-free projections, global versus company-only administration, session revocation across deactivate/reactivate and permission changes, immutable login identity, receipt replay/mismatch, competing administrators, failed-audit rollback, self-lockout protection, and pending-invitation activation denial. No invitation acceptance or password recovery is claimed by these checks.

Local account provisioning and recovery: full Gradle check and both application bootJar builds passed (176 tests total). Additional HTTP/PostgreSQL, worker/PostgreSQL, SMTP, and policy tests cover invitation replay/resend, concurrent one-use confirmation, purpose/expiry/version binding, expiry during password hashing, generic recovery responses and separate attempt budgets, disabled delivery, preserved MFA, cookie/native revocation, and complete rollback after failed audit. Worker tests cover actual loopback SMTP outside transactions, encrypted-token erasure, stale/competing/reclaimed leases, bounded retry/exhaustion, lost acknowledgement with stable message identity, delivery timeout, shutdown, cancelled timers, and late cancellation after executor reuse. Compose configuration and documentation links passed. No real email, production deployment, or browser acceptance screen was exercised.

Scheduled employment cancellation: full Gradle check and both application bootJar builds passed (180 tests total). Four additional HTTP/PostgreSQL tests cover immutable cancellation history, effective and interval reads, future-hire changes, company-local date boundaries, competing cancellations, receipt replay after the planned date, company isolation, cycle restoration, and complete audit-failure rollback. The full check exposed a worker test observation race after executor termination; the assertion now joins exiting threads within a shared two-second bound before checking for survivors. The worker suite and complete check subsequently passed. No transfer, onboarding checklist, or offboarding automation is claimed by this slice.

Cross-company transfer slice: full Gradle check and both API/worker bootJar tasks passed (189 tests total). Nine additional PostgreSQL/HTTP tests cover narrowly scoped secondary-company RLS and pool reuse, immutable source/destination history, existing destination grants, current/planned employment access retention, pending revisions and reporting dependencies, competing transfers, credential revocation during a paused command, self/last-administrator protection beyond a paginated directory, and complete rollback after a deliberately failed destination audit write. No future-dated transfer, compensation/balance migration, or deployment is claimed.

People lifecycle slice: full Gradle check and both API/worker bootJar tasks passed (201 tests total). Twelve new policy/HTTP/PostgreSQL tests cover frozen templates and immutable task history, bounded definitions, required/optional task outcomes, assignee queues and access removal, case/company isolation, company-local offboarding dates, pending reporting/revisions/cases, competing task reopening and finalization, credential revocation while finalization is paused, transfer/case races, source membership retention and self/last-administrator protection, and complete rollback after a failed final audit. Scheduled lifecycle execution and browser workflows are not claimed.

Employee import slice: full Gradle check and API/worker bootJar tasks passed (217 tests total). Sixteen additional CSV, policy, HTTP/PostgreSQL, and worker tests cover RFC 4180/BOM/Unicode input, malformed and bounded files, per-field errors, every duplicate occurrence, explicit partial approval, changes after preview, competing employee numbers and confirmations, cancellation, exhausted jobs, credential revocation during a paused step, fenced recovery, and complete row rollback after failed audit. Real Spring Batch executions preview and apply under restricted worker credentials and resume after a committed row without duplicate effects. Documentation links passed. No live personnel import or 5,000-row throughput measurement is claimed.

Person/account binding: full Gradle check and both bootJar tasks passed (223 tests total). Six new HTTP/PostgreSQL tests cover independent authorization, active company membership, owner scope, initial binding and self access, immutable identity/history, receipt replay, competing bindings, concurrent profile edits, live credential revocation while paused, expired authentication, and complete audit-failure rollback. The concurrent profile test exposed a reversed account/foreign-key lock dependency; changing the identity guard to `FOR NO KEY UPDATE` preserved credential serialization while permitting references to immutable IDs. Focused and full checks passed after the fix. No employee-client integration is claimed.

Private object storage boundary: full Gradle check and API/worker bootJar tasks passed (230 tests total). Seven new transport/resource tests cover real Garage 2.4.1 uploads, SHA-256 metadata, conditional/exact ranges, anonymous denial, 5-MiB limits, invalid destinations, bounded capacity, no automatic retries, stalled headers, trickling bodies, nested/late cancellation, and cancellation during pending SDK I/O with released capacity. Garage ignored conditional PUT and rejected the SDK's default streaming payload signature; the adapter uses compatible fixed-length signing, and document orchestration must use distinct keys per write attempt. No document publication, antivirus, retention, or production storage deployment is claimed by this boundary.

Temporary-object cleanup: full Gradle check and both application bootJar builds passed locally (237 tests total). Five HTTP/PostgreSQL and two worker/PostgreSQL tests cover company isolation, worker-only competing claims, stale leases, immutable keys, rollback of retained keys and acknowledgement, idempotent deletion after lost acknowledgement, bounded retry/exhaustion, explicit retry replay, revoked uploader recovery, and owned timer/thread shutdown. Compose configuration and documentation links passed. A separate CI run of the preceding storage slice exposed a slow-response shutdown issue; it is being investigated and is not counted as a passing remote check.

Storage response cancellation: the CI failure was traced to draining unread response bytes during closure. The S3 adapter now uses the bounded Apache 5 transport and explicitly aborts failed/cancelled response bodies. All seven storage tests passed, including the unchanged slow-response deadline assertion (0.507 seconds in the focused run); the full local check and both bootJar builds passed with 237 tests. Remote verification is tracked by CI separately.

Resumable document uploads: full Gradle check and both bootJar builds passed (253 tests total). Fourteen HTTP/PostgreSQL tests and two request-stream tests cover contiguous offsets and original receipt replay, competing/reclaimed writers with unique keys, cancellation and permission revocation during external I/O, complete audit-failure rollback, bounded retry/active-upload/quota limits, scoped self-service/classifications, expired revision replacement, immutable attempts, declared/chunked body limits, four concurrent request slots, real socket inactivity timeout, and absolute/late body deadlines. The existing JSON request boundary tests also passed. Storage writes in these document workflow tests use an owned datasource probe; Garage transport is validated separately. Content scanning, publication, and downloads are not claimed by this slice.

Content inspection boundary: full Gradle check and both bootJar builds passed (262 tests total). Nine additional tests exercise actual ClamAV 1.5.4 with an owned EICAR signature database, streamed content/digest/type detection, bounded connection/session capacity, stalled reads/writes, trickling responses, cancellation and late acquisition cleanup, corrupt chunks, and preserved storage failures after failed cleanup. Compose configuration and documentation links passed. The upload slice also passed remote CI. No live signature updates, validation jobs, publication, or downloads are claimed by these checks.

Document validation and publication: full Gradle check and both application bootJar builds passed (274 tests total). Ten additional HTTP/PostgreSQL tests cover accepted/rejected evidence, complete manifests, competing submission/replay, cancellation/revocation/expiry during inspection, reclaimed leases, complete audit/checkpoint/retention rollback, partial cleanup retention, explicit eight-attempt recovery, crashed-job exhaustion, immutable evidence/history, and quota after publication. Two worker/PostgreSQL tests exercise actual Spring Batch execution, restricted runtime credentials, I/O outside transactions, interrupted scanner ownership, and recovery by a new lease. Documentation links passed. Scanner/storage workflow tests use owned datasource probes; real Garage and ClamAV transports are covered separately. Downloads and deployment are not claimed by this slice.

Authorized document downloads: full Gradle check and both bootJar builds passed (285 tests total). Six new document HTTP/PostgreSQL tests cover complete/cross-part/suffix ranges, validators, HEAD without storage reads, malformed/multipart/unsatisfiable ranges, foreign/unready/rejected revisions, corrupted bytes, and permission revocation during pending storage I/O. Two real socket/HTTP tests verify backpressure, bounded admission, slow/disconnected clients, timeout cancellation, and slot reuse. Three transport ownership tests cover late results, interruption after executor reuse, bounded shutdown, and missing read progress. Compose configuration and documentation links passed. These tests establish behavior and configured bounds, not throughput or heap benchmarks; real S3 transport remains covered in its own suite.

Private document service configuration: the merged Compose configuration, Python syntax, workflow lint, and documentation links passed. Owned temporary Garage/ClamAV containers verified private bucket initialization, the exact configured health commands, non-root/read-only scanner operation, configured scan limits, and clean/EICAR verdicts. The checker is included in CI; the preceding document download commit also passed remote CI. No deployment, live signature download/update schedule, full-signature memory measurement, or maximum-file throughput test was performed for this configuration slice.

Expense category policies: the full Gradle check and both application bootJar builds passed (292 tests total). Seven additional HTTP/PostgreSQL tests cover effective/backdated revisions, immutable codes/history, bounded decimal input and pages, company/read/history scope, original receipt replay after archival, competing codes/versions/capacity, credential revocation while waiting, and complete audit-failure rollback. Documentation links passed. The private document-service configuration also passed remote CI. Claim submission, expense approval, payments, and browser workflows are not claimed by this slice.

Expense claim drafts: the full Gradle check and both application bootJar builds passed (302 tests total). Ten new HTTP/PostgreSQL tests cover immutable draft/line/receipt evidence, exact historical totals, late insertion rejection, concurrent edits/cancellation/capacity, original receipt replay, scoped references and current team access, bounded money/history/revisions, permission and credential revocation while waiting, and complete audit-failure rollback. A subsequent access hardening change restricts live permission checks to the request’s originally resolved capabilities. After that change, all 18 expense HTTP/PostgreSQL tests and ten identity domain tests passed, including an added test rejecting a newly granted capability until a fresh request; architecture/format checks and both bootJar builds also passed. Documentation links passed. Receipt references may point to unfinished uploads while drafting; submission must validate ready evidence in a later slice. No expense approval, payment, dashboard, or performance validation is claimed.

Approval access and resource bounds: the full Gradle check and both application bootJar builds passed (319 tests total). Sixteen new HTTP/PostgreSQL tests cover competing capacity claims, paginated administration, fail-closed policy/delegation overflow and repair, current delegate/delegator grants, revocation and reassignment before a waiting decision, a decision completing before competing revocation, policy archival during submission, credential revocation before receipt replay, permission removal during each leave action, newly linked/current/future beneficiary identity, complete audit-failure rollback, and delegation expiry with original receipt replay. A focused run initially found three incorrect assertions in the new tests (a revoked/unrelated caller used for balance verification and a field absent from the condensed workflow response); these were corrected before the successful focused and full runs. Documentation links passed. The preceding expense draft commit passed remote CI. No expense submission/review, browser workflow, deployment, or throughput benchmark is claimed by this slice.

Expense submission and withdrawal: full Gradle check and both application bootJar builds passed (335 tests total). Thirteen additional HTTP/PostgreSQL tests and three pure policy tests cover validated receipt snapshots, immutable category/cost-center labels, dated eligibility/limits, category totals across revisions, mixed-policy routing, historical maker exclusion, raw candidate overflow, duplicate receipt signals, exact reviewer scope, revocation, concurrent edits/submissions, receipt replay, bounded resubmission/open capacity, and complete submission/withdrawal rollback after failed audit insertion. Receipt setup uses the actual upload/validation use cases with owned storage/scanner probes; submission performs no additional object reads. Finance content delivery, expense decisions, and payment reconciliation are subsequent slices.

Expense review and correction: full Gradle check and both application bootJar builds passed (356 tests total). Ten review HTTP/PostgreSQL tests, six access/concurrency HTTP tests, and five pure policy tests cover staged approval, rejection, return/edit/resubmission, current duplicate acknowledgement, historical/current maker and beneficiary exclusion, delegation revocation/expiry, reassignment, permission/credential changes while waiting, competing decisions/withdrawal, original receipt replay, complete audit-failure rollback, and a database commit rejected without matching review evidence. The earlier draft/submission regressions also passed. Finance receipt content delivery and payment reconciliation remain subsequent slices. No browser workflow or performance measurement is claimed.

Expense receipt delivery: full Gradle check and both application bootJar builds passed (362 tests total). Six additional HTTP/PostgreSQL tests cover owner/reviewer access without broad personal-document permissions, exact submission/receipt binding, historical reviewer scope, complete and cross-chunk ranges, authorized conditional/HEAD responses, invalid ranges, corrupted object bytes, and permission/credential/reassignment/delegation expiry during pending storage reads. Existing document-download and binary-transport regressions passed after sharing HTTP negotiation. The review/correction commit also passed remote CI. No browser workflow, bank payment, or throughput measurement is claimed by this slice.

Expense payment workflow: full Gradle check and both application bootJar builds passed (381 tests total). Fifteen new HTTP/PostgreSQL tests and four pure policy tests cover immutable destinations/approved amounts, independent release and current beneficiary binding, competing reservations/release/cancellation/reconciliation, partial outcomes, explicit confirmed failures, unique bank references, receipt replay, permission/credential/recent-authentication changes while waiting, complete audit rollback, scoped employee progress without bank details, a 100-item export/settlement, twenty-attempt exhaustion, and database rejection of settlement without immutable result evidence. Initial focused failures identified incorrect HTTP validation expectations and a frozen test clock that did not distinguish bank confirmation from PostgreSQL-rounded release time; corrected fixtures and all subsequent runs passed. The receipt-download commit also passed remote CI. Bank submission remains manual; no real payment, bank-specific upload format, browser workflow, or throughput benchmark was exercised.

Bounded storage inventory reads: full Gradle check and both application bootJar builds passed (387 tests total). Six additional tests cover real Garage prefix isolation and finite pages, invalid provider ordering/scope, UTF-8 key order, XML byte limits before parsing, slow responses, cancellation, and input-stream ownership. All thirteen storage tests also passed in a focused run. The expense payment commit passed remote CI. This slice provides listing only; inventory jobs and orphan reconciliation remain subsequent work.

Document inventory reconciliation: full Gradle check and both application bootJar builds passed (410 tests total). Fourteen new HTTP/PostgreSQL tests, five domain policy tests, two job-progress tests, and two Spring Batch tests cover a late PUT after real cleanup acknowledgement, accepted/history retention, unknown and inconsistent objects, finite pages and explicit resume, three-recovery/eight-attempt limits, current permission/credential/lease changes, interruption, competing page readers/starts, complete audit rollback, company RLS/invoker views, and the 10,000-page ceiling. Focused runs also passed. Initial compilation exposed a PL/pgSQL expression and a public dependency visibility issue; both were corrected before the successful runs. Local documentation links and the preceding storage commit's remote CI passed. Tests use owned databases/storage; no live bucket cleanup, browser workflow, or performance benchmark was performed.

Business document references: full Gradle check and both application bootJar builds passed (414 tests total). Four new HTTP/PostgreSQL tests cover deduplicated receipt registration, immutable draft/cancellation history, company RLS, competing replay, complete audit-failure rollback, and a deferred foreign key rejecting a claim when registration is deliberately omitted. The focused draft/submission regression run also passed. Documentation links and the preceding inventory commit's remote CI passed. Content retirement and retention policies remain subsequent work.

Document retention administration: full Gradle check and both application bootJar builds passed (432 tests total). Fourteen new HTTP/PostgreSQL tests and four pure policy tests cover indefinite defaults, frozen policy deadlines, archive/restore/hold history, explicit cancellation of unfinished processing, continued content scope, independent sensitive grants, current credential/permission/step-up checks, competing uploads and state changes, cancellation, receipt replay, audit rollback, RLS, and database rejection of incomplete or inconsistent lifecycle evidence. One initial cancellation test used a fixture authentication timestamp later than its fixed clock; the fixture was corrected and the security suite and full run passed. Documentation links and the preceding evidence-reference commit's remote CI passed. Content retirement and physical deletion of accepted revisions remain subsequent work.

Accepted document retirement: full Gradle check and both application bootJar builds passed (450 tests total). Fourteen new HTTP/PostgreSQL tests and four pure policy tests cover complete 100-part retirement, guarded reference/hold/restore races, current permission/credential/recent-authentication checks, interruption, original receipt replay, complete audit and missing-cleanup rollback, publication removal, physical cleanup/quota conservation, historical evidence, pending downloads, RLS, and inventory recovery of a retired object that reappears. The focused retirement and retention/inventory/download regressions also passed. Local documentation links and the preceding retention-administration commit's remote CI passed. Storage/scanner tests use owned probes; no live object deletion, browser workflow, deployment, or performance benchmark was performed.

Mobile problem contract: full Gradle check and both application bootJar builds passed (459 tests total). Five new HTTP-contract unit tests and four HTTP/PostgreSQL tests cover stable codes and unlocalized parameters, field mapping without rejected values, filter/controller problem shape, cache/retry headers, authentication/CSRF/native failures, locale independence, concurrent correlation IDs, and cancellation/interruption. Existing request-size and OIDC admission tests now assert the same contract; focused authentication, native-session, SSO, and upload regressions passed. The initial focused compile found an old OIDC filter constructor in a test, which was updated before successful runs. Local documentation links and the preceding document-retirement commit's remote CI passed. Incremental synchronization and a mobile client integration are subsequent work.


Mobile synchronization: full Gradle check and both application bootJar builds passed in 16m25s (491 tests; no failures, errors, or skips). Thirty-two new tests cover cursor encryption/rotation and scope binding, bounded bootstrap/delta pagination, inserts behind an earlier page, native bearer command replay, account/company isolation, live revocation, shared readers, cancellation and late expiry, atomic post-commit publication, competing publishers, late business commits, rollback/retry, retention floors, pending-event preservation, tombstone transport, actual expense/leave transitions, and worker termination without queued event tasks. Focused runs also passed after correcting test cache-header/status assumptions and native fixture key/clock precision. Local documentation links (24 files), Compose configuration with fixture values, and the preceding mobile-problem commit’s remote CI passed. Only owned expense claims and leave requests are registered collections; no mobile UI, production deployment, throughput benchmark, or physical business-delete endpoint is claimed.

Leave attachment evidence: full Gradle check and both application bootJar builds passed in 17m29s (509 tests; no failures, errors, or skips). Fifteen new HTTP/PostgreSQL tests and three pure policy tests cover required/optional evidence, exact accepted personal revisions, cross-company and employment scope, replay after document/policy replacement, concurrent submission, audit/sync/reference rollback, missing evidence rejected at commit, RLS and immutable history, retirement protection after withdrawal, Range/ETag/HEAD responses, current permission/credential/approver changes during storage reads, cancellation, and content integrity. The focused leave/approval run passed all 57 HTTP tests. Local documentation links (24 files) and the preceding synchronization commit’s remote CI passed. Storage reads use owned test probes; no live download, browser workflow, deployment, or performance benchmark was performed.

Payroll configuration and calculation rules: full Gradle check and both application bootJar builds passed in 18m43s (540 tests; no failures, errors, or skips). Fourteen new pure policy tests cover the 125 reviewed TER brackets, official resident regular/final-period fixtures, refunds, subjective tax periods, domestic PPh 26, bounded gross-up/net convergence, interruption, malformed decimal input, and configured insurance bases/rounding. Seventeen new HTTP/PostgreSQL tests cover effective policy/compensation history, original receipt replay, independent salary administration, no implicit administrator or employee salary access, competing creates/edits, missing-revision and audit rollback, RLS/history immutability, permission/credential/recent-authentication changes while waiting, and cancellation after an insert. The focused run also passed after adding the data module's missing Jackson dependency. Local documentation links (25 files) and the preceding leave-attachment commit's remote CI passed. Insurance fixtures use an explicitly fictional pension cap; no 2026 cap, payroll period run, browser workflow, deployment, or performance benchmark is claimed.

Overtime workflow: full Gradle check and server/worker bootJar passed (567 tests, no failures, errors, or skips). Five new pure policy tests and 22 HTTP/PostgreSQL tests cover bounded planned/actual intervals, overnight dates, immutable schedule evidence, independent staged approval, live/delegated access, withdrawal, monthly capacity, conflicting commands, exact replay, closing exclusion, owned mobile synchronization, rollback, RLS, and interruption. The first full run exposed a missing approval configuration import in the explicitly composed worker application; that import was corrected and the complete check rerun passed. No device, payroll payment, or performance validation is claimed by this slice.

Leave entitlement accounting: full Gradle check and server/worker bootJar passed (594 tests, no failures, errors, or skips). Twenty-one new HTTP/PostgreSQL tests and five pure policy tests cover monthly/annual eligibility, independent authorship, frozen grant evidence, frequency changes, carryover/expiry, closed-year guards, owned balance synchronization, live access, concurrent commands, receipt replay, incomplete ledger effects, audit rollback, and interruption. A database migration test upgrades populated V42 balances to V43 without changing historical ledger IDs, then verifies projection versions, runtime writes, isolation, and rejection of direct balance edits. No company-wide scheduling, payroll cutoff, device, or performance validation is claimed by this slice.

Company leave entitlement batches: full Gradle check and server/worker bootJar passed in 22m33s (615 tests, no failures, errors, or skips). Eighteen new HTTP/PostgreSQL tests and three Spring Batch tests cover bounded target snapshots, partial outcomes, per-employee policy failures, cancellation and explicit resume, expired lease fencing, concurrent steps/starts, original and live permissions, credential revocation while waiting, complete checkpoint/evidence/audit rollback, immutable company-scoped history, and exhausted process attempts. Focused tests also passed after correcting explicit employee validation order and test fixture assumptions. Local documentation links passed. Automatic scheduling, payroll cutoff, device integration, and performance measurements remain subsequent work.

Payroll earning rules: all 25 payroll domain tests and 20 payroll/compensation HTTP tests passed, along with architecture/format checks and server/worker bootJar. Eleven new pure tests cover standard overtime bands, the 75% wage floor, exact-minute calculation, final rounding, bounded proration, calendar-anniversary THR service, termination eligibility, higher commitments, invalid values, and interruption. Three new HTTP tests cover optional pay-basis history, legacy request replay, field validation, and independent salary access. Local documentation links passed. No full-workspace rerun, payroll run, payment, deployment, or performance benchmark was performed for this additive configuration/policy slice.

Payroll opening history: 10 new HTTP/PostgreSQL tests, all 28 payroll domain tests, and 12 database tests passed (50 tests, no failures, errors, or skips). Architecture/format checks and both application bootJar builds passed. Coverage includes explicit zero/imported history, independent historical authors, beneficiary exclusion, bounded values/pages, concurrent save/verify, immutable RLS evidence, omitted revisions, forged verification terms, complete audit/receipt rollback, live revocation, expired authentication, original permission intersection, interruption, and thread reuse. Local documentation links passed. No full-workspace rerun, payroll calculation run, device integration, or performance measurement was performed for this slice.

Payroll period and input preparation: 18 new HTTP/PostgreSQL tests, all 33 payroll domain tests, and 12 database tests passed (63 tests, no failures, errors, or skips). Architecture/format checks and both application bootJar builds passed. Tests cover a complete 5,000-participant roster with bounded pages, separate earning/payment months, repeated draft limits, exact workforce references, lightweight history projections, independent review, concurrent create/edit/verify, revoked authority, stale credentials/employment, immutable company scope, omitted evidence, forged verification, complete audit rollback, and interruption/thread reuse. Local documentation links passed; the preceding opening-history commit also passed full remote CI. This is preparation validation, not a payroll throughput/heap benchmark, calculated run, or payment test.

Monthly payroll composition: all 50 payroll domain tests and seven period HTTP/PostgreSQL tests passed, along with architecture/format checks and both application bootJar builds. Seventeen new domain tests cover explicit attendance/leave resolution, half-day and scheduled proration, employment start/exit, grouped overtime, THR, insurance tax classification, nonresident/net/gross-up treatment, the assembled PMK 168 Tuan B refund, history/date mismatches, negative take-home prevention, bounds, and interruption. The draft response now labels its date-derived field `plannedPaymentMonth`; calculation requires an independently established income-liability date. Local documentation links passed. No durable run, payment, deployment, or payroll performance measurement was performed for this composition slice.


Durable payroll calculation: full Gradle check and both application bootJar builds passed in 28m49s (711 tests; no failures, errors, or skips). Twenty-four new HTTP/PostgreSQL tests and three Spring Batch tests cover immutable input/result snapshots, exact decimal outcomes, partial employee failures, bounded recovery, competing starts/steps, original receipt replay, source/leave cutoffs, current permissions and credentials, expired leases, retained checkpoints, interruption/thread reuse, RLS, missing effects, and complete audit rollback. The source query captures 5,000 lightweight targets within the existing request timeout; this test does not calculate 5,000 employees or establish a throughput target. Typed overtime/THR snapshot round trips and actual leave cancellation/recovery flows passed. Three HTTP diagnostic tests cover bounded cause chains, wrapped cancellation/interruption, and sanitized context; a batch failure test verifies that SQL and employee values are not emitted by framework logging. Initial focused runs exposed nullable primitive mapping, stale incremental test bytecode, an inefficient roster join, and missing financial grants in a worker fixture; each was corrected before the final successful run. Local documentation links passed, and the preceding payroll opening/preparation/composition commits passed remote CI. Approval/finalization, payment, device integration, deployment, and payroll throughput/heap measurements remain subsequent work.


Frozen approval makers: targeted approval, expense, leave-request, and overtime regressions passed across focused runs (117 HTTP/PostgreSQL tests and 73 domain/database tests). Three new pure tests and the expanded expense security workflow cover multiple makers, direct/delegated authority, reassignment, immutable stored exclusions, and the actionable inbox. The first HTTP run had one incorrect status assertion: domain validation uses 422, not 400. After correction, all six expense review security tests passed in the focused rerun. Architecture/format checks and both application bootJar builds passed. Existing expense maker evidence is backfilled without changing original decisions or assignment history. Local documentation links passed. No full-workspace rerun, browser workflow, deployment, or performance measurement was performed for this additive approval boundary.


Payroll aggregate review: 113 targeted tests passed in 4m28s (37 HTTP/PostgreSQL, three worker, 61 domain, and 12 database tests; no failures, errors, or skips). Thirteen new HTTP tests cover exact retained totals, independent staged review including an eligible payroll beneficiary, maker exclusion after another submitter, blocked repair, rejection, withdrawal before abandonment, finite resubmission/history, original receipt replay, concurrent commands, current credentials/permissions/delegation, company scope, interrupted transactions, missing history, forged transitions, and complete audit rollback. Existing calculation and worker regressions passed. Architecture/format checks and both application bootJar builds passed. Local documentation links passed; the durable-calculation commit also passed full remote CI. No finalization, bank payment, browser workflow, deployment, or payroll performance measurement is claimed by this slice.


Payroll finalization: 129 targeted tests passed in 6m39s (53 HTTP/PostgreSQL, three worker, 61 domain, and 12 database tests; no failures or errors). Sixteen new HTTP tests cover exact approved versions, retained company labels, cancellation/retry, competing submission/publication, live access and recent authentication, stale employment, immutable assessments, RLS, missing effects, complete audit rollback, and lease expiry/interruption before and after acquiring publication references. Architecture/format checks and both application bootJar builds passed. Publication of 5,000 preseeded retained results completed in 7,297 ms under the actual worker's 10-second statement, two-second lock, and 30-second transaction budgets. This measures reference publication, not calculation throughput or heap consumption. The worker-budget probe exposed repeated whole-run joins; bounded indexed lookups and removal of an unsuitable result index corrected them without extending timeouts. Local documentation links passed. The preceding review commit passed full remote CI. No payment, browser workflow, deployment, or complete-workspace local rerun is claimed by this slice.


Live job access: 54 targeted tests passed in 3m03s (24 HTTP/PostgreSQL, 28 worker, and two job domain tests), with architecture/format checks and both bootJar builds. Four additional HTTP tests exercise permission removal and new grants during pending reads/cancellation, company and credential revocation, owner cleanup, and cancellation replay after membership revocation. Payroll publication and all existing worker regressions passed. Local documentation links passed. No full-workspace local rerun or deployment was performed.


Finalized payroll tax continuity: 141 targeted tests passed in 6m06s (59 HTTP/PostgreSQL, three worker, 67 domain, and 12 database tests); an additional cross-month duplicate-THR HTTP test passed in 45s. Six new pure tests and seven HTTP tests cover September–December carry-forward, the sourced PMK 168 Tuan B refund, assessed gross-up allowances, retained previous-employer credits, gaps/out-of-order months, new-year opening requirements, terminal/rehire/registration review gates, company scope, omitted predecessor references, forged history arithmetic, and complete checkpoint rollback. An initially incorrect JSON-operator grouping in the database invariant was corrected before the passing regression run; temporary diagnostics were removed. Architecture/format checks and both bootJar builds passed. Publication of 5,000 retained references still passed the actual worker budget (7,336 ms in this run); no 5,000-employee calculation or heap benchmark is claimed. Local documentation links passed. The preceding finalization and live-job-access code passed full remote CI. Payslip delivery, payments, browser workflows, and deployment remain subsequent work.


Published payslip access and sync: 113 targeted tests passed across focused runs (37 HTTP/PostgreSQL, three worker, 61 payroll/sync domain, and 12 database tests). Seven new HTTP tests cover unpublished/late-commit invisibility, frozen labels, exact decimal amounts, lightweight newest-first pages, self/finance/company boundaries, live permission/membership/credential revocation, original permission intersection, owned bootstrap/deltas, and scope reset. Existing finalization tests additionally verify sync rollback after late failures/interruption and 5,000 captured invalidations. Two test-fixture assumptions were corrected: decimal strings are compared numerically, and profile renaming uses its versioned endpoint. The affected three HTTP tests then passed in 49s. Architecture/format checks and both bootJar builds passed. Publication of 5,000 retained results with sync metadata took 12,109 ms under the worker's unchanged 10-second per-statement, two-second lock, and 30-second transaction budgets. This is reference publication, not 5,000 calculations or a heap benchmark. Local documentation links passed. The preceding tax-continuity commit passed full remote CI. PDF delivery, bank payments, browser validation, and deployment remain subsequent work.

Bounded payroll publication: the preceding payslip CI run exposed a statement timeout on the 5,000-reference fixture. Query plans showed repeated company/run scans under RLS before statistics refresh. Bounded materialized inputs and function-local set joins preserve the original access checks and publication invariants. The 106 relevant finalization, tax-continuity, payslip, worker, domain, and database tests passed within the focused regression run; architecture/format and both bootJar builds also passed. The uninstrumented 5,000-reference publication completed in 880 ms, with its original SQL/lock/transaction budgets and a new same-connection assertion that planner settings do not escape the publication function. Temporary query diagnostics were removed. This measures retained reference publication, not 5,000 payroll calculations, production latency, or heap consumption.


Payroll payment processing: 150 unique targeted tests passed across focused runs (62 HTTP/PostgreSQL, three worker, 73 payroll/expense/sync domain, and 12 database tests). Sixteen new payment tests cover immutable amounts/destinations, independent release, partial/unknown outcomes, confirmed-failure retries, twenty-attempt bounds, receipt replay, shared bank-reference conflicts across salary/reimbursement workflows in either order and concurrently, RLS, live access, interruption, and complete evidence/audit/progress/sync rollback. An additional migration test upgrades two companies with existing settled reimbursements and preserves all prior evidence and sync rows. Architecture/format and both bootJar builds passed. The final 5,000-reference publication completed in 914 ms; listing 200 payable references from those assessments took 78 ms. These are bounded fixture observations, not payment execution, production latency, payroll calculation throughput, or a heap benchmark. The initial run corrected a sync-collection ordering assertion. No direct bank integration or deployment was performed.

Dashboard foundation: import/architecture checks, Biome, strict TypeScript, 22 transport/composition/controller tests, five Chromium fixture tests, and a production build passed. The real API/PostgreSQL browser test passed password errors, MFA enrollment, one-use recovery codes, current-company selection, English/Indonesian messages, and completed server logout. Browser fixtures additionally cover pending company-switch cancellation, late results, password cleanup, keyboard navigation, and the mobile shell. Local screenshots of sign-in, light/dark overview, and a narrow viewport were inspected. The production entry is 638.96 kB minified / 184.02 kB gzip and currently triggers Vite's 500 kB chunk warning; this is a bundle observation, not a rendering or memory benchmark. Initial browser checks corrected an uncontrolled Fluent input and test assumptions about company creation, correlation text, and asynchronous logout. No live SSO provider, delivery service, business dashboard workflow, or deployment was exercised.

Expense payment read guards: 47 targeted tests passed in 2m45s (23 HTTP/PostgreSQL, 12 expense domain, and 12 database tests), with architecture/format checks and both bootJar builds. Four new HTTP tests cover every finance read after a pending permission revocation, current membership/credentials/recent authentication, owned progress without bank details, and concurrent shared readers. The regression probe first demonstrated that the previous read path did not acquire its live-account guard. Existing preparation, reconciliation, bank-reference, and migration regressions passed. The preceding dashboard commit's browser CI job and payroll-publication hotfix's full CI passed; no device, deployment, or performance benchmark was performed for these guards.

Mobile collection selection: 52 targeted tests passed across focused runs (27 HTTP/PostgreSQL, 13 sync domain/data, and 12 database tests), with architecture/format checks and both bootJar builds. Four new HTTP tests and three pure policy tests cover explicit subsets, bootstrap/delta scope binding, compatibility with default cursors, permission intersection and pending revocation, rejected empty/unknown sets, and actual leave-balance exclusion from snapshots, deltas, and pending-publication status. The first run corrected two test assertions that called Jackson's node transformation instead of iterating array elements; all affected HTTP tests then passed. Existing native-session/offline-command, retention/publication, payroll, and leave sync regressions passed. No mobile application or deployment was exercised.

Company/profile access guards: 62 targeted tests passed in 2m22s (30 HTTP/PostgreSQL, 20 identity/people domain, and 12 database tests), with architecture/format checks and both bootJar builds. Four new HTTP tests and two pure policy tests cover company creation/settings, organization-unit changes, profile reads/history/edits, pending credential/permission/membership revocation, current authorization before original receipt replay, failed-key recovery, and exclusion of newly granted platform permissions. Existing company creation races, profile optimistic history, account binding, employment, and cross-company transfer regressions passed. No deployment or performance benchmark was performed.

The subsequent lock-order review moved transfer structure acquisition before company guards. An additional controlled HTTP race lets an organization edit complete while a transfer waits for that structure, proving that the two paths do not hold reversed company/structure guards. Transfer and company/profile regressions, architecture/format, and both bootJar builds passed again in 1m07s; this brings the slice to 63 unique passing tests. No latency or throughput claim is inferred from that ordering test.

Employment access guards: 53 targeted tests passed across focused runs (33 HTTP/PostgreSQL, eight people domain, and 12 database tests), with architecture/format checks and both bootJar builds. Four additional HTTP tests cover pending permission loss across create/revise/cancel and their receipts, directory narrowing to self access, history/transfer/detail scope, and linked-account deactivation. The initial run corrected two fixture requests that omitted the required effective date; all affected tests passed in the focused rerun. Existing revisions, profiles, bindings, reporting visibility, and transfer regressions passed. No deployment or performance benchmark was performed.

Lifecycle access and snapshots: 50 targeted tests passed in 2m16s (30 HTTP/PostgreSQL, eight people domain, and 12 database tests), with architecture/format checks and both bootJar builds. Eight new HTTP tests cover all checklist management commands and read paths under pending revocation, receipt access, narrowed assignee authority, exclusion of new grants, target-account deactivation, expiration while waiting for a case, and paginated case/task consistency during a concurrent write. Existing checklist completion/reopening, offboarding, transfer, rollback, and employment regressions passed. No deployment or throughput/heap benchmark was performed.

OpenAPI transport contract: 23 HTTP/PostgreSQL tests passed across focused runs, with architecture/format checks and both bootJar builds. Five new tests validate OpenAPI 3.1, local schema references, exact coverage of 227 operations across 205 paths, unique IDs, hidden framework parameters, auth/CSRF/bearer alternatives, idempotency and sync metadata, nullable/decimal/month values, binary transfer semantics, current authenticated documentation access, and identical descriptions across language headers. Existing native-session, problem, request-boundary, and identity tests passed, including documentation disabled by default. The generated artifact contains 380 schemas. Initial runs corrected a Jackson test API mismatch, registered manually referenced schemas after springdoc's pruning step, and replaced an estimated route count with an exact mapping comparison. Local documentation links, actionlint, and Compose configuration with an ephemeral validation key passed. The preceding company/employment code passed full remote backend/dashboard CI. No generated client, deployment, or performance benchmark was performed.


Employee import access: 39 targeted tests passed in 1m52s (17 HTTP/PostgreSQL, two worker, eight people domain, and 12 database tests), with architecture/format checks and both bootJar builds. Five new HTTP tests exercise pending permission/credential/membership/company revocation across import commands and reads, receipt replay, failed-key recovery, expiration while waiting for queue capacity, cancellation rollback, and consistent header/outcome reads during worker mutation. Existing preview/apply/recovery and employment regressions passed. No deployment or performance benchmark was performed.


Calendar access and snapshots: 86 targeted tests passed in 4m09s (47 HTTP/PostgreSQL, five worker, 22 workforce domain, and 12 database tests), with architecture/format checks and both bootJar builds. Five new HTTP tests cover schedule/holiday/roster commands and receipt replay under pending revocation, current calendar scopes, exclusion of newly granted permissions, shared readers with writer exclusion, and employment eligibility changes during a pending assignment. Existing overnight/calendar, attendance, overtime, closing, worker shutdown, and people regressions passed. An initial invocation used a worker test filter that matched no suite; the corrected selection passed. Local documentation links passed. The preceding lifecycle/OpenAPI code passed full remote backend/dashboard CI. No deployment or performance benchmark was performed.


Attendance access and decision timing: 77 unique targeted tests passed across focused runs (37 HTTP/PostgreSQL, five worker, 23 workforce domain, and 12 database tests), with architecture/format checks and both bootJar builds. Nine new HTTP tests and one pure policy test cover pending permission/credential/membership/company changes, original receipt access, current manager/employment scope, retained/current self-review exclusions, expired online proofs, the 31-day offline boundary, capture-window replay, and shared coherent event/correction reads. Existing native-session/offline-command, calendar, closing, and worker lifecycle regressions passed. The first run corrected two manager fixtures that omitted their explicit team-verification grant; all nine new HTTP tests then passed in 50s. No device, deployment, or performance benchmark was performed.


Work-period access: 77 targeted tests passed in 2m48s (37 HTTP/PostgreSQL, five worker, 23 workforce domain, and 12 database tests), with architecture/format checks and both bootJar builds. Five new HTTP tests cover pending closing/recovery revocation, original receipts, current scope for published snapshots, exclusion of newly granted read access, and worker revocation after acquiring its job/period. A worker cannot adopt a newer credential version than the job's origin. Existing competing finalization, exhausted-job recovery, late lease rollback, attendance, overtime, and worker lifecycle regressions passed. Local documentation links passed. No deployment or performance benchmark was performed.

Company identity access: 77 targeted tests passed in 4m27s (51 HTTP/PostgreSQL, 14 identity domain, and 12 database tests), with architecture/format checks and both bootJar builds. Six new HTTP tests and two policy tests cover separate company/platform scopes, pending grant/revocation, active targets, role and membership receipts, expired MFA, and coherent member/source reads with shared readers and excluded writers. Existing identity, native sessions, approval races, company/profile, and employment-transfer regressions passed. An initial run exposed stale incremental test bytecode after the repository interface changed; recompilation with Kotlin incremental compilation disabled resolved the linkage failure. Local documentation links passed. No deployment or performance benchmark was performed.

Global identity access: 68 unique targeted tests passed across focused runs (41 HTTP/PostgreSQL, 15 identity domain, and 12 database tests), with architecture/format checks and both bootJar builds. Eight new HTTP tests and one policy test cover authentication/MFA expiry during pending commands and receipt replay, current global permission/credential revocation, own metadata, crossed OIDC administration, self-binding session invalidation, failed-key recovery, and coherent directory versions/grants during a concurrent update. Existing OIDC validation, invitations/recovery, account administration, identity, and native-session regressions passed. Two initial fixture failures used a nonexistent company-list route; the tests now use the existing current-account contract, and the unused standalone company-list use case was removed. The focused recheck passed in 2m14s. Local documentation links passed, and the preceding workforce changes passed full remote backend/dashboard CI. No deployment or performance benchmark was performed.

Payslip PDF: 32 unique targeted tests passed across focused runs (17 HTTP/PostgreSQL, ten core HTTP, and five rendering tests), with architecture/format checks and both bootJar builds. Coverage includes native bearer/owner/finance scope, pending revocation before conditional responses, Range/ETag/HEAD negotiation, frozen issuer/employee values, Unicode font embedding, deterministic localized amounts, finite pagination/output/time, cancellation, and capacity release after parsing failure. The final transport check passed in 1m39s. English and Indonesian sample PDFs were rendered and visually inspected; the one-page fixtures were 10,852 and 11,069 bytes. OpenAPI now validates 206 paths and 228 operations, including binary PDF responses. Local documentation links passed. Broader scripts and operational payroll use retain their documented limits; no deployment or throughput/heap benchmark was performed.

Durable job scheduling: architecture/format checks, both application bootJar tasks, and 45 targeted tests passed (40 HTTP/PostgreSQL, three worker, two domain). New cases cover future versus due eligibility, unchanged immediate-job semantics, additive schedule/availability metadata, immutable schedules, cancellation wakeup, preserved cleanup backoff after duplicate cancellation, and complete rollback of a failed cancellation. Existing upload/import cancellation, live job access, OpenAPI, and worker tests passed. The initial retry fixture exceeded the existing 300-second delay limit; correcting it and rerunning the queue suite passed. No automatic business schedule, deployment, or performance measurement is claimed by this foundation.

Announcement authoring: architecture/format checks, both bootJar tasks, and 28 targeted tests passed (12 HTTP/PostgreSQL, 12 database, four domain). The new tests cover immutable draft/group revisions, normalized receipt replay, bounded summaries, empty/duplicate/wrong-kind/foreign references, inactive audiences, competing edits, atomic journal-failure rollback, live revocation after pending guards, company RLS, and database reference enforcement. jOOQ nullable count projections and a branch fixture missing its required timezone were corrected before the final checks. Generated OpenAPI covers 213 paths and 237 operations. Local documentation links passed. Publication, recipient inbox, push delivery, browser screens, and performance benchmarks are not claimed by this authoring slice.


Announcement publication and inbox: architecture/format checks, both bootJar tasks, and 71 distinct targeted tests passed (45 HTTP/PostgreSQL, 12 database, seven domain, seven worker). Coverage includes frozen publication, current audience resolution, deduplicated accounts, group changes, inactive/missing access, future scheduling and cancellation wakeup, explicit recovery, stale leases, competing commands, read/acknowledgement replay, archival withdrawal, native bearer access, live revocation after pending guards, complete rollback after late lease/audit failures, company RLS, and database reference/identity/completeness constraints. Worker integration verifies real Spring Batch publication and cleanup after credential revocation; existing shutdown and non-progress tests also passed. Initial test-fixture expectations/imports and the worker's missing communications configuration import were corrected before the final checks. OpenAPI covers 220 paths and 244 operations. Local documentation links passed.

The bounded publication fixtures use the existing worker query/lock limits (10/2 seconds) and 30-second transaction budget. A 5,000-recipient company publication took 2.15 seconds in the final local test. The maximal overlapping audience of 32 groups with 5,000 members each produced exactly 5,000 inbox items in 7.60 seconds. These tests force PostgreSQL generic plans. Recipient-reference validation was reduced from about 7.2 seconds to 39–48 ms by using scoped existence checks; group membership is expanded as a set instead of testing a large JSON array for every employee. The 5,001-recipient case fails before publication, and archival of all 5,000 items passed. These are isolated fixture measurements, not deployment throughput, interactive latency, or heap benchmarks. No external notifications were sent. The earlier authoring commit also passed the full backend/dashboard GitHub CI.


Account-owned inbox synchronization: architecture/format checks, both bootJar tasks, and 72 distinct targeted tests passed (nine sync-domain, five cursor/data, 12 database, 42 HTTP/PostgreSQL, four worker). New coverage verifies atomic pending references, read/acknowledgement versions, replayed pages, archival tombstones, unchanged default collection selection/cursors, explicit inbox scope changes, retained original recipients after group edits, revocation/regrant, company/account isolation, rejected employment-based inbox access, and preserved database ownership rules for older collections. Late publication failure rolls back inbox and sync together. Existing collection, publication/retention, encryption, worker, and OpenAPI tests passed. Two new assertions initially called Jackson's whole-node mapper instead of iterating an array; the corrected fixtures passed on rerun. Publication including sync capture took 2.89 seconds for 5,000 company recipients and 7.58 seconds for the maximal overlapping-group fixture on the local test environment; these remain isolated bounded checks, not throughput or heap benchmarks. Local documentation links passed. The protocol is ready for client integration; no mobile application/device validation is claimed.


Announcement audience preview: architecture/format checks, both bootJar tasks, and 31 targeted tests passed (seven domain, 22 HTTP/PostgreSQL, two worker). New tests cover read-only counts, empty audiences, current group revisions, company-local eligibility dates, live read grants, effective branch/department assignments, stale versions, frozen/archived content, company scope, management access, and credential revocation during a pending guard. The capacity fixtures also verify explicit refusal above 5,000 recipients. Deduplicating selected group membership before the employment join reduced the maximal 32-by-5,000 preview to 184 ms in the local generic-plan fixture; publication including inbox sync took 2.37 seconds for that audience and 2.28 seconds for a company audience. These isolated measurements do not establish deployment p95 or throughput. OpenAPI covers 221 paths and 245 operations, and local documentation links passed.


Native push registration: architecture/format checks, both application bootJar tasks, and 53 targeted tests passed (16 identity-domain, six identity-data, 19 HTTP/PostgreSQL, 12 database). New coverage exercises bearer-only transport, encrypted and version-bound tokens, sanitized metadata, concurrent create/update/replay, explicit disable/reactivation, no implicit cross-session token transfer, late journal rollback, revocation during a pending account guard, RLS, immutable ownership, and lifetime/version constraints. Existing native refresh/reuse/expiry and cryptographic tests passed. OpenAPI covers 223 paths and 248 operations; 75 local documentation links passed. No provider calls, mobile device validation, deployment, or performance measurement is claimed by registration.


FCM transport foundation: architecture/format checks and ten tests passed against owned loopback HTTP fixtures. Tests cover data-only Android/APNs envelopes, finite payload lifetime, explicit token invalidation versus request/configuration failures, rate-limit metadata, disabled SDK/send retries, concurrent OAuth refresh, redirect refusal, chunked response limits, credential configuration bounds, cancellation, and HTTP client shutdown. A regression reproduced the JDK request timeout ending at response headers; explicit future ownership now bounds stalled response bodies and cancels them. Initial compilation required an explicit logging dependency and the SDK's non-null delegation setting. No external FCM/OAuth endpoint, live credentials, device, or deployment was used. This commit adds the transport module without enabling inbox dispatch.

Inbox push dispatch: architecture/format checks, both application bootJar builds, and 85 distinct targeted tests passed across focused runs (eight communications-domain, 12 push-data, 36 HTTP/PostgreSQL, 17 worker, and 12 database tests). Coverage includes atomic inbox capture, bounded global claims, persisted device cursors, unchanged event IDs, retry deadlines, live credential and archival checks, registration replacement during send, atomic token retirement/checkpoints, terminal audit rollback, stale maintenance selections, expiry/retention while delivery is disabled, and worker interruption/shutdown. Provider I/O is verified to run without an open database transaction. The final maintenance recheck passed in 1m36s after recompiling stale incremental Kotlin test delegation bytecode; a query overload error was corrected before it. The optional Compose override and 80 local documentation links passed. The preceding registration/transport code passed full remote backend/dashboard CI.

The final local generic-plan fixtures published 5,000 company recipients, including inbox sync and push capture, in 2.53 seconds. The maximal overlapping 32-by-5,000 group audience preview took 206 ms and publication took 2.97 seconds. These are isolated bounded fixtures, not deployment throughput or heap benchmarks. Tests used owned HTTP fixtures and a fake push provider; no live FCM credentials, external notifications, device, or deployment was used.

Company client policy: architecture/format checks, both application bootJar builds, and 66 distinct targeted backend tests passed across focused runs (four administration-domain, 11 core HTTP, 37 HTTP/PostgreSQL, 12 database, and two worker tests). New coverage verifies effective revisions and supersession, maintenance boundaries, transport-specific build metadata, current permission/credential/company access after pending guards, concurrent edits, receipt replay, journal rollback, shared read consistency, immutable history/RLS, sync selection, and offline command recovery without consuming failed operation keys. Two initial fixture mistakes used a nonexistent account route and omitted the employee directory's required effective date; corrected requests passed. A fractional-second retry boundary was also checked. OpenAPI validates 226 paths and 252 operations.

The dashboard sends its compiled web build number and translates availability failures in English/Indonesian. Its architecture, formatting, type checks, 22 unit/controller tests, production build, five browser tests, and one browser integration test against the real API/PostgreSQL passed. Local documentation links passed. The preceding inbox push commit passed full remote backend/dashboard CI. No mobile-device, live deployment, or performance benchmark was performed for client policy.

Company headcount: architecture/format checks, both application bootJar builds, and 22 tests passed (two reporting-domain, three reporting-data, and 17 HTTP/PostgreSQL). New coverage verifies effective history, cancelled schedules, inclusive contract end dates, suspended employment, distinct-person totals, RLS, report/source permissions, availability flags, pending access and credential revocation, concurrent employment commits, sanitized failures, and cancellation cleanup. The initial run corrected a fixture that required an exact cache-header string instead of checking its `no-store` directive. The final read uses one SQL snapshot without the people mutation guard; the concurrent test retains the old coherent aggregate while a subsequent read sees the committed revision. The final check passed in 1m47s. OpenAPI validates 227 paths and 253 operations; 87 local documentation links passed. No browser report screen, device, deployment, or performance benchmark is claimed by this backend slice.

Headcount dashboard: architecture, formatting, TypeScript, 31 unit/controller tests, production build, nine browser tests, and one real API/PostgreSQL browser integration test passed. New tests cover input/scope/definition validation, reconciled counts, superseded results, disposal/remount, permission loss, URL history, invalid-link recovery, company switching, logout, locale/theme changes without refetching, and responsive layouts. Browser testing reproduced premature date-input reset and an uncontrolled Fluent input retaining the wrong date after Back; an applied filter form with the controlled React Hook Form adapter fixes both. Light/dark desktop and 390-pixel mobile screenshots were visually inspected, including native dark date controls. The final browser run passed in 19.8s; the real API fixture passed in 41.2s before the final color-scheme-only adjustment. The report route is a separate 6.02 kB minified chunk (2.55 kB gzip). 89 local documentation links passed. No device, deployment, or throughput/heap benchmark was performed.

Company audit metadata search: architecture/format checks, both application bootJar builds, and 52 targeted tests passed (seven administration-domain, three administration-data, 15 database, and 27 HTTP/PostgreSQL). New coverage includes timestamp ties, live page snapshots, exact filters, foreign/global/missing cursors, range and pagination bounds, metadata-only disclosure, access and MFA expiry after pending guards, cancellation cleanup, and sanitized failure recovery. Audit remains independently authorized during company maintenance and client-version gates. The final check passed in 3m06s; local documentation links passed. This slice adds no audit payload disclosure, browser explorer, device validation, deployment, or performance measurement.

Audit dashboard: architecture/Biome/TypeScript checks, 42 unit/controller tests, a production build, 18 browser tests, and one real API/PostgreSQL browser integration test passed. Coverage includes bounded UTC pagination, microsecond ordering, exact filters, invalid-link recovery, pending company changes, stale results, permission loss, disposal/remount, keyboard focus, and metadata disclosure. Browser inspection identified and corrected missing focus restoration and narrow table columns. Fluent's compatible Tabster version removes the observed Keyborg lifecycle diagnostic. The final browser suite passed in 48.2s; integration passed in 59.0s. Desktop light/dark and 390-pixel mobile screenshots were inspected. The audit route is loaded on demand; the existing main-bundle size warning remains. No deployment, device, throughput, or heap benchmark is claimed.

Company client settings snapshot: architecture/format checks, both application bootJar tasks, and 32 HTTP/PostgreSQL tests passed. Five new tests cover configured versus effective revisions, scheduled activation, authorization and MFA expiry after a pending policy guard, concurrent configuration changes, and cancellation cleanup. Existing audit, client admission/sync, and OpenAPI contracts passed. The settings response retains `latest` and adds `effective`; the check completed in 2m24s. No browser settings editor, deployment, or performance benchmark is claimed by this backend change.

Client policy dashboard review: architecture/Biome/TypeScript checks, 52 unit/controller tests, production build, 23 browser tests, and one real API/PostgreSQL browser integration test passed. Coverage includes scheduled/default/effective revisions, coherent bounded response mapping, cancellation between snapshot and history requests, stale results, disposal/remount, permission loss, URL history, localization, and responsive layouts. The first browser run exposed a test assumption that counted Strict Mode's aborted initial request as an extra refresh; the trace confirmed cancellation, and the assertion now measures requests added by the explicit action. The final browser suite passed in 57.8s. Light desktop and dark mobile screenshots were inspected. The review route is a 5.14 kB minified chunk (2.05 kB gzip); the existing main-bundle size warning remains. This slice provides review, not a configuration editor, and makes no device, deployment, or performance claim.

Job interactive access hardening: architecture/format checks, both application bootJar builds, and 51 targeted tests passed (17 identity-domain, two jobs-domain, and 32 HTTP/PostgreSQL). New coverage verifies live MFA expiry after pending guards and original/current action hints for both revoked and newly granted job permissions. Cancellation stays replayable after fresh assurance, without consuming a rejected attempt or producing duplicate audit entries. Existing queue leasing, inventory security/recovery, and OpenAPI contracts passed. The check completed in 2m34s. No job browser workflow, deployment, or performance benchmark is claimed by this backend change.

Job dashboard: architecture/Biome/TypeScript checks, 66 unit/controller tests, production build, 30 browser tests, and one real API/PostgreSQL browser integration test passed. Seven focused job browser tests also passed after refining the terminal cancellation message. Coverage includes bounded cursor progress, safe immutable metadata, own/company scope, observed versions, duplicate clicks, lost responses, stale conflicts, permission loss, late reads/commands, disposal/remount, row observations, and Fluent dialog focus. The integration test creates a scheduled announcement job through business endpoints, requests cancellation from the dashboard, and verifies its persisted queued/cancellation state without running a worker or delivering messages. Integration passed in 50.5s. Light desktop and dark mobile screenshots were inspected; the job route loads on demand and the existing main-bundle warning remains. 100 local documentation targets passed. No deployment, device, or throughput/heap benchmark is claimed.

Employee dashboard: architecture/Biome/TypeScript checks, 81 unit/controller tests, production build, and 40 browser tests passed. A real API/PostgreSQL browser test also passed, loading an employee directory, effective employment detail, and initial revision evidence through the production client composition. Coverage includes public projection boundaries, calendar dates, scoped IDs, bounded and non-stalling pagination, revision zero/cancellations, delayed results, disposal/remount, enclosing-scope revocation, controlled URL filters, company-pinned direct links, localization, and Fluent tab/drawer focus. Browser validation took 1.8m; integration took 48.8s. Desktop and mobile screenshots were inspected, and the responsive header was adjusted to keep resource names and actions readable. 102 local documentation targets passed. The existing main-bundle warning remains (679.82 kB minified, 196.86 kB gzip). This slice implements read workflows; it does not claim profile editing, organization labels, device/deployment validation, or runtime performance measurements.

Organization directory/detail API: architecture and format checks, both application bootJar builds, and 51 targeted tests passed (two domain policy tests and 49 HTTP/PostgreSQL tests). New coverage includes literal name/code search, kind/status filters, bounded tuple pagination, scoped parent details, original/live authority, credential and membership revocation while waiting, natural MFA expiry before reads/writes/receipt replay, a concurrent parent edit, and cancellation releasing the structure guard. Existing company, people, transfer, client-policy, OpenAPI, and mobile problem suites also passed. Directory pages remain live reads; this is not a synchronization feed or a multi-page snapshot. No browser workflow or performance result is claimed by this backend slice.

Organization dashboard: architecture/Biome/strict TypeScript checks, 90 unit/controller tests in 22 files, and a production build passed. All 40 existing browser cases passed; seven organization cases passed after correcting a position fixture that incorrectly referenced itself as its parent. They cover deferred requests, bounded pages, URL filters and Back/Forward, direct-link permissions, failures/retry, pending company changes, logout, keyboard activation, localization, and responsive themes. One real API/PostgreSQL browser test passed (42.6 seconds), including literal organization search, coherent parent details, and explicit parent navigation alongside existing identity/people/report/audit/policy/job workflows. The fixture sends unit IDs only in the URL, as required by the strict request contract. Desktop and mobile screenshots were inspected. The production entry chunk remains above Vite's 500 kB advisory (686.47 kB minified, 198.33 kB gzip); organization routes are loaded separately. No editing workflow, device validation, deployment, heap profile, or throughput benchmark is claimed.

- 2026-10-10 — Dashboard session revalidation and account verification: 101 unit tests, dashboard architecture/Biome/TypeScript checks, and the production build passed. The 55-case browser suite passed; eight focused workspace cases were rerun after the final ref typing adjustment. The real API/PostgreSQL browser flow passed in 50.0 seconds, including recovery-code verification, CSRF rotation, and retention of the same unsubmitted organization input. Reconnect/foreground checks, redacted MFA metadata, invalid and lost verification responses, repeated cancellation/focus restoration, credential/access revocation, and late results after logout are covered. Forms are retained only in mounted memory and restored after complete live scope checks; this does not implement business-command replay or dirty-navigation protection. The build still reports a 555.65 kB minified entry chunk; no performance claim is made.

- 2026-10-10 — Retained workspace portal isolation: 101 unit tests, dashboard architecture/Biome/TypeScript checks, the production build, and all 56 browser cases passed. The added browser case keeps an audit drawer open through a pending access check, a failed check, keyboard recovery, mandatory MFA, and credential revocation. Feature portals stay inside the hidden/inert workspace; suspended overlays release their modal focus trap. Global account verification remains outside that private boundary. The previous real-API MFA test was not rerun for this DOM ownership change.


## Organization editing and protected navigation

The dashboard creates, edits, reparents, and deactivates organization units with separate read/management grants, bounded parent selection, observed versions, and explicit receipt recovery. Each editor retains one immutable submission after an uncertain response. A later rejected retry cannot erase that uncertainty, and account verification never repeats the business command. Workspace-owned navigation decisions cover route history, company changes, logout, and native reload prompts; revocation discards both the form and the pending navigation intent.

Validation: architecture, Biome, strict TypeScript, and 113 unit/controller tests passed; the production build passed. All 66 Chromium browser tests passed, including dirty/pending departure choices, discarded late saves, revocation, MFA recovery, and responsive English/Indonesian layouts. The initial reload test incorrectly waited for a navigation deliberately cancelled by the native prompt; triggering the browser reload directly corrected that test, and the complete suite then passed. One real-API/PostgreSQL browser test passed in 52.3 seconds, including a committed response deliberately lost before browser acknowledgement, explicit idempotent recovery, and versioned deactivation. The 104 local documentation links resolve. The production entry chunk remains 508.31 kB minified (149.93 kB gzip), above Vite's advisory threshold; these checks do not establish runtime performance or production deployment readiness.


## Personal profile assurance after lock waits

Profile reads, immutable history, and edits now revalidate current MFA after acquiring resource/access guards, using the existing domain session policy. Receipt replay has the same assurance requirement. Three controlled PostgreSQL probes first reproduced accepted operations after natural proof expiry; all three pass after the correction and verify explicit renewal, a single committed change, and denial of a later expired replay.

Validation: 24 relevant tests passed (eight people-domain tests and 16 server/PostgreSQL tests), along with architecture/format checks and both API/worker boot jars in 1 minute 41 seconds. Existing profile rollback, history/version, owner-scope, and pending permission/credential tests passed. These are local checks; no production deployment was performed.


## Personal profile dashboard

Separate profile routes now support sensitive reads, bounded immutable history, and owner-company editing with a required reason. Ordinary employee views do not preload sensitive fields. Profile receipts bind the person ID and profile version independently of employment, and uncertain submissions use explicit idempotent recovery. Permission loss, company changes, and disposal clear private state and reject late responses.

Validation: architecture, Biome, strict TypeScript, 128 unit/controller tests, and all 77 Chromium browser tests passed. The production build passed; its entry chunk is 513.49 kB minified (150.82 kB gzip), retaining the existing size advisory. Light/dark desktop and mobile screenshots were inspected. One real-API/PostgreSQL browser test passed in 55.2 seconds, including profile edits, immutable history, and an unchanged employment version. All 105 local documentation links resolve. No runtime performance benchmark or deployment was performed.


## Keyboard skip navigation

The shell now uses a keyboard skip control that focuses its owned main element without creating native hash-history entries outside the router. This removes the observed blocker warning and preserves Back protection for edited forms. Architecture/Biome/TypeScript, 128 unit/controller tests, the production build, and 16 relevant browser tests passed. The complete 77-test suite and real API run passed immediately before this isolated focus change; they were not repeated for it.

### Employment assurance after access waits

Employee creation, detail/directory/history reads, revisions, and scheduled-revision cancellation now validate current MFA after ordered resource and access guards, including receipt replay. Six controlled PostgreSQL regressions first reproduced accepted expired proofs, then passed after the boundary change. Renewed proof permits the original command once; an expired replay cannot reveal its receipt or advance the aggregate. The focused run passed 39 tests (8 domain and 31 PostgreSQL-backed server tests), architecture/format checks, and both application boot jars. This includes employment access, cancellation, transfer, and profile assurance coverage. No deployment or device validation was performed.

### Dashboard employee creation

The dashboard creates separate person/employment records with initial terms, optional bounded organization/manager lookup, localized field validation, and a required reason. One immutable submission survives an uncertain response and MFA renewal; explicit receipt recovery does not duplicate creation. Dirty/pending departure protection, scope disposal, future-start detail navigation, and independent creation/read permissions are covered. The checks passed 142 unit/controller tests, architecture/Biome/TypeScript validation, a production build, 85 full browser tests, and the real-API/PostgreSQL browser flow (51.2 seconds including startup). The real API creates with organization assignments, deliberately loses the committed response, and recovers the original version-zero employee before verifying its profile and history. A stale fixture reason assertion was corrected before the successful integration run. Three focused browser tests passed after the final skip-control clipping adjustment; inspected light/dark/mobile screenshots show the form and keyboard skip control correctly. All 106 local documentation links resolve. The production entry remains above Vite’s advisory threshold at approximately 517 kB minified; this is not a runtime-performance measurement.

### Employment details with assignment references

A company-wide employment read now returns the effective employee plus bounded current organization/manager references in one transaction. It uses people-before-structure-before-access guard order and rechecks live original/current permissions and MFA after waiting. Sensitive profile fields and manager account details are excluded from the response. The focused validation passed 36 tests: 8 domain and 28 PostgreSQL-backed server tests including transport/OpenAPI, employment access and expired assurance, historical terms/current labels, cross-company rejection, and cancellation between reference reads. Both boot jars, architecture, and format checks passed. The generated OpenAPI includes the additive endpoint. No deployment was performed.


### Employment revision editor validation

- Added the dated employment editor with coherent assignment references, bounded selection, immutable observed-version commands, and explicit recovery through lost responses, MFA renewal, conflicts, and scope disposal. Shared onboarding assignment controls retain their own request and form ownership.
- Real PostgreSQL/browser validation exposed the existing employment-history sort mismatch after a second revision. The client and fixtures now follow the API's descending cursor, including revision zero and strict continuation progress. The repeated integration passed with one persisted revision after a deliberately lost acknowledgement; historical terms and headcount remained unchanged.
- Dashboard architecture, formatting, TypeScript, and all 156 unit/contract tests passed. All 92 Chromium browser tests passed (4.2 minutes), and the real API/PostgreSQL browser scenario passed (57.1 seconds). Production build passed; desktop light/dark and 390-pixel screenshots were inspected. All 107 local documentation links resolved.
- The production entry chunk is approximately 521 kB minified (153 kB gzip), retaining Vite's existing size advisory. No large-company performance measurement or live deployment is claimed.


### Single employment revision review

- Added a scoped revision detail API with one coherent employment version, immutable revision/cancellation evidence, company-local date, and current cancellation availability. Read and management authority remain independent; a grant acquired while waiting does not expand the original request.
- All 42 selected tests passed across the people domain and server contract, employment access, assurance, details, cancellation, and single-revision suites. New cases cover company isolation, the local midnight boundary, permissions gained/revoked during a pending read, credential revocation, cancellation cleanup, and MFA expiry after access waits. Architecture/format checks and both application JARs passed.


### Worker fixture assurance alignment

- CI exposed 16 worker test failures during employee fixture creation: test actors carried recent MFA proof, but their stored operator accounts had no enrollment. The communication, leave, and payroll fixtures now record their test-only enrollment alongside that proof, matching the live-access boundary. Production MFA policy is unchanged.
- All 38 worker tests passed, including publication/push, payroll, leave, imports, storage cleanup/validation, synchronization, SMTP, and worker lifecycle cases. Architecture and format checks passed. The preceding employment server/domain selection passed 42 tests; the dashboard revision editor passed 156 unit tests, 92 browser tests, and the real API scenario.

2026-10-10: The dashboard now reviews and cancels scheduled employment revisions using the guarded revision snapshot, current company date, observed aggregate version, and an immutable operation. The history entry and cancellation evidence remain retained. Dashboard architecture, Biome, TypeScript, and 168 unit tests across 34 files passed. All 99 Chromium browser tests passed, including seven cancellation scenarios for server-controlled eligibility, lost responses through MFA/already-cancelled replies, version conflicts, and scope disposal. The real PostgreSQL/API browser flow passed with a committed cancellation whose first response was deliberately lost; retry returned the same receipt and history retained the original revision. Light, dark, and mobile screenshots were inspected. The production build and 107 local documentation links passed. Vite reported its chunk-size advisory for the 538.19 kB entry chunk (155.39 kB gzip); no runtime performance claim is made.

2026-10-10: The dashboard client-policy editor now uses the configured head, complete versioned replacement commands, exact UTC fields, module switches, minimum builds, bounded maintenance, and explicit recovery of uncertain receipts. Dashboard architecture, Biome, TypeScript, and 185 unit tests across 37 files passed; all 107 Chromium tests passed. The focused browser suite covers eight editing scenarios, including first revision, configured/effective differences, validation, stale heads, MFA, expired activation after an unknown outcome, company departure, and credential revocation. The real PostgreSQL/API browser flow passed after creating policies through the UI, recovering a deliberately lost response, activating maintenance, enforcing a minimum Web build, and restoring access through independently authorized settings. Light/dark/mobile screenshots were inspected; the production build and 108 local documentation links passed. Vite retained its chunk-size advisory for the 540.37 kB entry chunk (155.63 kB gzip). GitHub CI for b989dd7 completed successfully for both backend and dashboard.


## Lifecycle assurance after access waits

Lifecycle use cases now recheck applicable MFA after acquiring resource and access guards, before returning data, committing a mutation, or replaying a receipt. Offboarding retains its additional recent-authentication requirement. A controlled account-lock probe expires the original proof, verifies rejection without a receipt, renews the proof, and checks the committed receipt can only be replayed with current assurance.

Validation: architecture and format checks, 34 selected server tests (including 12 assurance cases), eight people domain tests, and both server/worker builds passed. The initial probe reproduced 11 accepted operations with an expired proof; offboarding already rejected that proof through its stricter recent-authentication gate. Documentation link validation checked 108 local references.


## Scoped lifecycle template details

Added `GET /api/v1/companies/{companyId}/lifecycle/templates/{id}` for direct template review and versioned editing. The use case owns shared template/access guards, live read permission and MFA checks, and company-scoped not-found behavior. It reuses the existing repository contract and preserves case snapshots.

Validation: 42 selected server tests and eight people domain tests passed, including seven new detail tests and the additional pending-read MFA probe. Tests cover current definitions, unchanged existing cases, foreign-company IDs, read permission, credential/account/membership revocation during waits, and cancellation releasing guards before an edit. Architecture/format checks, OpenAPI registered-route validation, and server/worker builds passed.


## Lifecycle template dashboard

Added company-scoped template catalogs, direct details, manage-only creation, and versioned checklist editing. The feature owns one bounded page/read or one immutable save command. Dynamic task controls retain their values through row removal and equivalent identity renewal; additions/removals remain bounded even when actions are batched. Definite conflicts require explicit reload, while uncertain outcomes retain the original operation through later MFA or conflict replies. Existing case snapshots remain independent of template edits.

Validation: dashboard architecture, Biome, TypeScript, and 205 unit tests across 42 files passed. All 118 browser scenarios passed, including 11 lifecycle scenarios for pagination, direct-link permissions, dynamic rows, localization, company changes, cancellation, lost responses, MFA, and conflict recovery. The final batched-field-array guard was verified by the unit suite and real-API integration. The PostgreSQL/API browser flow passed in 1.1 minutes (38.9-second test), creating and revising a template, replaying a deliberately lost save response, preserving an existing case's definition, and rejecting a foreign-company template ID. Light/dark and 390-pixel screenshots were inspected. The production build passed; Vite retains its existing 500 kB advisory for the 548.08 kB minified entry (156.78 kB gzip). Local documentation validation checked 110 references. No performance or live-deployment claim is implied by these checks.


## Lifecycle employee references

Case details/lists and assigned-task reads now include a minimal current employee reference. One SQL snapshot acquires the case version, task state, employee number, and legal name. Command aggregates and authorization remain separate; lifecycle permissions do not expose private profiles or employee-directory endpoints.

Validation: 42 existing selected server tests and 8 people-domain tests passed, covering lifecycle access/MFA, concurrent reads, templates, commands, and generated OpenAPI. All 3 new context tests passed after correcting fixture expectations for normalized employee numbers and concealed profile/directory responses. They cover current names, company and assignee scope, profile exclusion, reassignment, and completion. Architecture/format checks passed, and both boot JAR tasks completed. No migration, deployment, device test, or performance measurement was performed for this change.


### Lifecycle case, history, and assigned-task dashboard reads

Implemented company-scoped case browsing, frozen checklist details, on-demand history, and an account-owned pending-task queue. Case pages acquire ten bounded aggregates; assigned-task and history pages acquire fifty entries. Task panels retain the selected observed snapshot. Company changes and disposal cancel the old owner, late results are ignored, and a history access failure removes the corresponding overview. Current employee identity is mapped from the minimal lifecycle read projection without requesting private profiles.

Validation: dashboard architecture, formatting, TypeScript, and 218 unit/controller tests across 44 files passed. The seven focused lifecycle browser tests passed, followed by all 125 browser tests in 5.7 minutes. Coverage includes direct-link authorization, pagination, company changes during a held read, history revocation, and responsive English/Indonesian layouts. Light, dark, and mobile screenshots were inspected. The real browser/API/PostgreSQL integration passed in 1.2 minutes (47.3-second test), including a frozen template snapshot, the current employee reference, the actor-owned task queue, history, and foreign-company denial. The production build passed; its main chunk is 600.34 kB (173.23 kB gzip) and still exceeds Vite's default chunk-size warning threshold. These checks do not establish a deployment performance measurement. Case and task mutation workflows remain planned.


### Versioned lifecycle task changes in the dashboard

Task panels now support completion/reopening and manager waiver of optional tasks. They pin the observed case/task, require a reason, submit one immutable operation, and retain its identity through an uncertain response, MFA renewal, and later rejected retries. A definitive conflict requires a fresh task list. Navigation protection covers edited, pending, and unconfirmed panels; disposal cancels owned work, and acknowledgement refreshes only the owning case or assigned queue. Read-only access, another assignee, and closed cases do not expose editing. The existing guarded backend commands remain authoritative.

Validation: dashboard architecture, formatting, TypeScript, and 232 unit/controller tests across 46 files passed. All 28 lifecycle browser tests passed in 1.5 minutes, including the ten new task scenarios for scoped actions, required reasons, immutable history, uncertain delivery, verification, conflict recovery, pending disposal, live permission loss, and mobile layout. Light desktop and dark Indonesian mobile screenshots were inspected. The real browser/API/PostgreSQL integration passed in 1.1 minutes (43.0-second test): it completed an assigned task, deliberately lost the committed response, recovered the same receipt, reopened the task at the current version, and verified exactly one history entry for each transition. The production build passed; its main chunk is 606.27 kB (174.55 kB gzip), with the existing Vite chunk-size warning. All 112 local documentation links were verified. Case initiation, assignee selection, case completion, and offboarding screens remain planned.


### Scoped lifecycle assignee discovery

Added a bounded display-name lookup that requires lifecycle management and returns only eligible active company members' account IDs and names. Identity supplies the minimal membership projection without acquiring email, grants, or credentials; the lifecycle use case owns eligibility permissions and current actor authorization. Shared company/member/account guards precede current credential/MFA checks. Selection remains advisory, with assignment/start commands rechecking the target before committing.

Validation: architecture and Kotlin formatting checks, 56 selected server integration/contract tests, 17 identity domain tests, and eight people domain tests passed in the final 3-minute-17-second Gradle run; both application jars built. Four new endpoint tests cover literal wildcard search, finite pagination, company isolation, minimal output, independent grants, disabled/ineligible members, and a candidate losing authority before assignment. Existing controlled access tests now include lookup revocation, and all 14 lifecycle assurance scenarios reject MFA proofs that expire during a guard wait. An initial compilation exposed an identity type crossing the feature's public delivery boundary; the use case now publishes its own lifecycle reference without adding a cross-feature delivery dependency. Browser assignee selection remains subsequent work.


## Lifecycle task assignment dashboard

Lifecycle managers can assign pending tasks through an on-demand company member picker or explicitly remove an assignee. The picker acquires only display names and account IDs through the scoped lookup, retains one 50-member page, and cancels abandoned searches. Assignment has its own panel and immutable operation, with observed case versions, required reasons, explicit receipt recovery, and protected departures. A rejected candidate can be replaced only when the original outcome is definite. Manage-only operators can assign from their queue without case, profile, or identity-administration reads.

Validation passed dashboard architecture, Biome and strict TypeScript checks, all 249 unit/controller tests, all 146 browser tests (6.6 minutes), and the real-API/PostgreSQL browser scenario (1.3 minutes; test 49.8 seconds). The database scenario removes and restores an assignee, deliberately loses a committed assignment response, recovers the same receipt, and verifies one transition at each version. Browser checks cover member withdrawal, explicit bounded search, company/account cancellation, uncertain saves through MFA and rejected retries, protected panel changes, and Indonesian mobile layouts.

Regression checks exposed missing focus-return targets when cancelling a dialog over a task drawer. The shared close button and task controls now declare those targets. Five targeted keyboard/focus cases and the full browser suite passed; staying preserves the form and restores focus inside its panel. The mobile assignment/picker screenshots were inspected.

The production dashboard build passed. The main bundle is 609.61 kB minified / 175.16 kB gzip and retains Vite's 500 kB chunk advisory; runtime performance has not been benchmarked. All 114 local documentation links resolve. Case initiation and case completion remain subsequent browser workflows.


## Membership read assurance after access waits

Company member directories, role template catalogs, and membership-grant details now recheck MFA after acquiring their company/member/account guards. They use the shared domain session policy with the current clock and live access, preserving the original/live permission intersection. An entry-time proof cannot authorize a response after expiring during a wait. The existing `mfa_required` response supports account verification followed by a new read.

Validation passed architecture and format checks, 31 HTTP/PostgreSQL tests and 17 identity domain tests, and both API/worker builds in one Gradle run (2 minutes 22 seconds). Three new HTTP scenarios hold the account acquisition, advance the policy clock beyond MFA validity, assert a safe 403 response, then verify the factor and successfully read again. Related membership revocation, role/grant consistency, identity administration, and MFA behavior remain covered. No route or database migration changed.


## Audit filter response synchronization

The dashboard CI run for lifecycle assignment exposed a timing-dependent audit assertion: the existing table could still be visible before the updated filter request was observed by the test. Its trace contains the successful response with the correct UTC window and resource filters. The test now waits for that specific response and validates its complete query. It also records the request count before editing, so the explicit-search assertion checks that typing does not fetch.

Dashboard architecture, Biome, strict TypeScript, and all 249 unit/controller tests passed. All six audit browser scenarios passed (22.8 seconds), covering pagination, filter recovery, access revocation, pending work, and focus. The change is limited to test synchronization; the subsequent CI run remains to be observed.


## Lifecycle case initiation dashboard

Operators can start an onboarding/offboarding case from an authorized employee detail, choose an active company template, review its version and calendar due dates, and submit a reason. The picker retains one explicit 20-template page and cancels abandoned reads. New tasks start unassigned. The command pins distinct case/operation IDs, employee, template version, date, and reason. Definite template rejection permits reselection; an uncertain result stays pinned through MFA or later rejection until receipt recovery. Company departure disposes the form and cannot restore a late response. Starting offboarding creates a checklist without terminating employment.

Validation: dashboard architecture, Biome, strict TypeScript, and 269 unit/controller tests across 53 files passed. The full browser run passed 155 of 156 scenarios, including all 146 pre-existing scenarios; its remaining failure was a new assertion expecting the wrong assignee label. After correcting that assertion, all ten case-creation scenarios passed (28.1 seconds), with no production changes after the full run. The real API/PostgreSQL browser integration passed in 1.3 minutes (48.4-second test), including a deliberately lost committed creation response, recovery of the same version-zero receipt, frozen task dates, and exactly one creation history entry. Desktop and dark Indonesian mobile screenshots were inspected. The production build passed with a 612.36 kB main chunk (175.57 kB gzip) and the existing Vite size advisory. All 115 local documentation links resolved.


## Lifecycle case cancellation and onboarding completion dashboard

Lifecycle managers can review an open company case, provide a reason, and cancel it or complete an onboarding checklist. Completion requires every required task to be done and every optional task to be done or waived. Cancellation retains task state/history while removing pending work from active queues; onboarding completion changes no employment terms or access. Separate use cases and HTTP operations share only the case receipt mapping used by task/assignment transitions. Offboarding completion remains a separate dashboard capability because it affects employment and company access.

The editor pins the observed case/version and one operation/payload. Task and case editors are mutually exclusive. A definite concurrent change requires a protected reload; ambiguous results remain immutable through MFA renewal and later rejection until the original receipt is recovered. Request ownership, navigation protection, and focus restoration cover dirty, pending, and unconfirmed states. Disposal cancels work and discards late results across company changes. The same review controls support English/Indonesian and light/dark responsive layouts.

Validation: dashboard architecture, Biome, strict TypeScript, and all 290 unit/controller tests in 55 files passed. All 61 lifecycle browser tests passed in 2.8 minutes, including the 12 new cancellation/completion scenarios. The real-API/PostgreSQL browser test passed in 1.4 minutes (56.4-second test), exercising both new operations with committed responses deliberately lost, exact replay receipts, preserved task data, and one immutable final transition. Production build passed; the main bundle is 614.76 kB (175.79 kB gzip) with the existing Vite advisory for chunks over 500 kB. The dark Indonesian 390-pixel review panel was visually inspected, changed-document relative links resolved, and git diff whitespace checks passed. These checks do not claim device, production deployment, or runtime performance validation. GitHub dashboard CI for the preceding case-initiation commit also passed its full browser and real-API suites.


## Guarded offboarding review context

The people API now exposes a scoped offboarding review read with the current case/checklist, minimal employee reference, employment aggregate version, and company-local date. It requires lifecycle read/manage and people manage/offboard permissions. One use case coordinates the existing repository contracts under shared people/case/company/member/account guards and revalidates original/live scope, credentials, and MFA after waiting. It does not acquire private profile fields, change business data, or move orchestration into a datasource/repository. The result is command context; the completion endpoint still independently checks both versions, current eligibility, recent authentication, and employment/access consequences.

Validation: architecture and Kotlin formatting checks passed, as did eight people-domain tests and 40 server tests across the targeted runs, covering the review, existing lifecycle mutations/races, MFA expiry, and generated OpenAPI. The initial run had one test-only assertion failure because Jackson's member map operation was selected instead of iterating history entries; explicit iteration fixed it, and all nine review HTTP tests then passed in a 53-second Gradle run. The other 31 server tests had passed in the initial run. Review checks include company-local dates, matching employment/case versions, completion rejecting an obsolete employment version, private-profile isolation, each required grant, foreign/wrong-kind/closed cases, and access or credential revocation during a controlled case wait. Server and worker jars were built successfully; no migration or deployment was needed.


## Offboarding completion dashboard

The case overview now links eligible operators to a separate offboarding review. Its read uses the guarded API context and retains the current checklist, minimal employee reference, employment/case versions, last working date, and server company date. The page explains employment closure and conditional company-access deactivation. Known checklist/date blockers prevent premature submission; remaining employment/access rules and recent authentication are evaluated by the server and mapped to localized failures.

One controller owns the review request or immutable completion command, including both observed versions. Definite conflicts require a protected reload; uncertain completion retains the original operation/payload through MFA and later stale/closed replies until an explicit retry recovers its receipt. Company changes and disposal cancel requests and reject late results. Equivalent verification, theme, and locale changes retain draft fields. Acknowledgement replaces the reviewed snapshot with confirmation and a separate current-case link. No profile or identity-administration endpoint is used by the review.

Validation: architecture, Biome, strict TypeScript, and all 307 unit/controller tests in 57 files passed. All 78 lifecycle/offboarding browser tests passed in 3.5 minutes; after refining confirmation and notice spacing, all 17 offboarding scenarios passed again in 37.6 seconds. Coverage includes every required grant, server date with an intentionally incorrect browser clock, required/optional task blockers, changed employment versions, protected reload/focus, uncertain completion through MFA and closed-case replies, current policy failures, cancellation, and company isolation. The real-API/PostgreSQL test passed in 1.4 minutes (54.5-second test), including an intervening employment revision, conflict recovery, a committed completion with its response deliberately lost, exact receipt replay, ended employment, and one immutable completion event. Its synthetic revision request was corrected to the existing version field; no API contract change was needed. The production build passed at 618.14 kB main output (176.41 kB gzip), retaining the existing Vite chunk-size advisory. The final dark Indonesian 390-pixel page was visually inspected, 121 local documentation links resolved, and whitespace checks passed. GitHub backend/dashboard CI for the preceding guarded-review commit also passed. No production deployment, native device, or runtime performance benchmark was performed.

## Employee import read assurance

Import directory, summary, row, and attempt use cases now check the current interactive session assurance after their resource/access guards. Import-specific permission and credential failures retain their existing classifications. The shared company-session policy prevents a proof that expires while waiting from exposing private proposals. Worker execution authority remains independent of interactive MFA lifetime.

Validation passed root formatting and architecture checks, eight people-domain tests, 22 PostgreSQL/server tests (import behavior, access, four controlled MFA-expiry/renewal cases, and the API contract), and server/worker boot JAR builds. These checks do not establish deployment or performance results.

## Independent private-profile access scenarios

The browser permission test now isolates missing read access, a read-only direct editor link, and editing from a non-owning company. The previous combined test exhausted its 30-second CI budget after five full navigations took 5.2–5.5 seconds each; its final assertion had only 637 milliseconds remaining. The downloaded trace identifies the cumulative navigation time. The three independent scenarios passed locally with the same per-test timeout and no retries.

## Employee import review dashboard

Company-scoped import directories and routed summaries now expose observed counts, bounded row proposals/outcomes, localized field issues, and attempt pages. Private row data is acquired on demand, scoped to current import grants, and cleared after access loss, refresh, or disposal. Invalid business proposals remain reviewable; transport mapping rejects malformed data and non-progressing pages. Each result view owns cancellation and rejects late responses.

The shared detail panel now fits the available viewport width when scrollbars reserve space. The architecture scanner distinguishes module paths ending in `-import` from static/dynamic import statements; four regression tests run in the dashboard check command.

Validation passed architecture/Biome/strict TypeScript checks, 323 unit/controller tests in 59 files, all 196 browser tests in 8.6 minutes, and a production build. The real-API/PostgreSQL browser scenario passed in 1.6 minutes, including queued preview counts, parsed proposals, and attempt history. Its preview remains queued because this browser fixture has no worker; backend tests separately exercise preview/apply under real job leases. The dark Indonesian mobile screenshot was inspected after animation completion, and final panel bounds/focus restoration passed. All 122 local documentation links resolved. The build retains the existing large-chunk advisory (main bundle 631.30 kB, 179.80 kB gzip). No deployment or performance benchmark is claimed.

## Employee import job context

The import summary now includes the current job status, cancellation request, and observed available actions. The application use case coordinates the import and job contracts under existing resource/access guards and uses a non-locking job observation, preserving the worker lock order. Delivery exposes no job payload/checkpoint and explicitly depends on the shared job domain type. Commands retain independent version, authority, queue, and partial-application checks.

Validation passed root formatting and architecture checks, eight people-domain tests, 25 PostgreSQL/server tests, and server/worker boot JAR builds. New HTTP checks follow preview, apply, cancellation, cleanup, resume, and completion; cover an all-invalid preview and terminal cancellation; and read a summary while another transaction holds its job row lock. MFA expiry/renewal and existing import/access/API contracts also passed. The initial compile identified the missing explicit jobs-domain dependency in delivery; the corrected build and tests passed.

## Employee import transitions in the dashboard

Implemented guarded apply, resume, and cancel routes with current job/cancellation context. Partial apply requires explicit confirmation. One immutable operation, version, reason, and choice survive uncertain delivery and MFA renewal; definite conflicts require a protected fresh review. Acknowledgement clears stale review data, and disposal rejects late results. Cancellation copy describes a request to stop further work without implying rollback or immediate worker completion.

Validation: dashboard architecture, Biome, strict TypeScript, and 343 unit/controller tests across 61 files passed. All 22 import browser scenarios passed, including localized dark mobile layout, permissions, available actions, protected refresh, MFA recovery, company departure, and lost responses. The real API/PostgreSQL browser integration passed, including queued import cancellation with a deliberately lost committed reply and idempotent recovery. Production build passed (636.19 kB main chunk, 180.59 kB gzip); the existing 500 kB chunk advisory remains. Checked 124 local documentation links. No production deployment or worker processing was exercised by the browser integration.

## Bounded browser file and CSV transport infrastructure

Added file repository/datasource contracts, owned native selection and UTF-8 reading, and bounded CSV downloads without object-URL registries or cleanup timers. The repository coordinates selection and acquisition; `safeFileCall` preserves cancellation and maps size, encoding, and access failures. CSV HTTP reads share the cookie transport, deadlines, bounded stream cleanup, and JSON error contract. Encoded request bodies exceeding the API's 1 MiB limit fail locally before CSRF or command delivery.

Validation: architecture, Biome, strict TypeScript, and 360 unit/controller tests across 63 files passed. New tests cover native cancellation, delayed aborts, picker/read/download cleanup, UTF-8 boundaries and BOM preservation, maximum acquisition size, immutable mappings, chunked response limits, safe errors, and expanded JSON rejection without network calls. Feature submission UI is integrated in subsequent work.

## Separate browser and API integration CI budgets

Run `38044161387` passed all 196 browser scenarios, then exhausted the dashboard job's 20-minute budget during API integration. The browser suite took 13 minutes 45 seconds and the duplicate server build took another four minutes. The integration job now consumes `apps-server.jar` from the checked backend job in the same workflow run. Browser verification retains its 20-minute budget; API integration owns a separate 15-minute budget and report artifact. The JAR artifact is retained for one day and is not a release or deployment.

Validation: actionlint 1.7.12 passed; the artifact name/path matches the existing integration fixture. Execution of the revised workflow remains a CI check after push.

## Employee CSV intake in the dashboard

Implemented `/people/imports/new` with local CSV selection, authenticated template download, a reason, and explicit preview submission. Use cases own file eligibility and coordinate import/file repositories. Public state contains metadata only; one bounded CSV and immutable operation remain in controller memory until acknowledgement or disposal. Unknown delivery retains the original file and identifiers through MFA renewal. Known initial format/size failures permit correction. The native reader has a 15-second deadline and releases its timer, callbacks, and abort listener on every completion path.

Validation: architecture, Biome, strict TypeScript, and 381 unit/controller tests across 65 files passed. All 224 dashboard browser scenarios passed in the full regression run, including 15 new CSV scenarios. The real API/PostgreSQL integration passed with native CSV selection, an actual template download, UTF-8 BOM preservation, parsed row proposals, a deliberately lost preview response, and cancellation/recovery. Localized dark mobile intake was inspected, including a 120-character filename without viewport overflow. Production build passed (641.81 kB main chunk, 182.10 kB gzip); the existing 500 kB chunk advisory remains. Checked 126 local documentation links. The browser integration does not run a worker or establish a production deployment.

## Approval inbox and stage review

Implemented an isolated approvals feature with domain repository contracts/use cases, DTO validation and immutable mapping, owned read controllers, lazy routes, and Fluent pages. Inbox access is checked before I/O; direct details preserve server-authorized author and beneficiary access. Twenty-item cursor pages reject duplicate or non-progressing responses. Pending reads cannot populate a replaced company or disposed screen, and refresh/access failures clear old assignment data.

Validation: dashboard architecture checks, formatting, TypeScript, and all 393 unit/controller tests passed. Production build passed. Nine new browser scenarios passed, covering pagination/context, author detail entry, denied inbox access, invalid cursors/responses, pending company changes, access recovery, and Indonesian dark mobile layout. The mobile screenshot was inspected after correcting a warning banner that expanded the grid. This increment did not run a real API browser scenario or change backend behavior; it does not claim approval mutation workflows.

## Approval observation and session assurance

Interactive approval reads now acquire a shared approval guard before shared company, membership, and actor-account guards. The header/version and separately acquired assignment overrides remain coherent while readers can share the guard. Template, delegation, and reassignment commands retain exclusive guards. All seven interactive readers/administrative commands revalidate current MFA and original/live authority after waiting, including receipt replay.

Validation: formatting and architecture checks passed; all eleven approval domain tests and 35 API/PostgreSQL tests passed. Seven new controlled account-wait scenarios verify MFA expiry, renewal, rollback, and replay without duplicate audits. Two new controlled read/write scenarios verify that inbox/detail snapshots cannot mix old request versions with new assignees. Existing decision/revocation, immutable snapshots, bounded policies/delegations, and OpenAPI contracts also passed. Server and worker JAR builds passed.

## Approval administration review API

Added guarded latest/exact template revision reads, owned delegation details, and bounded assignee references. Template output retains the existing `appliedRevision` transport field separately from the current header version. Assignee lookup uses the identity membership repository contract and returns only account identifiers and display names, with current company/kind access and session checks. No peer repository or use-case orchestration was introduced.

Validation: formatting, architecture, eleven approval domain tests, 23 API/PostgreSQL tests, and server/worker JAR builds passed. New HTTP cases cover exact revision values and decimal preservation, unowned/cross-company reads, inactive/expired delegations, literal wildcard searches, pagination, kind permissions, and minimal reference fields. Three additional controlled MFA-wait cases cover every new reader. OpenAPI registered-route validation passed. Browser administration forms remain a subsequent increment.

Approval administration validation: named template accounts and both delegation parties remain company-scoped even when a rule is inactive. Revoked local membership/account state still permits deactivation, while reactivation checks eligibility. Decimal validation preserves accepted eighteen-digit whole thresholds after PostgreSQL returns fractional zeros; meaningful precision and scale remain bounded. Forty-two distinct approval API/PostgreSQL cases and eleven domain tests passed across targeted runs, including access races, MFA after guards, read consistency, capacity, inactive reference scope, rollback, and decimal round trips. The first amount test expected the domain error for exponent notation; it was corrected to the existing HTTP `invalid_decimal_amount` boundary and both amount cases then passed. Format, architecture checks, and API/worker JAR builds passed.

CI completion budget: run `38053690694` completed Gradle verification and both JAR builds in 29 minutes 18 seconds, then uploaded the API/OpenAPI/test artifacts. The thirty-minute job limit cancelled Gradle action cleanup and skipped the dependent browser integration job. The bounded Gradle step now has thirty-five minutes, with forty minutes for the job including uploads and cleanup. `actionlint` passed. Run `38056998908` then completed backend verification, JAR builds, artifact uploads, and cleanup successfully. Its dashboard checks passed; the dependent cumulative browser/API scenario reached its ninety-second test deadline near logout. Approval administration now runs in a separate scenario, and the cumulative HR flow has a bounded two-minute budget. Run `38059887010` at `63fe2d1` subsequently passed all backend, dashboard, and real API integration jobs, validating the adjusted bounds and independent approval scenario.


### Approval template dashboard validation

The template catalog, retained rule revisions, latest-version editor, ordered stages, and bounded approver lookup passed dashboard architecture, Biome, TypeScript, and all 410 unit tests in 70 files. The production dashboard build passed. The full browser suite passed 244 scenarios before the final focus/history refinements; all 12 template scenarios then passed against the final implementation, including portal search isolation, keyboard focus restoration, protected history navigation, stale versions, company replacement, and uncertain-save recovery through MFA. A fresh browser integration run against the final Spring API and isolated PostgreSQL passed the complete workflow, including template creation, named approver lookup, decimal preservation, immutable revision review, and foreign-company rejection. The final Indonesian dark-mode mobile screenshot was inspected. No persistent development deployment or device validation was performed for this change.


### Approval delegation dashboard validation

Dashboard architecture, Biome, TypeScript, and all 428 unit tests in 74 files passed, including cancellation, late results, immutable operation retries, account ownership, invalid pages, and DST/precision handling. The production dashboard build passed. All 38 targeted browser scenarios for delegations, templates, approval reads, and identity passed; the final Indonesian dark-mode mobile screenshot was inspected after the theme transition settled. Both real API/PostgreSQL browser scenarios passed: the existing HRIS flow and an independent delegation flow covering current approver lookup, deliberately lost committed responses, receipt replay, foreign-company rejection, and disabling a delegation after recipient access revocation. The disposable fixture now provisions separate fictional scenario accounts and waits for setup and HTTP readiness. No production deployment, device validation, or runtime performance benchmark was performed.

### Approval reassignment dashboard validation

Dashboard architecture, Biome, TypeScript, all 439 unit tests in 76 files, and the production build passed. Forty-six targeted browser scenarios covering reassignment, templates, delegations, request reads, and identity passed. The Indonesian dark mobile screenshot was inspected. Tests cover excluded makers, bounded approver selection, terminal requests, immutable retries through MFA and later conflicts, explicit review after a stage advances, company replacement, and late results after disposal.

Both real API/PostgreSQL browser scenarios passed. The independent approval scenario creates and submits an expense claim, repairs its blocked assignment through the dashboard, deliberately drops the committed response, and recovers the original receipt. The claim status/version remain unchanged while only the approval version advances. Template revisions, foreign-company rejection, delegation replay, and deactivation after recipient revocation are also exercised. The cumulative identity/HR scenario passed with approval administration moved out and a two-minute overall test budget. The existing main-bundle size warning remains; no runtime performance or production deployment claim is made.

### Leave request access and read consistency

A controlled PostgreSQL probe first reproduced reassignment completing while a leave detail read was paused between approval-header and assignment acquisition. Request detail now owns the shared approval guard and current people/access guards. Request lists revalidate current employee scope under shared people/access guards. All six request readers/submission/workflow commands recheck live MFA after acquisition and before receipt replay.

Formatting, architecture checks, sixteen leave domain tests, twenty-nine API/PostgreSQL tests, and both server/worker JAR builds passed. Ten new scenarios cover coherent reassignment, cancellation cleanup, read revocation during acquisition, and MFA expiry/renewal for list, detail, submission, decisions, withdrawal, and cancellation. Existing simultaneous reservations, exactly-once ledger effects, payroll cutoffs, approval access races, and replay tests passed in the same targeted run. These checks do not claim a completed leave dashboard.

### Leave request review dashboard

Company and employee entry points now expose bounded request pages, submitted policy and working intervals, approval/cancellation snapshots, attachment metadata, and retained history. Current metadata remains current when older history is selected. The application links approvals to their source requests without feature-to-feature presentation dependencies. Owned controllers cancel obsolete reads and clear private snapshots after refresh, access loss, or company replacement.

Dashboard architecture, Biome, TypeScript, all 458 unit/controller tests in 78 files, and the production build passed. All 34 targeted browser scenarios for leave, approvals, people, and identity passed, including ten new leave scenarios. Both real API/PostgreSQL scenarios passed; the approval scenario now creates and reviews a half-day overnight leave request and follows its approval/source links. The dark Indonesian mobile screenshot was inspected. The existing main-bundle size advisory remains. This increment provides read workflows and attachment metadata; leave actions and evidence downloads remain subsequent work. No deployment or runtime performance benchmark was performed.

### Leave workflow actions in the dashboard

Implemented independently reviewed approval, rejection, withdrawal, and cancellation routes. One immutable operation and request version survive uncertain responses, verification, and later conflicts. Definite conflicts require a fresh review. Confirmation distinguishes a recorded action from final approval and separates original-leave decisions from cancellation decisions. Server available actions retain responsibility for scope, delegation, and maker exclusions.

Dashboard architecture, Biome, TypeScript, all 479 unit/controller tests in 80 files, and the production build passed. All 24 leave browser scenarios passed; after the final copy/context refinement, all fourteen action scenarios passed again. Coverage includes reasons, current versions, cancelled departures, stale requests, lost responses through MFA, company replacement during pending reads/writes, late outcomes, and localized mobile layout. The final mobile screenshot was inspected. All three real API/PostgreSQL scenarios passed in 2.7 minutes. The new independent two-account scenario verifies staged reservation/consumption, maker denial, a deliberately lost decision response, exact receipt replay, rejected and approved cancellation, withdrawal, and unchanged ledger/history counts on retry. The existing main-bundle advisory remains (656.93 kB, 182.65 kB gzip). Evidence downloads and production deployment were not part of this increment.

### Leave attachment session assurance

Attachment metadata and content now recheck current MFA after shared resource/access guards. Content repeats authorization after external storage acquisition, before returning bytes. The shared approval guard preserves coherence with assignment changes while allowing concurrent readers.

Root formatting and architecture checks, sixteen leave-domain tests, twenty API/PostgreSQL tests, and both application JAR builds passed. Three new controlled-wait cases cover MFA expiry during metadata acquisition, before storage, and after storage, followed by successful renewed reads. Existing attachment scoping, immutable references, access/credential revocation, reassignment, interruption cleanup, corruption, and HTTP Range/ETag tests passed. This change does not itself add browser downloads or establish throughput results.

### Bounded binary download infrastructure

Authenticated HTTP acquisition now validates exact file size, media type, and ETag before native handoff. It coalesces fragments into bounded 64 KiB blocks, preserves cancellation and safe JSON failures, and bounds size, deadlines, empty chunks, and loop progress. Native file I/O owns and releases its temporary object URL, anchor, abort listener, and short handoff timer. Text and JSON retain their existing 1 MiB bounds.

Dashboard architecture, Biome, TypeScript, 495 unit/controller tests in 82 files, and the production build passed. Sixteen new cases cover fragmented buffers, integrity metadata, truncated/excessive content, pending cancellation, timeouts, native dispatch failure, cleanup, and bounds before I/O. The existing main-bundle advisory remains. These infrastructure checks do not yet validate a feature download in a browser or measure heap use.

### Browser CI partitioning

The prior dashboard job exhausted its twenty-minute budget after completing 262 of 289 browser scenarios. The complete suite now runs in two independent matrix shards with separate reports, a fifteen-minute browser step, and the existing twenty-minute job boundary. Checks and build run in each job, both shards must pass before API integration, and one shard's failure does not cancel the other's report. No retries or disabled tests were introduced.

Actionlint passed. Playwright discovery verified that the 299 scenarios at `362e04f` partition exactly into 150 and 149 tests with no omission or overlap. GitHub CI at that commit passed both matrix shards, the complete backend job, and the dependent API integration job.

### Payroll cutoff contention fixture

CI exposed a test ordering error: the leave submission could pause at account acquisition during client admission, before holding its cutoff guard. Payroll could then legitimately win. The fixture now pauses after the real cutoff datasource has acquired the transaction-owned guard, with bounded latches and unconditional cleanup. Production transaction behavior was not changed for this test failure.

Root formatting and architecture checks and all twenty-four PayrollRun API/PostgreSQL tests across six classes passed in 2 minutes 46 seconds. Coverage includes cutoff contention, current access, source snapshots, calculation, receipt replay, rollback, and recovery. The complete remote backend suite also passed at `362e04f`.

### Leave evidence in the dashboard

Request details and action reviews now download selected submitted evidence through the authenticated, bounded binary transport. Domain use cases coordinate the request and native file repository contracts. Controllers own cancellation, reject late results after reload/scope replacement, and clear a revoked review without losing an uncertain decision receipt. Verification and preference changes preserve the reason field; no download is automatically replayed.

Dashboard architecture, Biome, TypeScript, all 523 unit/controller tests in 84 files, and the production build passed. All 49 targeted browser scenarios for leave review/actions/evidence and CSV intake passed, including ten new native-download scenarios that verify saved bytes, cancellation, stale failures, company replacement, malformed content, MFA renewal, and decision submission during acquisition. The mobile test caught a nested grid overflow; the corrected dark Indonesian screenshot was inspected. All three existing real API/PostgreSQL browser scenarios passed in 2.7 minutes.

The API integration fixture disables storage, so its three scenarios validate existing workflows, not a browser-to-object-storage download. Evidence UI uses owned binary fixtures; the attachment backend's twenty API/PostgreSQL tests separately cover authorization, storage access, integrity, and Range/ETag behavior. The existing main-bundle advisory remains (662.45 kB, 184.20 kB gzip). No release, production deployment, or runtime memory/throughput benchmark was performed.

### Leave policy administration API

Added a bounded administrative catalog of latest policy heads, including future and inactive definitions, and a coherent current-head/revision-history review. Administrative evidence requires `leave.manage`; the existing effective-date type lookup retains its broader request-preparation scope. Shared policy reads and exclusive writes acquire access guards in the same order and revalidate current MFA and intersected permissions, including before policy receipt replay.

Root formatting and architecture checks, sixteen leave-domain tests, twenty-seven API/PostgreSQL tests, and both application JAR builds passed. Thirteen new cases cover administrative/effective selection, complete immutable history, cursor bounds, company and role scope, concurrent shared reads versus saving, cancellation cleanup, and post-wait revocation/MFA expiry for catalog, review, effective lookup, and saving. OpenAPI generation and registered-route coverage passed in the same run. The initial test run exposed two fixture assertion mistakes involving Jackson iteration and the explicit replay marker; both were corrected. These checks cover the backend API, not its subsequent dashboard screens or a deployment.

### Leave policy review dashboard

Implemented an administrative catalog of latest policy definitions, explicit status filters, current-head details, and paginated immutable revision review. Future and disabled definitions remain visible to authorized administrators. Inline revision selection owns keyboard focus without fetching again. Company replacement, refresh, current access failure, and disposal cancel obsolete work and clear private selections; malformed responses never enter presentation state.

Dashboard architecture, Biome, TypeScript, all 546 unit/controller tests in 86 files, and the production build passed. The 34 existing leave browser scenarios and all twelve policy scenarios passed, covering bounded navigation, old revision selection, current-head preservation, invalid data, scope replacement, MFA recovery, and localized mobile layout. The final dark Indonesian mobile screenshot was inspected. All three real API/PostgreSQL scenarios passed across the regression and targeted runs; the approval scenario now opens the actual policy catalog and revision details and verifies foreign-company rejection. The production build retains a main-bundle size advisory (669.23 kB, 185.50 kB gzip). These checks do not constitute a production deployment or a runtime performance benchmark.

### Leave policy creation and editing

Added current-version policy editors with immutable codes, eligible contracts, half-day validation, optional entitlement rules, carryover limits, and required change reasons. Commands preserve their original version, operation, and detached payload after uncertain responses; subsequent MFA or conflict replies cannot make an unresolved command editable. Definite conflicts require a fresh protected review. Shared receipt DTOs now serve both policy and request commands while each repository verifies its own resource/version semantics.

Dashboard architecture, Biome, TypeScript, all 577 unit/controller tests in 88 files, and the production build passed. All 56 targeted leave browser scenarios passed across the regression and corrected-selector runs, including ten new editor scenarios. The final dark Indonesian mobile form was inspected. All four real API/PostgreSQL scenarios passed in 2.7 minutes. The independent policy scenario creates through the UI, saves a future revision, recovers a deliberately lost committed response, verifies unchanged history on replay, checks effective/current revision selection, and rejects a foreign-company read. The new scenario has its own fictional account and MFA state in the disposable fixture. The main bundle retains a size advisory (673.84 kB, 186.18 kB gzip); no deployment or runtime performance benchmark was performed.

### Leave balance session assurance

Interactive balance, ledger, and entitlement reads and independent adjustment, accrual, and year-closing commands now recheck current MFA after resource and access acquisition, including receipt replay. Durable background jobs retain their separate execution authorization.

A controlled account-lock probe reproduced six operations accepting expired assurance before the fix. Root formatting and architecture checks, sixteen leave-domain tests, forty-six API/PostgreSQL tests, and both server/worker JAR builds then passed. Thirteen new cases cover expiry and renewal across all six operations, current grant revocation, unchanged ledger contents after rejection, exact receipt replay, and cancellation cleanup. These checks validate the API; balance administration in the dashboard is subsequent work.

### Employee leave balance directory

Added a bounded employee/year catalog of existing accounts, including zero and closed balances, with current minimal type labels. Ledger responses now identify their employee/type context. Shared balance, policy, people, and access guards preserve current scope and metadata while leaving immutable movements unchanged. Balance HTTP operations have their own delivery controller; existing routes remain compatible.

Sixteen leave-domain tests and forty-seven distinct API/PostgreSQL cases passed across the regression and fresh-compilation runs. New coverage exercises bounded pages, company/team/self scope, minimal projections, future policy labels, empty and closed accounts, post-lock assurance, revocation, and cancellation. The first run exposed a stale incremental compiler-generated test delegate after its datasource interface gained a method; rebuilding only the test classes/cache resolved it, and all nineteen directory/assurance cases passed. Root format and architecture checks and server/worker JAR builds passed. The API change does not itself provide the balance dashboard.

### Employee balance and ledger dashboard

Employee details and charged years on a leave request now lead to the scoped balance directory and ledger. The views retain year/cursor context, keep current balances separate from older movements, and expose original movement evidence with related-request links. Read controllers own cancellation and reject late results after refresh, scope replacement, or disposal; equivalent MFA and preference changes do not repeat a read automatically.

Dashboard architecture, Biome, TypeScript, all 609 unit/controller tests in 90 files, and the production build passed. Fifty-one distinct targeted browser scenarios passed across the regression and corrected-fixture runs, including all twelve balance scenarios against the final code. The wait fixture now starts after React StrictMode's initial mount cycle, and filter assertions wait for the returned directory to finish loading. The final dark Indonesian mobile layout was inspected. All four real API/PostgreSQL browser scenarios passed in 2.7 minutes; the leave scenario reviews the actual refunded balance, selected movement, related request, and account directory using a reviewer without policy-management permission. The main bundle retains a size advisory (679.17 kB, 187.30 kB gzip). Balance mutations, deployment, and runtime performance measurements are not part of this increment.

### Balance adjustment availability

Ledger review now returns available balance actions after the existing resource/access guards and current assurance check. Adjustment requires original and live management authority, an independent actor, an open year, and applicable employment/type records. A new, unfunded account can be reviewed; an inactive type can still be corrected. The command remains independently authorized and versioned.

All sixteen leave-domain tests and thirty-one selected API/PostgreSQL cases passed, including four new cases for self-adjustment exclusion, closed/unavailable years, unfunded accounts, and original/live grant intersection after waiting. Root format and architecture checks and both application JAR builds passed. The frontend adjustment flow is a separate increment.

### Independent balance adjustment dashboard

The employee/year directory now leads to a bounded type picker for initial funding, while a ledger with current adjustment availability leads directly to review. The form pins its target and observed version, supports signed half-day corrections, and requires a reason. A definite conflict needs explicit re-review; an ambiguous outcome retains the original operation and payload through subsequent MFA or version failures. Re-review and equivalent verification preserve the draft without automatic submission. Scope replacement/disposal aborts owned work and rejects late results.

Dashboard architecture, Biome, TypeScript, all 655 unit/controller tests in 92 files, and the production build passed. All 25 targeted browser scenarios passed, including 13 new adjustment scenarios; the dark Indonesian mobile layout was inspected. All five real API/PostgreSQL browser scenarios passed in 3.0 minutes. The new scenario records an initial balance, deliberately loses the committed response, confirms one immutable movement after retry, rejects a competing version, and verifies a subsequent negative correction without rewriting earlier evidence. All 135 local document links resolve. The main bundle retains a size advisory (683.99 kB, 188.11 kB gzip). No live deployment, device validation, or runtime performance benchmark was performed for this increment.

Native password authentication: architecture and format checks, 17 identity domain tests, and 22 selected HTTP/PostgreSQL tests passed. Coverage includes native and browser session compatibility, pending-MFA redaction and business denial, native enrollment/verification, cookie isolation, shared web/native rate limits, concurrent replay with one issuance audit, device limits, credential revocation, and OpenAPI route/security coverage. Server and worker boot jars built. No mobile UI, device, configured OIDC handoff, or live deployment validation is claimed by this increment.


### Mobile employment discovery

The current-account employment endpoint always applies self scope, including for
company-wide readers, and returns the server company date with a bounded page.
Employment details now permit linked self readers while keeping foreign employees
not found and private profiles separate. Existing resource/access guard order and
post-wait MFA checks are retained. Architecture/format checks, eight people domain
tests, and 24 selected HTTP/PostgreSQL tests passed, including route/OpenAPI coverage,
self discovery, reference details, access revocation, company dates, bounded pages,
and proof expiry during pending reads. Server and worker JARs were built.


### Native session revocation guard

Revocation now rechecks account activation and credential version after waiting
for its account guard. Two new PostgreSQL regressions prove that a withdrawn
account or credential cannot revoke a target session or commit its audit entry.
Architecture/format checks and 21 native-session, native-password/MFA, and API
contract tests passed; server and worker JARs were built.

HRIS Mobile also provides self-profile and assignment reads at `39dc8dc`, with
account/company ownership, cancellation, bounded pages, and in-place MFA renewal.
Its full check, Android dev build, and Wasm compilation passed; nine people
domain/data tests and seven app UI tests passed. Device validation is recorded
separately and remains pending.

## Mobile sessions and company account review

HRIS Mobile now lists native device sessions, confirms revocation of another
session, and attempts server revocation before clearing the originating local
identity. Token rotation cannot prevent local logout, and a late action cannot
clear a replacement account. Cancellation finishes an admitted local deletion;
a lost remote response does not claim successful server revocation. The generic
identity-scoped storage contract and regression tests were also ported to Compose
Fluent Starter. Mobile workspace checks, Android dev build, Wasm compilation,
nine session-policy tests, three transport tests, and nine application-flow tests
passed. A disposable family on the local API returned 204 on revocation and both
its access and refresh tokens were subsequently rejected with 401. Physical-device
acceptance remains pending.

The dashboard provides company-account directories and versioned grant review.
The company/cursor own each bounded page; detail reads expose direct permissions
and applied role snapshots independently of employee profiles. Architecture checks,
661 unit tests, build, and four targeted browser tests passed. The live development
browser also completed password/MFA authentication, real membership reads, and
logout. The WireGuard dashboard is available at the configured VPN interface on
port 5173, with the API proxy targeting the loopback Compose port 8088.

## Employee account linking dashboard

The profile-owned binding review selects an eligible account and records the
observed version, reason, and immutable operation ID. Current scope, independent
authorship, and one-time binding remain server-enforced. Filtered pagination,
dirty departure, duplicate prevention, ambiguous-outcome recovery, cancellation,
and stale results are covered by the feature controllers and tests.

Architecture, format, TypeScript, 669 unit/controller tests in 96 files, and the
production build passed. Seventeen distinct targeted browser cases passed across
the profile regression and corrected-selector runs, including all four final
binding cases. The isolated real API/PostgreSQL scenario passed: a committed but
unacknowledged command replays without an extra revision, the linked native
employee obtains only its own profile, and neither a colleague's profile nor a
fabricated administrator employment becomes accessible. The database fixture and
native session are disposed after testing. The existing bundle-size advisory
remains; no device or runtime performance measurement is claimed.

## Native inbox and worker integration

A separate browser integration fixture runs the real API, PostgreSQL, and worker
with distinct non-bypass runtime roles. It owns its database and processes,
disables external delivery, generates temporary identity/cursor keys, and removes
the fixture on completion. Existing browser scenarios continue without a worker.

The integration scenario passed against the packaged applications. A reviewed
announcement reaches the intended employee's native inbox; explicit INBOX sync
returns only that recipient's references. Read replay retains one version,
acknowledgement rejects an obsolete version, and archival removes canonical
access and publishes a tombstone. A different account/company cannot read the
message, and revoking read access rejects subsequent synchronization.

Run after building both application JARs:

```sh
cd apps/dashboard
npm run test:integration:worker
```

CI downloads both verified JARs and runs this scenario separately from the
ordinary browser/API suite. This does not exercise a physical mobile device or
an external push provider.

## Announcement review dashboard

The dashboard provides company-scoped announcement directories, plain-text message
details, immutable revision history, and publication-job navigation. Controllers
own cancellation, discard replaced requests, and clear content on denied or
unavailable reads. Pagination stays bounded at 50 entries. Links retain the
selected company; they cannot silently switch to another company.

Architecture, Biome, TypeScript, 681 unit/controller tests in 98 files, and the
production build passed. Four browser scenarios passed, covering pagination,
revision navigation, literal HTML text, denied reads, late company responses,
Indonesian copy, dark mode, and a 390-pixel viewport. The final real API/PostgreSQL/
worker scenario passed in 1.1 minutes, including dashboard draft/revision reads,
published status and job review, native recipient access, read replay,
acknowledgement conflicts, withdrawal, and permission revocation. No external
delivery or physical-device validation is claimed. The existing bundle-size
advisory remains.

HRIS Mobile at `7801c99` contains the encrypted inbox reader and bounded change-feed
client. Its full check, Android dev and Web production builds passed; CI also
passed on Linux, Windows, and Apple. Reusable replica observation and accessible
list components, including regression tests, are published in Compose Fluent
Starter at `ce582e8`, whose CI passed on the same platforms. Offline company
admission, durable business commands, read/acknowledgement actions, and push
integration remain open acceptance work.

## Scoped audience reference discovery

Communications now exposes bounded company references for selecting branches,
departments, groups, and employments. The projection contains labels and IDs
without private profiles or account bindings. Selected-ID lookup can label an
inactive saved reference; it never grants permission to save or publish it.
Literal search, finite pagination, same-company RLS, and current access/MFA after
guard acquisition are enforced. Group membership remains separate from actual
publication eligibility.

Architecture and format checks and both application JAR builds passed. All seven
new HTTP/PostgreSQL cases and six API-contract cases passed across the final
targeted runs; thirteen existing authoring/preview regressions also passed.
Coverage includes minimal projections, future employments, activation, literal
wildcards, pagination, invalid/oversized selections, foreign resources, runtime
RLS, revoked credentials/grants, and MFA expiry during pending acquisition. The
endpoint's bounded inputs and company admission are included in OpenAPI.

## Communications session assurance

All interactive announcement, group, and inbox operations now check current MFA
after pending resource/access guards, including receipt recovery. Publication
checks again after queue admission. A failed check rolls back its transaction and
does not consume an operation ID; renewed verification can explicitly retry the
original command. Background execution retains its separate execution authority.

Architecture and format checks, eight communications-domain tests, 66 selected
HTTP/PostgreSQL tests, two publication-worker tests, and both application JARs
passed. The run completed in 4 minutes 34 seconds. Eighteen new regressions cover
current and historical reads, previews, edits, publication/recovery, archival,
recipient read/acknowledgement, original receipts, and MFA expiry during queue
admission. They verify denied results, unchanged evidence, and recovery after
fresh verification.

## Browser operation identifiers

Operation identifiers now work on private HTTP development origins that lack
`crypto.randomUUID()`, using browser secure random values with the version-4 UUID
layout. A direct browser check at the WireGuard development origin confirmed the
missing API and available secure randomness. Four unit tests and a browser MFA
enrollment scenario passed, including an unavailable UUID API and entropy failure.

## Announcement draft authoring

The dashboard can create and edit announcement drafts with plain-text content,
acknowledgement requirements, and company/branch/department/group audiences. Reference
searches and selected labels use bounded pages in the selected company. Controllers
retain immutable commands across lost responses, reject late results after disposal,
and require explicit conflict review. A failed label lookup preserves the draft.
MFA renewal and language changes preserve inputs; account/company replacement cancels
obsolete work. Unsaved and uncertain commands participate in departure protection.

Architecture, Biome, TypeScript, 710 unit/controller tests in 101 files, and the
production build passed. Eleven browser scenarios passed, including uncertain saves,
MFA renewal, conflict review, label retry, company replacement, denied access, English
and Indonesian copy, and a 390-pixel dark layout. The updated screenshot was reviewed.
The real API/PostgreSQL/worker scenario passed in 1.1 minutes: a dashboard save committed
with its response deliberately lost, an explicit identical retry recovered the receipt,
and history contained one draft revision. Publication, job review, native inbox delivery,
read replay, acknowledgement conflicts, withdrawal, and permission revocation also
passed. The existing bundle-size advisory remains; no device or external push validation
is claimed. Group management and publication actions are still separate work.

## Audience group administration

The dashboard provides bounded group directories, current and historical definitions,
creation, editing and activation. Employment selection supports up to 5,000 IDs with
one page of 50 current labels. Read-only revision views cannot mutate membership.
Conflicts require protected review; uncertain saves preserve one immutable operation
through later rejection. Company changes cancel reads and discard private state.

Architecture, Biome, TypeScript, all 734 unit/controller tests in 103 files, and the
production build passed. Five group browser scenarios and ten existing announcement
regressions passed in the final targeted runs. Coverage includes finite pagination,
revision zero, deactivation conflicts, lost responses, late company data, denied
access, localization, and a reviewed 390-pixel dark screenshot. The real API/PostgreSQL/
worker scenario passed in 1.2 minutes after adding group creation, deactivation,
reactivation, historical review, and publication to the named group. It also verified
the original draft receipt and native inbox delivery/replay/withdrawal. No physical
device or external push validation is claimed.

## Publication review API

Announcement managers can review current content, minimal publication-job status,
and available actions without acquiring unrelated job-directory permission. Job
inspection links retain their separate authority. Read capabilities respect
publication state, cancellation acknowledgement, revision/attempt limits, current
scope and MFA; commands remain authoritative. The review checks assurance again
after the job read and never locks a job after acquiring the announcement guard.

Architecture and format checks, 13 communications-domain tests, 41 selected
HTTP/PostgreSQL/API-contract tests, and both application JAR builds passed in the
final run. Tests cover minimal projections, independent job authority, failed-job
recovery, archival, company isolation, pending credential revocation, MFA expiry
after guards and job acquisition, and a review completing while another transaction
holds the job row. OpenAPI includes the endpoint and company admission. Dashboard
publication workflows are being connected separately.

## Dashboard publication workflows

Announcement managers can review eligibility and job status, preview recipients,
publish immediately or schedule in the company timezone, return stopped work to
draft, and archive correspondence. Each command requires an observed version,
reason and confirmation. Pending or uncertain outcomes retain one immutable
operation; verification and later rejection cannot replace it. Definite conflicts
require a fresh review. Company replacement cancels pending reads and clears content.

Architecture, Biome, TypeScript, 761 unit/controller tests in 105 files, and the
production build passed. Six new browser scenarios and ten existing announcement
regressions passed. They cover explicit preview, company/browser timezone differences,
confirmation, response loss, MFA, stale versions, disposal, denied access, localization
and a reviewed 390-pixel dark layout. The final real API/PostgreSQL/worker scenario
passed in 1.4 minutes. It creates a named audience and draft, loses and recovers the
original draft and scheduled-publication responses, cancels the scheduled job through
the dashboard, waits for worker acknowledgement, returns to draft, publishes again,
and archives through the dashboard. Native recipient isolation, read replay,
acknowledgement conflicts, withdrawal and permission revocation also pass. The existing
bundle-size advisory remains. No physical device or external push validation is claimed.

## Durable mobile inbox actions

[HRIS Mobile](https://github.com/fajarxfce/hris-mobile/commit/3d4048b) now persists
read and acknowledgement intents before transport, retaining the original operation
and observed version through response loss, process termination and a new session.
Account/company admission, bounded retries, conflict review and encrypted receipt
retirement belong to domain use cases. Reusable atomic credential storage and its
Android 7 correction were also ported to Compose Fluent Starter.

The mobile root check, Android dev build, 23 assembled Compose tests, 28 inbox-domain
tests on each JVM/browser target, and 13 JVM/two browser inbox-data tests passed.
Android 16 device checks used a fictional employee and the actual dev API. They
verified company isolation, profile/inbox reads, language persistence, admitted
offline content, read state, confirmation cancellation, and an offline acknowledgement
surviving force-stop/restart before successful retry. The temporary device proxy
was removed afterward. Native MFA, external push, iOS devices, and performance
measurements are separate acceptance work; these observations do not complete
the other mobile workflows in [completion](completion.md).

## Offline mobile workspace

[HRIS Mobile](https://github.com/fajarxfce/hris-mobile/commit/308481c) now restores
the selected company from an encrypted, session-owned snapshot. Domain policy
bounds admission by one hour, session-family expiry and required MFA assurance.
Foreground and worker checks revalidate live access; transport failure cannot
extend admission. Compare-and-set publication rejects stale selections and account
replacement, while unchanged renewals retain the route and inbox generation.

The mobile root check, Android dev build, 21 Compose tests, three application-MVI
tests and one link-parser test passed. Workspace domain tests passed on JVM and
Chromium (12 each), with five real SQLite/encryption tests; inbox domain tests
passed on both targets (30 each). The reusable atomic snapshot storage primitive
and its regressions were ported to Compose Fluent Starter; CI passed in both
repositories for that foundation change.

The Android 16 device reopened its company and inbox after force-stop with the
API connection disabled. A replacement company also restored offline without the
first company's content. Foreground revalidation retained the open detail after
reconnection. The temporary device proxy was stopped and the normal dev connection
restored. Clock/MFA expiry uses deterministic tests; iOS device and background
execution acceptance remain separate.

## Mobile company availability and identity acceptance

[HRIS Mobile](https://github.com/fajarxfce/hris-mobile/commit/bab8734) stores company
client policy inside the encrypted workspace. Maintenance and minimum builds gate
protected routes; module flags hide affected content and actions. A policy-only
renewal preserves workspace identity. Inbox synchronization and command delivery
require fresh policy before transport, retaining pending operations when availability
blocks them. Known scheduled maintenance and admission expiry have finite observation
deadlines, including when refresh fails.

The mobile root check and Android dev build passed. The assembled application passed
24 Compose tests, three application-MVI tests and one link-parser test. Workspace
domain tests passed on JVM and Chromium (21 each), with six SQLite/encryption tests
on JVM and three mapper tests on each target. Inbox domain tests passed on both targets
(37 each). Coverage includes revision zero, fractional deadlines, late results,
cancellation, scope replacement and retained commands. Shared bootstrap state
ownership and recovery coverage were also updated in
[Compose Fluent Starter](https://github.com/fajarxfce/compose-fluent-starter/commit/038d516);
its root check, Android build and Linux/Apple/Windows CI passed.

Android 16 checks used the real dev API. They verified minimum-build and maintenance
gates, an unaffected company, explicit recovery, disabled content while Inbox was
open, and restored content with its acknowledgement intact after re-enabling the
modules. Original company policy values were restored afterward.

Device checks also covered invalid/valid MFA proofs, authenticator enrollment with
a separate fictional account, acknowledgement of recovery codes, process restoration,
and recovery-code sign-in. Proofs were generated from fixture setup secrets; this
does not claim an external authenticator-app check. A disposable device session
survived cancellation of its revocation confirmation, then returned
`401 native_session_invalid` after explicit revocation. The temporary MFA account
was signed out and its company grant disabled, and the original employee account was
restored. External SSO, push delivery, iOS devices and workload measurements retain
their separate acceptance work.


Transfer admission and authority: 34 selected HTTP/PostgreSQL and API-contract tests passed, together with formatting and architecture checks. Regressions first reproduced destination People-disable bypass, adoption of an offboarding grant during a pending request, and expired MFA on transfer-history reads. The corrected transaction checks both company policies before mutation/replay and intersects captured/live authority. Tests also cover destination maintenance and client builds, unchanged original replay, secondary policy SELECT scope, denied secondary writes, third-company isolation, and context cleanup. This entry records isolated integration tests; the dashboard transfer workflow remains subsequent work.

## Scoped employment transfer review

The transfer-review endpoint supplies the current source version, both company
dates from one server instant, and current destination picker permissions.
It checks both company policies, intersected grants and MFA after acquiring its
guards. It neither acquires private profiles nor reserves mutation eligibility.

Forty-one distinct HTTP/PostgreSQL and API-contract tests and eight people-domain
tests passed, together with formatting, architecture checks and both application
JAR builds. Six review tests cover response scope, date eligibility, destination
availability, added/removed grants during a wait and cancellation cleanup. The
assurance suite also verifies MFA expiry while review waits on the account guard.
Cancellation releases both company policy guards and permits subsequent writes.

## Employee transfer dashboard

Employee details now link to an immediate company transfer with destination-scoped
assignments, explicit confirmation and immutable request recovery. The Transfers
tab loads incoming/outgoing history on demand. Reloading a conflicting review
clears discarded fields; language and theme changes retain current edits. Busy
confirmation buttons remain keyboard-focusable without accepting another action,
and long tab lists scroll within the mobile viewport.

Dashboard architecture, formatting, TypeScript, 783 unit/controller tests and the
production build passed. Sixty-four distinct browser scenarios passed across the
transfer, employee, employment-revision, lifecycle, offboarding and account-linking
workflows. The initial pass identified the form reset and mobile tab overflow;
both were corrected. Two other browser cases timed out during that pass and passed
on the focused rerun. Light desktop and dark mobile screenshots were inspected.

The real browser/API/PostgreSQL scenario passed using isolated fictional accounts.
It selects a destination branch, commits a transfer, deliberately loses its response,
and recovers one identical receipt. Source history records one transfer; the person,
profile owner and account stay unchanged. Native access to the source company is
denied afterward while the new destination employment remains readable. The owned
fixture database and native session were cleaned up.

The dev API and worker were rebuilt and restarted with migration 64 while preserving
the existing PostgreSQL volume. API health and the WireGuard dashboard returned
HTTP 200. No mobile runtime changed in this increment and no release was created.

## Work calendar session assurance

Shift and holiday directories, employee calendars, and shift/holiday/weekly/roster
commands now recheck current MFA after acquiring their guards. Seven regression
cases first reproduced acceptance of proofs that expired during an account wait.
Renewed proofs succeed; write retries retain their original ID and version while
requiring current assurance before returning the receipt.

Sixteen distinct calendar integration tests passed across the focused runs,
together with 23 workforce-domain tests, formatting and architecture checks.
The first corrected run exposed four incorrect test expectations for the receipt's
`replayed` flag; the focused rerun passed after fixing those assertions. These are
isolated PostgreSQL tests, not new dashboard or mobile acceptance.

## Shift detail API

The shift detail endpoint returns the current version and active state under
current company access. Foreign and missing IDs share one not-found response.
Shared readers can proceed concurrently; cancelling a pending detail releases
its schedule/access guards and permits a later writer.

Nineteen selected integration and API-contract tests passed, including the new
detail read, revoked grants/credentials after admission, cancellation cleanup,
inactive templates, and eight calendar MFA expiry scenarios. Formatting and
architecture checks passed. Dashboard shift management remains the next step.

## Shift management dashboard

The dashboard now provides bounded shift search, current details and versioned
editing. It supports overnight shifts, work modes and location policy. Changes
retain one immutable request until a known receipt, including across MFA renewal.
Confirmed reload clears discarded fields even when the fetched values are identical.

Architecture, formatting, TypeScript and 793 dashboard tests passed. The ten new
feature tests and six browser scenarios passed again after removing lint warnings
from the new test code. The production build passed; its 729 kB main chunk retains
the existing size advisory. Light desktop and dark mobile screenshots were inspected.

The real browser/API/PostgreSQL scenario passed. It deliberately loses a response
after a shift is committed, recovers the same receipt, assigns the original shift,
then edits and deactivates the template. The employee calendar retains revision zero
and the original overnight interval. Its isolated database was cleaned up. No mobile
runtime or physical-device interaction was part of this increment.

## Worker composition after transfer policy changes

GitHub CI exposed a missing Administration configuration in the worker's application
graph after transfer review began consuming company client policy. The API's 916
tests passed on that commit, but all 38 worker tests failed during context startup.
The dev worker was also restarting; it was stopped while the composition was repaired.
Building the JARs had not detected this runtime dependency failure.

The worker now imports the owning module configuration and declares its data/domain
dependencies. No worker SQL privilege or background execution policy was expanded.
All 38 worker tests, formatting, architecture checks and its JAR build passed.
Repository instructions now require validation of both application contexts when
shared module DI changes.

The repaired API and worker were then rebuilt in the dev Compose stack, preserving
the PostgreSQL volume. The worker logged successful startup with zero restarts;
API health was `UP`, and the WireGuard dashboard returned HTTP 200.

## Workforce self-service discovery

Workforce now exposes a bounded directory of the signed-in account's employment
references, with company-local date and timezone. It omits personal-profile fields
and remains available when People is disabled. Calendar reads still independently
authorize the selected employment. OpenAPI retains endpoint-specific pagination
bounds instead of overwriting them with the general list maximum.

Twenty-five focused API/assurance/contract tests and all 38 worker tests passed.
They cover native build admission, independent module availability, foreign scopes,
revoked credentials/membership/permissions after admission, grant replacement,
MFA expiry and cancellation cleanup. The nine API contract tests passed again after
adding explicit pagination metadata; formatting, architecture and both application
JAR builds also passed. A full recompilation initially exceeded the temporary
PostgreSQL startup wait and passed on retry after stopping the emulator.

The dedicated Android emulator also booted and restored the employee workspace
against the dev API. No physical-phone interaction was part of this increment.

## Mobile work calendar and emulator acceptance

[HRIS Mobile cffb545](https://github.com/fajarxfce/hris-mobile/commit/cffb545) connects
self-service employment discovery and weekly calendars to feature-owned MVI screens.
It retains one encrypted snapshot per company under bounded workspace/session/MFA
admission. Pending reads are cancellable, stale scopes cannot publish, and transport
failure retains only still-admitted content. Workforce access remains independent
of the People module.

Mobile root checks passed 350 JVM/UI tests. The named-timezone regression passed
in Chromium, and Android dev and production Web builds succeeded. API 24 and API 35
emulators used the dev API for overnight shifts, roster days off, week navigation,
company isolation, offline process restoration and recovery. English/Indonesian
and light/dark captures were inspected. These are emulator results, not physical
iOS acceptance or production Web authentication acceptance.

The API 24 probe reproduced a missing Java time class in the previous APK. Core
library desugaring corrected that failure without raising the supported minimum.
That convention and the date-only formatter were also ported to Compose Fluent
Starter in [5e3967c](https://github.com/fajarxfce/compose-fluent-starter/commit/5e3967c)
and [ddc73ac](https://github.com/fajarxfce/compose-fluent-starter/commit/ddc73ac).
The starter passed 197 tests, root checks and Android/Web builds.

The physical phone was unavailable over ADB during delivery; no phone UI was driven.
Validated updates use install-only when it reconnects. API health remained `UP`
and the worker had zero restarts. The preceding backend CI run passed all backend,
dashboard and integration jobs. No release was created.

## Company holiday detail API

The workforce API now exposes current holiday details for versioned dashboard
editing. Missing and foreign IDs share the same response. Reads recheck live
membership, original permissions, credentials and MFA after shared resource/access
guards; cancellation releases the guards without excluding other readers.

All 22 focused API, assurance and contract tests and 38 worker tests passed, along
with architecture/format checks and both application JAR builds. The initial test
invocation lacked Docker group access; the documented group-aware invocation
completed successfully. Dashboard authoring and deployment are separate work.

## Company holiday dashboard

The dashboard now provides bounded holiday filters, current details and versioned
creation/editing. Dates retain their calendar value across browser timezones.
Unknown save outcomes retain one operation through explicit retry and MFA renewal;
definite version conflicts require a protected reload. Company changes cancel
the previous owner and cannot restore its data from a late response.

Dashboard architecture, formatting, type checks and all 806 tests passed. Twelve
holiday/shift browser scenarios passed; the six holiday scenarios passed again
after refining shared error copy. English/Indonesian and light/dark captures were
inspected, including a 390-pixel viewport. The production build succeeded with the
existing shared chunk-size warning.

A browser scenario against isolated PostgreSQL and the real API verified one
committed holiday after a deliberately lost response, concurrent-version recovery,
and activation/deactivation effects on the employee calendar. API and worker dev
containers were updated from the validated JARs; their hashes matched, API health
was `UP`, both processes had zero restarts and the VPN dashboard returned HTTP 200.

The previously validated mobile calendar APK was installed on the authorized phone
with `--install-only`; installation and package metadata were checked without
driving its UI. Subsequent phone installations require advance notice to the user.

## Administrative work-calendar review

Workforce now exposes bounded employee selection without personal-profile fields
and a coherent calendar review with the effective weekly assignment and global
schedule version. Original shift snapshots remain distinguishable from later
template changes and future schedule assignments. The existing mobile calendar
contract is unchanged.

All 45 focused API/contract/access tests, 25 workforce domain tests and 38 worker
tests passed. Coverage includes cross-company IDs, separate profile permissions,
independent module admission, credential/grant/MFA changes while waiting, shared
read guards and cancellation cleanup. Calendar resolution bounds its date loop
without advancing beyond the last date. Formatting, architecture checks and both
application JAR builds passed. Dashboard scheduling forms are the next increment.

## Dashboard employee calendars and scheduling

The dashboard now provides bounded employee selection, coherent calendar review,
effective weekly assignments and daily roster editing. Shift selections retain
their original snapshots across catalog paging and search. Weekly writes observe
the global schedule version; roster writes observe the individual date, including
an existing explicit day off. Unknown outcomes retain one immutable request through
MFA renewal, while definite conflicts require a protected reload.

Architecture, formatting, type checks and all 826 tests passed. Nineteen calendar,
shift and holiday browser scenarios passed. English/light and Indonesian/dark
screenshots were inspected, including a 390-pixel viewport; browser testing found
and verified a shared select-width correction for long option text. The production
build passed with the existing shared chunk-size warning.

An isolated PostgreSQL/API browser scenario verified receipt replay after a
committed response was deliberately lost, retained future assignments and original
shift snapshots, explicit selection of a revised template, and conflicting daily
roster edits followed by reload. The API and worker dev containers were updated
from the previously validated JARs; API health was `UP` and neither process had
restarted. The dashboard and its same-origin API proxy returned HTTP 200 on the
WireGuard interface. Its dev process is now owned by a user service independently
of the terminal. No phone installation or UI interaction was performed for this
dashboard increment.

## Attendance session assurance

Attendance windows, capture, independent review, corrections, daily evidence and
correction history now revalidate MFA age after waiting for resource/access guards.
Replay retains the original operation and still requires current assurance.

All 41 focused API tests, 25 workforce domain tests and 38 worker tests passed.
Six PostgreSQL scenarios expire MFA during an account wait, assert no command
receipt was committed, renew verification and check original receipt replay.
Existing access, concurrent review, frozen schedules, cancellation and aggregate
read tests also passed. Architecture, formatting and API/worker JAR builds passed.
This validates the backend boundary; dashboard and mobile attendance delivery
remain their own increments.

## Attendance dashboard

The dashboard now provides employee attendance selection, bounded date review,
daily evidence, independent verification and versioned corrections. Times use the
company zone and frozen shift breaks; new corrections retain original punches.
Unknown outcomes preserve one request through explicit retry and MFA renewal.

Architecture, formatting, type checks and all 842 tests passed. Five new attendance
browser scenarios and seven calendar scenarios passed. One existing calendar test
now waits for the destination's loaded table before counting subsequent requests.
English/light and Indonesian/dark captures were inspected at desktop and 390-pixel
widths. Production build passed with the existing shared chunk-size warning.

An isolated API/PostgreSQL browser scenario recorded two native offline punches,
verified them independently into 450 accepted minutes, replayed one decision after
a lost response, and resolved a competing correction into a new 435-minute revision.
Original timestamps and both correction-history records remained available.
The database and API fixture were disposed after the scenario. Mobile capture is
the next delivery increment; browser fixtures do not validate physical GPS.

## Grouped dashboard navigation

Feature-owned navigation definitions replace the flat application menu. Grouped
sections, local search and longest-ancestor selection support additional modules
without turning the shell into a feature controller. Empty or unauthorized sections
are absent; company scope replacement disposes the search owner.

Architecture, formatting, type checks and 844 tests passed. Six browser scenarios
covered attendance behavior, detail selection, search, collapsed navigation,
Indonesian labels and a 390-pixel viewport. Existing command departure protection
remains in its workspace owner.

## Employee attendance and mobile services

[HRIS Mobile](https://github.com/fajarxfce/hris-mobile/commit/b9f7e1e) now provides
clock-in/out, attendance history, foreground location evidence and encrypted
offline commands. Permission and capture policy belong to use cases; platform
adapters only perform the requested OS operations. Commands retain their original
ID and evidence through retry and process restart. Company access is rechecked
after pending work and before publication or delivery.

Mobile root checks passed 366 JVM/UI tests. The workforce and location browser
suites passed 25 tests, and Android dev plus production Web builds passed. API 35
emulator acceptance covered denied/permanently denied permission, Settings
recovery, offline clock-in, force-stop/reopen, retry and online clock-out. The real
dev API retained exactly two entries, both pending review, and zero accepted
minutes. The emulator used simulated location; this does not validate physical
GPS accuracy or iOS permission behavior. No active application location request
remained after leaving the screen.

Home, Services and Account keep mobile navigation stable. Services groups admitted
destinations by purpose, while each feature retains its own route and MVI owner.
English/Indonesian, light/dark and 130% text captures were inspected. The validated
dev APK was installed on the authorized Android phone using `--install-only`;
installation metadata was verified without driving the phone UI.

The complete platform workflow also passed
[backend, all four browser shards and API/database integration](https://github.com/fajarxfce/hris-platform/actions/runs/38139796471).

## Company coworker directory

The company directory exposes current coworkers through a separate minimal read
projection. It returns name, employee number and assignment labels without granting
private profile access. Search is literal, pages are bounded, and live credentials,
membership, permissions and MFA are rechecked after access guards.

Architecture and formatting checks, people/identity domain tests, 26 focused API
tests and all 38 worker tests passed. The six new directory scenarios cover minimal
fields, company isolation, pagination, date policy, literal search, revoked access
after a lock wait and expired MFA. API and worker JAR builds passed. Mobile UI and
device acceptance are recorded in the mobile repository.

## Coworker department filtering

The directory supports a company-scoped department filter before pagination and a
bounded, searchable lookup of departments assigned to current coworkers. Both
reads use minimal directory permission and recheck live session assurance. OpenAPI
retains their explicit 1–50 page limits.

Eleven directory scenarios and eleven API-contract tests passed, including literal
search, combined filtering/paging, minimal fields, company isolation and revoked
credentials/grants/MFA after a wait. All 38 worker tests, architecture/formatting
and API/worker JAR builds passed. Mobile presentation is delivered separately.

## Unread inbox total

A separate account/company-scoped read returns the total unread inbox independently
of loaded pages. Confirmed read/acknowledgement commands and announcement archival
update the result; opening a detail remains a read-only action.

Nine inbox API scenarios and 21 communication-assurance scenarios passed, including
unloaded pages, another account's read state, replay, archival, access revocation
and MFA expiry while waiting. Directory and API-contract tests also passed, for
52 focused API tests, plus all 38 worker tests. Architecture/formatting and both
application JAR builds passed. Mobile badge delivery remains separate.

## Hosted backend verification budget

The backend job for `7dbe5f44` reached the 35-minute Gradle limit without a
reported test failure. The preceding run of the same backend implementation
completed in approximately 33 minutes; only the dashboard attendance fixture
had changed. The Gradle step now has a bounded 45-minute budget, with five
additional minutes for report upload and action cleanup. No tests are skipped.
Run `38167573675` at `b310f11f` subsequently passed backend verification, all four
dashboard shards, and the real API/dashboard integration job. Backend completed
in approximately 33 minutes. The earlier timeout is not counted as a test pass.

## Leave self-service discovery

Leave now exposes a bounded account-owned employment directory with company date
and timezone, independently of People and Workforce module availability. The
reader returns only minimal employment references and rechecks original/live
permission, membership, credentials and MFA after shared resource/access guards.

All 29 focused API/contract/leave tests and 38 worker tests passed, along with
architecture, formatting and both application JAR builds. Tests cover native
minimum-build admission, independent module admission, foreign account/company
isolation, bounded pagination, revoked and replaced grants, MFA expiry during
a pending read, and cancellation releasing guards. The MFA test supplies enforced
security explicitly; the general business HTTP fixture disables that enforcement.
API and worker dev containers were rebuilt without resetting PostgreSQL. API
health is `UP`, both processes have zero restarts, and a real fictional employee
session confirmed denied discovery before its grant and successful discovery
after an audited versioned membership update. The first company returns one owned
employment and empty balance/request/type pages; the second returns no employment.
Probe sessions were explicitly logged out. A fictional annual-leave policy and
12.5-day allowance were subsequently created through audited API commands for
mobile acceptance. The mobile balance/history screen and API 35 acceptance are
now recorded in [HRIS Mobile](https://github.com/fajarxfce/hris-mobile/blob/main/docs/leave.md).

## Employee leave request details API

Self-service request details now use an explicit owner-only read scope. A company
HR reader or assigned approver cannot use this route to broaden the employee
view. Existing resource/access guards, live credentials and MFA checks remain
shared with administrative details. Revision pagination preserves current header
metadata and uses a documented default of 10, with a maximum of 50 history rows.

Validation covered 41 focused HTTP/contract tests, 16 leave domain tests and all
38 worker tests, plus architecture, formatting and both application JAR builds.
The contract suite was rerun after declaring its history default explicitly.
Five new scenarios cover owner-only access, foreign-company isolation, history
progress, revocation while waiting, and MFA expiry/renewal. API and worker dev
containers were rebuilt without resetting PostgreSQL; API health is `UP` and both
processes have zero restarts. Mobile request details and revision history are
implemented in [HRIS Mobile eee789e](https://github.com/fajarxfce/hris-mobile/commit/eee789e).
Its root checks passed 491 desktop tests, and the affected 20 presentation tests
passed in Chromium. Android dev and production Web builds passed. A real
fictional pending half-day request was checked on the API 35 emulator, including
offline process restoration, online recovery, light/dark appearance and 200%
text. Mobile submission and other transactional flows remain separate work.

## Independent browser integration scenarios

CI reached logout in the cumulative identity workflow but exhausted its two-minute
budget. Its trace contained no long-running business request. CSV import and client
policy/job recovery now run independently, with separate fixture accounts,
companies and MFA enrollment. Existing assertions and per-operation timeouts remain.

Biome, TypeScript and all three affected real API/browser scenarios passed locally:
identity/lifecycle in approximately 60 seconds, import in 13 seconds and policy/jobs
in 19 seconds. The previous CI timeout is not counted as a pass. The remaining
browser scenarios passed in that CI run and were not rerun for this test-only move.
Run `38174844731` at `9abf9e08` subsequently passed backend verification, all four
dashboard shards and the complete real API/dashboard integration job.

## Employee leave preparation and mobile actions

The owner-only preparation endpoint returns effective policy, exact working-day
charges, excluded off dates and proposed balances before submission. Its use case
shares date and eligibility policies with submission and keeps resource/access
guards around its reads. It neither reserves entitlement nor creates a request,
approval or operation receipt. Current ownership, module availability, credentials,
permissions and MFA remain required after waits.

Architecture, formatting, all 18 Leave domain tests, 35 focused HTTP/contract tests
and both application JAR builds pass. The changed shared DI graph also passed all
38 worker tests. Coverage includes exact portions and overnight dates, invalid or
foreign selections, insufficient balance, existing occupancy, evidence requirements,
module independence, revocation during a wait, MFA expiry/recovery and interruption
cleanup. Repeated previews preserve the stored balance; a later actual submission
uses the same charge rules. No load or memory benchmark is claimed.

The API and worker development containers were rebuilt with zero restarts and
healthy API status. A real employee preview charged 0.5 day for a selected working
half-day and excluded the selected Saturday. Its proposed remaining balance was
11.5 days while the stored available balance remained 12 days at the same version.
The probe session was logged out; no development database reset was performed.

Mobile withdrawal, cancellation and withdrawal of a pending cancellation are
implemented in [2472964](https://github.com/fajarxfce/hris-mobile/commit/2472964).
Root checks passed 531 desktop tests, the affected 29 presentation and two
localization Chromium tests, and Android/Web builds. API 35 acceptance withdrew a
pending half-day request, restored a cancellation after an offline process restart,
sent it after reconnecting and withdrew the pending cancellation. Reservations and
consumption matched the server ledger throughout. The validated APK was installed
on the connected phone with advance notice and without launching its UI. New
mobile request creation and evidence transfer remain separate work.

## Leave confirmation against reviewed facts

Preview returns an opaque content revision covering selected dates, effective
policy, resolved calendar and annual balances. Submission can require that
revision, shares the source guards through commit, and rejects changed facts
without creating a request, reservation or receipt. Original committed operations
retain their receipt replay independently of later policy or calendar changes.
Existing direct-submit consumers retain their current authorization and accounting
checks; the optional precondition does not grant access.

Architecture, formatting, all 20 Leave domain tests, 58 focused API/contract tests,
and both application JAR builds pass. Two new domain tests and nine HTTP/PostgreSQL
tests cover stable input ordering, changed identity/date/policy/shift/ledger facts,
failed-precondition rollback and recovery, competing confirmations, replay after
withdrawal and policy replacement, pending source guards and interruption cleanup.
No new DI graph or database schema was introduced. Mobile command persistence and
the creation interface remain their own integration work.

## Employee leave submission in mobile

[HRIS Mobile 7aa9bcd](https://github.com/fajarxfce/hris-mobile/commit/7aa9bcd)
connects effective leave types, controlled dates and daily portions, a multiline
reason, server calculation review and encrypted durable confirmation. Retry keeps
the original request/operation IDs and reviewed facts. Uncertain delivery cannot
be discarded, and confirmed delivery opens the canonical request after reconciliation.
The form blocks types requiring document evidence until that integration is available.

Root checks passed with 592 JVM/UI tests, 42 Leave presentation Chromium tests,
two localization Chromium tests and Android/production Web builds. API 35 emulator
acceptance excluded Saturday from a three-date selection, reserved the expected
days, restored a confirmed input after process restart with the API blocked, and
delivered it after reconnection. Withdrawal returned the available balance to its
baseline. Dark mode and 200% text were inspected. The validated APK was installed
on the connected phone using install-only. This does not claim physical-phone UI
interaction, iOS acceptance or runtime performance measurements.

## Document request scope and session assurance

Interactive document metadata/content reads and upload commands now preserve the
request's original permission boundary while checking live access and session MFA
after resource waits. Shared aggregate readers coexist while writers wait. Content
acquisition remains outside SQL transactions and rechecks authority before returning
bytes; an expired proof after storage cannot publish upload progress. Background
validation keeps separate execution authority.

Formatting, architecture checks, 13 document domain tests, nine data tests, 126
document/leave-evidence API tests and all 38 worker tests passed, together with both
application JAR builds. Thirty-two new API regressions cover original grants,
MFA expiry and renewal, receipt replay, pending permission/credential changes,
shared readers, interrupted ownership and post-storage recovery. The initial
original-grant probes reproduced the failure before the implementation change.
API and worker development containers were rebuilt without resetting PostgreSQL;
API health is `UP` and both processes have zero restarts. Retention/inventory
administration has a separate assurance review; this increment does not complete
document UI or physical storage acceptance.

## Document administration and queued admission

Retention and inventory metadata now preserve original/live permissions and current
session MFA after shared access guards. Validation and inventory creation repeat
their proof checks after waiting for the job queue; inventory retains its stricter
recent-authentication requirement. An upload that expires during admission creates
no validation job. Rejected operations can recover using the original operation ID
after verification, without duplicating a job.

Ten regression scenarios reproduced the prior behavior before the fix. The final
26 new cases also cover proof renewal, pending credential/permission revocation,
original grant boundaries, interruption cleanup and upload expiry. Formatting,
architecture checks, 13 document domain tests, 73 affected API tests, all 38 worker
tests and both application JAR builds passed. Development API/worker containers
were rebuilt without resetting data; health is `UP` and both have zero restarts.

## Dashboard browser acceptance against production output

Browser suites now build and serve the production dashboard on their isolated
test ports, including the API and worker fixtures. This removes the development
module graph from browser acceptance while preserving proxy routing and existing
scenario/assertion timeouts. CI no longer builds the same dashboard separately
before the suite's own build.

The preceding CI run passed the backend and three browser shards; the fourth
timed out loading the malformed leave-policy scenario before application content
appeared. Its trace contained 465 development module requests. After the change,
all 409 browser scenarios, 13 real API scenarios and the real worker publication
scenario passed locally. Configuration formatting also passed. This is validation
of the production artifact and does not establish a runtime performance claim.

## Owned document evidence discovery

Documents now provides independent self-service employment discovery and a bounded
catalog of published evidence. Responses retain account/company/employment scope
and expose accepted revision metadata without object keys or storage I/O. A pending
replacement keeps the earlier published revision available. Current authority and
MFA are checked after access guards; administrative grants do not expand self access.

Formatting, architecture checks, 13 document domain tests, nine data tests, 19 new
self-service API cases, API contract/download/retirement regressions and all 38
worker tests passed. API and worker JARs were rebuilt and deployed. The private
Garage, signature updater and ClamAV services are healthy; the scanner reports
ClamAV 1.5.4 with signature database 28150 dated October 11, 2026. A fictional
employee uploaded a valid PDF through the development API. The real worker accepted
it, its owner discovered it through the new catalog, and an authorized byte-range
download matched the original content. The same self route rejected an account
with only administrative grants. Probe sessions were closed and credentials remain
in ignored development configuration. This does not complete the mobile file queue
or establish production signature monitoring, backup recovery or load capacity.

## Mobile leave evidence selection

The mobile application now selects up to three owned, published personal-document
revisions for leave submission. The catalog has bounded pages and expiring read
admission. Closing the picker cancels acquisition; late callbacks cannot change a
replacement form. A retained submission replays its original revision IDs.

Mobile root checks passed with 616 desktop tests, together with 52 Leave and two
localization Chromium tests, Android dev assembly and the production Web build.
API 35 emulator acceptance selected an existing processed PDF under a policy
requiring evidence. The API confirmed its original revision, size and digest,
reserved one day, then restored the balance on withdrawal. The isolated test grant
was reversed and its policy retired; document and request audit history remain.
Light/dark appearance and 200% text were inspected. The phone was unavailable;
no physical-phone interaction is claimed. File acquisition, scoped transfer and
the dashboard document workflows remain separate delivery work.

## Private VPN development API

The optional `compose.wireguard.yml` overlay publishes the API on
`10.77.77.1:8088` alongside its existing loopback port. Compose validation confirmed
both specific bindings and retained the private document storage/scanner settings.
The running API was recreated with the document and VPN overlays; PostgreSQL,
Garage, signature updates and ClamAV remained healthy. API health returned HTTP 200
on both interfaces, and the dashboard returned HTTP 200.

The VPS WireGuard firewall initially rejected forwarding into Docker. Two narrow
conntrack rules now admit only the original private API destination/port and its
established replies. The transaction passed `nft --check` before application and
was persisted in the existing WireGuard firewall configuration. Other isolation
rules were retained. An HTTP health request from the phone over WireGuard returned
200. HRIS Mobile separately verified sign-in, company selection and process
restoration on the emulator without ADB reverse; phone installation was install-only.

## Mobile document upload

[HRIS Mobile d4cdd58](https://github.com/fajarxfce/hris-mobile/commit/d4cdd58)
adds scoped confirmation, encrypted resumable delivery, server processing status,
explicit recovery and cancellation. Mobile root checks passed with 684 desktop/JVM
tests, the sync/application Chromium suites passed 12 tests, and Android dev plus
production Web builds passed. The API 35 emulator uploaded a 607-byte PDF to the
running development API, reached server `READY` after inspection, and restored the
local record after process restart. Phone ADB was unavailable for this increment.

The integration exposed a foreground scheduler dependency cycle. Its correction
and generated-DI regression also landed in Compose Fluent Starter, which passed
its own root checks, sync Chromium suite and Android dev build. Authenticated mobile
downloads and administrative document workflows retain their own delivery work.

## Mobile document download and export

[HRIS Mobile](https://github.com/fajarxfce/hris-mobile/commit/e991035) now lists owned
published revisions with employment and category filters. Downloads use fresh
content authorization, exact ranges, encrypted progress and whole-file checksum
verification. Pause and explicit resume retain committed bytes; reopening a partial
copy does not silently start transport. Imported and downloaded files have separate
detail routes.

Save copy uses the platform destination dialog, then rechecks company and resource
access before reading plaintext. Bounded writes, final authorization, finite
deadlines and owned cleanup cover cancellation and late results. Known denial
remains gated through a failed retry. Generic output adapters and their regressions
were also ported to Compose Fluent Starter.

Mobile root checks passed with 739 desktop/JVM tests, plus 83 document, two
localization and five app Chromium tests. Android dev and production Web builds
passed. API 35 acceptance downloaded a 607-byte PDF from the real development API,
cancelled and reopened the native save dialog, saved an identical SHA-256 copy,
and restored the encrypted completion after process restart. Screens were inspected.
After the owner reconnected WireGuard, the validated APK was installed on the
phone with `install-only`; no phone UI was driven. Administrative document
workflows remain separate delivery work.

## Document employee discovery

Administrative Documents now exposes bounded employee search and exact-reference
lookup under document and private-profile read grants. The current company
date/timezone accompanies minimal display references; unrelated People availability
and its general directory permission are not required. Foreign or missing exact
references return the same empty page. The use case reuses repository contracts,
preserves original/live authority and checks current MFA after access waits.

Formatting, architecture, 13 document domain tests, nine data tests, 14 new
employee-discovery API scenarios and 14 OpenAPI checks passed. Regression coverage
also passed for owner-only catalogs and original-grant handling, together with all
38 worker tests after the shared DI addition. Both application JARs built. The new
cases cover scoped fields, literal search, bounded pagination, exact lookup,
revocation while pending, expired MFA, interrupted ownership and independent module
admission. Dashboard consumption remains the next increment.

## Dashboard document read contracts

The dashboard Documents feature now has separate employee, document, revision and
validation-history contracts. Use cases own current read grants and exact resource
selection; the repository maps transport values inside the HTTP failure boundary.
Scope mismatches, duplicate entries, nonprogressing cursors, malformed revisions
and unsupported content metadata are rejected before reaching presentation.

Download re-reads the selected revision before requesting authenticated content.
The existing bounded binary transport and browser file handoff are reused; acquired
buffers are cleared after handoff, mapping failure or late cancellation. File bytes
do not enter presentation state or a query cache. The 33 feature boundary tests
passed alongside dashboard architecture, formatting and TypeScript checks.

## Dashboard document browsing and downloads

Documents now owns its employee directory, folders, document history and revision
routes. Fluent resource tables keep the published revision separate from unfinished
replacement files. Company-pinned navigation, explicit refresh, locale/timezone
formatting and download cancellation follow workspace ownership. Known content
denials hide details through pending and failed refreshes; MFA recovery does not
replay a download. Workspace suspension cancels an active file handoff.

Architecture, formatting, TypeScript and all 890 dashboard unit tests passed,
including 46 document boundary/query/controller cases. Eleven new browser scenarios
and two navigation regressions passed against production output. Screens were
inspected at desktop and narrow widths in light/dark themes. The real API/database
browser scenario verified independent employee discovery, empty folders, HR-only
upload metadata, revision cancellation and foreign-company rejection.

The development API and worker were updated with document employee discovery while
preserving private storage, antivirus and WireGuard bindings. A browser then read
the existing fictional employee's published document and downloaded the actual
worker-validated PDF. All 605 bytes matched its original file and SHA-256; the
browser session was logged out and closed. This validates read/download behavior,
not browser upload authoring or retention controls.

## Current document revision actions

Revision detail reads now return bounded validation/cancellation capabilities from
the request's intersected management grants and current resource/job facts. Stored
state stays separate from the displayed expiry projection, so an expired active
upload can still be closed explicitly. History rows do not authorize actions.
The existing command guards, observed versions and receipt replay remain in charge
of each mutation. The detail use case rechecks MFA after reading the validation job.

Architecture, formatting, 17 document domain tests, nine data tests and 58 selected
API scenarios passed. Both application JARs built; constructor/DI
composition did not change. Coverage includes current/original grants, incomplete
content, active validation, the eight-attempt limit, publication, expired upload
cancellation, MFA expiry during the job read, interruption and guard release.
The added response field preserves the existing flat metadata contract.

## Dashboard document processing

Document revision details now link to fresh validation and cancellation reviews.
Current server capabilities and local management grants control availability.
Confirmation uses the reviewed version and one immutable operation; an uncertain
receipt stays pinned through explicit retry, MFA recovery and later rejection.
Definite conflicts require a new review. Workspace departure owns cancellation,
and known access loss hides the review through pending or failed recovery.

Architecture, formatting, TypeScript and all 913 dashboard unit tests passed,
including 69 document cases. Twenty document browser scenarios passed against
production output. The real API/database browser scenario cancelled an upload,
discarded its first committed response and recovered the original receipt through
the UI with the same operation and payload. Foreign-company reads remained denied.

Development acceptance uploaded a 605-byte PDF as test setup, requested validation
through the dashboard and observed actual worker publication. The authenticated
browser download matched the original bytes and SHA-256. Dashboard cancellation
then closed an incomplete replacement while preserving the published revision.
Desktop and narrow layouts were inspected in both themes. Browser upload authoring
and retention administration remain separate increments; no new mobile build was
required for these dashboard controls.

## Bounded dashboard file input

The dashboard now has an owned binary file-input contract and browser adapter.
Opaque references keep SDK objects and file contents out of presentation. File
hashing reads at most 64 KiB at a time; part reads and HTTP upload bodies are bounded
to one MiB. Every retained file has explicit release and cancellation ownership.
The existing native picker is shared with CSV intake without peer-datasource calls.

Cookie transport retains current CSRF, same-origin credentials, restricted headers,
finite deadlines and stable operation IDs for binary commands. It snapshots the
part before authentication waits and clears that copy afterward. Repositories clear
buffers or release newly acquired file handles on mapping failure or late cancellation.

Architecture, formatting, TypeScript and all 933 dashboard unit tests passed,
including 64 file/HTTP boundary cases. Twenty-six existing CSV-intake and document
browser scenarios passed against production output. The new input adapters still
need their document-authoring UI and API acceptance; these checks do not establish
whole-workflow upload performance.

## Resumable document review

Current revision details now expose upload eligibility and, when needed, the
original reserved part request for its authorized operator. A different operator
may cancel an unfinished upload but cannot acquire that request reference. History
rows omit continuation metadata. Expiry, exhausted attempts and current grants
limit eligibility; session assurance is checked again after reading pending work.

Architecture, formatting, 18 document domain tests and 29 selected API/contract
scenarios passed. Both application JARs built. The cases include interrupted
storage, original request recovery, operator isolation, attempt exhaustion, MFA
expiry and interrupted resource ownership. Dashboard TypeScript and 71 document
tests passed; its mapper binds continuation metadata to the current part, freezes
it, and safely ignores unsupported future capabilities. These contracts support
the separate upload-authoring interface.

## Dashboard document upload contracts

The document feature now provides administrative creation, replacement and resume
use cases over the bounded file-input and HTTP contracts. Preparation preserves
document identity and observed versions; resume requires the selected content's
size and SHA-256 to match. Explicit retries recover metadata receipts and current
server offsets, including reserved part operations after page replacement.
Acquired part buffers are cleared on success, failure and late cancellation.

The 18 new boundary scenarios passed, including interrupted metadata/part
responses, immutable replacement identity, reserved operation recovery, invalid
selection, cancelled acquisition and transport, and loss of upload eligibility.
Architecture and TypeScript checks passed. The dashboard authoring controls and
their live storage/worker acceptance are delivered separately.

## Dashboard document authoring

Employee folders and document details now provide upload and replacement forms.
Revision details offer resume under current server capabilities. The page owns
selection, progress, pause, immutable retry and file release; raw bytes stay in
the file/HTTP boundaries. Changed companies cancel obsolete work, while equivalent
MFA recovery retains a paused command without replay. Completion requires a
separate validation action before publication. Fluent forms use concise localized
copy and bounded layouts in both themes.

Architecture, formatting, TypeScript and all 963 dashboard unit tests passed,
including 99 document cases. Nine upload browser scenarios and twenty existing
document scenarios passed against production output. Screens were inspected at
desktop and narrow widths. The isolated API/database scenario created metadata
through the browser, verified an actual reserved-part continuation when storage
was unavailable, and recovered a lost cancellation receipt with company isolation.

The development API and worker were updated while preserving private storage,
antivirus and WireGuard bindings. Browser acceptance uploaded a 2,280,605-byte PDF,
discarded its first committed part response, resumed from server progress, and
published it through the actual inspection worker. Its authenticated download
matched every byte and SHA-256. A replacement resumed after page departure and
file reselection; cancellation retained the original published revision. Retention
administration remains outstanding.

## Dashboard retention policy contracts

The Documents feature now has separate retention-policy use cases and a data
adapter for current definitions, ascending version history, and versioned saves.
The `documents.retention` grant is required independently of content access.
Unconfigured and indefinite policies remain distinct; no statutory duration is
assumed. Bounded response mapping rejects inconsistent identity, classification,
pagination, and receipts. Concurrent head/history reads require another review
if a newer history entry appears.

Architecture, formatting, TypeScript and the dashboard unit suite passed, including
23 new retention-boundary cases for scope, immutable projections, duration/version
limits, history races, MFA rejection, and late cancellation. This increment
provides the contracts; the retention dashboard remains subsequent work.

## Dashboard retention policies

Administration now provides retention classifications, current policy details,
bounded immutable history, and versioned configuration. The independent retention
grant does not provide content access. Unknown outcomes retain the original
command through MFA and later rejection; definite conflicts require another
review. Changing companies drops old policy references and cancels owned work.
English/Indonesian layouts were inspected in desktop light and narrow dark modes.

Architecture, formatting, TypeScript and all 998 unit tests passed, including
twelve new editor-lifecycle cases. All 38 document browser scenarios passed.
The initial narrow-screen test used an incorrect theme-button accessible name;
the corrected test passed without a product change. The isolated API/database
scenario passed with separate administrator/operator accounts: independent grants,
lost committed responses, exact replay, concurrent-version conflicts, finite and
indefinite revisions, cross-company denial, and revoked retention access. The
development dashboard serves this interface; no backend or mobile build changed.
Archive, legal-hold and content-retirement controls remain subsequent work.

## Dashboard document lifecycle contracts

Documents now exposes retention-state review, bounded lifecycle history, and
versioned archive, restore, hold, and release use cases. These use the independent
retention grant and do not acquire employee or content projections. Mapping
preserves immutable archive snapshots and checks duration/eligibility consistency,
history identity and ordering, and the exact command receipt. The API still owns
unfinished-upload checks, policy selection, and current authorization.

Architecture, formatting, TypeScript and all 1,027 dashboard unit tests passed.
The 29 new boundary cases cover nullable initial versions, indefinite retention,
inconsistent snapshots, head/history races, ineligible transitions, separate
permissions, receipt binding, MFA failures and cancelled late results. Lifecycle
UI controls remain the next increment.

## Dashboard archive and legal hold

The retention workspace now provides document-reference lookup, current status,
paginated lifecycle history and immutable change details. Independent operators
can archive, restore, place a hold and release it without acquiring file content
or employee profiles. Forms own fresh reviews, immutable receipt recovery and
cancelled/late work. Document details link to the same workspace when permitted.
Administration routes share a prefix; prior policy bookmarks preserve scope when
redirected. English and Indonesian durations use locale-aware unit formatting.

Architecture, formatting, TypeScript and all 1,041 unit tests passed, including
fourteen command-owner cases. All 48 document browser scenarios passed, including
nine lifecycle scenarios and legacy-route recovery. Desktop and narrow dark
history views were inspected. Both isolated API/database retention scenarios
passed: an unfinished upload prevented archive, an independent administrator
cancelled it, a lost archive response replayed its original receipt, a subsequent
policy change preserved the old snapshot, hold survived restore, rearchiving
selected the new policy, and revocation removed access. These dashboard changes
required no new backend or mobile build. Accepted-content retirement is next.

## Document retirement review

The API now exposes a retention-only revision review with server-time eligibility,
observed revision and retention versions, publication status, and a stable blocker.
It does not acquire content or private profiles and does not reserve the decision.
The retirement command rechecks recent assurance after reference and manifest
reads; expired proofs cannot authorize its subsequent writes.

Formatting, architecture, all 18 document domain tests, nine data tests, 47 selected
API cases and all 38 worker tests passed. Seventeen new review/assurance cases cover
independent grants, scope, frozen archive policies, competing holds, publication,
revocation, pending-read cancellation and proof expiry. Existing reference and
retirement regressions and the generated API contract also passed. API and worker
JARs were built. Dashboard retirement controls remain the next increment.

## Dashboard retirement contracts

Documents now maps the independent retirement review into immutable company and
resource-bound metadata. The command carries both observed versions, its original
operation ID and reason without acquiring a replacement review. Current and future
bounded blocker codes prevent submission; eligibility uses the server review time.

Architecture, formatting, TypeScript and all 1,069 dashboard unit tests passed.
Twenty-eight new boundary cases cover independent access, contradictory metadata,
unknown blockers, immutable snapshots, receipt binding, original-command replay,
safe failures and late cancellation. These are contracts; the retirement controls
and their browser/API acceptance remain subsequent work.

## Dashboard accepted-content retirement

The retention workspace now provides revision lookup, a separate server-derived
review, and explicit retirement confirmation. It displays the observed versions,
publication status and archive snapshot; blockers disable submission. The command
owner retains the original operation, review and reason through unknown outcomes,
MFA recovery and later rejection. Company departure cancels ownership and discards
late results. Ordinary revision details expose the link only with the independent
retention grant. Both languages and themes use the existing Fluent components.

Architecture, formatting, TypeScript and all 1,082 dashboard unit tests passed,
including thirteen new command-owner cases. All 61 document browser scenarios
passed. Narrow light/dark review and history views were inspected; a scoped form
spacing adjustment was checked with the narrow-layout scenario again.

All three isolated API/database retention scenarios passed. The new positive
scenario uses a pre-existing fictional archive seeded by the disposable database
owner, with constraints and triggers enabled. It verifies historical retirement
without clearing a newer publication, recovery of one committed result, a hold
inserted after review, original-receipt replay during that hold, fresh confirmation
after release, current publication removal and immutable history. This browser
scenario exercises real retirement transactions and cleanup registration; it does
not exercise upload, scanning or physical object deletion. Those retain their
separate backend/storage validation records.

All 17 isolated API/browser scenarios and the separate worker/browser scenario
passed with the shared fixture. Upload acceptance captures and disposes the real
API response before forwarding it to Chromium, avoiding a test failure when the
browser has already released its failed upload stream. The narrow-layout check
also passed after the final spacing change.

The development API and worker were updated with guarded retirement review while
preserving the private document services and WireGuard overlay. API requests on
loopback and WireGuard and the dashboard on WireGuard returned HTTP 200. These
dashboard changes required no new mobile build.

## Dashboard attendance correction history contracts

Workforce now exposes a correction-history use case over the existing API. It
coordinates the bounded employee reference and history repositories, validates
the employee/date/cursor before acquisition, and returns an immutable company and
date-bound page. Mapping checks exclusive descending revisions, distinct IDs,
correction intervals, bounded page size and cursor progress. Cancellation and
existing access/MFA failures retain their classifications.

Architecture, formatting, TypeScript and all 1,110 dashboard unit tests passed.
The 28 new boundary cases include empty/terminal pages, malformed and nonprogressing
responses, reference mismatches, private error suppression and cancellation before
and between reads. History browsing remains the next presentation increment.

## Dashboard attendance correction history

Attendance days now link to a bounded history table and immutable correction
details. Company timezone, explicit absence, original reasons, revision references
and recording actors are retained. The table preserves date filters across paging
and return navigation. Its query owner discards pages on scope replacement or
unmount, hides stale rows during refresh, and leaves MFA recovery explicit. Refresh
shares the page header; the detail panel uses the existing Fluent focus lifecycle.

All 1,118 dashboard unit tests passed with architecture, formatting and TypeScript
checks, including eight additional read-lifetime cases. All 14 attendance browser
scenarios passed, including nine history cases for pagination, immutable details,
invalid cursors, scope cancellation, MFA, localization and narrow layouts. The
updated API/browser scenario passed against PostgreSQL: two competing corrections
remained separately readable and the final overnight total and raw punches stayed
intact. An initial run was interrupted to correct the test's save-link locator;
the corrected run passed. Narrow light/dark and real-API desktop views were inspected.
No backend or mobile build changed for this dashboard increment.

## Company attendance exception API

Workforce now provides a company-wide directory of pending attendance evidence and
incomplete accepted clock-in/out pairs. One SQL snapshot returns bounded employee
display references and factual counts, with date/filter-bound keyset pagination.
Independent reviews can proceed while a page is being read; subsequent pages
remain live observations. Corrected days retain visibility when new evidence is
pending. The endpoint does not infer absence or expose location/device evidence.

The use case requires original and current administrative read access under shared
people/access guards and rechecks session assurance before releasing its result.
Migration V65 adds the company/date/employee index without altering existing records
or grants. Architecture and formatting checks and both application JAR builds
passed, together with 36 workforce domain, 48 selected API/attendance and 38 worker
tests. New cases cover cursor scope/order, resolved earlier rows, correction and
review transitions, RLS isolation, cancelled reads, credential/grant revocation,
and MFA expiry at the account guard and after projection acquisition. Initial test
failures corrected fixture assumptions about the credential column, validation
HTTP status and disabled fixture MFA; the rerun passed with explicit enforced MFA.
Dashboard queue delivery remains the next increment.

## Dashboard attendance exception contracts

The dashboard now has a Workforce use case and data boundary for exception pages.
It validates the administrative grant, date range, filter and cursor bounds before
acquisition. The mapper retains immutable company/query scope, validates exclusive
date/employee ordering and continued-page progress, and rejects resolved or
inconsistent facts. Pending and incomplete rows remain distinct from absence.
Technical failures use the existing localized Result boundary; cancellation is
propagated before and after acquisition.

Architecture, formatting, TypeScript and all 1,149 dashboard unit tests passed,
including 31 new boundary cases. The tests cover malformed and oversized pages,
cross-scope cursors, filter mismatches, late data, immutable mapping and access/MFA
failure preservation. The development API and worker were updated, with migration
V65 applied and the private document/WireGuard services preserved. Loopback and VPN
API checks and the VPN dashboard returned HTTP 200. No new mobile build was needed.

## Dashboard attendance exception queue

The dashboard now exposes an administrative employee-day review queue with bounded
dates, pending/incomplete filters, keyset paging and links to attendance details.
Company replacement clears the original query and cancels its request; filter
changes reset pagination. Refresh and MFA recovery do not expose the previous page.
Names and employee numbers share one column so desktop actions remain visible,
while narrow layouts retain table scrolling within the page.

All 1,157 dashboard unit tests passed with architecture, formatting and TypeScript
checks, including eight new query-lifetime cases. All 22 attendance/navigation
browser cases passed on the final layout. The real API/PostgreSQL scenario also
passed: self-only access could not read the directory; independent clock-in/out
reviews updated and resolved the queue; later evidence on a corrected day became
pending again without replacing accepted minutes or the immutable corrections.
The original lost-response and competing-correction checks continued to pass.
Desktop and narrow light/dark views were inspected; desktop action visibility is
also asserted against the actual API fixture. This dashboard increment needed no
new mobile APK.

## Overtime session assurance

Overtime reads and commands now apply the existing identity session policy after
their resource/access guards and before completing the operation. Original
receipts retain their outcome but require current assurance. Expiry before commit
rolls back the request, immutable history, approval effects, audit/outbox, mobile
invalidation and receipt together. Detail readers share employee and approval
guards while writers retain exclusive ownership.

Architecture and formatting checks, both application JAR builds, 36 Workforce
domain tests, 37 overtime API tests and 38 worker tests passed. Fifteen new cases
cover missing company scope, MFA expiry during account acquisition, read-result
acquisition and transaction completion, original-key recovery, cancellation and
concurrent detail readers. Existing access, delegation, overlapping windows,
period closing, immutable history and replay checks also passed. No API response
shape, database migration or mobile APK changed in this increment.

## Dashboard overtime read contracts

Workforce now has dashboard domain and data contracts for bounded overtime
directories and individual requests. Administrative directory access remains
separate from employee scope and assigned/delegated review. DTO mapping validates
requested/actual durations, status and approval references, immutable history and
exclusive page progress. Acquisition and mapping share the HTTP failure boundary;
cancellation before or after I/O prevents late results from reaching presentation.

All 1,220 dashboard unit tests passed with architecture, formatting and TypeScript
checks. The 63 added boundary cases include malformed or oversized responses,
filter mismatches, stale cursors, original snapshot preservation, mutable caller
input, limited directory grants, MFA errors and late cancellation/failure. The
validated API and worker were deployed with the document and WireGuard overlays
preserved; the VPN API and dashboard returned HTTP 200. Dashboard overtime pages
remain the next increment; no mobile APK changed.

## Dashboard overtime directories and request review

Overtime now has date/status-filtered directories, company-pinned detail navigation,
requested/actual/approved time, original work-calendar evidence and paged history.
Change panels retain keyboard focus without reacquiring the request. Assigned
approvers can follow their source request without company-wide overtime access.
Scope replacement cancels obsolete requests; refresh hides private data until a
fresh result succeeds, including after MFA verification.

Architecture, formatting, TypeScript and all 1,236 dashboard unit tests passed.
Fifteen overtime browser scenarios and eleven existing navigation/approval browser
scenarios passed, covering paging, filters, timezone boundaries, cancellation,
malformed responses, MFA recovery and contained English/Indonesian light/dark
layouts. The real API/PostgreSQL browser scenario also passed: a later calendar
change preserved the original overtime schedule, maker approval was rejected, an
independent reviewer saw the assigned request without directory access, and
original plan/submission/decision receipts replayed without extra history. Native
employee reads and foreign-company rejection were verified in that scenario.

The integration used its own disposable database and fictional accounts. The
development VPN dashboard and API returned HTTP 200; no backend migration or
runtime deployment was required. Overtime authoring/review forms and closing remain
separate dashboard work.

## Dashboard overtime action contracts

Dedicated domain use cases now review available actions, submit actual time,
decide a pending request and withdraw an unfinished request. Pure policies validate
whole-minute intervals, requested bounds, breaks and reasons. The API remains
authoritative for current ownership, reviewer independence, period locks and
completed work. Repositories map explicit DTOs and verify the returned request ID
and exact next revision inside the shared HTTP failure boundary.

A captured action snapshot can recover its original receipt without first reading
a newer request. Definite rejection and denied-read classifications are separate
from uncertain delivery, so a later failure cannot implicitly release an earlier
unconfirmed command. Presentation forms remain the next increment.

Architecture, formatting, TypeScript and all 1,272 dashboard unit tests passed.
The 36 added contract cases exercise validation before I/O, immutable input,
receipt mismatches, safe MFA errors, cancellation and late results/failures. No
backend runtime or mobile build changed.

## Dashboard overtime actual time and decisions

The request command bar now opens dedicated actual-time, approval, rejection and
withdrawal reviews. Time fields use the original request's timezone and validate
whole minutes through the domain. Separate presentation handlers own each action;
current server capabilities and grants determine availability. Approval records a
stage without implying that the entire request is approved.

One immutable command survives an unknown outcome, explicit retry, MFA renewal and
later conflict replies. Definite first conflicts require a new review. Private
details remain hidden after denied reads through pending or failed retries.
Navigation protects drafts and unresolved commands; changing company or disposing
the controller aborts acquisition and suppresses late results. Browser commands
remain in memory and do not claim durable offline recovery.

Architecture, formatting, TypeScript and all 1,291 dashboard unit tests passed,
including 19 new presentation/clock-input cases. Fourteen action browser scenarios
and the fifteen existing overtime read scenarios passed. English light and
Indonesian dark narrow layouts were inspected. The real API/PostgreSQL scenario
submitted actual work, approved independently, rejected, and withdrew both planned
and pending requests. Deliberately lost actual/decision/withdrawal responses
recovered their original receipts without duplicate history. It also verified
original calendar preservation, maker exclusion, assigned-reviewer access, native
employee reads and foreign-company rejection.

The scenario used an owned disposable database. The development VPN dashboard and
API returned HTTP 200; no backend deployment or mobile APK changed. New-plan
authoring and monthly closing remain separate dashboard work.

## Overtime planning preparation

Added bounded employee discovery and an individual planning review with the
observed employment version, effective schedule and server-derived company date
and timezone. Administrative overtime grants do not grant private-profile or
general work-calendar access. Employee self-service may review only its current
employment. Shared guards preserve scope and session assurance through pending
reads; review creates no request, receipt or period row.

Architecture and Kotlin formatting checks passed, along with 37 workforce domain,
64 selected API/contract and 38 worker tests. Both application JARs built. Ten new
HTTP/assurance cases cover discovery pagination, minimal output, ownership,
company-local date limits, missing schedules, period availability, revocation,
expired MFA after acquisition, concurrent readers and cancellation cleanup. One
domain case verifies the shared inclusive planning-date limits. OpenAPI generation
includes both endpoints. This increment has not changed the development runtime
or mobile APK; dashboard planning remains in progress.

## Overtime reviewed-calendar precondition

Planning review now returns a content revision over company, employment version,
timezone and resolved calendar references. Clients may pin it in `planningRevision`;
a changed calendar rejects the new plan without consuming its operation. Original
receipt recovery precedes comparison with current facts. Supplied revisions bind
the operation payload, while omitted revisions preserve existing client receipts.

Architecture, Kotlin formatting and both application builds passed. The 39
workforce domain, 67 selected API/contract and 38 worker cases passed across the
main check and a focused rerun of the three new HTTP cases. Coverage includes
changed calendars, complete rejection rollback, fresh-review recovery, exact
receipt replay, malformed revisions and foreign-company review references. Two
new pure policy cases verify stable content and changes to scope, employment,
timezone, schedule origin, holiday and shift evidence. No migration is required.

## Dashboard overtime planning contracts

Added domain/data contracts for employee discovery, planning review and request
creation. The command pins the observed employment and calendar revision, uses
company-local dates and shares interval limits with actual-time submission. A
new request's receipt must retain its original ID and revision zero. Explicit
retry performs no intervening read that could replace the original command.

Architecture, Biome, TypeScript and all 1,323 dashboard unit tests passed. The 32
new cases cover bounded discovery, independent grants, immutable review mapping,
invalid responses, exact receipt identity, minute precision, breaks, overnight
work, regular-shift overlap, daylight-saving dates and cancellation. Calendar-date
formatting is now a shared pure domain function; its existing presentation API
and tests remain compatible.

Development API and worker images were rebuilt from validated JARs and restarted
with the document-services and WireGuard overlays. Database, Garage and ClamAV
remained healthy; API readiness was UP and the VPN dashboard returned HTTP 200.
No migration or mobile APK changed. Dashboard planning presentation remains next.

## Dashboard overtime planning

Added the scoped employee picker and reviewed-calendar form for new requests.
Confirmation retains the exact employment, calendar revision, time interval and
operation through an uncertain response. Date/company replacement cancels stale
reads, denied details remain hidden, and drafts participate in navigation guards.
The compact picker fits both desktop panels and narrow layouts.

Architecture, Biome, TypeScript and all 1,340 dashboard unit tests passed,
including 17 new controller/time-input cases. The nine new planning browser
scenarios and 29 existing overtime scenarios passed across the combined run and
a focused rerun. Two focused browser cases and static checks also passed after
the final picker-width adjustment. English light, Indonesian dark and narrow
layouts were inspected.

The real API/PostgreSQL scenario now creates its first plan through the dashboard.
A deliberately lost committed response recovered the original receipt after the
calendar changed. Actual time, independent approval, rejection and withdrawal
also passed, including lost-response recovery, maker exclusion, employee-owned
reads and foreign-company rejection. The scenario cleaned up its disposable
database. No additional backend deployment or mobile APK was needed. Monthly
attendance closing is the next workforce dashboard increment.

## Monthly closing session assurance

Period lists, published employee snapshots, closing and recovery now validate
current session assurance after their guards and before returning private data
or committing. Late expiry rolls back the period, job, targets, journal and
operation receipt together. Background execution retains its existing credential
and fenced-lease checks.

Architecture and Kotlin formatting checks, 39 workforce domain tests and 40
selected API/contract tests passed. Twelve new database-backed cases cover expiry
at a guard, expiry after acquisition, cancellation, rollback and explicit receipt
recovery. Both application JARs built. The 38 worker cases passed across the main
run and a focused rerun: five existing closing fixtures needed an enrolled MFA
account and proof to invoke the newly protected interactive start command.
No migration, development deployment or mobile APK changed in this increment.

## Monthly closing overview and published employees

Added a read-only monthly overview with one coherent period/job projection,
minimal employee progress and current available actions. Viewing an unstarted
month creates no row. Published employee discovery uses bounded name/number
search, while snapshot details now include minimal employee and period metadata.
The original daily-fact response fields remain compatible. Closing authority does
not grant employee results or another operator's general job details.

Architecture, Kotlin formatting, 41 workforce domain and 38 worker tests passed;
both application JARs built. The 53 selected API/contract cases passed across the
main check and focused six-case rerun after fixing Jackson array iteration in the
new tests. Coverage includes company-local ended-month policy, minimal output,
partial-result exclusion, pagination, foreign-company rejection, stopped-attempt
recovery, nonblocking worker progress, late MFA expiry and cancellation. OpenAPI
describes both new endpoints. No migration or mobile APK is required.

## Dashboard closing contracts

Added dashboard domain/data contracts for period overview, published employee
discovery, monthly snapshots, closing and recovery. Mappers validate the complete
month, scope, period/job identity, finite progress and historical time evidence.
Factual totals retain worked time, recorded absence, missing attendance and
approved overtime separately. Explicit retries retain the original command and
observed version without a newer review or date replacing them.

Architecture, Biome, TypeScript and all 1,372 dashboard unit tests passed. The 32
new cases cover distinct grants, bounded queries, immutable mapping, malformed
responses, missing versus recorded absence, leap-month length, exact receipts,
later-date retry, scope/version rejection and cancellation. Shared attendance
failure copy now also applies to closing.

Development API and worker images were rebuilt from the validated JARs and
restarted with the document-services and WireGuard overlays. API readiness was UP,
the VPN dashboard returned HTTP 200 and database/Garage/ClamAV remained healthy.
No migration or mobile APK changed. Dashboard closing presentation remains next.

## Dashboard monthly closing

Added month selection, period/progress overview, closing and stopped-attempt
recovery forms, bounded published employee search, and monthly daily results.
Visibility/disposal cancels owned observation; progress has a finite update
budget. Unconfirmed commands retain their original context and operation through
MFA, later conflicts and explicit retry. Revoked read data stays hidden during
recovery. Factual metrics distinguish recorded absence from missing attendance.

Architecture, TypeScript and all 1,403 dashboard unit tests passed, including 31
new ownership/termination/recovery cases. Final Biome checks passed after ordering
the new CSS rules consistently. Eleven new browser scenarios passed; the affected
holiday rejection and final light/dark layout checks were also run. Screenshots
for the overview, closing form and employee result were inspected.

The real API/PostgreSQL/worker browser scenario passed: pending offline evidence
blocked closing, a lost response replayed its original receipt even after worker
failure, independent rejection released the next explicit attempt, and the worker
published immutable facts. It verified month-end employee discovery, original
target progress, a new attempt/version, closed-period write rejection and employee
self access without directory authority. The initial runs corrected test-only
assumptions about the existing failure text and company-roster progress count.
No additional backend build, migration, mobile APK or release was needed for the
presentation change.

## Job assurance after acquisition

Job detail/list reads and cancellation now recheck current session assurance after
acquiring data or recording the cancellation journal. A late failure rolls back the
flag, version, cleanup eligibility, audit and outbox. Repeated acknowledgement
retains the same assurance requirement without adding another mutation.

Architecture, Kotlin formatting, two Jobs domain tests, 19 selected API/database
tests and all 38 worker tests passed. Six new cases exercise proof expiry after
acquisition, rollback, explicit recovery and cancellation, including reuse of the
same executor thread after its interrupted transaction releases its guards.
Both application JARs built. No endpoint contract or migration changed.

## Overtime self-service employment discovery

The minimal Workforce employment directory now accepts overtime self-management,
read and management grants. It still returns only the current account's references
and grants no calendar, profile or other employee access. The final acquisition
also rechecks current session assurance before releasing the page.

Architecture, formatting, 41 Workforce domain and 38 worker tests passed. The 43
selected API/contract cases passed across the initial run and the focused 13-case
directory rerun. New coverage includes overtime-only grants, native build/module
admission, owner isolation, late MFA expiry and interrupted acquisition. The expiry
fixture explicitly enables MFA because the general API setup policy disables it.
Both application JARs built; no endpoint shape or migration changed. The preceding
Jobs fix was deployed to the development API/worker and readiness returned UP.

## Native overtime reads

[HRIS Mobile](https://github.com/fajarxfce/hris-mobile/commit/29eed7a) now exposes
owned overtime requests, month/status filters and request details with the reviewed
schedule, planned/actual/approved time and activity. Independent encrypted list
and request snapshots retain bounded offline reads. Protected-read failures remain
gated after cleanup failure; current scope and finite read leases still apply.

The mobile root checks passed with 805 desktop tests, 21 presentation Chromium
cases and Android dev assembly. The preceding domain/data increment passed 66
desktop and 53 Chromium cases. API 35 emulator acceptance covered real development
requests, overnight dates, filters, offline process restoration and explicit
recovery. A device-discovered hidden refresh message was corrected and covered by
scrolled-layout regressions. After notice, the validated APK was installed on the
authorized Android 16 phone with install-only delivery. This milestone covers
employee reads; native command authoring and recovery remain in progress.

## Native overtime planning preparation

The mobile [preparation boundary](https://github.com/fajarxfce/hris-mobile/commit/c9da3a2)
now obtains the current account's employment and reviewed calendar before a plan.
It retains the server employment/content revisions and bounds review lifetime by
current company/session policy. Application policies own duration, break, work-date,
shift-overlap and actual-time validation; transport mapping remains in data.

Mobile root checks passed with 825 desktop tests and Android dev assembly. Workforce
passed 56 domain and 30 data desktop tests, plus 56 domain and 17 data Chromium
cases. Twenty new cases cover acquisition ownership, cancellation, expiry,
protocol bounds, overnight work, DST day boundaries and actual-time limits. A
separate CI refresh-layout assertion was corrected to check retained scroll and
reachable activity after the viewport changes. This boundary added no device
flow or backend mutation; native durable commands and forms remain in progress.

## Native overtime commands and forms

The mobile [command foundation](https://github.com/fajarxfce/hris-mobile/commit/dd1d70b),
[planning form](https://github.com/fajarxfce/hris-mobile/commit/a66c787) and
[actual-time/withdrawal flow](https://github.com/fajarxfce/hris-mobile/commit/b58caf8)
now retain immutable encrypted input before transport. Manual and worker delivery
share bounded execution, original idempotency keys and observed versions. Uncertain
commands cannot be replaced by another action; confirmed evidence remains until
admitted request facts and revision-checked cleanup succeed. Company-local input
uses explicit timezone/DST validation and the reviewed server schedule.

The final mobile root checks passed with 903 desktop tests, five shared-presentation
and 40 Workforce presentation Chromium tests, Android dev assembly and application
Web compilation. API 35 acceptance created one reviewed plan, restored it offline,
then retained and restored actual time after background process death. Explicit
replay produced 85 worked minutes at version one. A timed-out detail read preserved
its receipt and recovered through Open request. Back from withdrawal changed no
record; explicit confirmation produced version two, one withdrawal entry and a
cancelled approval. API reads verified the original evidence and distinct history.
The development approval template was configured through the normal API.

The typed enum navigation adapter and regressions also returned to
[Compose Fluent Starter](https://github.com/fajarxfce/compose-fluent-starter/commit/76a5030),
where 271 desktop tests, five Chromium tests, Android dev assembly and application
Web compilation passed. After notice, the validated mobile APK was installed on
the authorized Android 16 phone with install-only delivery. No backend code,
migration, runtime deployment, release or physical-device UI interaction was required.

## Employee payslip scope and session assurance

Payslip list, detail, payment-progress and PDF endpoints now accept explicit `SELF`
scope. It narrows company-wide finance grants to owned employments; the default
`AUTHORIZED` behavior remains compatible with existing clients. Current assurance
is checked after resource/access guards and after acquisition. PDF delivery also
reauthorizes after rendering before HEAD/304 metadata and before content reads.

The affected 57 API tests, 38 worker tests, 56 payroll domain tests and five PDF
delivery tests passed. Thirteen new cases cover explicit self scope, conditional
PDF ownership, revocation after rendering, post-guard/post-read MFA expiry,
interruption recovery and the generated API contract. Format and architecture
checks passed; both application JARs built. No migration changed. Native payslip
pages and file export remain the next integration step.

## Native employee payslips

The [mobile payslip workflow](https://github.com/fajarxfce/hris-mobile/commit/46d8b2f)
adds annual history, published earnings/deductions, employer contributions, payment
progress and English/Indonesian PDF saving. All financial reads use employee SELF
scope. Independently encrypted list/detail snapshots have bounded read leases;
known denials remain gated through pending/failed retries and failed cleanup.
Typed MVI handlers own acquisition, year changes and export lifetime. Leaving a
route cancels its work; an Android file dialog's temporary Activity stop preserves
the explicit save operation.

Mobile root checks passed with 959 desktop tests and eighteen targeted Chromium
tests. Android dev assembly and the production Web build passed. Assembled UI
tests cover 200% text, destination selection, cancellation and saving
across a stopped Activity. API 35 acceptance used a payslip published through the
normal development policy, calculation, independent review and finalization APIs.
It verified year filters, exact retained amounts, unset payment status, picker
cancellation, native PDF saving, both themes, offline list/detail restoration after
background process death, and explicit recovery. The 11,099-byte PDF matched the
backend SHA-256. After notice, the validated APK was installed on the authorized
phone with install-only delivery; its UI was not driven.

The exact decimal value and localized formatting also returned to
[Compose Fluent Starter](https://github.com/fajarxfce/compose-fluent-starter/commit/95b16df),
where 276 desktop tests and seven targeted Chromium tests passed. Its complete
CI, including Apple and Windows, passed. Production Web optimization and emulator
sessions now run separately on the shared VPS following an observed host memory
exhaustion; the emulator was restored without clearing application data.


## Native employee expense reads

The [expense read foundation](https://github.com/fajarxfce/hris-mobile/commit/c963e5b)
and [pages](https://github.com/fajarxfce/hris-mobile/commit/a213e1a) add owned employment
discovery, month/status filters, current claim details, frozen receipt metadata,
approval activity and separate payment progress. Composite acquisition checks the
claim version before publication. Independently encrypted list/detail snapshots
use bounded authenticated chunks and atomic generation checks. Known access/MFA
failures remain gated after failed cleanup and during failed recovery.

Mobile root checks passed with 1,004 desktop tests and 36 targeted Chromium tests.
Android dev assembly and production Web compilation passed. API 35 acceptance used
two claims created through the normal development API and independent approval. It
verified filters, draft/approved states, unpaid approval, activity, both themes,
200% text, encrypted list/detail restoration after background process death offline,
and explicit recovery. After notice, the validated APK was installed on the
connected Android 16 phone with install-only delivery. No phone UI, bank transfer,
release or external message was involved. Native draft authoring, receipt actions
and submission remain in progress.


## Employee expense command assurance

Draft creation/editing, cancellation, submission and withdrawal now recheck current
session assurance after resource/access waits, before receipt replay and before
transaction completion. Late expiry rolls back evidence, approval effects, claim
history, audit/outbox and the operation receipt together. Existing permission
intersection, observed versions and command identity remain intact.

Architecture and formatting passed. The 122 existing expense API cases passed;
twenty new transaction/assurance cases and 21 API contract cases passed after
completing the required-receipt fixtures. New cases cover guard/acquisition/journal
expiry, unchanged receipt replay, rollback and cancellation with executor reuse.
Both application JARs built. The worker graph is unchanged and its test task was
up to date. No migration or mobile APK changed in this backend increment.


## Expense cost-center discovery

Added a minimal active cost-center directory for expense editing, independently
of general organization browsing. Name/code search and cursor pages are bounded;
responses contain only ID, code, name and observed version. Shared structure and
access guards retain current company, credential, permission and MFA admission
through acquisition. Selecting a reference reserves no eligibility.

Architecture, formatting, eight new API/ownership/cancellation cases and 22 API
contract cases passed. Checks include literal wildcard search, inactive/foreign
record exclusion, module/native-build admission, late proof expiry, removed/new
grants and executor reuse. Both application JARs built; the unchanged worker check
was up to date. No migration or mobile APK changed in this increment.


## Employee expense draft references

A bounded employee-owned lookup resolves category/cost-center labels and receipt
revision status for draft editing. It is independent of general organization and
document browsing. Unavailable, foreign and wrongly classified references retain
only their requested ID; archived labels remain recognizable. File contents and
storage diagnostics are never acquired. Submission still validates current policy
and evidence independently.

Architecture, formatting, eight new API/access/cancellation cases and 23 API
contract cases passed. Tests cover minimal editing grants, ownership, null metadata,
archived references, cardinality, MFA expiry after locks and metadata acquisition,
permission intersection, and interrupted executor reuse. Expense domain checks and
both application JARs passed; the unchanged worker check was up to date. The initial
fixture was corrected to remove its organization grant and apply active MFA policy.
No migration or mobile APK changed in this increment.


## Native expense draft authoring

The [mobile editor](https://github.com/fajarxfce/hris-mobile/commit/4503540)
creates and updates employee-owned drafts, resolves dated categories and cost
centers, and selects inspected personal receipts. Saves use encrypted immutable
commands and versioned API operations. Conflicting edits retain local input;
connection recovery reacquires authority without requiring input to be discarded.
Pending or uncertain delivery cannot become a different request.

Final mobile checks passed with 1,092 desktop tests, 36 presentation Chromium tests
and Android dev assembly. Initial editor checks also passed 73 expense domain/data
Chromium tests and production Web compilation. CI validated Linux, Windows and the
iOS simulator build. API 35 acceptance exercised creation, exact amounts, receipts,
competing edits, background retention, offline recovery, both themes and 200% text.
The validated APK was installed on the phone without launching it. Submission,
cancellation, withdrawal and receipt export are not yet exposed by the native UI.

## Native expense actions

The [mobile action pages](https://github.com/fajarxfce/hris-mobile/commit/1388947)
submit claims, withdraw pending submissions and cancel drafts. Confirmation keeps
the reviewed claim version. Retained commands prevent competing actions and keep
the original reason and operation during recovery. Back cancels reads and delivery
before route disposal; an uncertain result remains stored.

Final checks passed with 1,108 desktop tests, 49 presentation Chromium tests and
Android dev assembly. The policy integration also passed 52 domain Chromium tests
and production Web compilation. API 35 acceptance retained a pending submission
through offline process restart, replayed it once, withdrew its approval and
cancelled the draft through detail recovery. API reads verified the original reason,
one submission and one history entry per version. Normal/dark appearance and 200%
text were inspected, and emulator transport/display settings were restored.

The validated APK was installed on the authorized phone with install-only delivery.
No phone UI interaction or runtime performance measurement is claimed. Native
receipt export and the administrative expense pages remain separate increments.

## Native expense receipt export

[HRIS Mobile 0c880a6](https://github.com/fajarxfce/hris-mobile/commit/0c880a6)
adds receipt saving from claim detail. The use case owns fresh SELF authorization,
picker/transfer deadlines and output lifetime. The data adapter validates immutable
metadata, sequential ranges and the final SHA-256 without retaining a read-ahead
queue. Stopping for the native save dialog retains the export; Back and disposal
cancel it. Access failures stay gated through failed refresh and cleanup.

Root checks and focused regressions passed with 1,145 desktop tests in the final
suite, 157 expense Chromium tests, 36 network Chromium tests, Android dev assembly
and production Web compilation. API 35 acceptance saved a published PDF with the
server's exact hash, cancelled and retried the picker, and verified both themes,
200% text and return from Settings. The validated APK was installed on the phone
without launching its UI.

The bounded response reader and regressions were also ported to
[Compose Fluent Starter e33d908](https://github.com/fajarxfce/compose-fluent-starter/commit/e33d908).
Its root checks passed with 278 desktop tests, 36 network Chromium tests and Android
dev assembly. Administrative expense workflows remain separate work.

## Expense policy session assurance

Category creation and revision now validate current MFA after their resource and
access guards, before original receipt replay and before transaction completion.
A late failure rolls back category state, immutable revision, audit, outbox and
idempotency receipt together. The operation identity and permission intersection
are unchanged.

Six new regressions passed against PostgreSQL with MFA explicitly enabled. They
cover create/update expiry during guard waits, expiry after journal insertion,
renewed replay and interruption followed by recovery on the same executor.
The existing policy HTTP and expense command/read assurance cases also passed;
all 38 worker tests, architecture and format checks passed. The new fixture uses
enabled business policy independently of the HTTP fixture's disabled-MFA setup.

## Administrative expense policy reads

Added a bounded administrative catalog and coherent policy review. The catalog
includes future and inactive saved heads, supports status filtering and advances
by immutable code. Detail returns the latest saved definition with ascending
immutable revision history. Shared resource/access guards keep head and history
consistent across competing saves; current grants and MFA are revalidated after
waiting and before delivery. Effective policy selection for claims is unchanged.

Across focused runs, 86 API/PostgreSQL cases passed, including 23 OpenAPI contract
checks, five administrative read scenarios and six added read-assurance cases.
The suite covers current/foreign scope, revocation during access acquisition,
concurrent readers, head/history consistency, interrupted recovery and MFA expiry
before result delivery. Twelve expense domain tests, architecture and formatting
passed; both bootJars built. The initial new tests required corrections for
Jackson array iteration and an error response's echoed request path. Dashboard
interaction is a separate increment.

## Expense policy dashboard

Added the administrative category directory, saved-definition details, ascending
revision history and create/edit forms. Future and inactive policies remain
visible without changing effective-date selection for claims. Limits retain exact
decimal IDR values; policy codes and observed versions are protected. An uncertain
save keeps one operation and payload through explicit recovery. Definite conflicts
require a protected reload, and company replacement cancels obsolete reads.

Dashboard architecture, formatting, TypeScript and 1,444 unit tests passed, including
41 expense boundary/controller cases. Nine Chromium scenarios passed for filters,
history, exact amounts, validation, uncertain recovery, conflicts, company
replacement, access denial and retained input in Indonesian dark/narrow layout.
The real API/PostgreSQL scenario created a policy, scheduled an inactive revision,
dropped its committed response and recovered the same receipt. History contains
one new revision, the old effective policy remains available for today's claims,
and foreign-company review is rejected. Both API guards and the dashboard workflow
run with MFA in their production paths.

Visual inspection led to narrower desktop tables and an explicit revision-focus
offset below the sticky header. Browser checks cover both. The production build
passed with the existing main-bundle size advisory. Development API deployment
includes the policy-save assurance fix and administrative read endpoints; external
distribution and production performance are not claimed.

### Coherent expense claim review

Added a bounded claim review endpoint combining the current draft, descending
action/submission pages and payment progress. Shared aggregate guards keep these
reads consistent across writes; ordinary claim authority does not expand through
approval assignment. Payment responses exclude bank destinations and references.
Resource-dependent cursor validation follows authorization, acquisition failures
remain failures, and session assurance is rechecked before releasing the result.

Architecture and formatting checks, 79 API/PostgreSQL tests, the twelve expense
domain tests and the API boot JAR task passed. New checks cover concurrent shared
readers versus a draft save, descending pagination, assignment-only and foreign
scope denial, payment projection, revocation, expiry during acquisition and after
the final read, interruption cleanup and failed aggregate acquisition. Existing
mobile-compatible endpoints remain available. No additional physical-device
validation is claimed by this endpoint.

## Expense claim dashboard

Implemented company claim directories, current draft/history/submission/payment
review, immutable submitted evidence and receipt downloads. Approval source links
resolve submission IDs. Submitted category snapshots remain independent of later
policy revisions. Exact decimal formatting, bounded pagination, account/company
scope and cancellation are retained across the feature's layers.

Dashboard architecture, TypeScript, production build and all 1,499 unit/component
tests passed. Eight mocked Chromium scenarios passed for filtering/pagination,
tab retention, receipt cancellation/retry, revoked access, company replacement,
malformed responses and English/Indonesian light/dark layouts. The real
API/PostgreSQL browser scenario passed with MFA enabled: it verifies submitted
policy immutability, history, assignment-only review, foreign-company denial and
updated approval progress after an API decision. It does not claim that a dashboard
decision command is implemented. Current claim tabs do not trigger another review
request. Screenshots were inspected at desktop and 760-pixel widths.

The development dashboard also downloaded a published receipt through the real
WireGuard API and private object storage. Its 698 bytes and SHA-256 matched the
submitted metadata; switching claim tabs issued no additional review request.
The temporary browser session was closed after the check. This was a browser
acceptance check, not a new phone installation.

The existing production bundle-size advisory and two unrelated CSS specificity
warnings remain. Expense test fixtures no longer use non-null assertions. Draft
authoring, review commands and payment administration remain subsequent work.

## Expense decision session assurance

Approval, rejection and return now recheck current MFA after ordered account
guards, before original receipt replay and after journaling. A late expired proof
rolls back the decision, expense review, claim history, approval transition,
audit/outbox and operation receipt together. Explicit retry retains both observed
versions and the original operation.

Architecture, formatting, twelve expense domain cases, the API boot JAR and 71
API/PostgreSQL cases passed. The 32 command-assurance cases include expiry during
guard acquisition, expiry before receipt return, expiry after journaling, and
interruption followed by recovery on the same executor. Existing independent
review, duplicate evidence, revocation, concurrent decisions and API contracts
also passed. This change affects only expense composition in the API; it does not
modify the worker dependency graph.

## Guarded expense submission actions

Submission details now report ordinary claim-read access separately from current
decision availability. Only a pending latest submission's independent active stage
exposes `DECIDE`; maker/beneficiary exclusions, live delegator grants and explicit
SELF scope remain authoritative in domain. Shared actor/delegator account guards
are acquired in identifier order. Read scope and time-based action availability
are rechecked before delivery; a former delegation cannot release private evidence.

Architecture, formatting, fifteen expense domain cases and API assembly passed.
Six new API/PostgreSQL cases and 103 existing regression cases passed, covering
current versus historical stages, reassignment, both observed versions, ordinary
versus delegated reads, expiration during acquisition, and account revocation
before/during guarded reads. The expiration fixture uses PostgreSQL's persisted
microsecond boundary. Existing maker/current-beneficiary HTTP scenarios also
verify absent actions. Command authorization remains independent of this read
projection; the dashboard decision workflow is the next increment.

## Expense decision dashboard

Assigned reviewers can approve, reject or return an expense submission. The form
uses the server's current action projection, keeps both observed versions and
requires reasons for rejection/return. Approval with current duplicate signals
requires acknowledgement and an explanation. Receipt acquisition has a separate
owner shared with submission details; inspecting or downloading evidence does not
navigate away from the form or discard input.

One captured operation survives response loss, MFA recovery and later retry
rejections during the editor lifecycle. A definite conflict requires an explicit
fresh review before another command. Access loss removes evidence without treating
an in-flight write as rejected. Repeated presses, company replacement, disposal and
late results are covered; input and unconfirmed operations have navigation protection.

Dashboard architecture, formatting, TypeScript, production build and all 1,528
unit/component tests passed. Nineteen expense browser scenarios passed, including
capability gating, intermediate approval, all three decisions, duplicate review,
receipt download and revocation, original-operation recovery, reassignment,
company replacement and locale/theme retention. Initial assertions were corrected
to match the existing stage table and the backend's rejected approval attempt for
a returned claim. The final real API/PostgreSQL browser scenario passed with MFA:
a committed approval response was dropped and replayed with one recorded review;
another claim was returned, revised, resubmitted and rejected. Its earlier evidence
and return review remained immutable.

Desktop light and 760-pixel dark screenshots were inspected. The development
browser also downloaded the real 698-byte receipt with the expected SHA-256 and
no additional claim reads when switching tabs. No phone update was required for
this dashboard increment. The existing bundle-size advisory and two unrelated
CSS specificity warnings remain. Draft authoring and finance administration are
subsequent expense work.

## Expense payment assurance at completion

Finance reads now verify recent authentication after their final acquisition,
including bank destinations, reconciliation history and export instructions.
Preparation, release, cancellation and reconciliation check assurance again before
returning an original receipt and after the journal write. The same policy covers
recent MFA and deployments that use recent password authentication. A failed read
keeps its original error classification; interruption still propagates.

Architecture, formatting, fifteen expense domain tests, the API boot JAR and 54
API/PostgreSQL cases passed. The 34 new cases cover expiry after acquisition and
journaling, both new commands and original receipt replay, complete rollback of
payment/item/reference/history/journal state, and interrupted transactions followed
by recovery on the same executor. Existing payment bounds, reservations, independent
operators, concurrent writes, partial reconciliation, access revocation and shared
read tests passed. This changes only expense domain behavior in the API; the worker
dependency graph is unaffected.

## Coherent expense payment review

Added a finance-only batch review that acquires instructions, bounded ascending
history, reconciliation records and action availability under one shared aggregate
and access guard. The projection distinguishes cancellation, independent release,
independent reconciliation and export before any item is reconciled. Commands
retain their own authorization and version checks. History paging returns a new
coherent current batch without mixing separate endpoint versions.

Architecture, formatting, nineteen expense domain tests and API assembly passed.
Sixty-four payment API/PostgreSQL cases and twenty-three API contract cases passed.
Ten new cases cover partial/complete settlement, cursor bounds after authorization,
ordinary and foreign scope denial, beneficiary exclusions, independent concurrent
readers with a blocked release, access revocation during acquisition, late MFA or
authentication expiry, failed acquisition and interruption cleanup. The worker
does not import expense composition. The payment dashboard remains subsequent work.

## Expense payment dashboard reads and worksheets

Finance operators now have filtered batch directories and a coherent detail view
with bank instructions, reconciliation outcomes and ascending activity. Tab,
language and theme changes reuse the current review. Each page has explicit
refresh; an access or export-eligibility rejection hides the observed bank data
until a fresh review succeeds. Account/company replacement and disposal cancel
owned acquisition and discard inactive query entries.

The existing file repository saves a bounded CSV response with its original
leading-zero account values and stable payment references. The UI exposes export
only while the server permits it. Currency amounts stay decimal, including a
hundred maximum-sized claims whose batch total exceeds the individual claim limit.
DTO mapping rejects inconsistent totals, outcomes, history and action metadata.
Tables mask account numbers, while the selected payment panel shows the complete
instruction and any confirmed bank outcome.

Dashboard architecture, formatting, TypeScript, production build and all 1,591
unit/component tests passed. Sixty-three new cases cover boundary validation,
exact totals, cancellation, disposal, stale responses, explicit retry and bank-data
removal. Twelve Chromium scenarios passed for filters/paging, tab reuse, worksheet
bytes, cancellation/retry, changed access/eligibility, company replacement and
English/Indonesian light/dark layouts. Desktop and 760-pixel screenshots were
inspected.

The real API/PostgreSQL browser scenario passed with four independent accounts
and MFA. It prepared and released two approved claims, downloaded the expected
CSV, recorded a successful transfer and a separately confirmed no-transfer result,
and verified partial/closed status and four immutable activity entries. A finance
reader could not read the batch through another company; permission revocation
removed its displayed data. Ordinary expense administration did not grant finance
access. The fixture owned its database and did not execute any bank transfer.

The development dashboard remains available through WireGuard. No phone update
was needed for this dashboard increment. The existing bundle-size advisory and
two unrelated CSS specificity warnings remain. Preparation, release/cancellation
and reconciliation entry in the dashboard are subsequent work.

## Expense payment release and cancellation

Prepared batches now open a separate release/cancellation review from their
current server capabilities. Full destinations and amounts are visible before
confirmation, and both actions require a reason. The client captures one immutable
versioned command. A lost response preserves its operation and input through
explicit retries; later MFA, permission or conflict failures cannot turn an unknown
first outcome into a new command. Known initial conflicts require a fresh review.

Payment inspection stays local to the form. Navigation protection covers dirty
input, pending writes and unconfirmed results; account/company replacement and
disposal release owned work. Access or independence rejection removes bank data.
The ordinary detail reader and worksheet retain their separate ownership.

Dashboard architecture, formatting, TypeScript, production build and all 1,619
unit/component tests passed, including 28 new boundary/lifecycle cases. Twenty
Chromium scenarios passed: eight transition workflows and twelve existing payment
read/download checks. They exercised required input, duplicate presses, local
inspection, capability denial, explicit conflict recovery, response loss across
MFA verification and later rejection, company replacement, and language/theme
retention. Light and 760-pixel dark layouts were inspected; the layout scenario
was rerun after correcting the screenshot's scroll position.

The real API/PostgreSQL browser scenario passed with separate MFA-enrolled
operators. The batch maker cancelled an initial batch through the dashboard;
its claims then entered a replacement batch. An independent operator released
that batch, its committed response was dropped, and the original operation was
replayed with one release entry. Existing CSV, partial/final reconciliation,
foreign-company denial and finance revocation acceptance remained successful.
No bank transfer or phone installation was performed for this increment.

## Expense payment reconciliation entry

Finance can stage confirmed bank results in an item panel and submit them together
against one reviewed batch version. Payments without a result remain pending.
Success requires a bank reference; failure requires explicit confirmation that no
funds were transferred. Company-local time entry rejects invalid/ambiguous values
and preserves unchanged timestamp precision. The API remains the authority for
current eligibility, time bounds and transaction-reference uniqueness.

The page owns navigation protection for both the batch note and item panel. Local
editing does not reacquire data. Confirmation freezes all outcomes and one operation
ID; response loss retains that command through MFA renewal and subsequent denied
retries. Initial definite conflicts require review and re-entry, while input
rejections remain correctable. Access rejection hides bank details. Disposal and
scope replacement abort requests and discard late results. Browser drafts remain
in memory for the editor lifecycle.

Dashboard architecture, formatting, TypeScript, production build and 1,664
unit/component tests passed, including 45 new boundary, lifecycle and time-input
cases. Nine reconciliation Chromium scenarios passed across the final suite and
targeted validation rerun; twenty existing payment read/transition scenarios also
passed. Coverage includes multiple outcomes in one command, required no-transfer
confirmation, local correction/removal, unsaved panel departure, duplicate presses,
lost responses across MFA/rejection, scope replacement and language/theme retention.
Light desktop, 760-pixel dark and real-API result panels were inspected.

The real API/PostgreSQL scenario passed with independent MFA-enrolled operators.
Release and reconciliation each survived a deliberately dropped committed response
using their original operation. The first result left another payment pending;
confirmed no-transfer failure closed the batch. Stored history/results, CSV
eligibility, foreign-company denial and finance revocation remained correct.
The fixture owns its database; no bank transfer or phone update occurred.
