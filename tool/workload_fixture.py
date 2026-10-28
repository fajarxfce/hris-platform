"""Own a disposable four-CPU API/worker/PostgreSQL deployment for measurements."""

import base64
from contextlib import contextmanager
import json
import os
from pathlib import Path
import secrets
import shutil
import tempfile
import time
import uuid

import database_recovery as docker
from recovery_storage import container_endpoint

ROOT = Path(__file__).resolve().parents[1]


def write_environment(path, values):
    with docker.create_file(path) as output:
        output.write("".join(name + "=" + value + "\n" for name, value in values.items()).encode())


class WorkloadFixture:
    def __init__(self, directory, log, run_id, cpus):
        self.directory, self.log, self.run_id, self.cpus = directory, log, run_id, cpus
        self.name = "hris-workload-" + run_id
        self.resources, self.containers = [], {}
        self.volume = None
        self.retain_diagnostics = False

    def command(self, args, **kwargs):
        return docker.command(args, self.log, **kwargs)

    def run(self, role, image, arguments=(), *, extra=(), memory):
        name = self.name + "-" + role
        self.resources.append(("container", name))
        self.command([
            "docker", "run", "--detach", "--name", name, "--network", self.name,
            "--network-alias", role, "--label", docker.LABEL + "=" + self.run_id,
            "--cpuset-cpus", ",".join(map(str, self.cpus)), "--memory", memory,
            "--memory-swap", memory, "--pids-limit=512", "--init",
            "--env-file", str(self.directory / (role + ".env")),
            *extra, image, *arguments,
        ], timeout=90)
        self.containers[role] = name
        return name

    def sql(self, source, *, timeout=60):
        # Fixture input and returned data never enter command arguments or stdout.
        return self.command([
            "docker", "exec", "-i", "--env", "PGOPTIONS=-c statement_timeout=45000 -c lock_timeout=5000",
            self.containers["database"], "psql", "-X", "-qAt", "-U", "hris_migrator",
            "-d", "hris", "-v", "ON_ERROR_STOP=1",
        ], input=source.encode(), timeout=timeout).stdout.decode().strip()

    def start(self):
        self.resources.append(("network", self.name))
        self.command(["docker", "network", "create", "--internal", "--label", docker.LABEL + "=" + self.run_id, self.name])
        self.volume = self.name + "-data"
        self.command(["docker", "volume", "create", "--label", docker.LABEL + "=" + self.run_id, self.volume])
        passwords = {name: secrets.token_urlsafe(24) for name in ("migration", "api", "worker")}
        write_environment(self.directory / "database.env", {
            "POSTGRES_DB": "hris", "POSTGRES_USER": "hris_migrator", "POSTGRES_PASSWORD": passwords["migration"],
            "HRIS_DATABASE_PASSWORD": passwords["api"], "HRIS_WORKER_DATABASE_PASSWORD": passwords["worker"],
        })
        self.run("database", "postgres:18.6-alpine",
            ("postgres", "-c", "shared_buffers=2GB", "-c", "work_mem=16MB", "-c", "effective_cache_size=6GB"),
            memory="8g", extra=(
                "--mount", f"type=volume,source={self.volume},target=/var/lib/postgresql",
                "--mount", f"type=bind,source={ROOT / 'deploy/postgres/init-runtime.sh'},target=/docker-entrypoint-initdb.d/10-runtime.sh,readonly",
                "--mount", f"type=bind,source={ROOT / 'deploy/postgres/configure-worker.sh'},target=/docker-entrypoint-initdb.d/20-worker.sh,readonly"))
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            ready = self.command(["docker", "exec", self.containers["database"], "pg_isready", "-h", "127.0.0.1", "-U", "hris_migrator"], allow_failure=True, timeout=5)
            if ready.returncode == 0:
                break
            time.sleep(0.25)
        else:
            raise RuntimeError("Workload database readiness timed out")
        self.bootstrap_password = secrets.token_urlsafe(32)
        common = {
            "HRIS_DATABASE_URL": "jdbc:postgresql://database:5432/hris", "HRIS_DATABASE_USER": "hris_app",
            "HRIS_DATABASE_PASSWORD": passwords["api"], "HRIS_WORKER_DATABASE_USER": "hris_worker",
            "HRIS_WORKER_DATABASE_PASSWORD": passwords["worker"],
            "HRIS_IDENTITY_ACTIVE_KEY": "fixture", "HRIS_IDENTITY_KEYS": "fixture:" + base64.b64encode(secrets.token_bytes(32)).decode(),
            "HRIS_SYNC_ACTIVE_KEY": "fixture", "HRIS_SYNC_KEYS": "fixture:" + base64.b64encode(secrets.token_bytes(32)).decode(),
            "HRIS_STORAGE_ENABLED": "false", "HRIS_MAIL_ENABLED": "false", "HRIS_FCM_ENABLED": "false",
            "HRIS_FIREBASE_AUTH_ENABLED": "false", "HRIS_OIDC_ENABLED": "false", "HRIS_DOCUMENT_SCANNER_ENABLED": "false",
        }
        write_environment(self.directory / "api.env", common | {
            "HRIS_MIGRATIONS_ENABLED": "true", "HRIS_MIGRATION_USER": "hris_migrator",
            "HRIS_MIGRATION_PASSWORD": passwords["migration"], "HRIS_BOOTSTRAP_EMAIL": "workload-admin@example.invalid",
            "HRIS_BOOTSTRAP_PASSWORD": self.bootstrap_password, "HRIS_SECURE_COOKIES": "false",
        })
        write_environment(self.directory / "worker.env", common)
        self.run("api", "eclipse-temurin:21-jre", ("java", "-Xmx2g", "-jar", "/app.jar"), memory="4g",
                 extra=("--mount", f"type=bind,source={ROOT / 'apps/server/build/libs/apps-server.jar'},target=/app.jar,readonly"))
        self.endpoint = container_endpoint(self.containers["api"], self.name, 8080, self.log)

    def start_worker(self):
        self.run("worker", "eclipse-temurin:21-jre", ("java", "-Xmx1g", "-jar", "/app.jar"), memory="4g",
                 extra=("--mount", f"type=bind,source={ROOT / 'apps/worker/build/libs/apps-worker.jar'},target=/app.jar,readonly"))
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            path = self.directory / "worker-startup.log"
            try:
                with docker.create_file(path) as output:
                    docker.command(["docker", "logs", "--tail", "100", self.containers["worker"]], output, stdout=output)
                if path.stat().st_size > 256 * 1024:
                    raise RuntimeError("Worker readiness output exceeded its limit")
                if "Started WorkerApplicationKt" in path.read_text():
                    return
            finally:
                path.unlink(missing_ok=True)
            if docker.captured(["docker", "inspect", "--format", "{{.State.Running}}", self.containers["worker"]], self.log) != "true":
                raise RuntimeError("Workload worker exited before readiness")
            time.sleep(0.5)
        raise RuntimeError("Workload worker readiness timed out")

    def observations(self):
        result = {}
        for role, name in self.containers.items():
            info = json.loads(docker.captured(["docker", "inspect", name], self.log))[0]
            counters = {}
            for file in ("memory.current", "memory.peak", "memory.events", "cpu.stat"):
                counters[file] = docker.captured(["docker", "exec", name, "cat", "/sys/fs/cgroup/" + file], self.log)
            result[role] = {"image": info["Image"], "cpuSet": info["HostConfig"]["CpusetCpus"],
                            "memoryLimit": info["HostConfig"]["Memory"], "running": info["State"]["Running"],
                            "oomKilled": info["State"]["OOMKilled"], "cgroup": counters}
        return result

    def close(self):
        # Do not remove the volume if any container's removal is unconfirmed.
        docker.cleanup_owned(self.resources, self.run_id, self.log)
        if self.volume is not None:
            inspected = self.command(["docker", "volume", "inspect", "--format", '{{index .Labels "' + docker.LABEL + '"}}', self.volume], allow_failure=True)
            if inspected.returncode:
                present = docker.captured(["docker", "volume", "ls", "--filter", "name=" + self.volume, "--format", "{{.Name}}"], self.log)
                if present:
                    raise RuntimeError("Workload volume removal could not be confirmed")
            elif inspected.stdout.decode().strip() != self.run_id:
                raise RuntimeError("Refused to remove another run's volume")
            else:
                self.command(["docker", "volume", "rm", self.volume])


@contextmanager
def workload_fixture(cpus):
    original_affinity = os.sched_getaffinity(0)
    if len(cpus) != 4 or len(set(cpus)) != 4 or not set(cpus) <= original_affinity:
        raise ValueError("Select exactly four available CPUs")
    for role, artifact in (("server", "apps-server.jar"), ("worker", "apps-worker.jar")):
        if not (ROOT / "apps" / role / "build/libs" / artifact).is_file():
            raise ValueError("Build both application JARs before the workload exercise")
    run_id = uuid.uuid4().hex
    directory = Path(tempfile.mkdtemp(prefix="hris-workload-" + run_id + "-"))
    passed = False
    with docker.create_file(directory / "fixture.log") as log:
        fixture = WorkloadFixture(directory, log, run_id, cpus)
        try:
            fixture.start()
            driver_cpus = original_affinity - set(cpus)
            if driver_cpus:
                os.sched_setaffinity(0, driver_cpus)
            yield fixture
            passed = True
        finally:
            try:
                fixture.close()
                if passed and not fixture.retain_diagnostics:
                    shutil.rmtree(directory)
                else:
                    print("Private workload diagnostics retained at " + str(directory), flush=True)
            finally:
                os.sched_setaffinity(0, original_affinity)
