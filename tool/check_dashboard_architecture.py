"""Check static dashboard import boundaries without requiring a browser or Node install."""

from pathlib import Path
import re
import sys


def check_dashboard(root: Path) -> list[str]:
    source_root = root / "apps/dashboard/src"
    errors = []
    for source in sorted(source_root.rglob("*")):
        if source.suffix not in {".ts", ".tsx"} or ".test." in source.name:
            continue
        relative = source.relative_to(source_root)
        parts = relative.parts
        text = source.read_text()
        imports = re.findall(r"\bfrom\s+[\"']([^\"']+)[\"']", text)
        imports += re.findall(r"\bimport\s*(?:\(\s*)?[\"']([^\"']+)[\"']", text)
        for imported in imports:
            target = (source.parent / imported).resolve() if imported.startswith(".") else None
            target_parts = target.relative_to(source_root).parts if target and target.is_relative_to(source_root) else ()
            problem = None
            if "domain" in parts and (not target_parts or "domain" not in target_parts):
                problem = "domain may only import domain code"
            if "usecases" in parts and "usecases" in target_parts:
                problem = "use cases must not import other use cases"
            if "datasources" in parts and "domain" in target_parts:
                problem = "datasources return DTOs and must not import domain"
            if "repositories" in parts and "data" in parts and "repositories" in target_parts and "data" in target_parts:
                problem = "repositories must not import other repository implementations"
            if "presentation" in parts and target_parts and any(layer in target_parts for layer in ("data", "di")):
                problem = "presentation must call domain use cases, not data or DI"
            if "core" == parts[0] and target_parts and target_parts[0] == "features":
                problem = "core must not depend on features"
            if parts[0] == "features" and target_parts and target_parts[0] == "features" and parts[1] != target_parts[1] and "domain" not in target_parts:
                problem = "cross-feature dependencies must use domain contracts"
            if problem:
                errors.append(f"apps/dashboard/src/{relative}: {problem} ({imported})")
        if "pages" in parts and re.search(r"\b(fetch|useEffect|setInterval|setTimeout|XMLHttpRequest)\s*\(", text):
            errors.append(f"apps/dashboard/src/{relative}: pages render state; effects belong to controllers/bindings")
    return errors


if __name__ == "__main__":
    failures = check_dashboard(Path(__file__).resolve().parents[1])
    if failures:
        print("\n".join(failures))
        sys.exit(1)
    print("Dashboard architecture checks passed")
