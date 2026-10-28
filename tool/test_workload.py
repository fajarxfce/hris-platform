import io
import json
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest
from unittest.mock import Mock, patch

import measure_attendance as measurement
import document_workload_io as document_io
from document_workload_fixture import DocumentWorkloadFixture
from measure_payroll import payroll_outcome_passes
from workload_fixture import WorkloadFixture


class MeasurementTest(unittest.TestCase):
    def test_nearest_rank_retains_slow_tail_and_does_not_invent_empty_samples(self):
        result = measurement.distribution([10] * 94 + [1200] * 6)
        self.assertEqual(1200, result["p95"])
        self.assertEqual(100, result["count"])
        self.assertEqual({"count": 0}, measurement.distribution([]))
        self.assertEqual(4, measurement.distribution([4])["p99"])

    def test_arrivals_keep_a_finite_inflight_set_and_measure_delayed_start(self):
        entered = threading.Event()
        release = threading.Event()
        lock = threading.Lock()
        active = maximum = started = 0
        output = []

        def work(participant, scheduled, stop):
            nonlocal active, maximum, started
            with lock:
                active += 1
                started += 1
                maximum = max(maximum, active)
                if active == 2:
                    entered.set()
            try:
                self.assertTrue(release.wait(3))
                return participant, scheduled
            finally:
                with lock:
                    active -= 1

        runner = threading.Thread(target=lambda: output.append(measurement.run_arrivals(list(range(20)), .01, work, concurrency=2)))
        runner.start()
        try:
            self.assertTrue(entered.wait(3))
            with lock:
                self.assertEqual(2, started)
            release.set()
            runner.join(3)
            self.assertFalse(runner.is_alive())
            values, elapsed = output[0]
            self.assertEqual(list(range(20)), sorted(v[0] for v in values))
            self.assertEqual(2, maximum)
            self.assertEqual(0, active)
            self.assertGreater(elapsed, 0)
            # Planned times retain the offered schedule even after backpressure.
            times = sorted(v[1] for v in values)
            self.assertAlmostEqual(.0095, times[-1] - times[0], places=5)
        finally:
            release.set()
            runner.join(3)

    def test_worker_failure_stops_and_joins_sibling_work(self):
        sibling = threading.Event()
        ended = threading.Event()

        def work(participant, scheduled, stop):
            if participant == 0:
                self.assertTrue(sibling.wait(3))
                raise RuntimeError("injected acquisition failure")
            sibling.set()
            try:
                self.assertTrue(stop.wait(3))
            finally:
                ended.set()

        with self.assertRaisesRegex(RuntimeError, "injected"):
            measurement.run_arrivals([0, 1], .01, work, concurrency=2)
        self.assertTrue(ended.is_set())

    def test_http_failure_keeps_timing_without_retry_or_raw_response(self):
        samples = []
        response = Mock(status=403)
        response.read1.side_effect = [json.dumps({"code": "access_denied", "private": "never diagnostic"}).encode(), b""]
        with patch.object(measurement.http.client, "HTTPConnection") as constructor:
            connection = constructor.return_value
            connection.getresponse.return_value = response
            api = measurement.Api("http://127.0.0.1:1")
            with self.assertRaisesRegex(measurement.WorkloadError, "HTTP 403: access_denied") as result:
                api.call("/owned", samples=samples, label="read")
            self.assertNotIn("never diagnostic", str(result.exception))
            self.assertEqual(1, connection.request.call_count)
            response.close.assert_called_once()
            connection.close.assert_called_once()
        self.assertEqual(403, samples[0]["status"])
        self.assertGreaterEqual(samples[0]["milliseconds"], 0)

    def test_oversized_body_closes_response_and_cancelled_work_sends_nothing(self):
        response = Mock(status=200)
        response.read1.return_value = b"x" * (64 * 1024)
        with patch.object(measurement.http.client, "HTTPConnection") as constructor:
            connection = constructor.return_value
            connection.getresponse.return_value = response
            stop = threading.Event()
            api = measurement.Api("http://127.0.0.1:1", stop)
            with self.assertRaisesRegex(measurement.WorkloadError, "bound"):
                api.call("/owned")
            self.assertEqual(33, response.read1.call_count)
            response.close.assert_called_once()
            stop.set()
            with self.assertRaisesRegex(measurement.WorkloadError, "cancelled"):
                api.call("/owned")
            self.assertEqual(1, connection.request.call_count)


class PayrollOutcomeTest(unittest.TestCase):
    def evidence(self):
        from decimal import Decimal
        count = 5000
        values = {"results": count, "employees": count, "succeeded": count, "audit": count, "amountVariants": 1}
        for total, one in (("taxableGross", "oneTaxableGross"), ("withheld", "oneWithheld"), ("takeHome", "oneTakeHome")):
            values[one] = "49999999999.99"
            values[total] = str(count * Decimal(values[one]))
        return values, {"run": {"status": "CALCULATED", "failed": 0}, "job": {"status": "SUCCEEDED"}}

    def test_cent_discrepancy_is_not_hidden_by_large_aggregate_amounts(self):
        from decimal import Decimal
        counts, view = self.evidence()
        self.assertTrue(payroll_outcome_passes(counts, view, 5000))
        counts["takeHome"] = str(Decimal(counts["takeHome"]) + Decimal("0.01"))
        self.assertFalse(payroll_outcome_passes(counts, view, 5000))

    def test_fast_failed_partial_or_unaudited_cohort_cannot_pass(self):
        for field in ("results", "employees", "succeeded", "audit"):
            counts, view = self.evidence()
            counts[field] = 4999
            self.assertFalse(payroll_outcome_passes(counts, view, 5000), field)
        counts, view = self.evidence()
        view["run"]["failed"] = 1
        self.assertFalse(payroll_outcome_passes(counts, view, 5000))
        view["run"]["failed"] = 0
        view["job"]["status"] = "FAILED"
        self.assertFalse(payroll_outcome_passes(counts, view, 5000))

    def test_missing_or_inconsistent_financial_results_cannot_pass(self):
        for changes in ({"taxableGross": None}, {"oneWithheld": None}, {"amountVariants": 2}):
            counts, view = self.evidence()
            self.assertFalse(payroll_outcome_passes(counts | changes, view, 5000))


class FixtureOwnershipTest(unittest.TestCase):
    def fixture(self, root):
        return WorkloadFixture(Path(root), io.BytesIO(), "a" * 32, [0, 1, 2, 3])

    def test_failed_volume_creation_still_tracks_and_removes_its_owned_name(self):
        with tempfile.TemporaryDirectory() as root:
            fixture = self.fixture(root)
            calls = []

            def command(args, **kwargs):
                calls.append(args)
                if args[:3] == ["docker", "volume", "create"]:
                    raise RuntimeError("lost creation response")
                return subprocess.CompletedProcess(args, 0, (fixture.run_id + "\n").encode())

            fixture.command = command
            with self.assertRaisesRegex(RuntimeError, "lost"):
                fixture.start()
            self.assertIsNotNone(fixture.volume)
            with patch("workload_fixture.docker.cleanup_owned") as cleanup:
                fixture.close()
            cleanup.assert_called_once_with(fixture.resources, fixture.run_id, fixture.log)
            self.assertIn(["docker", "volume", "rm", fixture.volume], calls)

    def test_uncertain_container_removal_never_deletes_the_data_volume(self):
        with tempfile.TemporaryDirectory() as root:
            fixture = self.fixture(root)
            fixture.volume = fixture.name + "-data"
            fixture.command = Mock()
            with patch("workload_fixture.docker.cleanup_owned", side_effect=RuntimeError("unconfirmed")):
                with self.assertRaisesRegex(RuntimeError, "unconfirmed"):
                    fixture.close()
            fixture.command.assert_not_called()

    def test_foreign_volume_is_not_removed(self):
        with tempfile.TemporaryDirectory() as root:
            fixture = self.fixture(root)
            fixture.volume = fixture.name + "-data"
            fixture.command = Mock(return_value=subprocess.CompletedProcess([], 0, b"another-run\n"))
            with patch("workload_fixture.docker.cleanup_owned"):
                with self.assertRaisesRegex(RuntimeError, "another run"):
                    fixture.close()
            self.assertEqual(1, fixture.command.call_count)


class DocumentWorkloadTest(unittest.TestCase):
    def test_maximum_chunk_layout_contains_distinct_bytes_and_valid_pdf_offsets(self):
        import hashlib
        import re
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "sample.pdf"
            source = document_io.make_pdf(path, 3 * document_io.MIB, "owned")
            content = path.read_bytes()
            self.assertEqual(3 * document_io.MIB, len(content))
            self.assertEqual(hashlib.sha256(content).hexdigest(), source["sha256"])
            self.assertEqual(3, len(set(source["chunkDigests"])))
            xref = int(re.search(rb"startxref\n(\d+)", content)[1])
            self.assertTrue(content[xref:].startswith(b"xref\n0 5\n"))
            for number, entry in enumerate(content[xref:].splitlines()[3:7], 1):
                self.assertTrue(content[int(entry[:10]):].startswith(f"{number} 0 obj\n".encode()))
            length = int(re.search(rb"/Length (\d+)", content)[1])
            start = content.index(b"stream\n") + len(b"stream\n")
            self.assertTrue(content[start + length:].startswith(b"\nendstream\n"))
            other = document_io.make_pdf(Path(root) / "other.pdf", 3 * document_io.MIB, "other")
            self.assertFalse(set(source["chunkDigests"]) & set(other["chunkDigests"]))

    def test_truncated_or_changed_download_fails_and_closes_its_resources(self):
        import hashlib
        for payload in (b"short", b"different"):
            response = Mock(status=200)
            response.getheader.return_value = "9"
            response.read1.side_effect = [payload, b""]
            with patch.object(document_io.http.client, "HTTPConnection") as constructor:
                connection = constructor.return_value
                connection.getresponse.return_value = response
                with self.assertRaisesRegex(measurement.WorkloadError, "did not match"):
                    document_io.transfer("http://127.0.0.1:1", "/owned", "private", threading.Event(),
                        expected_size=9, expected_digest=hashlib.sha256(b"expected!").hexdigest())
                response.close.assert_called_once()
                connection.close.assert_called_once()
                self.assertEqual(1, connection.request.call_count)

    def test_download_progress_cannot_extend_the_total_deadline(self):
        response = Mock(status=200)
        response.getheader.return_value = "9"
        response.read1.return_value = b"x"
        with patch.object(document_io.http.client, "HTTPConnection") as constructor, \
                patch.object(document_io.time, "monotonic", side_effect=[0, 10, 71]):
            connection = constructor.return_value
            connection.getresponse.return_value = response
            with self.assertRaisesRegex(measurement.WorkloadError, "deadline"):
                document_io.transfer("http://127.0.0.1:1", "/owned", "private", threading.Event(), expected_size=9)
            self.assertEqual(1, response.read1.call_count)
            response.close.assert_called_once()
            connection.close.assert_called_once()

    def test_transfer_failure_joins_cancelled_sibling(self):
        entered, ended = threading.Event(), threading.Event()

        def perform(number, stop):
            if number == 0:
                self.assertTrue(entered.wait(3))
                raise RuntimeError("injected transfer failure")
            entered.set()
            self.assertTrue(stop.wait(3))
            ended.set()

        with self.assertRaisesRegex(RuntimeError, "injected"):
            document_io.parallel_transfers([0, 1], perform)
        self.assertTrue(ended.is_set())

    def test_lost_signature_volume_creation_remains_owned_for_cleanup(self):
        with tempfile.TemporaryDirectory() as root:
            fixture = DocumentWorkloadFixture(Path(root), io.BytesIO(), "b" * 32, [0, 1, 2, 3])
            fixture.command = Mock(side_effect=RuntimeError("lost creation response"))
            with self.assertRaisesRegex(RuntimeError, "lost"):
                fixture.volume_for("signatures")
            self.assertEqual([fixture.name + "-signatures"], fixture.extra_volumes)
            fixture.command = Mock(return_value=subprocess.CompletedProcess([], 0, (fixture.run_id + "\n").encode()))
            with patch("workload_fixture.docker.cleanup_owned"):
                fixture.close()
            self.assertEqual(["docker", "volume", "rm", fixture.extra_volumes[0]], fixture.command.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
