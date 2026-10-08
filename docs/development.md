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
