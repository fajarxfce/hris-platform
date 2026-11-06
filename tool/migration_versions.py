"""Order numbered SQL migrations using Flyway's numeric dotted versions."""
from pathlib import Path
import re


def migration_version(value: str) -> tuple[int, ...]:
    if not isinstance(value, str) or len(value) > 64 or not re.fullmatch(r"[0-9]+(?:[._][0-9]+)*", value):
        raise ValueError("Invalid migration version")
    parts = [int(part) for part in re.split(r"[._]", value)]
    while len(parts) > 1 and parts[-1] == 0:
        parts.pop()
    return tuple(parts)


def ordered_migrations(directory: Path) -> list[Path]:
    versions: dict[tuple[int, ...], Path] = {}
    for path in directory.glob("V*__*.sql"):
        match = re.fullmatch(r"V([0-9]+(?:[._][0-9]+)*)__.+\.sql", path.name)
        if match is None:
            raise ValueError("Invalid numbered migration filename: " + path.name)
        version = migration_version(match[1])
        if version in versions:
            raise ValueError("Duplicate migration version: " + path.name)
        versions[version] = path
    return [versions[version] for version in sorted(versions)]
