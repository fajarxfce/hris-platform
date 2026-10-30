# Workforce completion audit

The 27 actual SQL regressions reproduced 24 behavior failures against unchanged
production source at `a5927e5d` in 60.302 seconds. Three calendar controls were
already correct. No compilation/setup/fixture failure is counted as a defect.
The corrected five API suites and Workforce domain tests pass in 100.808s,
alongside architecture/format checks. See [measurements](measurements/workforce-assurance-2026-10-30.json)
and the [eight-row requirement review](workforce-feature-review-2026-10-30.md).
Broad API/worker/browser and complete source CI remain pending.

## Confirmed failures and correction

Eight older readers released shifts, holidays, calendar/attendance/correction
history or administrative employee references after proof expired during the
last acquisition. Seven commands could commit after expiry during journal or
receipt persistence. Four attendance receipt paths also acquired ownership or
window data after their original proof check. The use cases now retain their
already-guarded account access and validate session proof at completion. Failed
results roll back the existing transaction; no second account query is added.
Full SQL row snapshots verify unchanged business heads, histories, receipts,
audit/outbox and mobile changes after rejection. Renewed proof recovers the same
operation and its original receipt.

Weekly assignment could fingerprint a populated day map, then persist an empty
schedule when the caller cleared that map during an account wait. It now detaches
the bounded enum-keyed map before any wait and consistently uses the snapshot for
identity, shift acquisition and persistence. The regression verifies actual
persisted days, exact original replay and rejection of a changed payload.

Four overtime cases accepted APPROVE/REJECT after delegation expired during
candidate acquisition or journal completion. Decisions now use the time after
acquisition and revalidate the original stage at transaction completion. The
regression intentionally assigns the delegate to a later stage as well: that
future assignment cannot authorize this stage. Renewal recovers the original
operation; rollback preserves every business/approval/history row.

## Preserved contracts and controls

Self-employment discovery, calendar review and daily roster completion already
rejected expiry; their three controls remain passing. Newer reset/bulk, closed-day
adjustment and period closing/recovery retain their existing completion contracts.

The explicit overtime receipt behavior remains unchanged: a completed actor's
minimal original receipt is recoverable after delegation expires while current
session/permission still authorizes recovery. Private details authorize separately.
Both the existing HTTP test and new multi-stage tests verify this behavior; receipt
replay never makes another decision. Other modules' receipt rules are not imported.

Attendance capture/window admission keeps its documented decision timestamp;
this change does not alter captured/received time or extend a replayed window.
`AdvanceWorkPeriodClose` remains durable worker work without interactive MFA.
No schema, DI, transport or repository-query change is introduced.

## Remaining acceptance

Complete the broad API/domain and actual worker context, both application packages,
four normal browser scenarios (`calendars`, `roster-bulk`, `attendance`, `overtime`)
and the `work-periods` worker scenario, which includes closed-day adjustments.
Preserve their reports and source/package hashes, then run full source CI without
cancelling Reporting/Payroll CI `41087903927`. Finish Mobile/applicable Starter
review before closing the full goal. Historical native evidence is identified in
the requirement review; no fresh native execution is inferred from backend tests.

## Complete local acceptance

The committed correction `2c2a59e3` passes 369 API, six worker and 61 domain
cases, architecture/format checks and both packages in 622.192s. Four actual
normal browser workflows and the real worker closing/adjustment workflow pass;
[measurements](measurements/workforce-assurance-2026-10-30.json) retain exact
durations, report hashes, package hashes and complete owned-fixture cleanup.
Final full source CI remains required before accepting all eight inventory rows.
