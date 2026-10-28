#!/usr/bin/env python3
"""Back up the Compose database or verify a backup in isolated PostgreSQL."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import signal
import stat
import subprocess
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
LABEL = "hris.recovery-run"
MAX_ARCHIVE_BYTES = 16 * 1024 ** 3
MAX_ROLES_BYTES = 1024 * 1024


class RecoveryError(Exception):
    """An operation failed; raw diagnostics remain in a private log."""


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def private_directory(path, *, create=False):
    path = Path(path).absolute()
    if path.is_symlink():
        raise RecoveryError("The backup directory must not be a symbolic link")
    path = path.resolve()
    if path.is_relative_to(ROOT):
        raise RecoveryError("Keep backups outside the repository")
    if create:
        path.mkdir(mode=0o700)
        sync_directory(path.parent)
    info = path.stat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
        raise RecoveryError("The backup directory must be owned by this user with mode 0700")
    return path


def private_file(path, limit):
    info = path.lstat()
    if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid()
            or info.st_mode & 0o077 or not 0 < info.st_size <= limit):
        raise RecoveryError(f"Invalid private backup file: {path.name}")
    return path


def create_file(path):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    return os.fdopen(descriptor, "wb")


def sync_directory(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def write_json(path, value):
    with create_file(path) as output:
        output.write((json.dumps(value, indent=2) + "\n").encode())
        output.flush()
        os.fsync(output.fileno())
    sync_directory(path.parent)


def command(args, log, *, timeout=30, stdout=None, stdin=None, input=None, allow_failure=False):
    """Run one bounded operation without printing command arguments or raw errors."""
    try:
        result = subprocess.run(args, cwd=ROOT, check=False, timeout=timeout,
                                stdout=stdout if stdout is not None else subprocess.PIPE,
                                stderr=log, stdin=stdin, input=input)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise RecoveryError(f"External operation failed ({type(error).__name__}); inspect the private log") from None
    if result.returncode and not allow_failure:
        raise RecoveryError("External operation failed; inspect the private log")
    if stdout is None and len(result.stdout) > MAX_ROLES_BYTES:
        raise RecoveryError("External metadata exceeded its limit")
    return result


def captured(args, log, **kwargs):
    return command(args, log, **kwargs).stdout.decode().strip()


def query(container, sql, log):
    return captured(["docker", "exec", "--env", "PGOPTIONS=-c statement_timeout=15000 -c lock_timeout=5000",
                     container, "psql", "-X", "-At", "-U", "hris_migrator",
                     "-d", "hris", "-v", "ON_ERROR_STOP=1", "-c", sql], log)


def file_record(path):
    return {"bytes": path.stat().st_size, "sha256": sha256(path)}


def backup_database(directory, compose_files):
    directory = private_directory(directory, create=True)
    with create_file(directory / "backup.log") as log:
        compose = ["docker", "compose"]
        for path in compose_files:
            compose.extend(["-f", str(path)])
        container = captured(compose + ["ps", "-q", "database"], log)
        if not re.fullmatch(r"[a-f0-9]{64}", container):
            raise RecoveryError("Exactly one running Compose database is required")
        return dump_database_container(directory, container, log)


def backup_database_container(directory, container):
    """Checkpoint an already selected Compose database for a quiesced recovery set."""
    directory = private_directory(directory, create=True)
    with create_file(directory / "backup.log") as log:
        return dump_database_container(directory, container, log)


def dump_database_container(directory, container, log):
    service = captured(["docker", "inspect", "--format",
                        '{{index .Config.Labels "com.docker.compose.service"}}', container], log)
    if service != "database":
        raise RecoveryError("The selected container is not the Compose database service")
    image = captured(["docker", "inspect", "--format", "{{.Config.Image}}", container], log)
    schema = query(container, "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;", log)
    archive = directory / "database.partial"
    roles = directory / "roles.partial"
    try:
        with create_file(archive) as output:
            command(["docker", "exec", "--env", "PGOPTIONS=-c statement_timeout=120000", container,
                     "pg_dump", "-U", "hris_migrator", "-d", "hris", "--format=custom",
                     "--lock-wait-timeout=10s"], log, stdout=output, timeout=300)
            output.flush()
            os.fsync(output.fileno())
        private_file(archive, MAX_ARCHIVE_BYTES)
        with archive.open("rb") as source:
            command(["docker", "exec", "-i", container, "pg_restore", "--list"], log,
                    stdin=source, stdout=log, timeout=60)
        with create_file(roles) as output:
            command(["docker", "exec", container, "pg_dumpall", "-U", "hris_migrator", "-l", "hris",
                     "--roles-only", "--no-role-passwords"], log, stdout=output, timeout=60)
            output.flush()
            os.fsync(output.fileno())
        private_file(roles, MAX_ROLES_BYTES)
        restored_role_sql(roles)
        archive.rename(directory / "database.dump")
        roles.rename(directory / "roles.sql")
        manifest = {
            "format": 1, "createdAt": datetime.now(timezone.utc).isoformat(),
            "database": "hris", "bootstrapRole": "hris_migrator", "postgresImage": image,
            "observedSchema": schema,
            "files": {name: file_record(directory / name) for name in ("database.dump", "roles.sql")},
            "rolePasswordsIncluded": False, "externalSecretsIncluded": False, "objectStorageIncluded": False,
        }
        write_json(directory / "manifest.json", manifest)
        return manifest
    finally:
        archive.unlink(missing_ok=True)
        roles.unlink(missing_ok=True)

def load_backup(directory):
    directory = private_directory(directory)
    manifest_path = private_file(directory / "manifest.json", 64 * 1024)
    try:
        manifest = json.loads(manifest_path.read_text())
        if (type(manifest["format"]) is not int or manifest["format"] != 1 or manifest["database"] != "hris"
                or manifest["bootstrapRole"] != "hris_migrator" or manifest["rolePasswordsIncluded"] is not False):
            raise ValueError()
        image = manifest["postgresImage"]
        if not re.fullmatch(r"postgres:(?:18\.[0-9]+-alpine)|postgres@sha256:[a-f0-9]{64}", image):
            raise ValueError()
        for name, limit in (("database.dump", MAX_ARCHIVE_BYTES), ("roles.sql", MAX_ROLES_BYTES)):
            path = private_file(directory / name, limit)
            if manifest["files"][name] != file_record(path):
                raise RecoveryError(f"Backup checksum or length mismatch: {name}")
    except (KeyError, TypeError, ValueError):
        raise RecoveryError("Invalid backup manifest") from None
    restored_role_sql(directory / "roles.sql")
    return directory, manifest


def restored_role_sql(path):
    contents = private_file(path, MAX_ROLES_BYTES).read_text()
    if re.search(r"\bPASSWORD\s", contents, re.IGNORECASE) or contents.count("CREATE ROLE hris_migrator;") != 1:
        raise RecoveryError("Expected role definitions without passwords and one bootstrap role")
    # PostgreSQL 18 retains grantor identity. Restore as the original bootstrap role;
    # keep its attributes and grants, omitting only its already-completed creation.
    return contents.replace("CREATE ROLE hris_migrator;", "-- Bootstrap role already exists.")


def cleanup_owned(resources, run_id, log):
    failures = []
    for kind, name in reversed(resources):
        field = ".Labels" if kind == "network" else ".Config.Labels"
        try:
            inspected = command(["docker", kind, "inspect", "--format",
                                 '{{index ' + field + ' "' + LABEL + '"}}', name], log, allow_failure=True)
        except RecoveryError:
            failures.append("An owned verification resource could not be inspected")
            continue
        if inspected.returncode != 0:
            listing = ["docker", kind, "ls"] + (["--all"] if kind == "container" else [])
            try:
                present = captured(listing + ["--filter", "name=" + name, "--format", "{{.Name}}" if kind == "network" else "{{.Names}}"], log)
                if present:
                    failures.append("A verification resource exists but could not be inspected")
            except RecoveryError:
                failures.append("Could not confirm that a verification resource was removed")
            continue
        if inspected.stdout.decode().strip() != run_id:
            failures.append("Refused to remove a resource owned by another run")
            continue
        args = ["docker", kind, "rm"] + (["--force"] if kind == "container" else []) + [name]
        try:
            command(args, log, stdout=log, timeout=30)
        except RecoveryError:
            failures.append("An owned verification resource could not be removed")
    if failures:
        raise RecoveryError("; ".join(failures))


@contextmanager
def recovery_database(directory, image, log):
    run_id = uuid.uuid4().hex
    network = "hris-recovery-net-" + run_id
    container = "hris-recovery-db-" + run_id
    resources = []
    with tempfile.TemporaryDirectory(prefix="verification-", dir=directory) as temporary:
        temporary = Path(temporary)
        password = secrets.token_urlsafe(36)
        with create_file(temporary / "postgres.env") as output:
            output.write(("POSTGRES_USER=hris_migrator\nPOSTGRES_DB=hris\nPOSTGRES_PASSWORD=" + password + "\n").encode())
        with create_file(temporary / "flyway.env") as output:
            output.write(("FLYWAY_URL=jdbc:postgresql://database:5432/hris\nFLYWAY_USER=hris_migrator\nFLYWAY_PASSWORD=" + password + "\n").encode())
        try:
            resources.append(("network", network))
            command(["docker", "network", "create", "--internal", "--label", LABEL + "=" + run_id, network], log)
            resources.append(("container", container))
            command(["docker", "run", "--detach", "--name", container, "--network", network,
                     "--network-alias", "database", "--label", LABEL + "=" + run_id,
                     "--memory", "1g", "--cpus", "1", "--tmpfs", "/var/lib/postgresql:rw,size=768m,mode=0755",
                     "--env-file", str(temporary / "postgres.env"), image,
                     "-c", "statement_timeout=120000", "-c", "lock_timeout=10000"], log, timeout=60)
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                if captured(["docker", "inspect", "--format", "{{.State.Running}}", container], log) != "true":
                    raise RecoveryError("Verification PostgreSQL exited during startup")
                ready = command(["docker", "exec", container, "pg_isready", "-h", "127.0.0.1",
                                 "-U", "hris_migrator", "-d", "hris"], log, allow_failure=True, timeout=5)
                if ready.returncode == 0:
                    break
                time.sleep(0.5)
            else:
                raise RecoveryError("Verification PostgreSQL startup deadline exceeded")
            yield container, network, run_id, resources, temporary / "flyway.env"
        finally:
            cleanup_owned(resources, run_id, log)


def inspect_restored_database(container, log):
    return {
        "schema": query(container, "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;", log),
        "failedMigrations": int(query(container, "SELECT count(*) FROM flyway_schema_history WHERE NOT success;", log)),
        "publicRelations": int(query(container, "SELECT count(*) FROM pg_tables WHERE schemaname='public';", log)),
        "invalidConstraints": int(query(container, "SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND NOT convalidated;", log)),
        "runtimeRoles": query(container, "SELECT rolname,rolsuper,rolbypassrls,rolcreaterole,rolcreatedb FROM pg_roles WHERE rolname IN ('hris_app','hris_worker') ORDER BY rolname;", log).splitlines(),
    }


def restore_database_container(container, directory, log):
    command(["docker", "exec", "-i", container, "psql", "-X", "-U", "hris_migrator",
             "-d", "hris", "-v", "ON_ERROR_STOP=1"], log,
            input=restored_role_sql(directory / "roles.sql").encode(), stdout=log)
    with (directory / "database.dump").open("rb") as source:
        command(["docker", "exec", "-i", container, "pg_restore", "-U", "hris_migrator", "-d", "hris",
                 "--single-transaction", "--exit-on-error"], log, stdin=source, stdout=log, timeout=300)


def verify_backup(directory, *, migrate=False):
    directory, manifest = load_backup(directory)
    run_name = "verification-" + uuid.uuid4().hex
    with create_file(directory / (run_name + ".log")) as log:
        with recovery_database(directory, manifest["postgresImage"], log) as fixture:
            container, network, run_id, resources, flyway_env = fixture
            restore_database_container(container, directory, log)
            before = inspect_restored_database(container, log)
            after = before
            if migrate:
                for operation in ("migrate", "validate"):
                    name = "hris-recovery-flyway-" + operation + "-" + run_id
                    resources.append(("container", name))
                    command(["docker", "run", "--rm", "--name", name, "--network", network,
                             "--label", LABEL + "=" + run_id, "--memory", "512m", "--cpus", "1",
                             "--env-file", str(flyway_env), "--mount",
                             "type=bind,source=" + str(ROOT / "core/database/src/main/resources/db/migration") + ",target=/flyway/sql,readonly",
                             "redgate/flyway:12.4.0", "-connectRetries=3", operation], log, stdout=log, timeout=180)
                after = inspect_restored_database(container, log)
            expected = ["hris_app|f|f|f|f", "hris_worker|f|f|f|f"]
            for snapshot in (before, after):
                if snapshot["failedMigrations"] or snapshot["invalidConstraints"] or snapshot["runtimeRoles"] != expected:
                    raise RecoveryError("Restored schema or runtime-role verification failed")
        # Success is recorded only after the temporary resources have been removed.
        report = {"completedAt": datetime.now(timezone.utc).isoformat(), "backup": manifest["files"],
                  "restorePassed": True, "migrationsApplied": migrate, "before": before, "after": after,
                  "hostPortsPublished": False, "applicationProcessesStarted": False, "temporaryResourcesRemoved": True}
        write_json(directory / (run_name + ".json"), report)
        return report


def main(arguments=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="operation", required=True)
    backup = commands.add_parser("backup", help="Create a new private directory outside the repository")
    backup.add_argument("directory", type=Path)
    backup.add_argument("--compose-file", action="append", type=Path)
    verify = commands.add_parser("verify", help="Restore only into an owned temporary database; never into the live stack")
    verify.add_argument("directory", type=Path)
    verify.add_argument("--migrate", action="store_true", help="Also apply and validate current repository migrations")
    args = parser.parse_args(arguments)
    previous = signal.signal(signal.SIGTERM, request_cancellation)
    try:
        if args.operation == "backup":
            result = backup_database(args.directory, args.compose_file or [ROOT / "compose.yml"])
            print(json.dumps({"backupCreated": True, "schema": result["observedSchema"], "files": result["files"]}))
        else:
            result = verify_backup(args.directory, migrate=args.migrate)
            print(json.dumps(result))
    except (RecoveryError, OSError, ValueError) as error:
        parser.exit(1, f"Database recovery failed: {error}\n")
    except KeyboardInterrupt:
        parser.exit(130, "Database recovery cancelled. Inspect private logs if cleanup could not complete.\n")
    finally:
        signal.signal(signal.SIGTERM, previous)


def request_cancellation(_signal, _frame):
    raise KeyboardInterrupt()


if __name__ == "__main__":
    main()
