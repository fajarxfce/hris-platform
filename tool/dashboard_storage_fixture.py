"""Own an optional private Garage/S3 fixture; never borrow existing containers."""

import json
from pathlib import Path
import re
import secrets
import subprocess
import time

from dashboard_storage_faults import StorageFaultProxy


class DashboardStorageFixture:
    def __init__(self, root: Path, directory: Path, docker: list[str], *, faults=False):
        self.docker = docker
        self.container = None
        self.faults = None
        self.environment_file = directory / "storage.env"
        self.metadata_file = directory / "storage.json"
        self.bucket = "hris-browser-exports"
        access = "GK" + secrets.token_hex(16)
        secret = secrets.token_hex(32)
        try:
            self.environment_file.touch(mode=0o600, exist_ok=True)
            self.environment_file.chmod(0o600)
            self.environment_file.write_text(
                f"GARAGE_RPC_SECRET={secrets.token_hex(32)}\n"
                f"GARAGE_DEFAULT_ACCESS_KEY={access}\nGARAGE_DEFAULT_SECRET_KEY={secret}\n"
                f"GARAGE_DEFAULT_BUCKET={self.bucket}\n"
            )
            # Retain ownership before Docker starts: its client may time out after creation.
            self.container = "hris-browser-storage-" + secrets.token_hex(16)
            created = self.command([
                "run", "-d", "--name", self.container,
                "--label", "dev.fajar.hris.fixture=dashboard-storage",
                "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges:true",
                "--memory=512m", "--pids-limit=128", "--tmpfs", "/tmp:size=32m",
                "--tmpfs", "/var/lib/garage/meta:size=64m", "--tmpfs", "/var/lib/garage/data:size=128m",
                "--mount", f"type=bind,src={root / 'deploy/garage/garage.toml'},dst=/etc/garage.toml,readonly",
                "--env-file", str(self.environment_file), "-p", "127.0.0.1::3900",
                "dxflrs/garage:v2.4.1", "/garage", "server", "--single-node", "--default-bucket",
            ])
            if not re.fullmatch(r"[0-9a-f]{12,64}", created):
                raise RuntimeError("Unexpected storage fixture identifier")
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                ready = subprocess.run(docker + ["exec", created, "/garage", "bucket", "list"],
                                       capture_output=True, text=True, timeout=5)
                if ready.returncode == 0 and self.bucket in ready.stdout:
                    break
                if self.command(["inspect", "--format", "{{.State.Running}}", created]) != "true":
                    raise RuntimeError("Storage fixture exited during startup")
                time.sleep(0.25)
            else:
                raise RuntimeError("Storage fixture startup timed out")
            binding = self.command(["port", created, "3900/tcp"])
            if not re.fullmatch(r"127\.0\.0\.1:\d+", binding):
                raise RuntimeError("Unexpected storage fixture binding")
            self.endpoint = f"http://{binding}"
            if faults:
                self.faults = StorageFaultProxy(self.endpoint, directory)
                self.endpoint = self.faults.endpoint
            self.environment = {
                "HRIS_STORAGE_ENABLED": "true", "HRIS_STORAGE_ENDPOINT": self.endpoint,
                "HRIS_STORAGE_REGION": "garage", "HRIS_STORAGE_BUCKET": self.bucket,
                "HRIS_STORAGE_ACCESS_KEY": access, "HRIS_STORAGE_SECRET_KEY": secret,
                "HRIS_STORAGE_ALLOW_PLAINTEXT": "true",
            }
            # Acceptance can probe anonymous access without acquiring provider credentials.
            self.metadata_file.write_text(json.dumps({"endpoint": self.endpoint, "bucket": self.bucket}))
        except BaseException:
            self.close()
            raise

    def command(self, arguments: list[str]) -> str:
        return subprocess.run(self.docker + arguments, capture_output=True, text=True,
                              timeout=60, check=True).stdout.strip()

    def close(self):
        try:
            if self.faults is not None:
                self.faults.close()
                self.faults = None
        finally:
            self.close_storage()

    def close_storage(self):
        try:
            if self.container is not None:
                removed = subprocess.run(self.docker + ["rm", "-f", self.container],
                                         capture_output=True, text=True, timeout=20)
                if removed.returncode and f"No such container: {self.container}" not in removed.stderr:
                    raise RuntimeError("Storage fixture cleanup failed")
                self.container = None
        finally:
            self.environment_file.unlink(missing_ok=True)
            self.metadata_file.unlink(missing_ok=True)
