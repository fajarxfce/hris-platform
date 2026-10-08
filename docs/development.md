# Development

Requires JDK 21, Docker, and Python 3. Gradle Wrapper pins the build tool. Frontend tooling is configured when the dashboard lands.

## Backend checks

Run ./gradlew check :apps:server:bootJar. Query generation launches a temporary PostgreSQL container, applies all migrations, generates jOOQ types, and removes only its own container. Tests use Testcontainers against PostgreSQL. Test infrastructure never connects to the development database.

If Docker requires a group available through sudo, run the build as the same user with that group, for example sudo -n -u fajar -g docker ./gradlew check --no-daemon. Do not change Docker socket permissions. A previously running Gradle daemon may retain its old groups, hence --no-daemon.

Run ./gradlew format to format handwritten Kotlin. Architecture checks run with check. No generated Java/Kotlin is committed.

## Local runtime

Copy .env.example to .env and generate distinct secrets. Run docker compose up --build -d after building the server jar. PostgreSQL and API bind localhost only. A migration container owns schema changes; runtime receives only hris_app credentials without superuser/BYPASSRLS. Integration services and TLS deployment are added in their respective slices.

Changing a secret in .env does not rotate an existing PostgreSQL role password. Rotate credentials explicitly, preserving the named volume. Never delete the database volume to apply an application upgrade.

## Validation records

Use ignored .work/ for local logs and transient notes. Record actual checks in docs/delivery.md with the relevant capability. Unimplemented roadmap entries remain marked planned.

## Identity encryption and MFA

Before starting the local stack, generate a dedicated key with `openssl rand -base64 32` and set `HRIS_IDENTITY_KEYS=v1:<generated-value>` in the ignored `.env`. `HRIS_IDENTITY_ACTIVE_KEY` selects the key used for new encryption. Store production values in the deployment secret manager and retain them with encrypted backups. The application has no fallback encryption key.

Privileged accounts must enroll an authenticator after password sign-in. `GET /api/v1/me` returns the required verification/setup state; business access stays blocked until MFA succeeds. Password login alone does not publish company directories or permissions to an account awaiting MFA. Enrollment exposes a short-lived `otpauth://` URI and confirmation returns ten recovery codes once. Store those codes outside the application.

For key rotation, add a new `key-id:base64` entry, separated by a comma, and select it as active while retaining old keys for decryption. Existing secrets are not rewritten automatically. Do not retire an old key before a separate credential migration and backup retention review. Missing keys fail closed.

MFA enrollment/verification rotate the session and CSRF tokens; fetch a fresh CSRF token afterward. After a lost confirmation response, sign in again and verify with the next authenticator code. Recent MFA allows generating replacement recovery codes; replacement invalidates the old codes and other sessions.

Business tests explicitly disable MFA/rate enforcement so they can isolate their module behavior. Dedicated security suites enable these controls with fictional accounts and a test-only key. Runtime defaults keep both controls enabled. jOOQ bind-value logging is disabled to avoid exposing credentials and personnel data.
