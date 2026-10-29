#!/usr/bin/env python3
"""Checkpoint stopped Compose application/storage services and verify their recovery set."""

import argparse
import base64
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import signal
import tempfile
import tomllib
import uuid

import database_recovery as database
from database_recovery import RecoveryError
from recovery_archive import capture_file, inspect_archive, restore_archive
from recovery_storage import GARAGE_IMAGE, MAX_CONTENT, MAX_METADATA, restored_garage, scan_storage

ROOT = Path(__file__).resolve().parents[1]
SERVICES = ("api", "worker", "garage", "database")
FILES = {"metadata.tar": MAX_METADATA, "content.tar": MAX_CONTENT,
         "garage.toml": 16 * 1024, "inventory.jsonl": 1024 ** 3}
KEY_FIELDS = {"format", "checkpoint", "identityActive", "identityKeys", "syncActive", "syncKeys",
              "rpcSecret", "accessKey", "secretKey", "bucket"}
# Review this bound when new migrations add another persisted encryption contract.
# V99 adds global audit metadata only; no new encrypted payload family.
MAX_SCHEMA = 100
ENVELOPES_SQL = """COPY (
SELECT * FROM (
 SELECT 'hris:totp:'||id AS purpose, mfa_secret_encrypted AS envelope
 FROM accounts WHERE mfa_secret_encrypted IS NOT NULL
 UNION ALL SELECT 'hris:totp:'||account_id, secret_encrypted FROM mfa_enrollments
 UNION ALL SELECT 'credential-mail:'||account_id||':'||challenge_id, token_encrypted
 FROM identity_mail_deliveries WHERE token_encrypted IS NOT NULL
 UNION ALL SELECT 'hris:native:'||account_id||':'||id||':'||exchange_operation_id, exchange_encrypted
 FROM native_sessions WHERE exchange_encrypted IS NOT NULL
 UNION ALL SELECT 'hris:native:'||s.account_id||':'||s.id||':'||r.operation_id, r.replay_encrypted
 FROM consumed_refresh_tokens r JOIN native_sessions s ON s.id=r.session_id WHERE r.replay_encrypted IS NOT NULL
 UNION ALL SELECT 'hris:push:'||account_id||':'||session_id||':'||version, token_encrypted
 FROM native_push_registrations WHERE token_encrypted IS NOT NULL
) envelopes LIMIT 100001
) TO STDOUT WITH (FORMAT text, DELIMITER E'\\t');"""


def keyring(active, encoded, *, required):
    if not isinstance(active, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,32}", active):
        raise RecoveryError("Invalid recovery key identifier")
    if not isinstance(encoded, str) or len(encoded) > 4096 or "\n" in encoded or "\r" in encoded:
        raise RecoveryError("Invalid recovery keyring")
    keys = {}
    if encoded.strip():
        try:
            for item in encoded.split(","):
                name, value = item.strip().split(":", 1)
                if not re.fullmatch(r"[A-Za-z0-9_-]{1,32}", name) or name in keys or len(keys) >= 8:
                    raise ValueError()
                decoded = base64.b64decode(value, validate=True)
                if len(decoded) != 32:
                    raise ValueError()
                keys[name] = decoded
            if active not in keys:
                raise ValueError()
        except (ValueError, TypeError):
            raise RecoveryError("Invalid recovery keyring") from None
    elif required:
        raise RecoveryError("Identity recovery requires the configured encryption keys")
    return keys


def validate_keys(value):
    if (not isinstance(value, dict) or set(value) != KEY_FIELDS or type(value["format"]) is not int
            or value["format"] != 1 or not re.fullmatch(r"[a-f0-9]{32}", str(value["checkpoint"]))):
        raise RecoveryError("Invalid recovery key pack")
    keyring(value["identityActive"], value["identityKeys"], required=True)
    keyring(value["syncActive"], value["syncKeys"], required=False)
    for field, pattern in (("rpcSecret", r"[a-f0-9]{64}"), ("accessKey", r"GK[a-f0-9]{32}"),
                           ("secretKey", r"[a-f0-9]{64}"), ("bucket", r"[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")):
        if not isinstance(value[field], str) or not re.fullmatch(pattern, value[field]):
            raise RecoveryError("Unsupported Garage recovery configuration")
    return value


def environment(info):
    result = {}
    for entry in info["Config"]["Env"]:
        name, value = entry.split("=", 1)
        if name in result or len(value) > 8192:
            raise RecoveryError("Invalid source container configuration")
        result[name] = value
    return result


def source_services(project, log):
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,62}", project):
        raise RecoveryError("Invalid Compose project name")
    result = {}
    for service in SERVICES:
        container = database.captured([
            "docker", "ps", "--all", "--no-trunc", "--quiet", "--filter", "label=com.docker.compose.project=" + project,
            "--filter", "label=com.docker.compose.service=" + service,
        ], log)
        if not re.fullmatch(r"[a-f0-9]{64}", container):
            raise RecoveryError("Exactly one source container per Compose service is required")
        info = json.loads(database.captured(["docker", "inspect", container], log))[0]
        labels = info["Config"]["Labels"]
        state = info["State"]
        if labels.get("com.docker.compose.project") != project or labels.get("com.docker.compose.service") != service:
            raise RecoveryError("The selected source service changed")
        if service == "database":
            if state["Status"] != "running" or not state["Running"] or state["Paused"]:
                raise RecoveryError("The source database must remain running")
        elif (state["Status"] != "exited" or state["Running"] or state["Restarting"] or state["Paused"]
              or state["ExitCode"] not in (0, 143) or state["OOMKilled"]):
            raise RecoveryError("API, worker and Garage must already be gracefully stopped")
        result[service] = info
    if result["garage"]["Config"]["Image"] != GARAGE_IMAGE:
        raise RecoveryError("Only the configured Garage 2.4.1 single-node service is supported")
    return result


def source_signature(services):
    return {name: (info["Id"], info["State"]["StartedAt"], info["State"]["FinishedAt"], info["Image"])
            for name, info in services.items()}


def source_keys(services, checkpoint):
    api, worker, garage = (environment(services[name]) for name in ("api", "worker", "garage"))
    for role in (api, worker):
        if (role.get("HRIS_STORAGE_ENABLED") != "true" or role.get("HRIS_STORAGE_ENDPOINT") != "http://garage:3900"
                or role.get("HRIS_STORAGE_REGION") != "garage"):
            raise RecoveryError("Recovery requires the Compose Garage storage configuration")
    for name in ("HRIS_STORAGE_BUCKET", "HRIS_STORAGE_ACCESS_KEY", "HRIS_STORAGE_SECRET_KEY", "HRIS_IDENTITY_ACTIVE_KEY"):
        if api.get(name) != worker.get(name):
            raise RecoveryError("API and worker recovery configuration differs")
    if keyring(api.get("HRIS_IDENTITY_ACTIVE_KEY"), api.get("HRIS_IDENTITY_KEYS"), required=True) != keyring(
            worker.get("HRIS_IDENTITY_ACTIVE_KEY"), worker.get("HRIS_IDENTITY_KEYS"), required=True):
        raise RecoveryError("API and worker identity keyrings differ")
    return validate_keys({
        "format": 1, "checkpoint": checkpoint,
        "identityActive": api["HRIS_IDENTITY_ACTIVE_KEY"], "identityKeys": api["HRIS_IDENTITY_KEYS"],
        "syncActive": api.get("HRIS_SYNC_ACTIVE_KEY", "v1"), "syncKeys": api.get("HRIS_SYNC_KEYS", ""),
        "rpcSecret": garage.get("GARAGE_RPC_SECRET"), "accessKey": api.get("HRIS_STORAGE_ACCESS_KEY"),
        "secretKey": api.get("HRIS_STORAGE_SECRET_KEY"), "bucket": api.get("HRIS_STORAGE_BUCKET"),
    })


def separate_directories(directory, key_directory, *, create=False):
    first, second = Path(directory).absolute(), Path(key_directory).absolute()
    if first.resolve().is_relative_to(second.resolve()) or second.resolve().is_relative_to(first.resolve()):
        raise RecoveryError("Store the key pack separately from the data checkpoint")
    for path in (first.resolve(), second.resolve()):
        if any((ancestor / ".git").exists() for ancestor in (path, *path.parents)):
            raise RecoveryError("Keep recovery data and keys outside every Git checkout")
    # Preflight both paths before creating either directory.
    if create and (first.exists() or first.is_symlink() or second.exists() or second.is_symlink()):
        raise RecoveryError("Recovery output directories must be new")
    return database.private_directory(first, create=create), database.private_directory(second, create=create)


def validate_garage_config(path):
    try:
        # Only the reviewed single-node layout is supported; never execute a copied
        # arbitrary config with public admin ports or outbound cluster peers.
        actual = tomllib.loads(path.read_text())
        expected = tomllib.loads((ROOT / "deploy/garage/garage.toml").read_text())
        if actual != expected:
            raise ValueError()
    except (ValueError, UnicodeError):
        raise RecoveryError("Unsupported Garage recovery layout") from None


def validate_schema(manifest):
    try:
        schema = int(manifest["observedSchema"])
        if not 59 <= schema <= MAX_SCHEMA:
            raise ValueError()
    except (KeyError, TypeError, ValueError):
        raise RecoveryError("The source schema requires a reviewed recovery verifier") from None


def verify_database_keys(directory, keys, log):
    database_directory, manifest = database.load_backup(directory / "database")
    validate_schema(manifest)
    jar = ROOT / "apps/server/build/libs/apps-server.jar"
    if not jar.is_file():
        raise RecoveryError("Build the current API JAR before verifying recovery keys")
    with database.recovery_database(directory, manifest["postgresImage"], log) as fixture:
        container = fixture[0]
        database.restore_database_container(container, database_directory, log)
        snapshot = database.inspect_restored_database(container, log)
        if (snapshot["schema"] != manifest["observedSchema"] or snapshot["failedMigrations"] or snapshot["invalidConstraints"]
                or snapshot["runtimeRoles"] != ["hris_app|f|f|f|f", "hris_worker|f|f|f|f"]):
            raise RecoveryError("Restored database constraints or runtime roles failed verification")
        with tempfile.TemporaryDirectory(prefix="key-verify-", dir=directory) as temporary:
            temporary = Path(temporary)
            configuration = temporary / "keys.txt"
            with database.create_file(configuration) as output:
                output.write(("\n".join(keys[name] for name in ("identityActive", "identityKeys", "syncActive", "syncKeys")) + "\n").encode())
            records = temporary / "envelopes.tsv"
            capture_file([
                "docker", "exec", "--env", "PGOPTIONS=-c statement_timeout=30000 -c lock_timeout=5000",
                container, "psql", "-X", "-q", "-U", "hris_migrator", "-d", "hris", "-v", "ON_ERROR_STOP=1", "-c", ENVELOPES_SQL,
            ], records, log, limit=512 * 1024 ** 2, timeout=60)
            result = json.loads(database.captured([
                "java", "-Xmx128m", "-Dloader.main=dev.fajar.hris.recovery.RecoveryKeyVerifier", "-cp", str(jar),
                "org.springframework.boot.loader.launch.PropertiesLauncher", str(configuration), str(records),
            ], log, timeout=120))
            if (set(result) != {"identityEnvelopesVerified"} or type(result["identityEnvelopesVerified"]) is not int
                    or not 0 <= result["identityEnvelopesVerified"] <= 100_000):
                raise RecoveryError("Invalid recovery key verification result")
    return snapshot | result


def backup_set(directory, key_directory, project):
    directory, key_directory = separate_directories(directory, key_directory, create=True)
    checkpoint = uuid.uuid4().hex
    started = datetime.now(timezone.utc).isoformat()
    completed = False
    keys_created = False
    with database.create_file(directory / "backup.log") as log:
        try:
            services = source_services(project, log)
            signature = source_signature(services)
            keys = source_keys(services, checkpoint)
            keys_created = True
            database.write_json(key_directory / "keys.json", keys)
            backup = database.backup_database_container(directory / "database", services["database"]["Id"])
            validate_schema(backup)
            garage = services["garage"]["Id"]
            for name, source, limit in (("metadata.tar", "meta", MAX_METADATA), ("content.tar", "data", MAX_CONTENT)):
                capture_file(["docker", "cp", garage + ":/var/lib/garage/" + source + "/.", "-"],
                             directory / name, log, limit=limit, timeout=300)
                inspect_archive(directory / name, limit=limit)
            with tempfile.TemporaryDirectory(prefix="config-copy-", dir=directory) as temporary:
                temporary = Path(temporary)
                archive = temporary / "config.tar"
                capture_file(["docker", "cp", garage + ":/etc/garage.toml", "-"], archive, log, limit=32 * 1024, timeout=30)
                restore_archive(archive, temporary / "contents", limit=16 * 1024)
                with database.create_file(directory / "garage.toml") as output:
                    output.write((temporary / "contents/garage.toml").read_bytes())
                    output.flush()
                    os.fsync(output.fileno())
            validate_garage_config(directory / "garage.toml")
            image = services["garage"]["Image"]
            if not re.fullmatch(r"sha256:[a-f0-9]{64}", image):
                raise RecoveryError("Invalid Garage image identity")
            database_result = verify_database_keys(directory, keys, log)
            with restored_garage(directory, keys, log, image=image) as (endpoint, temporary):
                storage_result = scan_storage(endpoint, keys, temporary, log, directory / "inventory.jsonl")
            if source_signature(source_services(project, log)) != signature:
                raise RecoveryError("Source services changed while capturing the checkpoint")
            manifest = {
                "format": 1, "checkpoint": checkpoint, "garageImage": GARAGE_IMAGE, "garageImageId": image,
                "startedAt": started, "completedAt": datetime.now(timezone.utc).isoformat(),
                "sourceImages": {name: {"reference": services[name]["Config"]["Image"], "id": services[name]["Image"]} for name in SERVICES},
                "files": {name: database.file_record(directory / name) for name in FILES},
                "databaseManifest": database.file_record(directory / "database/manifest.json"),
                "keyPack": database.file_record(key_directory / "keys.json"),
                "databaseVerification": database_result, "storageVerification": storage_result,
            }
            database.write_json(directory / "manifest.json", manifest)
            completed = True
            return manifest
        finally:
            if not completed:
                for name in (*FILES, "manifest.json"):
                    (directory / name).unlink(missing_ok=True)
                if keys_created:
                    (key_directory / "keys.json").unlink(missing_ok=True)


def load_set(directory, key_directory):
    directory, key_directory = separate_directories(directory, key_directory)
    manifest = json.loads(database.private_file(directory / "manifest.json", 64 * 1024).read_text())
    try:
        if (type(manifest["format"]) is not int or manifest["format"] != 1
                or manifest["garageImage"] != GARAGE_IMAGE or set(manifest["files"]) != set(FILES)
                or not re.fullmatch(r"sha256:[a-f0-9]{64}", manifest["garageImageId"])):
            raise ValueError()
        if set(manifest["sourceImages"]) != set(SERVICES):
            raise ValueError()
        for image in manifest["sourceImages"].values():
            if (set(image) != {"reference", "id"} or not isinstance(image["reference"], str)
                    or not 0 < len(image["reference"]) <= 512 or not re.fullmatch(r"sha256:[a-f0-9]{64}", image["id"])):
                raise ValueError()
        if manifest["sourceImages"]["garage"] != {"reference": manifest["garageImage"], "id": manifest["garageImageId"]}:
            raise ValueError()
        for name, limit in FILES.items():
            path = database.private_file(directory / name, limit)
            if database.file_record(path) != manifest["files"][name]:
                raise RecoveryError("Recovery checkpoint length or checksum differs")
        key_file = database.private_file(key_directory / "keys.json", 32 * 1024)
        if database.file_record(key_file) != manifest["keyPack"]:
            raise RecoveryError("Recovery key pack length or checksum differs")
        keys = validate_keys(json.loads(key_file.read_text()))
        if keys["checkpoint"] != manifest["checkpoint"]:
            raise RecoveryError("Recovery key pack belongs to another checkpoint")
        backup_directory, backup = database.load_backup(directory / "database")
        validate_schema(backup)
        if database.file_record(backup_directory / "manifest.json") != manifest["databaseManifest"]:
            raise RecoveryError("Database backup belongs to another checkpoint")
    except (KeyError, TypeError, ValueError):
        raise RecoveryError("Invalid recovery checkpoint manifest") from None
    validate_garage_config(directory / "garage.toml")
    inspect_archive(directory / "metadata.tar", limit=MAX_METADATA)
    inspect_archive(directory / "content.tar", limit=MAX_CONTENT)
    return directory, keys, manifest


def verify_set(directory, key_directory):
    directory, keys, manifest = load_set(directory, key_directory)
    name = "verification-" + uuid.uuid4().hex
    with database.create_file(directory / (name + ".log")) as log:
        database_result = verify_database_keys(directory, keys, log)
        with restored_garage(directory, keys, log, image=manifest["garageImageId"]) as (endpoint, temporary):
            storage_result = scan_storage(endpoint, keys, temporary, log, temporary / "inventory.jsonl",
                                          expected=directory / "inventory.jsonl")
        if database_result != manifest["databaseVerification"] or storage_result != manifest["storageVerification"]:
            raise RecoveryError("Restored checkpoint differs from its original verification")
        report = {"checkpoint": manifest["checkpoint"], "restorePassed": True,
                  "database": database_result, "storage": storage_result,
                  "applicationProcessesStarted": False, "temporaryResourcesRemoved": True}
        database.write_json(directory / (name + ".json"), report)
        return report


def main(arguments=None):
    parser = argparse.ArgumentParser(description=__doc__)
    operations = parser.add_subparsers(dest="operation", required=True)
    backup = operations.add_parser("backup", help="Read already stopped API/worker/Garage services; never stop or restore the live stack")
    backup.add_argument("directory", type=Path)
    backup.add_argument("--keys-directory", required=True, type=Path)
    backup.add_argument("--project", default="hris-platform")
    verify = operations.add_parser("verify", help="Verify the entire checkpoint in owned isolated services")
    verify.add_argument("directory", type=Path)
    verify.add_argument("--keys-directory", required=True, type=Path)
    args = parser.parse_args(arguments)
    previous = signal.signal(signal.SIGTERM, database.request_cancellation)
    try:
        if args.operation == "backup":
            result = backup_set(args.directory, args.keys_directory, args.project)
            print(json.dumps({"checkpoint": result["checkpoint"], "backupCreated": True,
                              "database": result["databaseVerification"], "storage": result["storageVerification"]}))
        else:
            print(json.dumps(verify_set(args.directory, args.keys_directory)))
    except KeyboardInterrupt:
        parser.exit(130, "Recovery cancelled; inspect the private log for cleanup status.\n")
    except RecoveryError as error:
        parser.exit(1, f"Recovery failed: {error}. No success is acknowledged.\n")
    except (OSError, ValueError, KeyError, TypeError):
        parser.exit(1, "Recovery failed; inspect the private log. No success is acknowledged.\n")
    finally:
        signal.signal(signal.SIGTERM, previous)


if __name__ == "__main__":
    main()
