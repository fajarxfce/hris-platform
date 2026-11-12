"""Real SDK key arrival during a lost local receipt reply, quiet observation and reopen."""
from __future__ import annotations

import argparse
import re
import signal
import uuid

from collaboration_event_reads_native import EventReadExperiment, content_hash, staged_events
from collaboration_hris_native import private_json
from collaboration_space_original import checkpoint, interrupted, verified_artifact


def retained_keys(value):
    fields = {"receiptDigest", "recoveryGenerationDigest", "requestedGenerationDigest", "recoveryComplete"}
    if not isinstance(value, dict) or set(value) != fields or value["recoveryComplete"] is not False:
        raise ValueError("Incomplete retained late-key obligation")
    for name in fields - {"recoveryComplete"}:
        content_hash(value[name])
    if (value["receiptDigest"] != value["requestedGenerationDigest"]
            or value["receiptDigest"] == value["recoveryGenerationDigest"]):
        raise ValueError("Late-key recovery generations were collapsed")
    return dict(value)


def late_keys(value):
    flags = {"committedBeforeLostReply", "keyReceivedDuringHeldReply", "durableBeforeBothAcknowledgements",
             "firstReceiptNotRewritten", "sameRuntimeInstance", "rawReceiptsDrained"}
    counts = {"receiptAttempts", "receiptCommits", "nativeAcknowledgements"}
    if not isinstance(value, dict) or set(value) != flags | counts | {"retained"}:
        raise ValueError("Incomplete late-key evidence")
    if any(value[name] is not True for name in flags):
        raise ValueError("Late-key invariant was not demonstrated")
    if any(type(value[name]) is not int for name in counts):
        raise ValueError("Invalid late-key receipt count")
    if (not 2 <= value["receiptAttempts"] <= 4 or value["receiptCommits"] != 2
            or value["nativeAcknowledgements"] != 2):
        raise ValueError("Unbounded or incomplete late-key receipts")
    return value | {"retained": retained_keys(value["retained"])}


def late_key_staged(value, staged):
    checkpoint(value)
    if (set(value) != {"processId", "eventId"} or value["processId"] != staged["processId"]
            or not isinstance(value["eventId"], str)
            or not re.fullmatch(r"\$[!-~]{1,254}", value["eventId"])
            or value["eventId"] in staged["events"].values()):
        raise ValueError("Fresh encrypted key event was not staged separately")
    return dict(value)


def late_key_ready(value, staged):
    checkpoint(value)
    if set(value) != {"processId", "lateKeys"} or value["processId"] == staged["processId"]:
        raise ValueError("Late-key application did not reopen the physical store")
    return value | {"lateKeys": late_keys(value["lateKeys"])}


def quiet_checkpoint(value, ready):
    checkpoint(value)
    if (set(value) != {"processId", "observedMillis"} or value["processId"] != ready["processId"]
            or type(value["observedMillis"]) is not int or not 30_000 <= value["observedMillis"] <= 60_000):
        raise ValueError("Finite late-key quiet interval was not observed")
    return dict(value)


def adopted_keys(value, ready, quiet):
    checkpoint(value)
    fields = {"processId", "lateKeys", "quietMillis", "processCollectorJoined", "runtimeReleased"}
    if (set(value) != fields or value["processId"] != ready["processId"]
            or value["processCollectorJoined"] is not True or value["runtimeReleased"] is not True
            or type(value["quietMillis"]) is not int or value["quietMillis"] != quiet["observedMillis"]):
        raise ValueError("Late-key application ownership did not finish")
    proof = late_keys(value["lateKeys"])
    if proof != ready["lateKeys"]:
        raise ValueError("Late-key evidence changed during observation")
    return value | {"lateKeys": proof}


def reopened_keys(value, adopted):
    checkpoint(value)
    if (set(value) != {"processId", "retained", "runtimeNeverOpened", "databaseReleased"}
            or value["processId"] == adopted["processId"]
            or value["runtimeNeverOpened"] is not True or value["databaseReleased"] is not True):
        raise ValueError("Late-key local store did not reopen independently")
    retained = retained_keys(value["retained"])
    if retained != adopted["lateKeys"]["retained"]:
        raise ValueError("Late-key obligation changed on offline reopen")
    return value | {"retained": retained}


class LateKeyExperiment(EventReadExperiment):
    def execute(self):
        if self.fixture.get("incomingLateKeys") is not True:
            raise ValueError("Late-key scenario was not explicitly selected")
        if any(self.fixture.get(name, False) is not False for name in
               ("sdkIncomingHints", "incomingSources", "incomingWorker", "completePendingIncoming")):
            raise ValueError("Late-key and original-history scenarios must be independent")
        logical = self.fixture.get("lateLogicalId")
        if (not isinstance(logical, str) or str(uuid.UUID(logical)) != logical
                or logical in (self.fixture["logicalId"], self.fixture["secondLogicalId"],
                               self.fixture["firstEditId"], self.fixture["latestEditId"])):
            raise ValueError("Late-key encrypted event requires an independent identity")
        artifact = verified_artifact(self.native.apk)
        if artifact["sdkVersion"] != "26.10.23-hris.8":
            raise ValueError("Late-key acceptance requires SDK8 callbacks")
        self.native.prepare()
        installed = self.native.bind({"matrix": {"baseUrl": self.fixture["baseUrl"]}})
        if artifact["apkSha256"] != installed["apkSha256"]:
            raise RuntimeError("Late-key artifact changed before installation")
        self.result["artifact"] = artifact | installed
        self.write_app("worker-fixture", self.fixture)
        self.start("stage")
        private_json(self.directory / "ready.json", self.wait_app("ready"))
        self.write_app("original-stage-allowed", self.wait_host("stage-allowed"))
        redaction = self.wait_app("redaction-request")
        if (set(redaction) != {"processId", "targetEventId"}
                or not isinstance(redaction["targetEventId"], str)
                or not re.fullmatch(r"\$[!-~]{1,254}", redaction["targetEventId"])):
            raise ValueError("Invalid fixture redaction target")
        private_json(self.directory / "redaction-request.json", redaction)
        self.write_app("original-redaction-completed", self.wait_host("redaction-completed"))
        staged = staged_events(self.wait_app("staged"))
        late = late_key_staged(self.wait_app("late-key-staged"), staged)
        self.finished()
        private_json(self.directory / "staged.json", staged)
        private_json(self.directory / "late-key-staged.json", late)
        self.write_app("original-group", self.wait_host("group"))
        self.start("space")
        ready = late_key_ready(self.wait_app("late-key-ready"), staged)
        private_json(self.directory / "late-key-ready.json", ready)
        self.write_app("original-late-key-observe", self.wait_host("late-key-observe"))
        quiet = quiet_checkpoint(self.wait_app("late-key-quiet"), ready)
        private_json(self.directory / "late-key-quiet.json", quiet)
        self.write_app("original-late-key-finish", self.wait_host("late-key-finish"))
        adopted = adopted_keys(self.wait_app("events"), ready, quiet)
        self.finished()
        private_json(self.directory / "adopted.json", adopted)
        if self.wait_host("offline-allowed") != {"ready": True}:
            raise ValueError("Offline measurement was not armed")
        port = self.native.port
        if not port:
            raise RuntimeError("Late-key offline phase has no owned transport mapping")
        self.native.command("reverse", "--remove", f"tcp:{port}")
        if any(f"tcp:{port}" in line.split()[1:2] for line in self.native.command("reverse", "--list").splitlines()):
            raise RuntimeError("Late-key offline phase still has transport")
        self.native.port = None
        self.start("local")
        reopened = reopened_keys(self.wait_app("local"), adopted)
        self.finished()
        self.result.update(passed=True, physical=staged, lateKeyStaged=late, adopted=adopted,
                           reopened=reopened, offlineTransportRemoved=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--directory", required=True)
    arguments = parser.parse_args()
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    raise SystemExit(LateKeyExperiment(arguments.apk, arguments.sdk, arguments.directory).run())
