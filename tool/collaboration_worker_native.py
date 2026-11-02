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
from collaboration_native_device import NativeDevice, PACKAGE, native_proof_serial, native_diagnostic


def checkpoint(value, state="QUEUED", restored=False):
    if state not in ("PREPARED", "QUEUED", "FROZEN_DRAFT", "CANCELLED_DRAFT", "COMPLETED") or not isinstance(value, dict) or value.get("state") != state or value.get("taskKey") != "hris.collaboration-delivery":
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
    result = {"processId": pid, "messageId": message, "bodySha256": digest,
              "state": state, "taskKey": "hris.collaboration-delivery"}
    if state == "FROZEN_DRAFT":
        revision = value.get("draftRevision")
        if (type(value.get("pendingCommandCount")) is not int or value["pendingCommandCount"] != 0
                or not isinstance(revision, str) or str(uuid.UUID(revision)) != revision):
            raise ValueError("Frozen draft requires its original revision and an empty outbox")
        result.update(draftRevision=revision, pendingCommandCount=0)
    if state == "CANCELLED_DRAFT":
        cancelled_id, cancelled_digest = cancelled_identity(value, message)
        revision = value.get("cancelledDraftRevision")
        if (not isinstance(revision, str) or str(uuid.UUID(revision)) != revision
                or type(value.get("pendingCommandCount")) is not int or value["pendingCommandCount"] != 1):
            raise ValueError("Cancelled draft requires its original revision and one independent pending command")
        result.update(cancelledMessageId=cancelled_id, cancelledBodySha256=cancelled_digest,
                      cancelledDraftRevision=revision, pendingCommandCount=1)
        for flag in ("cancelledArchiveRetained", "cancelledReceiptAbsent", "cancelledReplayDidNotSchedule"):
            if value.get(flag) is not True:
                raise ValueError("Missing retained cancellation checkpoint")
            result[flag] = True
    return result


def cancelled_identity(value, primary):
    cancelled_id, digest = value.get("cancelledMessageId"), value.get("cancelledBodySha256")
    if (not isinstance(cancelled_id, str) or str(uuid.UUID(cancelled_id)) != cancelled_id or cancelled_id == primary
            or not isinstance(digest, str) or not re.fullmatch(r"[a-f0-9]{64}", digest)):
        raise ValueError("Invalid distinct cancellation identity")
    return cancelled_id, digest


def evidence(value, queued, restored):
    initial_state = queued.get("state") if isinstance(queued, dict) else None
    if initial_state not in ("QUEUED", "FROZEN_DRAFT", "CANCELLED_DRAFT"):
        raise ValueError("Invalid original worker state")
    queued = checkpoint(queued, initial_state)
    restored = checkpoint(restored, initial_state)
    result = checkpoint(value, "COMPLETED")
    if value.get("initialState", "QUEUED") != initial_state:
        raise ValueError("Worker completion changed its original preparation boundary")
    result["initialState"] = initial_state
    if initial_state == "FROZEN_DRAFT":
        if queued["draftRevision"] != restored["draftRevision"]:
            raise ValueError("Restored draft revision changed before worker recovery")
        for flag in ("frozenDraftRecovered", "outboxEmptyBeforeRecovery", "draftConfirmedCleared"):
            if value.get(flag) is not True:
                raise ValueError("Missing frozen-draft recovery evidence")
            result[flag] = True
    if initial_state == "CANCELLED_DRAFT":
        cancelled_id, digest = cancelled_identity(value, result["messageId"])
        if (any(queued[key] != restored[key] for key in ("cancelledMessageId", "cancelledBodySha256", "cancelledDraftRevision"))
                or cancelled_id != queued["cancelledMessageId"] or digest != queued["cancelledBodySha256"]):
            raise ValueError("Cancelled intent changed across recovery")
        result.update(cancelledMessageId=cancelled_id, cancelledBodySha256=digest)
        for flag in ("cancelledArchiveRetained", "cancelledReceiptAbsent", "cancelledReplayDidNotSchedule",
                     "cancelledDraftConfirmedCleared", "cancelledAbsentFromHistory"):
            if value.get(flag) is not True:
                raise ValueError("Missing cancellation recovery evidence")
            result[flag] = True
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
                "completionRetired", "repeatedWorkerComplete", "foregroundHistoryPaged",
                "foregroundStoppedHistoryRetained"):
        if value.get(key) is not True:
            raise ValueError("Missing worker proof")
        result[key] = True
    pages, references, pending = (value.get(key) for key in ("historyPages", "providerReferences", "pendingStateReferences"))
    if (type(pages) is not int or not 2 <= pages <= 12 or type(references) is not int or not 176 <= references <= 224
            or type(pending) is not int or not 0 <= pending <= 20):
        raise ValueError("Invalid bounded foreground history progress")
    peers = value.get("historyPeerEvents")
    if (not isinstance(peers, list) or len(peers) != 175
            or any(not isinstance(item, str) or not re.fullmatch(r"\$[!-~]{1,254}", item) for item in peers)
            or len(set(peers)) != 175 or event in peers):
        raise ValueError("Invalid encrypted history seed events")
    return result | {"eventId": event, "passes": passes, "historyPages": pages,
                     "providerReferences": references, "pendingStateReferences": pending,
                     "historyPeerEvents": list(peers), "incoming": incoming_evidence(value.get("incoming")),
                     "priority": priority_evidence(value.get("priority"), peers[0])}


def priority_evidence(value, oldest):
    if not isinstance(value, dict):
        raise ValueError("Missing native priority evidence")
    flags = ("priorityWithoutOutbox", "priorityWithoutSelection", "priorityQueueRetainedAfterReopen",
             "priorityOriginalRetainedAfterReopen", "priorityOldestOfflineEventRecovered",
             "priorityRuntimeReleased", "priorityTraversalUnchanged", "priorityGlobalWakeRetained",
             "priorityHintCompleted")
    if any(value.get(key) is not True for key in flags):
        raise ValueError("Incomplete native priority evidence")
    result = {key: True for key in flags}
    for key, minimum, maximum in (("priorityPasses", 1, 3), ("prioritySavedOriginals", 2, 175)):
        count = value.get(key)
        if type(count) is not int or not minimum <= count <= maximum:
            raise ValueError("Unbounded native priority evidence")
        result[key] = count
    event = value.get("priorityEventId")
    if not isinstance(event, str) or not re.fullmatch(r"\$[!-~]{1,254}", event) or event != oldest:
        raise ValueError("Priority must recover the oldest offline peer event")
    return result | {"priorityEventId": event}


def incoming_evidence(value):
    if not isinstance(value, dict):
        raise ValueError("Missing incoming recovery evidence")
    flags = ("incomingWithoutOutbox", "incomingWithoutSelection", "incomingFreshNativeAdmission",
             "incomingOriginalAfterOfflineSeed", "incomingHistoryRetainedAfterReopen", "incomingRuntimeReleased",
             "incomingAllOriginalsRecovered", "incomingTraversalRetainedAfterReopen", "incomingBrowseUnchanged")
    if any(value.get(key) is not True for key in flags):
        raise ValueError("Incomplete incoming recovery evidence")
    result = {key: True for key in flags}
    for key, minimum, maximum in (("incomingPasses", 2, 40), ("incomingSavedOriginals", 176, 176),
                                  ("incomingContainerReopens", 2, 40)):
        count = value.get(key)
        if type(count) is not int or not minimum <= count <= maximum:
            raise ValueError("Unbounded incoming recovery evidence")
        result[key] = count
    if result["incomingPasses"] != result["incomingContainerReopens"]:
        raise ValueError("Each incoming turn must survive a container reopen")
    return result


def progress_evidence(value, incoming=False):
    if not isinstance(value, dict):
        raise ValueError("Missing worker progress")
    if not incoming:
        phase = value.get("phase")
        if phase not in ("restore", "delivery", "seed", "priority", "incoming", "foreground", "complete"):
            raise ValueError("Invalid worker phase")
        result = {"phase": phase}
        for key, minimum, maximum in (("seeded", 0, 175), ("elapsedMillis", 0, 480000)):
            count = value.get(key)
            if type(count) is not int or not minimum <= count <= maximum:
                raise ValueError("Invalid worker phase count")
            result[key] = count
        return result
    result = {}
    for key, minimum, maximum in (("pass", 1, 40), ("originals", 0, 176), ("references", 0, 256)):
        count = value.get(key)
        if type(count) is not int or not minimum <= count <= maximum:
            raise ValueError("Invalid incoming progress count")
        result[key] = count
    for key in ("hasNext", "hasPendingSeek", "retryRequired"):
        if type(value.get(key)) is not bool:
            raise ValueError("Invalid incoming progress flag")
        result[key] = value[key]
    if value.get("result") not in ("Complete", "Retry", "Blocked"):
        raise ValueError("Invalid incoming progress result")
    result["result"] = value["result"]
    for key, pattern in (("failureKind", r"[A-Za-z]{1,32}"), ("failureCode", r"[a-z][a-z0-9_.]{0,95}")):
        item = value.get(key)
        if not isinstance(item, str) or not re.fullmatch(pattern, item):
            raise ValueError("Invalid incoming progress failure")
        result[key] = item
    return result


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
    initial_state = fixture.get("initialState", "QUEUED")
    if initial_state not in ("QUEUED", "FROZEN_DRAFT", "CANCELLED_DRAFT"):
        raise ValueError("Unsupported worker preparation boundary")
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
        deadline = time.monotonic() + 480
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
                    queued = checkpoint(observed, initial_state)
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
                    restored = checkpoint(observed, initial_state, restored=True)
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
        for name, key, incoming_progress in (("worker-progress", "phaseProgress", False),
                                             ("worker-incoming-progress", "incomingProgress", True)):
            observed = app_json(name)
            if observed is not None:
                try:
                    result[key] = progress_evidence(observed, incoming_progress)
                except ValueError:
                    result[key + "Rejected"] = True
        incoming = app_json("worker-incoming")
        if incoming is not None:
            try:
                result["incomingCheckpoint"] = incoming_evidence(incoming)
            except ValueError:
                result["incomingCheckpointRejected"] = True
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
