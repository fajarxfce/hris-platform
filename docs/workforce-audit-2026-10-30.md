# Workforce audit in progress

Read-only review began at Payroll source `cca5e151` while its broad suite runs.
The eight Workforce inventory rows remain unverified. No Workforce test or
implementation has changed in this audit yet. Findings below are source hypotheses
until reproduced with actual SQL and existing fixtures.

## Completion assurance to reproduce

The older shift/holiday list and detail readers, employee calendar, employee
attendance, correction history and administrative employee directory check session
assurance before acquisition but have no final proof check. Reuse
`WorkCalendarAssuranceTest`, `AttendanceAssuranceTest` and their actual database
fixtures to expire proof after the selected acquisition. Newer self-directory,
calendar review, bulk/reset and adjustment readers already contain completion
checks; preserve them as controls where applicable.

Weekly assignment, shift/holiday save, ordinary attendance record/review/correction
and capture-window issuance return after persistence or journal work without a
final session check. Capture and window receipt paths also acquire ownership or
stored window data after their initial assurance check. Reproduce these boundaries
before changing them; immediate receipts with no intervening acquisition need no
additional database query. Account locks already protect current enrollment.

`AssignWeeklySchedule` fingerprints the input map before waiting, then iterates
its original values and entries afterward. Reproduce mutation of that map during
an account wait and verify exact persisted input and original-operation replay.
Bulk selection and closed-adjustment decisions already use detached sorted input.

## Overtime decisions and receipt contract

New `DecideOvertimeRequest` decisions capture their time before beneficiary,
delegation and candidate acquisition. Final completion rechecks MFA but does not
recheck delegation eligibility. Reproduce expiry during those acquisitions and
journal completion for approval/rejection, with transaction rollback and renewal.
Current-stage candidate grants are acquired after the relevant account locks;
there is no established account-grant race in this path yet.

Preserve the explicit original-receipt behavior in
`OvertimeDelegationHttpTest.aDelegateRequiresLiveSourceAuthorityButACompletedDecisionKeepsItsReceipt`:
a completed actor's minimal original receipt remains recoverable after delegation
expires, while private request detail becomes unavailable. Do not impose another
module's receipt policy on this established recovery contract. Replaying a receipt
does not apply another decision or grant private read access.

`AdvanceWorkPeriodClose` intentionally uses durable worker authority. Do not add
interactive MFA to it. Attendance capture/window admission has its documented
post-resource/access-wait decision time; do not change capture expiry or received
at semantics merely because session proof must survive through completion.

## Documentation and remaining coverage

The module's older paragraphs call bulk dashboard editing, Mobile overtime and
closed-attendance/payroll-source selection unfinished. Bulk editing is already
implemented later in the same contract and in [roster bulk](roster-bulk.md).
Inspect the current adjustment, Payroll and Mobile source and retained native
acceptance before classifying those historical statements as missing features.

Finish the eight initial rows and all detailed sections: shifts/calendars/roster,
raw offline evidence and independent corrections, exception queues, closed-period
adjustments, overtime, durable closing/recovery and employee synchronization.
Verify duplicate/offline events, overnight/DST schedules, overlapping rosters,
immutable corrections, concurrent review and exclusion of unverified facts from
payroll. Run final relevant API/domain/data/worker and actual browser acceptance,
then complete source CI and the full Mobile/Starter review.
