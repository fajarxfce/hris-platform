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

Remaining review includes mutable policy/compensation/participant/payment input,
compensation receipt completion after beneficiary acquisition, and current-stage
delegation through ordinary/amendment review completion. These are hypotheses
until reproduced. The seven initial Payroll rows remain unverified until complete
module acceptance and final source CI. No runtime deployment occurred.
