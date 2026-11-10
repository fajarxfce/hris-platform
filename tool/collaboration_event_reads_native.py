"""Exact SDK6 event reads before and after original-runtime adoption, on fictional peers."""
from __future__ import annotations

import argparse
import re
import signal
import subprocess

from collaboration_native_device import PACKAGE
from collaboration_hris_native import private_json
from collaboration_space_original import OriginalDeliveryExperiment, checkpoint, interrupted, verified_artifact


def event_coordinates(value):
    names = {"original", "peer", "firstEdit", "latestEdit", "redaction"}
    if not isinstance(value, dict) or set(value) != names:
        raise ValueError("Incomplete exact event coordinates")
    if any(not isinstance(v, str) or not re.fullmatch(r"\$[!-~]{1,254}", v) for v in value.values()):
        raise ValueError("Invalid event identity")
    if len(set(value.values())) != len(names):
        raise ValueError("Event identities were collapsed")
    return dict(value)


def content_hash(value):
    if not isinstance(value, str) or not re.fullmatch(r"[a-f0-9]{64}", value):
        raise ValueError("Invalid canonical content fingerprint")
    return value


def original_window(value):
    """Observe the existing reader without presuming that a presentation anchor is present."""
    fields = {"kind", "code", "originals", "anchorIncluded"}
    kinds = {"Success", "Validation", "Unauthorized", "AccessDenied", "Unavailable", "Network",
             "Timeout", "Service", "Storage", "Unexpected", "Conflict", "NotFound"}
    if (not isinstance(value, dict) or set(value) != fields
            or not isinstance(value.get("kind"), str) or value["kind"] not in kinds):
        raise ValueError("Invalid original-window outcome")
    if not isinstance(value["code"], str) or not re.fullmatch(r"[a-z][a-z0-9_.]{0,95}", value["code"]):
        raise ValueError("Unsafe original-window failure code")
    if type(value["originals"]) is not int or not 0 <= value["originals"] <= 256:
        raise ValueError("Invalid original-window count")
    if type(value["anchorIncluded"]) is not bool:
        raise ValueError("Invalid original-window anchor observation")
    if value["kind"] != "Success" and (value["originals"] != 0 or value["anchorIncluded"]):
        raise ValueError("A failed read cannot publish original facts")
    return dict(value)


def staged_events(value):
    checkpoint(value)
    fields = {"processId", "events", "originalHash", "firstEditHash", "latestEditHash", "companyWindow",
              "missingKeyObserved", "decryptedAfterSync", "redactedTargetSenderPreserved", "bothSdkClosed"}
    if set(value) != fields:
        raise ValueError("Unexpected event-read checkpoint fields")
    flags = {"missingKeyObserved", "decryptedAfterSync", "redactedTargetSenderPreserved", "bothSdkClosed"}
    if any(value[name] is not True for name in flags):
        raise ValueError("Native acquisition sequence was not established")
    return {"processId": value["processId"], "events": event_coordinates(value["events"]),
            **{name: content_hash(value[name]) for name in ["originalHash", "firstEditHash", "latestEditHash"]},
            "companyWindow": original_window(value["companyWindow"]), **{name: True for name in flags}}


def retained_history(value, staged, *, completed=False):
    flags = {"originalTextPreserved", "replacementTextPreserved", "originalSenderTombstone",
             "noOriginalFactForEdit", "noSpaceSendWork", "physicalProviderPreserved"}
    fields = flags | {"messages", "originalEventId", "replacementEventId", "redactedEventId",
                      "originalHash", "replacementHash", "revisionHash", "frontierHash", "pendingHash",
                      "providerReferences", "pendingReferences", "providerExhaustedWithPending"}
    if (not isinstance(value, dict) or set(value) != fields
            or any(value[n] is not True for n in flags)
            or type(completed) is not bool or value["providerExhaustedWithPending"] is not (not completed)
            or type(value["messages"]) is not int or value["messages"] != 1):
        raise ValueError("Incomplete encrypted incoming history evidence")
    expected = {"originalEventId": staged["events"]["original"],
                "replacementEventId": staged["events"]["latestEdit"],
                "redactedEventId": staged["events"]["peer"],
                "originalHash": staged["originalHash"], "replacementHash": staged["latestEditHash"]}
    if any(value[n] != v for n, v in expected.items()):
        raise ValueError("Incoming history changed original/replacement identity")
    for name in ["revisionHash", "frontierHash", "pendingHash"]:
        content_hash(value[name])
    if (type(value["providerReferences"]) is not int or not 5 <= value["providerReferences"] <= 50
            or type(value["pendingReferences"]) is not int
            or value["pendingReferences"] != (0 if completed else value["providerReferences"] - 2)):

        raise ValueError("Provider coverage lost unresolved references")
    return dict(value)


def completed_incoming(value, staged, previous):
    fields = {"resolved", "unresolved", "more", "retryRequired", "emptyRepeatUnchanged", "retained"}
    if (not isinstance(value, dict) or set(value) != fields
            or type(value["resolved"]) is not int or not 1 <= value["resolved"] <= 16
            or value["resolved"] != previous["pendingReferences"]
            or type(value["unresolved"]) is not int or value["unresolved"] != 0
            or value["more"] is not False or value["retryRequired"] is not False
            or value["emptyRepeatUnchanged"] is not True):
        raise ValueError("Incomplete pending-reference completion")
    retained = retained_history(value["retained"], staged, completed=True)
    changed = {"pendingReferences", "providerExhaustedWithPending", "revisionHash", "frontierHash", "pendingHash"}
    if any(retained[n] != v for n, v in previous.items() if n not in changed):
        raise ValueError("Pending completion changed retained content or provider identity")
    if retained["pendingHash"] == previous["pendingHash"]:
        raise ValueError("Pending completion did not change the unresolved inventory")
    return {**value, "retained": retained}


def incoming_history(value, staged, *, completed=False):
    fields = {"retained", "replayUnchanged", "originalJournalUnchanged", "providerPages"}
    if completed:
        fields.add("completed")
    if (not isinstance(value, dict) or set(value) != fields
            or value["replayUnchanged"] is not True or value["originalJournalUnchanged"] is not True
            or type(value["providerPages"]) is not int or not 1 <= value["providerPages"] <= 4):
        raise ValueError("Incoming replay changed history or outgoing work")
    retained = retained_history(value["retained"], staged)
    result = {**value, "retained": retained}
    if completed:
        result["completed"] = completed_incoming(value["completed"], staged, retained)
    return result


def reopened_history(value, adopted, *, completed=False):
    checkpoint(value)
    expected = adopted["incoming"]["completed"] if completed else adopted["incoming"]
    flags = {"sameRevision", "runtimeNeverOpened", "databaseReleased"}
    if (set(value) != flags | {"processId", "incoming"}
            or any(value[n] is not True for n in flags)
            or value["processId"] == adopted["processId"]
            or value["incoming"] != expected["retained"]):
        raise ValueError("Incoming history was not reopened unchanged without a runtime")
    return {**value, "incoming": dict(value["incoming"])}


def worker_incoming(value, staged):
    if (not isinstance(value, dict) or set(value) != {"retained", "worker", "originalJournalUnchanged"}
            or value["originalJournalUnchanged"] is not True):
        raise ValueError("Invalid registered worker evidence")
    worker = value["worker"]
    flags = {"contentBeforeProviderEof", "registeredTask", "emptyRepeatUnchanged", "coveredHintUnchanged",
             "automaticStartup"}
    if (not isinstance(worker, dict) or set(worker) != flags | {"turns", "firstTurnMessages"}
            or any(worker[n] is not True for n in flags)
            or type(worker["turns"]) is not int or not 2 <= worker["turns"] <= 4
            or type(worker["firstTurnMessages"]) is not int or worker["firstTurnMessages"] != 1):
        raise ValueError("Worker did not retain content before EOF and stop when idle")
    return {"worker": dict(worker), "retained": retained_history(value["retained"], staged, completed=True),
            "originalJournalUnchanged": True}


def adopted_events(value, staged, *, completed=False, worker=False):
    if type(worker) is not bool or (worker and completed):
        raise ValueError("Invalid incoming worker scenario")
    checkpoint(value)
    flags = {"logicalRuntimePreserved", "physicalOwnerPreserved", "oldCompanyReadRejected",
             "foreignRuntimeReadRejected", "redactedTargetSenderPreserved", "runtimeReleased"}
    fields = flags | {"processId", "headEventId", "originalHash", "latestEditHash", "redactedEventId",
                      "redactionEventId", "spaceWindow", "incoming"}
    if set(value) != fields or any(value[name] is not True for name in flags):
        raise ValueError("Original runtime ownership was not preserved")
    for name in ["originalHash", "latestEditHash"]:
        if content_hash(value[name]) != staged[name]:
            raise ValueError("Adoption changed acquired content")
    expected = {"headEventId": staged["events"]["latestEdit"], "redactedEventId": staged["events"]["peer"],
                "redactionEventId": staged["events"]["redaction"]}
    if any(value[name] != event for name, event in expected.items()):
        raise ValueError("Adoption changed an event identity")
    return {**value, "spaceWindow": original_window(value["spaceWindow"]),
            "incoming": worker_incoming(value["incoming"], staged) if worker else incoming_history(value["incoming"], staged, completed=completed)}


class EventReadExperiment(OriginalDeliveryExperiment):
    def start(self, phase):
        if phase not in {"stage", "space", "local"}:
            raise ValueError("Unknown event-read phase")
        self.native.command("shell", "run-as", PACKAGE, "rm", "-f", "files/protocol-proof/instrumentation.json")
        self.process = subprocess.Popen([self.native.adb, "-s", self.native.serial, "shell", "am", "instrument",
            "-w", "-e", "scenario", "event-reads-" + phase, f"{PACKAGE}/.MatrixProofInstrumentation"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def execute(self):
        artifact = verified_artifact(self.native.apk)
        self.native.prepare()
        installed = self.native.bind({"matrix": {"baseUrl": self.fixture["baseUrl"]}})
        if artifact["apkSha256"] != installed["apkSha256"]:
            raise RuntimeError("Event-read artifact changed before installation")
        self.result["artifact"] = artifact | installed
        self.write_app("worker-fixture", self.fixture)
        self.start("stage")
        private_json(self.directory / "ready.json", self.wait_app("ready"))
        self.write_app("original-stage-allowed", self.wait_host("stage-allowed"))
        redaction = checkpoint(self.wait_app("redaction-request"))
        if (set(redaction) != {"processId", "targetEventId"}
                or not isinstance(redaction["targetEventId"], str)
                or not re.fullmatch(r"\$[!-~]{1,254}", redaction["targetEventId"])):
            raise ValueError("Invalid fixture redaction target")
        private_json(self.directory / "redaction-request.json", redaction)
        self.write_app("original-redaction-completed", self.wait_host("redaction-completed"))
        staged = staged_events(self.wait_app("staged"))
        self.finished()
        private_json(self.directory / "staged.json", staged)
        self.write_app("original-group", self.wait_host("group"))
        self.start("space")
        completed = self.fixture.get("completePendingIncoming", False)
        if type(completed) is not bool:
            raise ValueError("Invalid pending-reference scenario")
        worker = self.fixture.get("incomingWorker", False)
        adopted = adopted_events(self.wait_app("events"), staged, completed=completed, worker=worker)
        self.finished()
        port = self.native.port
        if not port:
            raise RuntimeError("Incoming offline phase has no owned transport mapping")
        self.native.command("reverse", "--remove", f"tcp:{port}")
        self.native.port = None
        if any(f"tcp:{port}" in line.split()[1:2] for line in self.native.command("reverse", "--list").splitlines()):
            raise RuntimeError("Incoming offline phase still has transport")
        self.start("local")
        reopened = reopened_history(self.wait_app("local"), adopted, completed=completed)
        self.finished()
        self.result.update(passed=True, physical=staged, adopted=adopted,
                           reopened=reopened, offlineTransportRemoved=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--directory", required=True)
    arguments = parser.parse_args()
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    raise SystemExit(EventReadExperiment(arguments.apk, arguments.sdk, arguments.directory).run())
