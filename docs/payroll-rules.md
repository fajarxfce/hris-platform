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

Configuration and the pure calculation library are implemented. Period input capture, durable calculation runs, approval, finalization, payslips, payments, amendments, and measured performance are subsequent implementation steps.
