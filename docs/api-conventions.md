# API conventions

The API prefix is `/api/v1`. Authenticated company resources use `/companies/{companyId}`. The server resolves company access from the current account; a company ID is not an authorization grant.

Web clients first fetch `GET /auth/csrf`, retain the session cookie, and send the returned token in `X-CSRF-TOKEN` for mutations. Fetch a new token after login because authentication rotates both session and CSRF tokens.

Creation and versioned changes use a UUID `Idempotency-Key` header. A key is scoped by company, account, and operation. Identical retries return the original `{id, version}` response, including after later resource updates. Reusing a key with a different normalized payload returns HTTP 409 `operation_payload_mismatch`. Failed transactions do not consume their key. Generate a new key when changing a command, and retain the original key when retrying after a lost response.

Updates send the last observed `version`. HTTP 409 `stale_version` requires reading the resource again before applying a new command. Blindly replacing the version and retrying can overwrite somebody else's intent. Receipt handling and optimistic locking run inside the same database transaction as the business mutation, audit, and outbox insertion.

Problems use RFC 9457 with a stable `code`, field-code map, unlocalized `parameters`, and a correlation ID. The [mobile contract](mobile-api.md) defines client translation, offline command replay, conflict handling, and cache scope. HTTP 400 means malformed input, 422 a domain validation failure, 401 authentication required, 403 denied scope, 404 missing resource, 409 conflict, 429 a rate limit, 503 a temporarily unavailable dependency, and 500 an unexpected failure. Driver errors and credentials are not response content. Malformed path/query/header values return 400 with a correlation ID. JSON bodies are limited to 1 MiB based on actual bytes, including chunked requests; larger bodies return 413. Upload streams use their separate document limits.

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

Work calendars:

- `GET /companies/{companyId}/workforce/shifts`: query, after code, limit.
- `PUT /companies/{companyId}/workforce/shifts/{id}`: code, name, startsAt/endsAt (minute precision), breakMinutes, IANA timezone, mode (ONSITE/REMOTE/FIELD), locationRequired, maxAccuracyMeters, optional fence, active, expectedVersion, reason.
- `PUT /companies/{companyId}/workforce/employees/{id}/schedule`: effectiveFrom, days keyed by MONDAY through SUNDAY with `{id, version}` shift references, expectedVersion, reason. Missing days are off; an empty pattern supports explicit rotating rosters. Version belongs to the employee's schedule, independently of employment version.
- `PUT /companies/{companyId}/workforce/employees/{id}/roster/{day}`: optional `{id, version}` shift, expectedVersion, reason. A null shift explicitly marks that date off. Version belongs to that roster date.
- `GET /companies/{companyId}/workforce/employees/{id}/calendar?from=YYYY-MM-DD&until=YYYY-MM-DD`: up to 62 days, with schedule version, WORK/OFF/UNASSIGNED status, UTC shift interval, and snapshot provenance.
- `GET /companies/{companyId}/workforce/holidays?from=YYYY-MM-DD&until=YYYY-MM-DD`: up to 366 days.
- `PUT /companies/{companyId}/workforce/holidays/{id}`: workDate, name, active, expectedVersion, reason.

All calendar writes require `workforce.manage` and an idempotency key. Schedule reads enforce company, team, or self access. Shift changes preserve already published assignments and rosters; publishing a new version requires a deliberate new assignment.

Team directory, employee details, and calendar authorization use current reporting assignments at the server instant in the company timezone. Historical/future date filters only select the returned projection; they cannot restore a former manager’s access. Team and self grants are combined.

Attendance:

- `POST /companies/{companyId}/workforce/employees/{id}/attendance/windows`: deviceId and idempotency key. Returns a one-use capture-window ID and expiry. Requires the current employee's `attendance.self.record` permission.
- `POST /companies/{companyId}/workforce/employees/{id}/attendance`: id, workDate, kind (CLOCK_IN/CLOCK_OUT), capturedAt, deviceId, optional windowId, explicit offline flag, and optional location `{latitude, longitude, accuracyMeters, mocked}`. Use a stable operation key when retrying the same event.
- `GET /companies/{companyId}/workforce/employees/{id}/attendance?from=YYYY-MM-DD&until=YYYY-MM-DD`: up to 31 dates, scoped to HR/current team/self. Returns immutable evidence, reviews, pending counts, incomplete pairs, and accepted minutes.
- `POST /companies/{companyId}/workforce/attendance/{id}/review`: version, decision (ACCEPT/REJECT), reason. Requires `attendance.verify` or the current manager's `attendance.team.verify`; self-review is denied.

Missing/expired windows and all explicit offline events require review. Reusing another account/device's window is rejected; a consumed window cannot back another event. Validation failures roll back both evidence and consumption. Unverified events never contribute to accepted minutes. A pending duplicate or unmatched checkout must be rejected or reviewed after its valid clock-in, rather than silently overwriting a punch.

Attendance corrections:

- `POST /companies/{companyId}/workforce/employees/{id}/attendance/corrections`: workDate, clockIn/clockOut (UTC minute precision), breakMinutes, expectedVersion, reason; requires `attendance.correct` and an idempotency key. Null expectedVersion creates the first correction; later edits use the current correction version. Null clock-in/out means an explicit absence.
- `GET /companies/{companyId}/workforce/employees/{id}/attendance/corrections?workDate=YYYY-MM-DD`: after revision, limit; follows workforce read scope and returns immutable correction history.

Corrected totals appear beside the original entries in daily attendance responses. Self-correction, future intervals, unresolved pending evidence, and stale versions are rejected. Closing rules will be added with period processing; this endpoint does not yet represent a finalized payroll amendment workflow.

Leave policies and balances:

- `PUT /companies/{companyId}/leave/types/{id}`: code, name, effectiveFrom, paid, allowPartialDays, minServiceMonths, allowedContracts, maxRequestDays, active, expectedVersion, reason. Requires `leave.manage` and an idempotency key; type code cannot change after creation.
- `GET /companies/{companyId}/leave/types?asOf=YYYY-MM-DD`: after code, limit; includes current `version` and the selected `appliedRevision`.
- `POST /companies/{companyId}/leave/employees/{id}/balances/{typeId}/{year}/adjustments`: decimal-string days (signed, nonzero, increments of 0.5, at most 366 in magnitude) and reason. Requires `leave.manage` and an idempotency key. Own balance adjustments are denied.
- `GET /companies/{companyId}/leave/employees/{id}/balances/{typeId}/{year}`: after opaque entry cursor and limit. Returns available/reserved/consumed day strings and a chronological, immutable ledger. Current company/team/self authorization applies independently of the balance year.

Balances are read consistently with their history. Manual reductions use only available balance; reserved/consumed units cannot be spent as available entitlement. Archived types retain ledger correction access without allowing new leave requests.

Leave requests:

- `POST /companies/{companyId}/leave/requests`: id, employeeId, typeId, days (`{workDate, portion}`), reason. Portions are FULL, FIRST_HALF, or SECOND_HALF. Requires own `leave.self.manage` or on-behalf `leave.manage`, with an idempotency key.
- `GET /companies/{companyId}/leave/requests`: optional employeeId, status, after, limit. Company-wide lists require `leave.read`; self/current-team reads require an explicit employeeId.
- `GET /companies/{companyId}/leave/requests/{id}`: optional historyAfter and historyLimit. Returns submitted policy/day snapshots, workflow state, version, availableActions, and bounded change history.
- `POST /companies/{companyId}/leave/requests/{id}/decisions`: version, decision (APPROVE/REJECT), reason. A rejection requires a reason. Applies to the current initial/cancellation workflow; requires its current independently assigned approver.
- `POST /companies/{companyId}/leave/requests/{id}/withdraw`: version, reason. Withdraws a pending request or its pending cancellation.
- `POST /companies/{companyId}/leave/requests/{id}/cancellation`: version, reason. Starts independent approval of cancellation for approved leave.

All request mutations use idempotency keys. Version is the leave request version, not the nested approval version. A submission includes at most 366 explicit dates within a 366-day span. Off/holiday dates are omitted from charged duration; missing schedules and ineligible employment dates fail the entire command. Reservations are made per balance year. Cancellation does not restore balance until approved. Submitted policy and schedule snapshots remain unchanged by later edits.

Password authentication counts successful and failed attempts in fixed 15-minute windows. Limits are ten per normalized account and one hundred per server-observed origin. HTTP 429 `sign_in_rate_limited` carries `Retry-After: 900`; clients must wait instead of automatically looping. Capacity exhaustion returns 503 `password_verification_busy`. Neither response confirms account existence.

Multi-factor authentication:

- `GET /me` includes `assurance` with required/verified/setupAvailable, validUntil, and recentUntil. A pending MFA session receives no company list or permission grants.
- `POST /auth/mfa/enrollment` with an `Idempotency-Key` creates a ten-minute enrollment for the signed-in account. A repeated key returns the same still-pending enrollment. Response: operationId, secret, otpauthUri, expiresAt.
- `POST /auth/mfa/enrollment/confirm`: operationId, code. Activates the authenticator and returns ten recovery codes once.
- `POST /auth/mfa/verify`: code and optional recovery boolean. Uses a current authenticator code or one unused recovery code.
- `POST /auth/mfa/recovery-codes`: replaces recovery codes using recent MFA. Other sessions and all old recovery codes are revoked.

All routes retain the `/api/v1` prefix and use the current session plus CSRF. Proof-bearing calls rotate session/CSRF tokens; refetch `/auth/csrf` afterward. Confirmation/verification codes are single-use, so do not retry them automatically after a lost response. Sign in again and use the next authenticator code; recent verification permits replacing recovery codes if their initial response was lost. MFA mutations are scoped to the authenticated account, never an account ID supplied in the body. Enrollment/proof responses use `Cache-Control: no-store`.

`mfa_setup_required`, `mfa_required`, and `recent_authentication_required` are HTTP 403 states requiring an explicit authentication interaction. `invalid_mfa_code` is 401; five attempts in a five-minute account window produce 429 `mfa_rate_limited`. A disabled account or changed credential version returns 401 `session_revoked` even on authentication metadata routes.


Native authentication uses `Authorization: Bearer <access-token>`. Send no Authorization header when posting `{ "refreshToken": "..." }` to `/api/v1/auth/native/refresh`; this endpoint requires `Idempotency-Key` and `Content-Type: application/json`, and does not read a cookie session. `/api/v1/auth/native/exchange` accepts `{ "deviceName": "..." }` with the authenticated cookie, CSRF token, and operation ID. Both return `sessionId`, `accessToken`, `refreshToken`, `accessExpiresAt`, `sessionExpiresAt`, and `tokenType` with `Cache-Control: no-store`. Store mobile refresh tokens in OS-backed secure storage; web clients retain their HttpOnly cookie workflow.

`GET /api/v1/auth/native/sessions` lists the account's active sessions. `DELETE /api/v1/auth/native/sessions/{sessionId}` revokes one session. Native MFA endpoints are `/api/v1/auth/native/mfa/verify` and `/api/v1/auth/native/mfa/recovery-codes`, both with an operation ID; their response contains `credentials` and, for regeneration, `recoveryCodes`. Cookie MFA endpoints reject bearer authentication. A malformed or invalid Authorization header cannot fall back to a valid cookie. Rotation invalidates the previous access token immediately; consumers must replace the pair together and serialize refresh work.


Job queries use `/api/v1/companies/{companyId}/jobs`, with `size` (maximum 200) and paired `beforeAt`/`beforeId` cursor fields. Detail is `/{jobId}`; `POST /{jobId}/cancel` takes `expectedVersion`. Cancellation is requested first and acknowledged by the feature worker later. Own-job access is allowed within an active company membership; `jobs.read` broadens reads and `jobs.manage` permits cancelling other accounts' jobs. Responses omit stored authentication snapshots, request payloads, and internal lease tokens. Feature workflows own job submission and recovery.
