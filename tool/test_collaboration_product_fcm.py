"""Pending peer publication must remain distinct from transport and permission failures."""
import subprocess
import unittest
from unittest.mock import Mock, patch
import xml.etree.ElementTree as ET

from collaboration_native_device import PACKAGE
from collaboration_product_device import SERIAL
from collaboration_product_fcm import activate_chat, read_peer


class ProductFcmActivationTest(unittest.TestCase):
    def tree(self, label):
        return ET.fromstring(f'<hierarchy><node enabled="true" clickable="true" bounds="[10,20][200,80]"><node enabled="true" text="{label}"/></node></hierarchy>')

    def test_refresh_replaced_by_ready_uses_current_button_then_checks_navigation(self):
        device = Mock()
        device.tree.side_effect = [self.tree("Refresh"), self.tree("Open chat")]
        mark = Mock()
        with patch("collaboration_product_fcm.time.sleep"):
            activate_chat(device, mark)
        self.assertEqual(2, device.tap.call_count)
        device.click.assert_not_called()
        device.find.assert_called_once_with("Fictional native SDK peer", seconds=30)
        self.assertEqual(["chat_activation_refresh", "chat_activation_open_chat"], [c.args[0] for c in mark.call_args_list])

    def test_unfinished_setup_is_bounded_and_does_not_claim_navigation(self):
        device = Mock()
        device.tree.return_value = ET.fromstring('<hierarchy/>')
        with patch("collaboration_product_fcm.time.sleep"), self.assertRaises(TimeoutError):
            activate_chat(device, Mock())
        self.assertEqual(25, device.tree.call_count)
        device.tap.assert_not_called()
        device.find.assert_not_called()


class ProductFcmPeerResultTest(unittest.TestCase):
    def read(self, code, output=b"", error=b"", name="product-peer-ready"):
        with patch("collaboration_product_fcm.subprocess.run", return_value=subprocess.CompletedProcess([], code, output, error)) as run:
            result = read_peer(Mock(adb="adb"), name)
            run.assert_called_once_with(["adb", "-s", SERIAL, "shell", "run-as", PACKAGE, "cat", f"files/protocol-proof/{name}.json"], capture_output=True, timeout=10)
            return result

    def test_absent_atomic_marker_is_pending(self):
        self.assertIsNone(self.read(1, error=b"cat: files/protocol-proof/product-peer-ready.json: No such file or directory\n"))

    def test_published_object_is_read_without_a_second_remote_operation(self):
        self.assertEqual({"ready": True}, self.read(0, b'{"ready":true}\r\n'))

    def test_permission_transport_and_wrong_path_errors_are_not_pending(self):
        for error in (b"cat: files/protocol-proof/product-peer-ready.json: Permission denied\n", b"error: device offline\n", b"run-as: package not debuggable\n", b"cat: another.json: No such file or directory\n", b""):
            with self.subTest(error=error), self.assertRaises(RuntimeError):
                self.read(1, error=error)

    def test_malformed_oversized_or_non_object_results_fail(self):
        for output in (b"broken", b"{}" + b" " * 16384, b"null", b"[]", b"true"):
            with self.subTest(output=output[:8]), self.assertRaises(ValueError):
                self.read(0, output)

    def test_path_or_shell_input_is_rejected_before_io(self):
        for name in ("../instrumentation", "$(id)", "x;y", "", "a" * 65, None):
            with self.subTest(name=name), patch("collaboration_product_fcm.subprocess.run") as run:
                with self.assertRaises(ValueError):
                    read_peer(Mock(adb="adb"), name)
                run.assert_not_called()

    def test_remote_timeout_remains_a_failure(self):
        with patch("collaboration_product_fcm.subprocess.run", side_effect=subprocess.TimeoutExpired("adb", 10)):
            with self.assertRaises(subprocess.TimeoutExpired):
                read_peer(Mock(adb="adb"), "instrumentation")


if __name__ == "__main__":
    unittest.main()
