# Native authentication

Android and iOS can sign in directly without creating a browser cookie session.
All credential traffic requires HTTPS outside explicitly isolated development.

`POST /api/v1/auth/native/login` takes `Idempotency-Key` and a JSON body with
`email`, `password`, and `deviceName` (1–100 characters). It returns `account`
(`id`, `email`, `displayName`, `mfaConfigured`) and `credentials` (`sessionId`,
`accessToken`, `refreshToken`, `accessExpiresAt`, `sessionExpiresAt`, `tokenType`).
The account summary is identity metadata, not authorization. Persist credentials
atomically in platform secure storage, then fetch `GET /api/v1/me` with bearer
authentication for current assurance, companies, and permissions.

Password verification shares web sign-in limits and credential-race checks.
Native issuance retains the account lock, live credential checks, finite device
limit, encrypted replay receipt, and audit transaction. Repeating the same key
and device name with valid credentials recovers the original issuance within
its two-minute replay window; it does not create another device session. A
changed device name conflicts. Rate limits still apply to retries. No cookie
is issued or used as an alternative to the supplied password.

## MFA

A successful password response may represent a pending MFA session. Such a
session can inspect its own `/me` assurance, enroll or verify MFA, inspect/end
native sessions, and refresh its tokens. It cannot read company directories or
perform business operations. `/me` redacts companies and permissions until
verification succeeds. Every subsequent business request rechecks live MFA
requirements, account credentials, company membership, and permissions.

- `POST /api/v1/auth/native/mfa/enrollment` accepts an idempotency key and returns
  the expiring authenticator setup. Reuse the key to recover the same setup.
- `POST /api/v1/auth/native/mfa/enrollment/confirm` takes its own idempotency key
  for token elevation and `{operationId, code}`, where `operationId` identifies
  the enrollment. It returns `credentials` and ten one-time `recoveryCodes`.
- `POST /api/v1/auth/native/mfa/verify` takes an idempotency key and
  `{code, recovery}`. It rotates and returns `credentials`.
- `POST /api/v1/auth/native/mfa/recovery-codes` rotates both credentials and
  recovery codes after current MFA/recent-authentication checks.

Enrollment and recovery-code rotation invalidate other credential versions.
Persist the returned credentials before using protected endpoints. Do not log
authenticator secrets, recovery codes, request bodies, or token responses.
Recovery codes require an explicit acknowledgement in the client.

MFA proof verification and native token elevation have separate ownership.
Confirmation is not a replayable recovery-code download: if the confirmation
response is lost, sign in again, verify with the next authenticator code, and
generate new recovery codes if needed. Do not retry a consumed proof indefinitely.

## Rotation and sign-out

`POST /api/v1/auth/native/refresh` takes `{refreshToken}` and `Idempotency-Key`.
Persist a refresh operation ID before sending it, reuse it with the original
token after a lost response, and replace the credentials atomically. One
application owner serializes rotation. A revoked session or expired replay
window requires fresh authentication, not an unbounded refresh loop.

`DELETE /api/v1/auth/native/sessions/{sessionId}` revokes a device session.
Local sign-out must also cancel account-owned work and clear its local data and
credentials. An offline local sign-out cannot claim remote revocation.

The existing cookie-only `/auth/native/exchange` remains available for verified
browser authentication. OIDC browser login does not yet provide a native app
callback handoff; it must not be approximated by trusting claims from a client.
