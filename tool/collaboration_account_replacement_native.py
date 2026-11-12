"""Retire a real native account while its durable room-key reply is still held."""
from __future__ import annotations

import argparse
import signal
import subprocess

from collaboration_event_reads_native import EventReadExperiment, content_hash
from collaboration_hris_native import private_json
from collaboration_native_device import PACKAGE
from collaboration_space_original import interrupted


def account_replacement(value):
    flags = {"receiptCommittedBeforeReplacement", "replacementParentPublished",
             "previousRuntimeClosed", "replacementCannotReadOriginalReceipt",
             "lateReplyNotAcknowledged", "originalReceiptRestored", "originalPrivateIdentityPreserved"}
    counts = {"receiptWrites": 1, "nativeAcknowledgements": 0}
    if (not isinstance(value, dict) or set(value) != flags | set(counts) | {"receiptDigest"}
            or any(value[name] is not True for name in flags)
            or any(type(value[name]) is not int or value[name] != count for name, count in counts.items())):
        raise ValueError("Incomplete native account replacement evidence")
    content_hash(value["receiptDigest"])
    return dict(value)


def offline_receipt(value, replacement):
    if not isinstance(value, dict):
        raise ValueError("Missing offline account receipt")
    digest = content_hash(value.get("incomingReceiptDigest"))
    if digest != account_replacement(replacement)["receiptDigest"]:
        raise ValueError("Account replacement lost the original durable receipt")
    return digest


class AccountReplacementExperiment(EventReadExperiment):
    def execute(self):
        if self.fixture.get("accountReplacement") is not True:
            raise ValueError("Native account replacement was not explicitly selected")
        super().execute()

    def start(self, phase):
        if phase == "local":
            private_json(self.directory / "online-complete.json", {"ready": True})
            if self.wait_host("offline-allowed") != {"ready": True}:
                raise ValueError("Offline account measurement was not armed")
        if phase != "space":
            return super().start(phase)
        self.native.command("shell", "run-as", PACKAGE, "rm", "-f", "files/protocol-proof/instrumentation.json")
        self.process = subprocess.Popen([self.native.adb, "-s", self.native.serial, "shell", "am", "instrument",
            "-w", "-e", "scenario", "event-reads-account-replacement", f"{PACKAGE}/.MatrixProofInstrumentation"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def wait_app(self, phase):
        value = super().wait_app(phase)
        if phase == "events":
            self.result["accountReplacement"] = account_replacement(value.get("accountReplacement"))
            # The original validator still checks every event, history, and release invariant.
            return {name: item for name, item in value.items() if name != "accountReplacement"}
        if phase == "local":
            self.result["offlineReceiptDigest"] = offline_receipt(value, self.result["accountReplacement"])
            return {name: item for name, item in value.items() if name != "incomingReceiptDigest"}
        return value


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--directory", required=True)
    args = parser.parse_args()
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    raise SystemExit(AccountReplacementExperiment(args.apk, args.sdk, args.directory).run())
