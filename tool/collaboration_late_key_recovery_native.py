"""Resume real retained key generations through the registered app worker after process loss."""
from __future__ import annotations

import argparse
import signal

from collaboration_event_reads_native import content_hash, retained_history
from collaboration_hris_native import private_json
from collaboration_late_keys_native import LateKeyExperiment
from collaboration_space_original import checkpoint, interrupted


def recovered_history(value, staged, late, retained):
    extras = {"lateEventId", "lateHash", "messageOrder", "recoveryComplete",
              "registeredWorkerDrained", "receiptDigest"}
    if (not isinstance(value, dict) or not extras <= set(value)
            or type(value.get("messages")) is not int or value["messages"] != 2
            or value["lateEventId"] != late["eventId"]
            or value["recoveryComplete"] is not True or value["registeredWorkerDrained"] is not True
            or value["receiptDigest"] != retained["receiptDigest"]):
        raise ValueError("Late-key recovery did not preserve and complete the retained obligation")
    content_hash(value["lateHash"])
    order = value["messageOrder"]
    if (not isinstance(order, list) or len(order) != 2
            or any(not isinstance(item, str) for item in order)
            or set(order) != {late["eventId"], staged["events"]["original"]}):
        raise ValueError("Late-key projection omitted or duplicated a message")
    # Preserve the existing original/edit/redaction contract; the second message has its own proof.
    original = {k: v for k, v in value.items() if k not in extras} | {"messages": 1}
    retained_history(original, staged, completed=True, company=True)
    return dict(value)


def recovered_checkpoint(value, prior, staged, late, retained, *, offline):
    checkpoint(value)
    if (set(value) != {"processId", "recovered", "runtimeNeverOpened", "resourcesReleased"}
            or value["processId"] == prior["processId"]
            or value["runtimeNeverOpened"] is not offline or value["resourcesReleased"] is not True):
        raise ValueError("Late-key recovery did not use and release a separate process")
    proof = recovered_history(value["recovered"], staged, late, retained)
    if offline and proof != prior["recovered"]:
        raise ValueError("Recovered encrypted history changed while reopening offline")
    return value | {"recovered": proof}


class LateKeyRecoveryExperiment(LateKeyExperiment):
    def execute(self):
        if self.fixture.get("recoverLateKeys") is not True:
            raise ValueError("Late-key worker recovery was not explicitly selected")
        super().execute()
        retained_result = dict(self.result)
        self.result["passed"] = False
        private_json(self.directory / "offline-complete.json", retained_result)
        if self.wait_host("recovery-allowed") != {"ready": True}:
            raise ValueError("Online recovery was not armed")
        installed = self.native.bind({"matrix": {"baseUrl": self.fixture["baseUrl"]}})
        if installed["apkSha256"] != self.result["artifact"]["apkSha256"]:
            raise RuntimeError("Recovery artifact changed")
        staged, late = self.result["physical"], self.result["lateKeyStaged"]
        retained = self.result["reopened"]["retained"]
        self.start("late-recovery")
        recovered = recovered_checkpoint(self.wait_app("late-recovered"), self.result["reopened"],
                                         staged, late, retained, offline=False)
        self.finished()
        private_json(self.directory / "recovery-ready.json", recovered)
        if self.wait_host("recovered-offline-allowed") != {"ready": True}:
            raise ValueError("Recovered offline measurement was not armed")
        port = self.native.port
        if not port:
            raise RuntimeError("Recovery has no owned transport mapping")
        self.native.command("reverse", "--remove", f"tcp:{port}")
        if any(f"tcp:{port}" in line.split()[1:2] for line in self.native.command("reverse", "--list").splitlines()):
            raise RuntimeError("Recovered offline phase still has transport")
        self.native.port = None
        self.start("late-recovered-local")
        local = recovered_checkpoint(self.wait_app("late-recovered-local"), recovered,
                                     staged, late, retained, offline=True)
        self.finished()
        self.result.update(passed=True, recovered=recovered, recoveredLocal=local,
                           recoveredOfflineTransportRemoved=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--directory", required=True)
    args = parser.parse_args()
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    raise SystemExit(LateKeyRecoveryExperiment(args.apk, args.sdk, args.directory).run())
