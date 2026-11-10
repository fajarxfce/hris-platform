"""Actual group task recovery after a native readiness control and owned process death."""
from __future__ import annotations

import argparse
import signal
import subprocess

from collaboration_native_device import PACKAGE
from collaboration_hris_native import private_json
from collaboration_space_original import OriginalDeliveryExperiment, interrupted


class SpaceRecoveryExperiment(OriginalDeliveryExperiment):
    """Retain the original artifact/store/process checks; add an explicit server control handshake."""
    def start(self, phase):
        self.native.command("shell", "run-as", PACKAGE, "rm", "-f", "files/protocol-proof/instrumentation.json")
        scenario = ("space-recovery-" if phase in ("interrupt", "recover") else "space-original-") + phase
        self.process = subprocess.Popen([self.native.adb, "-s", self.native.serial, "shell", "am", "instrument",
            "-w", "-e", "scenario", scenario, f"{PACKAGE}/.MatrixProofInstrumentation"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def wait_app(self, name, seconds=120):
        if name == "ack":
            listening = super().wait_app("listening", seconds)
            if listening.get("nativeSyncActive") is not True or listening.get("startupWakeCleared") is not True:
                raise ValueError("Group control was not isolated from fixture startup")
            private_json(self.directory / "listening.json", listening)
            control = super().wait_app("control", seconds)
            if (control.get("durableWake") is not True or control.get("capturedReplayDeduplicated") is not True
                    or type(control.get("publicationRevision")) is not int or not 1 <= control["publicationRevision"] <= 2**63 - 1):
                raise ValueError("Native control did not establish an acknowledged durable wake")
            private_json(self.directory / "control.json", control)
        value = super().wait_app(name, seconds)
        if name == "ack" and value.get("claimedBeforeDelivery") is not True:
            raise ValueError("Original delivery preceded its durable group page claim")
        if name == "recovered" and any(value.get(k) is not True for k in (
                "groupTaskCompleted", "progressAndWakeCompleted", "recoveredWithoutNewControl")):
            raise ValueError("Registered group task did not complete retained work")
        return value

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
