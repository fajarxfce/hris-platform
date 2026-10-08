# Identity

Own accounts, sessions, invitations, OIDC links, MFA, memberships, roles, and permission assignments.

Use verified issuer/subject for OIDC. Local invitations and recovery tokens are single-use. Web cookies use HttpOnly/Secure and CSRF; mobile refresh rotation detects reuse. Privileged accounts require MFA and sensitive actions recent authentication.

Group/company administration, HR, manager, finance, auditor, and employee have explicit action/resource scopes. Disabling account or membership revokes that access without granting payroll access through administrative role names.

Screens: users, roles, company assignments, SSO configuration, active sessions.

Acceptance: cross-company/resource denial, refresh replay/revocation, invitation reuse, stale memberships, CSRF, and secret-free diagnostics.

## Implementation status

Password sign-in, administrator bootstrap, server sessions in PostgreSQL, CSRF/session ID rotation, current-account reads, and live company authorization are implemented. Company member directories and versioned permission/activation updates are also implemented. Sensitive payroll/payment grants require platform administration and cannot be self-granted; company membership changes preserve the last administrator and are audited. Authenticator MFA, recovery codes, credential-version revocation, and recent-authentication checks are implemented. Configured OIDC sign-in and versioned global identity bindings are implemented. Configurable company role templates and versioned assignment snapshots are implemented. Invitations/password recovery remain planned.

Endpoints: GET /api/v1/auth/csrf, POST /api/v1/auth/login, POST /api/v1/auth/logout, GET /api/v1/me, GET /api/v1/companies/{companyId}/me/access. Login uses JSON email/password and a current X-CSRF-TOKEN header. Fetch a new CSRF token after successful login. Cookies are Secure by default; set HRIS_SECURE_COOKIES=false only for local HTTP development.

Password attempts share PostgreSQL budgets across application instances: ten attempts per normalized account and one hundred per observed origin in each 15-minute window. Failed and capacity-rejected attempts retain their budget consumption; expiration reopens the window. Responses remain indistinguishable for missing/incorrect accounts. HTTP 429 includes Retry-After. The application ignores client-supplied forwarded-address claims unless a trusted proxy is configured at deployment. Rate buckets store hashes and expired buckets are removed in batches of at most 100 during later attempts after a one-day retention period.

Argon2 work is limited to eight concurrent operations, without a waiting queue. Capacity returns 503; cancellation and failures release their permits. The request origin is not retained in the persisted security context. These limits are enabled by default; independent business integration suites disable rate limits explicitly, while dedicated tests exercise the default enforcement.

MFA uses RFC 6238 SHA-1 authenticator codes with a 30-second period and one adjacent period of tolerance. Each counter and recovery code can be consumed once, serialized under the account lock. Setup/verification attempts share a five-attempt, five-minute account budget. Codes generated for recovery are random, stored only as hashes, and replaced as a set. Credentials and recovery changes commit with audit/outbox; failed technical mutations roll back together.

Privileged and voluntarily enrolled accounts require a verified factor. The API entry interceptor resolves current access once and enforces this independently of controller parameters. Only self-service authentication metadata/setup/challenges allow pending MFA. Pending accounts do not receive company directories or permissions. A factor proof lasts at most 12 hours; membership changes and recovery-code replacement require a proof less than ten minutes old. Replacing recovery codes increments the security version, invalidating other sessions and old codes.

Authenticator secrets use AES-256-GCM with random nonces, an explicit key version, and account-bound associated data. The keyring comes from deployment secrets. Enrollment and proof responses are not cacheable. Missing encryption configuration never disables the MFA requirement. See [development](../development.md) for enrollment, key retention, lost-response recovery, and local setup.


## Native sessions

The first mobile sign-in uses the same password/OIDC and MFA handshake as the web. The client keeps the temporary session cookie and CSRF token until `POST /auth/native/exchange` returns native credentials. Exchange requires recent authentication and applicable MFA. Device names are display labels, not device attestation. Cookie and native transports have separate Spring Security chains; native requests do not create or load a web session.

Access tokens last ten minutes. Refresh tokens belong to an absolute thirty-day session and rotate on each successful refresh. Both are random 256-bit values; only SHA-256 hashes are indexed in PostgreSQL. The response needed for retry is encrypted using the configured identity keyring, bound to account, session, and operation ID, and replayable for two minutes. An identical retry returns the original successor only while it is still current. A different operation or a stale consumed token revokes the session, and that revocation commits before HTTP 401 is returned. Keep one refresh operation in flight per session and reuse its operation ID after a lost response.

Account locks serialize session creation, rotation, MFA updates, and revocation. Limits are ten active sessions and one hundred issued, unexpired sessions per account, a ten-second minimum rotation interval, and 5,000 rotations per session; exceeding the rotation ceiling requires signing in again. Expired sessions are deleted in bounded batches during later exchanges, with consumed hashes removed by foreign-key cascade. Expired replay ciphertext is cleared during subsequent rotations. Periodic cleanup by the worker remains planned; quiet accounts retain expired encrypted rows until that cleanup or a later exchange.

Native MFA verification and recovery-code regeneration return new native credentials. They do not elevate a browser cookie. Elevation rejects an obsolete session version after a competing refresh. A proof is consumed before transport credential elevation; a lost response or failure in that handoff requires another authenticator code, recovery code, or a fresh sign-in. Native clients must not automatically repeat one-use MFA actions. Initial MFA enrollment uses the shared sign-in handshake. Session listings and revocation are always restricted to the current account.

## OIDC

Spring Security handles authorization code, PKCE, ID-token validation, state, and nonce. Account resolution uses only an explicitly linked issuer/subject. Global credential administration is distinct from company membership administration. Provider role/email/MFA claims do not bypass local authorization. See [SSO configuration](../sso.md) for registration, revocation, request/resource limits, and the client flow.

## Company role templates

`GET/PUT /companies/{companyId}/role-templates` lists or saves named, versioned permission templates. Codes are stable, definitions can be archived, and changes retain immutable revisions. There are at most 128 definitions per company, including archived entries. `GET .../role-templates/permissions` returns the server permission catalog; defining a template does not assign its permissions to anyone.

Membership writes accept direct `permissions` and up to eight distinct `roleTemplates` selections, each with `id` and `version`. The use case validates current active templates in the same company and records their full permission snapshots with the resulting membership version. Existing members retain their granted permissions when a template changes or is archived. Reapplying a changed template requires an explicit membership update and expected membership version. Refresh the template before retrying a stale selection.

`GET /companies/{companyId}/members/{accountId}` returns effective membership access and the direct/template sources used for that version. Older grants without a recorded template application are represented as direct permissions. Template application history is immutable; access, source snapshots, receipts, and audit/outbox commit together.

Sensitive-grant checks run on the combined permissions. A company administrator cannot use a role template to bypass the platform-administrator requirement for payroll/payment access or grant those permissions to themselves. Reactivating a membership counts as granting its permissions again. Membership and template changes retain recent-MFA checks when enforcement is enabled. A template name never substitutes for a server permission check.
