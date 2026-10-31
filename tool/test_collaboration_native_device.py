"""Native proof cannot target a phone, replace an app or leak owned mappings."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from collaboration_native_device import NativeDevice, PACKAGE, native_diagnostic


class NativeDeviceTest(unittest.TestCase):
    def test_native_diagnostics_keep_only_bounded_categories_and_code_frames(self):
        value = {"replyComplete": True, "resultComplete": False, "token": "private-fixture",
                 "instrumentation": {"complete": False, "failureCategory": "IllegalStateException",
                                     "failureFrames": ["dev.fajar.proof.Probe.run:12"], "message": "private-fixture"}}
        output = native_diagnostic(value)
        self.assertEqual({"replyComplete": True, "resultComplete": False, "instrumentationComplete": False,
                          "failureCategory": "IllegalStateException", "failureFrames": ["dev.fajar.proof.Probe.run:12"]}, output)
        self.assertNotIn("private-fixture", str(output))
        for native in ({"failureCategory": "bad category"}, {"failureFrames": ["raw message"]},
                       {"failureFrames": ["dev.fajar.proof.Probe.run:12"] * 13}):
            with self.assertRaises(ValueError):
                native_diagnostic({"instrumentation": native})

    def test_native_progress_keeps_only_static_stages_and_boolean_readiness(self):
        value = {"progress": {"stage": "waiting_for_offline_room_state", "encrypted": True,
                              "expectedVersion": False, "body": "private-fixture"}}
        self.assertEqual({"stage": "waiting_for_offline_room_state", "encrypted": True,
                          "expectedVersion": False}, native_diagnostic(value)["progress"])
        for progress in ({"stage": "private-fixture"}, {"stage": "creating_offline_room", "encrypted": 1}):
            with self.assertRaises(ValueError):
                native_diagnostic({"progress": progress})

    def fixture(self, root):
        apk = root / "proof.apk"
        apk.write_bytes(b"fixture")
        directory = root / "run"
        directory.mkdir()
        return NativeDevice(apk, "emulator-5560", root / "sdk", directory)

    def test_phone_rejected_before_filesystem_or_adb_access(self):
        with self.assertRaises(ValueError):
            NativeDevice("missing.apk", "10.77.77.3:5555", "sdk", "work")

    def test_existing_package_is_preserved_and_never_uninstalled(self):
        with tempfile.TemporaryDirectory() as directory:
            device = self.fixture(Path(directory))
            with patch.object(device, "command", side_effect=["hris-mobile-dev\nOK", "1", f"package:{PACKAGE}"]) as command:
                with self.assertRaises(RuntimeError):
                    device.prepare()
                self.assertTrue(device.cleanup())
                self.assertEqual(3, command.call_count)
                self.assertFalse(device.installed)

    def test_wrong_avd_is_not_mutated(self):
        with tempfile.TemporaryDirectory() as directory:
            device = self.fixture(Path(directory))
            with patch.object(device, "command", return_value="someone-elses-avd\nOK") as command:
                with self.assertRaises(RuntimeError):
                    device.prepare()
                self.assertTrue(device.cleanup())
                command.assert_called_once_with("emu", "avd", "name")

    def test_unrelated_apk_is_never_installed(self):
        with tempfile.TemporaryDirectory() as directory:
            device = self.fixture(Path(directory))
            with patch.object(device, "command", side_effect=["hris-mobile-dev\nOK", "1", ""]) as command, patch("collaboration_native_device.subprocess.run", return_value=subprocess.CompletedProcess([], 0, "package: name='dev.fajar.hris.mobile'\napplication-debuggable", "")):
                with self.assertRaises(ValueError):
                    device.prepare()
                self.assertTrue(device.cleanup())
                self.assertEqual(3, command.call_count)

    def test_cleanup_removes_owned_reverse_even_if_app_removal_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            device = self.fixture(Path(directory))
            device.installed = True
            device.port = 45678
            with patch.object(device, "command", side_effect=["", RuntimeError("failed"), ""]) as command:
                self.assertFalse(device.cleanup())
                self.assertEqual(("reverse", "--remove", "tcp:45678"), command.call_args.args)

    def test_existing_reverse_is_not_overwritten_or_removed(self):
        with tempfile.TemporaryDirectory() as directory:
            device = self.fixture(Path(directory))
            with patch.object(device, "command", return_value="emulator-5560 tcp:45678 tcp:9988") as command:
                with self.assertRaises(RuntimeError):
                    device.bind({"matrix": {"baseUrl": "http://127.0.0.1:45678"}})
                self.assertTrue(device.cleanup())
                self.assertIsNone(device.port)
                command.assert_called_once_with("reverse", "--list")


if __name__ == "__main__":
    unittest.main()
