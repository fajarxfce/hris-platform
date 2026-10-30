# Payroll assurance audit

The full Payroll review is active. Thirteen interactive commands previously checked
recent proof without checking whether the account still had MFA enrolled. All 26
actual SQL command/receipt reproductions returned success after enrollment was
removed. The baseline failed in 95.618s; these are genuine behavior failures.

Period creation/cancellation, monthly input saving/verification, opening tax-history
saving/verification, calculation start/resume/abandonment, review submission/decision/
withdrawal and finalization admission now validate the current company session
under the existing account guards. Current enrollment is checked before a business
write or receipt replay. Worker advancement retains its durable authorization.
No schema, transport, DI or new repository acquisition is introduced.

The six complete affected API assurance suites, Payroll domain/repository tests,
architecture and format checks passed in 193.304s after formatting in 16.584s.
The tests verify stable `mfa_setup_required`, unchanged business/journal/receipt
state after denial, then recovery and exact replay with the original operation
after enrollment is restored. Existing expiry, cancellation and rollback cases
remain included. [Measurements](measurements/payroll-assurance-2026-10-30.json)
retain baseline classification and exact suite counts.

## Input, receipt and staged-delegation correction

Eight more actual SQL failures were reproduced in 64.86s. Compensation earnings
and insurance programs and policy review references could change after validation
while waiting for locks. Period creation compared the original mutable participant
set after fingerprinting its detached snapshot. Payment preparation queried the
original mutable instructions, causing a missing-key exception after the list was
cleared; reconciliation persisted the right results but wrote zero as its audit
count. Compensation receipt replay returned success after beneficiary acquisition
consumed the remaining recent authentication or MFA lifetime.

Those commands now use detached admitted values consistently for identity,
validation-dependent acquisition, persistence and audit. Bounded validation remains
before collection copying; existing sorted period/payment snapshots are reused.
Compensation receipt completion now checks recent proof after beneficiary lookup.

Six additional genuine failures involved two-stage Payroll and amendment review.
A reviewer directly assigned to the next stage could complete the current stage
after its delegation expired during acquisition or the journal write. Ordinary
review also used a timestamp acquired before loading the run. Both commands now
retain the original decision stage for completion checks; ordinary planning uses
a fresh post-acquisition instant. Original receipts retain their existing history
assignment scope after stage advancement.

The final seven-case delegation baseline failed six cases in 51.519s; amendment
source-acquisition expiry was the already-correct control. Initial JDBC-object
snapshot equality and a subsequently assumed nonexistent status column were
fixture errors and are excluded from defects. Canonical PostgreSQL JSON text now
compares complete retained heads without those fixture assumptions.

All fifteen added input/receipt/delegation cases, 114 domain and twelve repository
cases passed with architecture/format checks in 102.743s, after formatting in
16.818s. Tests verify rollback of heads/history/journal/receipts, recovery with the
original operation after renewed delegation, persisted input equality and unchanged
state during exact replay. Across both increments there are forty genuine failed
baseline cases and one existing control.

The [seven-row review](payroll-feature-review-2026-10-30.md) remains active until
broad API/worker/browser acceptance and final source CI. No schema, transport,
shared DI, statutory rule or runtime deployment changed. Continue Workforce and
full Mobile/Starter review after completing Payroll acceptance.

## Complete local acceptance

The final broad run at application/test source `cca5e151` passed 682 API cases
across 95 suites, three Payroll worker cases, 114 domain and twelve data cases,
architecture/format and both packages in 1,464.110s. Four actual normal browser
scenarios then passed in 47.453s and three worker/browser scenarios in 138.372s,
including setup. This includes calculation/recovery, review/publication, retained
slips/PDFs, payments/returns, amendments, input verification and comparison.

The preserved packages and XML/browser hashes identify the tested candidate.
All owned fixture listeners/metadata and the worker database were removed.
[Measurements](measurements/payroll-assurance-2026-10-30.json) distinguish source at
invocation from later documentation-only HEADs. Full source CI `41087903927` is
still running; no final CI acceptance or deployment is claimed yet.
