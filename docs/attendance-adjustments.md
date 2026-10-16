# Attendance after closing

Monthly closing freezes its original facts. A late correction creates a separate
employee snapshot revision; it cannot reopen the month, replace the closing job,
or update an already published payroll assessment.

## Workforce contract

One command corrects one employee and work date in a closed month, in years
2000–2100. Its review
shows the original closed day, the current adjusted day, current attendance and
the latest correction. A content revision binds the company, closing attempt,
employee version, monthly adjustment revision and every observed event version.
It is neither authorization nor a reservation.

The operator supplies corrected times or an explicit absence, a reason, and one
accept/reject decision for every pending event shown in that day. No pending event
is accepted implicitly. These decisions classify supporting evidence; the explicit
correction determines the worked duration. Capture time, location, original
assessment and received time remain immutable. A subsequent late capture stays
pending until another review.

Both time properties are required in the request. An absence explicitly sends
`clockIn: null` and `clockOut: null` with zero break minutes; omitted properties do
not record an absence.

All routes are relative to
`/api/v1/companies/{companyId}/workforce/employees/{id}/attendance/adjustments`.

| Method and suffix | Contract |
| --- | --- |
| `GET /review?workDate=YYYY-MM-DD` | Current sources and the content revision for one closed day. |
| `POST` | Corrected times, evidence decisions, reason and observed review revision; requires an idempotency key. |
| `GET ?month=YYYY-MM&before=revision&limit=25` | Descending immutable history, at most 50 entries per page. |
| `GET /{adjustmentId}` | An exact retained revision and its employee snapshot. |

The command requires current `attendance.correct`. Reviewing pending evidence also
requires current company or direct-report verification authority. The beneficiary
and the original capturing account cannot verify their own evidence. Readers
retain the existing self, team and company Workforce scope. Current credentials,
MFA and company admission apply after waits and through transaction completion.

One date contains at most 32 captures. A snapshot contains at most 31 days. Date
ranges, history pages and versions are bounded. Inputs are copied before waiting.
The lock order is operation, closed month, attendance day, People, company,
membership and account. An exclusive month guard serializes employee snapshot
revisions, including corrections to different days. Readers use the shared guard.
This reuses the existing period lock and creates no background job.

Event reviews, an immutable attendance correction, the next monthly adjustment
snapshot, audit/outbox entries and the operation receipt commit together. Failure
or cancellation rolls all of them back. A stale review changes nothing. Original
receipt recovery requires current authority and never reapplies earlier facts.
The existing attendance change feed publishes the corrected daily projection;
the closing snapshot and event/correction command versions keep their identities.

The adjustment table retains every revision and references its predecessor,
original snapshot and correction. Its datasource performs raw I/O; its repository
owns DTO mapping and the database failure boundary. Use cases coordinate the
existing period, attendance, correction, People and access repositories. Pure
policies own review comparison and replacing a single day in the frozen snapshot.

## Payroll input sources

Payroll's work-source and input-review responses expose the current adjustment
reference, revision, date, time and reason. Preparing an input supplies
`workAdjustmentId` together with the original closing job and observed work-period
and employment versions. Null selects the original closing and is valid only
while that employee has no adjustment. The reference must match the latest
employee/month revision; Payroll cannot invent or replace Workforce facts.

Verification preserves the selected reference and requires it to remain current.
A later correction requires another draft and independent verification. Historical
input revisions retain their own selection. The dashboard distinguishes the
current source from each retained input reference and includes source metadata
on the verification page. Original-closing commands omit the optional field for
compatibility with older APIs; absence and null both select the original closing.
Existing operation receipts still replay their original outcome after current
authority checks; a changed selection is a different command payload.

Calculation admission captures the latest adjustment reference for each employee
under the shared month guard. A verified input that no longer matches that cutoff
produces `stale_workforce_version`, rather than a successful result from old facts.
After admission, the worker reads the exact selected immutable snapshot. Later
corrections cannot change an active run's source. The result retains the reference
alongside its factual days; absence, proration and overtime rules remain explicit.

Original closing snapshots, calculation results and published assessments remain
immutable. This input path does not amend a finalized assessment. Amendment source
selection and its dashboard review remain separate work below.

## Delivery sequence

1. Workforce review, atomic correction, exact revision reads and bounded history
   are implemented. Transaction, access, concurrency, cancellation and sync tests
   passed; [delivery records](delivery.md#closed-attendance-adjustment-api) contain
   the executed checks.
2. Payroll source selection for unfinalized inputs is implemented and validated
   in the API, database, worker and dashboard, including a real browser workflow.
   Amendment selection remains in progress. Payroll-owned read
   projections acquire immutable references; clients never submit replacement
   work or leave facts. Independent verification, approval and publication remain
   required.
3. Dashboard before/after review, evidence decisions, revision history and pinned
   command recovery. Original and adjusted facts must be distinguishable.
4. Real API/browser acceptance and employee replica acceptance, followed by
   compatible development delivery.

The sequence records remaining implementation and validation. A retained Workforce
revision alone does not complete payroll consumption or dashboard delivery.
