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

from collaboration_native_device import NativeDevice, PACKAGE, native_proof_serial, native_diagnostic


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


def disconnect_owned_gateway(native):
    port = native.port
    if type(port) is not int or not 1024 <= port <= 65535:
        raise ValueError("Missing owned gateway mapping")
    local = f"tcp:{port}"
    mappings = [line.split() for line in native.command("reverse", "--list").splitlines()]
    selected = [fields for fields in mappings if len(fields) == 3 and fields[1] == local]
    if len(selected) != 1 or selected[0][2] != local:
        raise RuntimeError("Owned gateway mapping changed before disconnect")
    native.command("reverse", "--remove", local)
    if any(local in line.split()[1:2] for line in native.command("reverse", "--list").splitlines()):
        raise RuntimeError("Owned gateway mapping remained after disconnect")
    native.port = None


def proof_evidence(value, confirmed_queue=False):
    if type(confirmed_queue) is not bool:
        raise ValueError("Invalid owned queue scenario")
    if not isinstance(value, dict) or value.get("encryptedBothDirections") is not True or value.get("logicalIdsPreserved") is not True or value.get("syncMode") != "classic":
        raise ValueError("Invalid owned exchange evidence")
    output = {"encryptedBothDirections": True, "logicalIdsPreserved": True, "syncMode": "classic"}
    for name in ("protectedStoreReopened", "missingRecordPreserved", "wrongPassphrasePreserved", "exclusiveOwner",
                 "queueRestored", "transactionReused", "identityAfterRestart",
                 "messageIntentRestored", "queueMappingRecovered", "queueWriterExclusive",
                 "perRoomDeliveryIsolated", "heldQueueRestored", "nativeDeliveryOwned",
                 "commonSenderOwned", "receiptJournalRecovered", "projectionInterrupted",
                 "nativeReadReceiptsExchanged", "nativePrivateReceiptIsolated", "nativeReadReceiptStoreRestored",
                 "nativeLocalReadIntentPrepared", "nativeLocalReadIntentStoreRestored", "nativeReadReceiptReplayAdmitted", "nativeReadReceiptAckUncertaintyRecovered",
                 "originalHistoryWindowOwned", "historyBodiesRestored", "commonHistoryAcquisitionOwned", "latestHistoryHeadOwned", "providerHistoryPageOwned",
                 "commonProviderHistoryAcquisitionOwned", "providerHistoryReferencesRestored",
                 "providerHistoryReferencesReconciled", "providerHistoryRestartPreserved", "historyRuntimeReopened", "ackBeforeJournalRecovered", "completionCleanupRecovered",
                 "coldHistoryRecoveredOffline", "gatewayUnavailableAfterProcessDeath",
                 "quotedReplyDecrypted", "quotedReplyRecovered", "quotedReplyRecoveredOffline"):
        if value.get(name) is not True:
            raise ValueError("Invalid owned protected store evidence")
        output[name] = True
    for name in ("senderEventId", "recipientEventId", "ackSenderEventId", "senderTransaction", "recipientTransaction", "ackTransaction"):
        field = value.get(name)
        pattern = r"\$[!-~]{1,254}" if name.endswith("EventId") else r"[A-Za-z0-9._~-]{1,128}"
        if not isinstance(field, str) or not re.fullmatch(pattern, field):
            raise ValueError("Invalid owned receipt")
        output[name] = field
    if len({output[name] for name in ("senderEventId", "recipientEventId", "ackSenderEventId")}) != 3:
        raise ValueError("Owned receipts must be distinct")
    if output["ackTransaction"] == output["senderTransaction"]:
        raise ValueError("Owned crash transactions must be distinct")
    output["historyWindow"] = history_evidence(value.get("historyWindow"))
    if confirmed_queue:
        for name in ("confirmedNativeQueueRestored", "confirmedNativeQueueSettledOffline", "confirmedNativeQueueRetryNoop"):
            if value.get(name) is not True:
                raise ValueError("Invalid owned confirmed queue evidence")
            output[name] = True
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


def checkpoint_evidence(value, staged=False, acknowledged=False, completed=False):
    if not isinstance(value, dict):
        raise ValueError("Invalid owned queue checkpoint")
    pid = value.get("processId")
    transaction = value.get("transactionId")
    if type(pid) is not int or not 1 <= pid <= 2147483647:
        raise ValueError("Invalid owned process identifier")
    if not isinstance(transaction, str) or not re.fullmatch(r"[A-Za-z0-9._~-]{1,128}", transaction):
        raise ValueError("Invalid owned pending transaction")
    if acknowledged:
        if (value.get("ackObserved") is not True or value.get("receiptAbsent") is not True
                or value.get("quotedReplyDecrypted") is not True):
            raise ValueError("Invalid owned acknowledgement checkpoint")
    if completed:
        if value.get("archiveRetained") is not True or value.get("receiptRetained") is not True:
            raise ValueError("Invalid owned completion checkpoint")
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
    confirmed_queue = fixture.get("retainConfirmedNativeQueue", False)
    if type(confirmed_queue) is not bool:
        raise ValueError("Invalid owned queue scenario")
    home = urlparse(fixture["baseUrl"])
    if (home.scheme != "http" or home.hostname != "127.0.0.1"
            or not 1024 <= (home.port or 0) <= 65535 or home.username or home.password
            or home.query or home.fragment or home.path not in ("", "/")):
        raise ValueError("Only the owned loopback HRIS server is allowed")
    native = NativeDevice(apk, native_proof_serial(), sdk, directory)
    process = None
    result = {"passed": False, "cleanup": False}
    stage = "prepare"

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
        stage = "bind"
        result["artifact"] = native.bind({"matrix": {"baseUrl": fixture["baseUrl"]}})
        stage = "deliver_fixture"
        write_app("gateway-fixture", fixture)
        stage = "starting_initial_process"
        process = start("hris-stage")
        until = time.monotonic() + 300
        ready = False
        released = False
        staged = None
        restored = None
        resumed = False
        acknowledged = None
        ack_restored = None
        ack_resumed = False
        archived = None
        archive_restored = None
        archive_resumed = False
        stage = "awaiting_initial_process"
        while time.monotonic() < until:
            completion = app_json("instrumentation")
            if completion is not None:
                stage = "validating_completion"
                result["diagnostic"] = native_diagnostic({"instrumentation": completion})
                result["diagnostic"].pop("replyComplete")
                result["diagnostic"].pop("resultComplete")
                if completion.get("complete") is not True:
                    raise RuntimeError("Owned native instrumentation failed")
                if staged is None or restored is None or not resumed or acknowledged is None or ack_restored is None or not ack_resumed or archived is None or archive_restored is None or not archive_resumed:
                    raise RuntimeError("Owned process restart was not completed")
                proof = app_json("gateway-result")
                result["proof"] = proof_evidence(proof, confirmed_queue)
                if (type(proof.get("processId")) is not int or proof["processId"] != archive_restored["processId"]
                        or result["proof"]["senderTransaction"] != staged["transactionId"]
                        or result["proof"]["ackTransaction"] != acknowledged["transactionId"]):
                    raise RuntimeError("Owned pending receipt did not survive restart")
                result["proof"]["processRestarted"] = True
                result["proof"]["processRestartedAfterAck"] = True
                result["proof"]["processRestartedAfterCompletion"] = True
                result["passed"] = True
                break
            if not ready and app_json("gateway-ready") == {"ready": True}:
                private_json(directory / "native-ready.json", {"ready": True})
                ready = True
            if ready and not released and read_json(directory / "send.json") == {"ready": True}:
                stage = "releasing_send"
                write_app("gateway-send", {"ready": True})
                released = True
            if released and staged is None:
                checkpoint = app_json("gateway-staged")
                if checkpoint is not None:
                    stage = "validating_staged"
                    staged = checkpoint_evidence(checkpoint, staged=True)
                    if native.process_id() != staged["processId"]:
                        raise RuntimeError("Owned staging process was not confirmed")
                    native.command("shell", "am", "force-stop", PACKAGE)
                    if not stop_process(process) or native.process_id() is not None:
                        raise RuntimeError("Owned staging process did not stop")
                    private_json(directory / "native-staged.json", staged)
                    stage = "awaiting_queue_restore"
                    process = start("hris-resume")
            if staged is not None and restored is None:
                checkpoint = app_json("gateway-restored")
                if checkpoint is not None:
                    stage = "validating_queue_restore"
                    restored = checkpoint_evidence(checkpoint)
                    if (restored["processId"] == staged["processId"]
                            or restored["transactionId"] != staged["transactionId"]
                            or native.process_id() != restored["processId"]):
                        raise RuntimeError("Owned queue restoration was not confirmed")
                    private_json(directory / "native-restored.json", {"ready": True})
            if restored is not None and not resumed and read_json(directory / "resume.json") == {"ready": True}:
                stage = "awaiting_acknowledgement"
                write_app("gateway-resume", {"ready": True})
                resumed = True
            if resumed and acknowledged is None:
                checkpoint = app_json("gateway-acknowledged")
                if checkpoint is not None:
                    stage = "validating_acknowledgement"
                    acknowledged = checkpoint_evidence(checkpoint, acknowledged=True)
                    if (acknowledged["processId"] != restored["processId"]
                            or acknowledged["transactionId"] == staged["transactionId"]
                            or native.process_id() != acknowledged["processId"]):
                        raise RuntimeError("Owned acknowledgement process was not confirmed")
                    native.command("shell", "am", "force-stop", PACKAGE)
                    if not stop_process(process) or native.process_id() is not None:
                        raise RuntimeError("Owned acknowledgement process did not stop")
                    private_json(directory / "native-acknowledged.json", acknowledged)
                    stage = "awaiting_acknowledgement_restore"
                    process = start("hris-ack-recover")
            if acknowledged is not None and ack_restored is None:
                checkpoint = app_json("gateway-ack-restored")
                if checkpoint is not None:
                    stage = "validating_acknowledgement_restore"
                    ack_restored = checkpoint_evidence(checkpoint)
                    if (ack_restored["processId"] in (staged["processId"], restored["processId"])
                            or ack_restored["transactionId"] != acknowledged["transactionId"]
                            or native.process_id() != ack_restored["processId"]):
                        raise RuntimeError("Owned acknowledgement restoration was not confirmed")
                    private_json(directory / "native-ack-restored.json", ack_restored)
                    stage = "awaiting_archive"
                    write_app("gateway-ack-resume", {"ready": True})
                    ack_resumed = True
            if ack_resumed and archived is None:
                checkpoint = app_json("gateway-completed")
                if checkpoint is not None:
                    stage = "validating_archive"
                    archived = checkpoint_evidence(checkpoint, completed=True)
                    if (archived["processId"] != ack_restored["processId"]
                            or archived["transactionId"] != acknowledged["transactionId"]
                            or native.process_id() != archived["processId"]):
                        raise RuntimeError("Owned completion process was not confirmed")
                    native.command("shell", "am", "force-stop", PACKAGE)
                    if not stop_process(process) or native.process_id() is not None:
                        raise RuntimeError("Owned completion process did not stop")
                    private_json(directory / "native-completed.json", archived)
                    stage = "disconnecting_gateway"
                    disconnect_owned_gateway(native)
                    stage = "awaiting_offline_restore"
                    process = start("hris-completion-recover")
            if archived is not None and archive_restored is None:
                checkpoint = app_json("gateway-completion-restored")
                if checkpoint is not None:
                    stage = "validating_offline_restore"
                    archive_restored = checkpoint_evidence(checkpoint)
                    if (archive_restored["processId"] in (staged["processId"], restored["processId"], ack_restored["processId"])
                            or archive_restored["transactionId"] != archived["transactionId"]
                            or native.process_id() != archive_restored["processId"]):
                        raise RuntimeError("Owned completion restoration was not confirmed")
                    private_json(directory / "native-completion-restored.json", archive_restored)
                    stage = "awaiting_offline_completion"
                    write_app("gateway-completion-resume", {"ready": True})
                    archive_resumed = True
            if process.poll() is not None:
                raise RuntimeError("Owned native instrumentation exited without a result")
            time.sleep(0.15)
        else:
            raise TimeoutError("Owned native exchange exceeded its deadline")
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        result["failureCategory"] = type(error).__name__
        result["failureStage"] = stage
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
