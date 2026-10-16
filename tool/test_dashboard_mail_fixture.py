from email.message import EmailMessage
import json
from pathlib import Path
import smtplib
import tempfile
import unittest

from dashboard_mail_fixture import DashboardMailFixture


class MailFixtureTest(unittest.TestCase):
    def test_captures_only_owned_recipient_and_cleans_up(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "mail.json")
            fixture = DashboardMailFixture(path)
            try:
                message = EmailMessage()
                message["From"] = "no-reply@example.test"
                message["To"] = "owned@example.test"
                message["Subject"] = "Owned fixture"
                message.set_content("A bounded test message.")
                with smtplib.SMTP("127.0.0.1", fixture.port, timeout=2) as smtp:
                    smtp.send_message(message)
                self.assertEqual([{"recipient": "owned@example.test", "subject": "Owned fixture", "text": "A bounded test message.\r\n"}], json.loads(path.read_text()))
                self.assertEqual(0o600, path.stat().st_mode & 0o777)
                message.replace_header("To", "unowned@else.invalid")
                with smtplib.SMTP("127.0.0.1", fixture.port, timeout=2) as smtp:
                    with self.assertRaises(smtplib.SMTPRecipientsRefused):
                        smtp.send_message(message)
                self.assertEqual(1, len(json.loads(path.read_text())))
            finally:
                fixture.close()
            self.assertFalse(fixture.thread.is_alive())
            self.assertFalse(path.exists())

    def test_message_capacity_is_finite(self):
        with tempfile.TemporaryDirectory() as directory:
            fixture = DashboardMailFixture(Path(directory, "mail.json"))
            try:
                fixture.server.messages = [{}] * 32
                with smtplib.SMTP("127.0.0.1", fixture.port, timeout=2) as smtp:
                    with self.assertRaises(smtplib.SMTPDataError):
                        smtp.sendmail("no-reply@example.test", "owned@example.test", "Subject: Capacity\r\n\r\nOwned content")
                self.assertEqual(32, len(fixture.server.messages))
            finally:
                fixture.close()


if __name__ == "__main__":
    unittest.main()
