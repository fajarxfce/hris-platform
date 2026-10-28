import copy
import io
import json
import unittest

import review_deployment as deployment


class DeploymentReviewTest(unittest.TestCase):
    def specification(self):
        return {"services": {
            "api": {"environment": {"HRIS_SECURE_COOKIES": "true", "HRIS_PUBLIC_URL": "https://example.invalid"},
                    "ports": [{"host_ip": "127.0.0.1", "published": "8088", "target": 8080}]},
            "worker": {"environment": {}}, "database": {"ports": [{"host_ip": "::1", "published": "55432", "target": 5432}]},
        }}

    def test_rendered_flags_are_role_specific_and_do_not_claim_runtime_health(self):
        result = deployment.review(self.specification(), production=True)
        self.assertTrue(result["minimumWiringPassed"])
        self.assertEqual(set(deployment.FLAGS), set(result["runtimes"]["API"]["startupFlags"]))
        self.assertEqual({"SMTP", "FCM", "STORAGE", "SCANNER"}, set(result["runtimes"]["WORKER"]["startupFlags"]))
        self.assertFalse(result["processHealthChecked"])
        self.assertFalse(result["providerDeliveryChecked"])

    def test_provider_secrets_paths_and_even_invalid_flag_values_never_enter_output(self):
        spec = self.specification()
        private = "NEVER-EMIT-THIS-PROVIDER-MATERIAL"
        for role in ("api", "worker"):
            spec["services"][role]["environment"].update({"HRIS_MAIL_ENABLED": private,
                "HRIS_MAIL_PASSWORD": private, "HRIS_STORAGE_SECRET_KEY": private, "UNRELATED_ENV": private})
        spec["services"]["worker"]["volumes"] = [{"source": "/private/" + private}]
        output = io.StringIO()
        self.assertEqual(1, deployment.main([], source=io.BytesIO(json.dumps(spec).encode()), output=output))
        self.assertNotIn(private, output.getvalue())
        self.assertIn("smtp_flag_invalid", output.getvalue())

    def test_enabled_worker_fcm_requires_complete_config_and_read_only_existing_bind(self):
        spec = self.specification()
        worker = spec["services"]["worker"]
        worker["environment"] = {"HRIS_FCM_ENABLED": "true", "HRIS_FCM_PROJECT_ID": "private-project",
                                  "HRIS_FCM_CREDENTIALS_FILE": "/run/private.json"}
        worker["volumes"] = [{"type": "bind", "source": "/private/source.json", "target": "/run/private.json",
                              "read_only": True, "bind": {"create_host_path": False}}]
        self.assertTrue(deployment.review(spec)["minimumWiringPassed"])
        for change in ({"read_only": False}, {"bind": {"create_host_path": True}}, {"target": "/other"}):
            broken = copy.deepcopy(spec)
            broken["services"]["worker"]["volumes"][0].update(change)
            self.assertFalse(deployment.review(broken)["minimumWiringPassed"])
        del worker["environment"]["HRIS_FCM_PROJECT_ID"]
        self.assertIn("worker_fcm_configuration_incomplete", deployment.review(spec)["issues"])

    def test_runtime_disagreement_and_scanner_without_storage_are_explicit(self):
        spec = self.specification()
        spec["services"]["worker"]["environment"] = {"HRIS_DOCUMENT_SCANNER_ENABLED": "true", "HRIS_DOCUMENT_SCANNER_HOST": "scanner"}
        issues = deployment.review(spec)["issues"]
        self.assertIn("worker_scanner_requires_storage", issues)
        self.assertIn("scanner_runtime_flags_differ", issues)

    def test_production_rejects_public_service_bindings_and_development_transport(self):
        spec = self.specification()
        spec["services"]["api"]["ports"][0]["host_ip"] = "0.0.0.0"
        spec["services"]["api"]["environment"].update({"HRIS_SECURE_COOKIES": "false", "HRIS_PUBLIC_URL": "http://example.invalid", "HRIS_AUTH_LINKS_ALLOW_LOOPBACK_HTTP": "true"})
        spec["services"]["antivirus"] = {"ports": [{"host_ip": "127.0.0.1", "target": 3310}]}
        issues = deployment.review(spec, production=True)["issues"]
        for issue in ("api_public_binding", "antivirus_public_binding", "api_secure_cookies_required", "api_public_https_origin_required", "api_development_transport_override"):
            self.assertIn(issue, issues)

    def test_invalid_oversized_and_deep_inputs_fail_with_only_the_safe_category(self):
        for payload in (b'{"secret": "not printable",', b" " * (deployment.MAX_INPUT + 1), b"[" * 2000 + b"]" * 2000,
                        b'{"services":{"api":{"environment":[]}}}'):
            output = io.StringIO()
            self.assertEqual(1, deployment.main([], source=io.BytesIO(payload), output=output))
            self.assertEqual({"format": 1, "minimumWiringPassed": False, "issues": ["invalid_or_oversized_compose_input"]}, json.loads(output.getvalue()))


if __name__ == "__main__":
    unittest.main()
