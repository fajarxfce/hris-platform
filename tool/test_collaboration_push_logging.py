"""Exercise the exact deployable formatter/configuration without provider credentials."""
import importlib.util
import json
import logging
from pathlib import Path
import subprocess
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "deploy/synapse/hris_push_logging.py"
SPEC = importlib.util.spec_from_file_location("hris_push_logging", MODULE)
redaction = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(redaction)


class PushLoggingTest(unittest.TestCase):
    def setUp(self):
        self.key = "hriscp1_" + "a" * 64
        self.formatter = redaction.BoundedRedactingFormatter("%(name)s %(message)s")

    def record(self, message, args=(), exc_info=None):
        return logging.LogRecord("synapse.push.httppusher", logging.WARNING,
                                 "fixture.py", 10, message, args, exc_info)

    def test_substituted_keys_are_redacted_without_mutating_the_original_record(self):
        record = self.record("Failed pusher %s and %s", (self.key, "hriscp1_" + "b" * 64))
        output = self.formatter.format(record)
        self.assertNotIn("hriscp1_", output)
        self.assertNotIn("hrisgp1_", output)
        self.assertEqual(2, output.count("<hris-push-key>"))
        self.assertEqual(self.key, record.args[0])
        self.assertIsNone(record.exc_text)

    def test_chained_exception_stack_and_formatter_fields_are_also_redacted(self):
        try:
            try:
                raise ValueError(self.key)
            except ValueError as cause:
                raise RuntimeError("push=" + self.key) from cause
        except RuntimeError:
            record = self.record("failed %s", (self.key,), sys.exc_info())
        record.name += "." + self.key
        record.stack_info = "Fixture stack " + self.key
        output = self.formatter.format(record)
        self.assertNotIn("hriscp1_", output)
        self.assertNotIn("hrisgp1_", output)
        self.assertIn("ValueError", output)
        self.assertIn("RuntimeError", output)
        self.assertIn("Fixture stack <hris-push-key>", output)
        self.assertIsNone(record.exc_text)

    def test_existing_cached_exception_text_and_partial_capabilities_are_redacted(self):
        record = self.record("push hriscp1_abc and hriscp1_")
        record.exc_text = "Cached error " + self.key
        self.assertNotIn("hriscp1_", self.formatter.format(record))
        self.assertNotIn("hrisgp1_", self.formatter.format(record))
        self.assertIn(self.key, record.exc_text)

    def test_bad_formatting_never_invokes_logging_raw_argument_fallback(self):
        record = self.record("two placeholders %s %s", (self.key,))
        self.assertEqual("Log record could not be formatted.", self.formatter.format(record))
        class BrokenValue:
            def __str__(self):
                raise RuntimeError("unsafe formatting")
        self.assertEqual("Log record could not be formatted.",
                         self.formatter.format(self.record(BrokenValue())))

    def test_termination_is_not_swallowed(self):
        class StoppingValue:
            def __str__(self):
                raise KeyboardInterrupt()
        with self.assertRaises(KeyboardInterrupt):
            self.formatter.format(self.record(StoppingValue()))

    def test_long_unicode_output_is_byte_bounded_after_redaction(self):
        output = self.formatter.format(self.record("é" * 12000 + self.key))
        self.assertLessEqual(len(output.encode("utf-8")), 16384)
        self.assertTrue(output.endswith(" [truncated]"))
        self.assertNotIn("hriscp1_", output)
        self.assertNotIn("hrisgp1_", output)
        output = self.formatter.format(self.record("a" * 16360 + self.key))
        self.assertNotIn("hriscp1_", output)
        self.assertNotIn("hrisgp1_", output)

    def test_exact_deployment_configuration_loads_in_a_fresh_python_process(self):
        program = """
import json, logging, logging.config, sys
from pathlib import Path
directory = Path(sys.argv[1])
sys.path.insert(0, str(directory))
logging.config.dictConfig(json.loads((directory / 'logging.json').read_text()))
logging.getLogger('synapse.push.httppusher').info('Pusher %s', 'hriscp1_' + 'a' * 64)
try:
    raise RuntimeError('hriscp1_' + 'b' * 64)
except RuntimeError:
    logging.getLogger('twisted').exception('Delivery failed')
"""
        program = program.replace("hriscp1_", self.key[:8])
        result = subprocess.run([sys.executable, "-c", program, str(MODULE.parent)],
                                capture_output=True, text=True, timeout=5, check=True)
        self.assertNotIn("hriscp1_", result.stdout + result.stderr)
        self.assertNotIn("hrisgp1_", result.stdout + result.stderr)
        self.assertIn("Pusher <hris-push-key>", result.stdout)
        self.assertIn("RuntimeError: <hris-push-key>", result.stdout)
        self.assertEqual("", result.stderr)
        config = json.loads((MODULE.parent / "logging.json").read_text())
        self.assertEqual({"safe"}, {handler["formatter"] for handler in config["handlers"].values()})


class GroupPushLoggingTest(PushLoggingTest):
    def setUp(self):
        super().setUp()
        self.key = "hrisgp1_" + "a" * 64


if __name__ == "__main__":
    unittest.main()
