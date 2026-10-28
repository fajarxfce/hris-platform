#!/usr/bin/env python3
"""Review rendered Compose configuration without emitting deployment secrets."""

import argparse
import ipaddress
import json
import sys
from urllib.parse import urlsplit

MAX_INPUT = 4 * 1024 ** 2
FLAGS = {
    "FIREBASE_AUTH": "HRIS_FIREBASE_AUTH_ENABLED", "OIDC": "HRIS_OIDC_ENABLED",
    "SMTP": "HRIS_MAIL_ENABLED", "FCM": "HRIS_FCM_ENABLED",
    "STORAGE": "HRIS_STORAGE_ENABLED", "SCANNER": "HRIS_DOCUMENT_SCANNER_ENABLED",
}
REQUIRED = {
    "FIREBASE_AUTH": ("HRIS_FIREBASE_PROJECT_ID",),
    "OIDC": ("HRIS_PUBLIC_URL", "HRIS_OIDC_ISSUER", "HRIS_OIDC_CLIENT_ID", "HRIS_OIDC_AUTHORIZATION_URI", "HRIS_OIDC_TOKEN_URI", "HRIS_OIDC_JWK_SET_URI"),
    "SMTP": ("HRIS_PUBLIC_URL", "HRIS_MAIL_HOST", "HRIS_MAIL_FROM"),
    "FCM": ("HRIS_FCM_PROJECT_ID", "HRIS_FCM_CREDENTIALS_FILE"),
    "STORAGE": ("HRIS_STORAGE_ENDPOINT", "HRIS_STORAGE_BUCKET", "HRIS_STORAGE_ACCESS_KEY", "HRIS_STORAGE_SECRET_KEY"),
    "SCANNER": ("HRIS_DOCUMENT_SCANNER_HOST",),
}


def flag(environment, name, default=False):
    value = str(environment.get(name, str(default))).strip().lower()
    if value in ("true", "on", "yes", "1"):
        return True
    if value in ("false", "off", "no", "0"):
        return False
    return None


def private_bindings(service, allow_loopback):
    for port in service.get("ports", []):
        if not allow_loopback or not isinstance(port, dict):
            return False
        try:
            if not ipaddress.ip_address(port.get("host_ip", "")).is_loopback:
                return False
        except ValueError:
            return False
    return True


def review(specification, production=False):
    services = specification["services"]
    if not isinstance(services, dict):
        raise ValueError("invalid services")
    result = {"format": 1, "source": "RENDERED_COMPOSE", "productionReview": production,
              "runtimes": {}, "checks": {}, "issues": [], "processHealthChecked": False, "providerDeliveryChecked": False}
    issues = result["issues"]
    for role in ("api", "worker"):
        if role not in services:
            issues.append(role + "_missing")
            continue
        environment = services[role].get("environment", {})
        if not isinstance(environment, dict):
            raise ValueError("expected normalized environment")
        kinds = FLAGS if role == "api" else {key: FLAGS[key] for key in ("SMTP", "FCM", "STORAGE", "SCANNER")}
        configured = {kind: flag(environment, property_name) for kind, property_name in kinds.items()}
        result["runtimes"][role.upper()] = {"startupFlags": configured}
        for kind, enabled in configured.items():
            if enabled is None:
                issues.append(role + "_" + kind.lower() + "_flag_invalid")
            elif enabled and any(not isinstance(environment.get(name), str) or not environment[name].strip() for name in REQUIRED[kind]):
                issues.append(role + "_" + kind.lower() + "_configuration_incomplete")
        if configured["SCANNER"] and not configured["STORAGE"]:
            issues.append(role + "_scanner_requires_storage")
        if role == "worker" and configured["FCM"]:
            target = environment.get("HRIS_FCM_CREDENTIALS_FILE")
            mounts = services[role].get("volumes", [])
            if not any(isinstance(mount, dict) and mount.get("target") == target and mount.get("read_only") is True
                       and mount.get("type") == "bind" and mount.get("bind", {}).get("create_host_path") is False for mount in mounts):
                issues.append("worker_fcm_read_only_credential_mount_missing")
        if production:
            for name in ("HRIS_MAIL_ALLOW_LOOPBACK_PLAINTEXT", "HRIS_AUTH_LINKS_ALLOW_LOOPBACK_HTTP", "HRIS_OIDC_ALLOW_LOOPBACK_HTTP"):
                if flag(environment, name) is not False:
                    issues.append(role + "_development_transport_override")
                    break
            if role == "api":
                if flag(environment, "HRIS_SECURE_COOKIES", True) is not True:
                    issues.append("api_secure_cookies_required")
                address = urlsplit(str(environment.get("HRIS_PUBLIC_URL", "")))
                if address.scheme != "https" or not address.hostname or address.username or address.password or address.query or address.fragment or address.path not in ("", "/"):
                    issues.append("api_public_https_origin_required")
    for kind in ("SMTP", "STORAGE", "SCANNER"):
        if all(role in result["runtimes"] for role in ("API", "WORKER")):
            flags = [result["runtimes"][role]["startupFlags"][kind] for role in ("API", "WORKER")]
            if flags[0] != flags[1]:
                issues.append(kind.lower() + "_runtime_flags_differ")
    for role in ("api", "worker", "database", "garage", "antivirus", "antivirus-update"):
        if role in services:
            private = private_bindings(services[role], role in ("api", "database"))
            result["checks"][role + "PrivateBindings"] = private
            if not private:
                issues.append(role + "_public_binding")
    # Explicit property names only; never include arbitrary environment, provider
    # IDs/addresses, volume paths, credentials or raw parser exception messages.
    result["minimumWiringPassed"] = not issues
    return result


def main(arguments=None, *, source=None, output=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--production", action="store_true", help="Also require HTTPS/secure cookies and disable development transport overrides")
    args = parser.parse_args(arguments)
    source, output = source or sys.stdin.buffer, output or sys.stdout
    try:
        content = source.read(MAX_INPUT + 1)
        if len(content) > MAX_INPUT:
            raise ValueError("oversized input")
        result = review(json.loads(content), args.production)
    except (ValueError, TypeError, KeyError, AttributeError, RecursionError):
        result = {"format": 1, "minimumWiringPassed": False, "issues": ["invalid_or_oversized_compose_input"]}
    print(json.dumps(result, indent=2), file=output)
    return 0 if result["minimumWiringPassed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
