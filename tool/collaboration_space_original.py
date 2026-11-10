"""Actual original-only delivery, process loss and local recovery on an owned emulator."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import signal
import subprocess
import time
from urllib.parse import urlparse
import uuid
import zipfile

from collaboration_native_device import NativeDevice, PACKAGE, native_proof_serial, native_diagnostic
from collaboration_hris_native import (private_json, read_json, app_write_script,
                                      stop_process, disconnect_owned_gateway)

SDK6_X86_64 = "ecffbab72fd5dd29d656bc4757ea423bb4bccf0444140af0480684bc32761278"
SDK6_X86_64_BYTES = 111204848


def verified_artifact(path):
    """Exact SDK6 release bytes, deterministically stripped by NDK28.2.13676358."""
    path = Path(path).resolve(strict=True)
    if not 1 <= path.stat().st_size <= 256 * 1024 * 1024:
        raise ValueError("Invalid original-delivery APK size")
    with zipfile.ZipFile(path) as archive:
        names = [entry for entry in archive.infolist() if entry.filename.endswith("/libmatrix_sdk_ffi.so")]
        if len(names) != 1 or names[0].filename != "lib/x86_64/libmatrix_sdk_ffi.so" or names[0].file_size != SDK6_X86_64_BYTES:
            raise ValueError("Expected the immutable SDK6 x86_64 library")
        with archive.open(names[0]) as library:
            if hashlib.file_digest(library, "sha256").hexdigest() != SDK6_X86_64:
                raise ValueError("Original delivery requires the exact SDK6 release")
    with path.open("rb") as apk:
        return {"apkSha256": hashlib.file_digest(apk, "sha256").hexdigest(),
                "sdkVersion": "26.10.23-hris.6", "abi": "x86_64", "librarySha256": SDK6_X86_64}


def checkpoint(value):
    if not isinstance(value, dict) or type(value.get("processId")) is not int or not 1 <= value["processId"] <= 2147483647:
        raise ValueError("Invalid original process checkpoint")
    return value


def originals(value, fixture):
    checkpoint(value)
    if value.get("bothSdkClosed") is not True or value.get("recipientClosedBeforeDelivery") is not True:
        raise ValueError("Original SDK ownership was not closed")
    records = value.get("originals")
    ids = [fixture["logicalId"], fixture["secondLogicalId"]]
    if not isinstance(records, list) or len(records) != 2 or len(set(ids)) != 2:
        raise ValueError("Expected exactly two new fictional originals")
    result = {}
    for record in records:
        if not isinstance(record, dict) or record.get("logicalId") not in ids:
            raise ValueError("Unexpected original message")
        logical = record["logicalId"]
        for key in ("logicalId", "nativeSessionId", "deviceId", "bootstrapOperationId"):
            if str(uuid.UUID(record[key])) != record[key]:
                raise ValueError("Invalid original coordinate")
        for key in ("textHash", "originHash"):
            if not re.fullmatch(r"[a-f0-9]{64}", record.get(key, "")):
                raise ValueError("Invalid original content fingerprint")
        expected = hashlib.sha256((fixture["body"] + "-" + str(ids.index(logical))).encode()).hexdigest()
        if record["textHash"] != expected or record.get("sender") != fixture["sender"]["userId"]:
            raise ValueError("Original content or sender changed")
        transaction = record.get("transactionId")
        if not isinstance(transaction, str) or not re.fullmatch(r"[!-~]{1,255}", transaction):
            raise ValueError("Invalid original transaction")
        result[logical] = transaction
    if set(result) != set(ids) or len(set(result.values())) != 2:
        raise ValueError("Original transactions are not distinct")
    return result


def delivery_diagnostic(value, expected=2):
    """Strict metadata-only outcome; never copy arbitrary native diagnostic fields."""
    if type(expected) is not int or expected not in (2, 3):
        raise ValueError("Invalid opted-in original count")
    fields = {"reason", "confirmed", "unresolved", "receiptCount", "states", "elapsedMillis"}
    if not isinstance(value, dict) or set(value) != fields:
        raise ValueError("Invalid original delivery diagnostic fields")
    if not isinstance(value["reason"], str) or value["reason"] not in {"NONE", "DRAINED", "TIMED_OUT", "STOPPED", "FAILED", "UNRESOLVED"}:
        raise ValueError("Invalid original delivery reason")
    for name in ("confirmed", "unresolved", "receiptCount"):
        if type(value[name]) is not int or not 0 <= value[name] <= expected:
            raise ValueError("Invalid original delivery count")
    if type(value["elapsedMillis"]) is not int or not 0 <= value["elapsedMillis"] <= 120_000:
        raise ValueError("Invalid original delivery duration")
    states = value["states"]
    if not isinstance(states, list) or len(states) != expected or any(not isinstance(s, str) or s not in {"SENT", "STAGED", "OTHER"} for s in states):
        raise ValueError("Invalid original delivery states")
    return {**value, "states": states[:]}


def space_original(value, fixture, group, staged):
    """The third original is new Space work on the unchanged physical SDK, never relabeled."""
    checkpoint(value)
    fields = {"processId", "logicalId", "spaceId", "conversationId", "transactionId", "originHash",
              "sender", "textHash", "nativeSessionId", "deviceId", "bootstrapOperationId",
              "companyPartitionEmpty", "completeOriginalCount"}
    if set(value) != fields or value.get("companyPartitionEmpty") is not True or type(value.get("completeOriginalCount")) is not int or value["completeOriginalCount"] != 3:
        raise ValueError("Invalid mixed original checkpoint")
    return space_original_coordinates(value, fixture, group, staged)


def space_original_coordinates(value, fixture, group, staged):
    """Shared original coordinates; each caller separately validates its evidence schema."""
    logical = fixture.get("spaceLogicalId")
    if not isinstance(logical, str) or logical in (fixture["logicalId"], fixture["secondLogicalId"]) or value["logicalId"] != logical:
        raise ValueError("Expected a distinct new Space original")
    for key in ("logicalId", "spaceId", "conversationId", "nativeSessionId", "deviceId", "bootstrapOperationId"):
        if not isinstance(value[key], str) or str(uuid.UUID(value[key])) != value[key]:
            raise ValueError("Invalid Space original coordinate")
    if value["spaceId"] != group["spaceId"] or value["conversationId"] != group["conversationId"]:
        raise ValueError("Space original changed its logical owner")
    for key in ("nativeSessionId", "deviceId", "bootstrapOperationId"):
        if any(value[key] != old[key] for old in staged["originals"]):
            raise ValueError("Space original replaced the physical SDK")
    for key in ("textHash", "originHash"):
        if not isinstance(value[key], str) or not re.fullmatch(r"[a-f0-9]{64}", value[key]):
            raise ValueError("Invalid Space original content fingerprint")
    expected = hashlib.sha256((fixture["body"] + "-2").encode()).hexdigest()
    if value["textHash"] != expected or value["sender"] != fixture["sender"]["userId"]:
        raise ValueError("Space original content or sender changed")
    if not isinstance(value["transactionId"], str) or not re.fullmatch(r"[!-~]{1,255}", value["transactionId"]) or any(value["transactionId"] == old["transactionId"] for old in staged["originals"]):
        raise ValueError("Space original reused a physical transaction")
    return value.copy()


def lost_receipt_checkpoint(value, expected=2):
    """One app receipt, both SDK originals removed, before the owned hard process death."""
    checkpoint(value)
    if type(expected) is not int or expected not in (2, 3):
        raise ValueError("Invalid opted-in original count")
    if expected == 3 and (type(value.get("spaceReceiptCount")) is not int or value["spaceReceiptCount"] != 0):
        raise ValueError("Space callback was already retained before interruption")
    if (type(value.get("receiptCount")) is not int or value["receiptCount"] != 1
            or type(value.get("queuedOriginalCount")) is not int or value["queuedOriginalCount"] != 0
            or value.get("interruptedAfterSdkRemoval") is not True
            or value.get("journalDurable") is not True or value.get("projectionNotWritten") is not True):
        raise ValueError("Lost callback boundary was not demonstrated")
    return value


class OriginalDeliveryExperiment:
    """Own exactly one proof package, reverse and instrumentation child; retain failures."""
    def __init__(self, apk, sdk, directory):
        self.directory = Path(directory).resolve(strict=True)
        self.fixture = read_json(self.directory / "fixture.json")
        home = urlparse(self.fixture["baseUrl"])
        if (home.scheme != "http" or home.hostname != "127.0.0.1" or not 1024 <= (home.port or 0) <= 65535
                or home.username or home.password or home.path not in ("", "/") or home.query or home.fragment):
            raise ValueError("Expected a private loopback fixture")
        if not self.fixture.get("body", "").startswith("fictional-hris-worker-") or len(self.fixture["body"]) > 150:
            raise ValueError("Only new fictional messages may be used")
        self.native = NativeDevice(apk, native_proof_serial(), sdk, self.directory)
        self.expected = 2
        if "spaceLogicalId" in self.fixture:
            value = self.fixture["spaceLogicalId"]
            if not isinstance(value, str) or str(uuid.UUID(value)) != value or value in (self.fixture["logicalId"], self.fixture["secondLogicalId"]):
                raise ValueError("Invalid opted-in Space original")
            self.expected = 3
        self.process = None
        self.result = {"passed": False, "cleanup": False}

    def app_json(self, name):
        try:
            raw = self.native.command("exec-out", "run-as", PACKAGE, "cat", f"files/protocol-proof/{name}.json")
            if len(raw) > 16384:
                raise ValueError("Oversized original checkpoint")
            return json.loads(raw)
        except (RuntimeError, json.JSONDecodeError):
            return None

    def write_app(self, name, value):
        data = json.dumps(value)
        if len(data.encode()) > 16384:
            raise ValueError("Oversized original fixture")
        result = subprocess.run([self.native.adb, "-s", self.native.serial, "shell", "run-as", PACKAGE,
                                 "sh", "-c", shlex.quote(app_write_script(name))],
                                input=data, text=True, capture_output=True, timeout=15)
        if result.returncode:
            raise RuntimeError("Original fixture transfer failed")

    def start(self, phase):
        self.native.command("shell", "run-as", PACKAGE, "rm", "-f", "files/protocol-proof/instrumentation.json")
        self.process = subprocess.Popen([self.native.adb, "-s", self.native.serial, "shell", "am", "instrument",
            "-w", "-e", "scenario", "space-original-" + phase, f"{PACKAGE}/.MatrixProofInstrumentation"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def wait_app(self, name, seconds=120):
        until = time.monotonic() + seconds
        while time.monotonic() < until:
            value = self.app_json("original-" + name)
            if value is not None:
                return checkpoint(value)
            state = self.app_json("instrumentation")
            if state is not None:
                # The app writes its checkpoint before terminal instrumentation state, but these
                # are separate reads. A publication between them must not become a false loss.
                value = self.app_json("original-" + name)
                if value is not None:
                    return checkpoint(value)
                self.result["diagnostic"] = native_diagnostic({"instrumentation": state})
                if name == "recovered":
                    delivery = self.app_json("original-delivery-result")
                    if delivery is not None:
                        self.result["delivery"] = delivery_diagnostic(delivery, getattr(self, "expected", 2))
                raise RuntimeError("Original instrumentation ended before its checkpoint")
            time.sleep(0.1)
        raise TimeoutError("Original application phase timed out")

    def wait_host(self, name):
        until = time.monotonic() + 120
        while time.monotonic() < until:
            value = read_json(self.directory / (name + ".json"))
            if value is not None:
                return value
            time.sleep(0.1)
        raise TimeoutError("Original server phase timed out")

    def finished(self):
        self.process.wait(timeout=20)
        state = self.app_json("instrumentation")
        if self.process.returncode or not state or state.get("complete") is not True:
            raise RuntimeError("Original owner cleanup failed")

    def kill_owned(self, value):
        pid = checkpoint(value)["processId"]
        if self.native.process_id() != pid:
            raise RuntimeError("Original process changed before interruption")
        self.native.command("shell", "run-as", PACKAGE, "kill", "-9", str(pid))
        if not stop_process(self.process):
            raise RuntimeError("Original driver did not stop")
        until = time.monotonic() + 10
        while time.monotonic() < until:
            if self.native.process_id() is None:
                return pid
            time.sleep(0.1)
        raise RuntimeError("Original Android process did not exit")

    def validate_space_staging(self, value, group, staged):
        return space_original(value, self.fixture, group, staged)

    def await_interruption_checkpoint(self):
        """Original-only proof: lose the second callback after its SDK transaction is gone."""
        first = self.wait_app("first-ack")
        if type(first.get("receiptCount")) is not int or first["receiptCount"] != 1:
            raise ValueError("Expected only the first durable app receipt")
        private_json(self.directory / "first-ack.json", first)
        ack = lost_receipt_checkpoint(self.wait_app("ack"), getattr(self, "expected", 2))
        self.result["lostCallbackAfterSdkRemoval"] = True
        return ack

    def execute(self):
        artifact = verified_artifact(self.native.apk)
        self.native.prepare()
        installed = self.native.bind({"matrix": {"baseUrl": self.fixture["baseUrl"]}})
        if artifact["apkSha256"] != installed["apkSha256"]:
            raise RuntimeError("Original proof artifact changed before installation")
        self.result["artifact"] = artifact | installed
        self.write_app("worker-fixture", self.fixture)
        self.start("stage")
        ready = self.wait_app("ready")
        private_json(self.directory / "ready.json", ready)
        self.write_app("original-stage-allowed", self.wait_host("stage-allowed"))
        staged = self.wait_app("staged")
        mapping = originals(staged, self.fixture)
        self.finished()
        private_json(self.directory / "staged.json", staged)
        group = self.wait_host("group")
        self.write_app("original-group", group)
        self.start("interrupt")
        if self.expected == 3:
            new = self.validate_space_staging(self.wait_app("space-staged"), group, staged)
            private_json(self.directory / "space-staged.json", new)
            mapping[new["logicalId"]] = new["transactionId"]
        ack = self.await_interruption_checkpoint()
        if ack.get("journalDurable") is not True or ack.get("projectionNotWritten") is not True or ack.get("logicalId") not in mapping:
            raise ValueError("Interruption did not follow a durable original receipt")
        first_pid = self.kill_owned(ack)
        private_json(self.directory / "ack.json", ack)
        self.start("recover")
        recovered = self.wait_app("recovered")
        delivery = self.app_json("original-delivery-result")
        if delivery is not None:
            self.result["delivery"] = delivery_diagnostic(delivery, getattr(self, "expected", 2))
        if recovered["processId"] == first_pid or recovered.get("originalsRetained") is not True or recovered.get("recipientStayedOffline") is not True:
            raise ValueError("Original process recovery was not demonstrated")
        records = recovered.get("confirmed")
        if not isinstance(records, list) or len(records) != self.expected or {r["logicalId"]: r["transactionId"] for r in records} != mapping:
            raise ValueError("Recovery replaced or omitted an original")
        self.finished()
        private_json(self.directory / "recovered.json", recovered)
        disconnect_owned_gateway(self.native)
        self.start("offline")
        offline = self.wait_app("offline")
        self.finished()
        if offline["processId"] in (first_pid, recovered["processId"]) or offline.get("localBeforeTransport") is not True or offline.get("confirmedCount") != self.expected:
            raise ValueError("Local original projection was not recovered offline")
        self.result.update(passed=True, actualProcessDeaths=1, offlineRestart=True,
                           originalCount=self.expected, spaceOriginal=self.expected == 3, offline=offline)

    def cleanup(self):
        stopped = stop_process(self.process)
        if self.result["passed"]:
            return self.native.cleanup() and stopped
        successful = stopped
        self.result["retainedProofPackage"] = self.native.installed
        try:
            if self.native.installed:
                self.native.command("shell", "am", "force-stop", PACKAGE)
        except (RuntimeError, OSError, subprocess.TimeoutExpired):
            successful = False
        try:
            if self.native.port:
                disconnect_owned_gateway(self.native)
        except (RuntimeError, OSError, subprocess.TimeoutExpired):
            successful = False
        finally:
            if self.native.lock:
                self.native.lock.close()
        return successful

    def run(self):
        try:
            self.execute()
        except Exception as failure:
            self.result["failureCategory"] = type(failure).__name__
        finally:
            self.result["cleanup"] = self.cleanup()
            self.result["passed"] = self.result["passed"] and self.result["cleanup"]
            private_json(self.directory / "result.json", self.result)
        return 0 if self.result["passed"] else 1


def interrupted(_number, _frame):
    raise InterruptedError("Original proof driver interrupted")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--directory", required=True)
    arguments = parser.parse_args()
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    raise SystemExit(OriginalDeliveryExperiment(arguments.apk, arguments.sdk, arguments.directory).run())
