"""Explicit real FCM acceptance against disposable HRIS/Synapse and emulator owners."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import signal
import subprocess
import time
import traceback
import xml.etree.ElementTree as ET
from urllib.parse import urlparse

from collaboration_hris_native import app_write_script, read_json
from collaboration_native_device import NativeDevice, PACKAGE
from collaboration_product_device import PRODUCT, SERIAL, ProductFcmDevice, action_node, input_text, started_by_firebase


def until(read, predicate, seconds):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        value = read()
        if predicate(value):
            return value
        time.sleep(0.3)
    raise TimeoutError("Owned FCM phase exceeded its deadline")


def setup_product(device, fixture, directory):
    def mark(step):
        (directory / "product-progress.json").write_text(json.dumps({"stage": step}))
    mark("opening_login")
    device.command("shell", "am", "start", "-W", "-n", PRODUCT + "/dev.fajar.hris.MainActivity", timeout=35)
    device.click("Skip")
    device.find("Sign in", seconds=30)
    for index, value in enumerate((fixture["email"], fixture["password"])):
        fields = [n for n in device.tree().iter("node") if n.get("class") == "android.widget.EditText"]
        if len(fields) != 2:
            raise ValueError("Product login does not expose its two fields")
        device.tap(fields[index])
        device.command("shell", "input", "text", input_text(value))
    device.command("shell", "input", "keyevent", "KEYCODE_BACK")
    mark("submitting_login")
    device.click("Sign in")
    mark("selecting_company")
    device.click("People fixture", seconds=35)
    mark("opening_notification_settings")
    device.click("Account", seconds=35)
    device.click("Settings", scroll=True)
    device.click("Inbox notifications", scroll=True)
    mark("waiting_for_push_registration")
    until(lambda: read_json(directory / "server-status.json"), lambda v: v is not None and v.get("registrations") == 1, 35)
    device.capture("product-notifications-enabled")
    device.command("shell", "input", "keyevent", "KEYCODE_BACK")
    device.click("Home")
    device.click("Chat", scroll=True)
    device.click("Fictional native SDK peer", seconds=30)
    device.click("Chat on this device", seconds=30)
    mark("activating_chat")
    activate_chat(device, mark)
    mark("waiting_for_matrix_pusher")
    until(lambda: read_json(directory / "server-status.json"), lambda v: v is not None and v.get("pushers") == 1, 35)
    mark("chat_ready")
    device.capture("product-chat-ready")


def activate_chat(device, mark):
    deadline = time.monotonic() + 90
    for _ in range(25):
        tree = device.tree()
        for label in ("Open chat", "Activate chat", "Continue", "Refresh"):
            node = action_node(tree, label)
            if node is None:
                continue
            mark("chat_activation_" + label.lower().replace(" ", "_"))
            # Do not wait for a stale Refresh label after provisioning asynchronously becomes ready.
            device.tap(node)
            if label == "Open chat":
                device.find("Fictional native SDK peer", seconds=30)
                return
            break
        if time.monotonic() >= deadline:
            break
        time.sleep(0.5)
    raise TimeoutError("Product chat setup did not complete")


def write_peer(device, name, value):
    result = subprocess.run([device.adb, "-s", SERIAL, "shell", "run-as", PACKAGE, "sh", "-c", "'" + app_write_script(name) + "'"],
                            input=json.dumps(value).encode(), capture_output=True, timeout=15)
    if result.returncode:
        raise RuntimeError("Private peer input could not be written")


def read_peer(device, name):
    if not isinstance(name, str) or not re.fullmatch(r"[a-z][a-z0-9-]{0,63}", name):
        raise ValueError("Invalid peer result name")
    path = f"files/protocol-proof/{name}.json"
    # shell v2 propagates the remote exit status; exec-out returns zero even when cat fails.
    value = subprocess.run([device.adb, "-s", SERIAL, "shell", "run-as", PACKAGE, "cat", path], capture_output=True, timeout=10)
    if value.returncode == 1 and not value.stdout and value.stderr.strip() == f"cat: {path}: No such file or directory".encode():
        return None  # The peer publishes each result atomically, so absence is a pending state.
    if value.returncode:
        raise RuntimeError("Private peer result could not be read")
    if len(value.stdout) > 16384:
        raise ValueError("Oversized peer result")
    result = json.loads(value.stdout)
    if not isinstance(result, dict):
        raise ValueError("Invalid peer result object")
    return result


def reply_to_notification(device, body):
    device.command("shell", "cmd", "statusbar", "expand-notifications")
    device.find("Reply", seconds=30)
    device.capture("product-chat-notification")
    device.click("Reply")
    editors = [n for n in device.tree().iter("node") if n.get("class") == "android.widget.EditText" and n.get("focused") == "true"]
    if len(editors) != 1:
        raise ValueError("SystemUI reply editor did not acquire focus")
    device.command("shell", "input", "text", input_text(body))
    device.capture("product-chat-reply-editor")
    send = [n for n in device.tree().iter("node") if n.get("resource-id", "").endswith("/remote_input_send") and n.get("enabled") == "true"]
    if len(send) != 1:
        raise ValueError("SystemUI reply send action is unavailable")
    device.tap(send[0])


def product_diagnostics(device, directory):
    """Private bounded debugging evidence; never upload raw SDK/OS logs as acceptance output."""
    captured = {}
    for name, args in (
        ("product-logcat.private.txt", ["logcat", "-d", "-t", "3000", "-v", "threadtime"]),
        ("product-jobs.private.txt", ["shell", "dumpsys", "jobscheduler"]),
    ):
        try:
            value = subprocess.run([device.adb, "-s", SERIAL, *args], capture_output=True, timeout=10)
            if value.returncode:
                raise RuntimeError("Owned product diagnostics unavailable")
            with os.fdopen(os.open(directory / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as output:
                output.write(value.stdout[:2 * 1024 * 1024])
            captured[name] = {"saved": True, "truncated": len(value.stdout) > 2 * 1024 * 1024}
        except Exception:
            captured[name] = {"saved": False}
    return captured


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--directory", type=Path, required=True)
    args = parser.parse_args()
    directory = args.directory.resolve(strict=True)
    if directory.parent.name != "product-chat-fcm" or directory.stat().st_mode & 0o077:
        raise ValueError("Private owned fixture directory required")
    fixture = read_json(directory / "fixture.json")
    origin = urlparse(fixture["baseUrl"])
    if origin.scheme != "http" or origin.hostname != "127.0.0.1" or not 1024 <= origin.port <= 65535:
        raise ValueError("Only the isolated loopback API is accepted")
    sdk = Path(os.environ["ANDROID_HOME"])
    apk = Path(os.environ["HRIS_COLLAB_PRODUCT_FCM_APK"]).resolve(strict=True)
    proof_apk = Path(os.environ["HRIS_COLLAB_NATIVE_APK"]).resolve(strict=True)
    device = ProductFcmDevice(sdk, os.environ["HRIS_COLLAB_PRODUCT_SOURCE_IMAGE"], directory)
    peer = NativeDevice(proof_apk, SERIAL, sdk, directory)
    instrument = None
    stage = "starting_emulator"
    result = {"passed": False, "phoneTouched": False, "serial": SERIAL,
              "productApkSha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
              "proofApkSha256": hashlib.sha256(proof_apk.read_bytes()).hexdigest()}
    began = time.monotonic()
    def stop(_signal, _frame):
        raise KeyboardInterrupt()
    for event in (signal.SIGTERM, signal.SIGINT):
        signal.signal(event, stop)
    try:
        device.start()
        stage = "installing_product"
        device.install_product(apk)
        device.reverse(18084, origin.port)
        if origin.port != 18084:
            device.reverse(origin.port, origin.port)
        stage = "preparing_encrypted_peer"
        peer.prepare()
        stage = "setting_up_product"
        setup_product(device, fixture, directory)
        stage = "starting_encrypted_peer"
        write_peer(device, "product-peer", {key: fixture[key] for key in ("baseUrl", "peer", "roomId", "body", "reply", "logicalId")})
        instrument = subprocess.Popen([device.adb, "-s", SERIAL, "shell", "am", "instrument", "-w", "-e", "scenario", "hris-product-peer", PACKAGE + "/.MatrixProofInstrumentation"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        until(lambda: read_peer(device, "product-peer-ready"), lambda v: v is not None and v.get("ready") is True, 60)
        stage = "ordinary_product_death"
        device.command("shell", "input", "keyevent", "KEYCODE_HOME")
        previous = device.pid()
        if previous is None:
            raise RuntimeError("Product process was absent before controlled death")
        device.command("shell", "run-as", PRODUCT, "kill", "-9", str(previous))
        until(device.pid, lambda v: v is None, 10)
        result["killedProductPid"] = previous
        stage = "waiting_for_real_fcm"
        write_peer(device, "product-peer-send", {"send": True})
        sent = until(lambda: read_peer(device, "product-peer-sent"), lambda v: v is not None, 60)
        result["sentEventId"] = sent["eventId"]
        current = until(device.pid, lambda v: v is not None and v != previous, 90)
        events = device.command("logcat", "-b", "events", "-d", "-v", "brief", "-s", "am_proc_start:I")
        if not started_by_firebase(events, current):
            raise RuntimeError("Process start did not identify the Firebase receiver/service")
        result.update(productStartedByPush=True, restartedProductPid=current)
        # The sender is the sole push-producing actor in this disposable tenant. No UI/manual worker wake.
        until(lambda: read_json(directory / "server-status.json"), lambda v: v is not None and v.get("delivered", 0) > 0, 30)
        stage = "replying_through_system_ui"
        reply_to_notification(device, fixture["reply"])
        result["notificationShown"] = True
        stage = "waiting_for_encrypted_reply"
        reply = until(lambda: read_peer(device, "product-peer-result"), lambda v: v is not None and v.get("receivedReply") is True, 100)
        result["encryptedReplyReceived"] = reply["encrypted"] is True
        result["replyLogicalId"] = reply["logicalId"]
        result["replyEventId"] = reply["eventId"]
        native = until(lambda: read_peer(device, "instrumentation"), lambda v: v is not None, 15)
        if native.get("complete") is not True:
            raise RuntimeError("Encrypted peer cleanup failed")
        device.capture("product-chat-reply-acknowledged")
        result["passed"] = True
    except BaseException as failure:
        result.update(failureCategory=type(failure).__name__, failedStage=stage)
        result["failureFrames"] = [f"{Path(frame.filename).name}:{frame.name}:{frame.lineno}"
                                   for frame in traceback.extract_tb(failure.__traceback__)[-6:]]
        if device.process is not None and device.process.poll() is None:
            result["privateDiagnostics"] = product_diagnostics(device, directory)
            try:
                device.capture("product-failure")
                result["failureScreenshotSaved"] = True
            except Exception:
                result["failureScreenshotSaved"] = False
            try:
                (directory / "product-failure.xml").write_text(ET.tostring(device.tree(), encoding="unicode"))
                result["failureUiSaved"] = True
            except Exception:
                result["failureUiSaved"] = False
            try:
                native = read_peer(device, "instrumentation")
                if native:
                    result["peerFailure"] = {k: native[k] for k in ("complete", "failureCategory", "failureFrames") if k in native}
            except Exception:
                result["peerDiagnosticUnavailable"] = True
    finally:
        if instrument is not None:
            instrument.terminate()
            try:
                instrument.wait(timeout=5)
            except subprocess.TimeoutExpired:
                instrument.kill()
                instrument.wait(timeout=5)
        try:
            peer_clean = peer.cleanup()
        except Exception:
            peer_clean = False
        try:
            device_clean = device.close()
        except Exception:
            device_clean = False
        result.update(peerCleanedUp=peer_clean, deviceCleanedUp=device_clean)
        result["cleanedUp"] = peer_clean and device_clean
        result["passed"] = result["passed"] and result["cleanedUp"]
        result["elapsedSeconds"] = round(time.monotonic() - began, 3)
        (directory / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        print(json.dumps(result), flush=True)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
