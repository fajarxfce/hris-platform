# Development

Requires JDK 21, Docker, and Python 3. Gradle Wrapper pins the build tool. The dashboard uses Node.js 24 or newer with the committed npm lockfile.

## Backend checks

Run ./gradlew check :apps:server:bootJar :apps:worker:bootJar. Query generation launches a temporary PostgreSQL container, applies all migrations, generates jOOQ types, and removes only its own container. Tests use Testcontainers against PostgreSQL. Test infrastructure never connects to the development database.

If Docker requires a group available through sudo, run the build as the same user with that group, for example sudo -n -u fajar -g docker ./gradlew check --no-daemon. Do not change Docker socket permissions. A previously running Gradle daemon may retain its old groups, hence --no-daemon.

Run ./gradlew format to format handwritten Kotlin. Architecture checks run with check. No generated Java/Kotlin is committed.

## Local runtime

Copy .env.example to .env and generate distinct secrets. Run docker compose up --build -d after building both application jars. PostgreSQL and API bind localhost only. A migration container owns schema changes; runtime receives only hris_app credentials without superuser/BYPASSRLS. For private Garage/ClamAV services, follow [document service configuration](storage.md#private-document-services) and include `compose.documents.yml`. TLS deployment is configured separately.

For a dashboard on a private WireGuard interface, run from `apps/dashboard`:

```sh
HRIS_API_PROXY=http://127.0.0.1:8088 npm run dev -- --host 10.77.77.1 --port 5173 --strictPort
```

Use the server's actual VPN address. The dashboard and its same-origin API proxy
are then available on that interface; the database and direct API remain on
loopback. Local HTTP development uses `HRIS_SECURE_COOKIES=false` in the ignored
environment file. Deployed environments retain secure cookies and HTTPS.

For HRIS Mobile on this VPS, include `compose.wireguard.yml` to publish the API
on `10.77.77.1:8088` as well as loopback. The phone then needs WireGuard but no
ADB forwarding. With the document services enabled, start/update the API using:

```sh
docker compose -f compose.yml -f compose.documents.yml -f compose.wireguard.yml up -d api
```

Keep that overlay in subsequent deployments. For this local stack, the ignored
`.env` may set `COMPOSE_FILE=compose.yml:compose.documents.yml:compose.wireguard.yml`
so ordinary Compose commands use the same configuration. A different VPS must
set `host_ip` to its own private VPN address. Do not publish this HTTP development
port on a public interface. PostgreSQL, Garage and ClamAV remain private; staging
and production clients require the deployed HTTPS origin.

If WireGuard's firewall drops forwarding to Docker, allow only the original
private API destination and its established replies before those drop rules.
Docker translates the destination before the forwarding hook, so match conntrack's
original destination. The VPS stores these rules in `/etc/wireguard/wg0.nft`:

```nft
iifname "wg0" ip saddr 10.77.77.0/24 meta l4proto tcp ct original ip daddr 10.77.77.1 ct original proto-dst 8088 counter accept
oifname "wg0" ip daddr 10.77.77.0/24 meta l4proto tcp ct state established,related ct original ip daddr 10.77.77.1 ct original proto-dst 8088 counter accept
```

Keep existing isolation rules and validate a firewall transaction with `nft --check`
before applying it. Do not flush Docker or WireGuard rules. A health check from the
VPS alone does not validate the phone's route; check from the VPN client too.

Private HTTP origins do not expose `crypto.randomUUID()` in every browser. The
dashboard uses `crypto.getRandomValues()` for RFC 9562 version-4 operation IDs when
that API is unavailable. Both paths require browser-provided secure randomness;
there is no weak-random fallback.

Changing a secret in .env does not rotate an existing PostgreSQL role password. Rotate credentials explicitly, preserving the named volume. Never delete the database volume to apply an application upgrade.

## Validation records

Use ignored .work/ for local logs and transient notes. Record actual checks in docs/delivery.md with the relevant capability. Unimplemented roadmap entries remain marked planned.

## Identity encryption and MFA

Before starting the local stack, generate a dedicated key with `openssl rand -base64 32` and set `HRIS_IDENTITY_KEYS=v1:<generated-value>` in the ignored `.env`. `HRIS_IDENTITY_ACTIVE_KEY` selects the key used for new encryption. Store production values in the deployment secret manager and retain them with encrypted backups. The application has no fallback encryption key.

Privileged accounts must enroll an authenticator after password sign-in. `GET /api/v1/me` returns the required verification/setup state; business access stays blocked until MFA succeeds. Password login alone does not publish company directories or permissions to an account awaiting MFA. Enrollment exposes a short-lived `otpauth://` URI and confirmation returns ten recovery codes once. Store those codes outside the application.

For key rotation, add a new `key-id:base64` entry, separated by a comma, and select it as active while retaining old keys for decryption. Existing secrets are not rewritten automatically. Do not retire an old key before a separate credential migration and backup retention review. Missing keys fail closed.

MFA enrollment/verification rotate the session and CSRF tokens; fetch a fresh CSRF token afterward. After a lost confirmation response, sign in again and verify with the next authenticator code. Recent MFA allows generating replacement recovery codes; replacement invalidates the old codes and other sessions.

Business tests explicitly disable MFA/rate enforcement so they can isolate their module behavior. Dedicated security suites enable these controls with fictional accounts and a test-only key. Runtime defaults keep both controls enabled. jOOQ bind-value logging is disabled to avoid exposing credentials and personnel data.

## Background worker

The API and worker compose separate Spring application graphs. Changes to shared
module constructor bindings must be tested in both entry points. Run the affected
API tests and `./gradlew :apps:worker:test`; building both JARs alone cannot detect
a missing module configuration in the worker. Keep configuration ownership in the
module and import it from each application that composes that graph.

The worker is a separate non-web process with a two-thread executor, a two-job limit per company, and a four-job deployment limit. API and worker use distinct database logins. The worker login inherits the queue/Batch capability; it cannot bypass company RLS on business tables. Default leases last 60 seconds and renew every 15 seconds. A job is bounded to 30 minutes and at most eight attempts. Only transient unavailability is retried, with explicit capped delay.

For an existing local database, add `HRIS_WORKER_DATABASE_PASSWORD` to `.env`, recreate the database container to load the new environment/mount without deleting its volume, then run `docker compose exec -T database sh /docker-entrypoint-initdb.d/20-worker.sh`. This script creates the worker role if absent and explicitly sets its password to the configured value. Apply migrations before starting the API and worker. Fresh volumes perform role setup automatically.

Run the worker directly with `./gradlew :apps:worker:bootRun`, using `HRIS_DATABASE_URL`, `HRIS_WORKER_DATABASE_USER`, and `HRIS_WORKER_DATABASE_PASSWORD`. Do not use migration credentials for either application. `hris.worker.enabled=false` disables scheduling for controlled maintenance/test contexts; it is not a substitute for draining already-running workers. Shutdown cancels timers, interrupts owned work, and waits at most 40 seconds. Expired leases become available for recovery, and every business checkpoint rejects stale ownership.

Spring Batch metadata stores attempt execution/checkpoint diagnostics separately from the company job projection. The public API uses the latter and never exposes Batch tables or raw job parameters. Metadata retention/archival is a separate operational policy; no unbounded in-memory job registry is retained.

The dashboard browser suite runs in four CI shards, each retaining a 15-minute
step timeout. The previous two-shard allocation reached that limit as the suite
grew; one shard completed 204 scenarios in 15.1 minutes before the runner timed it
out. Sharding distributes the work without removing tests or extending a hung
scenario's timeout.

Browser suites build the dashboard and serve that production output with Vite
preview on their isolated test ports. This also applies to real API/worker
integration, preserving each fixture's same-origin proxy. The server startup budget
includes compilation; scenario and assertion timeouts remain unchanged. Run
`npm run test:e2e` from `apps/dashboard` for the complete browser suite, or supply
Playwright file/shard arguments. `npm run dev` remains the interactive development
server on the separately configured development port.
