# Payroll amendments

Implementation is in progress. Published payroll and confirmed bank evidence remain
immutable. An amendment creates a separately reviewed assessment revision; it never
edits an original run, approval, payslip or payment result.

## Preparation and preview

The first stage provides two company-scoped endpoints:

| Method | Path below `/api/v1/companies/{companyId}/payroll` | Result |
| --- | --- | --- |
| GET | `/payslips/{id}/amendment-preparation` | Complete published-year references, retained remuneration and original calculations |
| POST | `/payslips/{id}/amendment-preview` | Recalculated suffix and signed differences, without writing a draft or publishing an assessment |

Both require explicit `payroll.amend` authority; default company, HR, finance and
employee roles do not gain it automatically. A beneficiary cannot prepare their
own amendment. Preparation checks current credentials and MFA; preview also
requires recent authentication and MFA. Checks run after resource/access waits
and again before private results leave the transaction.

Send `sourceVersions` containing every `assessmentId` and `revision` returned by
preparation, plus one to twelve `corrections`. Each correction identifies an
assessment and supplies proposed `compensation`, `input`, or both. The first
selected assessment must have a correction; earlier months cannot be changed by
that proposal. The ordinary one-MiB JSON request bound applies. Money remains a
decimal string in both directions.

The server acquires retained facts under the shared payroll guard and constructs
the calculation basis. Clients cannot provide calculated amounts, cumulative tax
history, work/leave evidence, policy versions or replacement tax registration.
The original immutable assessment is currently source revision zero; revised
assessment heads belong to the later publication stage.

An additional published month or changed source version returns
`payroll_amendment_sources_changed`. The caller must obtain and review the current
year explicitly. Invalid proposal membership uses
`invalid_payroll_amendment_corrections`; changed tax registration uses
`payroll_amendment_tax_evidence_changed`. Existing calculation failures retain
their codes and identify the affected assessment and tax month. Infrastructure
failures remain sanitized domain failures.

These endpoints create no approval, job, payment, operation receipt or published
revision. They do not provide the draft, approval or settlement workflows below.

## Saved drafts and calculation attempts

The draft API retains reviewed proposals separately from published payroll:

| Method | Path below `/api/v1/companies/{companyId}/payroll` | Result |
| --- | --- | --- |
| GET | `/amendments?year=2026` | Current draft summaries, filtered by employee, status and literal name/number search |
| PUT | `/amendments/{id}` | Create or revise a proposal with an observed version and review reason |
| GET | `/amendments/{id}` | Current proposal, first history page, latest calculation summary and available actions |
| GET | `/amendments/{id}/history` | Paged immutable revision summaries |
| GET | `/amendments/{id}/revisions/{version}` | One retained proposal revision |
| POST | `/amendments/{id}/calculate` | Retain a calculation attempt against the selected draft revision |
| GET | `/amendments/{id}/calculations/{calculationId}` | One retained attempt and its monthly results |
| POST | `/amendments/{id}/cancel` | Cancel an unpublished draft while retaining its evidence |

Commands require `Idempotency-Key`, `expectedVersion` and `reason`. Creation uses
a client-generated amendment UUID and a null version; editing retains its first
assessment and beneficiary. The save body also includes `firstAssessmentId`,
`sourceVersions` and `corrections` in the preparation format. Calculate and cancel
accept only the observed version and reason. A command receipt identifies the
amendment and committed revision. Reusing an operation with changed input is
rejected; an exact retry returns the original receipt after current authorization
and beneficiary checks, even after another edit, cancellation or published month.

Successful calculation changes `DRAFT` to `CALCULATED`. Editing returns it to
`DRAFT`, retaining earlier attempts and results. A completed rule failure retains
a `FAILED` attempt and `CALCULATION_FAILED` revision, with stable failure codes,
fields and parameters; the command still returns its committed receipt. The draft
remains editable. Technical acquisition or storage failure and interruption roll
back the command instead of becoming a rule result. `CANCELLED` is terminal.

Saving and calculating recheck the complete observed year. An additional published
month requires explicitly reviewing and saving updated references. Historical
reads and cancellation do not require replacing an old proposal's source versions.
There are at most 32 calculation attempts and 1,000 revisions per amendment; the
last revision is reserved for cancellation. A calculation covers at most twelve
months. List/history pages contain metadata, accept limits from one to 100 and
use stable keyset cursors. The full proposal and calculation payloads have
separate detail reads.

Read and command guards retain current company membership, credentials and MFA.
Commands also require recent authentication and MFA after waits, on replay and
through the final write. Beneficiaries cannot read or prepare their private
amendment drafts; their entries are excluded from the administrative list. Original
employee statements remain available through the existing payslip permission.

Immutable revisions, complete calculation results, audit/outbox and operation
receipts commit together. Database constraints reject missing outcome records,
partial suffixes, changed source ownership and inconsistent differences. These
private drafts create no approval, publication, bank instruction or employee-sync
revision. Those capabilities are the subsequent stages below.

## Calculation and evidence

An amendment belongs to one company and person in one tax year. It starts at an
explicit published assessment and recalculates that month and every subsequently
published month in the year, at most twelve assessments. Earlier months are retained
as tax-continuity evidence. Corrected remuneration uses the existing monthly rule
engine; no client may supply a calculated net payment or withholding result.

The preparation path accepts proposed compensation and monthly-input corrections.
Employment, closed work, leave, payroll policy and opening evidence retain their
own module ownership. Adopting a corrected source requires an explicit versioned
reference to evidence supplied by that owner. A payroll amendment must not disguise
arbitrary client values as a previously verified source revision.

Each result retains its original assessment/version, corrected facts, calculation
and signed differences. Cumulative tax history is derived in chronological order,
including unchanged later remuneration: December reconciliation can change after
an earlier salary correction. Opening and prior-employer credits are applied once.
The existing residency, registration, employment-continuity and rule-pack checks
remain mandatory. A result failure identifies its assessment/month and preserves
the original failure classification. Cancellation interrupts the bounded sequence.

Preparation and calculation bind the complete published year to its observed
assessment versions. A newly published month or another amendment invalidates
that review. It requires an explicit recalculation, never an automatic replacement
of the user's approved facts.

## Approval and publication

Draft revisions, calculation attempts and decisions are retained separately.
The intended lifecycle is draft, calculated, in review, approved, publishing and
published. Editing invalidates the calculated review; rejected or withdrawn
submissions retain their evidence. Cancellation cannot reverse a publication.
Historical preparers and beneficiaries are excluded from approval. Submission,
decisions and publication use current scoped authority, recent authentication,
observed versions and stable operation IDs.

Publication advances every affected assessment together. Its transaction includes
the amendment, revised assessment heads, financial differences, audit/outbox,
employee synchronization and the fenced publication job. Ordinary calculation and
finalization must also bind the previous assessment's revision, so an amendment
cannot silently change the tax history of an already prepared run.

## Payment differences

A calculation difference is not a bank result. Settlement compares corrected
entitlement with confirmed payments and recoveries. An unpaid positive balance
can be prepared through the existing independently released payment workflow.
Negative balances remain explicit recoverable amounts; they do not create a
negative transfer or an automatic deduction from future salary.

Prepared instructions must be cancelled and pending bank outcomes resolved before
publication changes their basis. New instructions retain the reviewed assessment
revision and current payable amount. Confirmed payments and bank references remain
immutable. A confirmed recovery needs its own scoped evidence and receipt.

## Delivery sequence

1. Guarded preparation and deterministic recalculation from retained sources: implemented.
2. Versioned drafts and retained calculations: implemented. Independent approval remains.
3. Atomic revised assessment publication, ordinary tax-continuity integration,
   payment balances and confirmed recoveries.
4. Dashboard workflows, amended employee statements/PDFs and mobile incremental
   synchronization, followed by real API/worker and device acceptance.

Each stage requires isolation, competing-change, lost-response, assurance-expiry,
cancellation and cleanup tests where applicable. The feature is not complete at
the preparation, preview or saved-draft stage.
