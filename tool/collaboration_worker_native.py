"""Exercise the application worker across a real death of the owned emulator process."""
from __future__ import annotations

import argparse
import json
import re
import shlex
import signal
import subprocess
import time
import uuid
from pathlib import Path
from urllib.parse import urlparse

from collaboration_hris_native import app_write_script, private_json, read_json, stop_process
from collaboration_native_device import NativeDevice, PACKAGE, native_diagnostic


def checkpoint(value, state="QUEUED", restored=False):
    if not isinstance(value, dict) or value.get("state") != state or value.get("taskKey") != "hris.collaboration-delivery":
        raise ValueError("Invalid worker checkpoint")
    pid = value.get("processId")
    message = value.get("messageId")
    digest = value.get("bodySha256")
    if type(pid) is not int or not 1 <= pid <= 2147483647:
        raise ValueError("Invalid worker process")
    if not isinstance(message, str) or str(uuid.UUID(message)) != message:
        raise ValueError("Invalid worker identity")
    if not isinstance(digest, str) or not re.fullmatch(r"[a-f0-9]{64}", digest):
        raise ValueError("Invalid worker digest")
    if restored and value.get("senderRuntimeOpen") is not False:
        raise ValueError("Worker must restore its own sender runtime")
    return {"processId": pid, "messageId": message, "bodySha256": digest,
            "state": state, "taskKey": "hris.collaboration-delivery"}


def evidence(value, queued, restored):
    result = checkpoint(value, "COMPLETED")
    if (result["processId"] != restored["processId"] or result["processId"] == queued["processId"]
            or any(result[key] != queued[key] or result[key] != restored[key] for key in ("messageId", "bodySha256"))):
        raise ValueError("Worker completion did not retain the original intent")
    event = value.get("eventId")
    if not isinstance(event, str) or not re.fullmatch(r"\$[!-~]{1,254}", event):
        raise ValueError("Invalid worker event")
    passes = value.get("passes")
    if type(passes) is not int or not 1 <= passes <= 4:
        raise ValueError("Worker exceeded its bounded attempts")
    for key in ("encryptedReceived", "senderRuntimeRestoredByWorker", "originalHistoryAcquired",
                "completionRetired", "repeatedWorkerComplete"):
        if value.get(key) is not True:
            raise ValueError("Missing worker proof")
        result[key] = True
    return result | {"eventId": event, "passes": passes}


def diagnostic(status):
    if status is not None and not isinstance(status, dict):
        return {"failureCategory": "InvalidDiagnostic"}
    try:
        return native_diagnostic({"instrumentation": status})
    except ValueError:
        return {"failureCategory": "InvalidDiagnostic"}


def run(apk, sdk, directory):
    directory = Path(directory).resolve(strict=True)
    fixture = read_json(directory / "fixture.json")
    home = urlparse(fixture["baseUrl"])
    if (home.scheme != "http" or home.hostname != "127.0.0.1" or not 1024 <= (home.port or 0) <= 65535
            or home.username or home.password or home.query or home.fragment or home.path not in ("", "/")):
        raise ValueError("Only the owned loopback HRIS server is allowed")
    native = NativeDevice(apk, "emulator-5560", sdk, directory)
    process = None
    result = {"passed": False, "cleanup": False}

    def app_json(name):
        try:
            raw = native.command("exec-out", "run-as", PACKAGE, "cat", f"files/protocol-proof/{name}.json")
            if len(raw) > 16384:
                raise ValueError("Oversized worker checkpoint")
            return json.loads(raw)
        except (RuntimeError, json.JSONDecodeError):
            return None

    def write_app(name, value):
        response = subprocess.run([native.adb, "-s", native.serial, "shell", "run-as", PACKAGE,
                                   "sh", "-c", shlex.quote(app_write_script(name))],
                                  input=json.dumps(value), text=True, capture_output=True, timeout=15)
        if response.returncode:
            raise RuntimeError("Could not deliver owned worker fixture")

    def start(phase):
        return subprocess.Popen([native.adb, "-s", native.serial, "shell", "am", "instrument", "-w",
                                 "-e", "scenario", phase, f"{PACKAGE}/.MatrixProofInstrumentation"],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    try:
        native.prepare()
        result["artifact"] = native.bind({"matrix": {"baseUrl": fixture["baseUrl"]}})
        write_app("worker-fixture", fixture)
        process = start("hris-worker-stage")
        queued = restored = None
        sent = False
        deadline = time.monotonic() + 300
        while time.monotonic() < deadline:
            if process.poll() is not None:
                status = app_json("instrumentation")
                if queued is None or restored is None or not sent or not isinstance(status, dict) or status.get("complete") is not True:
                    result["nativeDiagnostic"] = diagnostic(status)
                    raise RuntimeError("Worker instrumentation did not complete")
                result["proof"] = evidence(app_json("worker-result"), queued, restored)
                result.update(passed=True, processes=2, forcedProcessDeaths=1)
                break
            if queued is None:
                observed = app_json("worker-queued")
                if observed is not None:
                    queued = checkpoint(observed)
                    if native.process_id() != queued["processId"]:
                        raise RuntimeError("Queued worker process was not confirmed")
                    native.command("shell", "am", "force-stop", PACKAGE)
                    if not stop_process(process) or native.process_id() is not None:
                        raise RuntimeError("Queued worker process did not stop")
                    private_json(directory / "native-worker-queued.json", queued)
                    process = start("hris-worker-resume")
            elif restored is None:
                observed = app_json("worker-restored")
                if observed is not None:
                    restored = checkpoint(observed, restored=True)
                    if (restored["processId"] == queued["processId"] or native.process_id() != restored["processId"]
                            or any(restored[key] != queued[key] for key in ("messageId", "bodySha256"))):
                        raise RuntimeError("Worker restore lost the original intent")
                    private_json(directory / "native-worker-restored.json", restored)
            elif not sent and read_json(directory / "send.json") == {"ready": True}:
                write_app("worker-send", {"ready": True})
                sent = True
            time.sleep(0.15)
        else:
            raise TimeoutError("Worker native proof exceeded its deadline")
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        result["failureCategory"] = type(error).__name__
        result.setdefault("nativeDiagnostic", diagnostic(app_json("instrumentation")))
    finally:
        stopped = stop_process(process)
        result["cleanup"] = native.cleanup() and stopped
        private_json(directory / "result.json", result)
    return result["passed"] and result["cleanup"]


def main():
    def cancelled(_signal, _frame):
        raise InterruptedError("Owned worker driver cancelled")

    signal.signal(signal.SIGTERM, cancelled)
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--directory", required=True)
    args = parser.parse_args()
    return 0 if run(args.apk, args.sdk, args.directory) else 1


if __name__ == "__main__":
    raise SystemExit(main())
