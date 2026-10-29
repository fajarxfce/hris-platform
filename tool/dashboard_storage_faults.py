"""Bounded loopback fault boundary in front of the fixture's real S3 provider."""

import hashlib
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import socket
import threading
import time
from urllib.parse import urlsplit
import uuid


MAX_BYTES = 2 * 1024 * 1024
DEADLINE = 15
HOP_HEADERS = {"connection", "transfer-encoding", "keep-alive", "proxy-connection"}


class StorageFaultProxy:
    """The caller owns the target; no request can select another destination.

    Arming drops the second successful PUT's response *after* the real provider
    consumes it, then refuses further requests until a new pass-through command.
    Commands and safe counters use private files, never a public control route.
    """

    def __init__(self, endpoint: str, directory: Path):
        target = urlsplit(endpoint)
        if target.scheme != "http" or target.hostname != "127.0.0.1" or not target.port or target.path:
            raise ValueError("Fault proxy requires the owned loopback storage endpoint")
        self.target_port = target.port
        self.command_file = directory / "storage-fault-command.json"
        self.status_file = directory / "storage-fault-status.json"
        if self.command_file.exists() or self.status_file.exists():
            raise RuntimeError("Existing storage fault markers require review")
        self.lock = threading.RLock()
        self.slots = threading.BoundedSemaphore(4)
        self.clients = set()
        self.connections = set()
        self.closed = False
        self.command_id = ""
        self.status = {"mode": "pass", "putCalls": 0, "deliveredPuts": 0,
                       "droppedResponses": 0, "blockedRequests": 0, "overCapacity": 0,
                       "lastDroppedBytes": 0, "lastDroppedSha256": None}
        owner = self

        class Server(ThreadingHTTPServer):
            daemon_threads = False
            block_on_close = True
            request_queue_size = 4

            def process_request(self, request, client_address):
                with owner.lock:
                    if owner.closed or not owner.slots.acquire(blocking=False):
                        owner.status["overCapacity"] += 1
                        self.close_request(request)
                        return
                    owner.clients.add(request)
                try:
                    super().process_request(request, client_address)
                except BaseException:
                    with owner.lock:
                        owner.clients.discard(request)
                    owner.slots.release()
                    raise

            def process_request_thread(self, request, client_address):
                try:
                    super().process_request_thread(request, client_address)
                finally:
                    with owner.lock:
                        owner.clients.discard(request)
                    owner.slots.release()

            def handle_error(self, request, client_address):
                # Socket failures are intentional here; never print request headers.
                pass

        class Handler(BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"

            def log_message(self, *args):
                pass

            def setup(self):
                super().setup()
                self.connection.settimeout(DEADLINE)

            def do_GET(self):
                owner.forward(self)

            do_PUT = do_GET
            do_HEAD = do_GET
            do_DELETE = do_GET

        self.server = Server(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever,
                                       kwargs={"poll_interval": 0.1}, name="owned-storage-fault-proxy")
        try:
            self.write(self.command_file, {"id": str(uuid.uuid4()), "mode": "pass"})
            self.write(self.status_file, self.status)
            self.thread.start()
        except BaseException:
            self.server.server_close()
            self.command_file.unlink(missing_ok=True)
            self.status_file.unlink(missing_ok=True)
            raise
        self.endpoint = f"http://127.0.0.1:{self.server.server_port}"

    @staticmethod
    def write(path, value):
        temporary = path.with_suffix(".tmp")
        with temporary.open("w") as output:
            temporary.chmod(0o600)
            json.dump(value, output)
        temporary.replace(path)

    def control(self):
        with self.command_file.open("rb") as source:
            raw = source.read(1025)
        if len(raw) > 1024:
            raise ValueError("Oversized storage fault command")
        value = json.loads(raw)
        if set(value) != {"id", "mode"} or value["mode"] not in ("pass", "drop-second-put"):
            raise ValueError("Invalid storage fault command")
        uuid.UUID(value["id"])
        if value["id"] != self.command_id:
            self.command_id = value["id"]
            self.status["mode"] = value["mode"]
            self.status["putCalls"] = 0

    @staticmethod
    def refuse(handler, status=503):
        handler.close_connection = True
        handler.send_response(status)
        handler.send_header("Content-Length", "0")
        handler.send_header("Connection", "close")
        handler.end_headers()

    def forward(self, handler):
        connection = None
        handler.close_connection = True
        deadline = time.monotonic() + DEADLINE
        try:
            if (not handler.path.startswith("/") or handler.path.startswith("//") or
                    "Transfer-Encoding" in handler.headers):
                self.refuse(handler, 400)
                return
            lengths = handler.headers.get_all("Content-Length", [])
            size = int(lengths[0]) if lengths else 0
            if len(lengths) > 1 or size < 0 or size > MAX_BYTES:
                self.refuse(handler, 413)
                return
            with self.lock:
                self.control()
                if self.closed or self.status["mode"] == "blocked":
                    self.status["blockedRequests"] += 1
                    self.refuse(handler)
                    return
                if handler.command == "PUT":
                    self.status["putCalls"] += 1
                drop = self.status["mode"] == "drop-second-put" and handler.command == "PUT" and self.status["putCalls"] == 2
                selected_command = self.command_id
            body = bytearray()
            while len(body) < size:
                handler.connection.settimeout(max(0.001, deadline - time.monotonic()))
                chunk = handler.rfile.read1(min(65536, size - len(body)))
                if not chunk or time.monotonic() >= deadline:
                    raise TimeoutError("Incomplete fixture request")
                body.extend(chunk)
            connection = HTTPConnection("127.0.0.1", self.target_port,
                                        timeout=max(0.001, deadline - time.monotonic()))
            with self.lock:
                if self.closed:
                    return
                self.connections.add(connection)
            headers = {key: value for key, value in handler.headers.items()
                       if key.lower() not in HOP_HEADERS}
            connection.request(handler.command, handler.path, body, headers)
            response = connection.getresponse()
            content = bytearray()
            while True:
                if time.monotonic() >= deadline:
                    raise TimeoutError("Fixture provider deadline")
                if connection.sock:
                    connection.sock.settimeout(max(0.001, deadline - time.monotonic()))
                chunk = response.read1(min(65536, MAX_BYTES + 1 - len(content)))
                if not chunk:
                    break
                content.extend(chunk)
                if len(content) > MAX_BYTES:
                    raise ValueError("Oversized fixture provider response")
            if handler.command == "PUT" and 200 <= response.status < 300:
                with self.lock:
                    self.status["deliveredPuts"] += 1
                    if drop and selected_command == self.command_id:
                        self.status["mode"] = "blocked"
                        self.status["droppedResponses"] += 1
                        self.status["lastDroppedBytes"] = len(body)
                        self.status["lastDroppedSha256"] = hashlib.sha256(body).hexdigest()
                        return  # Close without an HTTP response after the provider stored bytes.
            handler.send_response(response.status)
            for key, value in response.getheaders():
                if key.lower() not in HOP_HEADERS | {"content-length"}:
                    handler.send_header(key, value)
            handler.send_header("Content-Length", response.getheader("Content-Length", "0")
                                if handler.command == "HEAD" else str(len(content)))
            handler.send_header("Connection", "close")
            handler.end_headers()
            if content:
                handler.connection.settimeout(max(0.001, deadline - time.monotonic()))
                handler.wfile.write(content)
        except (OSError, ValueError, TypeError, KeyError):
            try:
                self.refuse(handler)
            except OSError:
                pass
        finally:
            if connection is not None:
                connection.close()
            with self.lock:
                self.connections.discard(connection)
                if not self.closed:
                    self.write(self.status_file, self.status)

    def close(self):
        with self.lock:
            if self.closed:
                return
            self.closed = True
            for client in self.clients:
                try:
                    client.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
            for connection in self.connections:
                if connection.sock:
                    try:
                        connection.sock.shutdown(socket.SHUT_RDWR)
                    except OSError:
                        pass
                connection.close()
        self.server.shutdown()
        self.thread.join(timeout=2)
        self.server.server_close()
        self.command_file.unlink(missing_ok=True)
        self.status_file.unlink(missing_ok=True)
