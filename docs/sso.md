# Single sign-on

The backend supports one configured OIDC provider per deployment using Spring Security authorization-code login, S256 PKCE, and RS256 ID tokens. It can use an appropriately configured Entra ID, Okta, or Keycloak client. Local password login remains available. No live provider tenant has been registered or validated by this repository.

## Provider registration

Create a confidential web client with the `openid` scope. Register the exact callback `https://<hris-host>/login/oauth2/code/company`. Use a tenant-specific issuer; a generic multi-tenant issuer is not an authorization boundary. Copy the issuer, authorization endpoint, token endpoint, and JWKS URI from the provider's published OIDC configuration.

Set these deployment secrets/settings, then restart the API:

| Variable | Value |
| --- | --- |
| HRIS_OIDC_ENABLED | true |
| HRIS_PUBLIC_URL | Public HTTPS origin, without a trailing slash or path |
| HRIS_OIDC_ISSUER | Exact provider issuer |
| HRIS_OIDC_CLIENT_ID | Registered confidential client ID |
| HRIS_OIDC_CLIENT_SECRET | Client secret from the secret manager |
| HRIS_OIDC_AUTHORIZATION_URI | Authorization endpoint |
| HRIS_OIDC_TOKEN_URI | Token endpoint |
| HRIS_OIDC_JWK_SET_URI | Provider JWKS endpoint |
| HRIS_OIDC_CLIENT_AUTH | client_secret_basic or client_secret_post, according to the provider |

Endpoints require HTTPS. `HRIS_OIDC_ALLOW_LOOPBACK_HTTP=true` permits HTTP only for explicit loopback development providers. It does not allow insecure remote endpoints. Discovery does not run at application startup; configured endpoints determine all outbound protocol requests. The callback comes from `HRIS_PUBLIC_URL`, independent of untrusted Host/forwarding headers.

## Account binding

Accounts are provisioned locally and linked using the provider's verified, stable `issuer + subject`. Matching email addresses and provider role claims never create links or grant HRIS permissions. Obtain the subject from the IdP administrator; a JWT decoded by a browser is not verification.

Only a platform administrator may use `PUT /api/v1/identity/accounts/{accountId}/oidc/{id}`. Company administration alone cannot add a credential to a global account. The body has `issuer`, `subject`, `active`, `expectedVersion`, and `reason`; commands also require `Idempotency-Key`. Creating a binding uses a new UUID and null version. Updates can activate/deactivate that same binding; account, issuer, and subject are immutable. At most one active subject per account/issuer is allowed. Revoked bindings cannot be reassigned to another account.

Credential changes require recent authentication and applicable MFA, increment the account security version, revoke older sessions, and commit their audit/outbox and receipt together. Replays do not repeat that change. Account-level receipts are isolated from company receipts and from other actors. `GET` on the same account collection supports bounded pagination and allows the account owner or a platform administrator to inspect bindings.

## Client flow and resource ownership

`GET /api/v1/auth/providers` returns the enabled provider label and authorization path. Open that path in a browser. A successful callback redirects to `/auth/complete`; the frontend then fetches a new CSRF token and `/api/v1/me`, including any required application MFA step. Failure redirects to `/login?sso=failed` without provider error details. These are frontend routes; dashboard integration is tracked separately in delivery status.

Only one authorization request is retained per browser session, valid for five minutes. Starting another sign-in replaces it. State, nonce, PKCE, signature, issuer, audience, and token time checks protect the callback. Session and CSRF tokens rotate after sign-in. Provider tokens and claims are removed before authentication reaches the session repository, including immediate session persistence. Unused provider access/refresh tokens are discarded. A claim that the IdP performed MFA does not become an HRIS MFA proof.

The callback admits eight concurrent exchanges per API instance without a waiting queue. Protocol HTTP uses a three-second connect timeout, eight-second read timeout, one-MiB token/JWK document limit, and a bounded executor. Spring owns that HTTP transport separately so failed initialization and normal shutdown release its resources. Only the configured provider occupies the decoder/client registry. Application credential revocation and company permissions are checked on subsequent API requests.
