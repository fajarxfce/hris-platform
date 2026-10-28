# Expenses

Own expense categories, policies, claim lines/receipts, review, payment batches, and reconciliation.

Claims store IDR amount, transaction date, cost center, and evidence. Snapshot limits on submit. Suspected duplicate receipts are review signals. Support return-for-correction/rejection with reasons and approval before payment.

Payments have stable item references and per-item success/failure status. Paid claims cannot enter another batch. Reimbursements are independently paid in the first version. External bank submission remains manual.

Screens: claims, review, policy setup, payment batches, reconciliation.

The [claim dashboard](../dashboard.md#expense-claims) implements bounded directories,
coherent current claim/history review, immutable submission evidence, payment
progress and scoped receipt downloads. Independent reviewers can approve, reject
or return submitted evidence with both observed versions and operation recovery.
Approval source links use submission IDs. Finance preparation, release,
cancellation/replacement and reconciliation are implemented separately. Dashboard
draft creation/editing, submitted actions and returned-claim correction are also
implemented and exercised in `apps/dashboard/integration/expense-claims.spec.ts`.
See [draft authoring](../dashboard.md#expense-draft-authoring) for the current contract.

Acceptance: category limits, company attachment access, concurrent submission, payment replay, failed/partial batch reconciliation, and immutable paid records.

## Operational expense report

The [reporting API](../reporting.md#operational-expenses) now aggregates exact IDR
amounts from each claim's latest submitted lines, filtered by transaction dates.
The report and paginated claim drill-down require company-wide expense and report
authority. Unsubmitted drafts are excluded; returned/withdrawn submissions retain
their original facts through later draft edits until resubmission replaces them.
Current status is separate from immutable submitted content and bank settlement.
Backend acceptance passed through staged approval, resubmission, scoped continuation,
revocation, cancellation and real partial-payment/retry workflows. The dashboard
now displays matching totals/claim pages and opens each exact submission with a
validated report return link. Browser/API acceptance verifies correction and
resubmission without replacing frozen evidence or losing the report selection.

## Category policies

`PUT /api/v1/companies/{companyId}/expenses/categories/{id}` creates or revises a category with an idempotency key, expected version (null on create), effective date, and reason. `expenses.policy.manage` is a distinct grant included in the company-administrator and finance permission catalogs; existing memberships/role snapshots require an explicit update to receive it.

Policies define a display name, maximum amount per line and per claim for that category, receipt/cost-center requirements, maximum transaction age (1–366 days), eligible permanent/fixed-term contracts, and active status. Amounts use decimal strings in IDR, with at most two decimal places and a maximum of 999,999,999,999.99. The category code is immutable; archive by creating an inactive policy revision.

`GET /expenses/categories?asOf=YYYY-MM-DD` selects the latest effective revision for each category. Responses distinguish the current optimistic `version` from the selected `appliedRevision`. Backdated corrections do not supersede a later effective revision. Authorized expense users can read applicable policy; `GET /expenses/categories/{id}/history` requires policy administration and returns immutable revisions, actors, reasons, and timestamps. Both lists use bounded cursor pagination (default 50, maximum 200).

Administrative `GET /expenses/policies` returns each latest saved definition,
including future and inactive heads, ordered by immutable code. It supports an
optional active filter and code cursor. `GET /expenses/policies/{id}` returns the
current saved head with a bounded history page (`historyAfter`, `historyLimit`).
History remains ordered by ascending revision; its cursor does not select a
different current head. Both endpoints require `expenses.policy.manage` and
hold shared policy/access guards while acquiring the response. They revalidate
credentials, current grants and session assurance after waiting and before
delivery. A review cannot combine a pre-save head with post-save history.

Each company allows 500 categories and each category 1,000 revisions. Competing definitions/updates use company serialization and optimistic versions. Current account, company, membership, and permission are checked before a mutation or receipt replay. Policy history, version, audit, and operation receipt commit together; no external I/O runs in that transaction. Submission uses these effective policies; payment processing uses the approved immutable evidence.

Policy saves also revalidate current session assurance after waiting for their
resource/access guards, before returning an original receipt and before transaction
completion. Expired MFA rolls back the category revision, audit, outbox and receipt
together. An explicit retry retains its original operation ID and body.

The [administrative dashboard](../dashboard.md#expense-policies) provides category
creation, versioned editing, status filters and revision review. Amount entry and
display preserve decimal IDR precision. An uncertain browser save retains its
original payload for explicit retry; changing company cancels its request owner.

## Employee reads

`GET /expenses/cost-centers` provides active cost-center references for claim editing,
with only ID, code, name and observed version. It requires `expenses.self.manage`
or `expenses.manage`, independently of general organization browsing. Search is
bounded to 120 characters; cursor pages default to twenty and allow at most fifty.
The endpoint applies expense module/native-build admission and rechecks session
assurance after shared structure/access guards and after acquisition. Selecting a
reference reserves nothing; submission independently validates and freezes it.

`GET /expenses/me/employments` returns only the signed-in account's employment references, the company date/timezone, and a bounded cursor page (default twenty, maximum fifty). Each reference contains its ID, employee number, status, and observed version. It requires `expenses.self.manage`, `expenses.read`, or `expenses.manage`; People access and the People module are not required. Company module admission and native minimum-build requirements still apply.

Claim lists, details, drafts, history, submission summaries, payment progress, submission details, and receipt downloads accept `scope=SELF`. This always restricts the response to the account's own employment, including when that account also has company-wide or reviewer grants. Self-scoped claim lists require an owned `employmentId`. The default `AUTHORIZED` preserves company, team, and assigned-reviewer access according to each endpoint's existing policy; it does not expose unsubmitted drafts to submission reviewers.

The canonical `GET /expenses/claims/{id}/record` instead defaults to `SELF`.
It acquires the current claim/draft, latest immutable submission with current
approval and reviews, and one descending history page in a single guarded
transaction. `historyLimit` defaults to twenty and accepts 1–20; `historyAfter`
selects actions below that exclusive version without changing the current head.
The response omits live action authority and duplicate-receipt review hints.
It accepts explicit `AUTHORIZED` for ordinary claim readers, but an assignment
alone cannot reveal the current draft/history. Missing and inaccessible claims
both return `expense_claim_not_found`, before resource-relative cursor validation.

Approval reassignment captures an owned `EXPENSE_CLAIMS` invalidation in its
transaction without changing the claim command version. Consumers must hydrate
that invalidation even at an equal claim version and use the approval's embedded
version when displaying current assignment. [Synchronization](../synchronization.md)
defines cursor publication and client upgrade behavior. Payment progress stays
in its separate versioned collection and never refreshes claim review authority.

Interactive claim and category reads acquire shared resource/access guards in the same order as their writers. They revalidate current company membership, credentials, the original permission intersection, and applicable MFA assurance after waiting and before returning acquired data. Receipt content uses short guarded database transactions before and after object-storage I/O. It verifies the immutable chunk, rechecks access before delivery, and clears the acquired byte buffer on both successful and failed delivery. External storage calls hold no database guards.

## Claim drafts

Employee draft saves, cancellation, submission and withdrawal revalidate current session assurance after their guards, before returning an existing operation receipt, and before transaction completion. Late expiry rolls back the claim, immutable evidence, approval changes, journal and receipt together. An explicit retry retains its original input and observed version.

`PUT /expenses/claims/{id}/draft` saves a draft with an employment ID, title, description, lines, reason, and expected version (null on create). Every line has a stable UUID, category, transaction date, decimal IDR amount, description, optional cost center, and receipt revision IDs. Drafts allow up to twenty lines and three receipts per line. An empty draft is allowed; policy eligibility, mandatory evidence, and approval routing are checked when the claim is submitted.

References must belong to the same company. Cost centers must have the correct organization-unit kind. Receipt documents must use the `RECEIPT` classification and belong to the claim's employment. A draft can reference an unfinished upload or an archived category while it is being prepared; saving does not make its evidence ready or authorize reimbursement. Claim employment and original author cannot be changed.

`expenses.self.manage` allows the linked employee to save/cancel their claim. `expenses.manage` allows authorized administration on behalf of an employee and is included in the company-administrator and finance catalogs for new grants. Existing membership/role snapshots require an explicit grant update. Reads also support `expenses.read` and the current manager's `expenses.team.read`. A former manager loses access when the employment's reporting relationship changes.

`GET /expenses/claims/{id}` returns the current draft. `/drafts` returns immutable draft revisions (maximum twenty per page); `/history` returns versioned actions (maximum 200 per page). `GET /expenses/claims` requires `from` and `until` dates spanning at most 366 days in the company timezone and supports status, employment, and cursor filters. Users without company-wide expense read/manage permission must provide an authorized employment scope. Summary pages default to fifty and allow at most 200 rows.

`GET /expenses/claims/{id}/review` returns the current claim/draft, a descending
history page, a descending submission-summary page, and bounded payment progress
in one guarded transaction. `historyAfter` is an exclusive version cursor;
`historyLimit` defaults to fifty and allows at most 200 records.
`submissionsAfter` is an exclusive submission-number cursor; `submissionsLimit`
defaults to twenty and allows at most twenty records. Each page request reads a
coherent current aggregate; it does not pin an earlier response. Payment progress
contains status and timestamps, never bank destinations or bank references.

The review uses ordinary current self/team/company claim authority and honors
explicit `scope=SELF`. Assignment-only reviewers use the immutable submission
endpoint instead; assignment does not expose the current draft or its history.
Resource-dependent cursor validation follows authorization. Shared expense and
access guards keep the draft, actions, submissions and payment evidence consistent
while allowing independent readers. Current session assurance is checked after
guard acquisition and again before releasing the aggregate. Receipt bytes are
not acquired by this transaction.

Every edit appends a complete revision; previous lines, receipts, totals, actors, and reasons remain unchanged. Database constraints prevent appending contents to an already committed draft and verify line counts/totals before commit. Header, revision, evidence references, action history, audit, and idempotency receipt commit together. A lost response can be replayed after fresh authorization without changing the current revision.

Limits are 1,000 open claims (`DRAFT`, `PENDING`, or `RETURNED`) per company, fifty per creating account, and 100 draft revisions per claim. Submission keeps its capacity reservation until a terminal outcome. `POST /expenses/claims/{id}/cancel` requires the expected version and a reason, releases open-claim capacity, and preserves its evidence/history. Cancellation remains available after the revision limit. Competing edits/cancellation cannot both commit the same version. Payment progress remains separate from the approved claim status.

Commands recheck live account, membership, and credential state before replay. Their permissions are limited to the intersection with the original request, so a grant made while a command waits cannot expand its authority.

## Draft reference lookup

Administrative drafting uses `/expenses/drafting/employees`, requiring
`expenses.manage` independently of People browsing. Search is bounded to 120
characters and pages to fifty entries (twenty by default). The response includes
the company date/timezone and employee ID, number, name, status and observed
version. Selection reserves neither employee eligibility nor submission policy.

`/{employmentId}/receipts` lists only published, accepted `RECEIPT` revisions for
that company employment. Its bounded metadata page excludes digests, storage keys,
inspection details and file bytes. `/{employmentId}/references` resolves at most
20 category, 20 cost-center and 60 receipt identifiers using the same minimal
reference projection as the employee editor. Missing or inaccessible references
retain their requested ID with null metadata. Every administrative route requires
the original and current `expenses.manage` grant; it grants no document-content
or personal-profile endpoint access.

These reads keep ordered resource/access guards and check session assurance
after waiting and after acquisition. Employee discovery uses the current company
date, and receipt discovery holds the document guard against concurrent retirement.

`GET /expenses/me/employments/{employmentId}/draft-references` resolves the names
used by the employee editor. Optional UUID sets accept at most 20 `categoryIds`,
20 `costCenterIds` and 60 `receiptRevisionIds`. Only an account-owned employment
with expense editing permission is admitted, including for finance administrators.
This endpoint is independent of general organization/document browsing.

The response contains category/cost-center names and active flags, plus receipt
filename/type/size/upload status. It never reads file contents or returns storage
keys, digests, inspection details or another employee's metadata. Missing,
foreign-company, wrong-kind and inaccessible receipt references contain only the
requested identifier with null metadata. Archived names remain available so the
editor can replace them; they do not reserve submission eligibility.

Resource/access guards retain scope through bounded lookup. Current grants,
credentials and MFA are checked after waiting and again before publication.
Submission still independently validates active categories/cost centers and ready
receipts against the transaction date.

## Submission and withdrawal

`POST /expenses/claims/{id}/submit` requires a client-generated `submissionId`, expected claim version, reason, and idempotency key. It freezes an immutable submission and creates an `EXPENSE` approval whose resource ID is that submission ID. The mutation receipt returns the claim ID/version; the caller's submission ID remains usable after a lost response or a later correction.

Submission requires at least one line. Transaction dates cannot be in the future or more than 366 days old; the selected category can impose a shorter age limit. Eligibility and category policy use the transaction date. Every referenced cost center must still be active, and every receipt must be a `READY` revision belonging to the same company's employment with the `RECEIPT` classification. Submission reads immutable validated metadata inside its transaction; it does not access object storage or run another scan.

The snapshot retains the exact category revision, observed policy version, cost-center name/code/version, employee identity, and receipt name/type/size/SHA-256. Later policy edits, archival, and label changes cannot rewrite it. Category totals include all lines in that category, including lines that selected different effective revisions; every applicable per-claim cap must be satisfied. Each category must select the same approval template using the full claim amount. A mixed claim that would use different workflows must be split.

All historical draft authors, the original creator, and the submitter are retained as makers and excluded from the initial approval candidates. The beneficiary is also excluded. A stage without an eligible reviewer is explicitly `BLOCKED`. Candidate overflow fails before maker filtering; it cannot silently truncate an approval group.

Repeated receipt digests within the claim or in another current pending or approved claim are saved as `possibleDuplicate` review signals. They do not automatically reject submission or expose another claimant's identity. The signal on an existing submission remains historical; review processing re-evaluates duplication before accepting a decision.

`GET /expenses/submissions/{submissionId}` returns only that submitted evidence and its approval/current claim status. Current self/team/company scope applies, and an assigned reviewer with live permissions or a valid delegation can read this exact submission. A historical reviewer does not gain access to the current unsubmitted draft or its history. `GET /expenses/claims/{id}/submissions` uses ordinary claim scope and returns summaries with a number cursor, at most twenty per page.

Submission details expose `canReadClaim` separately from `availableActions`.
`DECIDE` is available only to the current independent approval stage of a pending
latest submission. Makers, original/current beneficiaries and their delegates are
excluded. Earlier and later stage assignments may still authorize evidence reads
without authorizing a decision. Explicit `SELF` reads expose no reviewer action.
Duplicate signals require review input; they do not hide the decision workflow.

Reads acquire shared actor/delegator account guards in identifier order before
resolving current grants. A delegation that expires during acquisition cannot
release evidence unless another ordinary claim scope still permits it. Action
availability is derived again at result delivery. These observations do not
reserve a decision: commands independently validate current scope and both
observed claim/approval versions.

Pending claims cannot be edited or cancelled directly. `POST /expenses/submissions/{id}/withdraw` cancels the current pending/blocked approval and returns the claim to `DRAFT`, preserving all submitted evidence. It requires management scope, an expected claim version, and a reason. A later edit creates a new draft revision; resubmission creates a new submission/approval. There are at most twenty submissions per claim. Withdrawal and draft cancellation remain possible at that limit.

Claim transitions, submission evidence, approval state, action history, audit, and operation receipts commit together. Database insertion fences prohibit adding evidence to a committed submission, and deferred constraints require complete line/receipt snapshots. Competing saves/submissions/withdrawals use the same optimistic claim version and serialization guards. Replays recheck current account, company, membership, credentials, and resource access.

## Review and correction

`POST /expenses/submissions/{id}/decisions` accepts `APPROVE`, `REJECT`, or `RETURN`, an expected claim version, an expected approval version, a reason, and optional `acknowledgeDuplicates`. Both versions matter: reassignment invalidates a decision from an older screen even when the same reviewer remains assigned. There is no generic approval endpoint that can skip the expense business transition.

Only an assigned reviewer with a live expense-approval permission, or their currently authorized delegate, can decide. The original beneficiary, current beneficiary account, and every frozen maker are excluded, including when they are the source of a delegation. An account linked to the employee after submission is still the beneficiary. Generic administrative reassignment cannot bypass these expense checks; an ineligible assignment must be corrected.

Each stage stores an immutable review with the actor, delegator, approval/claim versions, decision, resulting status, current duplicate digests, acknowledgement, reason, and time. Approval advances one stage or produces `APPROVED`; rejection produces terminal `REJECTED`. Return records a rejected approval attempt with the distinct expense outcome `RETURNED`. Rejection and return require a reason. A returned claim must receive a new draft revision before resubmission, or it can be cancelled. Earlier evidence and reviews remain readable through their original submission ID.

An approval with currently duplicated receipt bytes requires explicit acknowledgement and a nonblank explanation. The current signal includes other pending/approved claims and repeated bytes within this submission. A cancelled/withdrawn/rejected other claim no longer causes a current signal, while submitted/reviewed historical signals remain unchanged. The submission detail exposes current digests separately from the frozen receipt flags and includes at most eight stage reviews.

Review uses the same company expense/approval guards as submission and withdrawal. It rechecks actor/delegator access, expiry, current beneficiary, and resource scope before a decision or receipt replay. Competing reviewers or withdrawal cannot commit the same claim version. The shared approval decision, expense status, review evidence, history, audit, and idempotency receipt form one transaction. Database constraints require matching review evidence for each review transition and prohibit changing terminal approved/rejected claims. Payment progress is tracked separately from this approved evidence.

Current MFA assurance is checked after the ordered account guards, before returning
a receipt and after journaling. An expired proof cannot authorize a decision or
replay. Renewing assurance allows explicit recovery with the original operation
and observed claim/approval versions.

## Receipt content

`GET/HEAD /expenses/submissions/{submissionId}/receipts/{revisionId}/content` serves a receipt attached to that exact immutable submission. Current claim owners, current managers with team read permission, company expense readers, and assigned reviewers with current approval access or valid delegation can use it. It does not grant general document/profile access, and an old submission cannot authorize a receipt added to a later draft.

Metadata and each content read revalidate company/account/credential access and current resource scope. The referenced revision must remain ready with the submitted size and digest. The storage read occurs outside the database transaction; its bytes are checked against the manifest, and access is checked again before delivery. Revocation during a pending read prevents those bytes from being sent. Range, ETag, conditional GET, and HEAD use the shared bounded download transport; HEAD/unchanged responses still authorize the caller and do not read storage. Responses use private no-store caching, attachment disposition, and nosniff.

## Payment batches

`expenses.pay` is a protected grant, separate from expense read/manage/approval. Company administrators do not receive it automatically. Payment queries, bank details, exports, and commands require current company/account/membership access and recent authentication (recent MFA when enforced). Commands recheck the original request's permission intersection after acquiring their resource/account guards and before receipt replay.

Reads recheck recent assurance after acquiring the final result. Commands recheck
before returning an original receipt and after journaling; expiry rolls back the
batch/item transition, bank-reference reservation, history, audit/outbox and operation
receipt together. An acquisition failure retains its original classification.
Renewing authentication permits explicit retry with the original command identity.

`GET /expenses/payments/{id}/review` returns the batch and its immutable instructions,
an ascending history page, reconciliation records and current `availableActions`
under one shared aggregate/access guard. History uses `historyAfter` and
`historyLimit` (default 50, maximum 200); a cursor beyond the current batch version
is rejected after resource authorization. Reconciliation evidence is bounded by
the batch's one hundred instructions. Paging rereads the current coherent batch;
it does not retain a historical transaction snapshot across requests.

Action availability distinguishes preparation cancellation, independent release,
independent reconciliation, and export before the first reconciled item. Original
makers and both beneficiary identities cannot release or reconcile their own
claims; a batch preparer cannot release the batch. A partial reconciliation removes
export availability, and closed/cancelled batches expose no actions. These display
hints do not replace authorization, version or evidence checks on the commands.

The dashboard exposes finance-only batch directories with company-date/status
filters and twenty-row pages. Detail tabs reuse a single review for the current
batch, bank instructions, reconciliation outcomes and activity. Opening an item
reveals its complete account and immutable payment reference; tables show only
the account's final four digits. Activity has a separate ascending cursor.

CSV acquisition is bounded to 256 KiB and uses the existing browser file port.
The server's export capability gates the action, and the export endpoint checks
current finance authority and batch eligibility independently. Cancellation,
departure and company replacement abort owned work; a late result cannot start a
download. Authorization or eligibility loss removes the observed review until an
explicit successful refresh. Changing tabs, language or theme does not refetch
the batch. Queries are scoped by account/company with no inactive cache retention.

The client validates item counts, exact decimal totals, immutable outcome
references, history continuity and action/status coherence before publication.
Batch totals permit fourteen integral digits while individual claims retain their
twelve-digit limit. This read flow does not grant access to the source claim and
does not issue bank transfers. Commands independently authorize their current
resource and observed version.

Prepared batch details expose release and cancellation only from the current
server action projection. Each action opens a fresh review with complete bank
destinations, amounts and a required reason. The confirmation captures the
reviewed batch version and one operation ID. Unknown outcomes retain the original
input and operation for explicit retry, including across account verification or
a later rejected retry. A definite conflict requires an explicit fresh review.
Inspection stays local to the form; departure and reload protect unsaved input
and unconfirmed commands. Disposal aborts owned work and ignores late results.
These browser commands are retained during the editor lifecycle, not persisted.

Reconciliation opens its own current review. Finance enters confirmed results in
an item panel, then submits one to 100 outcomes together with a reconciliation
note. Unentered payments remain pending. Success requires a bank reference;
failure requires explicit confirmation that no funds were transferred. Entry
uses the company timezone, rejects invalid or ambiguous local times, and preserves
an unchanged result's timestamp precision. The API enforces its own upper time
bound at commit.

Results can be edited or removed locally before submission. The captured command
freezes its outcomes, batch version, reason and operation ID. Response loss
retains that original command across verification and later rejected retries.
A definite initial conflict requires a fresh review and explicit result re-entry;
input rejection permits correction. Access loss hides bank data, and disposal
aborts acquisition/delivery and ignores late results. Theme, language and local
item inspection do not refetch the review. The page owns departure protection for
its form and item panel. Staged results and retry input remain in memory only for
the editor lifecycle.

`GET /expenses/payments/payables?from=YYYY-MM-DD&until=YYYY-MM-DD` lists approved claims without an unresolved or successful payment reservation. `PUT /expenses/payments/{id}` prepares an immutable batch of one to 100 instructions with stable item IDs, submission IDs, bank codes, account numbers/names, title, reason, and idempotency key. Finance supplies independently verified destinations for this batch; it does not change a person's profile or define a reusable bank-account master. Claim amounts and employee labels come from the approved submission, not the request. All amounts are IDR decimal strings.

Payable rows include `availableActions`: `PREPARE` for an independent operator,
otherwise an empty set. This guarded read projection covers every historical
maker and both the submitted and current beneficiary identities. It preserves
the date/cursor page and does not disclose those internal actor IDs. It is a
display hint; preparation still rechecks current independence, reservations and
capacity when the command executes.

The dashboard preparation page retains selected claims across twenty-row payable
pages and limits each batch to 100. A bank-details panel edits only its selected
payment; a separate review displays every complete destination and amount before
confirmation. Title, note, submission references and all generated identifiers
are frozen together for explicit retry after an unknown result. MFA renewal or a
later rejection cannot replace that command. Definite initial failures permit
correction and another review. Current access rejection removes bank data, and
scope replacement cancels owned discovery and submission. Form and panel share
one navigation guard; local edits do not reacquire claims. Browser draft/retry
state is retained only during the editor lifecycle.

Preparation reserves each claim. A database unique constraint prevents a claim from appearing in another prepared/pending/successful instruction, even under competing requests. Original makers, the frozen beneficiary, and the currently linked beneficiary cannot prepare, release, or reconcile their own claims. Payment instructions preserve their original destinations, amounts, and evidence. A correction requires cancellation of the prepared batch and a new batch.

`POST /expenses/payments/{id}/release` requires an expected batch version, reason, idempotency key, and an operator different from the preparer. It changes the batch to `RELEASED` and its items to `PENDING`. `POST .../{id}/cancel` is limited to `PREPARED` and releases its reservations. A released batch cannot be cancelled because a bank instruction may already be in progress. There are at most 100 open and 10,000 total batches per company, and twenty preparation attempts per claim.

`GET .../{id}/export` produces a deterministic CSV finance worksheet with stable payment references, employee/destination data, IDR amounts, and no-store/attachment/nosniff headers. The worksheet is available only after release and before any item has been reconciled; partial or completed batches cannot produce new instructions. Text fields use a spreadsheet text prefix to preserve leading zeroes and prevent formula evaluation. This generic worksheet is not a bank-specific upload format. Bank submission remains manual; external processing must retain the stable payment reference and its own delivery/duplicate controls. Download replay returns the same instruction references and does not mark a transfer successful.

`POST .../{id}/reconcile` records one to 100 distinct pending item outcomes with the expected batch version, reason, and idempotency key. Success requires an actual transfer timestamp and a company-unique transaction reference. Use a bank-qualified reference when bank statements provide bank-local identifiers. Failure requires explicit `confirmedNoTransfer: true`, a timestamp, and a reason. A timeout or unknown outcome stays `PENDING` and cannot enter another batch. Results may be partial; resolving the final item closes the batch. A definitively failed item may enter a new explicit batch. A successful item can never be reserved again, and recorded outcomes cannot be overwritten. Corrections to confirmed settlement require a separately designed financial adjustment.

Batch transitions, item results, immutable action/evidence history, audit, and operation receipts commit together. Database constraints require matching amounts/counts, independent actors, complete action/result evidence, and creation-only instruction insertion. Updates use the expected batch version; competing release/cancellation/reconciliation cannot commit the same version. No external bank or storage I/O occurs inside these transactions.

`GET /expenses/payments`, `/{id}`, `/{id}/history`, and `/{id}/results` provide finance views. List ranges span at most 366 company-local days, with default/max page sizes of 50/200; each batch contains at most 100 items/results. `GET /expenses/claims/{id}/payments` exposes at most twenty status/timestamp records through ordinary current self/team/company claim scope, without bank destinations or bank transaction references. Approved claim evidence remains unchanged as payment progresses.

Payment reads and employee progress hold shared expense, people, company, membership, and account guards for their bounded transaction. They revalidate the original/live permission intersection after acquiring the guards; finance reads also recheck recent authentication. Revocation cannot race between the access check and acquisition of payment evidence. Independent readers can proceed together, while resource/access mutations serialize against them.

`GET /expenses/claims/{id}/payment-progress?scope=SELF` returns `claimId`, a
monotonic `version`, and the same bounded employee-safe `attempts`. Its default
scope is `SELF`; explicit `AUTHORIZED` uses the existing claim read policy.
Before the first attempt it returns version `0` with an empty list. Preparation,
release, cancellation and reconciliation advance a separate claim payment head
and capture `EXPENSE_PAYMENTS` in the business transaction. Replays and rollbacks
do not create another version. The original `/payments` list contract remains
available. Mobile clients explicitly select this collection alongside
`EXPENSE_CLAIMS`; current read/manage grants never widen its account-owned audience.
See [synchronization](../synchronization.md).
