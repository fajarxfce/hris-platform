"""Owned document services with the real ClamAV databases and updater."""

import json
from pathlib import Path
import secrets
import socket
import time

import database_recovery as docker
from recovery_storage import GARAGE_IMAGE, container_endpoint
from workload_fixture import ROOT, WorkloadFixture, write_environment

CLAM_IMAGE = "clamav/clamav:1.5.4_base"
HARDENING = ("--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges:true")


class DocumentWorkloadFixture(WorkloadFixture):
    # Include storage, loaded signatures AND the updating process in the budget.
    database_memory, api_memory, worker_memory, api_heap = "6g", "2g", "2g", "1g"

    def volume_for(self, role):
        volume = self.name + "-" + role
        self.extra_volumes.append(volume)
        self.command(["docker", "volume", "create", "--label", docker.LABEL + "=" + self.run_id, volume])
        return volume

    def service_environment(self):
        signatures = self.volume_for("signatures")
        outbound = self.name + "-updates"
        self.resources.append(("network", outbound))
        # Only the updater has outbound access. No service publishes a host port.
        self.command(["docker", "network", "create", "--label", docker.LABEL + "=" + self.run_id, outbound])
        for role in ("updater", "scanner"):
            write_environment(self.directory / (role + ".env"), {})
        self.run("updater", CLAM_IMAGE,
            ("--daemon", "--foreground", "--stdout", "--config-file=/etc/clamav/freshclam.conf"),
            memory="2g", network=outbound, extra=(*HARDENING, "--user=100:101", "--entrypoint=/usr/bin/freshclam",
                "--tmpfs=/tmp:size=32m,mode=1777",
                "--mount", f"type=volume,source={signatures},target=/var/lib/clamav",
                "--mount", f"type=bind,source={ROOT / 'deploy/clamav/freshclam.conf'},target=/etc/clamav/freshclam.conf,readonly"))
        deadline, progress = time.monotonic() + 600, time.monotonic()
        while time.monotonic() < deadline:
            logs = docker.captured(["docker", "logs", "--tail", "30", self.containers["updater"]], self.log)
            if any(message in logs for message in ("429", "403", "cool-down", "cooldown")):
                raise RuntimeError("Signature provider refused this update; respect its cooldown before another run")
            if "bytecode.cvd updated" in logs or "bytecode.cvd database is up-to-date" in logs:
                break
            if docker.captured(["docker", "inspect", "--format", "{{.State.Running}}", self.containers["updater"]], self.log) != "true":
                raise RuntimeError("Signature updater exited before initial synchronization")
            if time.monotonic() >= progress:
                print(json.dumps({"preparation": "fullSignatureSynchronization"}), flush=True)
                progress = time.monotonic() + 30
            time.sleep(1)
        else:
            raise RuntimeError("Initial signature synchronization exceeded ten minutes")
        self.signature_info = {}
        for database in ("main", "daily", "bytecode"):
            # Fresh named volumes receive the official CVD files, no local test signatures.
            metadata = docker.captured(["docker", "exec", self.containers["updater"],
                "sigtool", "--info", "/var/lib/clamav/" + database + ".cvd"], self.log)
            selected = dict(line.split(": ", 1) for line in metadata.splitlines() if ": " in line)
            self.signature_info[database] = {key: selected[key] for key in ("Build time", "Version", "Signatures")}
        self.run("scanner", CLAM_IMAGE, ("--foreground", "--config-file=/etc/clamav/clamd.conf"),
            memory="3g", extra=(*HARDENING, "--user=100:101", "--entrypoint=/usr/sbin/clamd",
                "--tmpfs=/tmp:size=768m,mode=1777",
                "--mount", f"type=volume,source={signatures},target=/var/lib/clamav,readonly",
                "--mount", f"type=bind,source={ROOT / 'deploy/clamav/clamd.conf'},target=/etc/clamav/clamd.conf,readonly"))
        deadline = time.monotonic() + 180
        address = container_endpoint(self.containers["scanner"], self.name, 3310, self.log).split("//", 1)[1]
        host, port = address.rsplit(":", 1)
        while time.monotonic() < deadline:
            try:
                with socket.create_connection((host, int(port)), timeout=2) as connection:
                    connection.sendall(b"zVERSION\0")
                    version = connection.recv(1024).rstrip(b"\0").decode("ascii")
                    if version.startswith("ClamAV 1.5.4/"):
                        self.scanner_version = version
                        break
            except OSError:
                pass
            if docker.captured(["docker", "inspect", "--format", "{{.State.Running}}", self.containers["scanner"]], self.log) != "true":
                raise RuntimeError("Full-signature scanner exited before readiness")
            time.sleep(1)
        else:
            raise RuntimeError("Full-signature scanner readiness exceeded three minutes")
        access_key, secret_key = "GK" + secrets.token_hex(16), secrets.token_hex(32)
        bucket = "hris-document-workload"
        write_environment(self.directory / "garage.env", {
            "GARAGE_RPC_SECRET": secrets.token_hex(32), "GARAGE_DEFAULT_ACCESS_KEY": access_key,
            "GARAGE_DEFAULT_SECRET_KEY": secret_key, "GARAGE_DEFAULT_BUCKET": bucket,
        })
        metadata, content = self.volume_for("metadata"), self.volume_for("content")
        self.run("garage", GARAGE_IMAGE, ("/garage", "server", "--single-node", "--default-bucket"), memory="1g",
            extra=(*HARDENING, "--tmpfs=/tmp:size=64m,mode=1777",
                "--mount", f"type=volume,source={metadata},target=/var/lib/garage/meta",
                "--mount", f"type=volume,source={content},target=/var/lib/garage/data",
                "--mount", f"type=bind,source={ROOT / 'deploy/garage/garage.toml'},target=/etc/garage.toml,readonly"))
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            result = self.command(["docker", "exec", self.containers["garage"], "/garage", "bucket", "list"], allow_failure=True, timeout=5)
            if result.returncode == 0 and bucket.encode() in result.stdout:
                break
            time.sleep(.5)
        else:
            raise RuntimeError("Document workload storage readiness timed out")
        return {"HRIS_STORAGE_ENABLED": "true", "HRIS_STORAGE_ENDPOINT": "http://garage:3900",
            "HRIS_STORAGE_REGION": "garage", "HRIS_STORAGE_BUCKET": bucket, "HRIS_STORAGE_ACCESS_KEY": access_key,
            "HRIS_STORAGE_SECRET_KEY": secret_key, "HRIS_STORAGE_ALLOW_PLAINTEXT": "true",
            "HRIS_DOCUMENT_SCANNER_ENABLED": "true", "HRIS_DOCUMENT_SCANNER_HOST": "scanner", "HRIS_DOCUMENT_SCANNER_PORT": "3310"}

    def cgroup_counters(self, name, info):
        # Garage is a scratch image without cat. Read this owned process's Linux
        # cgroup directly; the benchmark already requires Linux/private bridge access.
        groups = Path("/proc") / str(info["State"]["Pid"]) / "cgroup"
        unified = [line[3:] for line in groups.read_text().splitlines() if line.startswith("0::/")]
        if len(unified) != 1:
            raise RuntimeError("Document measurement requires one cgroup v2 hierarchy")
        root = Path("/sys/fs/cgroup").resolve()
        path = (root / unified[0].lstrip("/")).resolve()
        if not path.is_relative_to(root) or path == root:
            raise RuntimeError("Owned service cgroup path could not be established")
        return {file: (path / file).read_text().strip()
                for file in ("memory.current", "memory.peak", "memory.events", "cpu.stat")}

    def close(self):
        try:
            for role, container in self.containers.items():
                with docker.create_file(self.directory / (role + ".log")) as output:
                    docker.command(["docker", "logs", "--tail", "150", container], output, stdout=output, allow_failure=True)
        finally:
            super().close()
