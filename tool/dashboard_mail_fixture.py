"""Bounded loopback SMTP sink for disposable dashboard/worker acceptance only."""

from email import policy
from email.parser import BytesParser
import json
from pathlib import Path
import socketserver
import threading
import time


class MailHandler(socketserver.StreamRequestHandler):
    def handle(self):
        deadline = time.monotonic() + 5
        recipient = None
        body = None

        def reply(value):
            self.wfile.write(value.encode("ascii") + b"\r\n")
            self.wfile.flush()

        reply("220 fixture ESMTP")
        try:
            for _ in range(2048):
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    return
                self.connection.settimeout(min(remaining, 3))
                line = self.rfile.readline(4097)
                if not line or len(line) > 4096 or not line.endswith(b"\n"):
                    return
                command = line.rstrip(b"\r\n")
                verb = command.upper()
                if body is not None:
                    if command == b".":
                        if recipient is None or len(self.server.messages) >= 32:
                            reply("452 fixture capacity")
                        else:
                            parsed = BytesParser(policy=policy.default).parsebytes(bytes(body))
                            if parsed.get_content_type() != "text/plain":
                                reply("550 plain text only")
                                return
                            text = parsed.get_content()
                            if not isinstance(text, str) or len(text.encode("utf-8")) > 65536:
                                return
                            self.server.messages.append({"recipient": recipient, "subject": str(parsed["Subject"]), "text": text})
                            temporary = self.server.output.with_suffix(".tmp")
                            temporary.write_text(json.dumps(self.server.messages), encoding="utf-8")
                            temporary.chmod(0o600)
                            temporary.replace(self.server.output)
                            reply("250 message retained")
                        body = None
                        recipient = None
                    else:
                        if len(body) + len(line) > 131072:
                            return
                        body.extend(line[1:] if line.startswith(b"..") else line)
                elif verb.startswith((b"EHLO ", b"HELO ")):
                    reply("250 fixture")
                elif verb.startswith(b"MAIL FROM:"):
                    recipient = None
                    reply("250 sender accepted")
                elif verb.startswith(b"RCPT TO:"):
                    address = command[8:].strip().strip(b"<>").decode("ascii")
                    if recipient is not None or not address.endswith(("@example.test", "@example.invalid")):
                        reply("550 fixture recipient required")
                    else:
                        recipient = address
                        reply("250 recipient accepted")
                elif verb == b"DATA" and recipient is not None:
                    body = bytearray()
                    reply("354 continue")
                elif verb == b"RSET":
                    recipient = None
                    reply("250 reset")
                elif verb == b"QUIT":
                    reply("221 bye")
                    return
                else:
                    reply("500 unsupported")
        except (OSError, UnicodeError, ValueError):
            # Test-owned content and tokens must never become diagnostics.
            return


class DashboardMailFixture:
    def __init__(self, output: Path):
        self.output = output
        self.server = socketserver.TCPServer(("127.0.0.1", 0), MailHandler)
        self.server.messages = []
        self.server.output = output
        try:
            output.write_text("[]", encoding="utf-8")
            output.chmod(0o600)
            self.port = self.server.server_address[1]
            self.thread = threading.Thread(target=self.server.serve_forever, kwargs={"poll_interval": 0.05}, name="owned-dashboard-smtp")
            self.thread.start()
        except Exception:
            self.server.server_close()
            output.unlink(missing_ok=True)
            raise

    def close(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=6)
        self.output.unlink(missing_ok=True)
        self.output.with_suffix(".tmp").unlink(missing_ok=True)
        if self.thread.is_alive():
            raise RuntimeError("Fixture SMTP failed to stop")
