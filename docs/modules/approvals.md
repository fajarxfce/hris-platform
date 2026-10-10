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

Commands revalidate account state, credential version, membership, session MFA age, and the original request's permissions after pending guards and before mutation or receipt replay. New grants require a freshly resolved request. Delegated decisions check both parties' live access and the delegation period at decision time. Leave and expense decisions also exclude the employment's current beneficiary account, including an account linked after submission or before employment starts; delegation cannot bypass this exclusion. Expenses additionally exclude every frozen draft maker. The inbox excludes assignments when the corresponding action permission or the delegator's access has been removed.

Interactive inbox, request, template, and delegation reads acquire the shared company approval guard before shared company, membership, and actor-account guards. They revalidate current session assurance after acquisition. The approval guard keeps request versions and separately loaded assignment overrides in one coherent observation while allowing concurrent readers. Commands retain their exclusive guard and cannot change assignments between those queries.

## Frozen maker exclusions

Feature use cases can supply at most 200 additional maker account IDs when creating an approval snapshot. These IDs are retained in `excludedAccountIds`; they are not supplied or edited through an HTTP request. Initial assignment, reassignment, direct decisions, delegated decisions, and the actionable inbox all respect them. Exclusion never removes the existing author/requester checks or the consuming feature's current-beneficiary policy. A blocked request remains visible to approval administrators for repair.

Expense submissions supply their complete retained maker set. Migration V48 derives the same set from existing expense submission evidence; it does not guess past account ownership. Stored exclusions are immutable. Database constraints also reject new assignments and decisions using an excluded maker.

## Bounded administration and selection

`GET /approvals/templates?kind=...&asOf=YYYY-MM-DD` and `GET /approvals/delegations` return `{items, nextCursor}` pages. Both accept `after` and `limit` (default fifty, maximum 200). Template pages include inactive definitions effective by the selected date. Delegation pages include active and inactive, unexpired incoming/outgoing delegations owned by the current account.

A company can have 200 active templates and 1,000 total definitions per approval kind. An account can have 200 active, unexpired delegations and 1,000 total unexpired delegations in its company. Future delegation periods reserve capacity. Deactivation releases active capacity; expiry releases unexpired capacity. Existing definitions can still be edited/deactivated if legacy data exceeds a limit, while adding or reactivating beyond capacity is rejected.

Inactive templates and delegations still require company-scoped account references. Revoked local memberships and inactive local accounts may be retained while disabling a rule; they cannot be substituted with accounts outside the company or reactivated without renewed eligibility. Decimal thresholds retain their numeric value when PostgreSQL pads fractional zeros. Extra meaningful precision, excess scale, and exponent notation remain invalid.

Policy selection reads at most 201 active effective templates, and delegated decisions read at most 201 current active delegations. The additional row detects overflow: selection/decision fails explicitly instead of choosing from a truncated set. Paged administration remains available to repair oversized data. Reasons, optimistic versions, audit, and original operation receipts remain transactional.

## Dashboard

The [approval inbox and stage review](../dashboard.md#approval-inbox-and-request-stages) and [template administration](../dashboard.md#approval-template-administration) are implemented. Templates support effective-date filters, exact rule revisions, ordered stages, bounded approver lookup, protected editing, and explicit receipt recovery. Delegation management, reassignment, decision history, and business-specific review actions remain subsequent workflows.

## Administration detail and assignee lookup

`GET /approvals/templates/{id}` returns the latest configuration to an approval administrator. `?revision=N` selects an exact retained rule revision. `version` is the current editable header version; `appliedRevision`, effective date, category, minimum amount, and stages identify the selected rules. Name and activation are current header fields. Editing must fetch the latest configuration rather than treat an older effective revision as the current draft.

`GET /approvals/delegations/{id}` requires `approvals.read` and ownership as delegator/recipient, or `approvals.manage`. Exact details remain available after deactivation or expiry. Missing, foreign-company, and unowned records use the same not-found result. Both detail readers hold the shared approval/access guards and check live MFA after waiting.

`GET /approvals/assignees?kind=...&query=...&after=...&limit=...` returns only `{id, displayName}` references. The caller needs `approvals.manage`, or `approvals.read` plus the decision permission for the selected kind. The query is limited to 120 characters and pages to 200 entries. Results require an active company membership, active account, and a matching decision permission. Lookup uses shared company/membership/account access guards and never acquires or returns email addresses, credentials, or permission lists. A lookup result does not authorize a future assignment: business commands still check current access, request ownership, and maker exclusions.
