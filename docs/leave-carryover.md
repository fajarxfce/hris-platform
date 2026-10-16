# Leave carryover accounting

Status: accounting, request transitions, adjustments, year closing, the durable
expiry worker, scoped history and terminal recovery are implemented and tested
across the API, dashboard and mobile read views. New public closings create tracked
awards; absent validity still means no additional expiry. The API accepts optional
validity configuration. Dashboard policy editing and native acceptance of finite
awards are the remaining integration steps.

## Contract

Carried credit is a separate, versioned bucket tied to its immutable year-closing
award. Ordinary adjustments and new-year accrual remain ordinary credit. One
employee/type/destination year has at most one closing award. Existing ledger
history is not retrospectively attributed to a bucket or given a new deadline.

An optional policy sets 1–12 complete calendar months of validity in the
destination year. No configured value means no additional carry expiry. The
closing freezes the last valid work date and exclusive company-local midnight,
using the later offset for a repeated midnight. Closing after that boundary must
not create already-expired spendable credit.

For a new request, eligible carried units are reserved before ordinary credit.
Both booking time and charged work dates must fit the frozen validity. Future
work dates cannot use a carry bucket whose validity has already ended by those
dates. Funding is retained per request and bucket; ordinary reservations made
before a bucket existed are not reclassified afterward.

Approval consumes the original reservation, including an approval after its
validity boundary. Expiry removes only free units. Rejection, withdrawal or approved
cancellation restores the original funding source; any returned carried units
past the boundary expire in the same transaction. They do not become ordinary
credit. Balance reductions consume ordinary credit first, then explicitly deduct
carried credit; additions remain ordinary. Expired free carry is settled before
applying either adjustment. Year-end transfers and expiry also record explicit subledger movements so carried
units cannot exceed the main balance.

## Request integration

Preview and submission use the same carry-first funding policy under the employee
ledger guard. The preview revision includes the current carry buckets and selected
funding. Crossing a deadline can therefore invalidate a review even when ordinary
credit could still fund the same dates. Expired free carry is excluded even when
the expiry worker has not settled the ledger yet.

Submission freezes original funding alongside its request and approval snapshot.
Decisions and withdrawals apply only the financial effect of the actual workflow
transition: an intermediate review or withdrawal of a cancellation request does
not release credit. Late approval consumes its original reservation. A late refund,
withdrawal or rejection restores that source and expires any remaining free units
in the same transaction. Existing ordinary requests have no inferred carry funding.

Date and evidence inputs are bounded and copied before acquisition. Current
session assurance is checked after guarded acquisition and again through receipt
and journal completion. Late failure or cancellation rolls back the request,
funding, ledger, approval, evidence references, journal and operation receipt.

## Balance adjustments

An adjustment first verifies the caller's observed main-balance version under the
employee guard. Its pure policy settles expired free carry before checking the
signed delta. A reduction uses ordinary credit first and records only the carried
portion as `DEDUCT`; additions cannot replenish a carry bucket. Reserved and
consumed original funding remains unchanged.

Expiry, adjustment, subledger movements, journal and receipt commit together. An
invalid correction does not partially expire the account. Original command replay
retains its first receipt after later balance changes and still requires current
scope, independent authorship and session assurance. Concurrent corrections cannot
both spend the same observed version.

## Year closing

Direct and batch closing share one pure calculation. An expired source bucket is
settled before the closing snapshot is recorded; those units cannot receive a new
award. Eligible source carry transfers first and any excess expires explicitly.
A closing at or after the destination deadline awards zero carried units. New
awards retain their original source, calendar and validity; no history is rewritten.

Direct closing checks the caller's original observed source version before any
settlement, then checks the destination version when credit will be transferred.
The stored closing source version reflects the completed source expiry. Pending
requests still prevent closing, and a stale review or late assurance/journal failure
rolls back expiry, closing, award, ledger and receipt together.

V84 retains an optional immutable batch origin. A batch closing must match its
reviewed type, policy, actor, reason, selected employee and timezone, with an active
job and a matching applied outcome in the same transaction. Changing the company's
timezone after admission cannot shift the reviewed deadline. Historical closings
keep a null batch origin; metadata cannot be added retrospectively.

## Storage boundary

V83 adds immutable award metadata, versioned carry balances, retained request
allocations and append-only movements paired with main-ledger entries. The
request freezes its allocation count at creation; a later write cannot add
funding to an ordinary historical reservation. Deferred constraints require
complete request funding, compatible request transitions, exact expiry proof
and balances contained within the main account. Late release/refund must settle
returned expired units in the same transaction. Projections are movement-owned;
direct balance edits are rejected.

A closing explicitly opts into tracked accounting. Historical closings remain
untracked. Migration preserves their ledger values, account IDs/versions,
policy evidence and sync records. `EXPIRE` keeps its existing public name; an
internal provenance field distinguishes closing expiry from carried-credit
expiry. Optional policy serialization omits an absent validity value, retaining
existing policy snapshots and operation hashes.

The carry datasource performs bounded raw queries and writes. Repository
acquisition, scope validation and DTO mapping share `safeDatabaseCall` and return
owned immutable snapshots. Technical errors cannot escape into domain, and
cancellation still propagates.

## Ownership and delivery

Pure domain policies calculate funding and unit movements. Use cases coordinate
carry, ledger, request, entitlement, identity and job repositories. Datasources
perform storage I/O only. Employee ledger guards serialize request transitions,
expiry and year closing. Ledger, carry movements, workflow/history, operation
receipts and sync invalidations must commit together; failure rolls everything
back. Late-year closing evaluates carry validity after acquiring the employee
guard so an expiry worker cannot miss credit created with a stale date.

Automatic expiry uses the existing durable worker. One closing batch shares one
expiry cohort of at most 5,000 employees; a direct positive award has one employee.
Admission reserves both batch jobs, or the direct expiry job, under the company
queue guard after resource/access guards. Capacity, journal or assurance failure
rolls back the entire admission. A closing after the deadline creates no expiry
job or spendable carried credit.

## Durable expiry and recovery

V85 retains an immutable origin with its original source, employee cohort,
calendar and UTC deadline. The job owns execution status; the origin holds only
its current job and recovery version. Attempts are immutable and capped at eight.
Outcomes form a contiguous employee prefix and retain their exact ledger effect.
A new expiring award requires its durable origin in the same transaction;
migration neither reattributes historical credit nor invents historical jobs.

Each worker step verifies its lease, locks the origin and employee ledger, then
checks the clock after acquisition. Free carried units expire; reserved and
consumed funding remains intact. A prior settlement records an unchanged outcome.
A target without an award from this origin records `NO_AWARD`; a parent closing
resumed after the deadline cannot introduce expired credit for that target.
Outcome, carry movement, main ledger, sync invalidation and fenced checkpoint
commit together. A final bounded step closes the job without another debit.

This derived task uses an empty-permission actor tied to its verified lease and
fixed origin. Revoking the original HR account does not extend awarded credit.
The task cannot choose another cohort, change validity or grant units. Generic
job cancellation is unavailable for fixed expiry, including a storage guard.
Existing interactive and other job authorization rules retain their own checks.

Terminal recovery requires current `leave.year.close` scope, recent assurance,
an observed version, a reason and an idempotency key. Another authorized HR user
may recover the fixed obligation. Recovery locks the old job before the origin
and access/queue guards, retains the completed prefix and original deadline, and
creates only the remaining work plus finalization. Competing recovery attempts
cannot become current together. A failed journal, lost lease, cancellation or
late assurance failure rolls back the affected transaction.

Reads preserve the UTC deadline originally validated at award creation. Updating
timezone data cannot recalculate an existing award. New award validation still
checks its calendar and requires agreement with its admitted expiry origin.

## History and recovery API

All routes are under `/api/v1/companies/{companyId}/leave/carry-expiries`.

| Method | Route | Behavior |
| --- | --- | --- |
| GET | `/` | Company-scoped history with an optional UUID `after` cursor |
| GET | `/{id}` | Current job, retained attempts and one employee-outcome page |
| POST | `/{id}/resume` | Recover terminal work using `expectedVersion`, `reason` and `Idempotency-Key` |

Reads require current `leave.read`; recovery independently requires
`leave.year.close`. Read page size defaults to 50 and is bounded to 1–100.
Detail `after` is an outcome ordinal from 1–5,000. Pages are live scoped reads,
not a synchronization cursor. Employee progress excludes the finalization step.

Overview labels come from the original closing or batch policy snapshot. A policy
rename cannot rewrite this history. Detail acquisition locks the current job
before the origin and People/access guards; concurrent recovery invalidates a
stale discovery. Scope and session assurance are checked again after acquiring
private data. Responses include only the current page's minimal employee labels
and safe job metadata, never worker credentials or lease authority.

## Dashboard

Leave processing links to company carry-expiry history, retained attempts and
bounded employee results. Each result links to the employee's corresponding
balance year. The original closing labels and deadline remain visible after
recovery. Commands require the current server-provided action and HR permission.

The detail controller owns one cancellable read and one timer. It stops work when
hidden or disposed, does not poll a queued deadline more than two minutes away,
and limits automatic reads to thirty before an explicit refresh. Confirmed
access loss clears private data. Company replacement discards the previous
selection and cursors. Recovery retains the original operation and reason after
an uncertain response; verification cannot silently submit a command.

## Observed validation

The foundation passed 45 Leave domain tests, including 17 carry-policy scenarios covering
company-local/leap-year validity, half-day units, ineligible future work dates,
the exact deadline, cross-year funding, retained reservations, late refunds,
immutable plans and unit conservation.

The first API/PostgreSQL run passed 26 scenarios, including 14 new storage cases
and existing year-closing, rollback and balance-sync regressions. A final run
passed the 14 carry storage cases and four mapper/ownership/cancellation cases.
They cover missing counterparts, early expiry, simultaneous expiry, retained
funding through approval/refund, rejected retrospective funding, cross-company
reads/writes and immutable history. The storage fixtures coordinate repositories
directly; subsequent request tests use the actual HTTP commands against these
tracked awards.

Both application contexts passed, including all 68 worker tests. The V82-to-V83
upgrade test and the existing ledger-upgrade test passed against PostgreSQL 18.
Kotlin compilation, query generation, formatting and architecture checks passed.
No browser, device or development deployment was performed for this increment.

Request integration first passed 46 domain, 73 API/PostgreSQL and all 68 worker
tests. The final focused run passed 48 domain and 52 API/PostgreSQL tests, including
nine new HTTP funding scenarios and ten assurance/cancellation/input-ownership
scenarios. Coverage includes half-days, future dates, deadline changes, simultaneous
reservations, staged decisions, original receipt replay, late journal rollback and
recovery after assurance renewal. Pure completion plans validate both cross-year
funding and the version capacity for a subsequent expiry. Formatting and
architecture checks passed. No browser, native or development deployment was
performed for this increment.

Adjustment integration passed 53 domain tests, 44 focused API/PostgreSQL tests
and all 68 worker tests. New coverage includes ordinary-first reductions, late
positive corrections, retained reservations, competing adjustments, incomplete
command rollback, expired assurance and cancellation after journaling. Both API
and worker application contexts passed with the additional repository dependency.
Formatting and architecture checks passed. No client or development deployment
was performed for this increment.

Closing integration passed 59 domain tests, 93 API/PostgreSQL regressions and all
68 worker tests. Seven new HTTP scenarios cover tracked public awards, source
expiry before rollover, unlimited carry transfer, late destination closing,
pending requests, original receipts, stale reviews, journal rollback and frozen
batch timezone with missing-outcome rollback. Three additional assurance/cancellation
cases exercise closing after acquisition and journaling. Both the V82-to-V83-to-V84
carry upgrade and the existing account migration test passed, preserving historical
ledger values, versions, policy evidence and sync records. Query generation,
Kotlin compilation, formatting and architecture checks passed. No browser,
native or development deployment was performed for this increment.

The worker increment passed 64 Leave domain tests and all 68 existing worker
checks. The initial API run passed 101 existing regressions; two new fixture
errors were corrected (missing approval setup and an assertion against an
intentionally omitted transport field). The final focused run passed 27 expiry
API/PostgreSQL cases, three real restricted-worker scenarios, two Jobs domain
checks and the extended V82-to-V85 migration test. Coverage includes original
creator revocation, recovery by another HR user, a partially completed parent
closing, retained reservations, lost or omitted checkpoints, competing recovery,
RLS, immutable history, late assurance, cancellation and original receipts.

The 5,000-employee admission fixture used two jobs and one expiry origin, measured
at 512 ms in this run; this measures admission, not full-cohort worker throughput.
The migration test preserves both tracked and untracked existing awards, ledger,
account versions and sync facts. Fourteen existing dashboard Jobs tests and the
changed label's Biome check passed. No browser, native or development deployment
was performed for this increment.

A final UTC-retention check passed 33 API/storage/mapper cases and the three
restricted-worker scenarios after changing reads to retain their persisted
deadline. New awards still reject an instant inconsistent with their current
calendar. Formatting and architecture checks passed; all 34 local documentation
link targets were verified.

The history/recovery API passed 65 Leave domain tests, 74 API/PostgreSQL cases
and all 71 worker tests. Coverage includes frozen labels, company/read scope,
finite paging, minimal employee references, original recovery receipts, retained
outcomes without another debit, recovery during discovery, revoked credentials,
late MFA and cancellation. OpenAPI contracts, Kotlin compilation, formatting and
architecture checks passed. No browser, native or development deployment was
performed for this increment.

Dashboard validation passed all 526 Leave unit tests, type checking, architecture
checks and the changed files' formatting checks. Thirty-two browser tests covered
carry expiry and the shared batch/accrual forms; the narrow Indonesian review was
also rechecked after resetting screenshot scroll position. Desktop and narrow
screenshots were inspected. These scenarios used controlled API responses.

An additional browser scenario used an isolated API, PostgreSQL and real worker.
A fictional pre-existing award and failed initial attempt were seeded with all
constraints enabled. Public recovery survived a lost successful response and
returned its original receipt after the worker completed. Exactly one three-day
debit matched the employee outcome and left the balance at zero. This validates
recovery and execution; optional-validity admission remains internal. API/worker
JAR builds passed. No native or development deployment was performed.

## Balance and sync projection

Balance directories, ledger details, entitlement review and the canonical
`LEAVE_BALANCES` resource include the same nullable `carry` breakdown. It retains
the closing ID, awarded/available/reserved/consumed/expired/transferred/deducted
days, optional business deadline, frozen timezone and independent bucket version.
Ordinary balances and historical untracked awards have no inferred breakdown.

A single raw query joins the account and its carried bucket. Repository mapping
checks scope, conservation and the carried subset of each main balance, inside
the database failure boundary. Domain results are detached from mutable SQL
records. Interactive balance readers retain current session assurance after their
last acquisition.

Totals remain committed ledger facts. Passing the deadline alone does not rewrite
a canonical response or advance its version. Commands exclude expired free credit
immediately; the durable expiry worker records the matching debit. Its transaction
already advances the main account and publishes the existing balance invalidation.
Clients must distinguish business validity from cache retention and keep their
published offline facts while acquiring newly available metadata.

Validation passed 196 API/PostgreSQL tests, 65 Leave domain tests and all 71 worker
tests. New cases cover matching projections, stable reads across the deadline,
exact expiry/sync versions, unlimited carry, retained reservation/consumption,
foreign scope, mutable-record ownership and cancellation. Post-acquisition MFA
and cancellation were checked across all four balance read use cases. OpenAPI,
Kotlin formatting and architecture checks passed.

The additive read API and V83–V85 are deployed to development. Both application
JAR hashes were verified in their running containers; API health succeeded on
loopback and the WireGuard interface from the VPS. Existing database/document
containers were preserved. The subsequent optional-policy API increment is described below.

Dashboard ledger review now displays the tracked breakdown and original calendar
deadline separately from the account totals. Responses without metadata remain
compatible. It passed 546 Leave unit tests and fourteen balance browser scenarios,
including Indonesian dark narrow layout, original UTC retention, conservation,
access loss and stable figures while paging older ledger movements. The isolated
API/PostgreSQL/worker scenario also verified the published expired/available
breakdown in the ledger page after the single debit. Type/build, architecture and
changed-file formatting checks passed; screenshots were inspected.

## Mobile balances and policy configuration

Employee balances now show the carried amount, original calendar validity and
posted expiry/transfer/deduction facts. Record format 3 acquires the new metadata
without changing ledger versions and preserves the published format while its
replacement is staged. Root checks, Android assembly, 200 targeted Chromium cases
and production Web distribution passed. The Indonesian 200% layout was inspected,
and the validated APK was installed on the authorized phone without opening its UI.
See [mobile validation](https://github.com/fajarxfce/hris-mobile/blob/main/docs/validation.md#carried-leave-balances).

`accrual.carryValidityMonths` is an optional integer from 1 to 12 and requires a
positive carryover limit. Omitted/null input adds no carry-specific deadline.
The policy head, revision history and submitted snapshots return the value.
Changing a policy never changes an existing award's date or UTC boundary. The
HTTP parser rejects fractional integer inputs, including observed versions,
with the existing `invalid_request` response instead of truncating them.

The public-policy increment passed 201 API/PostgreSQL scenarios, including seven
new cases for retained deadlines, policy history, original-command replay,
January/December bounds, invalid configuration, fractional input, absent metadata
and late closing. Architecture, formatting and both application JAR builds passed;
the previously validated 71 worker tests remained up to date.

## Remaining integration

- Validate finite carry awards in the Android emulator, including an offline upgrade.
- Add optional validity to dashboard policy editing and processing review.

The optional-policy API is deployed to development. Its running JAR matches the
validated build; API health returned 200 on loopback and the WireGuard interface
from the VPS, and the dashboard returned 200. The worker, database and document
service containers retained their existing identities and volumes.
