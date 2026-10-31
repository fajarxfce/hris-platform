import unittest
from unittest.mock import patch

import collaboration_protocol_metrics as metrics


class ProtocolMetricsTest(unittest.TestCase):
    def line(self, servlet="SyncRestServlet", count="3.0", extra=""):
        return (metrics.METRIC + '_total{method="GET",server_name="proof.invalid",'
                + 'servlet="' + servlet + '"' + extra + '} ' + count + '\n').encode()

    def test_emits_only_fixed_method_servlet_and_integer_count(self):
        raw = self.line() + b'other_metric{account="fictional-private"} 4\n'
        self.assertEqual([{"method": "GET", "servlet": "SyncRestServlet", "count": 3}],
                         metrics.protocol_counts(raw))
        self.assertNotIn("fictional-private", str(metrics.protocol_counts(raw)))

    def test_rejects_identifiers_extra_duplicate_labels_and_unbounded_counters(self):
        for raw in [self.line("@private:proof.invalid"), self.line(extra=',account="private"'),
                    self.line(extra=',method="POST"'), self.line() * 2,
                    self.line(count="1.5"), self.line(count="1000001"), self.line(count="-1"),
                    b'x' * (metrics.MAX_METRICS_BYTES + 1), b'no recognized metric\n']:
            with self.subTest(length=len(raw)):
                with self.assertRaises(ValueError):
                    metrics.protocol_counts(raw)

    def test_collection_closes_response_and_bounds_read_even_if_parser_rejects(self):
        class Response:
            status = 200
            closed = False
            size = None
            def __enter__(self):
                return self
            def __exit__(self, *args):
                self.closed = True
            def read(self, size):
                self.size = size
                return b'no recognized metric\n'
        response = Response()
        with patch.object(metrics.urllib.request, "urlopen", return_value=response) as open_url:
            with self.assertRaises(ValueError):
                metrics.collect_protocol_counts("http://127.0.0.1:1234")
            open_url.assert_called_once_with("http://127.0.0.1:1234/_synapse/metrics", timeout=8)
        self.assertTrue(response.closed)
        self.assertEqual(metrics.MAX_METRICS_BYTES + 1, response.size)


if __name__ == "__main__":
    unittest.main()
