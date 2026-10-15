# Scheduled onboarding

Status: backend and worker implemented. Dashboard controls are the next increment.
This workflow prepares an onboarding case at a date chosen by HR. Task completion, account activation, company grants and
employment changes retain their existing explicit workflows.

## Reviewed case initiation

The planned resource belongs to an employment:
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

The dashboard will reuse the existing template and assignee pickers, add a dated
review from employment details, and provide retained schedule details and explicit
cancel/recover actions. Its controllers will own finite observation, scope changes
and uncertain command recovery independently of ordinary case/task editing.
