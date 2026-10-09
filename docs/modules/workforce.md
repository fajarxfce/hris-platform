# Workforce

Own work calendars, holidays, shift templates, rosters, immutable attendance events, corrections, verification, overtime, and period closing.

Shift IDs and work dates anchor overnight events. Raw timestamps retain captured and server-received times and device evidence. Offline events remain pending verification. HR corrections require actor/reason and append an adjustment. Location evidence is evaluated against the applicable policy.

Overtime separates requested/actual/approved time. A payroll cutoff requires resolution of relevant exceptions. Workforce exposes factual time; leave remains separately owned and reporting/payroll combine through repository contracts.

Screens: roster, daily attendance, exceptions, offline verification, corrections, overtime, and closing.

Acceptance: duplicate/offline events, checkout across midnight, timezone transitions, overlapping roster, immutable corrections, concurrent verification, and unverified records excluded from payroll.

## Implementation status

Versioned shift templates, effective weekly assignments, explicit daily roster overrides, holidays, and scoped calendar reads are implemented. Published schedules store shift snapshots; changing a template does not rewrite them. Selecting a shift requires its observed version. Assignment, roster, shift, and holiday revisions are append-only and audited.

Calendar precedence is explicit roster, active holiday, then the effective weekly pattern. A null roster shift means an explicit day off. Calendars distinguish work, off, and unassigned dates. Overnight shifts retain their starting work date. Nonexistent DST local times return a dated validation failure; ambiguous local times use the earlier offset and elapsed UTC minutes. Calendar reads are bounded to 62 days and holiday queries to 366 days.

Attendance capture and independent verification are implemented. Server-issued capture windows last 120 seconds, bind account/employment/device, and can be consumed once. They establish recent server contact, not device attestation. A client offline flag, absent/expired proof, doubtful capture time, location violations, or an invalid punch sequence produces a pending event. Offline events never enter accepted totals before review.

The first event freezes that work date's schedule. One accepted clock-in/out pair produces elapsed minutes minus the snapshot break; incomplete/pending pairs contribute no minutes. Raw events and reviews are immutable. HR or the current manager can review, but never their own event. Competing reviewers transition once. Commands replay their original receipt even when their time window has subsequently expired.

Capture accepts up to 31 days of offline history, tolerates at most 30 seconds of future clock skew, and bounds one date to 32 raw events. Employee reads cover at most 31 days. Location evidence includes declared accuracy/mock status and is not a guarantee against a compromised device. Capture-window issuance is bounded to ten per employee per minute.

HR corrections are implemented as immutable revisions with optimistic versions and required reasons. They retain original evidence and snapshots, expose a paginated history, and prohibit self-correction. Pending evidence must be resolved first. Later punches on a corrected day remain pending and cannot overwrite the correction; reviewers can reject that evidence or HR can publish another explicit correction. An explicit absence has null clock-in/out and zero break minutes.

Overtime requests, actual-time submission, independent staged review, withdrawal, and closed-period snapshots are implemented. Late payroll adjustments, roster reset/bulk editing, and lifecycle cleanup remain planned. Accepted attendance totals are factual inputs; they are not a finalized payroll result.

## Monthly attendance closing

`POST /workforce/periods/{yyyy-MM}/close` requires `workforce.close`, an idempotency key, the observed period version (zero for a new period), and a reason. Only an ended month in the company timezone can close. It snapshots up to 5,000 employee IDs and creates one durable job. The company queue is limited to 100 pending jobs. This is factual attendance closing; it retains approved overtime evidence but does not calculate leave entitlement, payable absence, overtime pay, or payroll eligibility.

Each employee produces an immutable monthly snapshot, committed with its fenced checkpoint. Pending evidence recorded before this attempt and incomplete accepted pairs block closing. Facts distinguish worked time, explicitly corrected absence, unrecorded scheduled days, off days, and missing schedules. A missing record never automatically becomes a paid absence. The final transaction publishes the closed period, successful job, and audit/outbox together.

Attendance commands hold a shared month lock before their employee/day lock. Starting closing takes the exclusive month lock. Roster and holiday changes also honor the month gate; weekly assignments cannot rewrite a locked month. Moving a holiday checks both dates. Raw late attendance remains pending and is stamped with the current closing job ID. Current-attempt late evidence does not change the snapshot. Evidence from an earlier failed attempt must be resolved before another close can succeed. Closed-period late evidence may be rejected, but accepting it or modifying factual totals requires a future explicit adjustment workflow.

Cancellation and permanent failure leave the month in `REVIEW_REQUIRED`. Transient failures retain checkpoints for bounded retry. If a worker repeatedly crashes until its job exhausts all attempts, the period remains frozen until an authorized `POST /workforce/periods/{yyyy-MM}/recover` verifies that the job has stopped. A new close creates another attempt; old snapshots are retained. Worker steps re-resolve the initiating account's current credentials and company permission; fenced cleanup remains possible after revocation.

`GET /workforce/periods?from=yyyy-MM&until=yyyy-MM` covers up to 24 months. `GET /workforce/periods/{yyyy-MM}/employees/{id}` returns a closed employee snapshot under current HR/team/self scope. Employment or leave changes do not rewrite these facts; downstream payroll must validate their applicability separately.


## Overtime requests

`POST /workforce/overtime` records the requested time window and an immutable work-calendar snapshot. The command includes the observed employment version, a stable request ID, and an idempotency key. HR can enter a request for an employee; employees can enter their own while currently employed. Requests can cover the preceding 31 days or the next 90 days. A plan records proposed work; it is not a payable approval.

`POST /workforce/overtime/{id}/actual` submits completed actual time for review. Requested and actual intervals use whole minutes, a maximum twelve-hour elapsed interval, and explicit breaks of up to 120 minutes. Actual net time cannot exceed the requested window or requested net time. Ordinary shift time cannot be submitted as overtime. Overnight work retains its starting work date and timezone. A missing calendar must be resolved before planning.

Actual submission freezes the effective `OVERTIME` approval template. Every stage must approve before the request has approved minutes. The original planner, actual submitter, original beneficiary, and currently bound beneficiary cannot decide, including through delegation. Missing reviewers produce the existing blocked approval state and can be reassigned by an authorized administrator. Reassignment does not replace the submitted time or template snapshot.

`POST /workforce/overtime/{id}/decisions` applies a staged approval/rejection using the observed request version. Review approves the submitted actual duration; it does not silently alter the employee's claim. `POST /workforce/overtime/{id}/withdraw` withdraws an unfinished plan or pending review and cancels its approval. Approved, rejected, and withdrawn requests retain their history. Corrections after approval require the separately planned adjustment workflow.

Active windows cannot overlap for the same employee, including across adjacent work dates. PostgreSQL exclusion constraints enforce this alongside serialized employee writes. Each employee/month retains at most 128 requests, including withdrawn and rejected records. Lists cover up to 62 dates with a maximum 200 results per page; histories use descending revision cursors.

Every mutation rechecks live company membership and credentials without expanding the permissions resolved when the request began. Business state, immutable change history, approval consequences, audit/outbox, mobile invalidation, and command receipt commit together. Database constraints reject incomplete history and mismatched approval/business state. Retries return the original receipt only while the caller remains authorized.

Monthly closing holds the same month gate as overtime mutations and refuses unresolved plans/reviews. Closed employee snapshots include each approved request ID, version, actual interval, approved minutes, and original schedule. New plans and actual-time changes cannot enter a processing/closed month. These are verified time facts; statutory wage rates and payable eligibility belong to payroll.

Employee-owned requests are registered as `OVERTIME_REQUESTS` in the [mobile change feed](../synchronization.md). Approver access is specific to assigned/delegated requests; it does not grant a directory export.
