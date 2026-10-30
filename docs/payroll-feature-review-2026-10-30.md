# Payroll requirement review

The seven initial requirements and the original implementation-status section map
to the surfaces below. This review is still active: the new command corrections
need broad final API/worker/browser acceptance and complete source CI before the
seven inventory rows can close. [Assurance evidence](payroll-assurance-2026-10-30.md)
records actual reproductions separately from pre-existing controls and fixture errors.

| Initial requirement | Implementation and evidence to retain |
| --- | --- |
| `payroll.01` | Effective policy/compensation and immutable revisions; independently verified period inputs/opening history; frozen calculation facts; staged aggregate review; fenced finalization; JSON/PDF payslips; independent payment batches, export and reconciliation; referenced amendments and revised publication. The corresponding Payroll HTTP suites use actual SQL, immutable-history constraints, RLS and transaction probes. [Workflows](payroll-workflows.md), [payments](payroll-payments.md), [amendments](payroll-amendments.md) and [payslips](payroll-payslips.md) retain each contract. |
| `payroll.02` | `calculateMonthlyPayroll` composes exact IDR earnings, reviewed employment, attendance/leave, tax and insurance inputs. Citizenship, contract type and verified tax residency remain distinct. `MonthlyPayrollPolicyTest`, income-tax, overtime, proration, THR and insurance suites cover the supported monthly permanent/fixed-term resident/nonresident cases and GROSS/NET/GROSS_UP treatment. Attendance/input/source HTTP suites prove actual retained facts feed the calculation. |
| `payroll.03` | Immutable `ID-PP58-PMK168-2024-v1`, `ID-BPJS-PU-v1`, `ID-PP35-2021-v1` and `ID-PERMENAKER6-2016-v1` keep rule selection explicit. BigDecimal amounts, exact minutes/units, statutory rounding, bounded gross-up and missing-source failures are implemented in pure policies. Dated company policies select reviewed annual caps and references; no production pension cap is seeded. Unsupported treaty, equalization, daily/piecework, sector-incentive and termination packs remain explicitly outside these selected packs. |
| `payroll.04` | Calculation/finalization workers use durable leases, versioned immutable inputs, bounded checkpoints and fenced publication. Source cutoffs prevent changing frozen evidence; withdrawal, abandonment and explicit recovery preserve history. Payment preparation/release/reconciliation require independently reviewed finalized balances and globally protected bank references. Amendments produce retained assessment revisions, deltas and confirmed-return records; they do not rewrite earlier finalized statements. Run/finalization transaction, snapshot, recovery, security and continuity suites exercise these transitions. |
| `payroll.05` | Dashboard routes provide policy, compensation, period and input preparation, tax history, calculation detail/progress/errors, review, publication, period comparison, payslips/PDFs, finance payments/returns and amendment authoring/review/publication. Four normal integration workflows and three worker workflows cover real API/database/worker behavior. Employee Mobile retains owned statements, payment progress, revision selection and revision-pinned PDFs; its earlier actual native retained-revision/offline acceptance remains scoped to the recorded source. |
| `payroll.06` | Official Tuan R/A/B/C examples verify regular/final withholding, THR/bonus, resident employment start and subjective-obligation annualization. Pure tests cover band boundaries, gross-up convergence, contributions/caps, proration, overtime and rounding. Actual SQL/worker tests cover cancellation/restart, concurrent finalization, immutable assessments and revision continuity. The separately recorded 5,000-employee [workload](measurements/payroll-2026-10-28.json) remains a historical measurement, not a fresh performance claim. |
| `payroll.07` | [Rules and source references](payroll-rules.md) identify official instruments and exact example pages, immutable rule IDs and the derived expected values. IncomeTaxPolicyTest retains the official example results: R 515,000; A 14,595,000; B -2,975,000; C 580,000 IDR. Overtime/THR cases are derived rule tests, not invented official example payslips. Fictional HTTP caps are explicitly identified and cannot establish a statutory 2026 cap. |

## Detailed surfaces and assurance boundaries

Configuration, compensation, period, input and opening history use observed
versions, current company/account grants, independent actors and original
operation receipts. Admitted collection values must survive waits unchanged;
current enrollment and recent proof must authorize interactive commands and their
receipts. The correction records concrete failures in these boundaries.

Ordinary aggregate review excludes calculation/submission makers; receiving
payroll does not itself exclude a finance reviewer. Amendment review additionally
excludes original/current beneficiaries and historical preparers. Current-stage
delegation must survive acquisition and journal completion. Retained receipt
history keeps its existing independent assignment scope after a stage advances.
Reads reacquire grants after account guards and validate final session assurance.

Finalized statement history, payment balances and assessment revisions are
separate projections. Self-service authorization follows current account/employment
ownership, encrypted client partitions and bounded synchronization. Downloaded
PDFs use the explicitly selected immutable revision. Earlier native evidence in
[amendment delivery](payroll-amendments.md#mobile-retained-revisions) includes offline
process restoration and both revision-pinned digests; an install-only phone update
does not establish fresh device UI acceptance.

## Remaining acceptance

Complete the broad Payroll API and worker selection, domain/repository and quality
checks, both application packages, four normal browser scenarios and three actual
worker scenarios. Preserve suite counts, exact command/source and package hashes.
Then pass full Platform CI, including dashboard integration and isolated recovery.
Continue Workforce and full Mobile/Starter review; Payroll acceptance alone cannot
complete the workspace goal.
