#!/usr/bin/env python3
"""Exercise recovery against owned real PostgreSQL/API/worker/Garage fixtures only."""

import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import shutil
import signal
import struct
import tempfile
import time
from urllib.parse import quote
from urllib.request import Request, ProxyHandler, build_opener
from urllib.error import URLError
import uuid

import database_recovery as database
import recovery_set as recovery
from recovery_storage import GARAGE_IMAGE, container_endpoint, s3_get

ROOT = Path(__file__).resolve().parents[1]


def write_environment(path, values):
    with database.create_file(path) as output:
        output.write("".join(name + "=" + value + "\n" for name, value in values.items()).encode())


def api(endpoint, path, *, body=None, token=None, post=False, method=None):
    headers = {"Content-Type": "application/json", "Idempotency-Key": str(uuid.uuid4())}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = Request(endpoint + "/api/v1" + path, headers=headers,
                      data=json.dumps(body).encode() if body is not None else (b"" if post else None),
                      method=method or ("POST" if post else "GET"))
    with build_opener(ProxyHandler({})).open(request, timeout=30) as response:
        content = response.read(2 * 1024 ** 2 + 1)
        assert len(content) <= 2 * 1024 ** 2
        return json.loads(content)


def authenticator(secret):
    counter = int(time.time()) // 30
    value = hmac.new(base64.b32decode(secret), struct.pack(">Q", counter), hashlib.sha1).digest()
    offset = value[-1] & 15
    return str((struct.unpack(">I", value[offset:offset + 4])[0] & 0x7fffffff) % 1_000_000).zfill(6)


def put_object(endpoint, key, body, configuration, directory, log):
    config = directory / "fixture-curl.config"
    content = directory / "fixture-object"
    try:
        with database.create_file(content) as output:
            output.write(body)
        with database.create_file(config) as output:
            output.write((
                'url = ' + json.dumps(endpoint + "/" + configuration["bucket"] + "/" + quote(key, safe="/")) + '\n'
                'aws-sigv4 = "aws:amz:garage:s3"\n'
                'user = ' + json.dumps(configuration["accessKey"] + ":" + configuration["secretKey"]) + '\n'
            ).encode())
        database.command(["curl", "--disable", "--config", str(config), "--silent", "--show-error", "--fail",
                          "--noproxy", "*", "--connect-timeout", "5", "--max-time", "30",
                          "--request", "PUT", "--data-binary", "@" + str(content)], log, stdout=log, timeout=35)
    finally:
        content.unlink(missing_ok=True)
        config.unlink(missing_ok=True)


def main():
    os.umask(0o077)
    run_id = uuid.uuid4().hex
    project = "hris-recovery-check-" + run_id
    resources = []
    root = Path(tempfile.mkdtemp(prefix=project + "-"))
    result = None
    # A failed cleanup deliberately retains this private fixture directory so a
    # still-running container never loses the bind mounts needed for diagnosis.
    with database.create_file(root / "fixture.log") as log:
        def run(service, image, arguments, *, environment_file=None, alias=None, extra=()):
            name = project + "-" + service
            resources.append(("container", name))
            command = ["docker", "run", "--detach", "--name", name, "--network", project,
                       "--label", database.LABEL + "=" + run_id,
                       "--label", "com.docker.compose.project=" + project,
                       "--label", "com.docker.compose.service=" + service,
                       "--memory=1g", "--pids-limit=512"]
            if alias:
                command += ["--network-alias", alias]
            if environment_file:
                command += ["--env-file", str(environment_file)]
            database.command(command + list(extra) + [image] + arguments, log, timeout=60)
            return name

        def binding(container, port):
            return container_endpoint(container, project, int(port.split("/")[0]), log)

        def ready_api(container):
            endpoint = binding(container, "8080/tcp")
            deadline = time.monotonic() + 90
            while time.monotonic() < deadline:
                try:
                    api(endpoint, "/auth/native/firebase")
                    return endpoint
                except URLError:
                    if database.captured(["docker", "inspect", "--format", "{{.State.Running}}", container], log) != "true":
                        raise RuntimeError("Owned fixture API exited") from None
                    time.sleep(0.5)
            raise RuntimeError("Owned fixture API readiness timed out")

        def run_api(environment_file):
            return run("api", "eclipse-temurin:21-jre", ["java", "-Xmx512m", "-jar", "/app.jar"],
                       environment_file=environment_file,
                       extra=("--mount",
                              f"type=bind,source={ROOT / 'apps/server/build/libs/apps-server.jar'},target=/app.jar,readonly"))

        try:
            resources.append(("network", project))
            database.command(["docker", "network", "create", "--internal", "--label", database.LABEL + "=" + run_id, project], log)
            old, current = (base64.b64encode(secrets.token_bytes(32)).decode() for _ in range(2))
            configuration = {"rpcSecret": secrets.token_hex(32), "accessKey": "GK" + secrets.token_hex(16),
                             "secretKey": secrets.token_hex(32), "bucket": "hris-recovery-fixture"}
            passwords = {name: secrets.token_urlsafe(24) for name in ("migration", "api", "worker")}
            write_environment(root / "database.env", {
                "POSTGRES_DB": "hris", "POSTGRES_USER": "hris_migrator", "POSTGRES_PASSWORD": passwords["migration"],
                "HRIS_DATABASE_PASSWORD": passwords["api"], "HRIS_WORKER_DATABASE_PASSWORD": passwords["worker"],
            })
            database_container = run("database", "postgres:18.6-alpine", [], alias="database",
                environment_file=root / "database.env", extra=(
                    "--tmpfs", "/var/lib/postgresql:size=768m",
                    "--mount", f"type=bind,source={ROOT / 'deploy/postgres/init-runtime.sh'},target=/docker-entrypoint-initdb.d/10-runtime.sh,readonly",
                    "--mount", f"type=bind,source={ROOT / 'deploy/postgres/configure-worker.sh'},target=/docker-entrypoint-initdb.d/20-worker.sh,readonly"))
            for path in (root / "meta", root / "data"):
                path.mkdir(mode=0o700)
            write_environment(root / "garage.env", {
                "GARAGE_RPC_SECRET": configuration["rpcSecret"], "GARAGE_DEFAULT_ACCESS_KEY": configuration["accessKey"],
                "GARAGE_DEFAULT_SECRET_KEY": configuration["secretKey"], "GARAGE_DEFAULT_BUCKET": configuration["bucket"],
            })
            garage = run("garage", GARAGE_IMAGE, ["/garage", "server", "--single-node", "--default-bucket"],
                environment_file=root / "garage.env", alias="garage", extra=(
                    "--user", f"{os.getuid()}:{os.getgid()}",
                    "--mount", f"type=bind,source={root / 'meta'},target=/var/lib/garage/meta",
                    "--mount", f"type=bind,source={root / 'data'},target=/var/lib/garage/data",
                    "--mount", f"type=bind,source={ROOT / 'deploy/garage/garage.toml'},target=/etc/garage.toml,readonly"))
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                pg = database.command(["docker", "exec", database_container, "pg_isready", "-h", "127.0.0.1", "-U", "hris_migrator"], log, allow_failure=True, timeout=5)
                storage = database.command(["docker", "exec", garage, "/garage", "bucket", "list"], log, allow_failure=True, timeout=5)
                if pg.returncode == 0 and storage.returncode == 0 and configuration["bucket"].encode() in storage.stdout:
                    break
                time.sleep(0.5)
            else:
                raise RuntimeError("Owned fixture services were not ready")
            common = {
                "HRIS_DATABASE_URL": "jdbc:postgresql://database:5432/hris", "HRIS_DATABASE_USER": "hris_app",
                "HRIS_DATABASE_PASSWORD": passwords["api"], "HRIS_WORKER_DATABASE_USER": "hris_worker",
                "HRIS_WORKER_DATABASE_PASSWORD": passwords["worker"], "HRIS_MIGRATIONS_ENABLED": "true",
                "HRIS_MIGRATION_USER": "hris_migrator", "HRIS_MIGRATION_PASSWORD": passwords["migration"],
                "HRIS_IDENTITY_ACTIVE_KEY": "old", "HRIS_IDENTITY_KEYS": "old:" + old + ",current:" + current,
                "HRIS_SYNC_ACTIVE_KEY": "sync", "HRIS_SYNC_KEYS": "sync:" + base64.b64encode(secrets.token_bytes(32)).decode(),
                "HRIS_STORAGE_ENABLED": "true", "HRIS_STORAGE_ENDPOINT": "http://garage:3900", "HRIS_STORAGE_REGION": "garage",
                "HRIS_STORAGE_BUCKET": configuration["bucket"], "HRIS_STORAGE_ACCESS_KEY": configuration["accessKey"],
                "HRIS_STORAGE_SECRET_KEY": configuration["secretKey"], "HRIS_STORAGE_ALLOW_PLAINTEXT": "true",
                "HRIS_BOOTSTRAP_EMAIL": "recovery-admin@example.invalid", "HRIS_BOOTSTRAP_PASSWORD": secrets.token_urlsafe(32),
                "HRIS_SECURE_COOKIES": "false", "HRIS_FCM_ENABLED": "false", "HRIS_MAIL_ENABLED": "false",
                "HRIS_OIDC_ENABLED": "false", "HRIS_FIREBASE_AUTH_ENABLED": "false", "HRIS_DOCUMENT_SCANNER_ENABLED": "false",
            }
            write_environment(root / "old-api.env", common)
            source_api = run_api(root / "old-api.env")
            endpoint = ready_api(source_api)
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                if database.query(database_container,
                    "SELECT count(*) FROM accounts a JOIN platform_permissions p ON p.account_id=a.id WHERE a.email='recovery-admin@example.invalid' AND p.permission='identity.manage'", log) == "1":
                    break
                time.sleep(0.25)
            else:
                raise RuntimeError("Owned bootstrap transaction did not complete")
            login_body = {"email": common["HRIS_BOOTSTRAP_EMAIL"], "password": common["HRIS_BOOTSTRAP_PASSWORD"], "deviceName": "Owned recovery acceptance"}
            token = api(endpoint, "/auth/native/login", body=login_body, post=True)["credentials"]["accessToken"]
            enrollment = api(endpoint, "/auth/native/mfa/enrollment", token=token, post=True)
            token = api(endpoint, "/auth/native/mfa/enrollment/confirm", token=token, post=True,
                        body={"operationId": enrollment["operationId"], "code": authenticator(enrollment["secret"])})["credentials"]["accessToken"]
            company = api(endpoint, "/companies", token=token, post=True,
                body={"code": "RECOVERY", "name": "Fictional Recovery Company", "timezone": "Asia/Jakarta"})["id"]
            # Publish fictional correspondence through the real API and worker so
            # the restored database contains operational projection evidence too.
            owner = str(uuid.uuid4())
            database.query(database_container,
                "INSERT INTO accounts(id,email,display_name) VALUES('" + owner + "','recovery-recipient@example.invalid','Fictional recovery recipient')", log)
            api(endpoint, "/companies/" + company + "/members/" + owner, token=token, method="PUT",
                body={"active": True, "permissions": ["announcements.read"], "reason": "Owned delivery recipient"})
            employee = str(uuid.uuid4())
            base = "/companies/" + company
            api(endpoint, base + "/employees", token=token, post=True, body={
                "id": employee, "employeeNumber": "RECOVERY-RECIPIENT",
                "person": {"id": str(uuid.uuid4()), "legalName": "Fictional Recovery Recipient", "nationality": "ID"},
                "terms": {"effectiveFrom": "2025-01-01", "startDate": "2025-01-01", "status": "ACTIVE", "contract": "PERMANENT"},
                "reason": "Owned delivery recovery fixture"})
            api(endpoint, base + "/employees/" + employee + "/account-link", token=token, post=True,
                body={"accountId": owner, "expectedVersion": 0, "reason": "Owned recipient identity"})
            announcement = str(uuid.uuid4())
            api(endpoint, base + "/announcements/" + announcement, token=token, method="PUT", body={
                "title": "Fictional recovery announcement", "body": "Owned recovery fixture only.",
                "audience": {"kind": "COMPANY", "targetIds": []}, "acknowledgementRequired": False,
                "emailNotification": True,
                "reason": "Owned recovery publication"})
            api(endpoint, base + "/announcements/" + announcement + "/publish", token=token, post=True,
                body={"expectedVersion": 0, "scheduledFor": None, "reason": "Owned recovery publication"})
            # Seed one fictional immutable source record through the normal capture
            # triggers, then verify every typed field after an independent restore.
            audit_id = str(uuid.uuid4())
            database.query(database_container,
                "INSERT INTO audit_entries(id,actor_id,resource_type,resource_id,action,reason,details,correlation_id) "
                "SELECT '" + audit_id + "',id,'account',id,'identity.account_access_saved',"
                "'Owned recovery audit fixture','{\"active\":\"true\",\"permissions\":\"audit.read\"}'::jsonb,gen_random_uuid() "
                "FROM accounts WHERE email='recovery-admin@example.invalid'", log)
            audit_query = "SELECT row_to_json(a)::text FROM platform_account_access_audit a WHERE id='" + audit_id + "'"
            saved_audit = json.loads(database.query(database_container, audit_query, log))
            assert saved_audit["permissions"] == ["audit.read"] and saved_audit["active"] is True
            print("Owned source API created encrypted MFA and native-session records", flush=True)
            database.command(["docker", "stop", "--time", "90", source_api], log, timeout=100)
            database.command(["docker", "rm", source_api], log)
            resources.remove(("container", source_api))
            common["HRIS_IDENTITY_ACTIVE_KEY"] = "current"
            write_environment(root / "current.env", common)
            source_api = run_api(root / "current.env")
            endpoint = ready_api(source_api)
            api(endpoint, "/auth/native/login", body=login_body, post=True)
            worker = run("worker", "eclipse-temurin:21-jre", ["java", "-Xmx512m", "-jar", "/app.jar"],
                         environment_file=root / "current.env", extra=("--mount",
                         f"type=bind,source={ROOT / 'apps/worker/build/libs/apps-worker.jar'},target=/app.jar,readonly"))
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                startup = root / "worker-startup.log"
                try:
                    with database.create_file(startup) as output:
                        database.command(["docker", "logs", "--tail", "100", worker], output, stdout=output)
                    assert startup.stat().st_size <= 256 * 1024
                    logs = startup.read_text()
                finally:
                    startup.unlink(missing_ok=True)
                if "Started WorkerApplicationKt" in logs:
                    break
                if database.captured(["docker", "inspect", "--format", "{{.State.Running}}", worker], log) != "true":
                    raise RuntimeError("Owned fixture worker exited")
                time.sleep(0.5)
            else:
                raise RuntimeError("Owned fixture worker startup timed out")
            deadline = time.monotonic() + 45
            while time.monotonic() < deadline:
                if database.query(database_container,
                    "SELECT count(*) FROM announcement_push_delivery WHERE announcement_id='" + announcement + "'", log) == "1":
                    break
                time.sleep(0.5)
            else:
                raise RuntimeError("Owned announcement publication did not reach its delivery projection")
            delivery_query = "SELECT row_to_json(d)::text FROM announcement_push_delivery d WHERE announcement_id='" + announcement + "'"
            email_query = "SELECT row_to_json(d)::text FROM announcement_email_delivery d WHERE announcement_id='" + announcement + "'"
            email_queue_query = "SELECT row_to_json(d)::text FROM inbox_email_dispatches d WHERE inbox_id IN (SELECT inbox_id FROM announcement_email_delivery WHERE announcement_id='" + announcement + "')"
            storage_endpoint = binding(garage, "3900/tcp")
            objects = {f"page/{index:03}": f"owned fixture {index}".encode() for index in range(101)}
            objects["empty/"] = b""
            objects["punctuation/space ?#%&雪.txt"] = "Fixture unicode bytes 雪".encode()
            objects["large/body"] = bytes(range(256)) * 8192 + b"final block"
            for key, body in objects.items():
                put_object(storage_endpoint, key, body, configuration, root, log)
            for container, timeout in ((source_api, 90), (worker, 120), (garage, 30)):
                database.command(["docker", "stop", "--time", str(timeout), container], log, timeout=timeout + 10)
            saved_delivery = json.loads(database.query(database_container, delivery_query, log))
            assert saved_delivery["state"] == "PENDING" and saved_delivery["processed_count"] == 0
            saved_email = json.loads(database.query(database_container, email_query, log))
            saved_email_queue = json.loads(database.query(database_container, email_queue_query, log))
            assert saved_email["state"] == "PENDING" and saved_email_queue["attempts"] == 0
            retained_ids = database.query(database_container,
                "SELECT DISTINCT split_part(envelope,'.',1) FROM (SELECT mfa_secret_encrypted AS envelope FROM accounts UNION ALL SELECT exchange_encrypted FROM native_sessions) retained WHERE envelope IS NOT NULL ORDER BY 1", log).splitlines()
            assert retained_ids == ['current', 'old']
            print("Source roles stopped; capturing database, storage and separate keys", flush=True)
            checkpoint, keys = root / "checkpoint", root / "separate-keys"
            manifest = recovery.backup_set(checkpoint, keys, project)
            assert manifest["storageVerification"]["objects"] == len(objects)
            assert manifest["storageVerification"]["bytes"] == sum(map(len, objects.values()))
            assert manifest["databaseVerification"]["identityEnvelopesVerified"] >= 2
            inventory = [json.loads(line) for line in (checkpoint / "inventory.jsonl").read_text().splitlines()[1:]]
            assert {entry["key"]: (entry["bytes"], entry["sha256"]) for entry in inventory} == {
                key: (len(body), hashlib.sha256(body).hexdigest()) for key, body in objects.items()}
            database.command(["docker", "start", garage], log)
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                if database.command(["docker", "exec", garage, "/garage", "status"], log, allow_failure=True, timeout=5).returncode == 0:
                    break
                time.sleep(0.25)
            else:
                raise RuntimeError("Owned source Garage restart timed out")
            storage_endpoint = binding(garage, "3900/tcp")
            put_object(storage_endpoint, "large/body", b"source changed after checkpoint", configuration, root, log)
            put_object(storage_endpoint, "later-object", b"not in checkpoint", configuration, root, log)
            report = recovery.verify_set(checkpoint, keys)
            database_directory, database_manifest = database.load_backup(checkpoint / "database")
            with database.recovery_database(root, database_manifest["postgresImage"], log) as restored:
                database.restore_database_container(restored[0], database_directory, log)
                assert json.loads(database.query(restored[0], audit_query, log)) == saved_audit
                assert json.loads(database.query(restored[0], delivery_query, log)) == saved_delivery
                assert json.loads(database.query(restored[0], email_query, log)) == saved_email
                assert json.loads(database.query(restored[0], email_queue_query, log)) == saved_email_queue
                for table in ("inbox_email_dispatches", "announcement_email_delivery"):
                    assert database.query(restored[0], "SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='public." + table + "'::regclass", log) == "t"
                assert database.query(restored[0], "SELECT has_function_privilege('hris_app','capture_announcement_email_delivery()','EXECUTE')", log) == "f"
                assert database.query(restored[0], "SELECT has_function_privilege('hris_app','claim_inbox_email(uuid,integer,integer,integer)','EXECUTE')", log) == "f"
                assert database.query(restored[0], "SELECT bool_and(email_notification) FROM announcement_revisions WHERE id='" + announcement + "'", log) == "t"
                assert database.query(restored[0], "SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='public.announcement_push_delivery'::regclass", log) == "t"
                assert database.query(restored[0], "SELECT has_function_privilege('hris_app','capture_announcement_push_delivery()','EXECUTE')", log) == "f"

            actual = s3_get(storage_endpoint, "/" + configuration["bucket"] + "/large/body", configuration, root, log, limit=1024, timeout=10)
            assert actual.read_bytes() == b"source changed after checkpoint"
            actual.unlink()
            # A recomputed metadata hash must not disguise the wrong actual AES key.
            bad_data, bad_keys = root / "bad-checkpoint", root / "bad-keys"
            shutil.copytree(checkpoint, bad_data)
            shutil.copytree(keys, bad_keys)
            wrong = json.loads((bad_keys / "keys.json").read_text())
            wrong["identityKeys"] = "old:" + base64.b64encode(secrets.token_bytes(32)).decode() + ",current:" + current
            (bad_keys / "keys.json").write_text(json.dumps(wrong))
            wrong_manifest = json.loads((bad_data / "manifest.json").read_text())
            wrong_manifest["keyPack"] = database.file_record(bad_keys / "keys.json")
            (bad_data / "manifest.json").write_text(json.dumps(wrong_manifest))
            try:
                recovery.verify_set(bad_data, bad_keys)
                raise AssertionError("Wrong recovered identity key unexpectedly passed")
            except database.RecoveryError:
                pass
            result = {"restorePassed": report["restorePassed"], "schema": report["database"]["schema"],
                      "identityEnvelopesVerified": report["database"]["identityEnvelopesVerified"],
                      "objects": len(objects), "bytes": sum(map(len, objects.values())),
                      "sourceUnchangedByRestore": True, "wrongRetiredKeyRejected": True,
                      "separateKeyPack": True, "temporaryResourcesRemoved": True,
                      "typedAccountAuditRestored": True, "announcementDeliveryRestored": True, "announcementEmailRestored": True}
            result['retainedIdentityKeyIds'] = retained_ids
        except BaseException:
            for kind, name in resources:
                if kind == "container":
                    database.command(["docker", "logs", "--tail", "80", name], log, stdout=log, allow_failure=True, timeout=10)
            print("Owned recovery fixture failed; private diagnostics retained at " + str(root), flush=True)
            raise
        finally:
            database.cleanup_owned(resources, run_id, log)
    if result is not None:
        shutil.rmtree(root)
        database.write_json(ROOT / ".work" / ("recovery-set-result-" + run_id + ".json"), result)
        print(json.dumps(result), flush=True)


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, database.request_cancellation)
    main()
