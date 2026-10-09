# Identity

Own accounts, sessions, invitations, OIDC links, MFA, memberships, roles, and permission assignments.

Use verified issuer/subject for OIDC. Local invitations and recovery tokens are single-use. Web cookies use HttpOnly/Secure and CSRF; mobile refresh rotation detects reuse. Privileged accounts require MFA and sensitive actions recent authentication.

Group/company administration, HR, manager, finance, auditor, and employee have explicit action/resource scopes. Disabling account or membership revokes that access without granting payroll access through administrative role names.

Screens: users, roles, company assignments, SSO configuration, active sessions.

Acceptance: cross-company/resource denial, refresh replay/revocation, invitation reuse, stale memberships, CSRF, and secret-free diagnostics.

## Implementation status

Password sign-in, administrator bootstrap, server sessions in PostgreSQL, CSRF/session ID rotation, current-account reads, and live company authorization are implemented. Company member directories and versioned permission/activation updates are also implemented. Sensitive payroll/payment grants require platform administration and cannot be self-granted; company membership changes preserve the last administrator and are audited. Authenticator MFA, recovery codes, credential-version revocation, and recent-authentication checks are implemented. Configured OIDC sign-in and versioned global identity bindings are implemented. Configurable company role templates, versioned assignment snapshots, and global account access administration are implemented. Local invitations, one-use password recovery, and durable SMTP delivery are implemented.

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

Native MFA verification and recovery-code regeneration return new native credentials. They do not elevate a browser cookie. Elevation rejects an obsolete session version after a competing refresh. A proof is consumed before transport credential elevation; a lost response or failure in that handoff requires another authenticator code, recovery code, or a fresh sign-in. Native clients must not automatically repeat one-use MFA actions. Initial MFA enrollment uses the shared sign-in handshake. Session listings and revocation are always restricted to the current account. Account, company-list, and native-session metadata reads revalidate credentials under a shared account guard. Own-session revocation retains its cleanup semantics if credentials change after request admission; it never reveals another account's session or creates access.

## OIDC

Spring Security handles authorization code, PKCE, ID-token validation, state, and nonce. Account resolution uses only an explicitly linked issuer/subject. Global credential administration is distinct from company membership administration. Provider role/email/MFA claims do not bypass local authorization. See [SSO configuration](../sso.md) for registration, revocation, request/resource limits, and the client flow.

Binding changes lock the provider subject before actor/target accounts in stable ID order. Current platform authority, credentials, and recent authentication are checked after those guards and before mutation or receipt replay. Changing an operator's own binding invalidates the origin session; fresh authentication can retrieve its existing receipt. Listing another account's bindings requires current platform administration; owners may inspect their own bindings without that grant.

## Company role templates

`GET/PUT /companies/{companyId}/role-templates` lists or saves named, versioned permission templates. Codes are stable, definitions can be archived, and changes retain immutable revisions. There are at most 128 definitions per company, including archived entries. `GET .../role-templates/permissions` returns the server permission catalog; defining a template does not assign its permissions to anyone.

Membership writes accept direct `permissions` and up to eight distinct `roleTemplates` selections, each with `id` and `version`. The use case validates current active templates in the same company and records their full permission snapshots with the resulting membership version. Existing members retain their granted permissions when a template changes or is archived. Reapplying a changed template requires an explicit membership update and expected membership version. Refresh the template before retrying a stale selection.

`GET /companies/{companyId}/members/{accountId}` returns effective membership access and the direct/template sources used for that version. Older grants without a recorded template application are represented as direct permissions. Template application history is immutable; access, source snapshots, receipts, and audit/outbox commit together.

Sensitive-grant checks run on the combined permissions. A company administrator cannot use a role template to bypass the platform-administrator requirement for payroll/payment access or grant those permissions to themselves. Reactivating a membership counts as granting its permissions again. Membership and template changes retain recent-MFA checks when enforcement is enabled. A template name never substitutes for a server permission check.

Role/member commands and reads revalidate active company membership, credentials, and the original/live permission intersection under guards. Actor and target accounts are guarded in stable ID order. Company and platform grants remain separate in the request context: newly added global administration cannot authorize a sensitive grant that was already waiting, and removed global administration stops it. Successful receipt replay still requires current company administration and applicable recent MFA, while preserving the original outcome instead of reapplying mutable template or target-state checks. New membership grants require an active target account. Member/grant reads hold a shared membership guard so effective access and its recorded source version cannot diverge during an update.

## Global account administration

`GET /identity/accounts` provides a bounded global account directory for platform administrators. Queries acquire only administrative fields; password hashes and authenticator secrets are not selected or returned. Company membership administrators do not gain access to this directory. The initiating account is guarded and revalidated; each page selects account versions and platform grants in one SQL snapshot. A concurrent account update cannot pair an old observed version with new grants.

`PUT /identity/accounts/{accountId}/access` changes account activation and platform permissions with an expected account version, idempotency key, and reason. Every successful access change advances the credential version, invalidating existing cookie and native sessions. Reactivation requires a new sign-in. Company memberships remain recorded and still need their own grants.

Access changes serialize global administration and lock/revalidate the initiating account before changing the target. Recent authentication/MFA is checked again after pending account guards, including when returning an original receipt. Two competing administrators cannot both disable one another. Administrators cannot remove their own administration. Pending invitations cannot be activated through this endpoint; explicitly disabling one cancels its pending status and advances the credential version. Invitation acceptance uses the separate, one-use workflow below.

Account IDs and login email addresses remain immutable. Credential/version counters cannot move backwards. Account updates, permission changes, receipts, and audit/outbox share a transaction. The endpoint retains recent authentication and MFA requirements; it never accepts passwords or changes authenticator enrollment.

## Invitations and password recovery

`POST /api/v1/identity/invitations` requires a global administrator, recent authentication/MFA, an Idempotency-Key, and `id`, `email`, `displayName`, and `reason`. It creates an inactive account without membership grants and queues an encrypted invitation. Explicit resends include `expectedVersion`, supersede prior links, and cannot convert an active or existing local-password account into an invitation. Invitations expire after 72 hours. Disabled SMTP rejects new administrative invitation creation with 503 before any mutation. An existing receipt remains retrievable after current authority and recent authentication are revalidated; replay does not enqueue another invitation or require SMTP to be enabled again.

Public `POST /api/v1/auth/password-recovery` accepts `email` and returns an empty 202 for missing, inactive, OIDC-only, throttled, or mail-disabled accounts. Eligible accounts receive one usable recovery link for 30 minutes. Repeated requests do not invalidate a pending link or enqueue duplicate email. Limits are three attempts per account and thirty per observed origin in a 15-minute window, stored independently of login limits. Technical persistence failures remain errors rather than falsely claiming a completed mutation; no account details appear in responses.

`POST /api/v1/auth/invitations/accept` and `POST /api/v1/auth/password-recovery/confirm` accept `token` and a new `password` of 12–128 characters. These exact public routes retain CSRF protection. Use the public cookie/CSRF transport without an Authorization header. Tokens are bound to account, purpose, expiry, and current security version and serialized under the account lock. Expiry is checked again after password hashing. Acceptance or recovery advances credential versions, invalidates existing browser/native credentials, and preserves authenticator enrollment and recovery codes. It returns 204 without signing in automatically. After a lost confirmation response, try signing in with the new password; do not replay a one-use confirmation automatically.

Account/password writes, challenge consumption, queue supersession, and audit/outbox share the transaction. Invitation receipts are included in that transaction. Plaintext links, passwords, provider errors, and encryption keys are not returned by administrative APIs or logged. [SMTP configuration and delivery](../mail.md) describes limits and at-least-once delivery. No production provider or browser acceptance screen has been validated yet.
