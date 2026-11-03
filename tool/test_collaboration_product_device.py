"""Regression checks for physical-device refusal and cleanup after partial setup failures."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch
import xml.etree.ElementTree as ET

from collaboration_product_device import PRODUCT, SERIAL, ProductFcmDevice, center, input_text, started_by_firebase


class ProductFcmDeviceTest(unittest.TestCase):
    def test_only_matching_new_process_started_by_firebase_is_accepted(self):
        event = f"I/am_proc_start( 1200): [0,2345,10200,{PRODUCT},broadcast,{{{PRODUCT}/com.google.firebase.iid.FirebaseInstanceIdReceiver}}]"
        self.assertTrue(started_by_firebase(event, 2345))
        self.assertFalse(started_by_firebase(event, 2222))
        self.assertFalse(started_by_firebase(event.replace(PRODUCT, "another.app"), 2345))
        self.assertFalse(started_by_firebase(event.replace("com.google.firebase.iid.FirebaseInstanceIdReceiver", "androidx.work.impl.background.systemjob.SystemJobService"), 2345))

    def test_clickable_login_action_is_preferred_over_identical_header(self):
        device = ProductFcmDevice("sdk", "/missing/source", "/missing/run")
        tree = ET.fromstring('<hierarchy><node text="Sign in" enabled="true" clickable="false"/><node text="Sign in" enabled="true" clickable="true"/></hierarchy>')
        with patch.object(device, "tree", return_value=tree):
            self.assertEqual("true", device.find("Sign in").get("clickable"))

    def test_compose_text_child_resolves_to_its_button_instead_of_the_page_title(self):
        device = ProductFcmDevice("sdk", "/missing/source", "/missing/run")
        tree = ET.fromstring('<hierarchy><node text="Sign in" enabled="true" clickable="false"/><node class="android.view.View" text="" enabled="true" clickable="true" bounds="[100,200][400,300]"><node text="Sign in" enabled="true" clickable="false"/></node></hierarchy>')
        with patch.object(device, "tree", return_value=tree):
            node = device.find("Sign in", actionable=True)
            self.assertEqual("[100,200][400,300]", node.get("bounds"))

    def test_disabled_parent_cannot_be_tapped_via_an_enabled_text_child(self):
        device = ProductFcmDevice("sdk", "/missing/source", "/missing/run")
        tree = ET.fromstring('<hierarchy><node enabled="false" clickable="true"><node text="Sign in" enabled="true" clickable="false"/></node></hierarchy>')
        with patch.object(device, "tree", return_value=tree), self.assertRaises(TimeoutError):
            device.find("Sign in", seconds=0, actionable=True)

    def test_physical_device_is_rejected_before_any_io(self):
        with patch("collaboration_product_device.subprocess.run") as run:
            with self.assertRaises(ValueError):
                ProductFcmDevice("missing", "missing", "missing", serial="10.77.77.3:5555")
            run.assert_not_called()

    def test_typed_fixture_text_cannot_become_remote_shell_syntax(self):
        self.assertEqual("Fictional%sreply%s123-abc", input_text("Fictional reply 123-abc"))
        for text in ("$(cat secret)", "`id`", "a;b", "a\nb", "a'", "a\\b", "x" * 257, ""):
            with self.subTest(text=text), self.assertRaises(ValueError):
                input_text(text)

    def test_ui_bounds_require_a_real_nonempty_on_screen_rectangle(self):
        self.assertEqual(("30", "50"), center(ET.Element("node", bounds="[10,20][50,80]")))
        for bounds in ("[5,5][5,6]", "[-1,2][3,4]", "[1,2][0,4]", "[1,2][99999,4]", "invalid"):
            with self.subTest(bounds=bounds), self.assertRaises(ValueError):
                center(ET.Element("node", bounds=bounds))

    def test_existing_emulator_is_not_replaced(self):
        device = ProductFcmDevice("sdk", "/missing/source", "/missing/run")
        with patch("collaboration_product_device.subprocess.run", return_value=subprocess.CompletedProcess([], 0, b"device", b"")) as run:
            with self.assertRaises(RuntimeError):
                device.start()
            run.assert_called_once_with([device.adb, "-s", SERIAL, "get-state"], capture_output=True, timeout=10)
            self.assertIsNone(device.process)

    def test_wrong_product_or_release_apk_cannot_change_emulator_data(self):
        for manifest in ("package: name='unrelated.app'\napplication-debuggable", f"package: name='{PRODUCT}' versionCode='25'"):
            device = ProductFcmDevice("sdk", "/missing/source", "/missing/run")
            device.process = Mock()
            device.process.poll.return_value = None
            with patch("collaboration_product_device.subprocess.run", return_value=subprocess.CompletedProcess([], 0, manifest, "")), patch.object(device, "command") as command:
                with self.assertRaises(ValueError):
                    device.install_product("fixture.apk")
                command.assert_not_called()

    def test_existing_reverse_is_not_removed_by_rejected_setup(self):
        device = ProductFcmDevice("sdk", "/missing/source", "/missing/run")
        with patch.object(device, "command", return_value="host tcp:18084 tcp:12345") as command:
            with self.assertRaises(RuntimeError):
                device.reverse(18084, 12345)
            self.assertEqual([], device.ports)
            command.assert_called_once_with("reverse", "--list")

    def test_partial_reverse_failure_still_stops_its_owned_process(self):
        with tempfile.TemporaryDirectory() as location:
            directory = Path(location)
            device = ProductFcmDevice("sdk", directory / "source", directory)
            device.image.write_bytes(b"owned image")
            device.process = Mock()
            device.process.poll.return_value = None
            device.ports = [18084]
            with patch.object(device, "command", side_effect=RuntimeError()), patch("collaboration_product_device.subprocess.run", return_value=subprocess.CompletedProcess([], 1, b"", b"")):
                self.assertFalse(device.close())
            device.process.terminate.assert_called_once()
            device.process.wait.assert_called_once_with(timeout=15)
            self.assertTrue(device.image.exists())

    def test_cleaned_disposable_image_does_not_remove_its_source(self):
        with tempfile.TemporaryDirectory() as location:
            directory = Path(location)
            source = directory / "source.img"
            source.write_bytes(b"original")
            device = ProductFcmDevice("sdk", source, directory)
            device.image.write_bytes(b"disposable")
            with patch("collaboration_product_device.subprocess.run", return_value=subprocess.CompletedProcess([], 1, b"", b"")):
                self.assertTrue(device.close())
            self.assertEqual(b"original", source.read_bytes())
            self.assertFalse(device.image.exists())


if __name__ == "__main__":
    unittest.main()
