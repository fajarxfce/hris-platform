# Approvals

Own versioned templates, ordered stages, assignee snapshots, delegation periods, decisions, and inbox projections.

Conditions can select stages by category/amount and assign manager/role/named users. A valid decision completes its stage. Authors cannot approve their own request. Missing assignees block the request until audited reassignment. Existing requests retain the submitted template.

Shared transitions are pure policies. Each business module's use case coordinates ApprovalRepository with its own repository inside a transaction; approval never imports feature implementations or invokes their use cases.

Screens: inbox, request timeline, templates, delegation, blocked items.

Acceptance: competing decisions transition once, expiry/revocation are checked, self approval is denied, and policy edits never modify submitted requests.

## Implementation status

Effective template revisions, bounded stage rules (up to eight stages), named/manager/permission assignment, immutable request snapshots, optimistic decisions, blocked-stage reassignment, delegation ownership/expiry/revocation policy, and the current inbox are implemented. Assignment overrides are separate immutable records; the original snapshot remains intact. Decisions cannot come from the author or beneficiary. Delegated decisions require the delegator's current account, company membership, and action permission.

Feature use cases must coordinate their own business effect with ApprovalRepository in the same transaction. There is intentionally no generic HTTP decision endpoint: leave, expense, overtime, and payroll delivery belong to those modules. Leave submission, decisions, withdrawal, and cancellation already apply their business consequences atomically; expense submission, decisions, return-for-correction, and withdrawal also use this boundary. Overtime decisions and payroll aggregate review also apply business consequences through this boundary. Payroll templates use named or permission assignments because an aggregate has no single employee manager. Administrative reassignment and delegation changes require reasons and idempotency keys. Delegation periods are bounded to 90 days and cannot be reassigned to a different delegator.


## Access and concurrent changes

Approval mutations serialize per company. Business use cases acquire their business locks first, then the approval, company, membership, and account guards. Account IDs are locked in a consistent order; global account administration and invitation use the same account ordering. Delegation revocation, template changes, reassignment, and a leave decision cannot interleave inside the approval transaction. A revocation committed before a waiting decision acquires its guard is applied to that decision. A decision already protected by its guard completes before the competing revocation.

Commands revalidate account state, credential version, membership, and the original request's permissions before mutation or receipt replay. New grants require a freshly resolved request. Delegated decisions check both parties' live access and the delegation period at decision time. Leave and expense decisions also exclude the employment's current beneficiary account, including an account linked after submission or before employment starts; delegation cannot bypass this exclusion. Expenses additionally exclude every frozen draft maker. The inbox excludes assignments when the corresponding action permission or the delegator's access has been removed.

## Frozen maker exclusions

Feature use cases can supply at most 200 additional maker account IDs when creating an approval snapshot. These IDs are retained in `excludedAccountIds`; they are not supplied or edited through an HTTP request. Initial assignment, reassignment, direct decisions, delegated decisions, and the actionable inbox all respect them. Exclusion never removes the existing author/requester checks or the consuming feature's current-beneficiary policy. A blocked request remains visible to approval administrators for repair.

Expense submissions supply their complete retained maker set. Migration V48 derives the same set from existing expense submission evidence; it does not guess past account ownership. Stored exclusions are immutable. Database constraints also reject new assignments and decisions using an excluded maker.

## Bounded administration and selection

`GET /approvals/templates?kind=...&asOf=YYYY-MM-DD` and `GET /approvals/delegations` return `{items, nextCursor}` pages. Both accept `after` and `limit` (default fifty, maximum 200). Template pages include inactive definitions effective by the selected date. Delegation pages include active and inactive, unexpired incoming/outgoing delegations owned by the current account.

A company can have 200 active templates and 1,000 total definitions per approval kind. An account can have 200 active, unexpired delegations and 1,000 total unexpired delegations in its company. Future delegation periods reserve capacity. Deactivation releases active capacity; expiry releases unexpired capacity. Existing definitions can still be edited/deactivated if legacy data exceeds a limit, while adding or reactivating beyond capacity is rejected.

Policy selection reads at most 201 active effective templates, and delegated decisions read at most 201 current active delegations. The additional row detects overflow: selection/decision fails explicitly instead of choosing from a truncated set. Paged administration remains available to repair oversized data. Reasons, optimistic versions, audit, and original operation receipts remain transactional.

## Dashboard

The [approval inbox and stage review](../dashboard.md#approval-inbox-and-request-stages) are implemented. Template editing, delegation management, reassignment, decision history, and business-specific review actions remain subsequent workflows.
