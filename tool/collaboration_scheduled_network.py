"""Restore only connectivity changed by this test on the owned HRIS emulator."""
from __future__ import annotations

import time


class EmulatorNetwork:
    def __init__(self, native):
        if native.serial != "emulator-5560" or not native.installed:
            raise ValueError("Connectivity proof requires the claimed emulator package")
        self.native = native
        self.before = {key: self.read(key) for key in ("airplane_mode_on", "wifi_on", "mobile_data")}
        if (self.before["airplane_mode_on"] != "0" or self.before["wifi_on"] != "1"
                or self.before["mobile_data"] not in ("0", "1")):
            raise ValueError("Start the owned emulator with ordinary connected Wi-Fi")
        self.changed = False

    def read(self, key):
        return self.native.command("shell", "settings", "get", "global", key, timeout=5)

    def disable(self):
        # Set before the first mutation so a lost command response still requires restoration.
        self.changed = True
        self.native.command("shell", "cmd", "connectivity", "airplane-mode", "enable", timeout=5)
        self.native.command("shell", "svc", "wifi", "disable", timeout=5)
        self.native.command("shell", "svc", "data", "disable", timeout=5)

    def restore(self):
        if not self.changed:
            return True
        self.native.command("shell", "cmd", "connectivity", "airplane-mode", "disable", timeout=5)
        self.native.command("shell", "svc", "wifi", "enable", timeout=5)
        self.native.command("shell", "svc", "data",
                            "enable" if self.before["mobile_data"] == "1" else "disable", timeout=5)
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            if all(self.read(key) == value for key, value in self.before.items()):
                self.changed = False
                return True
            time.sleep(0.1)
        return False
