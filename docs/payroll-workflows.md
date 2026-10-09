# Payroll preparation, calculation, and review

Period preparation, independently verified inputs, durable monthly calculation, source cutoffs, and staged run review are implemented. Finalization, payslips, and payment processing are subsequent steps. Approval retains sign-off on calculated results; it does not establish payment.

## Periods

A period records its earnings month, planned payment date, company timezone, original author, and 1–5,000 selected employment IDs. Its `plannedPaymentMonth` describes the planned transfer, not an established tax period. PMK 168/2023 article 19 uses the earlier payment or income-liability event. Calculation therefore requires a reviewed income due date; actual bank settlement is not established by a draft. The planned payment date must fall between the first day of the earnings month and 62 days after that month's end.

One active period is admitted per company and earnings month. Its participant list and payment metadata are immutable. To change either, cancel the draft and create a replacement. Monthly inputs belong to the employment and earnings month, so replacement does not discard their verified history. At most twenty drafts, including cancelled ones, are retained for one company/month.

Period creation and cancellation require `payroll.calculate`, recent authentication/MFA, live company access, an idempotency key, and an observed version for cancellation. A cancelled period retains its roster, changes, and original receipts. Reads require `payroll.read`, `payroll.calculate`, or `payroll.review`; a generic administrator or employee self-service grant is insufficient.

The header, complete participant set, initial change, audit/outbox, and operation receipt commit together. Participant insertion is bound to the header's database transaction; a later insert cannot extend the roster. The deferred completeness check counts the roster once per creation, rather than once per participant. Listing uses finite UUID pages, including current input status for each participant.

## Monthly inputs

A prepared input references the exact closed workforce snapshot for its company, employment, and earnings month. The command carries the observed workforce and employment versions. Missing, unfinished, foreign, or changed source evidence blocks preparation. Payroll reads a scoped projection of the workforce reference; it does not edit factual workforce records or depend on their implementation module.

Inputs contain:

- Up to forty uniquely coded variable cash earnings with explicit taxable classification.
- Up to twenty uniquely coded net deductions and an explicit taxable noncash amount.
- An optional reviewed scheduled-month denominator, between one and thirty-one units in half-unit increments.
- Up to sixty-two non-overlapping half-day resolutions within the month, each declaring `PAID` or `UNPAID` and its evidence reference.
- An optional dated THR declaration, including the holiday kind/date, continuous-service start, prior payment assessment, review reference, and an optional higher promised amount.
- A required reference for the overall input review.

Empty earning, deduction, or resolution lists must be submitted explicitly. Missing attendance is not converted automatically into an unpaid deduction. Monetary values use bounded IDR decimal strings. A THR declaration does not establish payout eligibility or bypass prior-payment checks; the calculation/finalization workflow must validate those facts and prevent duplicate payout.

`payroll.calculate` prepares a draft; `payroll.review` verifies it. Neither may act on their currently bound employment. Any historical preparer is excluded from verification. Verification preserves the exact terms and workforce reference and rechecks the current employment version. A replacement creates another draft and requires verification again.

Every input retains at most 1,000 immutable revisions. Header, revision, audit/outbox, and receipt share the transaction; the database rejects missing history, changed verification terms, or an invalid workforce reference. A lost response can be retried with the same key and original payload. Authority and independence are still checked before replay.

## Durable calculation

Start from a `DRAFT` period with a closed workforce month, effective payroll policy, observed period/workforce/policy versions, reviewed `incomeDueDate`, reference, and reason. Starting requires `payroll.calculate`, recent authentication/MFA, and an idempotency key. The due date must fall in the earnings month for this ordinary monthly workflow. The run's tax month is that month; a later planned transfer does not postpone income already due. Former-employee or other-month liabilities need a separate assessment/amendment workflow.

Start retains the workforce job/version, policy revision, timezone, and at most 5,000 lightweight employee references: employment version, display labels, effective compensation revision, monthly input revision, and opening-history revision. Missing financial sources remain explicit null references and become per-employee readiness errors. A pending leave or cancellation decision on any selected actual leave date blocks start. The complete target set, first attempt, queued job, period transition, audit/outbox, and receipt commit together.

The worker processes one employee per transaction. It verifies the lease, current credential and original/live permission intersection, employment version, independently verified source revisions, and their workforce references. It combines closed workforce evidence and approved leave with the pure monthly calculator. The complete facts and line-level output are retained with the immutable outcome. Money uses decimal strings at the API. Business input errors retain safe `kind`, `code`, `fields`, and `parameters`; technical failures roll back the pending employee and use the shared finite worker retry policy. Failure classifications are not turned into successful amounts.

Each committed employee advances exactly one indexed checkpoint. The final metadata step changes the run and period to `CALCULATED` and completes the job atomically. `SUCCEEDED` on the job means the roster was processed; the run's failed count still matters. No payslip or payment is published by calculation. Snapshots are acquired one employee at a time; the worker does not retain 5,000 complete payloads. Stored facts/output are bounded to 256/128 KiB per employee.

A stopped job can be resumed by its original author with current assurance, observed run version, reason, and a new idempotency key. Resume creates another job attempt for the remaining employees and metadata step, preserving every prior outcome, including failures. Each run permits eight explicit attempts. If process retries exhaust before feature cleanup, the run may still say `PROCESSING` while the job is terminal; that terminal job is eligible for explicit recovery. A replaced or expired lease cannot commit late results.

To correct input, first stop a running job, then abandon its run with observed run and period versions. A completed calculation can also be abandoned after any pending or approved review has been explicitly withdrawn. Abandonment preserves evidence, reopens the period as `DRAFT`, and releases its source cutoffs. Start a new run to reassess corrected input; resume does not rewind an existing result. A period retains at most twenty runs. Changes to its immutable roster/payment metadata still require cancelling and replacing the draft.

The current tax-history source is an independently verified opening through the preceding month. Finalized history chaining, employee payslip delivery, payments, and amendments remain subsequent work. Do not simulate that chain by overwriting a frozen opening.

## Source cutoffs

All non-abandoned runs freeze selected employee/month absence facts, monthly input heads, the selected year's tax opening, and relevant compensation/company-policy dates. Saving or verifying a consumed source returns `payroll_period_frozen`. Future effective compensation/policy revisions remain possible; an existing run continues to use its captured revision. Employment changes are independently versioned; a change before an unprocessed employee is read produces a stale-employment outcome instead of combining different revisions.

Leave submission, decisions, withdrawal, and cancellation coordinate through the same payroll guard. A frozen approved request no longer advertises cancellation in its available actions. The cutoff uses the actual charged dates, so unrelated employees and other months remain available. Entitlement grants and balance administration do not silently alter absence snapshots. Database triggers provide the same source-write boundary if an application check is omitted.

Worker lock order is job lease, payroll guard/run, then people/company/membership/account guards. Leave takes its employee ledger guard and shared payroll guard before its people/approval/access guards. Payroll never takes a leave ledger guard. Recovery reads a terminal job without taking its row lock after the payroll guard, avoiding the reverse of the worker's lock order.

## Clients

Input and history lists return metadata summaries, without the earning/deduction/resolution payload. Fetch the current detail or an exact historical revision when opened. Period details paginate participants and changes separately. Run details expose progress, attempts, and paginated result summaries; fetch full facts/calculation for one employee when opened. Default/max page sizes are 50/200.

Scope local caches by account and company; add earnings month and revision to input keys. Keep unsent changes separate from a verified server revision. After `stale_version`, `stale_employment_version`, or `stale_workforce_version`, fetch the current resource before deciding whether to resubmit. An existing operation receipt is historical confirmation, not the latest resource state. Stop obsolete requests when navigating or switching accounts/companies. Clients translate stable error and field codes, including per-employee outcome parameters. Persist the run ID after the start receipt and restore progress after reconnecting; poll only while the screen owns the observation. A queued command with a lost response must reuse its original key. The current employee sync feed does not yet publish payroll results.

API paths and request fields are listed in [API conventions](api-conventions.md). Tax, overtime, proration, THR, and opening-history policies are documented in [payroll rules](payroll-rules.md).


## Run review

Only a fully calculated run with no failed employees can be submitted. Review retains the exact run version, participant count, taxable gross, withholding, and take-home totals. Total take-home selects the effective `PAYROLL` approval template on the submission date in the run's recorded timezone. Named and permission-based assignments are supported. A company payroll aggregate has no individual manager to resolve; manager assignment is rejected.

Submission and withdrawal require `payroll.calculate`; an assigned or currently delegated `payroll.review` account decides each stage. Every mutation requires current recent authentication/MFA, live company access, an idempotency key, and observed versions. Review versions and shared approval versions are separate: reassignment advances the approval version without editing the frozen payroll snapshot. Reads require `payroll.read`, `payroll.calculate`, or `payroll.review`.

The calculation maker and submission author cannot approve directly or through delegation. These exclusions remain in the shared approval snapshot after reassignment. A finance officer who receives payroll may approve the aggregate if neither maker. Personal compensation, monthly input, and opening history still require independent preparation and verification; aggregate sign-off does not authorize changing them.

A missing assignee blocks the shared approval until administrative reassignment. Rejection retains the decision and requires a new calculation; identical rejected results cannot be submitted to another reviewer. Pending or approved reviews may be explicitly withdrawn before finalization. Withdrawal cancels the approval and preserves its decisions. Only then can the run be abandoned to release source cutoffs. A withdrawn snapshot may be submitted again, with at most eight review rounds per run.

Review, approval transition, immutable change evidence, audit/outbox, and receipt share a transaction. The database rejects changed totals, missing history, unmatched decisions, or abandonment with an active review. Submission aggregates summary amount columns; it does not load every employee's complete JSON snapshot. Review reads return at most ten change records, and submission history uses bounded pages.

Mobile clients retain both observed versions with a pending command. A lost response can replay the original operation after current access checks. `stale_version` or `approval_changed` requires fetching the latest review before constructing a new action. A finalized/paid state is never inferred from a completed calculation or approved review.


## Finalization

`payroll.finalize` starts an explicit publication job for an unchanged, approved run. The command carries an idempotency key, a client-generated finalization ID, review ID, observed run/review/approval versions, and reason. Current company membership, credentials, recent authentication, and configured MFA are checked before both a new command and its original receipt replay. The existing aggregate maker/checker review remains mandatory; a finalizer may be a payroll beneficiary whose personal sources were independently reviewed.

Each request retains its actor, approved references, reason, job ID, and company code/name at submission. Later company renaming does not rewrite the published issuer labels. A run permits eight explicit finalization attempts. A queued/running attempt must be cancelled through the job API and reach a terminal outcome before retry or review withdrawal. A failed attempt does not undo review or silently recalculate sources. Start also respects the bounded company job queue.

The worker locks its lease before the company payroll, people, approval, and access guards. It rechecks current authority, credential version, authentication age, exact approved versions, and employment heads. A changed employment revision requires explicit withdrawal and recalculation, including a later effective revision. One person cannot receive two original assessments for the same company/tax month through different employment IDs. A positive THR result retains its holiday kind/year, with a company/person/year/kind uniqueness constraint.

One database transaction inserts thin immutable assessment references to all successful retained results, marks the finalization published, changes run and period to `FINALIZED`, retains period history, completes the job, and records audit/outbox. No employee is published early. The operation uses set-based source validation and checks completeness at the publication header, without loading 5,000 complete calculation payloads in the application. Deferred references prevent unpublished rows from committing. Cancellation, a lost lease, missing effects, or an audit failure rolls back publication.

The calculation job, counters, facts, and results remain unchanged. The run exposes `finalizationId`; the separate finalization detail exposes its own finite job progress and nullable `publishedAt`. A lost HTTP response can be recovered using the same command ID/key and payload. There is no hidden retry that changes an observed version. Finalization establishes assessment evidence, not a bank payment. Finalized payroll cannot be withdrawn, abandoned, edited, or cancelled; corrections require a subsequent referenced amendment.
