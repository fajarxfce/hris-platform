import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from urllib.parse import quote

from database_recovery import RecoveryError
import recovery_storage as storage


def listing(items, more=False):
    return ('<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">'
            '<EncodingType>url</EncodingType><IsTruncated>' + str(more).lower()
            + '</IsTruncated><KeyCount>' + str(len(items)) + '</KeyCount>'
            + ''.join('<Contents><Key>' + quote(key, safe='') + '</Key><Size>' + str(size) + '</Size></Contents>' for key, size in items)
            + '</ListBucketResult>').encode()


class StorageRecoveryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)

    def test_listing_accepts_empty_objects_unicode_and_escaped_punctuation(self):
        expected = [('empty/', 0), ('space ?#%&雪', 37)]
        self.assertEqual((expected, False), storage.list_page(listing(expected), ''))
        self.assertEqual(([], False), storage.list_page(listing([]), 'previous'))

    def test_listing_rejects_nonprogress_oversize_duplicate_and_malformed_metadata(self):
        cases = [
            (listing([('before', 1)]), 'previous'), (listing([('same', 1), ('same', 1)]), ''),
            (listing([], True), ''), (listing([('large', storage.MAX_OBJECT + 1)]), ''),
            (listing([('negative', -1)]), ''), (listing([(str(index), 1) for index in range(101)]), ''),
            (listing([('path/../other', 1)]), ''), (listing([('path/./other', 1)]), ''),
            (b'<!DOCTYPE unsafe><root/>', ''), (listing([]).replace(b'<KeyCount>0', b'<KeyCount>1'), ''),
        ]
        for body, previous in cases:
            with self.subTest(body=body[:80]), self.assertRaises(RecoveryError):
                storage.list_page(body, previous)

    def fake_requests(self, responses):
        remaining = iter(responses)

        def get(*args, **kwargs):
            response = self.directory / 'response.partial'
            response.write_bytes(next(remaining))
            return response
        return get

    def test_inventory_compares_exact_bytes_and_continues_from_the_last_key(self):
        baseline = self.directory / 'baseline'
        responses = [listing([('first', 0)], True), b'', listing([('next/雪', 3)]), b'abc']
        with patch.object(storage, 's3_get', side_effect=self.fake_requests(responses)) as requests:
            result = storage.scan_storage('http://owned', {'bucket': 'fixture'}, self.directory, io.BytesIO(), baseline)
        self.assertEqual(2, result['objects'])
        self.assertEqual(3, result['bytes'])
        self.assertIn('start-after=first', requests.call_args_list[2].args[1])
        with patch.object(storage, 's3_get', side_effect=self.fake_requests(responses)):
            actual = storage.scan_storage('http://owned', {'bucket': 'fixture'}, self.directory, io.BytesIO(), self.directory / 'verified', expected=baseline)
        self.assertEqual(result, actual)
        for changed in ([listing([('first', 0)]), b''], [listing([('first', 0), ('next/雪', 3)]), b'', b'xyz']):
            destination = self.directory / 'failed'
            with patch.object(storage, 's3_get', side_effect=self.fake_requests(changed)):
                with self.assertRaises(RecoveryError):
                    storage.scan_storage('http://owned', {'bucket': 'fixture'}, self.directory, io.BytesIO(), destination, expected=baseline)
            self.assertFalse(destination.exists())
            self.assertFalse((self.directory / 'response.partial').exists())

    def test_credentials_are_private_not_command_arguments_and_request_files_are_released(self):
        secret = {'accessKey': 'owned-access', 'secretKey': 'owned-secret'}

        def capture(arguments, destination, log, **kwargs):
            self.assertFalse(any('owned-secret' in argument or 'owned-access' in argument for argument in arguments))
            config = self.directory / 'curl.config'
            self.assertEqual(0o600, config.stat().st_mode & 0o777)
            self.assertIn('owned-access:owned-secret', config.read_text())
            destination.write_bytes(b'')
        with patch.object(storage, 'capture_file', side_effect=capture):
            response = storage.s3_get('http://owned', '/key', secret, self.directory, io.BytesIO(), limit=1, timeout=1)
        self.assertEqual(b'', response.read_bytes())
        self.assertFalse((self.directory / 'curl.config').exists())
        response.unlink()
        with patch.object(storage, 'capture_file', side_effect=KeyboardInterrupt):
            with self.assertRaises(KeyboardInterrupt):
                storage.s3_get('http://owned', '/key', secret, self.directory, io.BytesIO(), limit=1, timeout=1)
        self.assertFalse((self.directory / 'curl.config').exists())
        self.assertFalse(response.exists())

    def test_internal_network_endpoint_rejects_published_ports_or_a_different_network(self):
        info = {'NetworkSettings': {'Networks': {'owned': {'IPAddress': '172.20.0.2'}}, 'Ports': {}}}
        with patch.object(storage, 'captured', return_value=json.dumps([info])):
            self.assertEqual('http://172.20.0.2:3900', storage.container_endpoint('container', 'owned', 3900, io.BytesIO()))
        for change in ({'Ports': {'3900/tcp': [{'HostIp': '0.0.0.0'}]}}, {'Networks': {'other': {'IPAddress': '172.20.0.2'}}}):
            altered = {'NetworkSettings': info['NetworkSettings'] | change}
            with patch.object(storage, 'captured', return_value=json.dumps([altered])):
                with self.assertRaises(RecoveryError):
                    storage.container_endpoint('container', 'owned', 3900, io.BytesIO())

    def test_lost_creation_and_interruption_clean_names_retained_before_docker_run(self):
        def restore(_archive, destination, **_kwargs):
            destination.mkdir()
        for failure in (RecoveryError('lost creation response'), KeyboardInterrupt()):
            with patch.object(storage, 'restore_archive', side_effect=restore), \
                    patch.object(storage, 'command', side_effect=[subprocess.CompletedProcess([], 0), failure]), \
                    patch.object(storage, 'cleanup_owned') as cleanup:
                with self.assertRaises(type(failure)):
                    with storage.restored_garage(self.directory, {'rpcSecret': 'private'}, io.BytesIO()):
                        self.fail('A failed container must never be yielded')
                resources, run_id, _ = cleanup.call_args.args
                self.assertEqual(['network', 'container'], [kind for kind, _ in resources])
                self.assertTrue(all(run_id in name for _, name in resources))
            self.assertEqual([], list(self.directory.iterdir()))

    def test_failed_container_cleanup_preserves_owned_bind_data(self):
        def restore(_archive, destination, **_kwargs):
            destination.mkdir()
            (destination / 'retained').write_bytes(b'fixture')
        with patch.object(storage, 'restore_archive', side_effect=restore), \
                patch.object(storage, 'command', side_effect=RecoveryError('Docker unavailable')), \
                patch.object(storage, 'cleanup_owned', side_effect=RecoveryError('Cleanup unconfirmed')):
            with self.assertRaisesRegex(RecoveryError, 'Cleanup unconfirmed'):
                with storage.restored_garage(self.directory, {'rpcSecret': 'private'}, io.BytesIO()):
                    self.fail('Unexpected readiness')
        self.assertEqual(1, len(list(self.directory.glob('storage-verify-*/meta/retained'))))
