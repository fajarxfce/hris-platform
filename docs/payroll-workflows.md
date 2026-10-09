# Payroll period preparation

Period drafts and independently verified monthly inputs are implemented. Calculation, cutoff enforcement, run approval/finalization, payslips, and payment processing are subsequent steps. Creating a draft or verifying an input does not calculate tax or initiate a payment.

## Periods

A period records its earnings month, planned payment date, company timezone, original author, and 1–5,000 selected employment IDs. The payment date determines the intended tax month; it is separate from the month in which work was performed. Actual bank settlement is not established by this date. The date must fall between the first day of the earnings month and 62 days after that month's end.

One active draft is admitted per company and earnings month. Its participant list and payment metadata are immutable. To change either, cancel the draft and create a replacement. Monthly inputs belong to the employment and earnings month, so replacement does not discard their verified history. At most twenty drafts, including cancelled ones, are retained for one company/month.

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

## Clients

Input and history lists return metadata summaries, without the earning/deduction/resolution payload. Fetch the current detail or an exact historical revision when opened. Period details paginate participants and changes separately. Default/max page sizes are 50/200.

Scope local caches by account and company; add earnings month and revision to input keys. Keep unsent changes separate from a verified server revision. After `stale_version`, `stale_employment_version`, or `stale_workforce_version`, fetch the current resource before deciding whether to resubmit. An existing operation receipt is historical confirmation, not the latest resource state. Stop obsolete requests when navigating or switching accounts/companies. Clients translate stable error and field codes.

API paths and request fields are listed in [API conventions](api-conventions.md). Tax, overtime, proration, THR, and opening-history policies are documented in [payroll rules](payroll-rules.md).
