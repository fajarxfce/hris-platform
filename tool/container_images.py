"""Container versions shared by Gradle tests and owned deployment fixtures."""

import json
from pathlib import Path
import re
import tomllib

ROOT = Path(__file__).resolve().parents[1]


def postgres_image():
    catalog = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())
    image = catalog["versions"]["postgres-image"]
    if not re.fullmatch(r"postgres:[a-zA-Z0-9._-]+@sha256:[0-9a-f]{64}", image):
        raise ValueError("PostgreSQL must use an immutable catalog image")
    return image


def check_deployment_images():
    expected = postgres_image()
    # Compose and the external native proof harness need standalone manifests.
    # Keep their explicit pins checked against the catalog, without env fallbacks.
    compose = (ROOT / "compose.yml").read_text()
    images = re.findall(r"^\s+image:\s+(postgres:\S+)\s*$", compose, re.MULTILINE)
    if images != [expected]:
        raise ValueError("Compose PostgreSQL differs from the version catalog")
    proof = json.loads((ROOT / "tool/collaboration-proof/images.json").read_text())
    if proof["postgres"] != expected:
        raise ValueError("Native proof PostgreSQL differs from the version catalog")


if __name__ == "__main__":
    check_deployment_images()
    print("PostgreSQL deployment and fixture image pins agree")
