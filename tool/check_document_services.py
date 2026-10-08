#!/usr/bin/env python3
"""Exercise deployment configuration against owned, temporary containers only."""
from pathlib import Path
import hashlib
import json
import os
import socket
import struct
import subprocess
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]


def command(*args, timeout=20, env=None):
    return subprocess.run(args, cwd=ROOT, env=env, check=True, text=True,
                          capture_output=True, timeout=timeout).stdout.strip()


def wait_healthy(name, health):
    deadline = time.monotonic() + 60
    args = health[1:] if health[0] == "CMD" else ["/bin/sh", "-c", health[1]]
    while time.monotonic() < deadline:
        state = json.loads(command("docker", "inspect", name, timeout=5))[0]["State"]
        if not state["Running"]:
            raise RuntimeError(f"Service verification container exited: {name}")
        try:
            command("docker", "exec", name, *args, timeout=5)
            return
        except subprocess.CalledProcessError:
            time.sleep(0.5)
    raise TimeoutError(f"Service health deadline exceeded: {name}")


def frame(connection):
    value = bytearray()
    deadline = time.monotonic() + 5
    while len(value) < 4096:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("Scanner response deadline exceeded")
        connection.settimeout(remaining)
        received = connection.recv(min(256, 4096 - len(value)))
        if not received:
            raise RuntimeError("Scanner closed before its response")
        value.extend(received)
        if b"\x00" in value:
            return bytes(value).split(b"\x00", 1)[0].decode("ascii")
    raise RuntimeError("Scanner response exceeded its limit")


def scan(port, content):
    with socket.create_connection(("127.0.0.1", port), timeout=5) as connection:
        connection.settimeout(5)
        connection.sendall(b"zINSTREAM\x00" + struct.pack("!I", len(content)) + content + b"\x00" * 4)
        return frame(connection)


def main():
    env = dict(os.environ)
    env.update({
        "HRIS_MIGRATION_PASSWORD": "service-fixture-only", "HRIS_DATABASE_PASSWORD": "service-fixture-only",
        "HRIS_WORKER_DATABASE_PASSWORD": "service-fixture-only", "HRIS_IDENTITY_KEYS": "fixture-only",
        "HRIS_BOOTSTRAP_EMAIL": "admin@example.test", "HRIS_BOOTSTRAP_PASSWORD": "fixture-only",
        "HRIS_GARAGE_RPC_SECRET": "0123456789abcdef" * 4,
        "HRIS_STORAGE_ACCESS_KEY": "GK0123456789abcdef0123456789abcdef",
        "HRIS_STORAGE_SECRET_KEY": "0123456789abcdef" * 4,
        "HRIS_STORAGE_BUCKET": "hris-service-fixture",
    })
    specification = json.loads(command("docker", "compose", "--env-file", ".env.example", "-f", "compose.yml",
                                       "-f", "compose.documents.yml", "config", "--format", "json", env=env))
    services = specification["services"]
    for name in ("garage", "antivirus", "antivirus-update"):
        assert not services[name].get("ports"), f"Private service exposes host ports: {name}"
    suffix = uuid.uuid4().hex
    garage = f"hris-garage-check-{suffix}"
    antivirus = f"hris-antivirus-check-{suffix}"
    created = []
    temporary = None
    try:
        service = services["garage"]
        created.append(garage)
        command("docker", "run", "--detach", "--label", f"hris.verify-run={suffix}", "--name", garage, "--read-only", "--cap-drop=ALL",
                "--security-opt=no-new-privileges:true", "--tmpfs", "/tmp:size=64m",
                "--tmpfs", "/var/lib/garage/meta:size=64m", "--tmpfs", "/var/lib/garage/data:size=64m",
                "--mount", f"type=bind,source={ROOT / 'deploy/garage/garage.toml'},target=/etc/garage.toml,readonly",
                "--env", f"GARAGE_RPC_SECRET={env['HRIS_GARAGE_RPC_SECRET']}",
                "--env", f"GARAGE_DEFAULT_ACCESS_KEY={env['HRIS_STORAGE_ACCESS_KEY']}",
                "--env", f"GARAGE_DEFAULT_SECRET_KEY={env['HRIS_STORAGE_SECRET_KEY']}",
                "--env", f"GARAGE_DEFAULT_BUCKET={env['HRIS_STORAGE_BUCKET']}",
                service["image"], *service["command"], timeout=60)
        wait_healthy(garage, service["healthcheck"]["test"])
        buckets = command("docker", "exec", garage, "/garage", "bucket", "list")
        assert env["HRIS_STORAGE_BUCKET"] in buckets, "The configured private bucket was not initialized"
        print("Garage configuration, health, and private bucket initialization passed", flush=True)
        temporary = tempfile.TemporaryDirectory(prefix="hris-signature-fixture-")
        directory = temporary.name
        fixture = Path(directory)
        fixture.chmod(0o755)
        eicar = b"X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
        digest = hashlib.md5(eicar, usedforsecurity=False).hexdigest()
        signature = fixture / "owned-fixture.hdb"
        signature.write_text(f"{digest}:{len(eicar)}:Hris.OwnedFixture\n")
        signature.chmod(0o644)
        service = services["antivirus"]
        created.append(antivirus)
        command("docker", "run", "--detach", "--label", f"hris.verify-run={suffix}", "--name", antivirus, "--read-only", "--cap-drop=ALL",
                "--security-opt=no-new-privileges:true", "--user", service["user"],
                "--tmpfs", service["tmpfs"][0], "--memory", str(service["mem_limit"]), "--publish", "127.0.0.1::3310",
                "--mount", f"type=bind,source={ROOT / 'deploy/clamav/clamd.conf'},target=/etc/clamav/clamd.conf,readonly",
                "--mount", f"type=bind,source={ROOT / 'deploy/clamav/freshclam.conf'},target=/etc/clamav/freshclam.conf,readonly",
                "--mount", f"type=bind,source={fixture},target=/var/lib/clamav,readonly",
                 "--entrypoint", service["entrypoint"][0], service["image"], *service["command"], timeout=60)
        wait_healthy(antivirus, service["healthcheck"]["test"])
        info = json.loads(command("docker", "inspect", antivirus))[0]
        port = int(info["NetworkSettings"]["Ports"]["3310/tcp"][0]["HostPort"])
        assert scan(port, b"%PDF-1.7\n%%EOF") == "stream: OK"
        assert scan(port, eicar).endswith(" FOUND")
        configuration = command("docker", "exec", antivirus, "clamconf", "--non-default", "--config-dir=/etc/clamav")
        assert "ERROR" not in configuration, "Daemon/updater configuration was rejected"
        assert 'MaxScanTime = "60000"' in configuration, "The scan budget was not applied"
        print("ClamAV non-root/read-only configuration, health, bounded scan, and EICAR fixture passed", flush=True)
        print("Live signature downloading and update scheduling were not exercised", flush=True)
    except Exception:
        for name in created:
            logs = subprocess.run(["docker", "logs", "--tail", "100", name], cwd=ROOT,
                                  text=True, capture_output=True, timeout=10, check=False)
            target = ROOT / ".work" / f"{name}.log"
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(logs.stdout + logs.stderr)
        raise
    finally:
        # Labels prevent removal of an unrelated container, including after a timed-out run request.
        for name in reversed(created):
            inspected = subprocess.run(["docker", "inspect", name], cwd=ROOT, text=True,
                                       capture_output=True, timeout=10, check=False)
            if inspected.returncode == 0:
                labels = json.loads(inspected.stdout)[0]["Config"].get("Labels") or {}
                if labels.get("hris.verify-run") == suffix:
                    command("docker", "rm", "--force", name, timeout=15)
        if temporary is not None:
            temporary.cleanup()


if __name__ == "__main__":
    main()
