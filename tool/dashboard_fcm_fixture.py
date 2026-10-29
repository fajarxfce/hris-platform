"""Explicit opt-in for real FCM in an independently owned native fixture."""

import json
from pathlib import Path
import re
import stat


def worker_fcm_environment(environment):
    if environment.get("HRIS_TEST_WITH_FCM") != "1":
        return {}
    if environment.get("HRIS_TEST_WITH_WORKER") != "1":
        raise RuntimeError("Native FCM acceptance requires the owned worker fixture")
    project = environment.get("HRIS_TEST_FCM_PROJECT_ID", "")
    location = environment.get("HRIS_TEST_FCM_CREDENTIALS_FILE", "")
    if not re.fullmatch(r"[a-z][a-z0-9-]{4,28}[a-z0-9]", project) or not location:
        raise RuntimeError("Native FCM acceptance requires explicit project and credential path")
    path = Path(location)
    try:
        details = path.stat()
        if not path.is_absolute() or not stat.S_ISREG(details.st_mode):
            raise ValueError()
        if details.st_size > 32768 or details.st_mode & 0o077:
            raise ValueError()
        with path.open("rb") as source:
            content = source.read(32769)
        if len(content) > 32768:
            raise ValueError()
        value = json.loads(content)
        if (not isinstance(value, dict) or value.get("type") != "service_account"
                or value.get("project_id") != project
                or value.get("token_uri") != "https://oauth2.googleapis.com/token"
                or not all(isinstance(value.get(key), str) and value[key]
                           for key in ("private_key", "private_key_id", "client_email"))):
            raise ValueError()
    except (OSError, ValueError):
        raise RuntimeError("Native FCM acceptance requires a private, bounded matching service account") from None
    return {
        "HRIS_FCM_ENABLED": "true",
        "HRIS_FCM_PROJECT_ID": project,
        "HRIS_FCM_CREDENTIALS_FILE": str(path),
    }
