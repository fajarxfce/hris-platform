# Expenses

Own expense categories, policies, claim lines/receipts, review, payment batches, and reconciliation.

Claims store IDR amount, transaction date, cost center, and evidence. Snapshot limits on submit. Suspected duplicate receipts are review signals. Support return-for-correction/rejection with reasons and approval before payment.

Payments have stable item references and per-item success/failure status. Paid claims cannot enter another batch. Reimbursements are independently paid in the first version. External bank submission remains manual.

Screens: claims, review, policy setup, payment batches, reconciliation.

Acceptance: category limits, company attachment access, concurrent submission, payment replay, failed/partial batch reconciliation, and immutable paid records.

## Category policies

`PUT /api/v1/companies/{companyId}/expenses/categories/{id}` creates or revises a category with an idempotency key, expected version (null on create), effective date, and reason. `expenses.policy.manage` is a distinct grant included in the company-administrator and finance permission catalogs; existing memberships/role snapshots require an explicit update to receive it.

Policies define a display name, maximum amount per line and per claim for that category, receipt/cost-center requirements, maximum transaction age (1–366 days), eligible permanent/fixed-term contracts, and active status. Amounts use decimal strings in IDR, with at most two decimal places and a maximum of 999,999,999,999.99. The category code is immutable; archive by creating an inactive policy revision.

`GET /expenses/categories?asOf=YYYY-MM-DD` selects the latest effective revision for each category. Responses distinguish the current optimistic `version` from the selected `appliedRevision`. Backdated corrections do not supersede a later effective revision. Authorized expense users can read applicable policy; `GET /expenses/categories/{id}/history` requires policy administration and returns immutable revisions, actors, reasons, and timestamps. Both lists use bounded cursor pagination (default 50, maximum 200).

Each company allows 500 categories and each category 1,000 revisions. Competing definitions/updates use company serialization and optimistic versions. Current account, company, membership, and permission are checked before a mutation or receipt replay. Policy history, version, audit, and operation receipt commit together; no external I/O runs in that transaction. Submission uses these effective policies; payment processing uses the approved immutable evidence.

## Claim drafts

`PUT /expenses/claims/{id}/draft` saves a draft with an employment ID, title, description, lines, reason, and expected version (null on create). Every line has a stable UUID, category, transaction date, decimal IDR amount, description, optional cost center, and receipt revision IDs. Drafts allow up to twenty lines and three receipts per line. An empty draft is allowed; policy eligibility, mandatory evidence, and approval routing are checked when the claim is submitted.

References must belong to the same company. Cost centers must have the correct organization-unit kind. Receipt documents must use the `RECEIPT` classification and belong to the claim's employment. A draft can reference an unfinished upload or an archived category while it is being prepared; saving does not make its evidence ready or authorize reimbursement. Claim employment and original author cannot be changed.

`expenses.self.manage` allows the linked employee to save/cancel their claim. `expenses.manage` allows authorized administration on behalf of an employee and is included in the company-administrator and finance catalogs for new grants. Existing membership/role snapshots require an explicit grant update. Reads also support `expenses.read` and the current manager's `expenses.team.read`. A former manager loses access when the employment's reporting relationship changes.

`GET /expenses/claims/{id}` returns the current draft. `/drafts` returns immutable draft revisions (maximum twenty per page); `/history` returns versioned actions (maximum 200 per page). `GET /expenses/claims` requires `from` and `until` dates spanning at most 366 days in the company timezone and supports status, employment, and cursor filters. Users without company-wide expense read/manage permission must provide an authorized employment scope. Summary pages default to fifty and allow at most 200 rows.

Every edit appends a complete revision; previous lines, receipts, totals, actors, and reasons remain unchanged. Database constraints prevent appending contents to an already committed draft and verify line counts/totals before commit. Header, revision, evidence references, action history, audit, and idempotency receipt commit together. A lost response can be replayed after fresh authorization without changing the current revision.

Limits are 1,000 open claims (`DRAFT`, `PENDING`, or `RETURNED`) per company, fifty per creating account, and 100 draft revisions per claim. Submission keeps its capacity reservation until a terminal outcome. `POST /expenses/claims/{id}/cancel` requires the expected version and a reason, releases open-claim capacity, and preserves its evidence/history. Cancellation remains available after the revision limit. Competing edits/cancellation cannot both commit the same version. Payment progress remains separate from the approved claim status.

Commands recheck live account, membership, and credential state before replay. Their permissions are limited to the intersection with the original request, so a grant made while a command waits cannot expand its authority.

## Submission and withdrawal

`POST /expenses/claims/{id}/submit` requires a client-generated `submissionId`, expected claim version, reason, and idempotency key. It freezes an immutable submission and creates an `EXPENSE` approval whose resource ID is that submission ID. The mutation receipt returns the claim ID/version; the caller's submission ID remains usable after a lost response or a later correction.

Submission requires at least one line. Transaction dates cannot be in the future or more than 366 days old; the selected category can impose a shorter age limit. Eligibility and category policy use the transaction date. Every referenced cost center must still be active, and every receipt must be a `READY` revision belonging to the same company's employment with the `RECEIPT` classification. Submission reads immutable validated metadata inside its transaction; it does not access object storage or run another scan.

The snapshot retains the exact category revision, observed policy version, cost-center name/code/version, employee identity, and receipt name/type/size/SHA-256. Later policy edits, archival, and label changes cannot rewrite it. Category totals include all lines in that category, including lines that selected different effective revisions; every applicable per-claim cap must be satisfied. Each category must select the same approval template using the full claim amount. A mixed claim that would use different workflows must be split.

All historical draft authors, the original creator, and the submitter are retained as makers and excluded from the initial approval candidates. The beneficiary is also excluded. A stage without an eligible reviewer is explicitly `BLOCKED`. Candidate overflow fails before maker filtering; it cannot silently truncate an approval group.

Repeated receipt digests within the claim or in another current pending or approved claim are saved as `possibleDuplicate` review signals. They do not automatically reject submission or expose another claimant's identity. The signal on an existing submission remains historical; review processing re-evaluates duplication before accepting a decision.

`GET /expenses/submissions/{submissionId}` returns only that submitted evidence and its approval/current claim status. Current self/team/company scope applies, and an assigned reviewer with live permissions or a valid delegation can read this exact submission. A historical reviewer does not gain access to the current unsubmitted draft or its history. `GET /expenses/claims/{id}/submissions` uses ordinary claim scope and returns summaries with a number cursor, at most twenty per page.

Pending claims cannot be edited or cancelled directly. `POST /expenses/submissions/{id}/withdraw` cancels the current pending/blocked approval and returns the claim to `DRAFT`, preserving all submitted evidence. It requires management scope, an expected claim version, and a reason. A later edit creates a new draft revision; resubmission creates a new submission/approval. There are at most twenty submissions per claim. Withdrawal and draft cancellation remain possible at that limit.

Claim transitions, submission evidence, approval state, action history, audit, and operation receipts commit together. Database insertion fences prohibit adding evidence to a committed submission, and deferred constraints require complete line/receipt snapshots. Competing saves/submissions/withdrawals use the same optimistic claim version and serialization guards. Replays recheck current account, company, membership, credentials, and resource access.

## Review and correction

`POST /expenses/submissions/{id}/decisions` accepts `APPROVE`, `REJECT`, or `RETURN`, an expected claim version, an expected approval version, a reason, and optional `acknowledgeDuplicates`. Both versions matter: reassignment invalidates a decision from an older screen even when the same reviewer remains assigned. There is no generic approval endpoint that can skip the expense business transition.

Only an assigned reviewer with a live expense-approval permission, or their currently authorized delegate, can decide. The original beneficiary, current beneficiary account, and every frozen maker are excluded, including when they are the source of a delegation. An account linked to the employee after submission is still the beneficiary. Generic administrative reassignment cannot bypass these expense checks; an ineligible assignment must be corrected.

Each stage stores an immutable review with the actor, delegator, approval/claim versions, decision, resulting status, current duplicate digests, acknowledgement, reason, and time. Approval advances one stage or produces `APPROVED`; rejection produces terminal `REJECTED`. Return records a rejected approval attempt with the distinct expense outcome `RETURNED`. Rejection and return require a reason. A returned claim must receive a new draft revision before resubmission, or it can be cancelled. Earlier evidence and reviews remain readable through their original submission ID.

An approval with currently duplicated receipt bytes requires explicit acknowledgement and a nonblank explanation. The current signal includes other pending/approved claims and repeated bytes within this submission. A cancelled/withdrawn/rejected other claim no longer causes a current signal, while submitted/reviewed historical signals remain unchanged. The submission detail exposes current digests separately from the frozen receipt flags and includes at most eight stage reviews.

Review uses the same company expense/approval guards as submission and withdrawal. It rechecks actor/delegator access, expiry, current beneficiary, and resource scope before a decision or receipt replay. Competing reviewers or withdrawal cannot commit the same claim version. The shared approval decision, expense status, review evidence, history, audit, and idempotency receipt form one transaction. Database constraints require matching review evidence for each review transition and prohibit changing terminal approved/rejected claims. Payment progress is tracked separately from this approved evidence.

## Receipt content

`GET/HEAD /expenses/submissions/{submissionId}/receipts/{revisionId}/content` serves a receipt attached to that exact immutable submission. Current claim owners, current managers with team read permission, company expense readers, and assigned reviewers with current approval access or valid delegation can use it. It does not grant general document/profile access, and an old submission cannot authorize a receipt added to a later draft.

Metadata and each content read revalidate company/account/credential access and current resource scope. The referenced revision must remain ready with the submitted size and digest. The storage read occurs outside the database transaction; its bytes are checked against the manifest, and access is checked again before delivery. Revocation during a pending read prevents those bytes from being sent. Range, ETag, conditional GET, and HEAD use the shared bounded download transport; HEAD/unchanged responses still authorize the caller and do not read storage. Responses use private no-store caching, attachment disposition, and nosniff.

## Payment batches

`expenses.pay` is a protected grant, separate from expense read/manage/approval. Company administrators do not receive it automatically. Payment queries, bank details, exports, and commands require current company/account/membership access and recent authentication (recent MFA when enforced). Commands recheck the original request's permission intersection after acquiring their resource/account guards and before receipt replay.

`GET /expenses/payments/payables?from=YYYY-MM-DD&until=YYYY-MM-DD` lists approved claims without an unresolved or successful payment reservation. `PUT /expenses/payments/{id}` prepares an immutable batch of one to 100 instructions with stable item IDs, submission IDs, bank codes, account numbers/names, title, reason, and idempotency key. Finance supplies independently verified destinations for this batch; it does not change a person's profile or define a reusable bank-account master. Claim amounts and employee labels come from the approved submission, not the request. All amounts are IDR decimal strings.

Preparation reserves each claim. A database unique constraint prevents a claim from appearing in another prepared/pending/successful instruction, even under competing requests. Original makers, the frozen beneficiary, and the currently linked beneficiary cannot prepare, release, or reconcile their own claims. Payment instructions preserve their original destinations, amounts, and evidence. A correction requires cancellation of the prepared batch and a new batch.

`POST /expenses/payments/{id}/release` requires an expected batch version, reason, idempotency key, and an operator different from the preparer. It changes the batch to `RELEASED` and its items to `PENDING`. `POST .../{id}/cancel` is limited to `PREPARED` and releases its reservations. A released batch cannot be cancelled because a bank instruction may already be in progress. There are at most 100 open and 10,000 total batches per company, and twenty preparation attempts per claim.

`GET .../{id}/export` produces a deterministic CSV finance worksheet with stable payment references, employee/destination data, IDR amounts, and no-store/attachment/nosniff headers. The worksheet is available only after release and before any item has been reconciled; partial or completed batches cannot produce new instructions. Text fields use a spreadsheet text prefix to preserve leading zeroes and prevent formula evaluation. This generic worksheet is not a bank-specific upload format. Bank submission remains manual; external processing must retain the stable payment reference and its own delivery/duplicate controls. Download replay returns the same instruction references and does not mark a transfer successful.

`POST .../{id}/reconcile` records one to 100 distinct pending item outcomes with the expected batch version, reason, and idempotency key. Success requires an actual transfer timestamp and a company-unique transaction reference. Use a bank-qualified reference when bank statements provide bank-local identifiers. Failure requires explicit `confirmedNoTransfer: true`, a timestamp, and a reason. A timeout or unknown outcome stays `PENDING` and cannot enter another batch. Results may be partial; resolving the final item closes the batch. A definitively failed item may enter a new explicit batch. A successful item can never be reserved again, and recorded outcomes cannot be overwritten. Corrections to confirmed settlement require a separately designed financial adjustment.

Batch transitions, item results, immutable action/evidence history, audit, and operation receipts commit together. Database constraints require matching amounts/counts, independent actors, complete action/result evidence, and creation-only instruction insertion. Updates use the expected batch version; competing release/cancellation/reconciliation cannot commit the same version. No external bank or storage I/O occurs inside these transactions.

`GET /expenses/payments`, `/{id}`, `/{id}/history`, and `/{id}/results` provide finance views. List ranges span at most 366 company-local days, with default/max page sizes of 50/200; each batch contains at most 100 items/results. `GET /expenses/claims/{id}/payments` exposes at most twenty status/timestamp records through ordinary current self/team/company claim scope, without bank destinations or bank transaction references. Approved claim evidence remains unchanged as payment progresses.
