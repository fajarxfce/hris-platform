# Identity

Own accounts, sessions, invitations, OIDC links, MFA, memberships, roles, and permission assignments.

Use verified issuer/subject for OIDC. Local invitations and recovery tokens are single-use. Web cookies use HttpOnly/Secure and CSRF; mobile refresh rotation detects reuse. Privileged accounts require MFA and sensitive actions recent authentication.

Group/company administration, HR, manager, finance, auditor, and employee have explicit action/resource scopes. Disabling account or membership revokes that access without granting payroll access through administrative role names.

Screens: users, roles, company assignments, SSO configuration, active sessions.

Acceptance: cross-company/resource denial, refresh replay/revocation, invitation reuse, stale memberships, CSRF, and secret-free diagnostics.

## Implementation status

Password sign-in, administrator bootstrap, server sessions in PostgreSQL, CSRF/session ID rotation, current-account reads, and live company authorization are implemented. Company member directories and versioned permission/activation updates are also implemented. Sensitive payroll/payment grants require platform administration and cannot be self-granted; company membership changes preserve the last administrator and are audited. Authenticator MFA, recovery codes, credential-version revocation, and recent-authentication checks are implemented. OIDC, invitations/password recovery, native tokens, and configurable role templates remain planned.

Endpoints: GET /api/v1/auth/csrf, POST /api/v1/auth/login, POST /api/v1/auth/logout, GET /api/v1/me, GET /api/v1/companies/{companyId}/me/access. Login uses JSON email/password and a current X-CSRF-TOKEN header. Fetch a new CSRF token after successful login. Cookies are Secure by default; set HRIS_SECURE_COOKIES=false only for local HTTP development.

Password attempts share PostgreSQL budgets across application instances: ten attempts per normalized account and one hundred per observed origin in each 15-minute window. Failed and capacity-rejected attempts retain their budget consumption; expiration reopens the window. Responses remain indistinguishable for missing/incorrect accounts. HTTP 429 includes Retry-After. The application ignores client-supplied forwarded-address claims unless a trusted proxy is configured at deployment. Rate buckets store hashes and expired buckets are removed in batches of at most 100 during later attempts after a one-day retention period.

Argon2 work is limited to eight concurrent operations, without a waiting queue. Capacity returns 503; cancellation and failures release their permits. The request origin is not retained in the persisted security context. These limits are enabled by default; independent business integration suites disable rate limits explicitly, while dedicated tests exercise the default enforcement.

MFA uses RFC 6238 SHA-1 authenticator codes with a 30-second period and one adjacent period of tolerance. Each counter and recovery code can be consumed once, serialized under the account lock. Setup/verification attempts share a five-attempt, five-minute account budget. Codes generated for recovery are random, stored only as hashes, and replaced as a set. Credentials and recovery changes commit with audit/outbox; failed technical mutations roll back together.

Privileged and voluntarily enrolled accounts require a verified factor. The API entry interceptor resolves current access once and enforces this independently of controller parameters. Only self-service authentication metadata/setup/challenges allow pending MFA. Pending accounts do not receive company directories or permissions. A factor proof lasts at most 12 hours; membership changes and recovery-code replacement require a proof less than ten minutes old. Replacing recovery codes increments the security version, invalidating other sessions and old codes.

Authenticator secrets use AES-256-GCM with random nonces, an explicit key version, and account-bound associated data. The keyring comes from deployment secrets. Enrollment and proof responses are not cacheable. Missing encryption configuration never disables the MFA requirement. See [development](../development.md) for enrollment, key retention, lost-response recovery, and local setup.
