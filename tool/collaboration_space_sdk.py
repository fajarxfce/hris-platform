"""Owned original-key migration experiment on the dedicated Android emulator."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shlex
import signal
import subprocess
import time
from urllib.parse import urlparse

from collaboration_native_device import NativeDevice, PACKAGE, native_proof_serial, native_diagnostic
from collaboration_hris_native import private_json, read_json, app_write_script, stop_process


def checkpoint(value, fingerprint=False):
    if not isinstance(value, dict):
        raise ValueError("Invalid owned checkpoint")
    pid = value.get("processId")
    if type(pid) is not int or not 1 <= pid <= 2147483647:
        raise ValueError("Invalid owned process")
    if fingerprint and not re.fullmatch(r"[a-f0-9]{64}", str(value.get("fingerprint", ""))):
        raise ValueError("Invalid owned fingerprint")
    return value


def run(apk, sdk, directory):
    directory = Path(directory).resolve(strict=True)
    fixture = read_json(directory / "fixture.json")
    home = urlparse(fixture["baseUrl"])
    if (home.scheme != "http" or home.hostname != "127.0.0.1" or not 1024 <= (home.port or 0) <= 65535
            or home.username or home.password or home.path not in ("", "/") or home.query or home.fragment):
        raise ValueError("Expected the owned loopback API")
    native = NativeDevice(apk, native_proof_serial(), sdk, directory)
    process = None
    result = {"passed": False, "cleanup": False}

    def app_json(name):
        try:
            raw = native.command("exec-out", "run-as", PACKAGE, "cat", f"files/protocol-proof/{name}.json")
            if len(raw) > 65536:
                raise ValueError("Oversized owned checkpoint")
            return json.loads(raw)
        except (RuntimeError, json.JSONDecodeError):
            return None

    def write_app(name, value):
        call = subprocess.run([native.adb, "-s", native.serial, "shell", "run-as", PACKAGE, "sh", "-c", shlex.quote(app_write_script(name))],
                              input=json.dumps(value), text=True, capture_output=True, timeout=15)
        if call.returncode:
            raise RuntimeError("Owned fixture delivery failed")

    def start(phase):
        native.command("shell", "run-as", PACKAGE, "rm", "-f", "files/protocol-proof/instrumentation.json")
        return subprocess.Popen([native.adb, "-s", native.serial, "shell", "am", "instrument", "-w", "-e", "scenario", phase,
                                 f"{PACKAGE}/.MatrixProofInstrumentation"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def wait_app(name, seconds=120):
        until = time.monotonic() + seconds
        while time.monotonic() < until:
            value = app_json(name)
            if value is not None:
                return value
            state = app_json("instrumentation")
            if state is not None and state.get("complete") is not True:
                result["diagnostic"] = native_diagnostic({"instrumentation": state})
                raise RuntimeError("Owned instrumentation failed")
            time.sleep(0.2)
        raise TimeoutError("Owned app phase timed out")

    def wait_host(name, seconds=120):
        until = time.monotonic() + seconds
        while time.monotonic() < until:
            value = read_json(directory / f"{name}.json")
            if value is not None:
                return value
            time.sleep(0.1)
        raise TimeoutError("Owned server phase timed out")

    def kill_owned(value):
        pid = checkpoint(value)["processId"]
        if native.process_id() != pid:
            raise RuntimeError("Owned process changed before termination")
        native.command("shell", "run-as", PACKAGE, "kill", "-9", str(pid))
        if not stop_process(process):
            raise RuntimeError("Owned instrumentation driver did not stop")
        until = time.monotonic() + 10
        while time.monotonic() < until:
            if native.process_id() is None:
                return pid
            time.sleep(0.1)
        raise RuntimeError("Owned Android process did not exit")

    try:
        native.prepare()
        result["artifact"] = native.bind({"matrix": {"baseUrl": fixture["baseUrl"]}})
        write_app("space-fixture", fixture)
        process = start("space-history-stage")
        ready = checkpoint(wait_app("space-ready"), fingerprint=True)
        private_json(directory / "ready.json", ready)
        write_app("space-continue", wait_host("continue"))
        queued = checkpoint(wait_app("space-queued"), fingerprint=True)
        if queued["fingerprint"] != ready["fingerprint"] or queued.get("receiverClosedBeforeSend") is not True:
            raise ValueError("Original owned identity was not retained")
        if not re.fullmatch(r"\$[!-~]{1,254}", queued.get("eventId", "")):
            raise ValueError("Invalid owned event receipt")
        process.wait(timeout=20)
        if app_json("instrumentation").get("complete") is not True:
            raise RuntimeError("Stage cleanup failed")
        private_json(directory / "queued.json", queued)
        write_app("space-group", wait_host("group"))
        process = start("space-history-lose")
        lost = checkpoint(wait_app("space-lost"))
        private_json(directory / "lost.json", lost)
        wait_host("restart")
        first_pid = kill_owned(lost)
        process = start("space-history-recover")
        decrypted = checkpoint(wait_app("space-decrypted"), fingerprint=True)
        if decrypted["processId"] == first_pid or decrypted["fingerprint"] != queued["fingerprint"] or decrypted.get("eventId") != queued["eventId"]:
            raise ValueError("Owned process/key recovery was not demonstrated")
        private_json(directory / "decrypted.json", decrypted)
        wait_host("history")
        second_pid = kill_owned(decrypted)
        process = start("space-history-final")
        proof = checkpoint(wait_app("space-result"))
        process.wait(timeout=20)
        if app_json("instrumentation").get("complete") is not True or proof["processId"] in (first_pid, second_pid):
            raise RuntimeError("Final owned process recovery failed")
        for name in ("sameOriginalKeys", "decryptedAfterLostResponse", "localHistoryBeforeSync", "singleLogicalEvent"):
            if proof.get(name) is not True:
                raise ValueError("Incomplete owned history evidence")
        result.update(passed=True, proof=proof, actualProcessDeaths=2)
    except Exception as failure:
        result["failureCategory"] = type(failure).__name__
    finally:
        stopped = stop_process(process)
        result["cleanup"] = native.cleanup() and stopped
        result["passed"] = result["passed"] and result["cleanup"]
        private_json(directory / "result.json", result)
    return 0 if result["passed"] else 1


def interrupted(_number, _frame):
    raise InterruptedError("Owned SDK driver interrupted")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--directory", required=True)
    arguments = parser.parse_args()
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    raise SystemExit(run(arguments.apk, arguments.sdk, arguments.directory))
