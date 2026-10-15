# Architecture

## Deployment and modules

One modular monolith serves a single organization group with multiple companies. API and worker roles share code and PostgreSQL. The React application consumes the same versioned API as the future Compose client.

Business modules own domain, data, and delivery projects. Domain publishes entities, repository contracts, use cases, and pure policy without framework dependencies. Data implements datasource contracts, mapping, repositories, and DI. Delivery owns HTTP and job entry points. The application composes modules.

Use cases may depend on repository contracts from other modules. Datasources never depend on datasources, repositories never invoke repositories, and use cases never invoke use cases. Shared approval transitions are pure policies, not a universal business orchestrator.

Workforce owns schedules and factual attendance; leave owns absence decisions. Reporting and payroll combine those facts. Identity uses stable company/person identifiers without depending on their implementation. These directions prevent cycles. Payroll owns scoped read projections for immutable workforce evidence. Its datasources read raw records; repositories map them into payroll domain references. The originating module retains write ownership. This permits payroll to consume factual snapshots without importing another module’s data implementation or moving permission/period policy into a datasource.

Leave calendar readers take the shared People guard before company, membership
and account guards. The use cases select current direct-report or company scope
and own date/access policy. A Leave-owned raw projection reads active allocations
and submitted request labels in one SQL snapshot; it does not load private
profiles or coordinate another datasource. Repository acquisition and mapping
share the database failure boundary. Month summaries and day pages are separate
live reads. Both revalidate session assurance after acquisition. The dashboard
calendar controller owns and cancels each read independently; selecting a date
retains the month. Hidden workspace reads stop and reacquire after admission.
Presentation owns validated return context, without adding navigation fields to
the repository query or domain entities.

## Runtime

JDK 21, Spring Boot 4.1, Kotlin 2.3, Spring MVC, PostgreSQL 18, Flyway, and jOOQ. Gradle conventions own toolchain and quality configuration; the version catalog owns versions. jOOQ is generated from migrations against PostgreSQL. OpenAPI is reviewed and validated in CI.

## Transactions and failures

Domain Result carries typed failures with stable codes, field codes, and safe unlocalized parameters. HTTP adapters expose one RFC 9457 problem contract; client applications own translation and retry decisions. Stateless safeDatabaseCall and storage/SDK-specific boundaries map technical exceptions once, preserve interruption/cancellation, and do not retry. False/null outcomes have operation-specific semantics.

TransactionRunner is a domain port implemented using Spring transactions. A failed Result marks rollback. Request-scoped company context is installed using SET LOCAL and does not leak through pooled connections. Runtime credentials cannot bypass RLS; migration credentials are separate. Business transactions set local query (15 seconds), lock (5 seconds), and idle-transaction (30 seconds) timeouts with a 30-second transaction budget. A late interruption before commit rolls back and propagates cancellation. Mapping failures become sanitized data failures; internal diagnostics retain SQLState and application call frames, never SQL text or bound values. Framework batch-error logging that includes SQL is disabled in both application roles. HTTP diagnostics retain a bounded cause-category chain and application frames without exception messages; wrapped cancellation/interruption propagates.

An explicitly injected CrossCompanyTransactionRunner supports atomic employment transfers. It installs one secondary company for a bounded transaction, with RLS policies limited to transfer inputs, employment writes, and audit/outbox inserts. Ordinary transactions explicitly clear that secondary scope. Transfer use cases lock companies/memberships in stable order and recheck live access to both companies before mutation or replay. This is not a global RLS bypass.

Transfers capture destination authority before command/resource waits and intersect
both companies' original and live grants afterward. Shared client-policy guards are
acquired in company-ID order before people, destination structure, and access guards.
Maintenance, minimum builds, People availability, credentials and MFA are checked
for both companies before mutation or receipt replay. The secondary scope adds only
SELECT access to client settings; it cannot modify them or read a third company's
settings. Transfer-history reads also recheck session assurance after their guards.

Transfer review is a separate read use case with a shared source people guard.
It returns both server-derived company dates and the request's intersected
destination permissions, without calling the transfer use case or reserving
eligibility. Destination pickers authorize their own scoped reads. The command
rechecks all current conditions and its source version independently.

CompanyReadTransactionRunner provides a separate, explicit SELECT scope for group reporting. A global actor may select one to 32 companies; the use case must authorize every selection before reading report inputs. Policies are limited to employment history and company client settings, with no additional write scope. Company policy, company, membership, and account guards are acquired in that order, with company IDs sorted within each stage. Original and current grants, credentials, MFA assurance, and per-company client availability are checked after waiting. One SQL snapshot computes global and per-company counts without loading profiles. Every other transaction owner clears the read selection, and transaction-local context is discarded on success, failure, or cancellation.

Audit search belongs to administration. Its datasource selects an explicit metadata projection; reasons and change payloads are not acquired. Repository mapping and finite overfetch pagination share the database failure boundary. The use case owns time/filter policy, cursor compatibility, and current audit access/MFA checks. Immutable cursor records permit stable descending timestamp/ID navigation without blocking audit writers, while separate pages remain live reads. This history view does not substitute for commit-ordered mobile synchronization.

When a workflow needs both organization structure and company guards, acquire structure first. Employment transfers take the ordered people guards, destination structure, then ordered company/membership guards. Organization changes use the same structure-before-company order; a transfer must not hold a company guard while waiting on a structure writer that needs that company.

Lifecycle case operations acquire people guards before case locks, followed by company, membership, and ordered account guards. Template operations acquire the template guard before access guards. Shared case reads cannot hold access guards while waiting for a writer that needs them. Multi-query aggregate reads need their resource guard; paginated case/task projections instead use one bounded SQL snapshot.

Scheduled offboarding retains a separate, immutable authorization and existing
fixed-total background job. Its use cases own admission, departure policy,
cancellation and terminal-job recovery. Worker/cancellation/recovery acquire the
job before People, case and access guards. Bounded reads join schedule/job metadata
without taking job locks. Execution matches the locked request and captured security
version, then rechecks current authority and reviewed sources. Interactive proof
age does not authorize background execution. Employment, scoped access, case,
schedule, job progress and journal commit atomically; interruption or stale work
rolls back. Queue-wide recovery gains no additional People RLS scope. See
[scheduled offboarding](offboarding-schedules.md).

Scheduled onboarding uses the same queue with a distinct authorization and job
kind. Its admission captures the effective employment, immutable template revision,
company-local dates and a bounded assignee selection. Execution creates an open
case with pending tasks; it cannot complete tasks, activate accounts or modify
employment. Job, People, template and access guards precede current-source and
credential checks. A manual open or completed case prevents repeated initiation.
The new case, initial history, authorization outcome, checkpoint and journal share
one transaction. Retained template definitions stay in the existing revision
history. See [scheduled onboarding](onboarding-schedules.md).

Approval aggregate reads acquire the shared approval guard before company, membership, and actor-account guards. They observe headers and assignment overrides coherently; mutations retain the exclusive approval guard. Both readers and administrative commands revalidate live session assurance after waiting, including before returning an existing operation receipt.

Interactive work-calendar reads and commands also revalidate current session
assurance after schedule, applicable period/people, and access guards. A proof
that expires while waiting cannot authorize calendar data, a mutation, or receipt
replay. This uses the identity domain's pure policy with an explicit clock and
security configuration; background execution retains its separate authority.

Attendance capture, capture windows, verification, corrections, daily reads and
correction history apply the same interactive assurance policy after their guards.
An original receipt retains its data but still requires current session assurance.

Canonical mobile attendance days acquire the shared day guard before people,
company, membership and account guards. They reread the projection head after
waiting, combine captured evidence with the latest correction under that guard,
and revalidate session assurance before releasing the result. Account ownership
narrows administrative read permission. Storage triggers maintain the separate
daily sync version; event and correction command versions remain unchanged.

Owned calendar source reads use the shared calendar guard before people and
access guards, then recheck live session assurance before releasing their data.
Repositories map stored assignment snapshots, roster rows and holiday metadata;
the read use cases enforce account ownership. Schedule heads identify the latest
immutable assignment per effective date. Roster IDs and company-scoped holiday
changes share the existing transactional publication pipeline, with a separate
explicit company audience for holiday metadata. Shared holidays require owned
employment; they never widen access to another employee's schedule.

Interactive monthly attendance closing, recovery, period lists and published
snapshot reads use the same post-guard session policy. They recheck assurance
after acquisition and before returning private results or completing a write.
A late failure rolls back period/job changes, targets, journal and receipt together.
Background closing retains its separate initiating-credential and lease authority.

Payroll policy review holds the shared company payroll guard before company,
membership and account guards. Current configuration, the selected month's
effective policy and bounded revision history remain coherent under concurrent
edits. Interactive policy and tax-rule reads revalidate current session assurance
after waits; acquired policy results are checked again before delivery. Policy
saves retain their recent-authentication and MFA requirements through receipt
replay and transaction completion, rolling back the revision, journal and receipt
together when assurance expires during acquisition or journaling.

Compensation review takes the shared payroll guard before people and access
guards. A minimal employment reference includes its independent command version;
current salary, the selected month's salary and history share the same read.
Payroll owns the read projection of employment identifiers and labels, while
People retains all writes. Lookup never returns private person fields or requires
unrelated People administration grants. Compensation reads revalidate assurance
after acquisition; salary saves also retain recent assurance through journaling
and receipt completion. The bound beneficiary cannot change or replay their own
compensation, and the review exposes that capability without disclosing account IDs.

Payroll compensation and period repositories share a raw, minimal employee
projection datasource. Neither repository calls another repository or datasource
through a peer. Period discovery has its own use-case authorization; compensation
read grants remain independent. Period detail acquires shared payroll, People and
access guards before reading current participant labels and input heads. Session
assurance is checked again after reads and through period command completion.

Input review reuses that raw employee projection through its own repository.
Its monthly discovery combines employee references with nullable input heads in
one bounded SQL query. The review use case acquires shared payroll, work-period,
People and access guards, then coordinates input/source/cutoff repositories.
Domain policy derives action hints from current source versions and maker/checker
rules. Legacy input reads retain the same post-acquisition session check; input
commands preserve recent assurance through replay and transaction completion.

Opening tax-history discovery uses the shared minimal employee datasource and a
year-specific head projection without acquiring private profiles. Its review use
case holds shared payroll, People and access guards while combining employment,
current opening, bounded history, historical authors and calculation cutoffs.
Interactive opening reads revalidate session assurance after acquisition; commands
retain recent assurance through original receipt recovery and transaction completion.
Missing opening evidence remains distinct from an explicit zero declaration.

Calculation preparation holds shared payroll, work-period, People and access
guards. Its read combines period state with minimal closed-work and effective-policy
references; it does not reserve queue capacity or acquire full employee facts.
Run reads expose current period versions and recovery hints under the payroll
guard. Interactive reads recheck session assurance after acquisition, and start,
resume and abandon retain recent assurance through receipt replay and transaction
completion. Finalized runs cannot be abandoned.

Aggregate payroll review preparation reads retained summary totals and current
run/review heads under shared payroll and access guards. It does not acquire full
employee calculation payloads or create an approval snapshot. Detail readers
also hold the shared approval guard and lock actor/delegator accounts in identifier
order. Stage eligibility uses the same pure approval policy as decisions; current
company account labels are projected separately from immutable actor IDs without
returning account email or grants. The bounded party set never becomes a directory
search. Reads revalidate session assurance after acquisition. Submission, decisions
and withdrawal retain recent assurance through journal and receipt completion;
delegated decisions also recheck delegation validity before committing.

Monthly closing overview projects the period and minimal job progress in one SQL
snapshot under shared access guards. It neither creates period rows nor holds
resource locks that exclude the worker. The use case chooses available actions
from current grants, state and company-local date. Published employee discovery
holds shared people/access guards and acquires only display references belonging
to the closed attempt. It does not fetch profiles or snapshot bodies. Both reads
revalidate current assurance before releasing their result.

Overtime reads and commands apply the identity session policy after their resource
and access guards. They check current assurance again before releasing private
results or completing a transaction, including receipt replay. A late failed
check rolls back business state, history, approval effects and the operation
receipt together. Detail readers share the employee and approval guards while
mutations retain exclusive ownership; cancellation releases transaction resources.

Overtime planning discovery acquires shared people and access guards. Its separate
review acquires the shared schedule guard before people and access guards, then
returns the minimal employment reference and resolved day. Use cases own date,
employment, current scope and period-availability policy through existing repository
contracts. Review only reads an existing period; it never creates one or reserves
eligibility. Assurance is rechecked after acquisition, and the plan command
independently authorizes and validates its write under its mutation guards.

The overtime planning content revision is a pure domain precondition, derived from
scope, employment version, company timezone and immutable calendar references.
The command compares it after acquiring the schedule guard and resolving current
facts. Original receipts precede that comparison; the optional precondition is
included in operation identity only when supplied, preserving older receipts.

Payslip list, detail and payment-progress reads revalidate current session assurance
after their guards and again after acquisition. Explicit `SELF` scope narrows
company-wide finance grants to owned employments without granting new authority.
PDF rendering runs outside database transactions; the delivery adapter reauthorizes
after rendering before conditional metadata and again before content is released.

Payroll finalization discovery has an independent, bounded run-header projection;
the finalization grant does not widen input, employee or payslip read grants.
Preparation holds shared payroll, people, approval and access guards while reviewing
approved references and publication readiness. Job status is read without a job
lock, retaining the worker's job-before-payroll acquisition order. Preparation is
advisory and performs no writes. The command revalidates sources and observed
versions under its own guards. Finalization reads check session assurance after
acquisition; admission retains recent assurance after queue waits and through the
job, finalization, journal and operation-receipt transaction.

Amendment preparation and preview acquire shared payroll, People and access
guards in that order. A bounded payroll-owned projection reads the complete
published person/year with historical opening and calculation evidence; its
datasource performs only SQL acquisition. Repository mapping checks scope and
stored consistency inside the database error boundary. The use cases own
amendment authority, beneficiary exclusions and complete-year preconditions.
Pure policies assemble proposed payroll remuneration and reuse monthly tax rules
in chronological order. Preview cannot replace verified source ownership and
creates no persistent draft, approval or payment. Session checks follow waits
and acquisition; preview retains recent authentication through calculation.

Saved amendments acquire the operation receipt guard before exclusive payroll,
shared People and access guards. Use cases select and validate reviewed source
versions, retain pure calculation outcomes, and coordinate the immutable revision,
calculation evidence, journal and receipt transaction. Repositories map raw stored
snapshots within the database boundary; metadata queries do not deserialize entire
proposals or results. A read filter explicitly excludes the requesting beneficiary.
Current authority and assurance are checked after waits and before returning data
or committing a command, including receipt replay. SQL constraints protect the
revision sequence, original identity, complete chronological result set and exact
signed differences. Failed rule evaluation is retained; interruption and technical
failure roll back. Private drafts do not advance employee synchronization or
change published payroll and payment evidence.

Amendment review holds exclusive payroll, shared People, exclusive approval and
shared access guards after its operation lock. Review readers use shared aggregate
guards; actor and delegator account locks are ordered. Use cases coordinate the
retained calculation, independent approval, review changes, draft revision and
journal/receipt transaction. Repositories never invoke another repository. SQL
constraints independently couple the business and approval evidence. Historical
preparers and original/current beneficiaries remain ineligible, including through
delegation. Submission and approval recheck the complete source year; retained
reads, rejection, withdrawal and authorized receipt replay do not replace it.
Recent assurance and current delegation validity are retained through completion.
Approval remains separate from revised publication and employee synchronization.

Assessment identities retain an immutable revision ledger and a separate current
head. Original finalization records revision zero in the same transaction as its
assessment; backfill does not rewrite retained results or emit new sync events.
Ordinary calculation targets capture the predecessor's ID and revision. Domain
continuity and database arithmetic checks use that exact revision, while publication
readiness compares the current head under the payroll guard. A history read cannot
substitute a newer revision or fall back to revision zero when one is missing.
Amendment publication independently authorizes each later assessment revision.
Admission holds the operation, payroll, payment, People, approval, access and
queue guards. The worker owns its fenced job before acquiring those resource
guards, rechecks the exact approved basis and full source year, and commits all
revisions, current heads, THR ownership, employee invalidation and job outcome
atomically. A stale lease or substituted initiating proof cannot publish. Generic
queue leasing does not inspect private company rows; terminal/progress validation
runs within the scoped publication transaction.

Failed execution restores the exact approved basis without replacing its review
or calculation. Explicit recovery reads the immutable job pointer, locks that job
before payroll, and retains current operator authority through its receipt/journal
transaction. An exhausted job creates no implicit recalculation or new attempt.

Settlement compares current entitlement with immutable successful transfers and
confirmed returns. Payment commands serialize with publication through the payment
guard, retain observed assessment/payment versions and freeze each instruction's
amount. Confirmed returns have separate scoped evidence and a shared bank-reference
registry; they never become negative instructions. The independently synchronized
payment projection carries its own entitlement and assessment revision. PDF
acquisition and post-render authorization retain the selected statement revision.

Finance return review coordinates candidate, payment-progress, beneficiary and
retained-return reads under the shared payment, People and access guards. A return
or amended publication cannot interleave those facts; concurrent readers share the
guard. Domain policy derives advisory blockers and the earliest confirmation time.
Confirmed bank outcomes require their release/resolution timestamps at the data
mapping boundary. The review rechecks current assurance after acquisition and
does not reserve a balance or change original-command recovery.

Amendment acquisition selects the current head through a security-invoker evidence
view; it never falls back to original results. The repository maps its raw rows
inside the database failure boundary. Recalculation owns the chronological tax
policy and retains the effective predecessor revision separately from the original
run target. Database checks preserve the source facts, exact opening and cumulative
arithmetic. Existing calculation JSON and original payroll remain immutable.

Amendment discovery uses the same shared payroll, People and access guards, with
current assurance before and after acquisition. Its datasource selects minimal
current-revision metadata and retained employee labels, excludes the actor's
current beneficiary records, and applies bounded annual filtering and keyset
paging. The repository validates scope, ordering and mapping inside the database
failure boundary. Discovery neither acquires full calculations nor grants
payslip read authority; application policy stays in its use case.

Employee expense draft saves, cancellations, submission and withdrawal check
current session assurance after resource/access guards, before receipt replay,
and before transaction completion. Late expiry rolls back claim state, immutable
evidence, approval effects, history, audit/outbox and the operation receipt together.
Original input and observed versions remain unchanged during explicit recovery.

Expense approval, rejection and return also revalidate current session assurance
after their ordered account guards, before receipt replay and after journaling.
Expiry before transaction completion rolls back the approval transition, review
evidence, claim history, audit/outbox and operation receipt together.

Expense category-policy saves apply the same session-assurance fence after their
policy/access guards and before receipt replay or transaction completion. The
final check participates in the category, revision, journal and receipt transaction.

Expense claim review acquires the shared company expense guard before shared
people and access guards. Claims, policies and payments use that same aggregate
guard, so its bounded current-draft/history/submission/payment read cannot mix
versions across a concurrent mutation. The use case coordinates claim and payment
repository contracts directly, preserves each acquisition failure, and rechecks
current session assurance before returning. Submission-only approval authority
does not grant access to this draft aggregate; submitted evidence has its own
scoped endpoint. No object-storage I/O occurs inside the review transaction.

Submission review holds shared actor/delegator account guards in identifier order.
Its domain policy derives current-stage actions separately from ordinary claim
read access. Credential/MFA validation follows the ordered guards; delegated read
scope and action availability are checked again before delivery so elapsed
delegation validity cannot release stale authority. Commands retain their own
independent current-scope and observed-version checks.

Company attendance exceptions use one bounded SQL projection under shared people,
company, membership and account guards. They do not acquire employee/day locks:
evidence, reviews and correction heads come from the same statement snapshot,
while independent attendance writers may proceed. The use case rechecks assurance
after acquisition and again before releasing the page. Repository mapping and
finite overfetch share the database failure boundary. Cursor scope and date-range
policy belong to domain; the projection exposes only employee display references
and factual exception counts, without granting team readers a company directory.

Leave request details acquire the employee ledger and cutoff guards, then shared people, approval, company, membership, and actor-account guards. Request history and effective approvers remain coherent while reassignment waits. Leave directories acquire shared people and access guards before checking current employee scope. Request submission, decisions, withdrawal, cancellation, and these readers revalidate current MFA after their guards; an earlier proof cannot authorize a later mutation or receipt replay.

Leave attachment metadata and content use shared people, approval, and access guards. Content is read from storage outside the SQL transaction and reauthorized afterward, including current MFA age. A proof that expires during a guard or storage wait cannot release metadata or bytes. Read guards continue to serialize against approval mutations without excluding other authorized readers.

Leave policy administration acquires the shared policy guard before company, membership, and account guards. One current-head/history review remains coherent across its queries; concurrent readers share the guard and writers retain exclusive ownership. Effective type selection uses the same access/assurance fence while preserving date-based selection. Policy saves revalidate current MFA before mutation or receipt replay. Administrative history requires `leave.manage`, separately from the broader effective-type lookup used by employees and approvers.

Leave submission takes the employee ledger guard, then shared schedule, cutoff,
shared leave-policy, people, document/approval and access guards. The cutoff and
other existing mutation guards retain their original exclusive mode. A preview
content revision compares reviewed policy, schedule and balance facts under those
guards before any business write. It is a pure domain precondition; authorization
and idempotency remain independent. Original receipts replay before current-plan
comparison, so a later policy/calendar change cannot invalidate a committed retry.

Employee leave preparation acquires the shared ledger and schedule guards, the
existing cutoff guard, then shared policy, people, company, membership and account
guards. The order preserves schedule-before-period and policy-before-people
ownership. Its use case reuses pure submission policies and returns current facts
without reserving a balance or constructing an approval. The employee route never
broadens ownership through an administrative grant.

Administrative leave preparation uses that same domain use case with an explicit
scope and `leave.manage`. Minimal employee and published-evidence discovery also
belong to Leave use cases coordinating People and Documents repository contracts;
they do not grant general profile or file access. Shared resource/access guards
retain scope, and all three reads recheck session assurance after acquisition.

Interactive leave balance, ledger, and entitlement reads revalidate current session assurance after resource and access guards. Independent adjustments, entitlement posting, and year closing use the same post-acquisition check before mutation or original receipt replay. A proof that expires while waiting cannot authorize the later result; cancellation unwinds the transaction and releases its guards.

Batch entitlement preparation acquires shared policy, People and access guards.
Its type discovery requires the selected posting/closing capability rather than
policy administration. Review returns the effective policy and an owned, bounded
employee selection; submitting those IDs cannot silently include later hires.
Batch details acquire job, batch, shared policy, People and access guards, then
project names/numbers for the returned outcome page only. Recovery hints use
current authority and retained attempts. Interactive batch reads revalidate
session assurance after acquisition; admission/resume retain it after queue waits
and through journaling and receipt completion. Background execution keeps its
separate initiating-credential and lease authority.

Accrual schedules reuse that batch and queue ownership. The scheduling use case
coordinates existing repositories to create a finite immutable plan and all
periods atomically. Datasources persist raw rows; the schedule repository maps
bounded metadata and current job progress. Domain policies own recurrence and
company-local due times. Future recovery preserves the original due time, and
worker execution retains the existing independent authorization and lease checks.
See [leave accrual schedules](leave-accrual-schedules.md).

The employee balance directory and ledger acquire a shared employee-ledger guard, then the shared policy, people, and access guards. The directory datasource returns a bounded account/type projection in one query. Its repository maps rows and pagination inside the database failure boundary; the use case owns current employee scope and assurance. Minimal labels do not expose administrative policy revision reasons or require unrelated people-administration grants.

Import recovery and cancellation acquire the previous job before the import header, matching worker order. Import reads protect the header and row outcomes with a shared resource guard before access guards. Privileged commands recheck recent authentication after a pending resource or queue acquisition; an earlier check cannot authorize work indefinitely.

Business changes, audit, outbox, and idempotency receipts share their transaction. Receipt keys bind company or account scope, actor, operation, and payload hash. Account receipts cannot be read through a company scope or by another actor. Repeated identical commands return the committed outcome; key/payload mismatches fail. Optimistic versions reject obsolete updates. A lost response never implies that the transaction rolled back.

## Security

Account, person, employment, company membership, and role assignment are separate concepts. An account can have multiple company memberships. Initial binding to an existing person requires an independent authorized operator and an active company membership. Account row locks protecting credential/access changes use `FOR NO KEY UPDATE` where identity keys remain immutable, so unrelated foreign-key history writes can proceed without a reversed lock dependency. Multi-account commands acquire account IDs in a consistent order. Approval mutation guards follow business-resource locks and precede company, membership, and account guards. Server-side permission includes company and resource scope; permission to administer users does not automatically grant payroll access.

Web uses HttpOnly Secure session cookies and CSRF protection. Mobile uses short-lived access tokens with rotated refresh tokens. OIDC uses verified issuer/subject; provider claims are never trusted from client decoding. Privileged accounts require MFA and sensitive operations require recent authentication. Revalidating an already resolved request may remove permissions but must not add newly granted capabilities without a new request and its assurance checks. The shared company-command access policy applies this intersection; background jobs resolve execution authority separately.

The request actor retains company and platform permissions separately. The union used to decide MFA requirements is not an authorization scope. Revalidation intersects each original scope with its corresponding live grants; company administration cannot stand in for platform administration. Identity exposes an access-specific company guard using the same company-state lock as organization settings, preserving the acyclic domain dependency graph. Role/member operations acquire any template guard before company, membership, and ordered account guards.

Global credential administration takes its administration or provider-subject guard before ordered actor/target accounts. Recent authentication is rechecked after waiting. Account locking reads current access fields in a fresh SQL snapshot after acquiring the row guard; directory projections select versions and permissions together in one bounded query. This avoids both stale permissions after a lock wait and mixed-version list responses.

## Storage and jobs

Private S3-compatible object storage holds documents; the self-hosted distribution uses Garage. Uploads are resumable and bounded, downloads support Range/ETag, and documents are not readable until validation completes. Metadata and authorization stay in PostgreSQL.

Spring Batch provides persistent checkpoints for payroll/import/export. An outbox dispatches external delivery after commit. Jobs are idempotent, bounded, interruptible, and owned by the application lifecycle. Retries are explicit and failures stay inspectable. User-facing job use cases coordinate identity and company repository contracts for live access checks. Cancellation locks the job before company/member/account guards, matching worker lock order. Global worker leasing remains a separate capability boundary.

Interactive job reads and cancellation use the pure company-session policy to intersect original/live authority and check current MFA after waiting. Their domain details contain the resulting available actions; delivery only serializes them and cannot restore a permission from the originally resolved actor. This interactive policy is separate from background execution authority.

Job operations retain the guarded access snapshot and check assurance again after
data acquisition or cancellation journaling. Expiry before the final result rolls
back cancellation state, eligibility, version and journal together. An already
requested cancellation also requires current assurance; it adds no new mutation.

Optional job schedules are immutable UTC instants persisted separately from retry availability. Feature use cases decide scheduling policy and supply the request; datasources only persist it. The first cancellation advances eligibility to the use case's current instant so the worker can release feature resources. Repeated cancellation never advances a subsequent cleanup retry. PostgreSQL prevents reversing cancellation or moving an uncancelled job ahead of its original schedule. Application restart requires no in-memory timer reconstruction.

Payroll finance payment review holds the shared payment guard before People and
access guards. Batch instructions, ascending history, confirmed outcomes and pure
action hints remain coherent while concurrent payment writers wait. Interactive
payment reads repeat current session and recent-assurance validation after their
last acquisition. Preparation, release, cancellation and reconciliation check
again before original receipt replay and after journaling; a late failure rolls
back business state, bank references, mobile progress and the operation receipt.
The review use case coordinates repository contracts, without calling a command
use case or reserving a payment.

Fixed leave-carry expiry is a derived obligation, documented in
[carryover accounting](leave-carryover.md#durable-expiry-and-recovery). Its worker
verifies a lease and immutable origin before taking the employee ledger guard.
An empty-permission actor supplies company/audit scope for that fixed debit;
retirement of the original operator does not renew credit. Outcome, movement,
ledger and checkpoint remain one transaction. Recovery acquires the former job,
origin, company/member/account guards and finally the queue, and rechecks current
HR scope/assurance through receipt completion. The new job cannot change the
cohort or original deadline. Generic cancellation is unavailable for this job
kind. Other workers keep their own live authorization requirements.

## API and clients

Company client policy uses a separate admission transaction after actor resolution and before a business handler. Its use case acquires policy/company/member/account guards and evaluates current availability; accepted requests and durable jobs retain their own transaction/execution ownership. The application maps feature adapters and effective sync selections to capability keys. Client policy never adds authorization or modifies a cursor projection. Configuration revisions, receipts, audit, and events are atomic, and settings recovery remains independently authorized during maintenance.

/api/v1 is the public prefix; company resources use /companies/{companyId}. OpenAPI describes DTOs, error codes, pagination, optimistic versions, and idempotency. Money uses decimal strings; timestamps use UTC with explicit IANA schedule zones. Default/max list sizes are 50/200.

The mobile change feed is scoped by actor/company/permission and covers owned expense claims, leave requests/balances, overtime requests, finalized payslips/payment progress, and an explicitly selected account-owned inbox. Storage triggers capture raw invalidations atomically; a bounded worker publishes positions after commit. Sync use cases hold shared people/company/membership/account guards and a shared publication-head guard for one page, validate live scope, and issue encrypted finite cursors. Datasources do not resolve access policy. Commands carry stable operation IDs; offline attendance remains pending verification. Cursor invalidation and access revocation require replacing the corresponding client partition. See [synchronization](synchronization.md) for pagination, retention, key rotation, and client recovery.

## Frontend

Feature data adapters map transport DTOs. Pure use cases own application policy. Feature controllers/hooks coordinate state, events, effects, and cancellation. Pages render state. TanStack Query keys include account and company; navigation cancels obsolete work. React Hook Form owns form mechanics; validation affecting business remains in the domain/backend.

The dashboard identity controller owns one last-authorized presentation workspace, separately from pending session metadata. Revalidation hides that workspace until both session and company access succeed. Redacted MFA metadata cannot authorize restoration. Equivalent live scope retains the mounted feature; changed credentials, memberships, company metadata, or grants invalidate it. Immutable identity mappings prevent retained scope from changing through shared DTO references. Verification never owns or replays a business command.

Each authorized workspace also owns its navigation blocker and one pending departure decision. Editing hooks register protection state without sharing their form values. Router and header actions use that owner; identity suspension cancels pending decisions, and scope replacement disposes them. A narrow session contract lets feature bindings report invalid credentials/company access to the application owner without injecting a peer controller. Mutation controllers retain one immutable operation/payload until acknowledgement; an ambiguous outcome cannot become editable merely because a later retry is rejected. Native browser prompts and in-memory command retention do not claim durable offline recovery.

Server delivery also owns output representations such as CSV and PDF. The payslip PDF adapter consumes the domain snapshot returned by the existing authorized use case; it does not call repositories, perform payroll policy, or move PDFBox types into domain. Its delivery configuration owns a bounded renderer, and each request closes its document/content resources. Storage publication would be a separate data/application capability, not a hidden renderer side effect.

## Verification

Compile-time project boundaries and architecture checks complement behavior tests. PostgreSQL integration tests cover actual RLS, transactions, constraints, migrations, concurrent writes, and rollback. Payroll uses official golden fixtures and immutable rule versions. UI tests exercise company switching, permissions, lifecycle, keyboard focus, and screenshots. Performance is measured against the documented budget.

Business document references belong to the documents domain contract and are registered by consuming use cases in their business transaction. Consumers hold the company document guard before registering evidence; retention must hold that same guard when checking references and retiring content. The registry does not depend on consuming repositories. Mandatory consumer references also have database constraints, so forgetting registration cannot produce an unprotected committed receipt.

Document retention has its own versioned metadata aggregate. Use cases coordinate retention, document, identity, and audit repositories; no datasource or repository invokes another peer. Policy snapshots and lifecycle evidence are immutable. Upload and retention commands share the company document guard, then lock company, membership, and account access in that order. Archive requires explicit cancellation of unfinished processing rather than hiding that orchestration inside storage.

Interactive document readers take shared people, applicable document, company,
membership and actor-account guards in that order. Aggregate reads retain a
coherent revision snapshot; directories use a bounded query. Read and upload
use cases intersect original/live grants and revalidate session assurance after
waits. Content acquisition holds no SQL transaction and repeats those checks
before returning bytes. Background validation retains a separate execution policy.

Administrative document employee discovery reuses the People repository's bounded
company projection under shared people and access guards. Its Documents use case
requires current document and private-profile read grants and returns only display
references. It does not invoke the People directory use case or require that
unrelated module to be enabled. Self-service discovery retains its independent
owner-only contract.

Document retention and inventory administration also revalidate session assurance
after their shared access guards. Retention snapshots acquire the document guard
before access guards; metadata projections never wait for a job lock while holding
access. Validation/inventory admission retains the guarded identity snapshot and
checks its proof again after the job queue wait, including the stricter recent
authentication requirement for inventory. Expired upload admission creates no job.

Retirement review uses the shared document guard before access guards and reads
only the metadata needed for its independent retention projection. It checks
session assurance again after the revision, retention and business-reference
reads. Its pure eligibility policy also serves the command; review never persists
the proposed transition or reads storage. Retirement rechecks recent assurance
after reference and manifest acquisition before writing its immutable evidence.

Announcement edits acquire the announcement guard, then audience-group and organization guards, before company/membership/account access guards. Group edits acquire their group guard before people and access guards. The bounded member list is stored in one immutable revision with database validation of its company references; lists select summary columns without materializing those arrays. A revision head and its immutable snapshot are linked by a deferred foreign key and commit with the operation receipt, audit, and outbox event.


Publication workers acquire their job lease before announcement, audience-group, people, organization, company, membership, and actor-account guards. Returning a stopped publication to draft or archiving observes its job pointer, acquires that job first, then the announcement guard, and rechecks the pointer/version. Inbox read-state commands take the shared announcement guard before the recipient row and access guards; archival takes the exclusive announcement guard before withdrawing rows. These directions serialize publication, recovery, and archival without reversing worker/cancellation ownership.

A bounded immutable publication stores one recipient-account map and references frozen content. Inbox insertion uses one SQL insert/select. Statement-level validation materializes the recipient map once, while a deferred constraint requires the full inbox, published revision, and successful fenced job checkpoint in the same commit. Inbox identity and read/acknowledgement timestamps are monotonic; archival retains rows with a withdrawal marker. Audience policy lives in the publication use case; the datasource executes a supplied raw recipient query. Group queries expand the selected immutable member lists as a set, and publication-reference validation uses scoped existence checks so it can use identity indexes.

Interactive announcement, audience-group, and inbox reads and commands revalidate live session assurance after their resource/access guards, including before original receipt replay. Publication admission checks assurance again after acquiring the job queue; a proof that expires during either wait cannot authorize a write. Background publication continues to use its execution authority and credential checks, separately from interactive MFA age.

Publication review holds the shared announcement and access guards, then reads its
linked job without acquiring a job lock. This avoids reversing the worker's
job-before-announcement order. The use case validates the linked company, resource,
and job kind and checks MFA again after the read. It returns minimal job status and
current actions under announcement-management authority; general job inspection
still requires ownership or `jobs.read`. These capabilities are a live review,
not a reservation. Mutations independently revalidate scope and observed versions.

Inbox synchronization uses account ownership exclusively. Its captured change rows have no employment ID; database constraints retain employment ownership for the older collections. Statement-level triggers record bounded inbox insert/update sets atomically, while the existing worker still assigns publication positions after commit. Omitting collection selection retains the original six-collection projection.


Native push registration belongs to identity because a device endpoint is owned by a native session, independently of company selection. Its use cases acquire operation, account, then session guards, matching credential rotation/revocation. Repositories coordinate the raw registration store and existing identity cryptography datasource. Tokens use account/session/version-bound encryption and a unique fingerprint; no datasource chooses permission, registration lifetime, or account-switch policy. HTTP derives native session identity from its authenticated principal. Communications consumes the domain registration contract for authorized after-commit delivery.


Inbox push capture stores only source identities in the publication transaction. The delivery use case owns separate preparation and acknowledgement transactions, with provider I/O between them. Preparation takes announcement/company/member/account guards before the queue and native session; acknowledgement takes account before queue. The pinned target and monotonic session cursor survive worker restart. Database time fences claims and results even if a policy clock advances. Invalid-token retirement requires the observed registration version and commits with its checkpoint; late acknowledgements cannot retire a replacement token. Native registration revisions reserve one final disable/retirement step. Cleanup also matches the observed attempt and progress before retiring a queue entry, so a stale maintenance selection cannot overwrite a newer checkpoint or audit old counters.


Browser file acquisition belongs to the core file datasource. It selects raw browser files, performs bounded UTF-8 reads, and dispatches bounded text downloads; it does not choose import policy. The file repository coordinates selection/read operations and maps raw failures through `safeFileCall`. Native picker cancellation returns a successful null, while caller cancellation aborts owned reads and propagates. Feature use cases own file eligibility and any subsequent API command. The cookie HTTP transport supports bounded CSV representations without bypassing deadlines, redirect restrictions, problem JSON, or current CSRF acquisition. Oversized encoded JSON is rejected before any network request.

Binary input uses a separate file-input repository contract. Its datasource owns
at most four browser file handles and one active read per handle. Domain values
contain opaque references and metadata; feature owners release them on departure.
SHA-256 uses bounded asynchronous 64-KiB reads, a finite whole-file deadline and
the pinned hash implementation. Individual upload reads cannot exceed one MiB.
Cancellation releases readers, listeners and timers; failed acquisition clears
borrowed buffers before returning a domain failure.

The cookie transport snapshots at most one MiB for a binary request before waiting
for current CSRF. It admits only the upload offset/checksum headers, preserves the
operation ID, and clears its owned copy on every outcome. JSON and binary bodies
cannot be mixed. Resume, retry, publication and file eligibility remain feature
use-case policy; infrastructure performs no hidden retry or source selection.

Document upload use cases review current company/resource scope, verify selected
file identity, and coordinate the document and file-input repositories. Prepared
commands contain named immutable document, revision, start and part references.
Delivery reads current committed progress, advances at most 100 parts, and clears
each acquired buffer after its HTTP operation. It never retries automatically or
publishes content. A reserved part uses the current operator's server-issued
continuation; replacing page ownership requires reselecting identical content.


People owns a public coworker projection for mobile synchronization. Its worker
use case selects company-local eligibility and the next effective transition;
repositories acquire raw candidates, map the minimal fields, and store resulting
records. Source triggers only capture raw identities. A company timezone guard
precedes bounded queue row locks; no source lock is acquired afterward. MVCC
acquisition and queue serialization preserve an invalidation from a concurrent
source writer. Public records, tombstones, due scheduling and raw sync events
commit atomically. The canonical reader keeps live account/company/MFA checks
independent of the worker's projection authority and revision lifetime.

People also owns a separate private self-profile projection. One raw SQL snapshot
combines employment, its owner's person fields and minimal assignment references;
domain policy derives manager availability and the next effective transition.
Its aggregate sync revision is independent of employment and person command
versions. Repositories validate stored payload ownership and map malformed data
inside the database failure boundary. The canonical use case holds shared people
and access guards and checks current ownership and session assurance after waits.
An administrator's broader grants do not widen this account-owned projection.
