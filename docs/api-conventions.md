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
