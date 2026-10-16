# Dashboard design

Follow Azure Portal shell and resource interactions using Fluent UI v9. Use an Azure blue accent with semantic tokens, light/dark themes, Segoe UI/system font fallback, and concise English/Indonesian copy.

The persistent header owns permitted-resource search, company switcher, notifications, theme, and account. A collapsible sidebar groups modules. The workspace has breadcrumb, title/status, command bar, and content. Lists have server filters/sort/pagination, optional bulk actions, and resource links. Quick inspection uses a side panel; employee profiles and payroll runs use routed detail pages with tabs. Setup/import/payroll use staged workflows.

Shared components: AppPortalShell, AppPageHeader, AppCommandBar, AppFilterBar, AppDataTable, AppDetailsPanel, AppStatusBadge, and form controls. All support keyboard navigation, focus restoration, semantic status, and responsive layouts. Tables default to 50 rows; only genuinely long client lists need virtualization.

Pages consume display state and dispatch actions. Query/cache ownership includes account and company. Company switching cancels outstanding queries and removes obsolete view data. Filters live in the URL. Dirty forms require an explicit navigation decision. Polling/listeners stop when inactive.

Primary screens: organization and employee directory; roster and attendance exceptions; leave requests/balances; approval inbox; expense claims/payments; payroll periods/runs/reconciliation; documents; announcements; reports; settings/audit/jobs.

Reference: https://learn.microsoft.com/en-us/azure/azure-portal/azure-portal-overview

## Expense claims

`/expenses/claims` provides a company-scoped directory with creation-date/status
filters and twenty-row cursor pages. Company browsing requires expense read or
management authority; team and self readers require a selected employment. Dates
use the company timezone and an inclusive maximum range of 366 days.

Current claim details keep the draft, descending change history, submissions and
payment progress in separate tabs backed by one coherent response. Changing tabs
does not repeat acquisition. History paging and explicit refresh acquire a new
review. Payment progress excludes bank destinations and transaction references.

`/expenses/submissions/:submissionId` displays immutable submitted amounts,
category rules, cost-center references and receipt metadata separately from current
claim/approval status. The approval inbox links to this submitted evidence; an
assignment does not grant access to a later draft. Item details open in a side
panel. Historical duplicate flags remain distinct from current duplicate signals.

Receipt download uses the exact submission/revision endpoint and bounded binary
transport. Only one download can be active. Cancellation, refresh, company change
and disposal abort its owner and reject late results. Access loss clears the
evidence; recoverable transport failures retain it for explicit retry. Locale and
theme changes do not fetch data again.

Assigned reviewers can approve, reject or return a submission through
`/expenses/submissions/:submissionId/:intent`. Server-provided decision availability
is separate from ordinary claim-read authority. The form reviews the submitted
items and current duplicate signals; item inspection and receipt downloads keep
the current reason intact. Rejection/return require a reason. Approving current
duplicate signals also requires explicit acknowledgement and an explanation.

The editor captures the observed claim and approval versions with one operation ID.
Repeated clicks cannot start another write. A lost response keeps the original
command for explicit retry, including after MFA recovery or a later rejection.
A definite version conflict requires protected reload before a new decision.
Access loss clears evidence without guessing the outcome of a pending write.
Form input and retry state belong to the current browser editor; leaving a dirty,
pending or unconfirmed editor requires a navigation decision. Draft authoring
remains a subsequent workflow.

## Expense payments

`/expenses/payments` requires `expenses.pay` and provides bounded
company-date/status filters. A batch's tabs share one coherent review of its
instructions, results and history. Complete bank details open in a side panel;
the directory and ordinary instruction table retain masked account numbers.
CSV download, release, cancellation and reconciliation use current server action
availability and independently authorized endpoints.

`/expenses/payments/new` selects approved claims through a dated, twenty-row
picker. Selections persist across pages, with a maximum of 100. Unavailable
operator actions and already selected claims cannot be added. Each payment has
its own bank-details panel. Account numbers remain strings, including leading
zeroes; no profile or reusable bank-account record is changed.

The operator reviews every complete destination and amount before creation.
Confirmation freezes the title, note, submission references, bank instructions,
batch/item IDs and one operation ID. Unknown outcomes retain that same command
through verification and later rejected retries. Definite initial rejection
permits explicit correction and another review; denied access hides bank data.
Selection and bank input share the page's navigation protection. Discovery and
submission stop on disposal, and obsolete results cannot enter another company.
Drafts and retry commands remain in browser memory for the editor lifecycle.

Release and cancellation review the current batch version and require a reason.
Reconciliation stages confirmed item results before one versioned submission;
unentered items remain pending. Failure requires explicit confirmation that no
funds were transferred. A prepared batch can be cancelled and replaced to correct
its instructions; a released or successful payment cannot be silently rewritten.

## Expense policies

`/expenses/policies` provides a bounded directory of current saved category
definitions, including future and inactive revisions. Status filters and code
cursors remain scoped to the selected company. Detail keeps the latest definition
separate from ascending immutable history; selecting a revision reads the acquired
page and moves keyboard focus below the sticky header.

Policy administration requires `expenses.policy.manage`. Creation and editing
include exact decimal IDR limits, transaction age, eligible contracts, receipt and
cost-center requirements, effective date and a reason. Codes remain immutable.
An inactive revision takes effect on its stated date; saving a policy does not
rewrite submitted claims or change effective-date selection.

The editor retains one operation and observed version after response loss. Retry
uses the same payload, including after a later rejection. A definite conflict
requires protected reload of the current head. Account/company replacement aborts
the owner and discards obsolete results. Drafts are held only in browser memory;
locale and theme changes do not reacquire data or reset input.

## Payroll policies

`/payroll/policy` shows the policy applying to a selected month, the latest saved
configuration and bounded ascending history. An uncovered month retains the
configuration and history. Revision inspection uses the acquired page; it does
not change the head used for editing. Read access accepts `payroll.read`,
`payroll.policy.manage` or `payroll.compensation.manage`. Company administration
and employee payslip access do not grant payroll administration.

The separate editor requires `payroll.policy.manage` and saves a reason with the
observed current version. Effective periods stay within one calendar year. Tax
rule IDs come from the backend catalog; exact decimal IDR contribution bases,
rounding and review references remain explicit. Initial setup does not invent
statutory caps. A future revision does not replace an earlier month's policy.

The controller owns cancellation and freezes one command after submission.
Response loss retains the original payload and operation for retry, including
after a later rejection. A definite version conflict requires protected reload;
confirmed access loss removes acquired policy data. The form keeps unsaved values
through locale/theme changes and protects route/company changes. Draft input is
held in browser memory, not persistent offline storage.

## Employee compensation

`/payroll/compensation` provides bounded employee discovery by name or number,
including employments without configured salary and future employments. It uses
only payroll read or compensation-management authority and never acquires a
private person profile. Selected company, search and continuation belong to the
route; employee details discard their resource selection when company changes.

`/payroll/compensation/:employeeId` separates the selected month's compensation,
the latest saved revision and bounded immutable history. A month before salary
starts retains current configuration and history. Inspection opens already
acquired evidence in a detail panel. The current employment command version is
independent of salary revisions and is not inferred from an effective snapshot.

The separate editor requires `payroll.compensation.manage` and the server's
current independent-change capability. It covers decimal IDR earnings, up to 20
fixed allowances, verified tax registration, BPJS participation and exemptions,
recurring deductions, and optional reviewed proration/overtime/THR conventions.
Initial setup leaves salary, residency, PTKP and occupational risk unspecified.
An absent calculation basis remains absent unless explicitly configured.

Saving pins both employment and compensation versions with the reason and one
operation ID. Definite version conflicts require protected reload. Response loss
retains the immutable nested command through later rejected retries. A confirmed
scope loss clears private review data; a newly linked beneficiary cannot mutate
or replay their own salary. The API remains authoritative for current authority,
verification dates and frozen payroll periods. Forms retain input across theme
and locale changes and protect departure. Drafts are held in browser memory.

## Payroll periods

`/payroll/periods` lists 20 periods per page with month and status filters, bounded
to 36 months. Period reads accept `payroll.read`, `payroll.calculate` or
`payroll.review`; unrelated administration grants do not admit payroll data.

`/payroll/periods/new` requires `payroll.calculate`. Preparation selects an earnings
month, planned payment date, reason and 1–5,000 employees. The minimal employee
lookup includes future and unconfigured employments without acquiring private
profiles. Its 50-row pages retain selection across searches; the selected roster
is also paged. A separate review presents the exact selection before submission
and moves focus to its visible heading without animation.
Creation freezes the roster and payment metadata. Changing them requires cancelling
the draft and preparing a replacement; calculation eligibility is checked later.

`/payroll/periods/:periodId` separates the period properties, 50-row participant
pages and 20-entry history pages. Tabs reuse the acquired detail. Participant
names and input heads reflect the current read; retained employee IDs define the
immutable roster. `/payroll/periods/:periodId/cancel` reviews the current draft and
submits its observed version with a reason.

Response loss retains the original command and operation through later rejected
retries. Definite cancellation conflicts require protected reload. Company/account
replacement cancels outstanding work and drops obsolete selection; confirmed access
loss removes acquired private data. Forms retain values through locale/theme
changes and protect departure. Draft input remains in browser memory.

## Monthly payroll inputs

`/payroll/inputs` discovers employees by name, number and input status for a selected
earnings month. The 50-row pages include unprepared inputs without acquiring
private profiles. Reads require `payroll.read`, `payroll.calculate` or
`payroll.review`; unrelated administration grants do not admit payroll data.

`/payroll/inputs/:employeeId` combines the current employment and attendance source,
input terms and 20-entry immutable history pages. Overview/history tabs reuse the
acquired review. A side panel loads an explicitly selected historical revision.
Closing it, refreshing or replacing the account/company cancels obsolete reads.

The separate editor supports variable earnings, net deductions, explicit taxable
non-cash income, a reviewed scheduled-month denominator, half-day resolutions and
dated holiday allowance evidence. Amounts remain exact IDR decimal strings. New
inputs require explicit values; the form does not invent missing attendance or
statutory declarations. Saving requires `payroll.calculate` and pins the observed
employment, closed attendance and input versions with one reason and operation ID.

The verification page shows the exact revision and submits its observed version
with `payroll.review`. Current beneficiaries and all historical preparers are
excluded. Missing or changed sources and frozen inputs remove unavailable actions;
the API independently revalidates eligibility at command time.

A lost response retains the original immutable command through later rejected
retries. Definite conflicts require protected reload. Confirmed access loss removes
private evidence. Locale/theme changes retain form values, and draft departure is
protected. Editor state remains in browser memory. Tables scroll within their
panels on narrow screens.

## Opening tax history

`/payroll/tax-openings` discovers employees by name, number and opening status for
an explicit tax year. Its 50-row pages include unprepared employees without
acquiring private profiles. Reads require `payroll.read`, `payroll.calculate` or
`payroll.review`, independently of company administration.

`/payroll/tax-openings/:employeeId` separates the current opening and 20-entry
immutable history pages. Tabs reuse the acquired review; inspecting a revision
loads its retained terms in a side panel. Changing the year, account or company
cancels obsolete work and discards the prior scope's selection.

The editor requires explicit residency, PTKP, completed-through month, employment
months, annual totals and a review reference. Missing history is distinct from a
confirmed zero declaration. IDR amounts retain decimal precision and annual
limits. The server enforces completed months using the company timezone.

Preparation requires `payroll.calculate` and the observed employment and opening
versions. Verification requires `payroll.review` and the exact reviewed revision.
Current beneficiaries and all historical preparers cannot verify; a tax year
already fixed for calculation cannot be changed. Server-derived action hints do
not replace command authorization.

Definite conflicts require protected reload. Lost responses retain the original
operation and payload through subsequent rejected retries. Confirmed access loss
clears private data. Locale/theme changes preserve form values; pending, dirty and
unconfirmed commands protect departure. Drafts remain in browser memory.

## Payroll calculations

`/payroll/periods/:periodId/calculations` shows the current preparation review and
up to twenty retained runs. The review contains closed attendance and effective
policy references without acquiring employee profiles or requiring policy
administration. Reads accept `payroll.read`, `payroll.calculate` or
`payroll.review`; calculation commands require `payroll.calculate`.

`/payroll/periods/:periodId/calculate` reviews the period and its source versions,
then requires an income due date within the earnings month, review reference and
reason. Missing prerequisites remain explicit. Confirmation captures those
versions with one run ID and operation ID; reading the page does not reserve a
job or change the source records.

`/payroll/runs/:runId` separates employee results, job attempts and source
references. Results use pages of fifty summaries. Tabs reuse the acquired
snapshot; opening an employee fetches only that employee's retained evidence and
calculation. Returning preserves the result-page cursor. Failed employees retain
their failure code and nullable amounts, even when the job has processed the
entire roster. A completed calculation does not establish payment.

One visible run owns one request and at most thirty automatic progress reads,
four seconds apart after completion. Reads do not overlap. Hidden documents,
disposal, errors and terminal jobs stop observation; exhaustion offers explicit
Refresh. Temporary connection failure can retain the current authorized results.
Access denial or lost assurance clears them. Account/company replacement aborts
pending work and discards the previous scope; theme and language changes do not
recreate the controller.

Employee detail uses structured earning, contribution, overtime, THR, tax and
source sections. Decimal IDR amounts retain precision, including refunds smaller
than one rupiah. Missing evidence remains absent rather than becoming a zero
calculation. The source tab displays retained attendance and leave facts without
performing new payroll calculations in the browser.

Resume and abandon have separate reason/review pages. Resume captures the run
version; abandon captures both run and period versions. Only the original maker
can resume a stopped attempt; completed results remain intact. Correcting sources requires a stopped job and
explicit abandonment, which reopens the period without deleting its history.
Active aggregate reviews must first be withdrawn; finalized runs cannot be
abandoned. Job inspection/cancellation uses the independently authorized Jobs
screen. Current server action hints determine the available recovery links.

Unknown command outcomes retain the original operation and payload across
explicit retries, including after later conflicts. A definite changed-state
rejection requires review before creating a new command. Pending forms and
unconfirmed results use the workspace departure guard. Browser command retention
is in memory. Published payslips and finance payments have separate scoped
workflows described below.

## Payroll aggregate review

`/payroll/runs/:runId/reviews` acquires review preparation and up to eight retained
review rounds. Totals include only successful employee results; incomplete or
failed calculations cannot be submitted. The separate submission page captures
the reviewed run version, a new review ID, reason and one operation ID. Routing
is resolved by the current approval policy when that command is admitted.

`/payroll/reviews/:reviewId` displays retained totals, current assignment stages
and immutable decision history. Parent links appear only after the owning run or
period is known; loading does not redirect to an unrelated directory. Tabs reuse
the acquired detail. Current company
names label the captured account IDs without acquiring private profiles. The
calculation link opens the original employee results; the shared Approvals screen
links back to this payroll workflow for a decision.

Submission and withdrawal require `payroll.calculate`; assigned/delegated
`payroll.review` accounts approve or reject a stage. Server action hints preserve
maker exclusions, stage eligibility and active finalization restrictions. Decision
pages capture both review and approval versions, because reassignment can change
only the latter. Approving an intermediate stage records a decision without
claiming final approval. Rejection requires a new calculation; withdrawal keeps
history and permits a new bounded review round.

Owned requests are cancelled on departure or account/company replacement. Unknown
outcomes retain the original operation and body through explicit retries and later
MFA/conflict responses. Definite version conflicts require a fresh review before
another command. Reasons and pending commands remain only in mounted memory;
locale and theme changes do not restart acquisition or discard the input.

## Payroll finalization

`/payroll/publications` lists fifty completed calculations or finalized runs per
page, with earnings-month and status filters covering up to 36 months. Reads
accept `payroll.read`, `payroll.calculate`, `payroll.review` or `payroll.finalize`.
Finalization authority does not grant employee-result, input, approval-review or
payslip access. Related links respect those separate capabilities.

`/payroll/runs/:runId/finalizations` shows current preparation, reviewed totals,
blocking issues and up to eight retained publication attempts. The separate
`/payroll/runs/:runId/finalize` review requires `payroll.finalize` and a reason.
Confirmation captures the approved review ID, observed run/review/approval
versions, new finalization ID and one operation ID. Preparation creates no
records and does not reserve source readiness or queue capacity.

An uncertain response keeps that original command through explicit retry, renewed
MFA and later conflicts, including after the worker has finalized the run. A
definite first conflict requires another review before a new command. Drafts and
unconfirmed commands use the existing workspace departure guard and remain in
browser memory. Company/account replacement cancels owned work and clears private
context; locale and theme changes retain the current form and observation.

`/payroll/finalizations/:finalizationId` shows its own job outcome, captured issuer,
reason, publication time and retained totals. A cancelled attempt stays cancelled
when a later attempt publishes the same run. One visible detail owns one request
and at most thirty delayed progress reads, four seconds apart. Observation stops
on terminal state, error, disposal or a hidden document. Transport failure may
retain authorized context; lost access or assurance clears it. Exhaustion offers
explicit Refresh. Publication success is announced to assistive technology.

Job inspection and cancellation use the existing Jobs workflow; the link requires
job ownership or `jobs.read`. Published payslips and bank settlement remain distinct.
Finalized payroll corrections require an amendment rather than editing its
immutable result.

## Published payslips

`/payroll/payslips` provides fifty retained summaries per page, a month range of up
to thirty-six months, and submitted employee name/number search. Search uses the
labels frozen at publication. Changing filters resets the cursor; returning from
detail retains the directory filters. Company replacement clears the old search.
`payroll.read` permits company-wide reads. `payroll.self.read` readers use explicit
`SELF` scope, and finance readers may narrow to their own employments. Calculation,
review, finalization and generic administrative grants do not grant payslip access.

`/payroll/payslips/:payslipId` displays retained earnings, tax, deductions, insurance
contributions, overtime and holiday output. Amounts remain decimal strings through
mapping and formatting. The overview identifies its loaded assessment revision.
Payment history is a separately versioned projection with up to twenty attempts,
each retaining its amount and assessment revision. Its summary carries its own
entitlement, confirmed transfers/returns and payable or recoverable balance, so a
newer payment read cannot silently change the displayed statement. Its refresh
does not reacquire the immutable calculation;
the planned payment date is not a settlement confirmation. Tabs, locale and theme
changes retain the current read owner.

An optional `revision` selects one retained statement, including revision zero.
Previous, next, original and latest links preserve company, read scope and
directory filters; navigation acquires one revision at a time. Next is offered
only when current payment evidence identifies a later assessment revision.
The return-review link preserves a selected revision when navigating back.
Refreshing a retained statement keeps that revision. A newer payment projection
offers explicit navigation without replacing the open statement.

Malformed, duplicate or unavailable revision selections do not fall back to
current data. Response mapping requires the requested version. Changing revision
cancels its previous read, payment acquisition and PDF download; late responses
cannot restore an earlier owner. Current and retained reads keep the same
server-side employee/company authorization.

PDF downloads select Indonesian or English explicitly and pin the displayed
assessment revision throughout acquisition. The filename includes that revision.
A single GET acquires the
generated file using its declared length and strong entity tag, with a one-MiB
limit and the normal finite HTTP deadline. Mapping verifies the SHA-256 digest;
file handoff belongs to the file repository. Partial, failed and handed-off
buffers are cleared, and browser object URLs are released. Downloads require an
explicit action and never retry automatically. Read/PDF failures are localized;
confirmed access or assurance loss clears private detail and cancels other work.
Account/company replacement and disposal reject late results.

## Payroll payments

`/payroll/payments` provides a finance directory filtered by company-local
creation dates and status, with twenty batches per page and a maximum 366-day
range. `payroll.pay` and current recent authentication are required. Calculation,
publication, payslip and expense grants do not provide payroll finance access.
Detail tabs share one coherent batch review; activity has its own ascending
cursor. Amounts remain exact decimal strings. Bank-result mapping preserves
PostgreSQL timestamp precision when validating retained evidence.

Preparation selects up to one hundred distinct finalized payslips through bounded
publication-date discovery. Employee labels, tax months and planned dates come
from retained assessments. The amount is the current positive payment balance.
Selection freezes that amount and the observed assessment/payment versions; the
review displays the payslip revision. A revised assessment cannot silently replace
those original instructions during retry. Finance enters bank destinations, reviews the
complete instructions, and submits one immutable batch. Account numbers remain
strings, including leading zeros. The command cannot replace salary amounts.
Cancellation retains a prepared batch's history and permits replacement
instructions. Release requires a different account from its preparer; the server
rechecks current authority, evidence and observed versions.

Reconciliation stages confirmed outcomes for pending instructions. A successful
payment requires a bank reference; a failed payment requires explicit confirmation
that no funds were transferred. Confirmation time uses the company timezone.
Unentered or uncertain results stay pending, and the batch closes only when every
item has a confirmed outcome. The CSV worksheet is bounded to 256 KiB and is
available only while all released instructions remain pending. Downloading it
does not initiate or confirm a transfer.

Preparation, release, cancellation and reconciliation retain their original
operation, identifiers, payload and observed versions after an uncertain response.
Verification never resubmits a business command. A definite first conflict needs
an explicit fresh review; a later rejection cannot replace an already uncertain
command. Input and command retention are in memory. Company/account replacement
cancels pending work and discards its owner. Confirmed access loss removes bank
details. Tabs, theme and language changes retain the current owner.

### Confirmed payroll returns

Payment item details link to `/payroll/payslips/:id/returns`, with current
entitlement, confirmed transfers/returns, remaining balance and up to twenty
immutable return records. Finance can inspect this evidence without a payslip
read grant. An authorized payslip also links to the same review. Company and
account ownership are retained in the route and request owner.

The `/returns/new` form records a bank-confirmed partial or complete return.
The review supplies eligibility, observed assessment/payment versions and the
earliest confirmation instant. Amounts use exact decimals; confirmation input
uses the company timezone and rejects invalid or ambiguous local times. The API
rechecks current authority, beneficiary, versions, balance and its own clock.

A definite initial conflict preserves input and requires explicit reload. An
uncertain outcome freezes the original identifier, operation and evidence through
later authorization failures or conflicts; only an explicit retry resends it.
Navigation protection covers input, pending writes and unconfirmed results.
Confirmed access loss clears private evidence; disposal cancels pending work and
rejects late results. Draft and command retention are in memory. Theme and language
changes retain the form, and narrow layouts stack the review panels.

## Payroll amendments

`/payroll/amendments` provides annual, status and literal employee name/number
filters with bounded keyset pages. Authors, independent reviewers and publishers
can inspect this directory through their dedicated amendment grants, without
ordinary payslip or compensation read authority. Company and search context stay
attached to detail and return navigation.

The detail page separates overview, proposal, retained calculation and revision
history. Historical proposals use an explicit revision in the URL; invalid or
missing revisions cannot silently open the current proposal. A calculation keeps
its original draft version and provides a link to that basis, including after a
later edit. Proposed compensation and monthly inputs reuse the existing term
views. Monthly comparison and full original/recalculated evidence retain exact
decimal values and signed differences.

Tabs, comparison selection, theme and language changes reuse acquired data.
Refresh, revision and history-page changes cancel obsolete acquisition; company
or account replacement disposes the owner. Failed private reads clear their
dependent evidence, and internal diagnostics never appear in product messages.
History pages are bounded by the aggregate version observed for that acquisition.
`/payroll/amendments/new` provides a bounded annual source picker under
`payroll.amend`. Selecting a published month opens its complete retained year;
months before the selected first month remain read-only. Existing editable
proposals use `/payroll/amendments/:id/edit`. A month panel reuses compensation
and input fields, retaining the published tax registration. The owning page
protects both applied corrections and unapplied panel input on departure.

Preview is required before saving. Changing a correction invalidates that
preview; changing the reason does not repeat financial acquisition. New published
revisions require explicit review before replacing a draft's observed sources.
A conflict retains input and offers deliberate reload. An unconfirmed save pins
its proposal ID, operation and complete payload for explicit original-command
retry, even if a later attempt is rejected. A confirmed save links to its exact
proposal revision. Forms live in the account/company workspace; this browser
flow does not provide durable offline drafts.

Current proposals expose calculation and cancellation reviews under
`/payroll/amendments/:id/calculate` and `/cancel`, independently authorized by
`payroll.amend`. Each command captures the reviewed version and required reason.
It retains its original operation after response loss, without acquiring a new
version before replay. Initial conflicts require deliberate reload; withdrawn
access clears private review and form values. Reload protects unsaved input.

A calculation receipt acknowledges a retained attempt, including a failed rule
check. Its result link opens the exact committed proposal revision and calculation
tab. Cancellation retains proposal and calculation history. Historical revisions
cannot offer commands against a different current version.

`/payroll/amendments/:id/reviews` shows at most eight retained review rounds.
The separate submission page requires `payroll.amend`, a current successful
calculation and an available submission action. It captures the amendment version,
calculation, new review ID, reason and original operation. The receipt identifies
the amendment; successful navigation uses the separately captured review ID.

`/payroll/amendment-reviews/:id` displays retained amounts, current assignment
stages and immutable decisions. Proposal and calculation links open the submitted
revision; history links open the amendment revision recorded by each decision.
Parent links appear after acquisition. Directory reads reject mismatched review
versions when a submission or decision changes between the two API reads.

Assigned independent reviewers use `payroll.amend.review` to approve or reject;
authors use `payroll.amend` to withdraw. Separate action pages acquire live hints
and capture amendment, review and approval versions with a required reason.
Intermediate approval records a decision without claiming final approval.
Withdrawal retains the review and allows another eligible submission. Shared
Approvals navigation opens this workflow through its review resource ID.

Uncertain commands retain their exact payload through explicit retry, MFA renewal
and later conflicts. Initial conflicts require protected reload. Confirmed access
loss clears private evidence and entered reasons; company/account replacement
cancels owned work. Tabs, theme and language changes do not reacquire data. These
forms remain in browser memory.

`/payroll/amendments/:id/publications` lists retained attempts; `/publish` reviews
the approved amendment, calculation and approval versions before admission.
The independent publisher grant permits commands without granting authorship or
review authority. The admitted publication ID remains distinct from the amendment
receipt, including after a lost response and worker completion.

`/payroll/amendments/:id/publications/:publicationId` separates the captured basis,
attempt outcome and worker progress. Historical proposal/calculation links retain
their exact revision. Progress reads are visible-only, sequential and capped at
30 updates; errors or terminal jobs stop observation. Manual refresh starts a new
budget. `/recover` closes an exhausted pending attempt only after its current job
and amendment versions are reviewed. It does not start another publication.
The [publication contract](payroll-amendments.md#dashboard-publication) describes
scope, recovery and original-command handling.

## Shift templates

The Workforce route owns a bounded shift directory, current details, and create/edit
forms. Templates include overnight times, breaks, IANA timezone, work mode, active
state and location requirements. A reason accompanies each observed-version change.
Existing assignments retain their saved shift snapshots, including after a template
is edited or deactivated.

Forms keep one immutable operation through an unknown outcome and MFA renewal.
Only an explicit retry resends it. Definite conflicts require a protected reload;
discarding edits also resets fields when the server values have not changed.
Company changes cancel request owners and cannot restore old company content from
a late response. Read-only grants hide mutation controls, and use cases enforce
the same client permission boundary before transport.

## Company holidays

`/workforce/holidays` provides a date-filtered directory, current details and
versioned create/edit forms. The initial range uses the company's current year;
each query includes at most 366 calendar dates. Date-only values are formatted
without shifting them into the browser timezone. Both active and inactive records
remain visible. Active holidays override weekly schedules; roster overrides retain
precedence, and closed work periods cannot be changed.

Reading requires `company.read`; authoring additionally requires `workforce.manage`.
The form retains one operation and payload after an unknown response, including
through MFA renewal. An explicit conflict requires reloading the current revision.
Route and company changes own cancellation and protected draft departure. This is
an in-memory browser workflow, without durable draft storage or automatic retries.

## Employee calendars and scheduling

`/workforce/calendars` selects employees with bounded name/number search and an
explicit company date. The calendar shows at most 62 days, including overnight
hours, timezone, planned time, and the source of each assignment. Its separate
weekly-pattern table retains the original shift revisions at the range start.

Reading requires `workforce.read`; editing also requires `workforce.manage`.
Weekly changes take effect at the selected range's first date and use the global
schedule version, including future assignments. Later effective assignments keep
their dates. A daily roster instead uses that date's own observed version; an
explicit day off remains a versioned override of the weekly pattern and holidays.

Shift selection holds one catalog page and at most seven selected snapshots.
Searching or paging does not replace a selection with a newer template revision.
Changed templates require explicitly selecting an active revision. Definite
conflicts require protected reload; unknown outcomes retain the exact operation
and payload through MFA renewal and explicit retry. Company changes cancel the
previous owner and discard its query cache. Browser drafts are not durable storage.

## Attendance review and corrections

`/workforce/attendance` selects employees without acquiring private profiles.
Employee pages show at most 31 dates; a day displays frozen shift details, captured
and received times, location evidence, review reasons and the latest correction.
The employee reference and attendance are independently authorized reads.

The dashboard requires `workforce.read`. Verification additionally requires
`attendance.verify` or `attendance.team.verify`; corrections require
`attendance.correct`. The API independently enforces current scope, reviewer
independence, period status and observed versions. Pending evidence must be
resolved before correcting a day. Whole-minute correction inputs use the company
timezone, including overnight intervals, and preserve original capture evidence.

Editors retain one immutable command during an uncertain outcome. MFA renewal
does not send it again; explicit retry uses the original operation and payload.
Definite conflicts require a protected reload. Account/company changes abort the
owner and suppress late results. Overtime and period closing remain separate screens.

Each day links to `/workforce/attendance/{employeeId}/days/{workDate}/history`.
The page keeps one descending page of at most 50 immutable corrections, including
explicit absences, original reasons, recording accounts and company-local times.
Its detail panel reads the selected page without another profile request. Date
filters survive the return to attendance; page cursors cannot cross company scope.
Refresh and route replacement remove the prior projection and cancel pending reads.
MFA recovery does not automatically reacquire history. Locale/theme changes only
reformat the authorized page, and unmount discards its query cache.

`/workforce/attendance/exceptions` provides the company-wide review queue under
`workforce.read`. A bounded date range and pending/incomplete filter select one
50-row page. Each employee-day opens the existing, independently authorized day
view; the queue does not infer absence or authorize a decision. A later pending
event on a corrected day remains visible until independently resolved.

Company replacement discards filters, cursor and pending results. A filter change
returns to the first page. Refresh hides the previous projection; MFA recovery
requires an explicit fresh read. The query is disposed on departure, while
language/theme changes only reformat its current immutable page. Employee names
and numbers share one column so actions fit the desktop review table; the same
table scrolls within its own container on narrow layouts.

## Implemented foundation

`apps/dashboard` provides password sign-in, configured SSO entry links, session bootstrap, authenticator enrollment/verification, and explicit acknowledgement of one-use recovery codes. The portal includes current-company selection, an authorized company overview, organization directories, parent details and editing, employee onboarding, directories, employment history, dated revisions and scheduled revision cancellation, separately authorized personal profiles with editing/history, lifecycle template catalogs and checklist editing, scoped case/checklist/history views, assigned-task queues, versioned task changes and assignment, company/group headcount reports, audit metadata search, client policy review and versioned editing, a job monitor with cancellation, employee CSV intake, result review and guarded apply/resume/cancel actions, responsive navigation, and English/Indonesian light/dark interfaces. Other business resource screens listed above remain subsequent work; the overview does not invent statistics.

Feature datasources validate transport DTOs; repositories map them inside the HTTP failure boundary; use cases own policy; the identity controller owns requests and presentation state. Pages render that state. Shared `AppXxx` components wrap Fluent controls. The import checker rejects domain/framework dependencies, data access from presentation, repository peers, and use-case peers. These static checks complement review of orchestration and resource ownership.

The same-origin transport uses session cookies and fresh CSRF values for commands. It bounds responses to 1 MiB, owns request deadlines, propagates cancellation, and never retries a mutation automatically. Controllers clear private cached state during company/account changes and reject obsolete responses. Foreground/reconnect revalidation uses the same request owner. Forms clear passwords after submission, and enrollment secrets/recovery codes are not placed in browser storage. An expired enrollment requires an explicit new setup operation.

Tabster is pinned to `8.8.0`, which shares Keyborg 2 with the installed Fluent focus adapter. Tabster `8.8.1` changes that dependency to Keyborg 3 while Fluent `react-tabster@9.26.18` still requires Keyborg 2. Loading both can collide in their window-global instance registry; browser validation exposed incorrect-disposal diagnostics. Remove the override when Fluent and Tabster agree on one Keyborg major, after rerunning focus and lifecycle tests.

Application messages translate stable failure codes; unknown codes receive a generic localized message and an optional correlation reference. Backend diagnostic text is never displayed. Language and theme controls remain available before authentication.

## Session verification and retained forms

The application owns the verification dialog across workspace removal. Closing clears its contents immediately, pending content changes keep focus inside, and cancellation restores the triggering control. Feature portals mount inside the retained workspace. Detail and confirmation overlays suspend with its visibility, so pending or failed identity checks cannot leave a private drawer visible or trap focus away from recovery controls.

Foreground/reconnect checks retain one previously authorized workspace in memory while its content is hidden and inert. An unchanged account, company membership set, company metadata, platform grants, and selected-company grants restore the same mounted feature owners and controlled inputs. Equivalent ordering does not invalidate that scope. A changed account/company/permission partition, known revocation, logout, or application disposal removes it and clears private query state. Business values are not refreshed by this identity check; each feature retains its explicit Refresh action.

Expired MFA responses deliberately redact memberships and grants. The client does not interpret those redacted arrays as an authorized empty scope or restore the previous view from them. A Fluent verification dialog keeps the workspace hidden until a complete session read and live company-access read both succeed. Configured accounts can also request verification from the header or a supported authorization failure. Cancelling an optional challenge rechecks the session; a mandatory challenge cannot be dismissed into an unverified workspace. Initial enrollment and one-use recovery-code acknowledgement use the same form controls as initial sign-in.

Verification owns no business command and never resubmits one. If proof delivery is uncertain, Check session reads the current cookie session before another proof is sent. A successful proof followed by a failed access read retries only the read. Revoked credentials or company access purge the former form. Verification codes clear after submission and on stage changes; retained form state is not written to browser storage. Feature bindings can report a credential/company-scope failure through the workspace session contract; the identity owner revalidates access without retrying the feature command.

The authorized workspace owns one pending navigation decision. Editors declare dirty, pending, or unconfirmed changes; route navigation, Back, company selection, and header logout require an explicit Leave or Stay decision. Reload/close uses the browser's native unsaved-changes prompt. Identity checks suspend pending decisions, and scope changes discard them with the editor. A security/recovery screen keeps sign-out available. These prompts protect browser memory only; confirming departure or closing the process does not retain a recoverable draft or prove that a pending server operation was cancelled.

The keyboard skip control moves focus to the owned main content element without adding a native hash-history entry. This keeps browser history inside the router's navigation protection.

## Company and group headcount

`/reports/headcount?asOf=2026-10-01` loads the [group headcount API](reporting.md), initially selecting the active company. The default date uses that company's time zone. The controlled form can select one to 32 companies and keeps edits separate from the applied URL filters. Apply stores the date and a sorted `companies` selection; browser history restores both. The report shows distinct people, employment records, status, and contract totals with the source definition's historical limits. A group also displays company-level counts. The unique-person total comes from the backend and is never summed from company buckets.

The route is loaded on demand. Its controller owns one company-selection/date request and rejects obsolete results. Applied filter changes, account/company changes, route disposal, and changed identity scope remove the old report and cancel pending work. An unchanged foreground identity check preserves the mounted view while it is hidden pending authorization. Refresh clears old counts before loading; access failures cannot leave previous or partial company values visible. Language/theme changes only reformat the current view. Navigation reflects the active company's `reports.read` and `people.read` permissions, while the server independently authorizes every selected company. The selector lists current memberships without treating them as report grants. Direct links still pass through validation and server authorization. Response mapping checks the exact selection, date, definition, company bucket sums, and distinct-person bounds before displaying immutable values.

`AppCommandBar`, `AppMetricCard`, `AppPropertyList`, `AppMultiSelect`, and `AppResourceTable` provide reusable Fluent layouts. Native form controls inherit the selected color scheme. Filters use React Hook Form's controlled adapters so Back/Forward navigation updates the displayed values. The company selector supports keyboard interaction and validation focus. Tables keep numeric columns aligned and contain horizontal overflow within their panel.

## Audit explorer

`/administration/audit` uses the [company audit API](audit.md). The form supports explicit UTC start/end times, action/resource codes, and exact actor/resource IDs. Blank dates use the server's default window. A resource table shows metadata, and `AppDetailsPanel` shows full IDs and timestamps without another request or profile lookup. Action codes remain exact searchable identifiers; interface labels and failures are localized. The shared table and panel use Fluent's focus restoration attributes for keyboard navigation.

The controller retains one page of at most 50 events. Older-page navigation writes the exact server-resolved window, filters, company scope, and cursor to the URL. Browser Back/Forward reloads the relevant page. First page retains that window; Reset filters returns to the default search. Form edits use UTC seconds, while pagination preserves server microseconds. Response mapping verifies scope, window, filter matches, ordering, uniqueness, size, and cursor progress. Clock instants with nanoseconds are compared at database precision.

There is no polling or cumulative page cache. Refresh, filter changes, company/account changes, and unmount cancel previous requests and remove the page and details. Stale successes and failures are ignored. A company change drops a cursor belonging to the previous company. Revoked access cannot leave old events visible. Navigation visibility is only a client convenience; the use case and API independently enforce audit permission.

## Client policy review

`/settings/client-policy` shows the [company settings snapshot](client-policy.md): effective modules, minimum builds, maintenance status, evaluation time, and the latest configured revision. A scheduled head remains distinct from the revision currently enforced by the server. The revision form accepts an explicit immutable revision from 0 to 9999; its company and selection live in the URL, and Back/Forward restores the controlled input. The latest revision needs one settings request; a named historical revision uses a second authorized read.

The feature requires `settings.manage`. Its controller owns one pending review, cancels both stages on replacement/disposal, and clears configuration on refresh or access failure. Company changes discard the previous company's revision selection. Repository mapping validates bounded builds, distinct modules, maintenance intervals, version relationships, and coherent effective values before creating immutable entities. Locale/theme changes only reformat the loaded snapshot. Refresh is explicit; this screen has no polling or local policy cache.

## Client policy editing

`/settings/client-policy/edit` loads the latest configured head through the same guarded settings snapshot, including when opened from an older history entry. The form keeps the configured revision separate from the effective revision. It supports explicit immediate/scheduled activation, eight module switches, minimum Android/iOS/Web builds, a bounded maintenance window, and a required reason. UTC timestamp fields retain the exact acquired precision without conversion through the browser timezone. A future head initially retains its schedule; an already effective head proposes immediate activation. Selecting immediate activation creates a higher revision that supersedes older schedules.

The domain validates bounded builds/revisions, supported distinct modules, UTC ranges, maintenance duration, and safe reasons before I/O. Current activation time, permission, MFA, and version checks remain server decisions. Data maps a complete replacement payload and requires a receipt identifying the selected company at the next revision. A first configuration uses a null expected version and returns revision zero. The displayed dashboard build comes from the same application configuration used by the HTTP transport.

The controller pins one configured version, immutable payload, and operation key. A definite conflict requires an explicit protected reload; validation failures leave the form editable. An uncertain outcome remains read-only through verification or subsequent activation/version rejections until explicit retry recovers the original receipt. Saved-revision navigation is a separate read. Requests, field values, reasons, and pending commands are owned by the current workspace and discarded on departure or revoked scope. Locale/theme changes and equivalent identity revalidation retain the form; explicit reloading resets it even if the server returns identical values.

Settings remain independently authorized during maintenance or minimum-build restrictions. The editor displays the current dashboard build and explains a proposed Web minimum that exceeds it. Authorized operators can save another immediate revision to restore availability; changing a flag never grants resource permissions. The browser does not persist commands or perform automatic retries.

## Job monitor

`/administration/jobs` uses the [company jobs API](jobs.md). Members retain access to their own jobs; `jobs.read` selects the company-wide list. A bounded page shows kind, status, progress, and creation time. Exact continuation timestamps/IDs and an optional selected job live in the URL. Details load the canonical job and show schedule, eligibility, progress mode, attempts, version, and safe failure information. Unknown job kinds remain readable without assuming new actions are supported.

The list and details own separate cancellable requests. The details controller requires explicit confirmation before one observed-version cancellation. A missing response clears obsolete details and requires a status read; there is no automatic command retry. An acknowledgement displays cancellation requested until a worker reaches a terminal state. Accepted detail snapshots update matching visible rows by version, preserving their DOM and keyboard focus. Refresh and disposal clear owned data; account/company changes reject obsolete work. `AppConfirmationDialog` uses Fluent's controlled dialog and trigger for focus ownership. There is no polling or accumulating cache.

## Organization directory and unit details

`/organization/units` reads the [organization API](modules/people.md) with a literal name/code query, unit type, active status, and bounded `KIND:CODE` continuation. The controlled filters remain separate from applied URL values; Back/Forward restores them. The page retains at most 50 units. Branches, departments, positions, and cost centers use one resource table. The client validates unique identities, kind/code keys, selected filters, and cursor progress without substituting JavaScript order for PostgreSQL collation.

A unit opens `/organization/units/{id}`. One authorized API read returns the unit and its current parent under a common structure guard. Opening that parent is an explicit navigation and new read; the browser does not recursively load a tree or fetch every parent to decorate the directory. Inactive references remain visible. Current organization metadata does not claim the historical meaning of an employment revision.

Both routes require `company.read`; `company.manage` alone does not grant read access. Direct links are pinned to the selected company before I/O, and switching company from a detail returns to its own directory without retaining the former ID, filters, or cursor. Each controller owns one cancellable request, clears data on refresh/disposal, rejects late results and failures, and retries only on request. Parent and unit disappear together on failure. Locale/theme changes only reformat current values. Shared Fluent tables, property lists, and `AppSelect` provide responsive, labelled controls.

`/organization/units/new` and `/organization/units/{id}/edit` require both read and management grants. The editor supports name/code, parent selection, branch time zone, and active status; an existing unit's kind is immutable. The parent picker owns a separate bounded page with explicit search, type/status filters, and continuation. It excludes the edited unit and unsupported parent kinds without loading the complete organization. The backend independently validates current ancestry, depth, kind, availability, and IANA zones.

Save captures one immutable payload, the loaded version, and an operation ID. A known pre-commit rejection permits correction; a stale version requires an explicit, protected reload. A missing or invalid response keeps the submission read-only until an explicit retry obtains its original receipt. Once an outcome is uncertain, a later MFA or version rejection does not make editing safe. Verification never repeats Save. Acknowledgement and subsequent detail loading are separate, so a failed detail read cannot replay a successful command. Pending work is aborted on disposal and late results cannot restore a previous company. Cancellation of the browser request does not imply server rollback.

## Employee directory and employment details

`/people/employees` reads the [scoped employee API](modules/people.md) with an explicit effective date, initially today in the selected company's time zone. The controlled form applies a name/employee-number query of up to 120 characters. Company, date, query, and employee-number continuation live in the URL; Back/Forward restores applied filters. Pages contain at most 50 records without an accumulating cache. Employee-number order belongs to the database collation; the client verifies bounded, unique records and the returned continuation without imposing a different JavaScript order.

An employee opens a routed detail page with an overview and, for `people.read`, an employment-history tab. Company-wide, current direct-report, and self visibility remain server decisions. Historical dates never restore former-manager authority. The overview shows the effective employment terms and the current public name/email projection. Birth dates, nationality, account linkage, and organization IDs are not retained by this client projection. Authorized users can open a separate personal profile. Authorized operators can open the employment editor to review current assignment references and propose a dated revision.

History loads only when its tab is opened and has its own bounded request owner and descending revision cursor. Next pages contain revisions strictly below the previous page's last revision; zero terminates the sequence. Revision zero, gaps, scheduled changes, and immutable cancellations are preserved. A Fluent detail panel displays the selected revision's terms, reason, recorded time, and cancellation evidence without another request. The overview and history are independently authorized live reads; they do not claim a common transaction snapshot. History navigation never treats the latest aggregate version as the revision applied on a historical date.

Directory, detail, and history controllers clear their owned data before refresh and on disposal, ignore late successes/failures, and perform no polling or automatic retry. A history response reporting loss of the enclosing company/session scope also clears the employee overview. A history-only permission rejection does not infer loss of a separately authorized public employee projection. Direct links are pinned to the current company/date before acquisition. A company switch from an employee detail returns to that company's directory, discarding the previous employee and cursors. URL filters remain distinct from unsubmitted form edits. Locale/theme changes only reformat retained values. `AppTabs` uses Fluent tab selection and labelled panels; revision drawers restore keyboard focus through the shared table/panel components.

## Employee creation

`/people/employees/new` requires `people.manage`. The form captures a new person's legal name, optional birth date/email, nationality, an employee number, initial employment terms, optional organization/manager assignments, and a reason. The first effective date is the start date. The shared pure person policy normalizes profile fields; contract and date policies reject invalid proposals before I/O. Initial account binding and lifecycle task assignment remain separate workflows.

Assignment drawers call organization and employee domain use cases. They open only on request, retain one page of at most 50 results, and use explicit search and continuation. Organization choices require `company.read` and an active matching kind; manager choices require a readable employee scope and working employment on the proposed start date. The server rechecks assignment eligibility under its own guards. TanStack Query owns the consumed abort signal with retries, foreground/reconnect refresh, and retained inactive caches disabled. Changing scope or disposing the drawer cancels pending work; a failed refresh removes the displayed results.

The creation controller owns two resource identifiers and one frozen command per attempt. Its receipt must identify the employee at version zero. A definite first rejection allows correction with a new operation key; an uncertain response pins the original identifiers, key, and payload through later verification or rejection. Only explicit retry can resolve it. The form uses the shared departure protection and workspace verification boundary. The acknowledged result is separate from any subsequent detail read, whose effective date includes a future start. An operator without directory access can create an unassigned employee without acquiring employee/profile directories. Draft personal data and reasons stay in memory and are discarded on departure or revoked scope.

## Employment revisions

`/people/employees/{id}/employment/edit` requires `people.read` and `people.manage`. One scoped API request returns the employment at the selected effective date, its current aggregate version, and current assignment references. The form preserves missing or inactive assignment IDs until the operator explicitly replaces or clears them. Reference labels describe the current organization, not historical organization snapshots. Private person fields and account binding are excluded.

The original employment start date is read-only and pinned by the controller. A proposal contains its effective date, end date, contract, status, organization assignments, manager, and a reason. Shared domain rules validate dates and contract constraints. The assignment picker reuses the bounded onboarding flow; manager choices use the proposed effective date and exclude the edited employee. Organization browsing still requires `company.read`. The backend independently validates current references and the reporting graph.

Save captures the current aggregate version, not the historical applied revision, together with one immutable operation and payload. A definite conflict requires protected reloading before another proposal. Unknown outcomes remain read-only through MFA or later rejection until explicit retry acknowledges the original receipt. Acknowledgement and detail loading have separate ownership. The saved detail link selects the proposal's effective date. Scope replacement and disposal abort pending work, clear private state, and ignore late completions. Locale/theme changes retain dirty fields and assignment choices; no form data is persisted in browser storage.

## Employment transfers

`/people/employees/{id}/transfer` requires source `people.read`, `people.manage`
and `people.transfer`. Choosing a destination loads the [scoped transfer
review](modules/people.md#cross-company-transfers). Both dates come from the
server; this workflow performs an immediate transfer. Destination employee
number, contract, end date, status, assignments and reason form the proposal.
Assignments begin empty and use the destination's independently authorized
directories. Source assignments and role grants are not copied.

Confirmation pins the reviewed source version, destination employment ID and
operation ID. A lost response retains that exact proposal for explicit recovery,
including through MFA renewal or a later rejected retry. Definitive version/date
conflicts require a protected review reload, which clears discarded fields.
Locale/theme changes retain edits. Source workspace authority and a destination
access failure remain distinct; changing scope disposes pending reads and commands.
Busy confirmations retain keyboard focus while preventing duplicate actions.

The employment detail's Transfers tab loads at most two immutable incoming/outgoing
records on demand. It requires company-wide `people.read`; a counterpart name is
shown only from the current session's company memberships. The successful command
links to source history without acquiring a destination profile. Browser commands
remain in memory, rather than claiming durable offline submission.

## Scheduled employment revision cancellation

`/people/employees/{id}/revisions/{revision}/cancel` requires `people.read` and `people.manage`. A history drawer links eligible scheduled revisions to a separate review. That review acquires the selected revision, current aggregate version, current company date, and available action in one guarded API snapshot. The server's date and capability determine whether cancellation can be submitted; the historical view date and browser clock cannot authorize it. Revision zero, effective revisions, and existing cancellation evidence remain readable without a cancellation action.

The controller pins the reviewed employee, revision, and aggregate version; the form supplies only a reason. A successful cancellation creates a new aggregate version and retains the original revision and cancellation in history. A definite version or availability conflict requires protected reloading. An uncertain result retains the original operation and reason through MFA or subsequent already-cancelled responses until explicit retry recovers its receipt. No status read, automatic retry, or locale change can silently replace an unresolved command. Pending requests and private form state belong to the company-scoped controller/form lifetime; disposal ignores late completions.

## Personal profiles

`/people/employees/{id}/profile` acquires the current sensitive profile through its separate API. Directory and employment screens never preload this data. `people.profile.read` permits sensitive reads; `people.self.read` permits only a profile the server identifies as the caller's own. History requires the explicit sensitive-read grant, even when a direct URL asks for it. Current membership names can label the owning company without another cross-company request. Viewing a shared person never grants management of the owning company.

The overview shows legal name, birth date, nationality, email, ownership, and account/person identifiers. Its version belongs to the person profile, independently of the employment version or selected employment date. The optional history tab loads at most 50 immutable revisions with an ascending cursor. Null initial attribution, historical account linkage, reasons, and exact recording times remain intact. First/next and Back/Forward navigation use the URL. Profile and history are separate authorized live reads, not a claimed common transaction snapshot. Invalidating sensitive history access clears the enclosing private overview; refresh and disposal remove old values and cancel pending work.

`/people/employees/{id}/profile/edit` requires readable profile access, `people.profile.manage`, and the owning company. It edits only personal fields with a required reason. Account linkage and ownership are not form inputs. Blank optional dates/emails map explicitly to null; names/reasons are trimmed, nationality is uppercase, and email is lowercase. The server validates recognized ISO country codes and birth-date policy. Stable field errors receive localized messages without rendering technical details.

Each submission captures the loaded profile version, person ID, payload, and one operation ID. Receipt mapping expects that person ID and the next profile version, not the employee ID. Ambiguous saves remain immutable until explicit recovery; a later MFA/version rejection cannot authorize a different payload. Version conflicts require a protected reload. Verification, subsequent detail reads, and business submission have separate owners, and none automatically repeats a mutation. Shared navigation protection covers dirty or pending changes. Sensitive fields, reasons, and command snapshots stay in memory and disappear on disposal or scope revocation.

## Commands

The shared HTTP transport sends `X-HRIS-Client-Platform: WEB` with the compiled `HRIS_DASHBOARD_BUILD` (default `1`, bounded integer). Set this build-time value consistently for distributed dashboard artifacts. Company policy can require an update or pause business requests during maintenance; availability errors have English/Indonesian messages. Company preflight screens remain subsequent business workflows.

From `apps/dashboard`:

```sh
npm ci
npm run dev
npm run check
npm run build
npx playwright install chromium
npm run test:e2e
# Requires JDK 21 and Docker, with the backend built first:
../../gradlew -p ../.. :apps:server:bootJar --no-daemon
npm run test:integration
```

`check` runs import boundaries, Biome, strict TypeScript, and transport/controller tests. `test:e2e` launches an isolated local Vite server and runs the real client composition against owned API fixtures, including delayed responses, company-switch cancellation, form cleanup, and mobile keyboard navigation.

`test:integration` owns a temporary PostgreSQL container, runs the real API jar with separate migration/runtime credentials, and tests password errors, MFA enrollment, recovery codes, explicit MFA renewal with CSRF rotation and retained inputs, company switching, literal organization search and parent navigation, organization creation with a deliberately lost committed response and explicit receipt recovery, versioned deactivation, employee creation with organization assignments and lost-response receipt recovery, employee directory/detail/history reads, personal profile editing and history with an unchanged employment version, lifecycle template edits, case initiation with lost-response receipt recovery, task completion/reopening, member lookup and assignment, case cancellation, onboarding completion, and offboarding review/completion with a concurrent employment change and lost-response receipt recovery, employee CSV selection and template download, preview submission with lost-response recovery, parsed row proposals, attempt history, and queued import cancellation with lost-response recovery, company/group headcount including an empty company, filtered audit metadata/details, configured/effective client policy and history, scheduled job reads/cancellation, language selection, and completed server logout. It requires free loopback ports 18080 and 4174, tears down its processes/container, and keeps local diagnostics under `.work/dashboard-integration`. The fixture disables external providers, mail, storage, and scanning; no real OIDC provider, delivery service, or production deployment is exercised. No worker processes its scheduled announcement fixture. CI runs the browser suite independently from API integration. The integration job consumes the checked backend JAR from the same workflow run, preserving separate finite job budgets without rebuilding the server.

Group headcount validation passed the architecture/Biome/TypeScript checks, 33 unit/controller tests, a production build, 12 browser tests, and one real-API/PostgreSQL browser test. Browser checks cover pending-selection cancellation, scope changes, Back/Forward filters, localized failures, and light/dark responsive layouts. The initial browser fixtures used the wrong ARIA role for Fluent's multiselect entries; they now select `menuitemcheckbox`. The report route remains lazy-loaded. These checks do not claim device validation or runtime performance benchmarks.

The development proxy forwards `/api`, `/oauth2`, and `/login/oauth2` to `127.0.0.1:8080`; `HRIS_API_PROXY` can select another development API origin. No authentication token is exposed to JavaScript configuration. Use the backend's documented local cookie configuration for HTTP development and HTTPS with secure cookies for deployment. Production output is in `dist`; hosting must forward these paths and serve the SPA entry for client routes.


## Lifecycle templates

`/people/lifecycle/templates` lists the active company's templates, with routed details and creation/versioned editing. Read access requires `people.lifecycle.read`; creation requires `people.lifecycle.manage`. Editing an existing template additionally needs its authorized read. A manage-only operator can create a template without listing other templates or acquiring employee records.

Each template has a stable code and onboarding/offboarding type, a name, active status, and 1–64 tasks. Task keys are unique; due offsets range from −90 to 365 days relative to a case's target date. The editor pins an existing template's identity and observed version, while each save includes a new reason. Existing cases retain their selected definitions when the template changes. Starting cases, assignment, and completing tasks remain separate dashboard workflows.

Lists retain one page of at most 20 templates because each response includes the bounded task definitions. The exact server cursor and company live in the URL. Detail reads use the [scoped template API](modules/people.md#lifecycle-checklists), rather than searching accumulated pages. Response mapping rejects unrelated IDs, duplicate task/template identities, malformed fields, and stalled page continuations. Database code ordering remains authoritative.

React Hook Form owns dynamic task controls with stable field-array IDs; a task binding subscribes to its own controls. Removing a row preserves the remaining task values. The editor limits additions to 64 tasks and prevents removing the last task. Locale/theme changes and equivalent MFA renewal retain controlled values. Company/account changes dispose the feature owner and its private state.

One immutable command captures the idempotency key, expected version, and copied task values. Double submission is excluded. A definite version conflict requires an explicit reload; a lost response retains the original command through subsequent MFA or conflict failures until its receipt is recovered. Navigation protection covers dirty, pending, and unconfirmed states. Acknowledgement is separate from opening the current template; no automatic replay or durable browser draft storage is implied.


## Lifecycle case and task views

`/people/lifecycle/cases` lists company cases with status and optional employment filters. The URL owns the selected company, filters, and exact cursor; changing company discards the old selection. Each page contains at most 10 cases, including their bounded checklists, to stay within the shared 1 MiB JSON limit. `/people/lifecycle/cases/:id` opens the current employee reference, frozen template version, tasks, and separately loaded history. Case and history reads require `people.lifecycle.read`. History keeps one page of 50 transitions, with an accessible detail panel for reasons and actor/assignee account IDs. A revoked history read also invalidates the enclosing case overview.

`/people/lifecycle/tasks` shows only pending tasks assigned to the active account, using `people.lifecycle.perform` or `people.lifecycle.manage`. It does not acquire company cases, profiles, or employee-directory records. One page holds up to 50 tasks and uses the exact case/task cursor. Task panels pin the observed case version and minimal employee reference. Closing a case uses the separately authorized case overview.

Feature use cases enforce client admission and bounded query policy. Datasources return validated transport DTOs; repository mapping checks identities, immutable task state, filter compatibility, and cursor progress inside the HTTP failure boundary. Controllers own requests and selections; bindings own routing and disposal. Superseded work cannot restore a prior page/company, and locale/theme changes do not refetch unchanged data.


## Lifecycle task changes

A task panel supports completion and reopening by its current assignee with `people.lifecycle.perform`, or by a lifecycle manager. Only managers can waive optional tasks; required tasks cannot be waived. Read-only users, another assignee, and closed cases retain a details-only panel. Every command includes the pinned case version, task key, a required reason, and one operation ID. The API remains authoritative for current permissions, assignment, task requirements, and case state.

The controller retains one immutable command during submission or an uncertain outcome. Repeated clicks cannot create additional requests. A retry recovers the original receipt using the same operation/payload and fresh CSRF credentials; verification never repeats a task command automatically. A later rejected retry cannot make an earlier uncertain save editable. Definitive conflicts require an explicit departure and a fresh task list before another change. Closing a dirty, pending, or unconfirmed panel uses the workspace departure protection; disposal aborts the request and ignores late results. Successful acknowledgement closes the panel and refreshes the owning case or assigned-task queue. Controls that open a nested departure dialog declare Fluent focus-return targets, including the shared panel close button. Staying restores focus inside the retained panel rather than the table behind it.

The domain owns transition availability and command validation. The data boundary maps the command and verifies that the receipt belongs to the same case at the next version. Controllers own the submission lifecycle, form hooks own fields and departure registration, and bindings connect acknowledgement to the parent read owner without controller-to-controller dependencies. Reasons and account IDs remain inside the authorized workspace and its detail panel.


## Lifecycle task assignment

Lifecycle managers can assign or remove the assignee of a pending task in an open case. Assignment has a separate panel from status changes; moving between panels protects unsaved fields and retains the observed case version. Removal is explicit. The current assignee is excluded from replacement choices, and no member is selected by default. Perform-only users and resolved tasks have no assignment action. A manage-only operator can use the panel from their own queue without acquiring company cases or employee profiles.

The member picker opens on demand and uses the [scoped assignee lookup](modules/people.md#lifecycle-checklists). It shows only names and account IDs, supports explicit name search, and retains one page of at most 50 results. Account, company, permissions, and applied filters scope the query. Pending reads consume cancellation; disposal removes the inactive cache, and a failed refresh clears previous choices. Lookup results are observations: the server rechecks current membership and eligibility when assigning. No identity-administration endpoint is required.

Saving pins the case/task/version, chosen account or explicit removal, reason, and operation ID. A definitely rejected candidate may be replaced; a stale case requires reloading. An uncertain assignment keeps its original command and choice through MFA renewal or later rejection, until retry recovers its receipt or the operator explicitly leaves. Acknowledgement closes the panel and reloads its owning case or queue. The controller, form hook, and binding retain separate request, field, and navigation ownership.

## Starting lifecycle cases

Employee details expose **Start lifecycle case** when the operator has lifecycle read/manage permissions and directory read access. `/people/employees/:employeeId/lifecycle/new` reacquires that exact employee through the existing scoped use case, at the selected effective date. It does not acquire private profile or identity-administration data. The company and employee remain fixed for that form; switching company returns to its employee directory. The API independently requires lifecycle management and validates employment eligibility on the proposed target date.

The template picker opens on demand and retains one page of at most 20 definitions, scoped by account, company, and permissions. Only active definitions can be selected. Pagination and refresh are explicit, including pages without active templates; no background catalog scan or search over a partial page is implied. Closing the picker cancels its work and releases the cache. The selected name, kind, version, requirements, and calculated due dates remain available for review. Date offsets use calendar arithmetic without converting them to the browser's timezone. New tasks start unassigned and can be assigned through the existing task panel.

Starting pins a new case ID, operation ID, employee, template version, date, and reason. A definite template conflict permits an explicit new selection while preserving other fields. An ambiguous result retains the original command through verification and later rejection until its receipt is recovered or the operator explicitly leaves. Double submission, navigation protection, and disposal use the existing workspace ownership rules. The repository accepts only the matching initial version-zero receipt. Acknowledgement provides a separate **Open case** action; it does not silently refetch or create another case. Starting offboarding creates a checklist; it does not complete termination or revoke access.

## Scheduled onboarding

`/people/employees/{id}/onboarding-schedules` lists up to eight retained attempts.
Employee details expose this link only with People read and lifecycle read/manage
permissions. The employee directory's company, effective date and search context
are retained. Future employees are selected using their first working date.

`/new` first acquires the employment, then offers an active onboarding template
and preparation date. Explicit review obtains the server's employment/template
versions, execution window and blockers. A bounded company member picker assigns
or removes a responsible person per task. Selecting the task opens that picker,
including at narrow widths. The reviewed selection, copied assignee IDs, reason,
and separate operation/schedule/case identities are fixed before submission.

`/{scheduleId}` shows retained business dates, checklist, status and minimal job
progress. References are expandable. A successful initiation links to the open
case; task completion remains independent. `/{scheduleId}/cancel` and `/recover`
review current capability and job versions before an explicit command. An
uncertain result retains the exact original request through MFA and later
conflicts; no refresh or selection change silently replaces it. Denied access
clears private view/form state, and disposal rejects late results.

A visible detail observes only due or cancelling active jobs, with one request
and thirty delayed updates at most. Hidden, failed, terminal and disposed views
stop observation; future schedules load on demand. Account/company owners remain
independent of locale and theme changes. See [scheduled onboarding](onboarding-schedules.md)
for server authorization, transaction semantics and validation limits.

## Cancelling cases and completing onboarding

The case overview exposes **Cancel case** to lifecycle managers who can read the company case. An open onboarding case also exposes **Complete onboarding** once every required task is done and every optional task is done or waived. The review panel retains the employee, frozen template, target date, and case version, and requires a reason. Task and case editors are mutually exclusive; refresh cannot replace the reviewed snapshot while either panel is open. Current requirements, permissions, and versions remain authoritative on the API.

Cancellation closes either kind of checklist and removes pending tasks from active queues while preserving their states and history. Completing onboarding records a completed checklist; it does not change employment terms or grant access. Closed cases remain readable without another closing action. Offboarding completion uses the separate review described below because it affects employment and company access.

Each action uses its own use case and endpoint. A shared receipt mapper verifies the same case at exactly the next aggregate version for task, assignment, and closing transitions. One immutable command owns the reason, observed version, and operation ID. An ambiguous response stays read-only through account verification or a later rejection; retry sends the original command with fresh CSRF credentials. A definite version or task conflict requires a protected reload before another action. Acknowledgement closes the panel and refreshes its case. Closing or leaving cancels owned work, discards late results, and uses the existing workspace departure and focus-restoration behavior. Command recovery is confined to the mounted workspace, without automatic retries or durable browser storage.

## Offboarding review and completion

An open offboarding case exposes **Review offboarding** when the operator has lifecycle read/manage and people manage/offboard grants. `/people/lifecycle/cases/:caseId/offboarding` obtains the [guarded review context](modules/people.md#lifecycle-checklists) directly. It shows the current checklist, minimal employee reference, both observed versions, last working date, and company date returned by the server. It acquires no private profile or identity-administration data. A company change discards the former case and returns to the selected company's case list.

The page explains that completion ends the employment from the current company date and deactivates company access if no other current or planned employment remains. Required work must be done, optional tasks must be done or waived, and the last working date must precede the server-supplied company date. The browser clock does not decide that date. Remaining employment/access conditions and recent authentication stay authoritative on the completion endpoint. Scheduled revisions, reporting dependents, other open cases, self-offboarding, and the last administrator produce localized actionable failures. A review is an observation rather than a reservation; completion rechecks current conditions.

The controller owns one review request or immutable completion command. It captures the case version, employment version, reason, and operation ID. A definitely stale version or changed business condition requires a protected reload. An uncertain completion retains its original command through MFA renewal and later rejections, even when the case has already closed on the server. Explicit retry recovers the matching next-version receipt without reacquiring the case first. Successful acknowledgement replaces the review with confirmation and a separate **Open case** action. Navigation protection, disposal, and obsolete-result rejection cover reads, writes, and company changes; form fields remain in workspace memory and survive equivalent verification and locale/theme changes.

## Employee import review

`/people/imports` provides company-scoped import directories; `/people/imports/:importId` shows the observed header and row counts. Both require import, people-management, and private-profile read/manage grants before acquisition. The API rechecks live scope and current MFA after waiting for resource/access guards. Switching company from a detail returns to the selected company's directory.

Rows and attempts are acquired only when their tabs are opened. The directory and attempt pages contain at most ten items; row pages contain at most 25. URL cursors restore Back/Forward navigation and must advance monotonically. Each controller owns its request, clears retained data on refresh/disposal, and rejects obsolete success or failure. Losing row/attempt access also removes the retained overview. Refresh is explicit; there is no background polling or browser persistence of private proposals.

The overview presents counts from one API snapshot. Individual result pages are subsequent live reads. Row details retain the submitted proposal, localized field issues, and any created employment identity. A business-invalid proposal remains readable, including dates outside the supported import range; transport mapping does not apply employment-acceptance policy again. Malformed transport, contradictory outcomes, unsafe versions, count mismatches, duplicate items, and non-progressing cursors are rejected inside the HTTP failure boundary. Completed status means processing finished, so the overview calls out applied, invalid, and rejected counts. [CSV intake](#employee-csv-intake) and [import transitions](#employee-import-transitions) are implemented separately.

Fluent detail panels now size against the available viewport width, including the space reserved for a browser scrollbar. The mobile review checks final panel bounds and keyboard focus restoration after closing.

## Employee import transitions

The import overview includes job status, cancellation state, and actions from the authorized API summary. Dedicated `/people/imports/:importId/apply`, `/resume`, and `/cancel` routes acquire a fresh review and require all four import grants. Company changes return to the selected company directory without reusing the previous import identity.

Apply requires explicit confirmation before skipping invalid rows. Cancel requests that further work stop; it does not undo applied employees or claim that a queued/running job has already stopped. Resume is available only when the summary permits it and queues the remaining work under current authority. The server remains authoritative for counts, versions, access, and job state.

Each transition owns one immutable operation, observed import version, reason, and optional partial-apply choice. A lost or malformed reply retains the original command through MFA renewal and later stale/terminal responses. Verification never replays it automatically. Definite first-attempt conflicts require a protected fresh review; reloading resets the partial confirmation. Acknowledgement clears the old review and offers an explicit link to current progress. Leaving or changing scope aborts pending work and rejects late results.

## Employee CSV intake

`/people/imports/new` selects a UTF-8 CSV, records a reason, and queues a preview in the current company. A preview does not create employees; operators review its rows before explicitly applying it. Template downloads use the authenticated API and its bounded CSV representation. The existing backend parser remains responsible for headers, quoting, field syntax, and the 5,000-row/1,024-character-cell limits.

The file datasource owns the native picker, bounded reader, and download dispatch. The repository coordinates raw selection/read operations and maps technical failures; use cases own CSV eligibility and template/read/download orchestration. A file must have a valid `.csv` name of at most 120 characters and contain 1–524,288 bytes. Reads have a 15-second deadline and preserve UTF-8 BOM and original CSV content. Native cancellation returns to editing without an error; replacing or removing a selection cannot submit the previous file.

Public presentation state contains file metadata only. One controller owns the CSV and one immutable submission until acknowledgement or disposal. No CSV is logged, cached in browser storage, or automatically retried. Unknown delivery retains the exact CSV, reason, import identity, and operation ID through MFA renewal; a later rejection does not reopen editing until the original outcome is acknowledged. Known initial validation/encoded-size failures permit correction with a new operation. Leaving or changing account/company scope aborts pending acquisition and removes the retained file. This is in-memory recovery, not durable offline submission.

The browser starts a bounded text download without a retained object URL. Its status confirms dispatch, not completion of a save to disk. The HTTP transport's 1 MiB encoded JSON limit can require a smaller file when escaping expands the request; it rejects that condition before CSRF or command delivery.

## Approval inbox and request stages

`/approvals` presents the current account's bounded inbox, including blocked requests available to approval administrators. `/approvals/:approvalId` reads the submitted request, effective stage assignments, and current status. Authors and beneficiaries can open an authorized direct link without an inbox grant; the backend resolves ownership and delegated access.

Pages retain company-bound cursors and acquire at most twenty requests. Invalid or non-progressing pages, mismatched identifiers, oversized assignments, and malformed timestamps become safe failures. Controllers cancel superseded reads and clear snapshots during refresh, access failure, and disposal. Company replacement discards the previous request and cursor. Screens support English/Indonesian, dark mode, and contained mobile tables.

This view does not issue a generic approval decision. Leave, expense, overtime, and payroll workflows own their decisions and atomic business consequences. The displayed stages are the current assignment projection, not an invented decision history.

## Approval template administration

`/approvals/templates` requires `approvals.manage`. Its twenty-item pages retain company, approval kind, effective date, and cursor. Detail links expose exact rule revisions. Current name, active status, and editable version are distinguished from the selected rules revision; editing always fetches the latest configuration. Cross-company navigation discards the previous record and cursor.

The editor supports up to eight ordered manager, named-account, or permission stages. Payroll excludes manager assignment. Changing a new template's approval kind resets incompatible stages. Named stages select up to twenty-five accounts through a cancellable, ten-item lookup; only active company accounts with the relevant decision permission are returned. Account identifiers are retained when reopening a saved definition. Display names are lookup labels and never enter mutation payloads.

Amounts remain decimal strings throughout transport and presentation, including values beyond JavaScript's exact integer range. Expense and payroll thresholds are IDR; leave and overtime selection uses zero. Dates, category codes, stage assignments, reason, observed version, and operation identity are validated before delivery. The backend remains authoritative for eligibility, capacity, access, and policy selection.

Each save owns a detached, immutable submission. Unknown outcomes retain the same payload and operation through explicit retry, MFA renewal, and subsequent conflict replies; verification does not automatically retry a write. A definite initial conflict requires a protected reload. Unsaved or unresolved work participates in the shared navigation guard. Superseded reads and closed account pickers are aborted, and late replies cannot repopulate a disposed or different-company screen. No automatic polling or browser persistence is added.


## Approval delegations

`/approvals/delegations` lists the signed-in account's incoming and outgoing delegations whose end time has not passed, in pages of 20. Exact detail links retain expired or disabled records. This is an account-owned list, including for administrators. Delegators and approval administrators with `approvals.read` may edit; a delegate may only review the record. Administrators can select another delegator when creating a delegation, using the existing bounded approver lookup. The original delegator remains immutable when editing.

The form uses the company's IANA timezone and sends UTC instants. Newly entered ambiguous or nonexistent local times are rejected; unchanged stored instants retain their microsecond precision, including during a repeated local hour. Domain validation bounds the elapsed period to 90 days. The server owns current eligibility and whether an enabled delegation ends in the future. Disabling a delegation retains its original references even if the delegate has lost approval access.

Changes bind the observed version, reason, and one immutable operation to the account/company editor. Unconfirmed outcomes require explicit retries of the same command. A later conflict or MFA challenge cannot discard an earlier unconfirmed outcome. Scope replacement, picker disposal, and route departures abort owned work and reject late results. The API test fixture provisions independent fictional administrator accounts in its disposable database, and signals readiness only after setup and HTTP readiness complete.

## Approval reassignment

`/approvals/:approvalId/reassign` requires `approvals.manage` and reviews a pending or blocked request in the selected company. The operator replaces the current stage with one to twenty-five eligible accounts and records a reason. Other stages remain unchanged. Reassignment does not approve the associated leave, expense, overtime, or payroll resource. The picker excludes the author, beneficiary, and retained makers; the backend rechecks live eligibility and exclusions when committing.

The controller retains the original request version, selected accounts, reason, and operation ID through an unknown outcome, explicit retry, and MFA renewal. It does not reload the approval before retrying, because an advanced or completed request must not hide the original receipt. A definite initial conflict requires an explicit fresh review and clears the old selection. Company replacement and disposal abort owned requests and reject late results. Acknowledgement links back to current request details with the original company and inbox cursor.

The independent approval API scenario exercises templates, delegations, and reassignment on a submitted expense claim. It deliberately loses committed responses and verifies that receipt recovery leaves the claim state unchanged. The cumulative identity/HR scenario has a two-minute execution budget after its final checks exceeded the previous ninety-second limit on GitHub; individual assertion and request bounds remain in force.

## Leave request review

`/leave/requests` provides twenty-item pages with company, employee, status, and cursor scope. Company browsing requires `leave.read`; employee entries are available from employee details under team, self-service, or company read grants. The API checks the current employment relationship. Changing a filter resets pagination, and changing company discards the previous employee, request, and cursor.

`/leave/requests/:requestId` presents the submitted schedule, partial-day charge, frozen policy, approval and cancellation stages, evidence metadata, and twenty-entry history pages. History navigation retains current request metadata while selecting immutable older changes. Readers do not recalculate historical leave from the latest roster or policy. Assigned approvers can open the source request from their approval inbox without broad leave-directory access; the backend resolves assignment and delegation.

Response mapping rejects repeated dates, inconsistent quantities, invalid intervals, duplicate assignments, non-progressing pages, and history newer than the current request. Controllers clear snapshots while reloading, abort obsolete work, and reject late results after disposal or company replacement. Locale/theme changes update presentation without repeating the request. Submitted evidence can be downloaded from details and action reviews.

## Leave request creation

`/leave/requests/new` requires `leave.manage` and opens from the request directory.
Employee discovery supports explicit search and twenty-item pages without general
People access. Effective leave types are selected for the starting date. The form
accepts a bounded date range, full or partial first/last days, a reason, and up to
three published personal documents belonging to the selected employee. Intermediate
dates are requested as full days; the server identifies nonworking dates.

The review shows server-calculated working days, excluded dates and annual balances.
It creates no reservation. Response mapping verifies the employee/type, selected
date partition, schedule intervals, exact half-day quantities and balance totals.
Changing dates, portions, employee or type cancels the previous preview. Submission
retains the reviewed content revision, company, immutable input and operation ID.
A definitive conflict requires another review. An ambiguous result retains the
original command through explicit retries, MFA renewal and subsequent conflicts.

The command bar reports submission separately from approval and links to the saved
request. Scoped drawers restore focus; changing employee clears selected evidence.
Company/account replacement and disposal cancel pending work and suppress late
results. Access denials remove private form details while an uncertain command
remains recoverable by its existing owner. Locale/theme changes retain the form;
the shared departure guard protects drafts and pending or unconfirmed submissions.
This dashboard retention is in memory and does not provide offline draft recovery
after a browser restart.

## Binary downloads

Binary file acquisition uses the authenticated HTTP transport, including current client-admission headers, private cache behavior, cancellation, and explicit deadlines of at most sixty seconds. The transport validates the expected media type, ETag, and exact byte count, retaining at most 100 MiB in 64 KiB blocks. Stream reads, empty responses, and retained block counts are bounded. Native downloads own one short-lived object URL and release their anchor, timer, and abort listener after dispatch or failure. This primitive reports browser handoff, not completion of an operating-system save; feature controllers own the acquisition lifetime.

## Leave request actions

`/leave/requests/:requestId/:intent` provides separate approval, rejection, withdrawal, and cancellation reviews. The detail command bar intersects current grants with the server's available actions; the server owns resource assignment, delegation, and maker exclusions. Each action route acquires the current request independently of the retained history page. It displays the submitted working intervals, half-day charge, current request version, and whether the decision concerns the original leave or its cancellation.

Dedicated domain use cases call the corresponding decision, withdrawal, and cancellation repository methods. Reasons are bounded to 1,000 characters and required for rejection, withdrawal, and cancellation. The immutable command captures the reviewed version and one operation ID. An uncertain response retains that command through MFA renewal and later conflicts; explicit retry does not first read a newer resource that could hide the receipt. A definite first conflict requires protected reloading and a fresh reason. Receipt mapping verifies the resource identity and exact next version.

Confirmation reports that the action was recorded, then links to the current request. It does not infer final approval from an intermediate stage. Cancellation approval refunds leave only after its final stage; rejecting or withdrawing cancellation preserves approved leave. Company replacement/disposal cancels owned work and ignores late results. Access failures remove private review details while an uncertain receipt remains recoverable within the current owner. Forms use the shared dirty/pending/unconfirmed departure protection and preserve drafts across equivalent verification, locale, and theme changes.

## Leave evidence downloads

Details and decision reviews download selected PDF, JPEG, or PNG evidence through the request-scoped API. The use case coordinates the leave and file repository contracts: validate the submitted reference, acquire its exact binary representation, then dispatch the native browser download. The server remains responsible for current request access, MFA, document availability, and storage integrity; a broad personal-document permission is not required. The client checks the submitted revision's ETag, media type, and exact size before file handoff.

One controller owns one pending acquisition. Explicit cancellation, request reload, company replacement, disposal, or decision submission aborts it and ignores late outcomes. A failure from an older request snapshot cannot redact its replacement or overwrite a completed decision. A current access failure clears private review details without discarding an uncertain command receipt. MFA renewal retains the review and reason but never automatically repeats a download.

Binary blocks never enter presentation state, query caches, or browser persistence. The file datasource releases its object URL, listener, timer, and temporary anchor after handoff or cancellation. The UI reports that the download started; cancelling cannot retract a file already handed to the operating system. Uploaded evidence creation remains part of the subsequent document/submission workflows.

## Leave policy review

`/leave/policies` requires `leave.manage` and lists the latest saved policy definitions, including future and disabled policies. Twenty-item pages use the server's type-code cursor. Status filters apply explicitly and reset pagination. Detail links and browser Back/Forward retain company, filter, and directory cursor; changing company discards the old scope.

`/leave/policies/:policyId` shows the latest head separately from immutable revision history. Each twenty-revision history page retains that latest head. Selecting a loaded revision focuses an inline region with its full terms, effective date, actor, recorded time, and reason without another request. The administrative API is distinct from the effective-date `/leave/types` lookup used when preparing a request.

Mapping verifies exact identity, latest/applied revision consistency, calendar dates, bounded decimal entitlements, unique contracts, contiguous history, and cursor progress. Controllers cancel superseded reads and clear selected evidence during refresh, access failure, disposal, or company replacement. Equivalent MFA renewal does not repeat a failed read automatically. Locale/theme updates retain the current read and selection. Balance adjustments have a separate employee/year review.

## Leave policy editing

`/leave/policies/new` and `/leave/policies/:policyId/edit` require `leave.manage`. Editing always acquires the latest saved head, even when entered from an older history page. Codes are normalized when creating and remain immutable afterward. The form supports effective dates, availability, paid/partial-day rules, eligible contract kinds, minimum service, per-request limits, evidence requirements, and optional manual/monthly/annual entitlement with carryover limits and optional 1–12-month validity. A finite validity starts on 1 January of the destination year and requires a positive carryover limit. Clearing it removes the extra deadline from future awards; existing awards retain their original dates. Decimal day strings preserve half days; whole-day policies reject fractional entitlement and carryover. Saving a policy does not post balance movements.

Each submission owns its observed version, reason, detached policy terms, and operation ID. Definite conflicts require a protected current review. Unknown outcomes retain the original command across explicit retries, MFA renewal, and subsequent conflict responses; they do not trigger another read or replace the command. Receipt validation checks the resource identity and exact next version. Creation, future revisions, current/effective selection, immutable history, and deliberately lost committed responses are included in the independent API browser scenario.

Drafts and unresolved saves use the shared departure guard. Company replacement or disposal aborts owned work and suppresses late results without claiming that network cancellation reverses a server commit. Equivalent verification, locale changes, and theme changes retain the current form. Periodic entitlement posting, year closing, and balance administration remain separate operations. Current policy, historical revisions, batch preparation and retained process/schedule snapshots display the applicable validity. Text fields can shrink within a narrow form at enlarged text sizes.

## Leave balance review

`/leave/employees/:employeeId/balances` lists existing accounts for an explicit year, including zero and closed accounts. Entry points are employee details and each charged year on a leave request. Twenty-account pages retain their year and directory cursor through detail and browser navigation. Changing year applies explicitly and resets the cursor; changing company leaves the employee scope. Readers need current company/team/self leave access, independently of policy or people administration.

`/leave/employees/:employeeId/balances/:typeId` displays the current available, reserved, and used days, account version, and closed status. Older movement pages retain that current balance instead of reconstructing totals from a partial ledger. Selecting a loaded movement focuses its original reason, actor, timestamp, and source reference without another read. Request movements link back to their scoped leave request. Current type labels do not replace submitted policy evidence.

Mapping validates employee/type/year identity, half-day decimal bounds, unique accounts/movements, precise timestamp order, and finite cursor progress. Controllers own one read, cancel obsolete work, and clear private values and selection during refresh, access failure, disposal, or company replacement. Verification never silently retries a failed read. Locale/theme updates retain the observation and selection. The directory does not create unused accounts; entitlement posting and year closing remain subsequent dashboard actions.

Tracked carry appears in a separate balance section, including awarded, free,
reserved, used, expired, transferred and deducted days, the original validity date
and award timezone. These figures are part of the main account totals. Mapping
checks conservation and the carried subset, freezes detached values and retains
the original UTC deadline across timezone database changes. Ordinary or older
responses without a breakdown remain readable. Calendar dates do not shift when
the operator uses another timezone. Recorded balances do not guarantee that a
future request can use expired carried credit; request preview applies that policy.

The increment passed 546 Leave unit tests, type checking, architecture checks,
changed-file formatting and 14 balance browser scenarios. English desktop and
Indonesian dark narrow screenshots were inspected. An additional isolated
API/PostgreSQL/worker browser scenario verified matching expired and available
figures after the recovered worker's single debit. No native validation is included.

## Leave balance adjustments

`/leave/employees/:employeeId/balances/adjust` selects a leave type for an explicit year, including types without an existing balance account. Its use case coordinates bounded employee-balance and policy repository reads; the catalog is not an atomic cross-request snapshot. Twenty-type pages retain their cursor and directory context. Opening `/leave/employees/:employeeId/balances/:typeId/adjust` independently reviews the latest balance and server-provided actions. Current management and balance-read scope are required. Missing or unknown action flags never enable adjustment.

The form records an independent adjustment in nonzero half-day increments, up to 366 days in either direction, with a reason. Target identities, year, and observed version come from the review. The command rechecks authorization, independence, applicable records, balance limits, and the open year on the server. A zero account uses version zero; its first movement creates the account. Inactive leave types can still be corrected when the server permits it.

A definite version conflict disables saving until an explicit new review. Re-review preserves the amount/reason and requires another save; it never resubmits automatically. Access or assurance failures hide the private review. One immutable command and operation ID survive an ambiguous outcome, including a later MFA or version rejection. Only its original receipt acknowledges that outcome. The receipt identifies an immutable ledger movement at version zero, not a new balance version; the success link loads the current balance separately.

Drafts, pending writes, and unconfirmed outcomes use the workspace departure guard. Account/company replacement and disposal abort owned work and suppress late results. Equivalent verification, language, and theme changes retain the form without starting another command. Commands remain in memory; browser cancellation does not reverse a server commit or provide durable offline recovery.

## Leave calendar

`/leave/calendar` shows company or current direct-report occupancy by work date.
Company readers can choose either scope; team readers start with their own team.
Approval and self-service grants alone do not expose this screen. The initial
month and Today use the API's company-local date, independently of the browser's
clock. Explicit month navigation supports 1900–2200 and retains date-only values.

The month grid counts employees. Selecting a date shows request counts and a
20-entry page with submitted employee/type labels, portions and status. Two
half-day requests can therefore represent one employee. Overnight portions belong
to the submitted work date. Month and date queries are independent live reads;
Refresh renews both. Date changes and pagination retain the month without polling
or scanning further pages.

Request detail, history and action reviews retain a validated same-company
calendar scope, month, date and page. Returning reacquires current occupancy;
withdrawing a request can remove it. Arbitrary return URLs and incompatible
company/date/cursor context are discarded. Company replacement clears the former
selection and cancels pending reads.

The controller owns one month request and one date request. Late responses cannot
replace a newer selection. A day-read authority failure clears the entire view;
a transient failure retains the authorized month. Hidden workspaces cancel reads,
and successful workspace admission reacquires them after account verification.
Calendar read recovery does not replay commands. Locale/theme changes preserve
the read and filter draft.
The shared month grid supports keyboard activation, current-date/selection labels,
Fluent light/dark tokens and responsive placement above the resource table.

## Leave entitlement processing

`/leave/batches` lists company accrual and year-closing processes in pages of
20. History requires `leave.read`; each operation additionally requires its own
posting or closing permission. Type selection opens a bounded picker on demand
and does not require general policy or People administration.

A new process first reviews the company-local period, effective policy and exact
employee population on the server. The displayed policy can differ from the
current head; submission sends that reviewed head and a detached copy of the
employee IDs. Later hires do not enter an already reviewed command. The interface
shows the population and policy rate separately, since eligibility is decided
per employee. Year closing selects an ended year and explicitly describes carryover.

Detail distinguishes current job status from batch metadata. Employee progress
excludes the final metadata checkpoint; applied, unchanged, skipped and failed
counts remain separate. Each outcome includes minimal employee labels, a
localized failure when applicable and a link to that employee/type/year ledger.
Result pages retain the originating directory cursor. History preserves previous
job attempts and immutable policy references. General job monitoring and
cancellation reuse the existing authorized Jobs page.

Resume requires the current server action and corresponding operation grant. It
reviews the batch version and submits an explicit reason. It does not replay
employees whose outcome is already recorded. Creation and resume retain the
original command and operation after response loss, including through later MFA
or version conflicts. Definite first-attempt conflicts require a new review;
confirmed denial removes private visible data. Draft and uncertain-command
navigation protections remain separate from read acquisition.

Each detail controller owns one request and at most thirty delayed progress
updates. Hidden pages cancel pending reads and timers, terminal jobs and failures
stop observation, and only explicit refresh replenishes the update budget.
Company/page replacement and disposal reject late results. English/Indonesian
views use existing Fluent command bars, resource tables and semantic tokens.

## Company role administration

`/administration/roles` provides company-scoped role directories, current
definitions, revision history and explicit create/edit actions. Access requires
`identity.manage`. Each directory/history page holds at most twenty entries;
company and cursor belong to the route. The editor groups the server permission
catalog by module and requires a name, stable code and reason. Archiving a role
prevents new applications without changing existing membership grants.

Each save pins the reviewed version, detached permission selection and one
operation ID. An uncertain response retains that exact command through MFA
verification and later conflicts; explicit retry retrieves its original receipt.
A definite first conflict requires protected reloading. Navigation protections
cover unsaved and unconfirmed work, while disposal or company replacement aborts
requests and clears the retained form. Refresh/access failure removes private
views. Locale/theme changes preserve the observation and form.

Domain use cases validate scope and bounded input; repositories map DTOs inside
the HTTP failure boundary. Mapping rejects inconsistent current/history values,
duplicate permissions, non-progressing cursors and mismatched receipts. Feature
controllers own requests and command recovery; pages render their state using
existing Fluent command bars, tables and fields.

Validation passed all 3,394 dashboard unit tests, thirteen role/member browser
scenarios and an isolated API/PostgreSQL browser workflow. The latter verified
creation, revision history, lost-response recovery, archival and unchanged access
for a member with an earlier applied role. Desktop and Indonesian dark views at
390 pixels with actual text sizes doubled were inspected. No global account or
membership editing workflow is implied by role administration.

## Company account review

`/administration/members` lists the active company's accounts with separate global
account and company-access status. Each page contains at most 50 accounts; the exact
company and cursor live in the URL. Details read the observed membership version,
effective/direct permissions, and the immutable applied role versions. Changing a
role elsewhere does not rewrite the displayed membership snapshot. The API's
`active` field maps explicitly to domain `membershipActive`.

The feature requires `identity.manage`, uses the feature's datasource/repository/use-case
boundaries, and acquires no employee profiles. Account/company changes and disposal
cancel reads; refresh and failed access remove prior accounts and grants. Repeated
or malformed cursors, duplicate identities, wrong-account details, and oversized
payloads are rejected. Language/theme changes only reformat the current snapshot.
Account creation, company-access editing, invitations, and employee linkage are
separate commands.

Validation includes the dashboard architecture checks, 661 unit tests, production
build, and four browser scenarios for pagination, responsive/localized views, denied
access, refresh failures, and pending reads during a company change. A separate
browser exercised password sign-in, MFA, directory/detail reads, and logout against
the local server using fictional development data. Both membership reads returned
200. No external invitations or messages were sent.

## Company access editing

`/administration/members/{accountId}/edit` reviews one existing member's account
status, company-access version, direct permissions and applied role snapshots.
It requires `identity.manage` and never acquires private employee profiles. The
editor shows the combined permissions before saving and requires a reason.

Previously applied roles remain pinned until explicitly replaced or removed.
The role picker loads one page of at most twenty company definitions on demand;
archived and duplicate selections are unavailable. Up to eight roles may be
selected. Replacing a role reviews its current version. Reactivating company
access requires current active role selections. A globally inactive account may
have its company access disabled, but cannot be reactivated here. The API
independently enforces these rules, sensitive grants and administrator protection.

Saves retain one observed member version, detached payload and operation ID.
Unknown outcomes keep that command through MFA and later conflicts; explicit
retry checks the original receipt. A confirmed membership conflict requires a
protected reload. Read cancellation, company/account replacement and disposal
clear private data; closing the picker cancels its read and removes its cache.
Form departures use the shared unsaved/pending-command protection.

Validation passed all 3,438 dashboard unit tests, 23 role/member browser scenarios
and an isolated API/PostgreSQL workflow. The latter retained an archived applied
role, recovered a deliberately lost committed response and required explicit
current-role selection before reactivation. Desktop and Indonesian dark views at
390 pixels with doubled text were inspected; long account text wraps within the
available width. Global account provisioning remains a separate workflow.

## Platform account administration

`/platform/accounts` is a separate global directory requiring platform
`identity.manage`. It remains available without a selected company. Explicit
name/email search and fifty-row cursor pages acquire only administrative account
fields. Details show activation, pending invitation, MFA configuration status,
platform permissions and the observed account version; no authenticator values
or private employee profiles are acquired.

The access editor uses the server's assignable permission catalog and requires
a reason. It explains that saving invalidates the account's existing sessions.
Administrators cannot deactivate themselves or remove their own administration.
Pending invitations cannot be activated here; explicitly saving one as inactive
cancels it. Company membership and HR permission changes remain separate.

One observed version and detached command survive an unconfirmed response.
Explicit retry checks the original receipt, including after MFA renewal or a
later conflict. A definite version conflict requires protected reloading. Revoked
authority clears private data and revalidates the application session; permitted
self-edits return to sign-in when that session is revoked. Reads, saves and form
state belong to the account editor and stop on disposal. Locale/theme changes
retain the form without another acquisition.

All 3,487 dashboard unit tests and eleven account browser scenarios passed.
An isolated API/PostgreSQL scenario verified administration without company
access, lost-response recovery, reactivation and self-edit session revocation.
Desktop and Indonesian dark views at 390 pixels with doubled text were inspected.
Invitations, new company assignments, provider bindings and session administration
remain subsequent workflows.

## Employee account binding

An unbound employee profile offers **Link account** to an independent operator with
profile-management, identity-management, and account-linking permissions in the
profile's owning company. The review selects an active account with active company
membership from bounded pages. The operator's account and inactive accounts are
excluded; an empty filtered page still permits moving to the next source page.

The command pins the selected account, observed profile version, reason, and
operation ID. Duplicate submission is disabled. A lost response keeps that exact
submission read-only for explicit receipt recovery, including after a later
conflict. Definite conflicts require a fresh review; an existing binding cannot be
replaced through this form. Dirty/pending departure protection and workspace
cancellation follow the existing feature boundaries.

The real API browser scenario uses an independent native employee session. Before
linking, its own employment directory is empty and profile access is denied. After
one committed binding and a deliberately lost response, replay preserves one new
history revision, the native account can read its profile, and a colleague's
profile remains inaccessible. Company directory reads remain self-scoped for the
employee, while the administrator's self directory stays empty.

Navigation entries are owned by their features. The portal composes accessible
sections for people, time and attendance, requests, communications and administration.
Local menu search filters only authorized entries. A detail route selects its most
specific visible ancestor; the existing departure guard still owns navigation.
The sidebar scrolls independently and retains an icon rail on narrow viewports.

The compact rail ignores the desktop search while its field is hidden. Returning
to the wider sidebar restores the query. The media-query subscription belongs to
the workspace hook and is released with it. Both resize directions have a browser
regression test; architecture, type checks and 844 unit tests passed.

## Documents

`/documents` provides bounded employee search under both `documents.read` and
`people.profile.read`. Employee folders, document history and revision details have
their own routes. They use the Documents discovery API without depending on the
general People directory or its availability. A pinned route from another company
returns to the current directory before acquiring any document.

The published revision remains distinct from a replacement still uploading or
being validated. Details show file metadata and bounded validation history;
timestamps use the company timezone. English and Indonesian copy, Fluent command
bars and resource tables support the existing themes and narrow navigation rail.

Refresh hides prior data until the new read succeeds. Account, company, grant and
route changes cancel obsolete reads. Download reauthorizes the revision and
content; one controller owns cancellation without storing bytes in UI state.
Hiding the authorized workspace also cancels its pending download. A known access
or availability rejection hides the detail until a successful metadata refresh;
MFA verification never repeats the download automatically. Browser handoff reports
that a download started, not that the user saved it.

Revision details expose validation, retry and upload cancellation only when the
current API capability and management scope permit them. Each confirmation route
acquires a fresh review. Cancellation remains available for an expired upload that
has not yet been closed; completed content cannot be revalidated arbitrarily.

The controller retains one immutable operation, observed version and reason until
the outcome is acknowledged. An uncertain response permits only an explicit replay
of that command, including after MFA recovery or a later rejection. Definite version
conflicts require a new review. Departure protection covers edits and pending or
unconfirmed commands; replacing the workspace cancels obsolete work. Known access
loss hides metadata until a successful authorized review.

Employee folders provide **Upload document**; document details provide **Upload
revision**. These administrative actions require both management grants as well
as the existing read scope. Replacement uploads preserve the document's employee,
title, category and observed version. **Resume upload** is available only from a
current revision capability.

The form accepts PDF, JPG and PNG files up to 100 MiB. It verifies the selected
file, sends bounded parts and shows committed progress. Pause stops local work;
explicit retry continues the same immutable command from current server progress.
A lost response stays pinned through MFA recovery and later rejection. Upload
completion links to validation without claiming the file is already published.

The page owns its selected file until completion or departure. Closing it releases
that local reference; an unfinished server revision remains in history. Resume
requires selecting a file with identical content. Browser file references and
commands are not persisted as an offline draft. Company replacement cancels the
old owner; equivalent workspace suspension pauses delivery without replaying it.

### Retention policies

**Administration → Document retention** provides the three classifications,
current definitions, and ascending revision history in pages of twenty.
The route is `/documents/retention/policies`; a company change drops the previous
classification and returns to that company's catalog. Access requires the separate
`documents.retention` grant. It does not acquire employee folders or file content.

An unconfigured policy is distinct from an explicitly indefinite policy. Editors
accept a reason and either an elapsed-day duration or a blank duration for
indefinite retention. Existing archives keep their recorded policy. No statutory
duration is preselected.

Saves retain one immutable operation and reviewed version until acknowledged.
Response loss stays recoverable through MFA or a later rejection; definite
conflicts require a fresh review. Leaving a pending form uses workspace departure
protection. Scope replacement cancels its request and rejects late results.

### Document archive and legal hold

The retention catalog also accepts a document reference. Document details expose
that reference and a retention link for authorized operators. The lifecycle routes
stay under `/documents/retention/documents/{id}`; old policy bookmarks redirect
to the current administration routes without losing company scope.

Retention status and bounded history do not acquire document contents or employee
profiles. A selected change opens its immutable resulting state in a detail panel.
The separate retention version protects archive, restore, hold and release
commands. Each action acquires a fresh review and requires a reason.

Archiving records the current policy and requires unfinished uploads to be
cancelled separately. Policy changes preserve existing archive snapshots. Restore
allows new uploads while retaining a legal hold; explicitly archiving again adopts
the current policy. Hold/release keeps archive metadata intact. These operations
do not retire accepted content. Lost responses, MFA recovery, departure protection
and current-scope checks follow the same command ownership as policy editing.

### Content retirement

Retention details accept a revision reference, and ordinary revision details offer
the review link only with the independent retention grant. The route stays under
`/documents/retention/documents/{documentId}/revisions/{revisionId}`. It reads
revision identity/status, publication and retention metadata without acquiring a
file or employee profile. The server supplies the review time and current blocker.

Eligible revisions require a reason and explicit confirmation. The page states
that access is removed permanently and file deletion is scheduled, while metadata
and history remain. A blocked review has no confirmation control. A new hold or
version change requires another review; a previously uncertain command keeps its
original operation, two versions and reason through retry and MFA recovery.
Scope replacement aborts its owner and cannot publish late results into the next
company. Retirement history opens the original revision reference and archive
snapshot in the existing detail panel.

## Overtime review

The overtime directory defaults to the current company month and supports bounded
date/status filters and explicit paging. Request details keep requested, actual
and approved time distinct, show the original schedule, and open immutable change
details without repeating the request. Displayed times use the request's timezone.
Returning preserves directory filters and the page cursor.

Account/company/grant changes replace query ownership. Refresh and failed reads
remove the previous private result; MFA verification alone cannot restore it.
Assigned reviewers reach their source request through Approvals and return there
without a company directory grant. All reads still authorize independently on the
server.

Request actions open separate current reviews for actual-time submission,
approval, rejection and withdrawal. Time fields use the request's zone, preserve
unchanged stored instants and reject newly entered ambiguous clock changes.
Approval acknowledges the current stage without claiming final approval.

Each action captures the observed version, immutable input and one operation ID.
Unconfirmed delivery retains that command through explicit retries, renewed MFA
and later conflicts; retry never acquires a newer request first. A definite first
conflict requires protected re-review. Denied reads clear private details and stay
hidden through pending or failed recovery. Company replacement and disposal abort
owned work and ignore late results. Unsaved and unresolved actions participate in
the workspace navigation guard. These commands remain in browser memory.

## Overtime planning

`/workforce/overtime/new` requires `overtime.manage` and preserves the directory's
company and filters. A bounded employee panel uses the selected work date and
minimal name/number references, without acquiring personal profiles. Selecting an
employee acquires their current calendar review. Changing date clears the employee
and cancels any older review; changing company replaces the entire form owner.

The page separates the work schedule from requested start/end time, breaks and
reason. Times use the reviewed company timezone. Confirmation pins employee and
employment version, calendar revision, new request ID, operation and entered time.
An unknown result retains that exact command through MFA and later conflicts.
A definite changed-calendar rejection requires explicit review before another
confirmation. Denied data stays hidden through pending or failed recovery.

Selection and time controls are read-only during submission or receipt recovery.
Drafts participate in the shared navigation guard, and successful creation links
to the resulting request. Browser commands remain in memory. The employee panel
uses compact reference columns within the shared resource table; larger directory
tables retain their default minimum width.

## Monthly attendance closing

`/workforce/periods/:month` shows the month status, closing timezone and minimal
job progress. The directory defaults to the preceding company-local month;
eligibility comes from the server's current date and available actions. The page
accepts `workforce.read` or `workforce.close`. Only an authorized published result
opens employee discovery, with explicit name/number search and pages of 50.
Job inspection links use the independently authorized Jobs screen.

One visible page owns one overview request and at most 30 delayed progress reads,
four seconds apart after completion. Reads do not overlap. Observation stops on
terminal job state, error, disposal or a hidden document; exhaustion shows an
explicit Refresh prompt. Returning to a visible document resumes only eligible
progress. It never retries a denied read automatically. Failed reads clear the
old private view, including throughout pending or failed manual recovery.

Closing and stopped-attempt recovery have separate review routes and require a
reason. Confirmation retains the observed version, month, original reviewed date,
reason and one operation ID. An uncertain command remains fixed through MFA and
later conflicts; explicit retry does not load a newer review first. A definite
initial conflict requires a protected fresh review. Drafts and unconfirmed
commands use the workspace departure guard and remain in browser memory.
Recovery releases a stopped attempt for review; it does not start another job.

`/workforce/periods/:month/employees/:employeeId` shows the published daily facts,
historical timezone, accepted attendance and approved overtime minutes. Recorded
absence and unrecorded scheduled days remain separate totals. Names and numbers
come from authorized display references; no personal profile is acquired. Query
ownership includes account, company, grants and the selected period/employee.
Company replacement clears selections and aborts pending work. Locale and theme
changes reformat the current observation without acquiring it again.


## Leave accrual schedules

`/leave/accrual-schedules` provides company-owned history in pages of 20.
An authorized operator selects a leave type, first month and 1–12 periods, then
reviews each effective policy, due time, credit, minimum service and eligible
contracts. Frequency follows policy. Admission retains the reviewed employee
selection, policy head, reason and one immutable operation; overdue periods may
start immediately. See the [schedule contract](leave-accrual-schedules.md).

A definite policy conflict requires another review. An uncertain response keeps
the original command through MFA and later conflicts; recovery requires explicit
retry. Verification reacquires a denied review but never submits a command.
Workspace replacement aborts obsolete acquisition and clears private data. Drafts
and uncertain commands participate in the existing navigation guard and remain
in browser memory.

Detail displays the immutable authorization and current progress for at most 12
periods. Opening a period retains the schedule and its history cursor through
outcome paging and recovery. Cancellation and resume use the existing job and
batch workflows; future recovery retains the original due time. Schedule readers
perform explicit bounded reads, without browser timers for future periods.

## Leave carry expiry

Leave processing opens company carry-expiry history, attempt details and employee
results. Results link to the matching balance year; original labels and deadlines
remain retained. Terminal recovery uses current HR scope, the observed version,
a reason and the original operation ID after response loss. Hidden reads stop;
active details use bounded polling, while distant scheduled work is not polled.
Company changes clear the previous selection and cursors. English/Indonesian
copy and responsive Fluent components reuse the existing portal shell.

Unit and browser tests cover disposal, finite polling, original-command recovery,
MFA, company replacement and narrow dark-mode review. A real API/PostgreSQL/worker
scenario verified recovery and one ledger debit after a lost successful response.
Optional validity configuration and carried-balance presentation are covered by
[dashboard and native acceptance](leave-carryover.md#dashboard-and-native-acceptance).
