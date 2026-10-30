# Workforce requirement review

The eight initial requirements and five detailed sections are mapped below.
All 24 reproduced completion/input/delegation failures are corrected. Complete
local acceptance passes; final full source CI remains the acceptance gate.

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

## Complete local acceptance and final CI

The actual SQL baseline ran 27 cases in 60.302s: 24 real behavior failures and
three already-correct controls, with no fixture errors. The corrected five API
suites and pure domain cases pass in 100.808s, including original overtime receipt
recovery after delegation expiry and existing account-wait/MFA rollback coverage.
[Measurements](measurements/workforce-assurance-2026-10-30.json) retain exact counts,
commands, baseline messages, source hashes and formatting checks. Workforce data
has no standalone tests; its actual repository behavior is covered by the SQL/API
suites. No standalone data-test count is claimed.

The broad run at exact `2c2a59e3` passed 369 API, six worker and 61 domain cases,
architecture/format checks and both application packages in 622.192s.
Four actual normal browser workflows passed in 66.494s; the real worker closing,
recovery and closed-day adjustment scenario passed in 43.507s, including setup.
All reports/package hashes are retained; both owned database containers, fixture
metadata/environments and four listening ports were removed. Full source CI remains.
Mobile's fifteen-area review and the applicable Starter review are complete at
`f4f101a1` and `7605916`; their unchanged implementations retain independent CI
acceptance. No live deployment or new physical device test occurred.

## Approved overtime boundary

Approved overtime requests retain immutable actual/approved facts. There is no
post-approval overtime-fact amendment command. The future adjustment note was
introduced by implementation commit `1019b022`; the original product contract
`d27c848d` specifies separate requested/actual/approved time, attendance corrections
and resolution before cutoff, without defining an overtime amendment workflow.
This review accepts those stated workflows. The implemented closed-attendance
adjustments and approved Payroll financial amendments do not rewrite an approved
overtime request. A future overtime-fact amendment needs its own business contract;
it is not represented here as an implemented feature.
