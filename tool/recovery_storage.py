"""Restore Garage only into an owned isolated service and inspect private S3 bytes."""

from contextlib import contextmanager
import ipaddress
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
import time
from urllib.parse import quote, unquote_to_bytes
import uuid
import xml.etree.ElementTree as ET

from database_recovery import (
    LABEL, RecoveryError, captured, cleanup_owned, command, create_file, sha256,
)
from recovery_archive import capture_file, restore_archive

GARAGE_IMAGE = "dxflrs/garage:v2.4.1"
MAX_METADATA = 128 * 1024 ** 2
MAX_CONTENT = 16 * 1024 ** 3
MAX_OBJECT = 128 * 1024 ** 2
MAX_OBJECTS = 100_000
NAMESPACE = "{http://s3.amazonaws.com/doc/2006-03-01/}"


def container_endpoint(container, network, port, log):
    info = json.loads(captured(["docker", "inspect", container], log))[0]["NetworkSettings"]
    if set(info["Networks"]) != {network} or any(info.get("Ports", {}).values()):
        raise RecoveryError("Recovery service must use only its private network without published ports")
    address = ipaddress.IPv4Address(info["Networks"][network]["IPAddress"])
    if not any(address in ipaddress.IPv4Network(cidr) for cidr in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")):
        raise RecoveryError("Recovery service has an unexpected network address")
    return f"http://{address}:{port}"


@contextmanager
def restored_garage(directory, secrets, log, *, image=GARAGE_IMAGE):
    run_id = uuid.uuid4().hex
    network = "hris-recovery-storage-net-" + run_id
    container = "hris-recovery-storage-" + run_id
    resources = []
    temporary = Path(tempfile.mkdtemp(prefix="storage-verify-", dir=directory))
    try:
        restore_archive(directory / "metadata.tar", temporary / "meta", limit=MAX_METADATA)
        restore_archive(directory / "content.tar", temporary / "data", limit=MAX_CONTENT)
        with create_file(temporary / "garage.env") as output:
            output.write(("GARAGE_RPC_SECRET=" + secrets["rpcSecret"] + "\n").encode())
        resources.append(("network", network))
        command(["docker", "network", "create", "--internal", "--label", LABEL + "=" + run_id, network], log)
        resources.append(("container", container))
        command([
            "docker", "run", "--detach", "--name", container, "--network", network,
            "--label", LABEL + "=" + run_id, "--read-only", "--cap-drop=ALL",
            "--security-opt=no-new-privileges:true", "--memory=1g", "--pids-limit=128",
            "--user", f"{os.getuid()}:{os.getgid()}", "--tmpfs", "/tmp:size=32m,mode=1777",
            "--env-file", str(temporary / "garage.env"),
            "--mount", f"type=bind,source={temporary / 'meta'},target=/var/lib/garage/meta",
            "--mount", f"type=bind,source={temporary / 'data'},target=/var/lib/garage/data",
            "--mount", f"type=bind,source={directory / 'garage.toml'},target=/etc/garage.toml,readonly",
            image, "/garage", "server", "--single-node",
        ], log, timeout=60)
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            ready = command(["docker", "exec", container, "/garage", "status"], log, timeout=5, allow_failure=True)
            if ready.returncode == 0:
                break
            if captured(["docker", "inspect", "--format", "{{.State.Running}}", container], log) != "true":
                raise RecoveryError("Restored Garage exited before readiness")
            time.sleep(0.25)
        else:
            raise RecoveryError("Restored Garage readiness exceeded its deadline")
        yield container_endpoint(container, network, 3900, log), temporary
    finally:
        cleanup_owned(resources, run_id, log)
        shutil.rmtree(temporary)

def s3_get(endpoint, target, secrets, directory, log, *, limit, timeout):
    """curl owns SigV4; credentials go in a mode-0600 file, never argv or output."""
    config = directory / "curl.config"
    response = directory / "response.partial"
    if config.exists() or config.is_symlink() or response.exists() or response.is_symlink():
        raise RecoveryError("A previous recovery request still owns its files")
    try:
        with create_file(config) as output:
            output.write((
                'url = ' + json.dumps(endpoint + target, ensure_ascii=True) + '\n'
                'aws-sigv4 = "aws:amz:garage:s3"\n'
                'user = ' + json.dumps(secrets["accessKey"] + ":" + secrets["secretKey"]) + '\n'
            ).encode())
        capture_file([
            "curl", "--disable", "--config", str(config), "--silent", "--show-error",
            "--fail", "--noproxy", "*", "--proto", "=http",
            "--connect-timeout", "5", "--max-time", str(max(1, int(timeout))),
        ], response, log, limit=limit, timeout=timeout + 1)
        return response
    except BaseException:
        response.unlink(missing_ok=True)
        raise
    finally:
        config.unlink(missing_ok=True)


def list_page(contents, previous):
    if len(contents) > 512 * 1024 or b"<!DOCTYPE" in contents or b"<!ENTITY" in contents:
        raise RecoveryError("Invalid restored S3 listing")
    try:
        root = ET.fromstring(contents)
        if root.tag != NAMESPACE + "ListBucketResult" or root.findtext(NAMESPACE + "EncodingType") != "url":
            raise ValueError()
        truncated = root.findtext(NAMESPACE + "IsTruncated")
        if truncated not in ("true", "false"):
            raise ValueError()
        items = []
        for node in root.findall(NAMESPACE + "Contents"):
            encoded = node.findtext(NAMESPACE + "Key") or ""
            if re.search(r"%(?![0-9A-Fa-f]{2})", encoded):
                raise ValueError()
            key = unquote_to_bytes(encoded).decode("utf-8")
            size = int(node.findtext(NAMESPACE + "Size"))
            # curl's SigV4 signer rejects --path-as-is. Refuse standalone dot
            # segments rather than let URL normalization select another object.
            if (not key or len(key.encode()) > 1024 or "\x00" in key or any(part in (".", "..") for part in key.split("/"))
                    or not 0 <= size <= MAX_OBJECT or len(items) >= 100
                    or key.encode() <= previous.encode()):
                raise ValueError()
            previous = key
            items.append((key, size))
        if int(root.findtext(NAMESPACE + "KeyCount")) != len(items) or (truncated == "true" and not items):
            raise ValueError()
        return items, truncated == "true"
    except (ET.ParseError, ValueError, TypeError, UnicodeError):
        raise RecoveryError("Invalid or non-progressing restored S3 listing") from None


def scan_storage(endpoint, secrets, directory, log, inventory, *, expected=None):
    """One finite inventory of exact bytes; compare an existing inventory when given."""
    deadline = time.monotonic() + 600
    count = total = 0
    previous = ""
    complete = False
    created = False
    expected_file = expected.open("rb") if expected is not None else None
    try:
        with create_file(inventory) as output:
            created = True
            header = b'{"format":1}\n'
            if expected_file is not None and expected_file.readline(128) != header:
                raise RecoveryError("Invalid checkpoint inventory header")
            output.write(header)
            while True:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise RecoveryError("Storage verification exceeded its total deadline")
                target = "/" + secrets["bucket"] + "/?list-type=2&max-keys=100&encoding-type=url"
                if previous:
                    target += "&start-after=" + quote(previous, safe="")
                response = s3_get(endpoint, target, secrets, directory, log, limit=512 * 1024, timeout=min(30, remaining))
                try:
                    items, more = list_page(response.read_bytes(), previous)
                finally:
                    response.unlink()
                for key, size in items:
                    if count >= MAX_OBJECTS or total + size > MAX_CONTENT:
                        raise RecoveryError("Storage verification exceeded its inventory limit")
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise RecoveryError("Storage verification exceeded its total deadline")
                    response = s3_get(endpoint, "/" + secrets["bucket"] + "/" + quote(key, safe="/"),
                                      secrets, directory, log, limit=max(1, size), timeout=min(60, remaining))
                    try:
                        if response.stat().st_size != size:
                            raise RecoveryError("Restored object length differs from its metadata")
                        record = (json.dumps({"key": key, "bytes": size, "sha256": sha256(response)},
                                             ensure_ascii=True, separators=(",", ":")) + "\n").encode()
                    finally:
                        response.unlink()
                    if expected_file is not None and expected_file.readline(16 * 1024) != record:
                        raise RecoveryError("Restored object differs from the checkpoint inventory")
                    output.write(record)
                    count += 1
                    total += size
                    previous = key
                if not more:
                    break
            if expected_file is not None and expected_file.read(1):
                raise RecoveryError("Restored inventory is missing checkpoint objects")
            output.flush()
            os.fsync(output.fileno())
        complete = True
        return {"objects": count, "bytes": total, "inventorySha256": sha256(inventory)}
    finally:
        if expected_file is not None:
            expected_file.close()
        if created and not complete:
            inventory.unlink(missing_ok=True)
