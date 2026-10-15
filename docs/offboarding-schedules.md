# Scheduled offboarding

HR can explicitly authorize an open offboarding case for execution after its last
working day. Creating a case or completing its checklist does not create a schedule.
The existing manual completion remains available after the date has passed.

## Review and admission

The base path is
`/api/v1/companies/{companyId}/lifecycle/cases/{caseId}/offboarding-schedules`.

| Method and suffix | Purpose |
| --- | --- |
| `GET /review` | Current case, employment versions, company date/timezone, execution window, reviewed basis and blockers |
| `GET` | Up to eight retained attempts with minimal job state |
| `GET /{id}` | One retained attempt and current cancellation/recovery hints |
| `POST` | Authorize a new attempt using the reviewed versions and basis |
| `POST /{id}/cancel` | Request cancellation of its observed job |
| `POST /{id}/recover` | Close an authorization whose job has already failed or been cancelled |

Reads require `people.lifecycle.read`, `people.lifecycle.manage`, `people.manage`
and `people.offboard`. Commands require the three management grants and recent
interactive authentication/MFA. Both current permissions and the original request
scope apply after waits, before releasing reads, and through command completion.
Receipt recovery retains those checks.

Admission takes `{id, expectedVersion, employmentVersion, basisRevision, reason}`
and an `Idempotency-Key`. The case version and employment aggregate/effective
revision, linked account, last working date and company timezone form the reviewed
basis. The opaque revision does not disclose account identifiers. Times are derived
by the server; the client cannot supply another execution date or job payload.

The last working date is inclusive. Execution becomes eligible at the following
midnight in the company timezone. Admission must occur before that boundary, with
a last working date from today through 365 days ahead. The retained execution window
lasts 168 hours. A schedule is an earliest eligibility time; queue capacity, outages
and finite worker retries can delay it. A case retains at most eight attempts and
at most one unclosed authorization. A stopped attempt is never silently rescheduled.

Required tasks must be done; optional tasks must be done or explicitly waived.
Other open cases, future employment revisions, reporting dependents, self-departure
and removing the last company administrator block admission where applicable.
The command independently reviews every condition, even after a successful read.

## Execution and ownership

`PEOPLE_OFFBOARD` is fixed-total work with one item. The worker uses the captured
account and security version, then rechecks live account/company/membership/grants.
It does not reuse an expired interactive MFA proof. A replacement credential cannot
revive an existing schedule. The supplied job request must exactly match its locked,
retained record.

The job lease precedes People, case, company/membership and ordered account guards.
Execution rechecks the captured case, employment revision, account linkage, date,
timezone, checklist and departure protections. Changed sources require human review.
A late execution writes the ended employment revision effective on its actual
company-local execution day. It retains the agreed last working date and does not
rewrite earlier workforce or payroll evidence.

Company membership is deactivated only when no other current or planned employment
remains in that company. Permission-source history, the global account and other
company memberships are preserved. Employment, access, case history, schedule
outcome, job checkpoint/result and audit/outbox evidence commit together. Failure,
interruption, lost lease or a date change during completion rolls back the step.
Datasources perform raw I/O, repositories map records and failures, and the use case
owns these decisions and the transaction.

## Cancellation and recovery

Cancellation takes `{expectedVersion, expectedJobVersion, reason}` plus an
`Idempotency-Key`. Its receipt identifies the **job** that changed; the schedule
ID remains the navigation reference. A future cancellation wakes cleanup. The
worker closes it without ending employment. Original receipt recovery returns the
original job version after cleanup as well.

Manual case cancellation or offboarding completion rejects an active, uncancelled
schedule with `offboarding_cancellation_required`. Once cancellation has committed,
manual work can proceed while a stopped worker still awaits cleanup. A late cleanup
cannot repeat or undo that manual transition.

The queue's exhausted-lease recovery has no global access to People tables. If it
terminates a job before feature cleanup, the schedule can remain `SCHEDULED` with a
terminal job. `recover` observes both versions and closes that authorization in an
audited transaction. It creates no replacement job. An already passed or expired
execution boundary uses manual completion after a new review; creating another
schedule does not bypass the date rules.

Retained attempts and terminal outcomes are immutable. Storage requires coherent
case/employment/job evidence and company-scoped references. The bounded list reads
schedule and job metadata in one SQL snapshot without acquiring job locks under
People/access guards.

## Dashboard

Open **People → Lifecycle cases → Offboarding schedules** from an offboarding
case. Closed cases retain this history. An open case without an unclosed attempt
can open **Schedule offboarding**. The review shows the employee, last working
date, company date, timezone and server-derived execution window. Resolve every
reported blocker, then provide a reason to authorize the reviewed basis. Creating
or completing the checklist still does not schedule execution automatically.

A schedule detail separates its retained authorization from the current job.
**Cancel schedule** requests cancellation using both observed versions.
**Close stopped attempt** is available only when the job has stopped but its
schedule header still needs recovery. Neither action starts another attempt.
References and versions remain available in an expandable detail section;
ordinary review forms focus on the departure and execution dates.

Each command retains its original operation, schedule ID and exact payload after
an unconfirmed response. Retrying after MFA or a later conflict cannot silently
replace it. Cancellation receipts identify the job, but navigation keeps the
original schedule ID. Revoked access clears private review and entered text.
Explicit navigation protects pending commands and unsaved reasons; scope changes
abort obsolete reads and ignore late results.

Future schedules load on demand. Once eligible or awaiting cancellation cleanup,
a visible detail can perform up to thirty progress reads, four seconds apart.
Hidden tabs, failed reads, terminal jobs and disposal stop observation. Refresh
explicitly renews the observation budget. This UI limit does not control worker
execution or expire retained schedule data.

See [lifecycle behavior](modules/people.md#lifecycle-checklists) and
[job execution](jobs.md).
