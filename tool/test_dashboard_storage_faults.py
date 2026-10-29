from http.client import HTTPConnection, RemoteDisconnected
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import socket
import tempfile
import threading
import time
import unittest
from urllib.parse import urlsplit
import uuid

from dashboard_storage_faults import MAX_BYTES, StorageFaultProxy


class StorageFaultTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.objects = {}
        self.entered = threading.Event()
        self.release = threading.Event()
        owner = self

        class Provider(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_PUT(self):
                owner.objects[self.path] = self.rfile.read(int(self.headers["Content-Length"]))
                self.send_response(200)
                self.send_header("ETag", '"stored"')
                self.send_header("Content-Length", "0")
                self.end_headers()

            def do_GET(self):
                if self.path == "/stall":
                    owner.entered.set()
                    owner.release.wait(5)
                body = owner.objects.get(self.path, b"missing")
                try:
                    self.send_response(200)
                    self.send_header("Content-Length", str(len(body)))
                    self.end_headers()
                    self.wfile.write(body)
                except OSError:
                    pass

        self.provider = ThreadingHTTPServer(("127.0.0.1", 0), Provider)
        self.provider_thread = threading.Thread(target=self.provider.serve_forever,
                                               kwargs={"poll_interval": 0.05})
        self.provider_thread.start()
        self.proxy = StorageFaultProxy(f"http://127.0.0.1:{self.provider.server_port}", Path(self.folder.name))

    def tearDown(self):
        self.release.set()
        self.proxy.close()
        self.provider.shutdown()
        self.provider.server_close()
        self.provider_thread.join(2)
        self.folder.cleanup()

    def command(self, mode):
        self.proxy.write(self.proxy.command_file, {"id": str(uuid.uuid4()), "mode": mode})

    def request(self, method, path, body=None, headers=None):
        connection = HTTPConnection("127.0.0.1", urlsplit(self.proxy.endpoint).port, timeout=3)
        try:
            connection.request(method, path, body, headers or {})
            result = connection.getresponse()
            return result.status, result.read()
        finally:
            connection.close()

    def test_real_forwarding_response_loss_blocking_and_explicit_recovery(self):
        self.command("drop-second-put")
        self.assertEqual((200, b""), self.request("PUT", "/first", b"one"))
        with self.assertRaises(RemoteDisconnected):
            self.request("PUT", "/second", b"two")
        self.assertEqual({"/first": b"one", "/second": b"two"}, self.objects)
        self.assertEqual((503, b""), self.request("PUT", "/third", b"three"))
        self.assertNotIn("/third", self.objects)
        self.command("pass")
        self.assertEqual((200, b"two"), self.request("GET", "/second"))
        status = json.loads(self.proxy.status_file.read_text())
        self.assertEqual(1, status["droppedResponses"])
        self.assertEqual(2, status["deliveredPuts"])
        self.assertEqual(3, status["lastDroppedBytes"])
        self.assertEqual(1, status["blockedRequests"])
        self.assertEqual(0o600, self.proxy.command_file.stat().st_mode & 0o777)

    def test_input_and_control_bounds_never_forward_invalid_requests(self):
        self.assertEqual(413, self.request("PUT", "/too-large", headers={"Content-Length": str(MAX_BYTES + 1)})[0])
        self.assertEqual(400, self.request("PUT", "/chunked", headers={"Transfer-Encoding": "chunked"})[0])
        for content in ('{"mode":"PRIVATE"}', 'x' * 1025, '{', '[]'):
            self.proxy.command_file.write_text(content)
            self.assertEqual((503, b""), self.request("GET", "/ignored"))
        self.assertEqual({}, self.objects)

    def test_shutdown_interrupts_an_owned_provider_wait_and_removes_private_files(self):
        failures = []

        def request():
            try:
                self.request("GET", "/stall")
            except (OSError, RemoteDisconnected) as error:
                failures.append(type(error).__name__)

        client = threading.Thread(target=request)
        client.start()
        self.assertTrue(self.entered.wait(2))
        started = time.monotonic()
        self.proxy.close()
        client.join(2)
        self.assertFalse(client.is_alive())
        self.assertFalse(self.proxy.thread.is_alive())
        self.assertEqual(set(), self.proxy.clients)
        self.assertEqual(set(), self.proxy.connections)
        self.assertLess(time.monotonic() - started, 2)
        self.assertTrue(failures)
        self.assertFalse(self.proxy.command_file.exists())
        self.assertFalse(self.proxy.status_file.exists())
        self.proxy.close()

    def test_connection_capacity_is_bounded_and_disposal_closes_waiting_headers(self):
        sockets = []
        try:
            for _ in range(4):
                sockets.append(socket.create_connection(("127.0.0.1", urlsplit(self.proxy.endpoint).port), timeout=2))
            deadline = time.monotonic() + 2
            while len(self.proxy.clients) < 4 and time.monotonic() < deadline:
                time.sleep(0.01)
            self.assertEqual(4, len(self.proxy.clients))
            with self.assertRaises((RemoteDisconnected, ConnectionResetError)):
                self.request("GET", "/over-capacity")
            self.assertEqual(1, self.proxy.status["overCapacity"])
            self.proxy.close()
            self.assertEqual(set(), self.proxy.clients)
        finally:
            for connection in sockets:
                connection.close()


if __name__ == "__main__":
    unittest.main()
