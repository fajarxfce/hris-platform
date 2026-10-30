# Workforce requirement review

Review is active. The eight initial requirements and five detailed sections are
mapped below; 24 completion/input/delegation failures are reproduced and corrected; broad
validation and final source CI remain required before accepting these rows.

| Requirement | Implemented surface and matching evidence |
| --- | --- |
| `workforce.01` | Versioned shift/holiday/weekly/roster records, raw attendance and independent corrections, overtime approval snapshots and durable monthly closing. Domain owns policies/use cases; raw SQL remains in data adapters. WorkCalendarHttpTest, AttendanceHttpTest, OvertimeWorkflowHttpTest and WorkPeriodHttpTest exercise actual records, transactions and receipts. |
| `workforce.02` | Captured/server-received timestamps, device/window/location evidence and a frozen work-date schedule; offline evidence remains pending. Independent review and immutable correction history preserve original punches. AttendancePolicyTest/CalendarPolicyTest, AttendanceHttpTest, AttendanceAccessHttpTest and AttendanceDayConsistencyTest cover admission, midnight, invalid location, current scope and coherent totals. |
| `workforce.03` | Requested, actual and approved overtime remain separate; staged approval snapshots and beneficiary/maker exclusion gate decisions. Monthly closing blocks unresolved earlier evidence/plans/reviews and freezes approved facts. Payroll's retained attendance selection and calculation tests verify independent consumption without rewriting Workforce. |
| `workforce.04` | Dashboard calendars/weekly/daily/bulk/reset editing, daily attendance/evidence, exception queue, immutable corrections/history, overtime planning/actions and monthly closing/recovery/adjustment workflows. Four normal browser scenarios (`calendars`, `roster-bulk`, `attendance`, `overtime`) and the actual worker `work-periods` workflow exercise these surfaces. |
| `workforce.05` | ListSelfWorkforceEmployments returns only the account's minimal employment references, under Workforce availability independently of People. SelfWorkforceHttpTest and the existing self-directory completion control verify scope/permission/enrollment and final proof. Calendar/overtime reads authorize separately. |
| `workforce.06` | ListWorkforceEmployees implements bounded current administrative references and pagination. EmployeeCalendarReviewHttpTest/WorkCalendarAccessHttpTest exercise selection; RosterBulkHttpTest verifies that advertised capability does not grant permission. The completion audit covers proof after People acquisition. |
| `workforce.07` | GetEmployeeCalendarReview combines the effective assignment, future-aware schedule version, immutable shift snapshots, explicit off/reset origins and roster versions under shared guards. CalendarSourceConsistencyTest, EmployeeCalendarReviewHttpTest, RosterResetHttpTest and RosterBulkHttpTest preserve coherence, stale-write rejection and receipt recovery. |
| `workforce.08` | Pure calendar/attendance tests and actual transaction/race tests cover overnight/DST, duplicate/offline events, overlapping rosters, independent concurrent reviews, immutable evidence/corrections and exclusion from closing/payroll until verified. WorkerIntegrationTest covers durable close, revocation, bounded progress and shutdown ownership. |

## Detailed sections

Implementation status includes current calendar/attendance/exception and command
recovery behavior. Roster reset retains the head and appends an inactive revision;
bulk preview pins observed input and applies bounded changes atomically. These
features already exist in the API and dashboard. Mobile reset compatibility was
independently demonstrated before enabling the administrative command.

Monthly closing preserves per-employee immutable snapshots/checkpoints and limits
the initial selection to 5,000 employees. Ended-month admission, ordered guards,
late evidence, crash exhaustion, explicit recovery, revoked origin and fence checks
are covered by WorkPeriodHttpTest/WorkPeriodAccessHttpTest/WorkPeriodAssuranceTest
and the worker context. No interactive MFA is added to durable worker execution.

Closed-day adjustments retain the original closing snapshot while publishing a
new employee/month revision and canonical daily record. AttendanceAdjustmentHttpTest
and AttendanceAdjustmentCompletionTest cover stale reviews, independent decisions,
rollback and completion assurance. Payroll attendance/amendment source tests and
the actual worker browser workflow cover explicit downstream selection. Existing
calculations keep their original sources.

Overtime decisions check the original current stage. A direct assignment to a
future stage cannot substitute for an expired delegation at this stage. Existing
minimal original receipts deliberately remain recoverable after delegation expiry
when current session/permission still allows receipt recovery; private request
reading authorizes independently. This is not authority to make another decision.

## Retained native evidence

Mobile's `docs/validation.md` records actual Android attendance response loss
(Platform fixture `a837320f`), overtime response loss (`578a5031`), closed-attendance
revision synchronization and roster reset/reactivation across offline process
restarts. Native acceptance is historical and precisely source-scoped. The latest
full Mobile CI `41077194465` passes all targets, including these maintained test
suites. No new phone UI exercise or physical iPhone acceptance is claimed here.

## Focused acceptance and remaining gates

The actual SQL baseline ran 27 cases in 60.302s: 24 real behavior failures and
three already-correct controls, with no fixture errors. The corrected five API
suites and pure domain cases pass in 100.808s, including original overtime receipt
recovery after delegation expiry and existing account-wait/MFA rollback coverage.
[Measurements](measurements/workforce-assurance-2026-10-30.json) retain exact counts,
commands, baseline messages, source hashes and formatting checks. Workforce data
has no standalone tests; its actual repository behavior is covered by the SQL/API
suites. No standalone data-test count is claimed.

Run broad Workforce API/domain and worker checks, both packages, the four normal
actual browser workflows and `work-periods` worker workflow, then final source CI.
Audit the complete Mobile and applicable Starter contracts before closing the
workspace objective. No live deployment or new physical device test occurred.
