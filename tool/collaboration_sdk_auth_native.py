"""Observe actual SDK auth rejection on an exclusively owned emulator fixture."""
from pathlib import Path
import argparse
import json
import shlex
import signal
import subprocess
import time
from urllib.parse import urlparse
from collaboration_native_device import NativeDevice, PACKAGE, native_proof_serial, native_diagnostic
from collaboration_hris_native import private_json, read_json, app_write_script, stop_process

FLAGS = ("typedAuthRejection", "liveSyncRejected", "runtimeEntryRejected",
         "localReadPolicyPreserved", "originalKeysPreserved", "ownedRuntimeDrained", "sameStoreCredentialsReplaced", "actualSdkSyncRestored",
         "queueOperationPreserved", "legacyQueueRecovered", "oldAndNewEncrypted", "retainedApiFailureRetried")


def run(apk, sdk, directory):
    directory = Path(directory).resolve(strict=True)
    fixture = read_json(directory / "fixture.json")
    home = urlparse(fixture["baseUrl"])
    if (home.scheme != "http" or home.hostname != "127.0.0.1"
            or not 1024 <= (home.port or 0) <= 65535 or home.username or home.password
            or home.query or home.fragment or home.path not in ("", "/")):
        raise ValueError("Only the owned loopback HRIS server is allowed")
    native = NativeDevice(apk, native_proof_serial(), sdk, directory)
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

    try:
        native.prepare()
        result["artifact"] = native.bind({"matrix": {"baseUrl": fixture["baseUrl"]}})
        request = subprocess.run([native.adb, "-s", native.serial, "shell", "run-as", PACKAGE,
                                  "sh", "-c", shlex.quote(app_write_script("auth-fixture"))],
                                 input=json.dumps(fixture), text=True, capture_output=True, timeout=15)
        if request.returncode:
            raise RuntimeError("Could not deliver owned fixture")
        process = subprocess.Popen([native.adb, "-s", native.serial, "shell", "am", "instrument",
                                    "-w", "-e", "scenario", "hris-auth-rejection",
                                    f"{PACKAGE}/.MatrixProofInstrumentation"],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        until = time.monotonic() + 180
        ready = False
        delivered = False
        while time.monotonic() < until:
            if not ready:
                checkpoint = app_json("auth-ready")
                if checkpoint is not None:
                    if checkpoint != {"ready": True}:
                        raise ValueError("Invalid owned readiness")
                    private_json(directory / "native-ready.json", checkpoint)
                    ready = True
            if ready and not delivered:
                replacement = read_json(directory / "replacement.json")
                if replacement is not None:
                    sent = subprocess.run([native.adb, "-s", native.serial, "shell", "run-as", PACKAGE,
                                           "sh", "-c", shlex.quote(app_write_script("auth-replacement"))],
                                          input=json.dumps(replacement), text=True, capture_output=True, timeout=15)
                    if sent.returncode:
                        raise RuntimeError("Could not deliver original replacement")
                    delivered = True
            completion = app_json("instrumentation")
            if completion is not None:
                result["diagnostic"] = native_diagnostic({"instrumentation": completion})
                evidence = app_json("auth-result")
                if completion.get("complete") is not True or not isinstance(evidence, dict):
                    raise RuntimeError("Owned SDK auth instrumentation failed")
                if not ready or not delivered or evidence.get("complete") is not True or any(evidence.get(flag) is not True for flag in FLAGS):
                    raise ValueError("Invalid owned SDK auth evidence")
                result.update({flag: True for flag in FLAGS})
                result["passed"] = True
                break
            if process.poll() is not None:
                raise RuntimeError("Owned auth instrumentation exited without evidence")
            time.sleep(0.15)
        else:
            raise TimeoutError("Owned auth observation exceeded its deadline")
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
