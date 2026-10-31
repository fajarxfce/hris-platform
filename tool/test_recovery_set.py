import base64
import io
import json
from pathlib import Path
import shutil
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import database_recovery as database
import recovery_set as recovery


def keys():
    return {
        'format': 1, 'checkpoint': 'a' * 32, 'identityActive': 'current',
        'identityKeys': 'old:' + base64.b64encode(b'1' * 32).decode() + ',current:' + base64.b64encode(b'2' * 32).decode(),
        'syncActive': 'sync', 'syncKeys': 'sync:' + base64.b64encode(b'3' * 32).decode(),
        'rpcSecret': 'a' * 64, 'accessKey': 'GK' + 'b' * 32, 'secretKey': 'c' * 64, 'bucket': 'owned-recovery',
    }


class RecoverySetTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)

    def test_envelope_projection_keeps_older_schemas_and_binds_matrix_credentials(self):
        for schema in (59, 103, 105):
            sql = recovery.envelope_query(schema)
            self.assertNotIn("collaboration_provider_sessions", sql)
            self.assertIn("native_push_registrations", sql)
        sql = recovery.envelope_query(106)
        self.assertIn("'hris:matrix:'||company_id||':'||account_id||':'||device_id||':'||generation", sql)
        self.assertIn("FROM collaboration_provider_sessions WHERE token_encrypted IS NOT NULL", sql)
        self.assertIn("LIMIT 100001", sql)
        self.assertNotIn("token_expires_at", sql)
        expiring = recovery.envelope_query(107)
        self.assertIn("CASE WHEN token_expires_at IS NULL THEN ''", expiring)
        self.assertIn("extract(epoch FROM token_expires_at)*1000000", expiring)
        self.assertIn(":expires:", expiring)
        with self.assertRaises(recovery.RecoveryError):
            recovery.envelope_query(110)

    def test_gateway_replay_projection_is_schema_gated_and_authenticates_its_metadata(self):
        for schema in (59, 103, 106, 107, 108):
            self.assertNotIn("collaboration_gateway_sessions", recovery.envelope_query(schema))
        sql = recovery.envelope_query(109)
        self.assertIn("hris:matrix-gateway:v1:", sql)
        self.assertIn("encode(sha256(convert_to(", sql)
        self.assertIn("native_session_id::text", sql)
        self.assertIn("access_hash||':'||refresh_hash", sql)
        self.assertIn("extract(epoch FROM replay_until)", sql)
        self.assertIn("bootstrap_operation_id::text ELSE previous_refresh_hash", sql)
        self.assertIn("FROM collaboration_gateway_sessions WHERE replay_encrypted IS NOT NULL", sql)

    def test_schema_gate_accepts_reviewed_device_registry_and_rejects_unknown_future_schema(self):
        for schema in (59, 101, 102, 103, 104, 105, 106, 107, 108, 109):
            recovery.validate_schema({'observedSchema': str(schema)})
        for schema in (58, 110, None, 'invalid'):
            with self.assertRaises(database.RecoveryError):
                recovery.validate_schema({'observedSchema': schema})

    def test_keypack_requires_exact_fields_current_key_and_unique_bounded_old_keys(self):
        self.assertEqual(keys(), recovery.validate_keys(keys()))
        values = [keys() | {'format': True}, keys() | {'unknown': 'field'}, keys() | {'identityActive': 'missing'},
                  keys() | {'identityKeys': 'current:AA=='}, keys() | {'identityKeys': keys()['identityKeys'] + ',old:AA=='},
                  keys() | {'rpcSecret': 'invalid'}, keys() | {'bucket': '../../outside'}]
        for value in values:
            with self.subTest(fields=list(value)), self.assertRaises(database.RecoveryError):
                recovery.validate_keys(value)
        self.assertEqual(keys() | {'syncKeys': ''}, recovery.validate_keys(keys() | {'syncKeys': ''}))

    def test_source_environment_requires_shared_identity_and_storage_configuration(self):
        value = keys()
        environment = {
            'HRIS_STORAGE_ENABLED': 'true', 'HRIS_STORAGE_ENDPOINT': 'http://garage:3900', 'HRIS_STORAGE_REGION': 'garage',
            'HRIS_STORAGE_BUCKET': value['bucket'], 'HRIS_STORAGE_ACCESS_KEY': value['accessKey'], 'HRIS_STORAGE_SECRET_KEY': value['secretKey'],
            'HRIS_IDENTITY_ACTIVE_KEY': value['identityActive'], 'HRIS_IDENTITY_KEYS': value['identityKeys'],
            'HRIS_SYNC_ACTIVE_KEY': value['syncActive'], 'HRIS_SYNC_KEYS': value['syncKeys'],
        }
        def service(env):
            return {'Config': {'Env': [name + '=' + value for name, value in env.items()]}}
        source = {'api': service(environment), 'worker': service(environment), 'garage': service({'GARAGE_RPC_SECRET': value['rpcSecret']})}
        self.assertEqual(value, recovery.source_keys(source, value['checkpoint']))
        for field, replacement in [('HRIS_STORAGE_SECRET_KEY', 'd' * 64), ('HRIS_IDENTITY_KEYS', 'current:' + base64.b64encode(b'8' * 32).decode())]:
            changed = source | {'worker': service(environment | {field: replacement})}
            with self.assertRaises(database.RecoveryError) as failed:
                recovery.source_keys(changed, value['checkpoint'])
            self.assertNotIn(replacement, str(failed.exception))

    def test_existing_nested_or_git_output_is_rejected_without_overwrite(self):
        existing = self.directory / 'existing'
        existing.mkdir()
        (existing / 'keep').write_bytes(b'original')
        with self.assertRaises(database.RecoveryError):
            recovery.separate_directories(existing, self.directory / 'keys', create=True)
        self.assertEqual(b'original', (existing / 'keep').read_bytes())
        for first, second in [(self.directory / 'data', self.directory / 'data/keys'), (self.directory / 'data/keys', self.directory / 'data')]:
            with self.assertRaises(database.RecoveryError):
                recovery.separate_directories(first, second, create=True)
        repository = self.directory / 'repo'
        repository.mkdir()
        (repository / '.git').write_text('gitdir: elsewhere')
        with self.assertRaises(database.RecoveryError):
            recovery.separate_directories(self.directory / 'data', repository / 'keys', create=True)
        self.assertFalse((self.directory / 'data').exists())

    def test_signature_detects_restarted_or_replaced_source_services(self):
        source = {'api': {'Id': 'original', 'Image': 'same', 'State': {'StartedAt': 'a', 'FinishedAt': 'b'}}}
        for changed in ({'Id': 'replacement'}, {'State': {'StartedAt': 'later', 'FinishedAt': 'later'}}):
            self.assertNotEqual(recovery.source_signature(source), recovery.source_signature({'api': source['api'] | changed}))

    def test_running_paused_or_killed_application_roles_refuse_a_checkpoint(self):
        def responses(changed_service=None, changes=None):
            values = []
            for service in recovery.SERVICES:
                running = service == 'database'
                state = {'Status': 'running' if running else 'exited', 'Running': running,
                         'Paused': False, 'Restarting': False, 'ExitCode': 0 if running else 143,
                         'OOMKilled': False}
                if service == changed_service:
                    state.update(changes)
                info = {'Config': {'Labels': {'com.docker.compose.project': 'owned', 'com.docker.compose.service': service},
                                   'Image': recovery.GARAGE_IMAGE if service == 'garage' else 'owned-image'}, 'State': state}
                values.extend(['a' * 64, json.dumps([info])])
            return values
        with patch.object(database, 'captured', side_effect=responses()):
            self.assertEqual(set(recovery.SERVICES), set(recovery.source_services('owned', io.BytesIO())))
        for service in ('api', 'worker', 'garage'):
            for changes in ({'Running': True, 'Status': 'running'}, {'Paused': True}, {'ExitCode': 137}, {'OOMKilled': True}):
                with self.subTest(service=service, changes=changes), patch.object(database, 'captured', side_effect=responses(service, changes)):
                    with self.assertRaises(database.RecoveryError):
                        recovery.source_services('owned', io.BytesIO())

    def test_backup_interruption_removes_partial_keypack_and_never_acknowledges_manifest(self):
        data, secret = self.directory / 'data', self.directory / 'keys'
        with patch.object(recovery, 'source_services', return_value={'database': {'Id': 'owned'}}), \
                patch.object(recovery, 'source_signature', return_value={}), \
                patch.object(recovery, 'source_keys', return_value=keys()), \
                patch.object(database, 'backup_database_container', side_effect=KeyboardInterrupt):
            with self.assertRaises(KeyboardInterrupt):
                recovery.backup_set(data, secret, 'owned')
        self.assertFalse((data / 'manifest.json').exists())
        self.assertFalse((secret / 'keys.json').exists())
        self.assertTrue((data / 'backup.log').exists())

    def prepare_set(self):
        data, secret = self.directory / 'data', self.directory / 'keys'
        data.mkdir(mode=0o700)
        secret.mkdir(mode=0o700)
        (data / 'database').mkdir(mode=0o700)
        for name in ('metadata.tar', 'content.tar'):
            with database.create_file(data / name) as output, tarfile.open(fileobj=output, mode='w') as archive:
                item = tarfile.TarInfo('.')
                item.type = tarfile.DIRTYPE
                archive.addfile(item)
        with database.create_file(data / 'garage.toml') as output:
            output.write((recovery.ROOT / 'deploy/garage/garage.toml').read_bytes())
        with database.create_file(data / 'inventory.jsonl') as output:
            output.write(b'{"format":1}\n')
        database.write_json(data / 'database/manifest.json', {'observedSchema': '97'})
        database.write_json(secret / 'keys.json', keys())
        manifest = {'format': 1, 'checkpoint': keys()['checkpoint'], 'garageImage': recovery.GARAGE_IMAGE,
                    'garageImageId': 'sha256:' + 'd' * 64,
                    'sourceImages': {name: {'reference': recovery.GARAGE_IMAGE if name == 'garage' else 'owned', 'id': 'sha256:' + 'd' * 64} for name in recovery.SERVICES},
                    'files': {name: database.file_record(data / name) for name in recovery.FILES},
                    'databaseManifest': database.file_record(data / 'database/manifest.json'),
                    'keyPack': database.file_record(secret / 'keys.json')}
        database.write_json(data / 'manifest.json', manifest)
        return data, secret, manifest

    def test_mismatched_recorded_garage_image_is_rejected_before_docker(self):
        data, secret, manifest = self.prepare_set()
        manifest['sourceImages']['garage']['id'] = 'sha256:' + 'e' * 64
        (data / 'manifest.json').write_text(json.dumps(manifest))
        with patch.object(database, 'command') as command:
            with self.assertRaises(database.RecoveryError):
                recovery.verify_set(data, secret)
            command.assert_not_called()

    def test_tampered_payload_keypack_permissions_and_cross_checkpoint_keys_fail_before_docker(self):
        data, secret, manifest = self.prepare_set()
        with patch.object(database, 'load_backup', return_value=(data / 'database', {'observedSchema': '97'})):
            self.assertEqual(keys(), recovery.load_set(data, secret)[1])
        with patch.object(database, 'command') as command:
            (data / 'content.tar').write_bytes(b'tampered')
            with self.assertRaisesRegex(database.RecoveryError, 'checksum'):
                recovery.verify_set(data, secret)
            command.assert_not_called()
        # Restore the tar payload for the independent keypack checks.
        shutil.copyfile(data / 'metadata.tar', data / 'content.tar')
        manifest['files']['content.tar'] = database.file_record(data / 'content.tar')
        (data / 'manifest.json').write_text(json.dumps(manifest))
        (secret / 'keys.json').chmod(0o640)
        with self.assertRaises(database.RecoveryError):
            recovery.load_set(data, secret)
        (secret / 'keys.json').chmod(0o600)
        (secret / 'keys.json').write_text(json.dumps(keys() | {'checkpoint': 'b' * 32}))
        manifest['keyPack'] = database.file_record(secret / 'keys.json')
        (data / 'manifest.json').write_text(json.dumps(manifest))
        with self.assertRaisesRegex(database.RecoveryError, 'another checkpoint'):
            recovery.load_set(data, secret)
