# Identity

Own accounts, sessions, invitations, OIDC links, MFA, memberships, roles, and permission assignments.

Use verified issuer/subject for OIDC. Local invitations and recovery tokens are single-use. Web cookies use HttpOnly/Secure and CSRF; mobile refresh rotation detects reuse. Privileged accounts require MFA and sensitive actions recent authentication.

Group/company administration, HR, manager, finance, auditor, and employee have explicit action/resource scopes. Disabling account or membership revokes that access without granting payroll access through administrative role names.

Screens: users, roles, company assignments, SSO configuration, active sessions.

Acceptance: cross-company/resource denial, refresh replay/revocation, invitation reuse, stale memberships, CSRF, and secret-free diagnostics.

## Implementation status

Password sign-in, administrator bootstrap, server sessions in PostgreSQL, CSRF/session ID rotation, current-account reads, and live company authorization are implemented. Company member directories and versioned permission/activation updates are also implemented. Sensitive payroll/payment grants require platform administration and cannot be self-granted; company membership changes preserve the last administrator and are audited. MFA, OIDC, invitations/recovery, native tokens, and configurable role templates remain planned.

Endpoints: GET /api/v1/auth/csrf, POST /api/v1/auth/login, POST /api/v1/auth/logout, GET /api/v1/me, GET /api/v1/companies/{companyId}/me/access. Login uses JSON email/password and a current X-CSRF-TOKEN header. Fetch a new CSRF token after successful login. Cookies are Secure by default; set HRIS_SECURE_COOKIES=false only for local HTTP development.

Password attempts share PostgreSQL budgets across application instances: ten attempts per normalized account and one hundred per observed origin in each 15-minute window. Failed and capacity-rejected attempts retain their budget consumption; expiration reopens the window. Responses remain indistinguishable for missing/incorrect accounts. HTTP 429 includes Retry-After. The application ignores client-supplied forwarded-address claims unless a trusted proxy is configured at deployment. Rate buckets store hashes and expired buckets are removed in batches of at most 100 during later attempts after a one-day retention period.

Argon2 work is limited to eight concurrent operations, without a waiting queue. Capacity returns 503; cancellation and failures release their permits. The request origin is not retained in the persisted security context. These limits are enabled by default; independent business integration suites disable rate limits explicitly, while dedicated tests exercise the default enforcement.
