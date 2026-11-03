"""Own one disposable emulator image; this driver cannot select a physical device."""
from pathlib import Path
import json
import os
import re
import socket
import subprocess
import time
import xml.etree.ElementTree as ET

PRODUCT = "dev.fajar.hris.mobile.dev"
SERIAL = "emulator-5562"


def input_text(value):
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9 @._!:+-]{1,256}", value):
        raise ValueError("Only bounded fictional fixture input is allowed")
    return value.replace(" ", "%s")


def center(node):
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        raise ValueError("Invalid UI bounds")
    x1, y1, x2, y2 = map(int, match.groups())
    if not (0 <= x1 < x2 <= 10000 and 0 <= y1 < y2 <= 10000):
        raise ValueError("Invalid UI bounds")
    return str((x1 + x2) // 2), str((y1 + y2) // 2)


def started_by_firebase(events, pid):
    components = ("com.google.firebase.iid.FirebaseInstanceIdReceiver", "dev.fajar.hris.StarterMessagingService")
    for line in events.splitlines():
        if "am_proc_start" not in line or "[" not in line or "]" not in line:
            continue
        fields = [part.strip() for part in line[line.index("[") + 1:line.rindex("]")].split(",")]
        if len(fields) >= 6 and fields[1] == str(pid) and fields[3] == PRODUCT:
            return any(component in fields[-1] for component in components)
    return False


def action_node(tree, label):
    """Resolve an enabled label to its current Compose button in this exact UI snapshot."""
    parents = {child: parent for parent in tree.iter() for child in parent}
    for node in tree.iter("node"):
        if node.get("enabled") != "true" or label not in (node.get("text"), node.get("content-desc")):
            continue
        current = node
        for _ in range(12):
            if current is None or current.get("enabled") == "false":
                break
            if current.get("clickable") == "true":
                return current
            current = parents.get(current)
    return None


class ProductFcmDevice:
    def __init__(self, sdk, image, directory, serial=SERIAL):
        if serial != SERIAL:
            raise ValueError("Product FCM proof requires the owned emulator")
        self.sdk, self.source, self.directory = Path(sdk), Path(image), Path(directory)
        self.adb = str(self.sdk / "platform-tools/adb")
        self.process = None
        self.ports = []
        self.image = self.directory / "userdata.img"

    def command(self, *args, timeout=20):
        result = subprocess.run([self.adb, "-s", SERIAL, *args], capture_output=True, timeout=timeout)
        if result.returncode:
            raise RuntimeError("Owned product emulator command failed")
        if len(result.stdout) > 2 * 1024 * 1024:
            raise ValueError("Owned emulator output exceeded limit")
        return result.stdout.decode().strip()

    def start(self):
        state = subprocess.run([self.adb, "-s", SERIAL, "get-state"], capture_output=True, timeout=10)
        if state.returncode == 0 or self.image.exists():
            raise RuntimeError("Product fixture emulator/image already exists")
        if not self.source.is_absolute() or not self.source.is_file():
            raise ValueError("Explicit existing source image required")
        for port in (5562, 5563):
            with socket.socket() as reservation:
                reservation.bind(("127.0.0.1", port))
        for path in Path("/proc").glob("[0-9]*/cmdline"):
            try:
                arguments = path.read_bytes().split(b"\0")
            except (OSError, ProcessLookupError):
                continue
            if os.fsencode(self.source) in arguments:
                raise RuntimeError("Source image currently has an owner")
        subprocess.run(["cp", "--reflink=auto", "--sparse=always", str(self.source), str(self.image)], check=True, timeout=60)
        args = [str(self.sdk / "emulator/emulator"), "-avd", "hris-mobile-dev", "-port", "5562",
                "-data", str(self.image), "-no-window", "-no-snapshot", "-no-audio", "-no-boot-anim",
                "-gpu", "swiftshader_indirect", "-memory", "1536", "-cores", "2", "-feature", "-Vulkan"]
        with (self.directory / "emulator.log").open("w") as log:
            self.process = subprocess.Popen(args, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        (self.directory / "emulator-owner.json").write_text(json.dumps({"pid": self.process.pid, "serial": SERIAL, "command": args}))
        self._wait_for_boot()

    def _wait_for_boot(self):
        deadline = time.monotonic() + 100
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise RuntimeError("Owned emulator exited before boot")
            try:
                result = subprocess.run([self.adb, "-s", SERIAL, "shell", "getprop", "sys.boot_completed"],
                                        capture_output=True, timeout=min(10, max(0.1, deadline - time.monotonic())))
            except subprocess.TimeoutExpired:
                # A booting guest may not answer one probe. Keep the original total deadline;
                # installation, provider and product-command timeouts still fail immediately.
                continue
            if result.returncode == 0 and result.stdout.strip() == b"1":
                if self.command("emu", "avd", "name").splitlines()[0] != "hris-mobile-dev":
                    raise RuntimeError("Unexpected AVD")
                if self.command("shell", "getprop", "ro.kernel.qemu") != "1":
                    raise RuntimeError("Emulator property missing")
                return
            time.sleep(min(1, max(0, deadline - time.monotonic())))
        raise TimeoutError("Owned emulator boot deadline")

    def install_product(self, apk):
        if self.process is None or self.process.poll() is not None:
            raise RuntimeError("Owned emulator is not running")
        value = subprocess.run([str(self.sdk / "build-tools/36.0.0/aapt2"), "dump", "badging", str(apk)], capture_output=True, text=True, timeout=20, check=True).stdout
        if not value.startswith(f"package: name='{PRODUCT}' ") or "application-debuggable" not in value:
            raise ValueError("Only explicit devDebug product fixture APK is allowed")
        # Only the disposable image is changed. The source image and phone are never selected.
        if self.command("shell", "pm", "list", "packages", PRODUCT):
            self.command("uninstall", PRODUCT, timeout=40)
        self.command("install", "--no-incremental", str(apk), timeout=120)
        self.command("shell", "pm", "grant", PRODUCT, "android.permission.POST_NOTIFICATIONS")

    def reverse(self, local, remote):
        if not all(isinstance(p, int) and 1024 <= p <= 65535 for p in (local, remote)):
            raise ValueError("Invalid private API port")
        if any(f"tcp:{local}" in line.split()[1:2] for line in self.command("reverse", "--list").splitlines()):
            raise RuntimeError("API reverse port already owned")
        self.ports.append(local)  # A lost response still owns the attempted mapping.
        self.command("reverse", f"tcp:{local}", f"tcp:{remote}")

    def tree(self):
        self.command("shell", "uiautomator", "dump", "/sdcard/product-fcm.xml")
        return ET.fromstring(self.command("shell", "cat", "/sdcard/product-fcm.xml"))

    def tap(self, node):
        self.command("shell", "input", "tap", *center(node))

    def find(self, label, seconds=20, scroll=False, actionable=False):
        deadline = time.monotonic() + seconds
        for attempt in range(20):
            tree = self.tree()
            action = action_node(tree, label)
            if action is not None:
                return action
            nodes = [n for n in tree.iter("node") if n.get("enabled") == "true" and label in (n.get("text"), n.get("content-desc"))]
            if nodes and not actionable:
                return nodes[0]
            if time.monotonic() >= deadline:
                break
            if scroll and attempt < 4:
                self.command("shell", "input", "swipe", "480", "1600", "480", "600", "300")
            time.sleep(0.3)
        raise TimeoutError("Expected product UI state was not reached")

    def click(self, label, seconds=20, scroll=False):
        self.tap(self.find(label, seconds, scroll, actionable=True))

    def capture(self, name):
        if not re.fullmatch(r"[a-z][a-z0-9-]{0,50}", name):
            raise ValueError("Invalid capture name")
        with (self.directory / f"{name}.png").open("wb") as output:
            subprocess.run([self.adb, "-s", SERIAL, "exec-out", "screencap", "-p"], stdout=output, stderr=subprocess.PIPE, timeout=20, check=True)

    def pid(self):
        value = subprocess.run([self.adb, "-s", SERIAL, "shell", "pidof", PRODUCT], capture_output=True, text=True, timeout=10)
        if value.returncode == 1 and not value.stdout.strip() and not value.stderr.strip():
            return None
        if value.returncode or not re.fullmatch(r"[1-9][0-9]{0,9}", value.stdout.strip()):
            raise RuntimeError("Product process identity unavailable")
        return int(value.stdout.strip())

    def close(self):
        failures = []
        if self.process is not None and self.process.poll() is None:
            for port in self.ports:
                try:
                    self.command("reverse", "--remove", f"tcp:{port}")
                except Exception:
                    failures.append("reverse")
            self.process.terminate()
            try:
                self.process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=5)
        state = subprocess.run([self.adb, "-s", SERIAL, "get-state"], capture_output=True, timeout=10)
        if self.process is not None and state.returncode == 0:
            failures.append("transport")
        if not failures:
            # Only this instance's private image and sidecar are disposable.
            self.image.unlink(missing_ok=True)
            self.image.with_suffix(".img.qcow2").unlink(missing_ok=True)
        return not failures
