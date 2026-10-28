import io
import json
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest
from unittest.mock import Mock, patch

import measure_attendance as measurement
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


if __name__ == "__main__":
    unittest.main()
