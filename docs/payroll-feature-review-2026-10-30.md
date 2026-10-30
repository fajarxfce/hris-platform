# Payroll requirement review

The seven initial requirements and implementation-status section now have complete
local acceptance. Forty genuine enrollment/input/receipt/delegation failures are
corrected. Full source CI `41087903927` at `eec8a53a` passed all ten jobs;
all seven inventory rows are accepted. [Assurance evidence](payroll-assurance-2026-10-30.md)
separates actual reproductions from existing controls and fixture errors.

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

## Local acceptance and remaining gate

Source `cca5e151` passed all 682 API cases in 95 suites, three Payroll worker
cases, 114 domain and twelve repository cases, architecture/format and both
application packages in 1,464.110 seconds. Later local commits only changed docs;
the measurement records the invocation source and completion HEAD separately.

Four normal API/browser scenarios passed in 47.453 seconds with setup, then three
actual worker/browser scenarios in 138.372 seconds. These cover preparation,
calculation/recovery, independent review, publication, retained payslips/PDFs,
payments/returns, amendment authoring/review/publication, monthly input and period
comparison. Every scenario passed without retries/skips. The owned worker database,
fixture metadata and all four listeners were removed. Both JARs and report hashes
are preserved in [measurements](measurements/payroll-assurance-2026-10-30.json).

Full Platform CI `41087903927` passed all ten jobs, including complete dashboard
integration and isolated schema-103 recovery. All seven Payroll rows are accepted.
[Downloaded evidence](measurements/reporting-payroll-full-ci-2026-10-30.json) confirms
3,063 API cases, 104 active worker cases, 1,111 mocked browser scenarios and 56
actual browser workflows. Workforce final CI continues; Mobile/Starter review is
complete. No live deployment occurred.
