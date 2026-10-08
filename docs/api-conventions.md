# API conventions

The API prefix is `/api/v1`. Authenticated company resources use `/companies/{companyId}`. The server resolves company access from the current account; a company ID is not an authorization grant.

Web clients first fetch `GET /auth/csrf`, retain the session cookie, and send the returned token in `X-CSRF-TOKEN` for mutations. Fetch a new token after login because authentication rotates both session and CSRF tokens.

Creation and versioned changes use a UUID `Idempotency-Key` header. A key is scoped by company, account, and operation. Identical retries return the original `{id, version}` response, including after later resource updates. Reusing a key with a different normalized payload returns HTTP 409 `operation_payload_mismatch`. Failed transactions do not consume their key. Generate a new key when changing a command, and retain the original key when retrying after a lost response.

Updates send the last observed `version`. HTTP 409 `stale_version` requires reading the resource again before applying a new command. Blindly replacing the version and retrying can overwrite somebody else's intent. Receipt handling and optimistic locking run inside the same database transaction as the business mutation, audit, and outbox insertion.

Problems contain a stable `code`, optional field codes, and a correlation ID. HTTP 400 means malformed input, 422 a domain validation failure, 401 authentication required, 403 denied scope, 404 missing resource, 409 conflict, 503 a temporarily unavailable dependency, and 500 an unexpected failure. Driver errors and credentials are not response content.

Company setup:

- `POST /companies`: code, name, IANA timezone; requires platform `companies.create`. The creator receives company administration permissions, without payroll finalization or payment permissions.
- `GET /companies/{companyId}`: current company details; requires `company.read`.
- `PUT /companies/{companyId}`: code, name, timezone, version; requires `company.manage`.
- `GET /me`: account, platform permissions, and available companies.
- `GET /companies/{companyId}/me/access`: current company permissions.

List defaults and limits are 50 and 200. Public cursors are endpoint-specific; clients treat them as opaque. Money is represented as decimal strings and timestamps as ISO 8601 UTC values. Schedule dates are interpreted in the explicitly configured IANA zone.

Organization and people:

- `GET /companies/{companyId}/organization-units`: optional kind, after, limit.
- `PUT /companies/{companyId}/organization-units/{id}`: code, name, kind, optional parentId/timezone, active, expectedVersion. A null expectedVersion creates a new UUID resource; an existing version updates it. Kind is immutable. Branches require an IANA timezone. Requires `company.manage` and an idempotency key.
- `POST /companies/{companyId}/employees`: id, employeeNumber, person, terms, reason. The person object has a distinct UUID, optional accountId, legalName, birthDate, nationality (ISO alpha-2), and optional email. Terms include effectiveFrom, contract, startDate/endDate, status, and optional organization/manager IDs. Requires `people.manage` and an idempotency key.
- `GET /companies/{companyId}/employees?asOf=YYYY-MM-DD`: query, after, limit. `people.read`, `people.team.read`, and `people.self.read` select company, direct-report, and self visibility respectively.
- `GET /companies/{companyId}/employees/{id}?asOf=YYYY-MM-DD`: scoped employment details, with `version` for editing and `appliedRevision` for the selected date.
- `POST /companies/{companyId}/employees/{id}/revisions`: version, terms, reason; append an effective change with an idempotency key. The original employment start is immutable.
- `GET /companies/{companyId}/employees/{id}/history`: after revision and limit; requires company-wide `people.read`.

Employee creation uses effectiveFrom equal to startDate. PERMANENT and FIXED_TERM contracts are supported; fixed-term employment requires an end date and does not permit probation. Changes never overwrite an earlier revision. Replays are resolved before mutable organization references are revalidated, while current authorization is still required.

Access and approvals:

- `GET /companies/{companyId}/members`: bounded account directory for `identity.manage`.
- `PUT /companies/{companyId}/members/{accountId}`: expectedVersion, active, permissions, reason. Null version adds an existing account; grants and revocations require an idempotency key.
- `GET /companies/{companyId}/approvals/templates?kind=LEAVE&asOf=YYYY-MM-DD`: effective policies for `approvals.manage`.
- `PUT /companies/{companyId}/approvals/templates/{id}`: name, kind, effectiveFrom, optional category, decimal-string minimumAmount, active, expectedVersion, stages, reason. Stage assignment is MANAGER, NAMED (accountIds), or PERMISSION (permission). Writes retain immutable policy revisions.
- `GET /companies/{companyId}/approvals`: bounded current assignment/delegation inbox; administrators additionally see blocked requests.
- `GET /companies/{companyId}/approvals/{id}`: request snapshot with effective assignments for participants and approval administrators.
- `POST /companies/{companyId}/approvals/{id}/reassign`: version, assignees, reason; changes only the current pending/blocked stage and retains the original snapshot.
- `GET /companies/{companyId}/approvals/delegations`: current/future delegations involving the account.
- `PUT /companies/{companyId}/approvals/delegations/{id}`: kind, fromAccount, toAccount, validFrom, validUntil, active, expectedVersion, reason. The delegator is immutable; ordinary approvers manage only their own delegations.

Approval changes use idempotency keys and optimistic versions. Business decision routes belong to their respective features so a successful approval and its business consequence cannot commit separately.
