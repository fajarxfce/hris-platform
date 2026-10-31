"""Run the owned Android SDK against a private, fictional HRIS test server."""
from __future__ import annotations

import argparse
import json
import os
import re
import signal
from pathlib import Path
import subprocess
import time
from urllib.parse import urlparse

from collaboration_native_device import NativeDevice, PACKAGE, native_diagnostic


def private_json(path, value):
    data = json.dumps(value).encode()
    temporary = path.with_suffix(path.suffix + ".next")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as output:
            output.write(data)
        os.link(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def read_json(path):
    if not path.is_file():
        return None
    if path.stat().st_size > 65536:
        raise ValueError("Oversized owned fixture")
    return json.loads(path.read_text())


def proof_evidence(value):
    if not isinstance(value, dict) or value.get("encryptedBothDirections") is not True or value.get("logicalIdsPreserved") is not True or value.get("syncMode") != "classic":
        raise ValueError("Invalid owned exchange evidence")
    output = {"encryptedBothDirections": True, "logicalIdsPreserved": True, "syncMode": "classic"}
    for name in ("senderEventId", "recipientEventId", "senderTransaction", "recipientTransaction"):
        field = value.get(name)
        pattern = r"\$[!-~]{1,254}" if name.endswith("EventId") else r"[A-Za-z0-9._~-]{1,128}"
        if not isinstance(field, str) or not re.fullmatch(pattern, field):
            raise ValueError("Invalid owned receipt")
        output[name] = field
    if output["senderEventId"] == output["recipientEventId"]:
        raise ValueError("Owned receipts must be distinct")
    return output


def stop_process(process):
    if process is None:
        return True
    try:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
        return True
    except (OSError, subprocess.SubprocessError):
        return False


def run(apk, sdk, directory):
    directory = Path(directory).resolve(strict=True)
    fixture = read_json(directory / "fixture.json")
    home = urlparse(fixture["baseUrl"])
    if (home.scheme != "http" or home.hostname != "127.0.0.1"
            or not 1024 <= (home.port or 0) <= 65535 or home.username or home.password
            or home.query or home.fragment or home.path not in ("", "/")):
        raise ValueError("Only the owned loopback HRIS server is allowed")
    native = NativeDevice(apk, "emulator-5560", sdk, directory)
    process = None
    result = {"passed": False, "cleanup": False}

    def app_json(name):
        try:
            value = native.command("exec-out", "run-as", PACKAGE, "cat", f"files/protocol-proof/{name}.json")
            if len(value) > 65536:
                raise ValueError("Oversized owned result")
            return json.loads(value)
        except (RuntimeError, json.JSONDecodeError):
            return None

    def write_app(name, value):
        request = subprocess.run([native.adb, "-s", native.serial, "shell", "run-as", PACKAGE,
                                  "sh", "-c", f"'cat > files/protocol-proof/{name}.json'"],
                                 input=json.dumps(value), text=True, capture_output=True, timeout=15)
        if request.returncode:
            raise RuntimeError("Could not deliver owned fixture")

    try:
        native.prepare()
        result["artifact"] = native.bind({"matrix": {"baseUrl": fixture["baseUrl"]}})
        write_app("gateway-fixture", fixture)
        process = subprocess.Popen([native.adb, "-s", native.serial, "shell", "am", "instrument",
                                    "-w", "-e", "scenario", "hris",
                                    f"{PACKAGE}/.MatrixProofInstrumentation"],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        until = time.monotonic() + 115
        ready = False
        released = False
        while time.monotonic() < until:
            completion = app_json("instrumentation")
            if completion is not None:
                result["diagnostic"] = native_diagnostic({"instrumentation": completion})
                result["diagnostic"].pop("replyComplete")
                result["diagnostic"].pop("resultComplete")
                if completion.get("complete") is not True:
                    raise RuntimeError("Owned native instrumentation failed")
                result["proof"] = proof_evidence(app_json("gateway-result"))
                result["passed"] = True
                break
            if not ready and app_json("gateway-ready") == {"ready": True}:
                private_json(directory / "native-ready.json", {"ready": True})
                ready = True
            if ready and not released and read_json(directory / "send.json") == {"ready": True}:
                write_app("gateway-send", {"ready": True})
                released = True
            if process.poll() is not None:
                raise RuntimeError("Owned native instrumentation exited without a result")
            time.sleep(0.15)
        else:
            raise TimeoutError("Owned native exchange exceeded its deadline")
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        result["failureCategory"] = type(error).__name__
    finally:
        stopped = stop_process(process)
        result["cleanup"] = native.cleanup() and stopped
        private_json(directory / "result.json", result)
    return result["passed"] and result["cleanup"]


def main():
    def cancelled(_signal, _frame):
        raise InterruptedError("Owned driver cancelled")

    signal.signal(signal.SIGTERM, cancelled)
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--directory", required=True)
    args = parser.parse_args()
    return 0 if run(args.apk, args.sdk, args.directory) else 1


if __name__ == "__main__":
    raise SystemExit(main())
