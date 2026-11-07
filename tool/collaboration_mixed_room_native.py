"""Measure two fictional rooms through the actual Android worker; never a user device."""
from __future__ import annotations

import argparse
import json
import re
import shlex
import signal
import subprocess
import time
from pathlib import Path
from urllib.parse import urlparse

from collaboration_hris_native import app_write_script, private_json, read_json, stop_process
from collaboration_native_device import NativeDevice, PACKAGE, native_proof_serial, native_diagnostic


def evidence(value):
    if not isinstance(value, dict):
        raise ValueError("Missing mixed-room evidence")
    flags = ("complete", "originalReadyIntentRetained", "unavailableOriginalRetained", "unavailableReceiptAbsent")
    if any(value.get(key) is not True for key in flags):
        raise ValueError("Incomplete mixed-room evidence")
    result = {key: True for key in flags}
    required = ("recipientRuntimeClosedMillis", "unavailableQueuedMillis", "readyQueuedMillis",
                "sentObservedMillis", "completionObservedMillis")
    for key in (*required, "journalObservedMillis"):
        item = value.get(key)
        if item is None and key == "journalObservedMillis":
            continue  # The journal may have retired before this separate observer acquired it.
        if type(item) is not int or not 0 <= item <= 300000:
            raise ValueError("Invalid monotonic mixed-room observation")
        result[key] = item
    if not (result[required[0]] <= result[required[1]] <= result[required[2]]
            <= result[required[3]] and result[required[2]] <= result[required[4]]):
        raise ValueError("Mixed-room milestones are out of order")
    if "journalObservedMillis" in result and result["journalObservedMillis"] < result["readyQueuedMillis"]:
        raise ValueError("Receipt preceded its original queue publication")
    event = value.get("eventId")
    if not isinstance(event, str) or not re.fullmatch(r"\$[!-~]{1,254}", event):
        raise ValueError("Invalid real message acknowledgement")
    return result | {"eventId": event}


def run(apk, sdk, directory):
    directory = Path(directory).resolve(strict=True)
    fixture = read_json(directory / "fixture.json")
    home = urlparse(fixture["baseUrl"])
    if (home.scheme != "http" or home.hostname != "127.0.0.1" or not 1024 <= (home.port or 0) <= 65535
            or home.username or home.password or home.query or home.fragment or home.path not in ("", "/")):
        raise ValueError("Only the owned loopback HRIS server is allowed")
    native = NativeDevice(apk, native_proof_serial(), sdk, directory)
    process = None
    result = {"passed": False, "cleanup": False}

    def app_json(name):
        try:
            raw = native.command("exec-out", "run-as", PACKAGE, "cat", f"files/protocol-proof/{name}.json")
            if len(raw) > 16384:
                raise ValueError("Oversized mixed-room checkpoint")
            return json.loads(raw)
        except (RuntimeError, json.JSONDecodeError):
            return None

    def write_app(name, value):
        response = subprocess.run([native.adb, "-s", native.serial, "shell", "run-as", PACKAGE,
                                   "sh", "-c", shlex.quote(app_write_script(name))],
                                  input=json.dumps(value), text=True, capture_output=True, timeout=15)
        if response.returncode:
            raise RuntimeError("Could not deliver owned mixed-room fixture")

    forwarded = set()
    try:
        native.prepare()
        result["artifact"] = native.bind({"matrix": {"baseUrl": fixture["baseUrl"]}})
        write_app("worker-fixture", fixture)
        process = subprocess.Popen([native.adb, "-s", native.serial, "shell", "am", "instrument", "-w",
                                    "-e", "scenario", "hris-mixed-room", f"{PACKAGE}/.MatrixProofInstrumentation"],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        deadline = time.monotonic() + 310
        while time.monotonic() < deadline:
            for name, expected in (("initialized", {"ready": True}), ("prepared", {"recipientRuntimeClosed": True})):
                if name not in forwarded and app_json("mixed-" + name) == expected:
                    private_json(directory / f"{name}.json", expected)
                    forwarded.add(name)
            for name in ("readiness", "start", "enqueue"):
                if name not in forwarded and read_json(directory / f"{name}.json") == {"ready": True}:
                    write_app("mixed-" + name, {"ready": True})
                    forwarded.add(name)
            if process.poll() is not None:
                status = app_json("instrumentation")
                result["nativeDiagnostic"] = native_diagnostic({"instrumentation": status})
                if not isinstance(status, dict) or status.get("complete") is not True or len(forwarded) != 5:
                    raise RuntimeError("Mixed-room instrumentation did not complete")
                result["proof"] = evidence(app_json("mixed-result"))
                result["passed"] = True
                break
            time.sleep(0.1)
        else:
            raise TimeoutError("Mixed-room native proof exceeded its deadline")
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        result["failureCategory"] = type(error).__name__
    finally:
        stopped = stop_process(process)
        # Keep original private observations even when incomplete; never promote them to proof.
        try:
            for name in ("mixed-result", "instrumentation"):
                observed = app_json(name)
                if observed is not None:
                    private_json(directory / f"original-{name}.json", observed)
        except (OSError, ValueError, RuntimeError, subprocess.SubprocessError):
            result["evidenceCaptureFailed"] = True
            result["passed"] = False
        finally:
            result["cleanup"] = native.cleanup() and stopped
            private_json(directory / "result.json", result)
    return result["passed"] and result["cleanup"]


def main():
    def cancelled(_signal, _frame):
        raise InterruptedError("Owned mixed-room driver cancelled")
    signal.signal(signal.SIGTERM, cancelled)
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--directory", required=True)
    args = parser.parse_args()
    return 0 if run(args.apk, args.sdk, args.directory) else 1


if __name__ == "__main__":
    raise SystemExit(main())
