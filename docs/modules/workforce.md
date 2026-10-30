# Workforce

Own work calendars, holidays, shift templates, rosters, immutable attendance events, corrections, verification, overtime, and period closing.

Shift IDs and work dates anchor overnight events. Raw timestamps retain captured and server-received times and device evidence. Offline events remain pending verification. HR corrections require actor/reason and append an adjustment. Location evidence is evaluated against the applicable policy.

Overtime separates requested/actual/approved time. A payroll cutoff requires resolution of relevant exceptions. Workforce exposes factual time; leave remains separately owned and reporting/payroll combine through repository contracts.

Screens: roster, daily attendance, exceptions, offline verification, corrections, overtime, and closing.

`GET /workforce/me/employments` selects only employments linked to the current
account, under `attendance.self.record`, `workforce.read`, `overtime.self.manage`,
`overtime.read` or `overtime.manage`. Its bounded directory
returns employee number, status, observed version, company date and timezone;
personal profiles are not included. The endpoint belongs to Workforce availability,
so disabling People does not prevent employees from opening their work calendar
or overtime. Discovery does not grant calendar, profile or other employees' access.
Company access, credential versions and current MFA are rechecked after waiting
and after acquisition. Subsequent calendar and overtime reads authorize independently.

Administrative selection uses `GET /workforce/employees?asOf=yyyy-MM-dd`, under
`workforce.read`, with at most 50 employee references per page and bounded search.
It returns only ID, employee number, legal name, employment status and version.
The People feature's availability and profile permissions remain independent.
The page separately advertises `bulkRosterSupported`; this API capability does
not grant management access. Clients treat its absence on an older API as false.

`GET /workforce/employees/{id}/calendar/review?from=...&until=...` returns that
reference, the resolved calendar, and the weekly assignment effective at `from`.
One shared schedule/people/access transaction keeps its component queries coherent.
The global `scheduleVersion` includes future assignments; `assignmentAtStart`
retains the applicable date, revision and original shift snapshots. A roster day's
origin/version distinguishes an explicit day off from holiday or weekly time off.
The review requires `workforce.read`; self/team calendar readers retain their
existing endpoint and scope. Missing and foreign employment IDs share one error.
All reads recheck current session assurance after waiting and release their guards
on cancellation. These observations do not reserve a later write: commands still
require current authority, selected shift versions and the observed schedule or
roster version.

Acceptance: duplicate/offline events, checkout across midnight, timezone transitions, overlapping roster, immutable corrections, concurrent verification, and unverified records excluded from payroll.

## Implementation status

Versioned shift templates, effective weekly assignments, explicit daily roster overrides, holidays, and scoped calendar reads are implemented. Published schedules store shift snapshots; changing a template does not rewrite them. Selecting a shift requires its observed version. Assignment, roster, shift, and holiday revisions are append-only and audited.

`GET /workforce/shifts/{id}` returns one current template and its observed version,
including inactive templates, under `workforce.read`. A missing ID and an ID from
another company both return `shift_not_found`. The read uses the shared schedule
and access guards and rechecks session assurance before releasing the result.

`GET /workforce/holidays/{id}` returns one current holiday, including an inactive
date, under `company.read`. Missing and foreign IDs both return `holiday_not_found`.
It holds the shared calendar/access guards and rechecks live membership, original
permissions, credentials and MFA before reading the current revision. Cancelling
a pending read releases those guards.

The dashboard provides shift search, a bounded company-holiday directory, current
details and versioned create/edit forms, with explicit request recovery after
response loss. Holiday activation and deactivation apply to employee calendars
under the existing precedence and closed-period rules. The employee calendar
directory and review now support effective weekly assignments and per-date roster
editing. Forms retain original selected shift revisions across catalog paging,
use the corresponding schedule/roster version and recover explicitly after an
unknown response. The daily editor also restores the underlying calendar through
the reviewed reset command described below. The [bulk editing API](../roster-bulk.md)
provides bounded before/after review and an atomic multi-employee/date command;
its dashboard provides the same reviewed preview, atomic application and explicit
receipt recovery.
The mobile employee
application provides its own [weekly calendar](https://github.com/fajarxfce/hris-mobile/blob/main/docs/workforce.md)
with a bounded encrypted offline snapshot. Its attendance route also supports
foreground location evidence, online capture windows, encrypted offline commands,
process restoration and idempotent retry. Employee mobile screens distinguish a
saved command, server receipt and pending/accepted attendance. Overtime planning, actual-time submission and withdrawal also retain encrypted
working copies and original commands through offline restart and response loss.

Calendar precedence is explicit roster, active holiday, then the effective weekly pattern. A null roster shift means an explicit day off. Calendars distinguish work, off, and unassigned dates. Overnight shifts retain their starting work date. Nonexistent DST local times return a dated validation failure; ambiguous local times use the earlier offset and elapsed UTC minutes. Calendar reads are bounded to 62 days and holiday queries to 366 days.

Calendar commands and reads revalidate live company membership, credentials, the original/live permission intersection, and current MFA age under guards. A proof that expires while a request waits cannot authorize its result or an existing command receipt. Receipts require current authority but do not reapply a successful command's mutable period or shift checks. Calendar reads hold a shared schedule guard across their component queries; concurrent readers can proceed while schedule writers wait. Assignment and roster commands protect employment references before checking eligibility. The lock order is schedule, applicable months, people, company, membership, then account.

### Roster reset contract

`POST /workforce/employees/{id}/roster/{day}/reset` restores the calendar beneath a
reviewed daily override. It requires `workforce.manage`, an idempotency key, the
observed roster version and a reason. An active row with a null shift still means
an explicit day off. Reset instead records an inactive revision, allowing the
active holiday, effective weekly pattern or unassigned date to apply.

Migration V87 retains the day ID and every original revision. Reset advances the
version; a later ordinary roster save uses that version and reactivates the same
ID. Old creates or stale edits cannot silently replace the retained head. Calendar
review includes `rosterOverrides`, with date, active status, observed version and
the frozen shift. This metadata remains separate from the resolved day's origin.

The command rejects a locked month, unavailable employment, stale version or
already inactive override. It validates the restored rule's local time before
mutation. Captured attendance retains its original schedule; resetting an open
calendar does not rewrite evidence or published closing snapshots. Current
authority and MFA are checked after guards and through atomic revision, journal,
receipt and sync publication capture. Replaying the original receipt requires
current authority without repeating mutable calendar eligibility checks.

Reset publishes a versioned `ROSTER_DAYS` deletion. Bootstrap omits inactive heads,
and the owned canonical endpoint returns `roster_day_not_found` for a withdrawn
override. A subsequent save publishes an upsert at the same ID and a higher
version. Clients must also handle a known missing roster during hydration of an
older upsert; unrelated transport and authorization failures remain failures.
The mobile client handles this race and is tested against reset, offline process
restart and reactivation. See [native validation](https://github.com/fajarxfce/hris-mobile/blob/main/docs/validation.md#roster-reset-synchronization).
Roll out client compatibility before enabling administrative reset.

Attendance capture and independent verification are implemented. Server-issued capture windows last 120 seconds, bind account/employment/device, and can be consumed once. They establish recent server contact, not device attestation. A client offline flag, absent/expired proof, doubtful capture time, location violations, or an invalid punch sequence produces a pending event. Offline events never enter accepted totals before review.

The first event freezes that work date's schedule. One accepted clock-in/out pair produces elapsed minutes minus the snapshot break; incomplete/pending pairs contribute no minutes. Raw events and reviews are immutable. HR or the current manager can review, but never their own event. Competing reviewers transition once. Commands replay their original receipt even when their time window has subsequently expired.

Capture, window issuance, verification, corrections, and attendance reads revalidate live credentials, company membership, and original permissions after acquiring their guards. Current reporting assignments determine team access. Verification excludes both the original event owner and the currently bound employee account. New capture/windows require working employment; an original receipt still requires current ownership and permission, without repeating the successful command's expired time checks. Replaying a capture window never extends its expiry.

These interactive operations and correction-history reads also recheck current MFA
age after waiting for resource and account guards. Renewed verification can retry
the original operation; an expired proof cannot release evidence or replay a receipt.

Capture time limits and server proof expiry are evaluated again after pending resource/access acquisition. `receivedAt` reflects that decision time. Aggregate reads acquire shared employee/day guards in ascending date order, bounded to 31 days, before people and access guards. This prevents old event state from being combined with a new correction while allowing concurrent readers. Correction history is one bounded immutable-record query.

Capture accepts up to 31 days of offline history, tolerates at most 30 seconds of future clock skew, and bounds one date to 32 raw events. Employee reads cover at most 31 days. Location evidence includes declared accuracy/mock status and is not a guarantee against a compromised device. Capture-window issuance is bounded to ten per employee per minute.

`GET /workforce/attendance/exceptions?from=...&until=...` requires company-wide
`workforce.read`. It returns employee display references and dates with pending
evidence or an incomplete accepted clock-in/out pair, plus the pending count,
current correction version and latest evidence receipt time. Optional `kind`
selects `PENDING` or `INCOMPLETE`; omission includes both. A correction resolves
raw pair incompleteness but does not hide later pending evidence. Missing punches
do not establish absence, and the directory does not calculate payable time.

The range is at most 31 days; pages default to 50 rows and allow at most 200.
Ordering is work date descending, then employee ID ascending. The opaque `after`
cursor is bounded to 160 characters and retains its company, range and filter.
Each page is one SQL snapshot, so earlier resolved rows do not invalidate its
continuation. Pages are live observations, not a frozen export or action approval.
The subsequent day read and review/correction command authorize independently.
Locations, devices, personal profiles and raw evidence are not returned here.

In a mutable period, HR corrections are immutable revisions with optimistic versions and required reasons. They retain original evidence and snapshots, expose a paginated history, and prohibit self-correction. Pending evidence must be resolved first. Later punches on a corrected day remain pending and cannot overwrite the correction; reviewers can reject that evidence or HR can publish another explicit correction. An explicit absence has null clock-in/out and zero break minutes.

The dashboard now exposes bounded employee attendance, daily capture evidence,
independent review, and versioned corrections in the company timezone. Unknown
outcomes retain their original request through verification and explicit retry.
The real API/browser scenario covers offline clock-in/out verification, overnight
totals, lost responses, a competing correction and preservation of raw evidence.
Each day also provides bounded correction-history browsing with immutable details,
original reasons and recording actors. The interface distinguishes corrected
absence from working time and preserves the original attendance evidence. The
company-wide exception queue adds bounded date/status filters, paginated review
and links to the independently authorized day view. Resolved pairs leave the
queue; later pending evidence on a corrected day remains visible for review.

Overtime requests, actual-time submission, independent staged review, withdrawal, and closed-period snapshots are implemented. Reviewed roster reset is implemented in the API, dashboard and mobile sync. Reviewed bulk editing is implemented and validated in the API and dashboard. [Payroll amendments](../payroll-amendments.md) consume explicit retained source revisions;
[scheduled offboarding](../offboarding-schedules.md) owns employment lifecycle cleanup. Accepted attendance totals are factual inputs; they are not a finalized payroll result.

## Monthly attendance closing

`POST /workforce/periods/{yyyy-MM}/close` requires `workforce.close`, an idempotency key, the observed period version (zero for a new period), and a reason. Only an ended month in the company timezone can close. It snapshots up to 5,000 employee IDs and creates one durable job. The company queue is limited to 100 pending jobs. This is factual attendance closing; it retains approved overtime evidence but does not calculate leave entitlement, payable absence, overtime pay, or payroll eligibility.

Each employee produces an immutable monthly snapshot, committed with its fenced checkpoint. Pending evidence recorded before this attempt and incomplete accepted pairs block closing. Facts distinguish worked time, explicitly corrected absence, unrecorded scheduled days, off days, and missing schedules. A missing record never automatically becomes a paid absence. The final transaction publishes the closed period, successful job, and audit/outbox together.

Attendance commands hold a shared month lock before their employee/day lock. Starting closing takes the exclusive month lock. Roster and holiday changes also honor the month gate; weekly assignments cannot rewrite a locked month. Moving a holiday checks both dates. Raw late attendance remains pending and is stamped with the current closing job ID. Current-attempt late evidence does not change the snapshot. Evidence from an earlier failed attempt must be resolved before another close can succeed. Closed-period late evidence may be rejected through ordinary verification. Accepting it or correcting factual totals uses the separate adjustment contract below.

Cancellation and permanent failure leave the month in `REVIEW_REQUIRED`. Transient failures retain checkpoints for bounded retry. If a worker repeatedly crashes until its job exhausts all attempts, the period remains frozen until an authorized `POST /workforce/periods/{yyyy-MM}/recover` verifies that the job has stopped. A new close creates another attempt; old snapshots are retained. Worker steps re-resolve the initiating account's current credentials and company permission; fenced cleanup remains possible after revocation.

Closing steps recheck the initiating credential version and live company/member/account authority after acquiring the job and period locks. A later sign-in cannot replace an old job's originating credentials. Recovery uses the same job-before-period order and validates current authority before mutation or receipt replay. Period lists and closed-snapshot reads recheck live permissions; snapshot visibility uses current team/self scope. Closed periods and their published snapshots are immutable, so these reads do not create or update period rows.

`GET /workforce/periods?from=yyyy-MM&until=yyyy-MM` covers up to 24 months. `GET /workforce/periods/{yyyy-MM}/employees/{id}` returns a closed employee snapshot under current HR/team/self scope. Employment or leave changes do not rewrite these facts; downstream payroll must validate their applicability separately.

Interactive closing, recovery and these reads recheck current MFA after their
guards and after acquisition. A proof that expires during the operation cannot
release private data, a receipt, or partial closing state. Cancellation unwinds
the transaction and its locks; a later explicit retry retains the same operation.

`GET /workforce/periods/{yyyy-MM}` returns the current period and minimal attempt
progress from one SQL snapshot, plus the company date/timezone and available
actions. It accepts `workforce.read` or `workforce.close`. An unstarted month has
no period or attempt; viewing it creates nothing. Closing authority does not grant
employee snapshots or inspection of another operator's general job details.

`GET /workforce/periods/{yyyy-MM}/employees` requires `workforce.read` and a closed
period. It selects published employee display references as of month end with
name/number search and 50-row maximum pagination. Employees added after that
attempt are absent from its results. Individual snapshot reads retain their
existing HR/team/self authorization. The overview and discovery do not reserve
eligibility; close/recover commands independently recheck current state.

Individual snapshot responses also include the minimal employee reference and
closed period metadata. The existing `employeeId`, `month` and `days` fields remain
compatible. Display references use employment at month end and current identity
labels; recorded daily facts and their calendar evidence remain immutable.

Dashboard contracts validate period/attempt consistency, complete monthly results
and immutable calendar evidence. Employee discovery reuses the shared reference
page mapper, which only needs its observed cursor. Closing/recovery commands pin
the original month, period version, reason and operation; explicit retry performs
no fresh read or current-clock check. The dashboard includes closing, monitoring,
stopped-attempt recovery and published employee results.

## Closed attendance adjustments

The [adjustment API](../attendance-adjustments.md) reviews one closed day and
requires explicit corrected times or absence, a reason, and a decision for every
pending capture. It retains original evidence and the closing snapshot while
appending a new employee snapshot revision. Different dates share the same ordered
monthly revision; stale reviews cannot overwrite a concurrent correction.

Event verification, the correction, revision, journal, receipt and daily mobile
sync changes commit atomically. Self-correction and self-verification are denied;
current company/team authority and MFA remain required through completion and
receipt recovery. Exact revisions and bounded history retain the existing
Workforce read scopes.

Explicit payroll source selection, dashboard adjustment and native offline
synchronization have recorded acceptance in [adjustments](../attendance-adjustments.md)
and [Payroll amendments](../payroll-amendments.md). Existing payroll calculations
continue to use their original frozen sources.


## Overtime requests

`POST /workforce/overtime` records the requested time window and an immutable work-calendar snapshot. The command includes the observed employment version, a stable request ID, and an idempotency key. HR can enter a request for an employee; employees can enter their own while currently employed. Requests can cover the preceding 31 days or the next 90 days. A plan records proposed work; it is not a payable approval.

`POST /workforce/overtime/{id}/actual` submits completed actual time for review. Requested and actual intervals use whole minutes, a maximum twelve-hour elapsed interval, and explicit breaks of up to 120 minutes. Actual net time cannot exceed the requested window or requested net time. Ordinary shift time cannot be submitted as overtime. Overnight work retains its starting work date and timezone. A missing calendar must be resolved before planning.

Actual submission freezes the effective `OVERTIME` approval template. Every stage must approve before the request has approved minutes. The original planner, actual submitter, original beneficiary, and currently bound beneficiary cannot decide, including through delegation. Missing reviewers produce the existing blocked approval state and can be reassigned by an authorized administrator. Reassignment does not replace the submitted time or template snapshot.

`POST /workforce/overtime/{id}/decisions` applies a staged approval/rejection using the observed request version. Review approves the submitted actual duration; it does not silently alter the employee's claim. `POST /workforce/overtime/{id}/withdraw` withdraws an unfinished plan or pending review and cancels its approval. Approved, rejected, and withdrawn requests retain their history. Corrections after approval require the separately planned adjustment workflow.

Active windows cannot overlap for the same employee, including across adjacent work dates. PostgreSQL exclusion constraints enforce this alongside serialized employee writes. Each employee/month retains at most 128 requests, including withdrawn and rejected records. Lists cover up to 62 dates with a maximum 200 results per page; histories use descending revision cursors.

Every mutation rechecks live company membership and credentials without expanding the permissions resolved when the request began. Business state, immutable change history, approval consequences, audit/outbox, mobile invalidation, and command receipt commit together. Database constraints reject incomplete history and mismatched approval/business state. Retries return the original receipt only while the caller remains authorized.

Interactive reads and commands require current session assurance after acquiring
their guards and before returning data or completing the transaction. Expired MFA
cannot authorize an original receipt or partially commit a command. Independent
detail readers share the employee and approval guards across current state and
history; writers continue to acquire exclusive ownership. Cancellation unwinds
the transaction without retaining a reader's locks.

Monthly closing holds the same month gate as overtime mutations and refuses unresolved plans/reviews. Closed employee snapshots include each approved request ID, version, actual interval, approved minutes, and original schedule. New plans and actual-time changes cannot enter a processing/closed month. These are verified time facts; statutory wage rates and payable eligibility belong to payroll.

Employee-owned requests are registered as `OVERTIME_REQUESTS` in the [mobile change feed](../synchronization.md). Approver access is specific to assigned/delegated requests; it does not grant a directory export.

Dashboard overtime contracts retain immutable employee display snapshots and
requested, actual and approved time separately. Directories use bounded date,
employee and status filters with exclusive UUID cursors; history uses descending
revisions. The client validates structural consistency while the API retains
current company, employee, reviewer and delegation authorization.

The dashboard now provides overtime directories, request details, original schedule
evidence and paged change history. It retains date/status filters when returning
from a request and uses the request's timezone for time records. Assigned reviewers
can open a source request from Approvals without gaining company-wide directory
access. Refresh hides the previous private result until acquisition succeeds;
changing account/company or leaving the route cancels its request. Actual-time,
approval, rejection and withdrawal forms acquire their own current action review.
Input uses the request's timezone and domain interval policy. Receipt recovery
retains the observed version and operation without acquiring a newer request;
an ambiguous result cannot become editable after a later rejection. Access and MFA
failures hide private details during recovery. New-plan authoring uses a dedicated
employee picker and reviewed-calendar form. The dashboard also provides monthly
closing, bounded progress observation, stopped-attempt recovery and published
employee results. Closing and recovery retain their original commands through
explicit receipt recovery. Missing attendance stays distinct from recorded
absence; published overtime remains a time fact rather than a payable amount.

Planning preparation uses `GET /workforce/overtime/planning/employees` with a
work date, optional name/number query and bounded cursor. It requires
`overtime.manage` and returns up to 50 display references with observed employment
versions. Discovery validates the date range and current access; it does not
guarantee that every result has an eligible schedule or employment status.

`GET /workforce/overtime/planning/employees/{employeeId}` returns the employee,
requested work date, server-derived company date/timezone and effective schedule.
Current employees may review their own reference with `overtime.self.manage`.
Both endpoints apply the plan's preceding-31/following-90-day range and current
session assurance after their shared guards.

Individual review creates no business records, receipts or period rows. It rejects
missing schedules, inactive employment and an observed processing/closed month.
The plan command independently rechecks current scope, employment, schedule,
period availability and capacity when saving; review does not reserve eligibility.

The review's `revision` binds company, employee, observed employment version,
timezone and resolved calendar facts. New clients send it as `planningRevision`
when creating the request. A changed review returns `overtime_planning_changed`
without consuming the operation. The original receipt is recovered before
comparing today's calendar, and changing or removing a sent revision cannot reuse
that receipt key. The field remains optional for existing clients; their operation
identity is unchanged. This content revision grants no permission or reservation.

Dashboard planning contracts use the dedicated discovery and review endpoints.
Domain validates requested intervals, company-local work dates, regular-shift
overlap and immutable command context. A retained command needs employee identity,
employment version and calendar preconditions, without retaining display names or
shift metadata. Repository mapping rejects inconsistent reviews and receipts that
do not identify the new request at revision zero. Read acquisition and mapping
share the HTTP failure boundary, including cancellation and late responses.

## Completion audit — 30 October 2026

The [assurance correction](../workforce-audit-2026-10-30.md) closes 24 reproduced
reader/command/receipt, weekly-input and current-stage delegation failures. Private
results and interactive commands require valid session proof after the last
acquisition or write under their existing guards. Weekly input is detached before
waits. New overtime decisions require valid current-stage eligibility through
completion; original minimal receipt recovery remains independent of expired
delegation and requires current session/permission. No worker MFA or capture-window
time policy changes. [The requirement review](../workforce-feature-review-2026-10-30.md)
records complete broad/browser acceptance and all ten passed jobs in final
source CI `41094285531`; the eight initial requirements are accepted.
