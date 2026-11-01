"""Run the owned Android SDK against a private, fictional HRIS test server."""
from __future__ import annotations

import argparse
import json
import os
import re
import shlex
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


def app_write_script(name):
    if not isinstance(name, str) or not re.fullmatch(r"[a-z][a-z0-9-]{0,63}", name):
        raise ValueError("Invalid owned fixture name")
    target = f"files/protocol-proof/{name}.json"
    temporary = target + ".next"
    # The final name becomes visible only after cat has consumed all stdin. This driver
    # exclusively owns the app directory. Android disallows hard links here; mv -n
    # preserves an old signal, and a retained temporary makes that collision fail.
    return f"set -eu; umask 077; set -C; cat > {temporary}; mv -n {temporary} {target}; test ! -e {temporary}"


def proof_evidence(value):
    if not isinstance(value, dict) or value.get("encryptedBothDirections") is not True or value.get("logicalIdsPreserved") is not True or value.get("syncMode") != "classic":
        raise ValueError("Invalid owned exchange evidence")
    output = {"encryptedBothDirections": True, "logicalIdsPreserved": True, "syncMode": "classic"}
    for name in ("protectedStoreReopened", "missingRecordPreserved", "wrongPassphrasePreserved", "exclusiveOwner",
                 "queueRestored", "transactionReused", "identityAfterRestart",
                 "messageIntentRestored", "queueMappingRecovered", "queueWriterExclusive",
                 "perRoomDeliveryIsolated", "heldQueueRestored", "nativeDeliveryOwned",
                 "commonSenderOwned", "receiptJournalRecovered", "projectionInterrupted",
                 "originalHistoryWindowOwned", "historyBodiesRestored", "commonHistoryAcquisitionOwned", "latestHistoryHeadOwned", "providerHistoryPageOwned",
                 "commonProviderHistoryAcquisitionOwned", "providerHistoryReferencesRestored"):
        if value.get(name) is not True:
            raise ValueError("Invalid owned protected store evidence")
        output[name] = True
    for name in ("senderEventId", "recipientEventId", "senderTransaction", "recipientTransaction"):
        field = value.get(name)
        pattern = r"\$[!-~]{1,254}" if name.endswith("EventId") else r"[A-Za-z0-9._~-]{1,128}"
        if not isinstance(field, str) or not re.fullmatch(pattern, field):
            raise ValueError("Invalid owned receipt")
        output[name] = field
    if output["senderEventId"] == output["recipientEventId"]:
        raise ValueError("Owned receipts must be distinct")
    output["historyWindow"] = history_evidence(value.get("historyWindow"))
    return output


def history_evidence(value):
    if not isinstance(value, dict):
        raise ValueError("Missing owned history evidence")
    output = {}
    limits = {"originals": (2, 2), "unavailable": (0, 128),
              "extractionNanos": (0, 5_000_000_000),
              "modelCharacters": (1, 4_194_304), "originalCharacters": (1, 4_194_304)}
    for name, (minimum, maximum) in limits.items():
        field = value.get(name)
        if type(field) is not int or not minimum <= field <= maximum:
            raise ValueError("Invalid owned history measurement")
        output[name] = field
    return output


def checkpoint_evidence(value, staged=False):
    if not isinstance(value, dict):
        raise ValueError("Invalid owned queue checkpoint")
    pid = value.get("processId")
    transaction = value.get("transactionId")
    if type(pid) is not int or not 1 <= pid <= 2147483647:
        raise ValueError("Invalid owned process identifier")
    if not isinstance(transaction, str) or not re.fullmatch(r"[A-Za-z0-9._~-]{1,128}", transaction):
        raise ValueError("Invalid owned pending transaction")
    if staged:
        for name in ("protectedStoreReopened", "missingRecordPreserved", "wrongPassphrasePreserved", "exclusiveOwner"):
            if value.get(name) is not True:
                raise ValueError("Invalid owned store checkpoint")
        for name in ("senderKeyFingerprint", "recipientKeyFingerprint"):
            if not isinstance(value.get(name), str) or not re.fullmatch(r"[a-f0-9]{64}", value[name]):
                raise ValueError("Invalid owned identity checkpoint")
    return {"processId": pid, "transactionId": transaction}


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
                                  "sh", "-c", shlex.quote(app_write_script(name))],
                                 input=json.dumps(value), text=True, capture_output=True, timeout=15)
        if request.returncode:
            raise RuntimeError("Could not deliver owned fixture")

    def start(phase):
        return subprocess.Popen([native.adb, "-s", native.serial, "shell", "am", "instrument",
                                 "-w", "-e", "scenario", phase,
                                 f"{PACKAGE}/.MatrixProofInstrumentation"],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    try:
        native.prepare()
        result["artifact"] = native.bind({"matrix": {"baseUrl": fixture["baseUrl"]}})
        write_app("gateway-fixture", fixture)
        process = start("hris-stage")
        until = time.monotonic() + 230
        ready = False
        released = False
        staged = None
        restored = None
        resumed = False
        while time.monotonic() < until:
            completion = app_json("instrumentation")
            if completion is not None:
                result["diagnostic"] = native_diagnostic({"instrumentation": completion})
                result["diagnostic"].pop("replyComplete")
                result["diagnostic"].pop("resultComplete")
                if completion.get("complete") is not True:
                    raise RuntimeError("Owned native instrumentation failed")
                if staged is None or restored is None or not resumed:
                    raise RuntimeError("Owned process restart was not completed")
                proof = app_json("gateway-result")
                result["proof"] = proof_evidence(proof)
                if (type(proof.get("processId")) is not int or proof["processId"] != restored["processId"]
                        or result["proof"]["senderTransaction"] != staged["transactionId"]):
                    raise RuntimeError("Owned pending receipt did not survive restart")
                result["proof"]["processRestarted"] = True
                result["passed"] = True
                break
            if not ready and app_json("gateway-ready") == {"ready": True}:
                private_json(directory / "native-ready.json", {"ready": True})
                ready = True
            if ready and not released and read_json(directory / "send.json") == {"ready": True}:
                write_app("gateway-send", {"ready": True})
                released = True
            if released and staged is None:
                checkpoint = app_json("gateway-staged")
                if checkpoint is not None:
                    staged = checkpoint_evidence(checkpoint, staged=True)
                    if native.process_id() != staged["processId"]:
                        raise RuntimeError("Owned staging process was not confirmed")
                    native.command("shell", "am", "force-stop", PACKAGE)
                    if not stop_process(process) or native.process_id() is not None:
                        raise RuntimeError("Owned staging process did not stop")
                    private_json(directory / "native-staged.json", staged)
                    process = start("hris-resume")
            if staged is not None and restored is None:
                checkpoint = app_json("gateway-restored")
                if checkpoint is not None:
                    restored = checkpoint_evidence(checkpoint)
                    if (restored["processId"] == staged["processId"]
                            or restored["transactionId"] != staged["transactionId"]
                            or native.process_id() != restored["processId"]):
                        raise RuntimeError("Owned queue restoration was not confirmed")
                    private_json(directory / "native-restored.json", {"ready": True})
            if restored is not None and not resumed and read_json(directory / "resume.json") == {"ready": True}:
                write_app("gateway-resume", {"ready": True})
                resumed = True
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
