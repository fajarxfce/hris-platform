"""Own isolated API/PostgreSQL processes, optionally with the real worker."""

import base64
import json
import os
from pathlib import Path
import re
import secrets
import shlex
import signal
import subprocess
import sys
import time
from urllib.error import URLError
from urllib.request import urlopen
from dashboard_mail_fixture import DashboardMailFixture
from dashboard_storage_fixture import DashboardStorageFixture
from dashboard_fcm_fixture import worker_fcm_environment


root = Path(__file__).resolve().parents[1]
docker = shlex.split(os.environ.get("HRIS_TEST_DOCKER_COMMAND", "docker"))
jar = root / "apps/server/build/libs/apps-server.jar"
worker_jar = root / "apps/worker/build/libs/apps-worker.jar"
with_worker = os.environ.get("HRIS_TEST_WITH_WORKER") == "1"
with_storage = os.environ.get("HRIS_TEST_WITH_STORAGE") == "1"
port = 18081 if with_worker else 18080
container = None
api = None
worker = None
mail = None
storage = None
logs = root / (".work/worker-integration" if with_worker else ".work/dashboard-integration")
environment_file = logs / "postgres.env"
resource_file = logs / "database.json"


def command(arguments, *, input=None):
    return subprocess.run(arguments, input=input, text=True, capture_output=True, check=True, timeout=90).stdout.strip()


def stop(_signal, _frame):
    raise KeyboardInterrupt


def main():
    global container, api, worker, mail, storage
    if not jar.is_file():
        raise RuntimeError("Build the API with ./gradlew :apps:server:bootJar first")
    if with_worker and not worker_jar.is_file():
        raise RuntimeError("Build the worker with ./gradlew :apps:worker:bootJar first")
    os.umask(0o077)
    fcm_environment = worker_fcm_environment(os.environ)
    logs.mkdir(parents=True, exist_ok=True)
    for event in (signal.SIGINT, signal.SIGTERM):
        signal.signal(event, stop)
    if with_worker:
        mail = DashboardMailFixture(logs / "mail.json")
    if with_storage:
        storage = DashboardStorageFixture(root, logs, docker,
                                          faults=os.environ.get("HRIS_TEST_STORAGE_FAULTS") == "1")
    database_password = secrets.token_urlsafe(24)
    environment_file.write_text(f"POSTGRES_PASSWORD={database_password}\n")
    initialization = logs / "runtime-role.sql"
    roles = (root / "apps/server/src/test/resources/runtime-role.sql").read_text()
    if with_worker:
        roles += (root / "apps/worker/src/test/resources/worker-role.sql").read_text()
    initialization.write_text(roles)
    initialization.chmod(0o644)
    created = command(docker + [
        "run", "-d", "--label", "dev.fajar.hris.fixture=dashboard",
        "--env-file", str(environment_file), "-e", "POSTGRES_USER=fixture_migrator", "-e", "POSTGRES_DB=hris_browser",
        "-p", "127.0.0.1::5432", "--mount",
        f"type=bind,src={initialization},dst=/docker-entrypoint-initdb.d/10-runtime.sql,readonly",
        "postgres:18.6-alpine",
    ])
    if not re.fullmatch(r"[0-9a-f]{12,64}", created):
        raise RuntimeError("Unexpected fixture container identifier")
    container = created
    resource_file.write_text(json.dumps({"container": container, "database": "hris_browser"}))
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        ready = subprocess.run(docker + ["exec", container, "pg_isready", "-h", "127.0.0.1", "-U", "fixture_migrator", "-d", "hris_browser"], capture_output=True, timeout=5)
        if ready.returncode == 0:
            break
        if command(docker + ["inspect", "--format", "{{.State.Running}}", container]) != "true":
            raise RuntimeError("Fixture database exited; inspect .work/dashboard-integration/database.log")
        time.sleep(0.5)
    else:
        raise RuntimeError("Fixture database startup timed out")
    binding = command(docker + ["port", container, "5432/tcp"])
    if not binding.startswith("127.0.0.1:") or not binding.removeprefix("127.0.0.1:").isdigit():
        raise RuntimeError("Unexpected fixture database binding")
    environment = os.environ | {
        "HRIS_DATABASE_URL": f"jdbc:postgresql://{binding}/hris_browser",
        "HRIS_DATABASE_USER": "hris_test_runtime",
        "HRIS_DATABASE_PASSWORD": "test-runtime-only",
        "HRIS_MIGRATIONS_ENABLED": "true",
        "HRIS_MIGRATION_USER": "fixture_migrator",
        "HRIS_MIGRATION_PASSWORD": database_password,
        "HRIS_BOOTSTRAP_EMAIL": "browser-admin@example.invalid",
        "HRIS_BOOTSTRAP_PASSWORD": "Browser-fixture-password-123!",
        "HRIS_IDENTITY_KEYS": f"v1:{base64.b64encode(secrets.token_bytes(32)).decode()}",
        "HRIS_IDENTITY_ACTIVE_KEY": "v1",
        "HRIS_SYNC_KEYS": f"v1:{base64.b64encode(secrets.token_bytes(32)).decode()}",
        "HRIS_SYNC_ACTIVE_KEY": "v1",
        "HRIS_SECURE_COOKIES": "false",  # Loopback HTTP fixture only.
        # Binding administration exercises configured values without contacting an IdP.
        # OIDC protocol acceptance uses the separate signed loopback provider in API tests.
        "HRIS_OIDC_ENABLED": "true" if os.environ.get("HRIS_TEST_OIDC_BINDINGS") == "1" else "false",
        "HRIS_OIDC_ISSUER": "https://oidc.example.test",
        "HRIS_OIDC_CLIENT_ID": "owned-browser-fixture",
        "HRIS_OIDC_CLIENT_SECRET": "owned-fixture-only",
        "HRIS_OIDC_AUTHORIZATION_URI": "https://oidc.example.test/authorize",
        "HRIS_OIDC_TOKEN_URI": "https://oidc.example.test/token",
        "HRIS_OIDC_JWK_SET_URI": "https://oidc.example.test/jwks",
        "HRIS_OIDC_ALLOW_LOOPBACK_HTTP": "true",
        "HRIS_MAIL_ENABLED": "true" if mail else "false",
        "HRIS_MAIL_HOST": "127.0.0.1",
        "HRIS_MAIL_PORT": str(mail.port if mail else 1),
        "HRIS_MAIL_FROM": "no-reply@example.test",
        "HRIS_MAIL_USERNAME": "",
        "HRIS_MAIL_PASSWORD": "",
        "HRIS_MAIL_TRANSPORT": "PLAINTEXT",
        "HRIS_MAIL_ALLOW_LOOPBACK_PLAINTEXT": "true",
        "HRIS_PUBLIC_URL": "http://127.0.0.1:4175" if with_worker else "http://127.0.0.1:4174",
        "HRIS_AUTH_LINKS_ALLOW_LOOPBACK_HTTP": "true",
        "HRIS_STORAGE_ENABLED": "false",
        "HRIS_DOCUMENT_SCANNER_ENABLED": "false",
        "HRIS_FCM_ENABLED": "false",
        "SERVER_ADDRESS": "127.0.0.1",
        "SERVER_PORT": str(port),
    }
    if storage is not None:
        environment.update(storage.environment)
    with (logs / "backend.log").open("w") as output:
        api = subprocess.Popen(["java", "-jar", str(jar)], env=environment, stdout=output, stderr=subprocess.STDOUT)
        # Wait for the bootstrap transaction before provisioning separate scenario accounts.
        # All setup is limited to the container owned by this process.
        sql = docker + ["exec", "-i", container, "psql", "-U", "fixture_migrator", "-d", "hris_browser", "-v", "ON_ERROR_STOP=1"]
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            if api.poll() is not None:
                raise RuntimeError("Fixture API exited during account setup")
            ready = subprocess.run(sql + ["-Atqc", "SELECT count(*) FROM accounts a JOIN platform_permissions p ON p.account_id=a.id WHERE a.email='browser-admin@example.invalid' AND p.permission='identity.manage'"], text=True, capture_output=True, timeout=5)
            if ready.returncode == 0 and ready.stdout.strip() == "1":
                break
            time.sleep(0.5)
        else:
            raise RuntimeError("Fixture account setup timed out")
        command(sql, input=(root / "apps/dashboard/integration/fixtures/accounts.sql").read_text())
        command(sql, input=(root / "apps/dashboard/integration/fixtures/retirement-archive.sql").read_text())
        if with_worker:
            command(sql, input=(root / "apps/dashboard/integration/fixtures/leave-carry-expiry.sql").read_text())
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            if api.poll() is not None:
                raise RuntimeError("Fixture API exited during readiness check")
            try:
                with urlopen(f"http://127.0.0.1:{port}/api/v1/auth/csrf", timeout=2) as response:
                    if response.status == 200:
                        response.read(4096)
                        break
            except (URLError, TimeoutError):
                pass
            time.sleep(0.5)
        else:
            raise RuntimeError("Fixture API readiness timed out")
        if with_worker:
            worker_environment = {
                key: value for key, value in environment.items()
                if not key.startswith(("HRIS_MIGRATION", "HRIS_BOOTSTRAP", "HRIS_SYNC_"))
            } | {
                "HRIS_WORKER_DATABASE_USER": "hris_worker_test",
                "HRIS_WORKER_DATABASE_PASSWORD": "worker-fixture-only",
            } | fcm_environment
            with (logs / "worker.log").open("w") as worker_output:
                worker = subprocess.Popen(
                    ["java", "-jar", str(worker_jar)], env=worker_environment,
                    stdout=worker_output, stderr=subprocess.STDOUT,
                )
        print(f"Browser API fixture ready; diagnostics: {logs.relative_to(root)}/backend.log", flush=True)
        deadline = time.monotonic() + (900 if fcm_environment else 600)
        while time.monotonic() < deadline:
            if api.poll() is not None:
                raise RuntimeError("Fixture API exited; inspect its local diagnostics")
            if worker is not None and worker.poll() is not None:
                raise RuntimeError("Fixture worker exited; inspect its local diagnostics")
            time.sleep(0.5)
        raise RuntimeError("Fixture lifetime budget expired")


exit_code = 0
try:
    main()
except KeyboardInterrupt:
    pass
except (RuntimeError, subprocess.SubprocessError) as failure:
    # Subprocess command lines may contain test setup details; emit a bounded category only.
    print(str(failure) if isinstance(failure, RuntimeError) else "Fixture subprocess failed", file=sys.stderr)
    exit_code = 1
finally:
    try:
        for process in (worker, api):
            if process is not None and process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
    finally:
        try:
            if container:
                try:
                    diagnostic = subprocess.run(docker + ["logs", "--tail", "200", container], capture_output=True, text=True, timeout=10)
                    (logs / "database.log").write_text((diagnostic.stdout + diagnostic.stderr)[-64000:])
                finally:
                    subprocess.run(docker + ["rm", "-f", container], capture_output=True, timeout=20, check=True)
        finally:
            environment_file.unlink(missing_ok=True)
            resource_file.unlink(missing_ok=True)
            try:
                if mail is not None:
                    mail.close()
            finally:
                if storage is not None:
                    storage.close()
sys.exit(exit_code)
