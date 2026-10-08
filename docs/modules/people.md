# People

Own companies, branches, departments, positions, cost centers, people, employments, reporting lines, and effective-dated changes.

Separate global person/account identity from company employee number, contract, and salary. Support administrative onboarding, probation, contract extension, transfers, and offboarding. A cross-company transfer creates a new employment and preserves the old history.

Foreign identity/document expiry and tax residency are separate attributes. Import supports preview, field errors, duplicate detection, and row outcomes. Referenced historical data is archived rather than destroyed.

Screens: company/employee directories, organization structure, employee tabs, import, scheduled changes.

Acceptance: concurrent employee number uniqueness, effective-dated history, company-reference constraints, safe import replay, and historical payroll unchanged.

## Implementation status

Company creation, reads, and versioned updates are implemented. Creation grants only the documented company administrator permissions. Idempotency receipts, membership, audit, and outbox records commit together; replay returns the original mutation outcome. Company codes are normalized to uppercase and timezones must be recognized IANA identifiers.

Organization unit creation/update/archive and cycle checks are implemented. Employee onboarding separates person and employment records. Employment revisions are append-only, optimistic, audited, and effective-dated; reads select the latest applicable revision for the requested date. Reporting line validation includes scheduled future boundaries. The database enforces company references, employee number uniqueness, and immutable revisions.

Employee directories and detail reads support company-wide HR, direct-report manager, and self scope. Their public projection excludes birth date and nationality. Lists and history use bounded cursor pagination. Current endpoints require an explicit `asOf` date for employee reads.

Personal profile editing, lifecycle checklists, CSV import, cross-company transfers, and automatic offboarding access changes remain planned. See [API conventions](../api-conventions.md).

Team visibility follows current reporting assignments using company-local dates from the server clock. Historical filters cannot restore former-manager access. Combined team/self grants include both scopes.
