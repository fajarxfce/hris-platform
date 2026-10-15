# Leave accrual schedules

An HR operator can review and authorize a finite sequence of monthly or annual
entitlement batches. A schedule uses the existing durable `LEAVE_ACCRUAL` queue;
it does not introduce another scheduler, recurring timer or worker lifecycle.

## Review and admission

The review selects a leave type, first processing month and 1–12 periods. Monthly
periods advance by one month; annual periods advance by one year and retain the
selected eligibility month. Processing years remain within 1900–2199. Monthly
credit becomes due at the company-local start of the following month; annual
credit is due at the selected month's start. Repeated midnight uses the later
offset, matching PostgreSQL. Admission uses the later of that due time and now,
so explicitly reviewed past periods are eligible immediately.

The response includes the company-local date, timezone, effective policy for
each period, observed policy head and at most 5,000 employee IDs. The command
requires an explicit, nonempty employee selection, observed policy version,
client-generated schedule ID, idempotency key and reason. All selected employees
must belong to the company. Every period retains the same reviewed selection;
later hires are not added implicitly. Eligibility is evaluated again by the
existing per-employee worker, not guaranteed by review.

Known effective-dated terms can vary across the selected periods, but frequency
must remain consistent and configured. The command freezes each period's policy
snapshot and current policy head. Later policy edits stop obsolete work through
the existing batch checks. They do not silently replace the reviewed terms.

`leave.accrual.post` authorizes review and admission. History and detail require
`leave.read`; these grants do not expose private profiles or job credentials.
Original and current authority are intersected after waits. Session assurance is
checked after acquisition, after queue admission and through transaction completion.
Idempotent replay retains the original receipt and still requires current access.

## Atomicity and execution

One transaction creates the immutable schedule, period links, batches, employee
targets, first attempts, jobs, audit/outbox entry and operation receipt. Admission
holds the existing batch, shared policy, People and access guards, followed by
the queue guard. The company-wide limit remains 100 pending jobs; the complete
schedule must fit. Any failure, cancellation or late assurance loss rolls back
every period. Storage enforces complete reviewed periods, due-date floors, exact
employee cohorts and immutable authorization; periods cannot be appended later.

Each period uses existing batch progress, employee outcomes, independent authorship,
current execution authority, credential checks, lease fencing, finite retries and
cancellation. The retained initial job identifies the authorization; detail joins
the batch's current job so recovery is reflected correctly. Per-period cancellation
and resume use the existing job and batch routes. Resuming future work preserves
its original due time. Completed employee outcomes remain idempotent.

Detail projects at most 12 periods and their current job progress in one SQL
snapshot, without holding job locks or blocking worker progress. The schedule
header itself is immutable. No group cancellation or implicit rescheduling is
introduced. Carry-bucket expiry remains separate; current year closing expires
excess available balance under its explicit policy.

## API

Base: `/api/v1/companies/{companyId}/leave/accrual-schedules`.

| Method and path | Input | Result |
| --- | --- | --- |
| `GET /review` | `typeId`, `firstPeriod`, `periods` | Company date, timezone, policies, employee IDs and due times |
| `POST /` | `id`, `typeId`, `firstPeriod`, `periods`, `expectedPolicyVersion`, `employeeIds`, `reason`; `Idempotency-Key` | Mutation receipt |
| `GET /` | Optional UUID `after`, `limit` 1–200 (default 20) | Bounded schedule history |
| `GET /{id}` | Schedule ID | Immutable authorization and current period progress |

Stable failures include `invalid_leave_accrual_schedule`,
`leave_accrual_not_configured`, `leave_schedule_frequency_changed`,
`leave_batch_active`, `stale_policy_version`, `leave_batch_employee_unavailable`,
`job_queue_full` and `leave_accrual_schedule_not_found`. Clients localize these codes.

## Delivery status

Backend implementation is validated. The affected API/contract set contains 133
scenarios, including rollback, overlapping admission, queue capacity, immutable
plans, current credentials/grants, MFA after waits and acquisition, cancellation,
owned input selection and future-period recovery. The initial 132-scenario run
found eleven fixture failures: MFA was disabled in the fixture's injected policy,
and three queue tests advanced only the application clock while leasing correctly
used PostgreSQL time. Strict use-case subjects and distinct due/future fixtures
fixed those assumptions; all 23 affected scenarios passed their rerun.

A separate maximum-size test passed with 12 periods and 5,000 employees, retaining
60,000 targets without posting an entitlement during admission. Admission took
5,854 ms in the local disposable PostgreSQL fixture; this is one measurement,
not a throughput benchmark. All 28 Leave domain tests and 68 worker tests passed,
along with architecture, formatting, query generation and both application JAR
builds. The [backend CI run](https://github.com/fajarxfce/hris-platform/actions/runs/38785137161)
also passed all four backend shards, all dashboard shards and real integration.

The [dashboard](dashboard.md#leave-accrual-schedules) now provides policy review,
protected admission, bounded history and current per-period progress. It preserves
one uncertain command through MFA and later conflicts; verification never replays
the command. Schedule-origin navigation survives period outcomes and recovery.

An isolated API/PostgreSQL/worker browser scenario passed. A lost admission response
recovered one schedule and one grant for the eligible employee, while the excluded
contract was skipped. The future period remained unprocessed after cancellation
and resume, with its original due time and new current job reflected in schedule
detail. Company replacement removed the former review. The fixture processes and
database were stopped. No new mobile capability is claimed by these administrative
endpoints.

Dashboard verification passed architecture, formatting, TypeScript, 3,282 unit and
component tests, and all 27 affected browser scenarios. Narrow dark Indonesian
review was checked with actual page text doubled, including the shared textarea
width correction. Two existing stylesheet advisories and the existing production
bundle-size advisory remain.
