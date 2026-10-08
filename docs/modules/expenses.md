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

Each company allows 500 categories and each category 1,000 revisions. Competing definitions/updates use company serialization and optimistic versions. Current account, company, membership, and permission are checked before a mutation or receipt replay. Policy history, version, audit, and operation receipt commit together; no external I/O runs in that transaction. Claim submission and payment workflows are separate implementation slices.
