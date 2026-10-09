# Payroll preparation and calculation

Period preparation, independently verified inputs, durable monthly calculation, and source cutoffs are implemented. Run approval/finalization, payslips, and payment processing are subsequent steps. A calculated run is not an approved payroll or a payment instruction.

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

To correct input, first stop a running job, then abandon its run with observed run and period versions. A completed calculation can also be abandoned before approval/finalization exists. Abandonment preserves evidence, reopens the period as `DRAFT`, and releases its source cutoffs. Start a new run to reassess corrected input; resume does not rewind an existing result. A period retains at most twenty runs. Changes to its immutable roster/payment metadata still require cancelling and replacing the draft.

The current tax-history source is an independently verified opening through the preceding month. Chaining later finalized payroll into that history, duplicate payout prevention, approval/finalization, and amendments remain subsequent work. Do not simulate that chain by overwriting a frozen opening.

## Source cutoffs

All non-abandoned runs freeze selected employee/month absence facts, monthly input heads, the selected year's tax opening, and relevant compensation/company-policy dates. Saving or verifying a consumed source returns `payroll_period_frozen`. Future effective compensation/policy revisions remain possible; an existing run continues to use its captured revision. Employment changes are independently versioned; a change before an unprocessed employee is read produces a stale-employment outcome instead of combining different revisions.

Leave submission, decisions, withdrawal, and cancellation coordinate through the same payroll guard. A frozen approved request no longer advertises cancellation in its available actions. The cutoff uses the actual charged dates, so unrelated employees and other months remain available. Entitlement grants and balance administration do not silently alter absence snapshots. Database triggers provide the same source-write boundary if an application check is omitted.

Worker lock order is job lease, payroll guard/run, then people/company/membership/account guards. Leave takes its employee ledger guard and shared payroll guard before its people/approval/access guards. Payroll never takes a leave ledger guard. Recovery reads a terminal job without taking its row lock after the payroll guard, avoiding the reverse of the worker's lock order.

## Clients

Input and history lists return metadata summaries, without the earning/deduction/resolution payload. Fetch the current detail or an exact historical revision when opened. Period details paginate participants and changes separately. Run details expose progress, attempts, and paginated result summaries; fetch full facts/calculation for one employee when opened. Default/max page sizes are 50/200.

Scope local caches by account and company; add earnings month and revision to input keys. Keep unsent changes separate from a verified server revision. After `stale_version`, `stale_employment_version`, or `stale_workforce_version`, fetch the current resource before deciding whether to resubmit. An existing operation receipt is historical confirmation, not the latest resource state. Stop obsolete requests when navigating or switching accounts/companies. Clients translate stable error and field codes, including per-employee outcome parameters. Persist the run ID after the start receipt and restore progress after reconnecting; poll only while the screen owns the observation. A queued command with a lost response must reuse its original key. The current employee sync feed does not yet publish payroll results.

API paths and request fields are listed in [API conventions](api-conventions.md). Tax, overtime, proration, THR, and opening-history policies are documented in [payroll rules](payroll-rules.md).
