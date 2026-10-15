# Scheduled onboarding

Status: backend, worker and dashboard implemented.
This workflow prepares an onboarding case at a date chosen by HR. Task completion, account activation, company grants and
employment changes retain their existing explicit workflows.

## Reviewed case initiation

The resource belongs to an employment:
`/api/v1/companies/{companyId}/employees/{employmentId}/onboarding-schedules`.

| Method and suffix | Purpose |
| --- | --- |
| `GET /review?templateId=…&targetDate=…&startsOn=…` | Current employment and template, company date, immutable basis and eligibility |
| `GET` | Up to eight retained attempts and minimal job state |
| `GET /{id}` | Original schedule, selected checklist/assignees and current cancellation/recovery hints |
| `POST` | Authorize the reviewed creation with distinct schedule/case IDs, observed versions, basis, dates, assignees and reason |
| `POST /{id}/cancel` | Cancel the observed job without deleting an existing case |
| `POST /{id}/recover` | Close an authorization whose job has already failed or been cancelled |

Reads require `people.read`, `people.lifecycle.read` and
`people.lifecycle.manage`. Commands require lifecycle management and recent
interactive authentication/MFA. A schedule retains the initiating credential
version; execution rechecks the actor's current company membership and management
grant without reusing an expired interactive proof.

The target date is the employment's reviewed first working day. HR selects a
future company-local `startsOn` date no later than that first day and no more than
365 days ahead. The worker becomes eligible at its midnight in the reviewed
company timezone. Its finite window ends at the earlier of 168 hours later or
the following company midnight after the first working day. A repeated midnight
uses its later offset; a nonexistent midnight advances to the next valid instant.
These boundaries match PostgreSQL, including zones that change offset at midnight.
Already due preparation uses the existing manual case-initiation workflow.

The authorization captures the employment aggregate/effective revision and the
exact active onboarding template revision. The immutable template history owns
its task definitions; the schedule references that revision. Selected assignees
are a bounded map of checklist keys to company accounts. Names and current
authority are independent: active membership and task/management grants are
checked before admission and again before creation. Unassigned tasks remain
unassigned. No account or permission is created by this workflow.

Each employment retains at most eight attempts and one unclosed schedule. Open
lifecycle cases and completed onboarding/offboarding block admission. Completing
a manually initiated onboarding case before execution also stops the schedule;
automatic preparation cannot repeat completed onboarding. A manually initiated
case, transfer, changed employment/template/timezone, or an unavailable assignee
can stop a later execution and require review. A stopped attempt never creates
a replacement automatically.

## Transactions and recovery

The fixed-total `PEOPLE_ONBOARD_START` job has one item. Worker, cancellation and
recovery acquire the job before People and access guards. Execution additionally
locks the template before company/membership and ordered account guards;
cancellation and recovery do not acquire an unused template guard. Reads join only their scoped job metadata without acquiring job
locks under People/access guards. Admission uses queue capacity for a new job;
no code takes an existing job lock after People guards.

Execution verifies the complete retained lease request, captured versions and
current authority, then creates one open case with pending tasks and the retained
assignees. Case creation, initial history, schedule outcome, job progress and
journal/outbox commit together. Interruption, lease loss, a concurrent manual
case, date-window change or a late failure rolls the step back. The case's
actual creation timestamp does not rewrite historical employment data.

Cancellation wakes future cleanup and creates no case. A completed initiation
is labelled `STARTED`; subsequent task completion belongs to the new case. A
terminal failed/cancelled job with an unclosed header can be recovered explicitly,
without creating another case. The distinct schedule, case and job identities
remain stable through original receipt recovery. All company references, initial
authorization evidence and terminal outcomes are enforced in storage.

## Dashboard workflow

Employee details link to the independently authorized schedule history. For future
employees, select their first working date in the employee directory. The nested
routes preserve that date, company and directory filters. Company replacement
clears the previous scope before returning to the new directory.

The new-schedule form selects an active onboarding template and preparation date,
then requests an explicit review. Its first working day comes from the acquired
employment; the server supplies the authoritative versions, window and blockers.
The checklist supports bounded member selection and explicit removal of an
assignee. Existing template revision history owns the retained task definitions.
Admission creates a schedule, while its later `STARTED` outcome means the open
checklist was created. It does not mean the employee completed onboarding.

Controllers retain one original command through response loss, renewed MFA and
later conflicts. Known first conflicts require another deliberate review. Denied
access clears private form/detail data without replacing an uncertain command.
Cancellation acknowledges the original job receipt; navigation retains the
schedule ID. Recovery closes a stopped authorization without scheduling another.

One visible detail owns at most thirty delayed progress reads, four seconds apart,
while a due or cancelling job remains active. Future schedules are read on demand.
Hidden views, disposal, errors and terminal outcomes stop observation. An explicit
refresh can resume it. Task definitions and references remain readable after an
ordinary transport failure, while current action links are withheld.

## Validation and development

The dashboard passed architecture, formatting and TypeScript checks with all
3,025 unit tests. Final browser/build verification passed forty lifecycle,
offboarding and onboarding scenarios, including EN/ID layouts, narrow dark mode,
assignment selection/removal, revoked access, late results, original-command
recovery and company replacement. Desktop and narrow screenshots were inspected.

The real API/PostgreSQL/worker browser scenario created a future employee,
template and eligible coordinator, recovered a deliberately lost admission
response, then cancelled the future job and recovered that response after worker
cleanup. It verified one retained attempt, unchanged employment and no created
case. Due execution and the completed-manual-onboarding guard were validated by
the backend/worker suites, not by advancing this browser scenario's clock.

Development is migrated to V80 and runs the validated API and worker artifacts.
Their application contexts started successfully, forced RLS was verified and
readiness passed through loopback and WireGuard. Private document services and
the existing database volume were retained. The dashboard is available through
the existing WireGuard development address.
