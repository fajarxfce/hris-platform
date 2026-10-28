# Native Firebase Authentication

The native exchange accepts Firebase email/password and Google sign-in from one
configured project. It returns the same HRIS native credentials as password login,
including pending application MFA. Firebase claims do not grant company access,
roles or HRIS MFA assurance.

## Configuration and account approval

Set `HRIS_FIREBASE_AUTH_ENABLED=true` and `HRIS_FIREBASE_PROJECT_ID=cmp-fajar` on the
API process. The Compose API forwards these settings; both are optional and the
exchange is disabled by default. Public signature verification needs neither a
Firebase API key nor service-account credentials. Worker FCM delivery has separate
credentials and configuration.

Enable Email/password and Google in Firebase Authentication. Android Google login
also needs the registered package/signing certificate and its public web OAuth
client ID. Client SDK setup and native device acceptance belong to HRIS Mobile.

A platform administrator must approve the Firebase UID through the existing
[SSO binding workflow](sso.md#account-binding). Use issuer
`https://securetoken.google.com/cmp-fajar` and the exact UID from the Firebase
project's user administration. Firebase can be enabled independently of browser
OIDC. Matching email addresses never create an account binding, even when Firebase
marks the email verified. Enabling either provider does not provision HRIS users.
Tenant-scoped Firebase identities are rejected: this deployment uses the project's
single UID namespace.

## Transport

`GET /api/v1/auth/native/firebase` is public and returns `enabled`, `projectId`
(null when disabled), and supported `providers` (`password`, `google.com`).

`POST /api/v1/auth/native/firebase/login` accepts JSON `idToken` and `deviceName`,
plus a required UUID `Idempotency-Key`. Neither a browser cookie nor an existing
HRIS bearer credential replaces the Firebase proof. Responses use `Cache-Control:
no-store`; login sets no browser cookie. Success returns `account` and
`credentials`, following the [native session contract](mobile-api.md).

The API verifies RS256 signatures against Google's fixed HTTPS Secure Token X.509
endpoint. It requires the configured issuer and audience, a nonblank bounded UID,
an allowed sign-in provider, valid issue/expiry times, and recent `auth_time`.
Fresh authentication uses the HRIS recent-authentication window (ten minutes by
default, capped at one hour). Refreshing a Firebase ID token cannot renew the
original authentication time. The client must obtain fresh interactive proof when
that window expires.

Only then does the API resolve the active approved binding and issue the HRIS
session with the resolved account's current security version. HRIS MFA enrollment
or verification remains necessary before business access. The client stores HRIS
credentials for normal API calls; a Firebase ID token is not an HRIS bearer token.

An unchanged operation and device name recover the original native issuance after
a lost response, including with fresh proof for the same approved account. Device
changes conflict with that operation. Concurrent retries create one session and
one issuance audit. Revoking an approved binding advances the account's security
version, invalidates old HRIS credentials and prevents a new exchange through it.

## Bounds and lifecycle

Before signature acquisition, requests consume an origin budget of 100 attempts
per 15 minutes. Verified project/UID identities also consume a budget of ten
attempts per 15 minutes, including receipt retries. A denied attempt remains
accounted for; accounting failure cannot issue a session.

ID tokens are limited to 8 KiB, with canonical base64url segments, restricted
headers and bounded claim types. Provider key responses are limited to 64 KiB,
one to ten RSA certificates and a five-second owned request. One application-owned
cache honors cache age and certificate expiry; key fetches are serialized with a
five-second lock wait. Failed refreshes back off for 30 seconds. Unknown key IDs
cannot choose URLs or force a refresh of fresh cached keys. Redirects are disabled.
Interruption cancels pending acquisition and propagates; shutdown closes the
application-owned HTTP client. Disabled configuration acquires no key transport.

Malformed or rejected proof returns `firebase_sign_in_failed`; unavailable key
verification returns `firebase_sign_in_unavailable`. Attempt exhaustion returns
`sign_in_rate_limited`. Approved-binding failures retain the existing OIDC error
contract. Diagnostics and value string representations exclude raw credentials.

Signature verification does not query Firebase user disablement or refresh-token
revocation. Already issued Firebase proof remains subject to its short accepted
authentication window; HRIS account/binding revocation is the application access
control. Real Firebase login, native UI/provider cancellation and physical-device
acceptance must be recorded separately from local signed fixtures.

## Validation

Domain tests cover project/provider/tenant/time policy, rate limits, late expiry
and interruption. Data tests exercise real RSA verification, malformed JWTs,
bounded HTTP streaming, concurrent key caching, retry backoff, timeout and
interruption. API/PostgreSQL tests exercise both providers, explicit binding,
MFA, revoked credentials, cookie isolation and concurrent receipt replay. The
shared DI graph also requires the complete worker tests. See the delivery record
for executed checks and remaining acceptance.
