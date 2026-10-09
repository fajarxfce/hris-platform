# OpenAPI contract

The server generates OpenAPI 3.1 from its registered HTTP controllers and transport DTOs. Shared metadata describes filter-based sign-in/logout, cookie/CSRF and native bearer authentication, company scope, idempotency, localizable problems, sync collection selection, and binary transfer behavior. Internal `Actor`, servlet, and CSRF framework types are not client inputs.

## Generate and validate

With JDK 21 and Docker available, run:

```sh
./gradlew :apps:server:test --tests dev.fajar.hris.ApiContractHttpTest --no-daemon
```

The integration test starts an isolated PostgreSQL instance and the real server. It verifies controller coverage, unique operation IDs, required path parameters, local references, security alternatives, decimal/month/null types, and download/upload media types. Swagger Parser validates the generated description. The output is `apps/server/build/generated/openapi/hris-v1.json`; generated output stays outside Git.

The `Verify` workflow uploads this file as the `hris-openapi-v1` artifact. Use the artifact matching the backend commit when generating or reviewing client adapters. Domain and data modules have no OpenAPI dependency.

## Runtime access

Documentation endpoints are disabled by default. Set `HRIS_API_DOCS_ENABLED=true` when needed, then access `GET /api/v1/openapi` with a current authenticated session. The normal MFA and credential checks still apply. Enabling documentation does not enable Swagger UI or change business endpoint authorization. The local Compose configuration forwards this setting to the API process.

The description uses a relative server URL so environment addresses and session data do not enter the artifact. Security schemes describe alternatives: native bearer requests use the access token; browser mutations use the session cookie and CSRF token. Public browser sign-in/recovery still needs its initialized CSRF session. Native refresh carries its refresh credential in the request body and a stable operation ID.

Documentation has one canonical English locale and a one-entry cache. Changing `Accept-Language` does not build additional localized descriptions. Application error translation remains the client's responsibility.

## Client compatibility

Translate `ApiProblem.code`, field codes, and safe interpolation parameters in the client. New error codes and response properties are additive; preserve an unknown-code fallback. Nullable fields remain nullable in generated clients. Keep money as decimal strings and month values as `YYYY-MM`.

An idempotency header documents retry identity, not permission to retry indefinitely. Shared error responses describe the possible transport/business failure categories; feature policy determines the specific code. Binary transfers can return an empty admission/range response or stop after headers, so incomplete files require explicit recovery.

OpenAPI does not define offline merge policy or turn ordinary list cursors into sync cursors. Follow the [mobile API](mobile-api.md) and [synchronization protocol](synchronization.md) for partition ownership, original payload replay, finite retries, cursor reset, and revoked access.
