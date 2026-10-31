#!/usr/bin/env python3
"""Bounded real-provider proof on owned loopback containers and fictional users.

Run as the normal workspace user with Docker group access. This never reads HRIS
runtime secrets or modifies existing services. Private artifacts remain in .work;
the printed result only references a sanitized evidence file.
"""
from __future__ import annotations

import base64
import argparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROOF = ROOT / "tool/collaboration-proof"
MAX_HTTP_BYTES = 2 * 1024 * 1024
DIAGNOSTICS = None
DOCKER_COMMAND = ["docker"]


def command(*arguments, timeout=30, cwd=ROOT, env=None):
    if arguments[0] == "docker":
        arguments = (*DOCKER_COMMAND, *arguments[1:])
    result = subprocess.run(arguments, cwd=cwd, env=env, capture_output=True,
                            text=True, timeout=timeout)
    if result.returncode:
        # Arguments may contain short-lived fixture credentials. Do not expose
        # CalledProcessError's command representation in logs/tracebacks.
        if DIAGNOSTICS:
            with DIAGNOSTICS.open("a") as log:
                log.write(f"{arguments[0]} exit={result.returncode}\n{result.stderr}\n")
        raise RuntimeError(f"{arguments[0]} operation failed (exit {result.returncode})")
    return result.stdout.strip()


def write_private(path, value):
    with open(path, "w", opener=lambda name, flags: os.open(name, flags, 0o600)) as stream:
        json.dump(value, stream, indent=2)
        stream.write("\n")


def http(url, data=None, *, token=None, method=None, accepted=(200,)):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = None if data is None else json.dumps(data).encode()
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=8)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        content = response.read(MAX_HTTP_BYTES + 1)
        if len(content) > MAX_HTTP_BYTES:
            raise RuntimeError("Provider response exceeded the proof limit")
        if response.status not in accepted:
            raise RuntimeError(f"Provider HTTP status {response.status}")
        return response.status, json.loads(content) if content else {}


def jwt(secret, *, subject, issuer="urn:hris:collaboration:proof", audience="matrix:proof", expiry=None):
    def encoded(value):
        return base64.urlsafe_b64encode(json.dumps(value, separators=(",", ":")).encode()).rstrip(b"=")
    now = int(time.time())
    unsigned = encoded({"alg": "HS256", "typ": "JWT"}) + b"." + encoded({
        "iss": issuer, "aud": audience, "sub": subject,
        "iat": now, "nbf": now - 1, "exp": expiry if expiry is not None else now + 120,
    })
    signature = base64.urlsafe_b64encode(hmac.new(secret.encode(), unsigned, hashlib.sha256).digest()).rstrip(b"=")
    return (unsigned + b"." + signature).decode()


class ProviderProof:
    def __init__(self):
        self.run_id = uuid.uuid4().hex
        self.directory = ROOT / ".work/collaboration" / self.run_id
        self.directory.mkdir(parents=True, mode=0o700)
        self.images = json.loads((PROOF / "images.json").read_text())
        self.network = None
        self.containers = []
        self.record = {"schemaVersion": 1, "runId": self.run_id,
                       "kind": "isolated_provider_proof", "complete": False,
                       "productAcceptance": False, "capacityAcceptance": False,
                       "images": {}, "checks": {}, "cleanup": False}
        sources = [Path(__file__).resolve(), ROOT / "tool/test_collaboration_providers.py",
                   *[PROOF / name for name in ("package.json", "package-lock.json", "images.json",
                     "index.html", "playwright.config.mjs", "src/probe.js", "tests/providers.spec.mjs")]]
        self.record["sourceSha256"] = {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
                                       for path in sources}

    def own(self, role, image, *options, arguments=()):
        name = f"hris-collab-proof-{self.run_id}-{role}"
        # Record the unique name before creation to cover interrupted responses.
        self.containers.append(name)
        return command("docker", "run", "--detach", "--name", name,
                       "--label", f"hris.collaboration-proof={self.run_id}",
                       "--network", self.network, "--network-alias", role,
                       "--security-opt", "no-new-privileges:true", "--pids-limit", "256",
                       "--log-opt", "max-size=5m", "--log-opt", "max-file=1",
                       *options, image, *arguments, timeout=45)

    def wait(self, condition, seconds=75):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            try:
                if condition():
                    return
            except (RuntimeError, OSError, ValueError, subprocess.TimeoutExpired):
                pass
            time.sleep(0.3)
        raise TimeoutError("Provider readiness deadline exceeded")

    def port(self, container, port):
        state = json.loads(command("docker", "inspect", container))[0]
        bindings = state["NetworkSettings"].get("Ports", {}).get(f"{port}/tcp")
        if not bindings or len(bindings) != 1 or bindings[0]["HostIp"] != "127.0.0.1":
            write_private(self.directory / "port-failure.json", {
                "status": state["State"]["Status"], "error": state["State"]["Error"],
                "bindings": state["NetworkSettings"].get("Ports"),
                "requested": state["HostConfig"].get("PortBindings"),
            })
            raise RuntimeError("Provider loopback port was not published")
        return int(bindings[0]["HostPort"])

    def start(self):
        for role, reference in self.images.items():
            try:
                metadata = json.loads(command("docker", "image", "inspect", reference))[0]
            except RuntimeError:
                print(f"Pulling pinned {role} version", flush=True)
                command("docker", "pull", reference, timeout=300)
                metadata = json.loads(command("docker", "image", "inspect", reference))[0]
            self.record["images"][role] = {"reference": reference, "id": metadata["Id"],
                                            "digests": metadata.get("RepoDigests", [])}

        self.network = f"hris-collab-proof-{self.run_id}"
        command("docker", "network", "create", "--label",
                f"hris.collaboration-proof={self.run_id}", self.network)
        database_password = secrets.token_urlsafe(32)
        self.own("postgres", self.images["postgres"], "--memory", "512m", "--cpus", "1",
                 "--env", "POSTGRES_USER=matrix", "--env", f"POSTGRES_PASSWORD={database_password}",
                 "--env", "POSTGRES_DB=matrix", "--env",
                 "POSTGRES_INITDB_ARGS=--encoding=UTF8 --lc-collate=C --lc-ctype=C",
                 "--tmpfs", "/var/lib/postgresql:rw,size=256m")
        self.wait(lambda: command("docker", "exec", self.containers[0], "pg_isready", "-U", "matrix", "-d", "matrix") != "")

        shared_secret = secrets.token_urlsafe(32)
        jwt_secret = secrets.token_urlsafe(48)
        synapse_data = self.directory / "synapse"
        synapse_data.mkdir(mode=0o700)
        config = {
            "server_name": "proof.invalid", "public_baseurl": "http://localhost/",
            "report_stats": False, "enable_registration": False,
            "registration_shared_secret": shared_secret,
            "password_config": {"enabled": False},
            "jwt_config": {"enabled": True, "secret": jwt_secret, "algorithm": "HS256",
                           "issuer": "urn:hris:collaboration:proof", "audiences": ["matrix:proof"]},
            "listeners": [{"port": 8008, "type": "http", "tls": False,
                           "bind_addresses": ["0.0.0.0"], "x_forwarded": False,
                           "resources": [{"names": ["client"], "compress": False}]}],
            "database": {"name": "psycopg2", "args": {"host": "postgres", "port": 5432,
                         "database": "matrix", "user": "matrix", "password": database_password,
                         "cp_min": 2, "cp_max": 5}},
            "media_store_path": "/data/media", "signing_key_path": "/data/signing.key",
            "macaroon_secret_key": secrets.token_urlsafe(32),
            "form_secret": secrets.token_urlsafe(32),
            "federation_domain_whitelist": [], "trusted_key_servers": [],
            "suppress_key_server_warning": True, "url_preview_enabled": False,
            "enable_metrics": False, "max_upload_size": "10M",
            "rc_message": {"per_second": 20, "burst_count": 100},
            "rc_login": {"address": {"per_second": 20, "burst_count": 100},
                         "account": {"per_second": 20, "burst_count": 100},
                         "failed_attempts": {"per_second": 20, "burst_count": 100}},
        }
        write_private(synapse_data / "homeserver.yaml", config)
        synapse_id = self.own("synapse", self.images["synapse"], "--memory", "1g", "--cpus", "2",
                             "--user", f"{os.getuid()}:{os.getgid()}",
                             "--publish", "127.0.0.1::8008",
                             "--mount", f"type=bind,source={synapse_data},target=/data",
                             "--entrypoint", "python",
                             arguments=("-m", "synapse.app.homeserver", "--config-path", "/data/homeserver.yaml"))
        matrix_url = f"http://127.0.0.1:{self.port(synapse_id, 8008)}"
        self.wait(lambda: http(matrix_url + "/_matrix/client/versions")[0] == 200)
        users = {}
        for name in ("alice", "bob"):
            _, nonce = http(matrix_url + "/_synapse/admin/v1/register")
            password = secrets.token_urlsafe(32)
            body = b"\x00".join(value.encode() for value in (nonce["nonce"], name, password, "notadmin"))
            mac = hmac.new(shared_secret.encode(), body, hashlib.sha1).hexdigest()
            http(matrix_url + "/_synapse/admin/v1/register", {
                "nonce": nonce["nonce"], "username": name, "password": password, "admin": False, "mac": mac,
            })
            _, session = http(matrix_url + "/_matrix/client/v3/login", {
                "type": "org.matrix.login.jwt", "token": jwt(jwt_secret, subject=name),
                "device_id": f"PROOF_{name.upper()}", "initial_device_display_name": "Fictional provider fixture",
            })
            users[name] = {"baseUrl": matrix_url, "userId": session["user_id"],
                           "deviceId": session["device_id"], "accessToken": session["access_token"]}
        for name, options in (("wrong_issuer", {"issuer": "urn:wrong"}),
                              ("wrong_audience", {"audience": "wrong"}),
                              ("expired_jwt_beyond_provider_leeway", {"expiry": int(time.time()) - 300})):
            status, _ = http(matrix_url + "/_matrix/client/v3/login", {
                "type": "org.matrix.login.jwt", "token": jwt(jwt_secret, subject="alice", **options),
            }, accepted=(403,))
            self.record["checks"][name] = status == 403
        # Synapse v1.164.1 validates JWTs with 120 seconds of clock leeway.
        # HRIS must enforce strict grant expiry before its internal exchange;
        # exposing this login directly would not enforce the HRIS deadline.
        status, _ = http(matrix_url + "/_matrix/client/v3/login", {
            "type": "org.matrix.login.jwt", "token": jwt(jwt_secret, subject="alice", expiry=int(time.time()) - 10),
            "device_id": "PROOF_EXPIRY_OBSERVATION",
        })
        self.record["observations"] = {"recent_expired_jwt_accepted_by_provider": status == 200,
                                       "strict_hris_broker_expiry_required": True}
        status, _ = http(matrix_url + "/_matrix/client/v3/register", {"username": "outsider", "password": "fictional-only"}, accepted=(403,))
        self.record["checks"]["public_registration_disabled"] = status == 403

        api_key = "proof-" + secrets.token_hex(8)
        api_secret = secrets.token_urlsafe(48)
        livekit_config = self.directory / "livekit.yaml"
        write_private(livekit_config, {
            "port": 7880, "bind_addresses": ["0.0.0.0"],
            "rtc": {"tcp_port": 7881, "udp_port": 7882, "use_external_ip": False,
                    "stun_servers": []},
            "keys": {api_key: api_secret},
            "logging": {"level": "warn"},
            "room": {"max_participants": 8, "empty_timeout": 30},
        })
        livekit_id = self.own("livekit", self.images["livekit"], "--memory", "512m", "--cpus", "2",
                             "--publish", "127.0.0.1::7880",
                             "--user", f"{os.getuid()}:{os.getgid()}",
                             "--mount", f"type=bind,source={livekit_config},target=/config.yaml,readonly",
                             arguments=("--config", "/config.yaml"))
        media_url = f"http://127.0.0.1:{self.port(livekit_id, 7880)}"
        self.wait(lambda: self.media_healthy(media_url))
        self.record["checks"]["loopback_only_http"] = True
        self.record["checks"]["isolated_dedicated_network"] = True
        fixture = self.directory / "fixture.json"
        write_private(fixture, {"matrix": {"baseUrl": matrix_url, **users},
                                "media": {"httpUrl": media_url, "wsUrl": media_url.replace("http:", "ws:"),
                                          "apiKey": api_key, "apiSecret": api_secret}})
        return fixture

    @staticmethod
    def media_healthy(url):
        with urllib.request.urlopen(url, timeout=3) as response:
            return response.status == 200

    def browser(self, fixture):
        with socket.socket() as reservation:
            reservation.bind(("127.0.0.1", 0))
            web_port = reservation.getsockname()[1]
        report = self.directory / "browser-report.json"
        environment = dict(os.environ, HRIS_COLLAB_PROOF_FIXTURE=str(fixture),
                           HRIS_COLLAB_PROOF_WEB_PORT=str(web_port),
                           HRIS_COLLAB_PROOF_REPORT=str(report))
        print("Running actual browser Matrix/media proof", flush=True)
        with (self.directory / "browser.log").open("w") as log:
            process = subprocess.Popen(["npm", "test"], cwd=PROOF, env=environment,
                                       stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                returncode = process.wait(timeout=240)
            finally:
                # npm can spawn Playwright/Vite grandchildren. Terminate only
                # this explicitly owned process group, including on timeout.
                try:
                    os.killpg(process.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    try:
                        os.killpg(process.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                    process.wait(timeout=5)
        if report.exists():
            stats = json.loads(report.read_text())["stats"]
            self.record["browser"] = {key: stats.get(key) for key in ("expected", "unexpected", "flaky", "skipped", "duration")}
        if returncode:
            raise RuntimeError("Browser provider proof failed; inspect the owned private browser log")
        self.record["checks"]["browser_e2ee_restart_media"] = True

    def cleanup(self):
        successful = True
        for name in reversed(self.containers):
            try:
                state = json.loads(command("docker", "inspect", name))[0]
            except RuntimeError:
                successful = False
                continue
            if (state["Config"].get("Labels") or {}).get("hris.collaboration-proof") != self.run_id:
                successful = False
                continue
            try:
                logs = subprocess.run([*DOCKER_COMMAND, "logs", "--tail", "1000", name],
                                      capture_output=True, text=True, timeout=10)
                (self.directory / f"{name.rsplit('-', 1)[-1]}.log").write_text(logs.stdout + logs.stderr)
            except (OSError, subprocess.TimeoutExpired):
                pass
            try:
                command("docker", "rm", "--force", name)
            except (RuntimeError, subprocess.TimeoutExpired):
                successful = False
        if self.network:
            try:
                state = json.loads(command("docker", "network", "inspect", self.network))[0]
                if (state.get("Labels") or {}).get("hris.collaboration-proof") != self.run_id:
                    successful = False
                else:
                    command("docker", "network", "rm", self.network)
            except (RuntimeError, subprocess.TimeoutExpired):
                successful = False
        self.record["cleanup"] = successful


def main():
    global DIAGNOSTICS, DOCKER_COMMAND
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--docker-sudo", action="store_true", help="Use existing noninteractive sudo authorization for Docker only")
    options = parser.parse_args()
    if options.docker_sudo:
        DOCKER_COMMAND = ["sudo", "-n", "docker"]
    os.umask(0o077)
    proof = ProviderProof()
    DIAGNOSTICS = proof.directory / "command-errors.log"
    started = time.monotonic()
    error = None
    try:
        if not (PROOF / "node_modules").is_dir():
            raise RuntimeError("Install pinned proof dependencies with npm ci first")
        proof.browser(proof.start())
        proof.record["complete"] = True
    except (RuntimeError, OSError, ValueError, TimeoutError, subprocess.TimeoutExpired) as failure:
        error = type(failure).__name__
        proof.record["failureCategory"] = error
        (proof.directory / "failure.txt").write_text(str(failure))
        print("Collaboration proof failed; private diagnostics retained", flush=True)
    finally:
        proof.cleanup()
        proof.record["elapsedSeconds"] = round(time.monotonic() - started, 3)
        result = proof.directory / "result.json"
        write_private(result, proof.record)
        print(f"Sanitized result: {result.relative_to(ROOT)}", flush=True)
    return 0 if proof.record["complete"] and proof.record["cleanup"] and not error else 1


if __name__ == "__main__":
    sys.exit(main())
