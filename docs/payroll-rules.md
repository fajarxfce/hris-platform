# Payroll configuration and calculation rules

Payroll uses IDR decimal strings at the API. Account, employment, company, compensation revision, and statutory rule identifiers remain separate. Configuration does not initiate a payment or finalize a payroll period.

## Income tax

`ID-PP58-PMK168-2024-v1` contains the reviewed monthly TER tables from PP 58/2023: 44 category A bands, 40 category B bands, and 41 category C bands. Upper bounds are inclusive. The same immutable version contains the progressive annual rates, PTKP categories, job-expense deduction, and domestic PPh 26 rate. A regulatory change requires a new identifier and reviewed fixtures; salary administrators cannot edit these tables.

Regular resident periods use TER on current taxable gross. The final period reconciles annual tax against prior withholding, including a negative result when the employee is owed a refund. Annual taxable income is rounded down to thousands of rupiah; withholding uses whole rupiah rounded down. Final-period employment months determine the job-expense cap. Subjective tax-obligation months determine annualization and proportional annual tax. Starting employment later in the year does not, by itself, require annualization.

Tax residency is explicitly verified and independent of citizenship or permanent/fixed-term contract classification. Nonresidents use domestic 20% PPh 26. Treaty relief, tax equalization, daily/piece-rate work, special sector incentives, and lump-sum termination taxation require separate rule packs.

The calculation inputs separate cash earnings, exempt cash, taxable noncash benefits, deductible employee retirement contributions, qualified donations, other net deductions, and prior employer evidence. The final workflow must obtain these from verified and versioned inputs; the pure calculator does not fetch employment or prior payroll records.

| Treatment | Behavior |
| --- | --- |
| `GROSS` | The employee bears withholding and employee deductions. |
| `GROSS_UP` | A taxable cash allowance covers nonnegative withholding; employee deductions still reduce take-home pay. |
| `NET` | Taxable allowances cover nonnegative withholding and the declared employee deductions, preserving the specified cash target. |

Gross-up uses a monotone fixed-point calculation bounded to 256 iterations, with interruption propagation and an amount ceiling. It handles jumps between TER bands and selects the smallest stable whole-rupiah allowance. Refunds remain visible rather than being clamped to zero. An unresolved allowance returns `payroll_tax_allowance_unresolved`.

Official calculation fixtures:

| Source | Case | Expected final withholding |
| --- | --- | --- |
| PP 58/2023 explanation, pages 8–9 | Tuan R, K/0, monthly salary IDR 10,000,000 and pension IDR 100,000 | IDR 515,000 |
| PMK 168/2023, pages 33–35 | Tuan A, variable earnings, THR, bonus, insurance, pension, and zakat | IDR 14,595,000 |
| PMK 168/2023, page 36 | Tuan B, existing resident starting employment in September | IDR -2,975,000 refund |
| PMK 168/2023, pages 37–38 | Tuan C, Australian citizen whose resident obligation starts in September | IDR 580,000 |

Sources: [PP 58/2023](https://jdih.kemenkeu.go.id/dok/pp-58-tahun-2023), [PMK 168/2023](https://jdih.kemenkeu.go.id/dok/pmk-168-tahun-2023), and [PPh 26](https://www.pajak.go.id/id/pph-pasal-26).

## Social insurance and company policy

`ID-BPJS-PU-v1` calculates separately enabled health, old-age, pension, accident, and death contributions. It records employee and employer amounts separately. The standard rates are health 1%/4%, old-age 2%/3.7%, pension 1%/2%, and death 0%/0.3%; accident risk selects 0.24%, 0.54%, 0.89%, 1.27%, or 1.74% for the employer. Additional health dependents add an explicit employee contribution. The assessed insurance wage is explicit and is not silently inferred from citizenship or a prorated cash payment.

Company policy provides the applicable minimum monthly wage, health cap, pension cap, and contribution rounding (`HALF_UP` or `UP`). Its effective range must remain within one calendar year and include review references. No production annual pension cap is seeded. The IDR 10,000,000 pension cap in HTTP tests is a fictional fixture, not a stated 2026 statutory amount. Operators must review the applicable dated rules and enrolment before calculation. An expired policy returns `payroll_policy_not_effective`; a later year requires an explicit policy revision.

Source: [BPJS Ketenagakerjaan participant information](https://www.bpjsketenagakerjaan.go.id/penerima-upah.html). Company review references accompany the exact selected caps and rounding.

## Compensation and access

Compensation records basic salary, at most twenty distinct fixed earnings, tax treatment and verification, insurance enrolment and assessed wage, additional retirement contribution, qualified donation, and other deductions. Exempt insurance programs require a reason. Verification dates follow the company's timezone. Monthly monetary inputs are bounded to IDR 50,000,000,000; retained yearly tax inputs are bounded to IDR 600,000,000,000.

`payroll.policy.manage` controls configuration. `payroll.compensation.manage` controls salary changes. `payroll.read` allows company payroll reads. Policy management alone does not grant compensation access; `payroll.self.read` is reserved for scoped published payroll output. The new management permissions are assignable explicitly and are not added to existing default administrators.

Mutations require recent authentication and recent MFA when enforcement is enabled. Salary changes also require the observed employment version and prohibit the currently bound account from changing its own compensation. Every command rechecks live authority under the company/payroll/access guards, including replay. A request may lose permissions while waiting but cannot gain newly granted capabilities without a new request.

Policy and compensation revisions, operation receipts, audit, and outbox entries commit atomically. Optimistic versions reject competing edits. Database constraints require every header version to have its immutable revision. History and pagination retain company RLS. Failed validation or a rolled-back transaction does not consume the operation ID.

Configuration, period/input preparation, and the pure calculation library are implemented. Durable calculation runs, approval, finalization, payslips, payments, amendments, and measured performance are subsequent implementation steps.

## Earnings and pay basis

Compensation may retain a reviewed `payBasis`. It selects immutable overtime/THR rule IDs and chooses calendar/scheduled units for proration, a standard five/six-day workweek, the six-day week's shortest weekday, explicit overtime eligibility or a referenced exemption, regular nonfixed wage, a service-month convention, and whole-rupiah earning rounding. Missing settings remain missing in older revisions; they do not silently select a payroll convention. The same independent compensation permission, live access guards, observed versions, immutable history, and operation fingerprint protect these settings.

`ID-PP35-2021-v1` implements the standard monthly-wage overtime bands in PP 35/2021 articles 31–32. Weekdays use 1.5 times the first hour and twice subsequent hours. Five-day rest/public holidays use 2/3/4 multipliers across hours 1–8/9/10–12; six-day schedules use 1–7/8/9–11. Official holidays on the six-day schedule's shortest day use hours 1–5/6/7–9. The monthly base is basic plus fixed wage, raised to 75% of total regular wage when applicable. Pay is calculated from exact weighted minutes divided by 173×60; the display hourly amount does not feed the calculation. A caller must combine approved intervals on the same work date before applying bands. Time outside these standard bands requires review and is never truncated or silently treated as unpaid. Weekly time compliance, approved source evidence, meal/rest obligations, and sector exceptions belong to their corresponding workflow.

Proration consumes explicitly established total/payable units. The pure function neither treats missing attendance as absence nor decides whether leave is paid. Amounts are rounded once at the final earning boundary according to the reviewed configuration.

`ID-PERMENAKER6-2016-v1` implements monthly THR amounts: at least one continuous month of service, a prorated month of wage below twelve months, and a full month of wage after twelve months. Higher contractual amounts remain explicit top-ups. The service convention is an employer setting: complete months or exact calendar-anniversary fractions; the regulation is not represented as specifying that rounding choice. The fraction is retained as whole months and remaining days, avoiding premature decimal rounding. The permanent-contract termination window is thirty days before the holiday in the termination year; it does not extend to fixed-term contracts that end before the holiday. The due date is seven days before the selected holiday. Payout deduplication, continued-service transfer evidence, and previous-company payment checks remain responsibilities of the payroll workflow, not the amount calculator.

Sources: [PP 35/2021](https://peraturan.go.id/id/pp-no-35-tahun-2021), articles 26–27 and 31–32; [Permenaker 6/2016](https://peraturan.go.id/id/permenaker-no-6-tahun-2016), articles 2–8. Both official catalogue entries were marked in force when reviewed. Rule-band tests derive expected values from these provisions; they are not labelled as official example payslips. The profile and calculator do not initiate a payment or finalize a period.

## Opening tax history

An employment's first payroll year can start with explicit opening evidence, including verified zero history. A draft records the last covered month (`0` for no earlier month), current-year employer gross/retirement/donation/withholding totals, employment-month count, optional prior-employer net/withholding, residency, PTKP, and a review reference. Missing history is not a zero balance. Amounts remain decimal strings; unsupported negative or inconsistent history requires correction instead of being silently clamped. Nonresident opening evidence cannot request resident prior-employer reconciliation.

`payroll.calculate` prepares the draft; `payroll.review` verifies it. Both require current company access and recent authentication/MFA. The current beneficiary cannot prepare or verify their own opening. Any account that prepared a historical revision is excluded from verification, even if another preparer edited it afterward. `payroll.read`, `payroll.calculate`, or `payroll.review` permits company-scoped reads; payroll policy management and employee self-service do not confer this access.

A replacement always creates a draft revision and requires independent verification again. Verification preserves the exact submitted terms. Header, immutable revision, audit/outbox, and operation receipt commit together; the database rejects an incomplete or rewritten revision. Commands use observed opening/employment versions and idempotency keys. A replay returns its original receipt while current scope and independence remain valid. An opening retains at most 1,000 revisions; lists/history use bounded pages. Importing these facts does not calculate or finalize payroll. Run capture and finalized-period history consumption are the next integration step.

Period draft and monthly input preparation are described in [payroll workflows](payroll-workflows.md).

## Monthly composition

`calculateMonthlyPayroll` combines reviewed compensation, monthly inputs, effective employment terms, closed workforce days, approved leave, and tax history. It is a pure domain policy. Acquiring, authorizing, freezing, and persisting these facts belongs to the run use cases and repositories.

The ordinary monthly rule requires a reviewed income due date within the earnings month. A planned transfer in the following month does not move an earlier liability into that later tax month. Inputs must represent the same tax month; former-employee payments or liabilities in other months need an explicit supported correction or separate assessment. THR uses its statutory due month, so a liability from another month cannot silently join an ordinary later-month calculation. This follows PMK 168/2023 article 19; it does not imply that a planned date proves payment.

Calendar proration counts employed calendar days. Scheduled proration uses an explicit reviewed denominator and checks it against a complete monthly schedule. Approved unpaid leave reduces only its affected half-day slots. Paid leave preserves pay. Missing/absent attendance requires an explicit paid/unpaid resolution for each uncovered half; a resolution cannot override accepted work or an approved leave decision. Missing schedule evidence, ambiguous suspension, an unsupported contract, or empty employment coverage blocks calculation with a stable code.

Basic and fixed lines are prorated and rounded separately. Variable cash lines retain their reviewed amounts and tax classification. `regularNonFixedWage` is an overtime wage-base reference; its actual cash belongs in the monthly variable lines and is not added twice. Approved overtime intervals on each date share the first-hour band. Exempt employees retain their reviewed exemption. The monthly amount policy does not certify weekly working-time, meal/rest, or sector compliance.

The composition adds employer health, accident, and death contributions to taxable noncash income, excludes employer retirement/old-age contributions from that tax base, and classifies employee retirement/old-age contributions as deductible. Employee health and other net deductions remain distinct. References: PMK 168/2023 articles 5, 7, and 10. Insurance wage and enrolment remain explicit, including for a partial month. Other declared noncash values represent assessed benefit values; `employerCost` includes those values and employer contributions, rather than claiming to establish an accounting expense valuation.

Tax history must cover the preceding tax month and match the verified residency/PTKP assessment. Missing history is not treated as zero. December, an employment ending in the month, or the end of subjective obligation invokes final reconciliation; employment start alone does not shorten subjective obligation. The result retains earning lines, proration, overtime bands, THR, contributions, exact tax inputs, tax allowances/refunds, deductions, and take-home pay. Built-in component kinds and failure parameters remain unlocalized for clients.
