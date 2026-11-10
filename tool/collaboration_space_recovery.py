"""Actual group task recovery after a native readiness control and owned process death."""
from __future__ import annotations

import argparse
import signal
import subprocess

from collaboration_native_device import PACKAGE
from collaboration_hris_native import private_json
from collaboration_space_original import OriginalDeliveryExperiment, interrupted, checkpoint, space_original_coordinates


def pending_space_original(value, fixture, group, staged):
    """Actual worker staging and retained app originals, distinct from an SDK inventory capture."""
    checkpoint(value)
    fields = {"processId", "logicalId", "spaceId", "conversationId", "transactionId", "originHash",
              "sender", "textHash", "nativeSessionId", "deviceId", "bootstrapOperationId",
              "companyPartitionEmpty", "retainedOriginalCount", "workerStaged", "unboundQueued"}
    if (set(value) != fields or any(value.get(k) is not True for k in
            ("companyPartitionEmpty", "workerStaged", "unboundQueued"))
            or type(value.get("retainedOriginalCount")) is not int or value["retainedOriginalCount"] != 3):
        raise ValueError("Invalid pending-worker staging evidence")
    return space_original_coordinates(value, fixture, group, staged)


class SpaceRecoveryExperiment(OriginalDeliveryExperiment):
    """Retain the original artifact/store/process checks; add an explicit server control handshake."""
    def start(self, phase):
        self.native.command("shell", "run-as", PACKAGE, "rm", "-f", "files/protocol-proof/instrumentation.json")
        scenario = ("space-recovery-" if phase in ("interrupt", "recover") else "space-original-") + phase
        self.process = subprocess.Popen([self.native.adb, "-s", self.native.serial, "shell", "am", "instrument",
            "-w", "-e", "scenario", scenario, f"{PACKAGE}/.MatrixProofInstrumentation"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def await_control(self, seconds):
        if getattr(self, "control_observed", False):
            return
        listening = super().wait_app("listening", seconds)
        if listening.get("nativeSyncActive") is not True or listening.get("startupWakeCleared") is not True:
            raise ValueError("Group control was not isolated from fixture startup")
        if self.expected == 3 and listening.get("queuedSpaceWithoutBinding") is not True:
            raise ValueError("Worker scenario did not start with an unbound local command")
        private_json(self.directory / "listening.json", listening)
        control = super().wait_app("control", seconds)
        if (control.get("durableWake") is not True or control.get("capturedReplayDeduplicated") is not True
                or type(control.get("publicationRevision")) is not int or not 1 <= control["publicationRevision"] <= 2**63 - 1):
            raise ValueError("Native control did not establish an acknowledged durable wake")
        private_json(self.directory / "control.json", control)
        self.control_observed = True

    def wait_app(self, name, seconds=120):
        # The server must see listening before it publishes control; that control permits staging.
        if name in ("space-staged", "ack"):
            self.await_control(seconds)
        value = super().wait_app(name, seconds)
        if name == "ack":
            if value.get("claimedBeforeDelivery") is not True:
                raise ValueError("Original delivery preceded its durable group claim")
            if self.expected == 3 and value.get("pendingMessageClaim") is not True:
                raise ValueError("Space pending command was not durably claimed")
        if name == "recovered" and any(value.get(k) is not True for k in (
                "groupTaskCompleted", "progressAndWakeCompleted", "recoveredWithoutNewControl")):
            raise ValueError("Registered group task did not complete retained work")
        return value

    def validate_space_staging(self, value, group, staged):
        return pending_space_original(value, self.fixture, group, staged)

    def execute(self):
        super().execute()
        self.result.update(actualGroupTask=True, actualNativeReadiness=True,
                           capturedConsumerReplayDeduplicated=True, recoveredWithoutFcm=True)

    def await_interruption_checkpoint(self):
        """Worker proof owns its control/claim handshake, independently of callback-loss proof."""
        return self.wait_app("ack")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--directory", required=True)
    arguments = parser.parse_args()
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    raise SystemExit(SpaceRecoveryExperiment(arguments.apk, arguments.sdk, arguments.directory).run())
