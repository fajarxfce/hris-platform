"""Prove actual Android scheduling after ordinary process death, without driving a phone."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shlex
import signal
import subprocess
import time
import uuid
from pathlib import Path
from urllib.parse import urlparse

from collaboration_hris_native import app_write_script, private_json, read_json
from collaboration_native_device import NativeDevice, PACKAGE
from collaboration_scheduled_network import EmulatorNetwork
from collaboration_worker_native import checkpoint, diagnostic


def canonical_work(value):
    if not isinstance(value, str) or str(uuid.UUID(value)) != value:
        raise ValueError("Invalid scheduled work identity")
    return value


def queued_work(value, prepared):
    result = checkpoint(value)
    if any(result[key] != prepared[key] for key in ("processId", "messageId", "bodySha256")):
        raise ValueError("Scheduled intent changed during preparation")
    work = canonical_work(value.get("workId"))
    job = value.get("jobId")
    namespace = value.get("jobNamespace")
    if not isinstance(namespace, str) or (namespace and not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.-]{0,79}", namespace)):
        raise ValueError("Invalid Android job namespace")
    if type(job) is not int or not 0 <= job <= 2147483647:
        raise ValueError("Invalid Android system job")
    expected = {"workState": "ENQUEUED", "networkConstraint": "CONNECTED",
                "inputKeys": ["sync_task"], "workerClass": "dev.fajar.starter.worker.SyncWorker"}
    if any(value.get(key) != wanted for key, wanted in expected.items()):
        raise ValueError("Work must persist only the production task key with network constraints")
    if type(value.get("runAttemptCount")) is not int or value["runAttemptCount"] != 0:
        raise ValueError("Offline work already executed")
    return result | expected | {"workId": work, "jobId": job, "jobNamespace": namespace, "runAttemptCount": 0}


def restored_work(value, queued):
    result = checkpoint(value, restored=True)
    if (result["processId"] == queued["processId"]
            or any(result[key] != queued[key] for key in ("messageId", "bodySha256"))
            or canonical_work(value.get("workId")) != queued["workId"]):
        raise ValueError("Background work did not recover the original request in a new process")
    return result | {"workId": queued["workId"], "senderRuntimeOpen": False}


def completion(value, queued, restored):
    result = checkpoint(value, "COMPLETED")
    if (result["processId"] != restored["processId"] or result["processId"] == queued["processId"]
            or any(result[key] != queued[key] for key in ("messageId", "bodySha256"))
            or canonical_work(value.get("workId")) != queued["workId"]
            or value.get("workState") != "SUCCEEDED"):
        raise ValueError("Android work did not complete its original persisted request")
    attempt = value.get("runAttemptCount")
    event = value.get("eventId")
    if type(attempt) is not int or not 0 <= attempt <= 3:
        raise ValueError("Scheduled attempts exceeded the proof bound")
    if not isinstance(event, str) or not re.fullmatch(r"\$[!-~]{1,254}", event):
        raise ValueError("Invalid scheduled event")
    flags = ("senderRuntimeRestoredByWorker", "originalHistoryAcquired", "completionRetired")
    if any(value.get(key) is not True for key in flags):
        raise ValueError("Scheduled completion is missing original history or retirement evidence")
    return result | {"workId": queued["workId"], "workState": "SUCCEEDED", "eventId": event,
                     "runAttemptCount": attempt} | {key: True for key in flags}


def application_backgrounded(value):
    if not isinstance(value, str) or len(value) > 262144:
        raise ValueError("Invalid activity ownership snapshot")
    resumed = [line for line in value.splitlines() if "topResumedActivity" in line or "mResumedActivity" in line]
    if not resumed:
        raise ValueError("No resumed activity could be identified")
    return all(PACKAGE not in line for line in resumed)


def run(apk, sdk, directory):
    directory = Path(directory).resolve(strict=True)
    fixture = read_json(directory / "fixture.json")
    home = urlparse(fixture["baseUrl"])
    if (home.scheme != "http" or home.hostname != "127.0.0.1" or not 1024 <= (home.port or 0) <= 65535
            or home.username or home.password or home.query or home.fragment or home.path not in ("", "/")):
        raise ValueError("Only the owned loopback HRIS server is allowed")
    native = NativeDevice(apk, "emulator-5560", sdk, directory)
    network = None
    result = {"passed": False, "cleanup": False, "automaticJobStart": False}

    def app_json(name):
        try:
            raw = native.command("exec-out", "run-as", PACKAGE, "cat", f"files/protocol-proof/{name}.json")
            if len(raw) > 16384:
                raise ValueError("Oversized scheduled checkpoint")
            return json.loads(raw)
        except (RuntimeError, json.JSONDecodeError):
            return None

    def write_app(name, value):
        response = subprocess.run([native.adb, "-s", native.serial, "shell", "run-as", PACKAGE,
                                   "sh", "-c", shlex.quote(app_write_script(name))],
                                  input=json.dumps(value), text=True, capture_output=True, timeout=15)
        if response.returncode:
            raise RuntimeError("Could not deliver owned scheduling fixture")

    try:
        native.prepare()
        network = EmulatorNetwork(native)
        private_json(directory / "network-before.json", network.before)
        result["artifact"] = native.bind({"matrix": {"baseUrl": fixture["baseUrl"]}})
        write_app("worker-fixture", fixture)
        write_app("workmanager-enabled", {"enabled": True})
        native.command("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.HrisScheduledProofActivity")
        prepared = queued = restored = None
        offline = sent = False
        deadline = time.monotonic() + 350
        while time.monotonic() < deadline:
            failure = app_json("scheduled-failure")
            if failure is not None:
                result["nativeDiagnostic"] = diagnostic(failure)
                raise RuntimeError("Scheduled application proof failed")
            if prepared is None:
                value = app_json("scheduled-prepared")
                if value is not None:
                    prepared = checkpoint(value, "PREPARED")
                    if (prepared["processId"] != native.process_id()
                            or prepared["messageId"] != fixture["logicalId"]
                            or prepared["bodySha256"] != hashlib.sha256(fixture["body"].encode()).hexdigest()):
                        raise RuntimeError("Scheduled preparation did not bind the fixture")
                    private_json(directory / "prepared.json", prepared)
            elif queued is None:
                if not offline and (directory / "offline.json").exists():
                    if read_json(directory / "offline.json").get("ready") is not True:
                        raise ValueError("Invalid offline preparation signal")
                    network.disable()
                    write_app("scheduled-offline", {"ready": True})
                    offline = True
                value = app_json("scheduled-queued")
                if value is not None:
                    if not offline:
                        raise RuntimeError("Work was queued without its offline constraint")
                    queued = queued_work(value, prepared)
                    if native.process_id() != queued["processId"]:
                        raise RuntimeError("Queued process was not confirmed")
                    native.command("shell", "input", "keyevent", "KEYCODE_HOME")
                    background_deadline = time.monotonic() + 10
                    while not application_backgrounded(native.command("shell", "dumpsys", "activity", "activities")):
                        if time.monotonic() >= background_deadline:
                            raise RuntimeError("The proof activity did not move to the background")
                        time.sleep(0.1)
                    native.command("shell", "run-as", PACKAGE, "kill", "-9", str(queued["processId"]))
                    stop_deadline = time.monotonic() + 10
                    while native.process_id() is not None and time.monotonic() < stop_deadline:
                        time.sleep(0.1)
                    if native.process_id() is not None:
                        raise RuntimeError("Queued process did not stop")
                    package = native.command("shell", "dumpsys", "package", PACKAGE)
                    if len(package) > 262144 or not re.search(r"User 0:.*\bstopped=false\b", package):
                        raise RuntimeError("Ordinary death must not force-stop the package")
                    job_args = ["shell", "cmd", "jobscheduler", "get-job-state"]
                    if queued["jobNamespace"]:
                        job_args += ["-n", queued["jobNamespace"]]
                    job = native.command(*job_args, PACKAGE, str(queued["jobId"]))
                    if len(job) > 512 or not any(state in job.split() for state in ("waiting", "pending")) or "active" in job.split():
                        raise RuntimeError("The constrained system job was not retained")
                    private_json(directory / "stopped.json", queued | {
                        "ordinaryProcessDeath": True, "packageNotForceStopped": True, "systemJobRetained": True})
            else:
                if not sent and (directory / "send.json").exists():
                    if read_json(directory / "send.json").get("ready") is not True:
                        raise ValueError("Invalid scheduled send admission")
                    if not network.restore():
                        raise RuntimeError("Could not restore the emulator network")
                    sent = True
                if restored is None:
                    value = app_json("scheduled-started")
                    if value is not None:
                        if not sent:
                            raise RuntimeError("Background process started before network restoration")
                        restored = restored_work(value, queued)
                        if native.process_id() != restored["processId"]:
                            raise RuntimeError("Background process was not confirmed")
                        private_json(directory / "started.json", restored)
                value = app_json("scheduled-result")
                if value is not None:
                    if restored is None or not sent:
                        raise RuntimeError("Scheduled work completed without observed OS startup")
                    result["proof"] = completion(value, queued, restored)
                    result.update(passed=True, processes=2, ordinaryProcessDeaths=1, automaticJobStart=True)
                    break
            time.sleep(0.1)
        else:
            raise TimeoutError("Bounded Android scheduling proof did not complete")
    except Exception as error:
        result["failureCategory"] = type(error).__name__
        result.setdefault("nativeDiagnostic", diagnostic(app_json("scheduled-failure")))
    finally:
        cleanup = True
        if native.installed:
            try:
                native.command("shell", "am", "force-stop", PACKAGE, timeout=5)
            except (RuntimeError, OSError, subprocess.TimeoutExpired):
                cleanup = False
        if network is not None:
            try:
                result["networkRestored"] = network.restore()
            except (RuntimeError, OSError, subprocess.TimeoutExpired):
                result["networkRestored"] = False
            cleanup = result["networkRestored"] and cleanup
        result["cleanup"] = native.cleanup() and cleanup
        private_json(directory / "result.json", result)
    return result["passed"] and result["cleanup"]


def main():
    def cancelled(_signal, _frame):
        raise InterruptedError("Owned scheduled driver cancelled")

    signal.signal(signal.SIGTERM, cancelled)
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--directory", required=True)
    args = parser.parse_args()
    return 0 if run(args.apk, args.sdk, args.directory) else 1


if __name__ == "__main__":
    raise SystemExit(main())
