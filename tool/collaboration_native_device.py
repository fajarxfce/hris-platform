"""Own one debug protocol APK on the dedicated emulator, never a user phone."""
from __future__ import annotations

import fcntl
import hashlib
from pathlib import Path
import subprocess

PACKAGE = "dev.fajar.hris.collaboration.proof"


class NativeDevice:
    def __init__(self, apk, serial, sdk, directory):
        if serial != "emulator-5560":
            raise ValueError("Native proof requires the dedicated emulator-5560")
        self.apk = Path(apk).resolve(strict=True)
        self.serial = serial
        self.sdk = Path(sdk)
        self.adb = str(self.sdk / "platform-tools/adb")
        self.directory = directory
        self.installed = False
        self.port = None
        self.lock = None

    def command(self, *arguments, timeout=15):
        result = subprocess.run([self.adb, "-s", self.serial, *arguments],
                                capture_output=True, text=True, timeout=timeout)
        if result.returncode:
            raise RuntimeError("Owned emulator operation failed")
        return result.stdout.strip()

    def prepare(self):
        self.lock = (self.directory.parent / f"{self.serial}.lock").open("w")
        fcntl.flock(self.lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if self.command("emu", "avd", "name").splitlines()[0] != "hris-mobile-dev":
            raise RuntimeError("Selected emulator is not the dedicated HRIS AVD")
        if self.command("shell", "getprop", "sys.boot_completed") != "1":
            raise RuntimeError("Dedicated emulator is not booted")
        if self.command("shell", "pm", "list", "packages", PACKAGE):
            raise RuntimeError("Proof package already exists; refusing to replace unowned data")
        # Verify the artifact before allowing installation or later cleanup.
        aapt = self.sdk / "build-tools/36.0.0/aapt2"
        result = subprocess.run([str(aapt), "dump", "badging", str(self.apk)],
                                capture_output=True, text=True, timeout=15, check=True)
        if not result.stdout.startswith(f"package: name='{PACKAGE}' ") or "application-debuggable" not in result.stdout:
            raise ValueError("Only the separate debug protocol APK may be installed")
        self.installed = True  # Cover a successful install whose response was lost.
        if "Success" not in self.command("install", str(self.apk), timeout=180):
            raise RuntimeError("Protocol APK installation was not confirmed")
        self.command("shell", "run-as", PACKAGE, "mkdir", "-p", "files/protocol-proof")

    def bind(self, fixture):
        from urllib.parse import urlparse
        port = urlparse(fixture["matrix"]["baseUrl"]).port
        if any(f"tcp:{port}" in line.split()[1:2] for line in self.command("reverse", "--list").splitlines()):
            raise RuntimeError("Proof port already has an emulator mapping")
        self.port = port
        self.command("reverse", f"tcp:{port}", f"tcp:{port}")
        fixture["nativeDevice"] = {"adb": self.adb, "serial": self.serial, "package": PACKAGE}
        return {"apkSha256": hashlib.sha256(self.apk.read_bytes()).hexdigest(),
                "package": PACKAGE, "avd": "hris-mobile-dev",
                "androidApi": self.command("shell", "getprop", "ro.build.version.sdk")}

    def cleanup(self):
        successful = True
        if self.installed:
            try:
                self.command("shell", "am", "force-stop", PACKAGE)
            except (RuntimeError, OSError, subprocess.TimeoutExpired):
                successful = False
            try:
                if "Success" not in self.command("uninstall", PACKAGE, timeout=30):
                    successful = False
            except (RuntimeError, OSError, subprocess.TimeoutExpired):
                successful = False
        if self.port:
            try:
                self.command("reverse", "--remove", f"tcp:{self.port}")
            except (RuntimeError, OSError, subprocess.TimeoutExpired):
                successful = False
        if self.lock:
            self.lock.close()
        return successful
