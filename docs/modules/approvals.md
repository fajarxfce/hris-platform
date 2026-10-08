# Approvals

Own versioned templates, ordered stages, assignee snapshots, delegation periods, decisions, and inbox projections.

Conditions can select stages by category/amount and assign manager/role/named users. A valid decision completes its stage. Authors cannot approve their own request. Missing assignees block the request until audited reassignment. Existing requests retain the submitted template.

Shared transitions are pure policies. Each business module's use case coordinates ApprovalRepository with its own repository inside a transaction; approval never imports feature implementations or invokes their use cases.

Screens: inbox, request timeline, templates, delegation, blocked items.

Acceptance: competing decisions transition once, expiry/revocation are checked, self approval is denied, and policy edits never modify submitted requests.
