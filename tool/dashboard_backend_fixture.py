"""Own an isolated real API/PostgreSQL fixture for browser integration tests."""

import base64
import os
from pathlib import Path
import re
import secrets
import shlex
import signal
import subprocess
import sys
import time


root = Path(__file__).resolve().parents[1]
docker = shlex.split(os.environ.get("HRIS_TEST_DOCKER_COMMAND", "docker"))
jar = root / "apps/server/build/libs/apps-server.jar"
container = None
api = None
logs = root / ".work/dashboard-integration"
environment_file = logs / "postgres.env"


def command(arguments):
    return subprocess.run(arguments, text=True, capture_output=True, check=True, timeout=90).stdout.strip()


def stop(_signal, _frame):
    raise KeyboardInterrupt


def main():
    global container, api
    if not jar.is_file():
        raise RuntimeError("Build the API with ./gradlew :apps:server:bootJar first")
    os.umask(0o077)
    logs.mkdir(parents=True, exist_ok=True)
    for event in (signal.SIGINT, signal.SIGTERM):
        signal.signal(event, stop)
    database_password = secrets.token_urlsafe(24)
    environment_file.write_text(f"POSTGRES_PASSWORD={database_password}\n")
    initialization = logs / "runtime-role.sql"
    initialization.write_text((root / "apps/server/src/test/resources/runtime-role.sql").read_text())
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
        "HRIS_SECURE_COOKIES": "false",  # Loopback HTTP fixture only.
        "HRIS_OIDC_ENABLED": "false",
        "HRIS_MAIL_ENABLED": "false",
        "HRIS_STORAGE_ENABLED": "false",
        "HRIS_DOCUMENT_SCANNER_ENABLED": "false",
        "SERVER_ADDRESS": "127.0.0.1",
        "SERVER_PORT": "18080",
    }
    with (logs / "backend.log").open("w") as output:
        api = subprocess.Popen(["java", "-jar", str(jar)], env=environment, stdout=output, stderr=subprocess.STDOUT)
        print("Started isolated browser API fixture; diagnostics: .work/dashboard-integration/backend.log", flush=True)
        deadline = time.monotonic() + 600
        while time.monotonic() < deadline:
            if api.poll() is not None:
                raise RuntimeError("Fixture API exited; inspect its local diagnostics")
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
        if api is not None and api.poll() is None:
            api.terminate()
            try:
                api.wait(timeout=10)
            except subprocess.TimeoutExpired:
                api.kill()
                api.wait(timeout=5)
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
sys.exit(exit_code)
