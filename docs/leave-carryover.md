# Leave carryover accounting

Status: domain/storage accounting and request preview, submission, approval,
withdrawal and cancellation integration are implemented and tested. New public
closing commands do not create tracked awards yet, and validity configuration is
not exposed. Adjustment, closing, worker and client integration remains; these
changes have not been deployed.

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
credit. Negative adjustments and year-end transfers need explicit subledger
movements so carried units cannot exceed the main balance.

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

Automatic expiry will use the existing durable worker, with a bounded cohort for
a company year-closing batch and a single employee for direct closing. It must
not create thousands of browser timers or one long transaction for all employees.
Worker authority, fencing, recovery and current client views remain required
before exposing the new policy in the dashboard.

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

## Remaining integration

- Apply carry movements in adjustments and direct/batch year closing, retaining
  current access and receipt guarantees before enabling tracked public awards.
- Admit and execute bounded expiry work with immutable origin, lease fencing,
  original due times and explicit terminal-job recovery.
- Expose validity and progress through scoped transport/sync models, dashboard
  policy/recovery views and employee balance views; validate browser and native
  behavior against the completed API.
