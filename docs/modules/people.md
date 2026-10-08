# People

Own companies, branches, departments, positions, cost centers, people, employments, reporting lines, and effective-dated changes.

Separate global person/account identity from company employee number, contract, and salary. Support administrative onboarding, probation, contract extension, transfers, and offboarding. A cross-company transfer creates a new employment and preserves the old history.

Foreign identity/document expiry and tax residency are separate attributes. Import supports preview, field errors, duplicate detection, and row outcomes. Referenced historical data is archived rather than destroyed.

Screens: company/employee directories, organization structure, employee tabs, import, scheduled changes.

Acceptance: concurrent employee number uniqueness, effective-dated history, company-reference constraints, safe import replay, and historical payroll unchanged.

## Implementation status

Company creation, reads, and versioned updates are implemented. Creation grants only the documented company administrator permissions. Idempotency receipts, membership, audit, and outbox records commit together; replay returns the original mutation outcome. Company codes are normalized to uppercase and timezones must be recognized IANA identifiers.

Organization units, people, effective employment history, imports, and transfer workflows remain planned. See [API conventions](../api-conventions.md).
