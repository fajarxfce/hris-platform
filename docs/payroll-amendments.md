# Payroll amendments

Implementation is in progress. Published payroll and confirmed bank evidence remain
immutable. An amendment creates a separately reviewed assessment revision; it never
edits an original run, approval, payslip or payment result.

## Preparation and preview

Preparation provides company-scoped discovery and assessment review:

| Method | Path below `/api/v1/companies/{companyId}/payroll` | Result |
| --- | --- | --- |
| GET | `/amendment-sources?year=2026` | Published employee/month references with current revisions |
| GET | `/payslips/{id}/amendment-preparation` | Complete published-year references, retained remuneration and original calculations |
| POST | `/payslips/{id}/amendment-preview` | Recalculated suffix and signed differences, without writing a draft or publishing an assessment |

All three require explicit `payroll.amend` authority; default company, HR, finance and
employee roles do not gain it automatically. A beneficiary cannot prepare their
own amendment. Discovery and preparation check current credentials and MFA; preview also
requires recent authentication and MFA. Checks run after resource/access waits
and again before private results leave the transaction.

Discovery accepts a year from 2024 through 2100, an optional employee reference,
literal name/number search of at most 120 characters, an assessment UUID cursor
and a page limit from one to 100. Results retain publication labels, current
assessment revision and its publication time. They exclude the current beneficiary
and omit compensation, calculation amounts and private profile fields. Stable
assessment-ID ordering and one-row lookahead provide the next cursor. Discovery
does not reserve a revision; preparation acquires the complete published year.

Send `sourceVersions` containing every `assessmentId` and `revision` returned by
preparation, plus one to twelve `corrections`. Each correction identifies an
assessment and supplies proposed `compensation`, `input`, or both. The first
selected assessment must have a correction; earlier months cannot be changed by
that proposal. The ordinary one-MiB JSON request bound applies. Money remains a
decimal string in both directions.

The server acquires retained facts under the shared payroll guard and constructs
the calculation basis. Clients cannot provide calculated amounts, cumulative tax
history, work/leave evidence, policy versions or replacement tax registration.
Original immutable assessments retain revision zero in a separate revision ledger.
Revised publication belongs to the later stage below.

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
| GET | `/amendments/{id}` | Current proposal, first history page, latest calculation/review summaries and available actions |
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
partial suffixes, changed source ownership and inconsistent differences. Saving
and calculating do not publish payroll, create bank instructions or advance
employee synchronization. Independent review uses the separate workflow below.

## Independent review

The review API binds an immutable calculation to a separate approval request:

| Method | Path below `/api/v1/companies/{companyId}/payroll` | Result |
| --- | --- | --- |
| PUT | `/amendments/{amendmentId}/reviews/{id}` | Submit the observed draft/calculation for independent review |
| GET | `/amendments/{amendmentId}/reviews` | Retained review summaries, at most eight |
| GET | `/amendment-reviews/{id}` | Review, current amendment, retained calculation, approval stages, decision history and action hints |
| POST | `/amendments/{amendmentId}/reviews/{id}/decisions` | Approve the current stage or reject the review |
| POST | `/amendments/{amendmentId}/reviews/{id}/withdraw` | Withdraw a pending or approved unpublished review |

Submission requires `payroll.amend`, a client-generated review UUID,
`expectedVersion`, `calculationId` and `reason`. Decisions require the separate
`payroll.amend.review` grant. Withdrawal requires `payroll.amend`. Private read
access accepts either grant or `payroll.amend.publish` while excluding the current
beneficiary. Default company, HR and finance roles do not gain amendment grants
automatically.

Decide and withdraw send `expectedVersion` for the amendment,
`expectedReviewVersion`, `expectedApprovalVersion` and `reason`; decide also sends
`decision` (`APPROVE` or `REJECT`). Approval reassignment advances its own version
without changing the amendment or review version. Every command requires
`Idempotency-Key`; its receipt identifies the amendment and committed amendment
revision. An exact retry returns that original receipt after current authority
and session checks, even when the workflow has subsequently moved on.

Configure a `PAYROLL_AMENDMENT` approval template using named reviewers or the
dedicated review permission. Manager assignment is unavailable. Historical
preparers, the submitter, the original beneficiary and the current beneficiary
cannot approve, directly or through delegation. A later account binding changes
current eligibility without rewriting the retained approval snapshot. Current
assignee grants, delegation validity, credentials, MFA and recent authentication
are checked where applicable after waits and through command completion.

Template thresholds use retained calculation exposure: for each month, take the
largest absolute difference among taxable gross, withholding, take-home pay,
employee deductions and employer cost; then sum those monthly values. Opposing
months cannot cancel each other out. Exposure is a decimal IDR amount and is not
a bank payment. Submission retains the company's date and timezone used to select
the effective template.

Submission changes `CALCULATED` to `IN_REVIEW`. Intermediate approvals keep it
in review; the final stage changes it to `APPROVED`. Rejection changes it to
`REJECTED` and requires an explicit edit and fresh calculation before resubmission.
Withdrawal returns it to `CALCULATED` and permits reusing that calculation if its
sources remain current. Editing or cancellation of an active/approved review
requires withdrawal first. Review history is retained throughout.

Submission and approval recheck the complete published year. A newly published
month requires explicit source review; rejection, withdrawal, historical reads
and authorized receipt recovery do not require replacing old source references.
Each amendment permits at most eight reviews, each with up to eight stages. A
submission reserves twelve remaining amendment revisions for bounded transitions;
the final revision remains reserved for cancellation.

Review, approval, amendment revision, immutable decision evidence, audit/outbox
and command receipt commit together. Storage rejects missing or inconsistent
components, changed review bases and invalid decision references. Technical
failure, interruption or late assurance expiry rolls back the entire command.
Approval does not publish revised assessments, change tax continuity, settle
payments or create employee synchronization records.

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

The retained target also identifies its effective predecessor revision. An
unchanged earlier month keeps its current revision; a later corrected month names
the next revision produced earlier in the same suffix. A second amendment uses the
current revised facts and amounts. It never silently substitutes a different
assessment ID or an unobserved base revision. Storage validates the opening,
predecessor reference and cumulative arithmetic independently of the calculator.
Previously retained calculations remain readable. A legacy calculation without
that revised lineage must be explicitly recalculated before publication.

The existing residency, registration, employment-continuity and rule-pack checks
remain mandatory. A result failure identifies its assessment/month and preserves
the original failure classification. Cancellation interrupts the bounded sequence.

Preparation and calculation bind the complete published year to its observed
assessment versions. A newly published month or another amendment invalidates
that review. It requires an explicit recalculation, never an automatic replacement
of the user's approved facts.

## Revised publication

The API publishes an approved amendment through a separately authorized worker
attempt. The dedicated `payroll.amend.publish` permission is not included in the
default roles. Current and originally captured beneficiaries cannot publish their
own amendment. Admission retains recent authentication/MFA through queue waits,
receipt replay and completion; the worker retains its initiating credential,
current authority, authentication age and exact lease.

| Method | Path below `/api/v1/companies/{companyId}/payroll` | Result |
| --- | --- | --- |
| GET | `/amendments/{amendmentId}/publications` | At most eight retained attempts, jobs, amendment state and recovery hints |
| PUT | `/amendments/{amendmentId}/publications/{id}` | Admit the exact approved basis and its publication job |
| POST | `/amendments/{amendmentId}/publications/{id}/recover` | Explicitly restore an approved amendment after an exhausted terminal job |

Admission requires an `Idempotency-Key`, client-generated publication ID,
`expectedVersion`, `reviewId`, `expectedReviewVersion`, `expectedApprovalVersion`,
`calculationId` and `reason`. It changes `APPROVED` to `PUBLISHING`; successful
publication changes it to `PUBLISHED`. A lost response replays the original
receipt after current authorization, even after the worker finishes. Another
operation cannot publish a second copy. Cancellation cannot reverse publication.

The worker rechecks the complete observed year, approved calculation and every
retained predecessor. It publishes the entire corrected suffix, assessment heads,
current THR ownership, amendment outcome, employee sync changes and successful
job checkpoint in one transaction. Original assessments, calculations, approvals
and payments remain retained. A following ordinary run reads the revised
predecessor explicitly; a run using the previous revision cannot be finalized.

Failed or cancelled execution restores the same approved proposal and records a
stopped attempt. An expired or replaced lease cannot publish or clean up another
attempt. Generic scheduler exhaustion may leave the amendment awaiting explicit
recovery; that command requires observed amendment/job versions and current
publisher authority. It creates no new calculation or publication. A different
authorized operator can recover an unavailable initiator's job. Source changes
still require withdrawal, explicit source review and recalculation before another
publication. There are at most eight publication attempts per amendment; admission
reserves both its start and outcome revisions before the cancellation-only slot.

## Payment differences

A calculation difference is not a bank result. Settlement compares corrected
entitlement with confirmed payments and recoveries. An unpaid positive balance
can be prepared through the existing independently released payment workflow.
Negative balances remain explicit recoverable amounts; they do not create a
negative transfer or an automatic deduction from future salary.

Prepared instructions must be cancelled and pending bank outcomes resolved before
publication changes their basis. New instructions retain the reviewed assessment
revision and current payable amount. Confirmed payments and bank references remain
immutable. A confirmed recovery needs its own scoped evidence and receipt. The implemented
[settlement contract](payroll-payments.md#confirmed-recoveries) exposes the current
entitlement, confirmed transfers/returns and observed payment version separately
from immutable individual instructions.

## Dashboard inspection

The amendment directory supports year, status and literal employee search with
bounded pages. Dedicated author, reviewer and publisher grants admit private
inspection independently of ordinary payroll reads. Details provide separate
overview, proposal, calculation and history tabs, including explicit retained
proposal revisions and the original basis of each retained calculation.

Monthly comparison preserves signed decimal differences and full original and
recalculated evidence. A later edit does not relabel an older calculation as the
current proposal. Revision navigation cancels obsolete reads; tab, language and
theme changes retain the acquired snapshot. Failed dependent reads clear private
content. These views do not save, approve or publish an amendment.

## Delivery sequence

1. Guarded preparation and deterministic recalculation from retained sources: implemented.
2. Versioned drafts, retained calculations and independent approval: implemented in the API.
3. Atomic revised assessment publication, ordinary tax-continuity integration,
   payment balances and confirmed recoveries: implemented in the API/worker.
4. Dashboard balance summaries, revision-pinned PDFs, retained-revision navigation,
   observed positive-delta payment instructions and confirmed-return entry/history
   are implemented.
   Real API/PostgreSQL/worker acceptance covers approved negative corrections,
   partial/full returns, competing balances and original-command recovery.
   Native current-revision payslips,
   independent balances, compatible offline acquisition and revision-pinned PDF
   export are implemented and validated on the emulator. The amendment directory,
   retained proposals, calculations and revision history are implemented in the
   dashboard. Amendment authoring, review decisions, publication and native retained-revision
   navigation remain. The current/retained statements and sync contracts are
   available in the API. The revised APK awaits phone connectivity.

Each stage requires isolation, competing-change, lost-response, assurance-expiry,
cancellation and cleanup tests where applicable. The feature is not complete at
the preparation, draft or review stage.
