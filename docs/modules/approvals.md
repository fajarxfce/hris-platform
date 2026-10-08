# Approvals

Own versioned templates, ordered stages, assignee snapshots, delegation periods, decisions, and inbox projections.

Conditions can select stages by category/amount and assign manager/role/named users. A valid decision completes its stage. Authors cannot approve their own request. Missing assignees block the request until audited reassignment. Existing requests retain the submitted template.

Shared transitions are pure policies. Each business module's use case coordinates ApprovalRepository with its own repository inside a transaction; approval never imports feature implementations or invokes their use cases.

Screens: inbox, request timeline, templates, delegation, blocked items.

Acceptance: competing decisions transition once, expiry/revocation are checked, self approval is denied, and policy edits never modify submitted requests.

## Implementation status

Effective template revisions, bounded stage rules (up to eight stages), named/manager/permission assignment, immutable request snapshots, optimistic decisions, blocked-stage reassignment, delegation ownership/expiry/revocation policy, and the current inbox are implemented. Assignment overrides are separate immutable records; the original snapshot remains intact. Decisions cannot come from the author or beneficiary. Delegated decisions require the delegator's current account, company membership, and action permission.

Feature use cases must coordinate their own business effect with ApprovalRepository in the same transaction. There is intentionally no generic HTTP decision endpoint: leave, expense, overtime, and payroll delivery belong to those modules. Their end-to-end business effects remain planned. Administrative reassignment and delegation changes require reasons and idempotency keys. Delegation periods are bounded to 90 days and cannot be reassigned to a different delegator.
