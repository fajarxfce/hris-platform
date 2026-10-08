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

Each company allows 500 categories and each category 1,000 revisions. Competing definitions/updates use company serialization and optimistic versions. Current account, company, membership, and permission are checked before a mutation or receipt replay. Policy history, version, audit, and operation receipt commit together; no external I/O runs in that transaction. Submission uses these effective policies; review and payment workflows are separate implementation slices.

## Claim drafts

`PUT /expenses/claims/{id}/draft` saves a draft with an employment ID, title, description, lines, reason, and expected version (null on create). Every line has a stable UUID, category, transaction date, decimal IDR amount, description, optional cost center, and receipt revision IDs. Drafts allow up to twenty lines and three receipts per line. An empty draft is allowed; policy eligibility, mandatory evidence, and approval routing are checked when the claim is submitted.

References must belong to the same company. Cost centers must have the correct organization-unit kind. Receipt documents must use the `RECEIPT` classification and belong to the claim's employment. A draft can reference an unfinished upload or an archived category while it is being prepared; saving does not make its evidence ready or authorize reimbursement. Claim employment and original author cannot be changed.

`expenses.self.manage` allows the linked employee to save/cancel their claim. `expenses.manage` allows authorized administration on behalf of an employee and is included in the company-administrator and finance catalogs for new grants. Existing membership/role snapshots require an explicit grant update. Reads also support `expenses.read` and the current manager's `expenses.team.read`. A former manager loses access when the employment's reporting relationship changes.

`GET /expenses/claims/{id}` returns the current draft. `/drafts` returns immutable draft revisions (maximum twenty per page); `/history` returns versioned actions (maximum 200 per page). `GET /expenses/claims` requires `from` and `until` dates spanning at most 366 days in the company timezone and supports status, employment, and cursor filters. Users without company-wide expense read/manage permission must provide an authorized employment scope. Summary pages default to fifty and allow at most 200 rows.

Every edit appends a complete revision; previous lines, receipts, totals, actors, and reasons remain unchanged. Database constraints prevent appending contents to an already committed draft and verify line counts/totals before commit. Header, revision, evidence references, action history, audit, and idempotency receipt commit together. A lost response can be replayed after fresh authorization without changing the current revision.

Limits are 1,000 open claims (`DRAFT` or `PENDING`) per company, fifty per creating account, and 100 draft revisions per claim. Submission keeps its capacity reservation until a terminal outcome. `POST /expenses/claims/{id}/cancel` requires the expected version and a reason, releases open-claim capacity, and preserves its evidence/history. Cancellation remains available after the revision limit. Competing edits/cancellation cannot both commit the same version. Expense decisions, finance receipt downloads, and payment reconciliation remain separate slices.

Commands recheck live account, membership, and credential state before replay. Their permissions are limited to the intersection with the original request, so a grant made while a command waits cannot expand its authority.

## Submission and withdrawal

`POST /expenses/claims/{id}/submit` requires a client-generated `submissionId`, expected claim version, reason, and idempotency key. It freezes an immutable submission and creates an `EXPENSE` approval whose resource ID is that submission ID. The mutation receipt returns the claim ID/version; the caller's submission ID remains usable after a lost response or a later correction.

Submission requires at least one line. Transaction dates cannot be in the future or more than 366 days old; the selected category can impose a shorter age limit. Eligibility and category policy use the transaction date. Every referenced cost center must still be active, and every receipt must be a `READY` revision belonging to the same company's employment with the `RECEIPT` classification. Submission reads immutable validated metadata inside its transaction; it does not access object storage or run another scan.

The snapshot retains the exact category revision, observed policy version, cost-center name/code/version, employee identity, and receipt name/type/size/SHA-256. Later policy edits, archival, and label changes cannot rewrite it. Category totals include all lines in that category, including lines that selected different effective revisions; every applicable per-claim cap must be satisfied. Each category must select the same approval template using the full claim amount. A mixed claim that would use different workflows must be split.

All historical draft authors, the original creator, and the submitter are retained as makers and excluded from the initial approval candidates. The beneficiary is also excluded. A stage without an eligible reviewer is explicitly `BLOCKED`. Candidate overflow fails before maker filtering; it cannot silently truncate an approval group.

Repeated receipt digests within the claim or in another current pending claim are saved as `possibleDuplicate` review signals. They do not automatically reject submission or expose another claimant's identity. The signal on an existing submission remains historical; subsequent approval processing will re-evaluate duplication before accepting a decision.

`GET /expenses/submissions/{submissionId}` returns only that submitted evidence and its approval/current claim status. Current self/team/company scope applies, and an assigned reviewer with live permissions or a valid delegation can read this exact submission. A historical reviewer does not gain access to the current unsubmitted draft or its history. `GET /expenses/claims/{id}/submissions` uses ordinary claim scope and returns summaries with a number cursor, at most twenty per page.

Pending claims cannot be edited or cancelled directly. `POST /expenses/submissions/{id}/withdraw` cancels the current pending/blocked approval and returns the claim to `DRAFT`, preserving all submitted evidence. It requires management scope, an expected claim version, and a reason. A later edit creates a new draft revision; resubmission creates a new submission/approval. There are at most twenty submissions per claim. Withdrawal and draft cancellation remain possible at that limit.

Claim transitions, submission evidence, approval state, action history, audit, and operation receipts commit together. Database insertion fences prohibit adding evidence to a committed submission, and deferred constraints require complete line/receipt snapshots. Competing saves/submissions/withdrawals use the same optimistic claim version and serialization guards. Replays recheck current account, company, membership, credentials, and resource access.
